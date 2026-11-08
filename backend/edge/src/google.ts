import { b64urlDecode } from "./crypto";

/**
 * Verification of a Google ID token on the server (A65 §2.1 step 4, §9). The phone gets the token
 * from Credential Manager («Войти через Google») with our server nonce; the Worker trusts nothing
 * in it until all of this holds:
 *
 *  - the signature is RS256 by a key of Google's JWKS (`kid`), cached per `Cache-Control`;
 *  - `iss` ∈ {accounts.google.com, https://accounts.google.com};
 *  - `aud` = our Web client id (GOOGLE_WEB_CLIENT_ID, a Worker setting, never in code);
 *  - `exp` > now (60 s skew), `iat` not older than 10 minutes and not in the future;
 *  - `nonce` is present (the caller checks it is ours and spends it).
 *
 * The identity key is `sub` only — never the e-mail (A65 §2.3: no linking by e-mail). The e-mail,
 * name and picture claims are not read at all.
 */

export const GOOGLE_JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs";
export const GOOGLE_ISSUERS: readonly string[] = ["accounts.google.com", "https://accounts.google.com"];

const SKEW_SECONDS = 60;
const MAX_AGE_SECONDS = 10 * 60;
const MAX_TOKEN_LENGTH = 8 * 1024;
/** Used when Google sends no max-age. */
const DEFAULT_JWKS_TTL_MS = 60 * 60 * 1000;
/** An unknown kid refetches the JWKS at most this often (key rotation at Google). */
const REFETCH_COOLDOWN_MS = 60 * 1000;

const enc = new TextEncoder();
const dec = new TextDecoder();

export type GoogleCheck =
  | { ok: true; sub: string; nonce: string }
  | { ok: false; reason: "malformed" | "bad_signature" | "unknown_key" | "bad_issuer" | "bad_audience" | "expired" | "no_nonce" | "jwks_unavailable" };

interface JwksCache {
  keys: Map<string, CryptoKey>;
  expiresAt: number;
  fetchedAt: number;
}

export class GoogleIdTokenVerifier {
  private cache: JwksCache | null = null;
  private inflight: Promise<JwksCache> | null = null;

  constructor(
    private readonly http: typeof fetch = (input, init) => fetch(input, init),
    private readonly nowMs: () => number = Date.now,
  ) {}

  async verify(idToken: unknown, audience: string): Promise<GoogleCheck> {
    if (typeof idToken !== "string" || idToken.length > MAX_TOKEN_LENGTH) return { ok: false, reason: "malformed" };
    const parts = idToken.split(".");
    if (parts.length !== 3) return { ok: false, reason: "malformed" };
    const [h, p, s] = parts as [string, string, string];
    let header: { alg?: unknown; kid?: unknown };
    let claims: Record<string, unknown>;
    let signature: Uint8Array;
    try {
      header = JSON.parse(dec.decode(b64urlDecode(h))) as typeof header;
      claims = JSON.parse(dec.decode(b64urlDecode(p))) as Record<string, unknown>;
      signature = b64urlDecode(s);
    } catch {
      return { ok: false, reason: "malformed" };
    }
    if (header.alg !== "RS256" || typeof header.kid !== "string") return { ok: false, reason: "malformed" };

    let key: CryptoKey | undefined;
    try {
      key = await this.key(header.kid);
    } catch {
      return { ok: false, reason: "jwks_unavailable" };
    }
    if (!key) return { ok: false, reason: "unknown_key" };
    const valid = await crypto.subtle.verify({ name: "RSASSA-PKCS1-v1_5" }, key, signature, enc.encode(`${h}.${p}`));
    if (!valid) return { ok: false, reason: "bad_signature" };

    if (typeof claims.iss !== "string" || !GOOGLE_ISSUERS.includes(claims.iss)) return { ok: false, reason: "bad_issuer" };
    const aud = claims.aud;
    const audOk = typeof aud === "string" ? aud === audience : Array.isArray(aud) && aud.length === 1 && aud[0] === audience;
    if (!audience || !audOk) return { ok: false, reason: "bad_audience" };
    const now = Math.floor(this.nowMs() / 1000);
    const exp = claims.exp;
    const iat = claims.iat;
    if (typeof exp !== "number" || typeof iat !== "number") return { ok: false, reason: "malformed" };
    if (exp + SKEW_SECONDS <= now || iat < now - MAX_AGE_SECONDS || iat > now + SKEW_SECONDS) return { ok: false, reason: "expired" };
    if (typeof claims.sub !== "string" || !/^[A-Za-z0-9_-]{1,255}$/.test(claims.sub)) return { ok: false, reason: "malformed" };
    if (typeof claims.nonce !== "string" || claims.nonce.length === 0) return { ok: false, reason: "no_nonce" };
    return { ok: true, sub: claims.sub, nonce: claims.nonce };
  }

  private async key(kid: string): Promise<CryptoKey | undefined> {
    const now = this.nowMs();
    let jwks = this.cache;
    if (!jwks || jwks.expiresAt <= now) jwks = await this.fetchJwks();
    const key = jwks.keys.get(kid);
    if (key || now - jwks.fetchedAt < REFETCH_COOLDOWN_MS) return key;
    return (await this.fetchJwks()).keys.get(kid);
  }

  /** Single-flight fetch of Google's keys. */
  private fetchJwks(): Promise<JwksCache> {
    if (!this.inflight) {
      this.inflight = this.load().finally(() => {
        this.inflight = null;
      });
    }
    return this.inflight;
  }

  private async load(): Promise<JwksCache> {
    const res = await this.http(GOOGLE_JWKS_URL, { headers: { Accept: "application/json" } });
    if (!res.ok) throw new Error(`jwks ${res.status}`);
    const body = (await res.json()) as { keys?: Array<Record<string, unknown>> };
    const keys = new Map<string, CryptoKey>();
    for (const k of body.keys ?? []) {
      if (k.kty !== "RSA" || typeof k.kid !== "string" || typeof k.n !== "string" || typeof k.e !== "string") continue;
      if (k.alg !== undefined && k.alg !== "RS256") continue;
      if (k.use !== undefined && k.use !== "sig") continue;
      keys.set(
        k.kid,
        await crypto.subtle.importKey("jwk", { kty: "RSA", n: k.n, e: k.e }, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"]),
      );
    }
    const maxAge = /max-age=(\d+)/.exec(res.headers.get("Cache-Control") ?? "")?.[1];
    const now = this.nowMs();
    const ttl = maxAge ? Math.min(Number(maxAge) * 1000, 24 * 60 * 60 * 1000) : DEFAULT_JWKS_TTL_MS;
    this.cache = { keys, expiresAt: now + ttl, fetchedAt: now };
    return this.cache;
  }
}

/** One verifier per isolate, so the JWKS cache survives between requests. */
let shared: GoogleIdTokenVerifier | null = null;
export function sharedGoogleVerifier(): GoogleIdTokenVerifier {
  shared ??= new GoogleIdTokenVerifier();
  return shared;
}
