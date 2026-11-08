import { b64urlDecode, b64urlEncode, hmac, randomBytes, sha256b64url, timingSafeEqual } from "./crypto";

/**
 * The small self-verifying values of the account flow (A65 §2–3, D-062):
 *
 *  - login nonce `n1.<payload>.<mac>` (5 min, one use: its id is spent in the account store);
 *  - sign-up ticket `t1.<payload>.<mac>` (10 min) — «this Google `sub` was proven, now an invite»;
 *  - refresh token `hr1.<sid>.<32 bytes>`: only a keyed hash is stored; the next token is derived
 *    from the presented one with a server key, so a retried request gets the same answer;
 *  - device proof: an ECDSA P-256 signature by the phone's Keystore key (A65 §3.3).
 *
 * Nothing here logs.
 */

// ---------------------------------------------------------------------------------------------
// Login nonce

export const NONCE_TTL_SECONDS = 5 * 60;
const MAC_BYTES = 16;

export async function issueNonce(key: CryptoKey, nowSeconds: number): Promise<{ nonce: string; exp: number }> {
  const exp = nowSeconds + NONCE_TTL_SECONDS;
  const body = new Uint8Array(16);
  new DataView(body.buffer).setUint32(0, exp);
  body.set(randomBytes(12), 4);
  const payload = b64urlEncode(body);
  const mac = (await hmac(key, `hot-edge|nonce|v1|${payload}`)).slice(0, MAC_BYTES);
  return { nonce: `n1.${payload}.${b64urlEncode(mac)}`, exp };
}

export type NonceCheck = { ok: true; id: string; exp: number } | { ok: false; reason: "malformed" | "bad_signature" | "expired" };

/** Checks the MAC and the expiry; the caller spends `id` in the store (one use). */
export async function checkNonce(key: CryptoKey, nonce: unknown, nowSeconds: number): Promise<NonceCheck> {
  if (typeof nonce !== "string" || nonce.length > 128) return { ok: false, reason: "malformed" };
  const parts = nonce.split(".");
  if (parts.length !== 3 || parts[0] !== "n1") return { ok: false, reason: "malformed" };
  const [, payload, mac] = parts as [string, string, string];
  let body: Uint8Array;
  try {
    body = b64urlDecode(payload);
    b64urlDecode(mac);
  } catch {
    return { ok: false, reason: "malformed" };
  }
  if (body.length !== 16) return { ok: false, reason: "malformed" };
  const expected = b64urlEncode((await hmac(key, `hot-edge|nonce|v1|${payload}`)).slice(0, MAC_BYTES));
  if (!timingSafeEqual(expected, mac)) return { ok: false, reason: "bad_signature" };
  const exp = new DataView(body.buffer, body.byteOffset, body.byteLength).getUint32(0);
  if (nowSeconds > exp) return { ok: false, reason: "expired" };
  return { ok: true, id: b64urlEncode(body.slice(4)), exp };
}

// ---------------------------------------------------------------------------------------------
// Sign-up ticket: a proven identity without an account waits for an invite (A65 §2.1 step 5, §2.5)

export const TICKET_TTL_SECONDS = 10 * 60;

export interface Ticket {
  provider: "google";
  subject: string;
  exp: number;
}

export async function issueTicket(key: CryptoKey, provider: "google", subject: string, nowSeconds: number): Promise<string> {
  const payload = b64urlEncode(new TextEncoder().encode(JSON.stringify({ p: provider, s: subject, e: nowSeconds + TICKET_TTL_SECONDS })));
  const mac = b64urlEncode(await hmac(key, `hot-edge|ticket|v1|${payload}`));
  return `t1.${payload}.${mac}`;
}

export async function checkTicket(key: CryptoKey, ticket: unknown, nowSeconds: number): Promise<Ticket | null> {
  if (typeof ticket !== "string" || ticket.length > 1024) return null;
  const parts = ticket.split(".");
  if (parts.length !== 3 || parts[0] !== "t1") return null;
  const [, payload, mac] = parts as [string, string, string];
  const expected = b64urlEncode(await hmac(key, `hot-edge|ticket|v1|${payload}`));
  if (!timingSafeEqual(expected, mac)) return null;
  try {
    const t = JSON.parse(new TextDecoder().decode(b64urlDecode(payload))) as { p?: unknown; s?: unknown; e?: unknown };
    if (t.p !== "google" || typeof t.s !== "string" || typeof t.e !== "number" || nowSeconds > t.e) return null;
    return { provider: t.p, subject: t.s, exp: t.e };
  } catch {
    return null;
  }
}

// ---------------------------------------------------------------------------------------------
// Refresh token

const SID_RE = /^[A-Za-z0-9_-]{22}$/;
const REFRESH_RE = /^hr1\.([A-Za-z0-9_-]{22})\.([A-Za-z0-9_-]{43})$/;

export function newSid(): string {
  return b64urlEncode(randomBytes(16));
}

export function isValidSid(sid: unknown): sid is string {
  return typeof sid === "string" && SID_RE.test(sid);
}

export function newRefreshToken(sid: string): string {
  return `hr1.${sid}.${b64urlEncode(randomBytes(32))}`;
}

/** The session id of a well-formed refresh token, or null. */
export function refreshSid(token: unknown): string | null {
  if (typeof token !== "string") return null;
  return REFRESH_RE.exec(token)?.[1] ?? null;
}

/** What the store keeps instead of the token: HMAC(pepper, token). */
export async function refreshHash(pepper: CryptoKey, token: string): Promise<string> {
  return b64urlEncode(await hmac(pepper, `hot-edge|refresh|v1|${token}`));
}

/** The token that replaces [token] at rotation: the same input always gives the same output. */
export async function nextRefreshToken(rotate: CryptoKey, token: string): Promise<string> {
  const sid = refreshSid(token);
  if (!sid) throw new Error("not a refresh token");
  return `hr1.${sid}.${b64urlEncode(await hmac(rotate, `hot-edge|rotate|v1|${token}`))}`;
}

// ---------------------------------------------------------------------------------------------
// Device key (A65 §3.3): EC P-256 in the Android Keystore, public part as SPKI (base64url).

const MAX_DEVICE_PUB = 256;
/** A signed refresh request is accepted ±5 min around the server clock. */
export const DEVICE_SIG_WINDOW_SECONDS = 5 * 60;

export function loginMessage(binding: string): string {
  return `hot-login|v1|${binding}`;
}

export async function refreshMessage(refresh: string, ts: number): Promise<string> {
  return `hot-refresh|v1|${await sha256b64url(refresh)}|${ts}`;
}

export function isPlausibleDevicePub(pub: unknown): pub is string {
  return typeof pub === "string" && pub.length >= 80 && pub.length <= MAX_DEVICE_PUB && /^[A-Za-z0-9_-]+$/.test(pub);
}

/**
 * Android's `SHA256withECDSA` gives ASN.1 DER `SEQUENCE { INTEGER r, INTEGER s }`; WebCrypto wants
 * the 64-byte `r ‖ s`. Raw 64-byte signatures are accepted as they are.
 */
export function derToRawP256(sig: Uint8Array): Uint8Array | null {
  if (sig.length === 64) return sig;
  if (sig.length < 8 || sig[0] !== 0x30) return null;
  let i = 2;
  if (sig[1]! & 0x80) i = 2 + (sig[1]! & 0x7f);
  const out = new Uint8Array(64);
  for (let n = 0; n < 2; n++) {
    if (sig[i] !== 0x02) return null;
    const len = sig[i + 1]!;
    let start = i + 2;
    let l = len;
    while (l > 32 && sig[start] === 0) {
      start++;
      l--;
    }
    if (l > 32 || start + l > sig.length) return null;
    out.set(sig.subarray(start, start + l), n * 32 + (32 - l));
    i = i + 2 + len;
  }
  return i === sig.length ? out : null;
}

/** True when [sigB64] is a valid signature of [message] by the device key [pubB64] (SPKI). */
export async function verifyDeviceSignature(pubB64: unknown, message: string, sigB64: unknown): Promise<boolean> {
  if (!isPlausibleDevicePub(pubB64) || typeof sigB64 !== "string" || sigB64.length > 200) return false;
  try {
    const key = await crypto.subtle.importKey("spki", b64urlDecode(pubB64), { name: "ECDSA", namedCurve: "P-256" }, false, ["verify"]);
    const raw = derToRawP256(b64urlDecode(sigB64));
    if (!raw) return false;
    return await crypto.subtle.verify({ name: "ECDSA", hash: "SHA-256" }, key, raw, new TextEncoder().encode(message));
  } catch {
    return false;
  }
}

// ---------------------------------------------------------------------------------------------
// Invite codes (A65 §2.5): 128 bits, Crockford base32, 26 characters; only a hash is stored.

const CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

export function newInviteCode(): string {
  const bytes = randomBytes(17); // 136 bits, 26 symbols × 5 bits = 130 used (≥ 128)
  let bits = 0;
  let value = 0;
  let out = "";
  for (const b of bytes) {
    value = (value << 8) | b;
    bits += 8;
    while (bits >= 5 && out.length < 26) {
      out += CROCKFORD[(value >>> (bits - 5)) & 31];
      bits -= 5;
    }
    value &= (1 << bits) - 1;
  }
  return out;
}

/** Normalises what a person typed: case, spaces and dashes, I/L → 1, O → 0. */
export function normaliseInvite(code: unknown): string | null {
  if (typeof code !== "string" || code.length > 64) return null;
  const c = code.toUpperCase().replace(/[\s-]/g, "").replace(/[IL]/g, "1").replace(/O/g, "0");
  return /^[0-9A-HJKMNP-TV-Z]{26}$/.test(c) ? c : null;
}

export function inviteHash(code: string): Promise<string> {
  return sha256b64url(`hot-edge|invite|v1|${code}`);
}
