/**
 * Crypto primitives of hot-edge (A14 §5, ADR-04, D-025). WebCrypto only, no dependencies.
 *
 * One root secret, TOKEN_ENC_KEY (32 random bytes, base64), is never used directly. HKDF-SHA256
 * derives one key per purpose, so a key can never be misused for another job:
 *  - "hot-edge/whoop-token/aead/v1" → AES-256-GCM for the WHOOP refresh and access tokens;
 *  - "hot-edge/oauth-state/hmac/v1" → HMAC-SHA256 for the signed OAuth `state`.
 *
 * Nothing here logs. Errors carry no secret material.
 */

const enc = new TextEncoder();
const dec = new TextDecoder();

export class ConfigError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "ConfigError";
  }
}

// ---------------------------------------------------------------------------------------------
// base64url

export function b64urlEncode(bytes: Uint8Array): string {
  let bin = "";
  for (const b of bytes) bin += String.fromCharCode(b);
  return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

export function b64urlDecode(text: string): Uint8Array {
  if (!/^[A-Za-z0-9_-]*$/.test(text)) throw new Error("invalid base64url");
  const pad = text.length % 4 === 0 ? "" : "=".repeat(4 - (text.length % 4));
  const bin = atob(text.replace(/-/g, "+").replace(/_/g, "/") + pad);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

function b64Decode(text: string): Uint8Array {
  const bin = atob(text.trim());
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

// ---------------------------------------------------------------------------------------------
// small helpers

export function randomBytes(n: number): Uint8Array {
  const out = new Uint8Array(n);
  crypto.getRandomValues(out);
  return out;
}

export async function sha256(data: string | Uint8Array): Promise<Uint8Array> {
  const bytes = typeof data === "string" ? enc.encode(data) : data;
  return new Uint8Array(await crypto.subtle.digest("SHA-256", bytes));
}

export async function sha256b64url(data: string | Uint8Array): Promise<string> {
  return b64urlEncode(await sha256(data));
}

/** Constant-time comparison of two strings of possibly different length. */
export function timingSafeEqual(a: string, b: string): boolean {
  const x = enc.encode(a);
  const y = enc.encode(b);
  let diff = x.length ^ y.length;
  const n = Math.max(x.length, y.length);
  for (let i = 0; i < n; i++) diff |= (x[i] ?? 0) ^ (y[i] ?? 0);
  return diff === 0;
}

// ---------------------------------------------------------------------------------------------
// keys

export interface EdgeKeys {
  /** AES-256-GCM for tokens at rest in the Durable Object. */
  tokenAead: CryptoKey;
  /** HMAC-SHA256 for the OAuth state. */
  stateMac: CryptoKey;
}

const keyCache = new Map<string, Promise<EdgeKeys>>();

/**
 * Derives the purpose keys from TOKEN_ENC_KEY. Fails closed with ConfigError if the secret is
 * missing or is not exactly 32 bytes of base64 (`openssl rand -base64 32`).
 */
export function deriveKeys(tokenEncKey: string | undefined): Promise<EdgeKeys> {
  if (!tokenEncKey) return Promise.reject(new ConfigError("TOKEN_ENC_KEY is not set"));
  const cached = keyCache.get(tokenEncKey);
  if (cached) return cached;
  const derived = (async () => {
    let raw: Uint8Array;
    try {
      raw = b64Decode(tokenEncKey);
    } catch {
      throw new ConfigError("TOKEN_ENC_KEY is not valid base64");
    }
    if (raw.length !== 32) throw new ConfigError("TOKEN_ENC_KEY must be 32 bytes (openssl rand -base64 32)");
    const ikm = await crypto.subtle.importKey("raw", raw, "HKDF", false, ["deriveKey"]);
    const hkdf = (info: string) => ({ name: "HKDF", hash: "SHA-256", salt: new Uint8Array(32), info: enc.encode(info) });
    const tokenAead = await crypto.subtle.deriveKey(
      hkdf("hot-edge/whoop-token/aead/v1"),
      ikm,
      { name: "AES-GCM", length: 256 },
      false,
      ["encrypt", "decrypt"],
    );
    const stateMac = await crypto.subtle.deriveKey(
      hkdf("hot-edge/oauth-state/hmac/v1"),
      ikm,
      { name: "HMAC", hash: "SHA-256", length: 256 },
      false,
      ["sign", "verify"],
    );
    return { tokenAead, stateMac };
  })();
  // A failed derivation must not stay cached: the next request re-reads the secret.
  derived.catch(() => keyCache.delete(tokenEncKey));
  keyCache.set(tokenEncKey, derived);
  return derived;
}

// ---------------------------------------------------------------------------------------------
// AES-GCM sealing of tokens: "v1.<iv>.<ciphertext+tag>", AAD binds the value to its owner/field

const SEAL_VERSION = "v1";

export async function seal(key: CryptoKey, plaintext: string, aad: string): Promise<string> {
  const iv = randomBytes(12);
  const ct = new Uint8Array(
    await crypto.subtle.encrypt({ name: "AES-GCM", iv, additionalData: enc.encode(aad), tagLength: 128 }, key, enc.encode(plaintext)),
  );
  return `${SEAL_VERSION}.${b64urlEncode(iv)}.${b64urlEncode(ct)}`;
}

/** Throws on a wrong key, wrong AAD, tampering or an unknown format. */
export async function open(key: CryptoKey, sealed: string, aad: string): Promise<string> {
  const parts = sealed.split(".");
  if (parts.length !== 3 || parts[0] !== SEAL_VERSION) throw new Error("unknown sealed format");
  const iv = b64urlDecode(parts[1]!);
  const ct = b64urlDecode(parts[2]!);
  if (iv.length !== 12) throw new Error("bad iv");
  const pt = await crypto.subtle.decrypt({ name: "AES-GCM", iv, additionalData: enc.encode(aad), tagLength: 128 }, key, ct);
  return dec.decode(pt);
}

// ---------------------------------------------------------------------------------------------
// HMAC

export async function hmac(key: CryptoKey, message: string): Promise<Uint8Array> {
  return new Uint8Array(await crypto.subtle.sign("HMAC", key, enc.encode(message)));
}
