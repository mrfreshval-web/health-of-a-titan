import { b64urlDecode, b64urlEncode, ConfigError } from "./crypto";

/**
 * Access token of a HoT account (A65 §3.1, D-062): a compact JWS signed with Ed25519 (EdDSA).
 *
 *   header  {"alg":"EdDSA","typ":"at+jwt","kid":<kid>}
 *   payload {"iss":"hot-edge","sub":<account_id>,"sid":<session id>,"iat":<s>,"exp":<s>}
 *
 * It lives 15 minutes and is never stored: routes check the signature only; sensitive routes also
 * ask the account store whether the session `sid` is still live (A65 §3.4).
 *
 * Keys come only from Worker secrets (never from code):
 *  - AUTH_SIGNING_KEY — the current private key, a JWK `{"kty":"OKP","crv":"Ed25519","d","x","kid"}`;
 *  - AUTH_SIGNING_KEY_PREVIOUS — optional, the previous key after a rotation (only `x` and `kid`
 *    are used): its tokens stay valid until they expire (≤ 15 min).
 */

export const ACCESS_TTL_SECONDS = 15 * 60;
export const ACCESS_ISSUER = "hot-edge";
/** Clock skew accepted on `iat`/`exp`. */
const SKEW_SECONDS = 60;
const MAX_TOKEN_LENGTH = 2048;

const enc = new TextEncoder();
const dec = new TextDecoder();

export interface AccessClaims {
  sub: string;
  sid: string;
  iat: number;
  exp: number;
}

export interface SigningKeys {
  kid: string;
  privateKey: CryptoKey;
  /** kid → public key: the current one and, during a rotation, the previous one. */
  verify: Map<string, CryptoKey>;
}

interface OkpJwk {
  kty?: unknown;
  crv?: unknown;
  d?: unknown;
  x?: unknown;
  kid?: unknown;
}

const KID_RE = /^[A-Za-z0-9._-]{1,64}$/;
const B64URL_32 = /^[A-Za-z0-9_-]{43}$/;

function parseJwk(raw: string, name: string, needPrivate: boolean): { kid: string; x: string; d?: string } {
  let jwk: OkpJwk;
  try {
    jwk = JSON.parse(raw) as OkpJwk;
  } catch {
    throw new ConfigError(`${name} is not a JSON JWK`);
  }
  if (jwk.kty !== "OKP" || jwk.crv !== "Ed25519") throw new ConfigError(`${name} must be an Ed25519 JWK`);
  if (typeof jwk.kid !== "string" || !KID_RE.test(jwk.kid)) throw new ConfigError(`${name} needs a kid`);
  if (typeof jwk.x !== "string" || !B64URL_32.test(jwk.x)) throw new ConfigError(`${name} has no public part`);
  if (needPrivate && (typeof jwk.d !== "string" || !B64URL_32.test(jwk.d))) throw new ConfigError(`${name} has no private part`);
  return { kid: jwk.kid, x: jwk.x, d: typeof jwk.d === "string" ? jwk.d : undefined };
}

const cache = new Map<string, Promise<SigningKeys>>();

/** Loads the signing keys from the secrets. Fails closed with ConfigError. */
export function loadSigningKeys(current: string | undefined, previous?: string): Promise<SigningKeys> {
  if (!current) return Promise.reject(new ConfigError("AUTH_SIGNING_KEY is not set"));
  const cacheKey = `${current}\u0000${previous ?? ""}`;
  const hit = cache.get(cacheKey);
  if (hit) return hit;
  const loaded = (async () => {
    const cur = parseJwk(current, "AUTH_SIGNING_KEY", true);
    const alg = { name: "Ed25519" };
    const privateKey = await crypto.subtle.importKey("jwk", { kty: "OKP", crv: "Ed25519", x: cur.x, d: cur.d }, alg, false, ["sign"]);
    const verify = new Map<string, CryptoKey>();
    verify.set(cur.kid, await crypto.subtle.importKey("jwk", { kty: "OKP", crv: "Ed25519", x: cur.x }, alg, false, ["verify"]));
    if (previous) {
      const prev = parseJwk(previous, "AUTH_SIGNING_KEY_PREVIOUS", false);
      if (prev.kid !== cur.kid) {
        verify.set(prev.kid, await crypto.subtle.importKey("jwk", { kty: "OKP", crv: "Ed25519", x: prev.x }, alg, false, ["verify"]));
      }
    }
    return { kid: cur.kid, privateKey, verify };
  })();
  loaded.catch(() => cache.delete(cacheKey));
  cache.set(cacheKey, loaded);
  return loaded;
}

export async function signAccessToken(keys: SigningKeys, accountId: string, sid: string, nowSeconds: number): Promise<{ token: string; exp: number }> {
  const exp = nowSeconds + ACCESS_TTL_SECONDS;
  const header = b64urlEncode(enc.encode(JSON.stringify({ alg: "EdDSA", typ: "at+jwt", kid: keys.kid })));
  const payload = b64urlEncode(enc.encode(JSON.stringify({ iss: ACCESS_ISSUER, sub: accountId, sid, iat: nowSeconds, exp })));
  const input = `${header}.${payload}`;
  const sig = new Uint8Array(await crypto.subtle.sign({ name: "Ed25519" }, keys.privateKey, enc.encode(input)));
  return { token: `${input}.${b64urlEncode(sig)}`, exp };
}

/** The claims of a valid access token, or null (bad format, unknown kid, bad signature, expired). */
export async function verifyAccessToken(keys: SigningKeys, token: string | null, nowSeconds: number): Promise<AccessClaims | null> {
  if (!token || token.length > MAX_TOKEN_LENGTH) return null;
  const parts = token.split(".");
  if (parts.length !== 3) return null;
  const [h, p, s] = parts as [string, string, string];
  try {
    const header = JSON.parse(dec.decode(b64urlDecode(h))) as { alg?: unknown; kid?: unknown; typ?: unknown };
    if (header.alg !== "EdDSA" || header.typ !== "at+jwt" || typeof header.kid !== "string") return null;
    const key = keys.verify.get(header.kid);
    if (!key) return null;
    const ok = await crypto.subtle.verify({ name: "Ed25519" }, key, b64urlDecode(s), enc.encode(`${h}.${p}`));
    if (!ok) return null;
    const c = JSON.parse(dec.decode(b64urlDecode(p))) as Record<string, unknown>;
    if (c.iss !== ACCESS_ISSUER) return null;
    if (typeof c.sub !== "string" || typeof c.sid !== "string" || typeof c.iat !== "number" || typeof c.exp !== "number") return null;
    if (c.exp <= nowSeconds || c.iat > nowSeconds + SKEW_SECONDS) return null;
    return { sub: c.sub, sid: c.sid, iat: c.iat, exp: c.exp };
  } catch {
    return null;
  }
}
