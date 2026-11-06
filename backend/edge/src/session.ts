import { b64urlEncode, randomBytes, sha256b64url } from "./crypto";

/**
 * Device session of the phone (A14 §5 «AppAuth», ADR-04 refined in slice 1).
 *
 * Since D-025 the refresh token lives only in the Durable Object, so the phone needs its own
 * credential to ask for access tokens. After a successful login the Worker issues one opaque
 * session token: `hs1.<whoopUserId>.<32 random bytes, base64url>`.
 *  - The user id part only routes the request to that user's Durable Object; it is not trusted.
 *  - The Durable Object stores SHA-256 of the whole token, never the token itself, and compares
 *    in constant time. One session per user: a new login replaces the old one.
 *  - The phone keeps it encrypted (Tink AEAD, Android Keystore) and sends it as
 *    `Authorization: Bearer hs1.…` to `/whoop/access`, `/whoop/disconnect` and every AppAuth route.
 */

export const SESSION_PREFIX = "hs1";
const SESSION_RE = /^hs1\.([A-Za-z0-9-]{1,64})\.([A-Za-z0-9_-]{43})$/;

export function newSessionToken(userId: string): string {
  return `${SESSION_PREFIX}.${userId}.${b64urlEncode(randomBytes(32))}`;
}

/** The routing part of a well-formed session token, or null. */
export function sessionUserId(token: string | null): string | null {
  if (!token) return null;
  return SESSION_RE.exec(token)?.[1] ?? null;
}

export function sessionHash(token: string): Promise<string> {
  return sha256b64url(`hot-edge|session|v1|${token}`);
}

export function isValidUserId(id: string): boolean {
  return /^[A-Za-z0-9-]{1,64}$/.test(id);
}
