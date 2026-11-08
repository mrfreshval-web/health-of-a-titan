import { b64urlDecode, b64urlEncode, hmac, randomBytes, sha256b64url, timingSafeEqual } from "./crypto";

/**
 * Signed OAuth `state` that binds the authorization code to the phone that started the login
 * (A14 ADR-03). WHOOP does not document PKCE, so the Worker does the PKCE check itself:
 *
 *  1. The phone makes a random `code_verifier` (RFC 7636 alphabet, 43–128 chars) and sends only
 *     `code_challenge = base64url(sha256(verifier))` to `POST /whoop/authorize`.
 *  2. The Worker returns `state = base64url(exp ‖ nonce) "." base64url(HMAC(exp ‖ nonce ‖ challenge))`.
 *     Nothing is stored: the state is self-verifying and expires in 10 minutes.
 *  3. WHOOP sends the code and the state back to `com.healthofatitan.app://oauth/whoop`.
 *  4. `POST /whoop/token {code, state, code_verifier}`: the Worker recomputes the challenge from
 *     the verifier and checks the MAC and the expiry before it spends `client_secret` on the code.
 *
 * Another app that intercepts the custom-scheme redirect gets the code and the state but not the
 * verifier, so it cannot turn the code into a session.
 */

export const STATE_TTL_SECONDS = 600;
const VERIFIER_RE = /^[A-Za-z0-9\-._~]{43,128}$/;
const CHALLENGE_RE = /^[A-Za-z0-9_-]{43}$/;
const MAC_BYTES = 16;

export function isValidChallenge(challenge: unknown): challenge is string {
  return typeof challenge === "string" && CHALLENGE_RE.test(challenge);
}

export function isValidVerifier(verifier: unknown): verifier is string {
  return typeof verifier === "string" && VERIFIER_RE.test(verifier);
}

/**
 * What a state is for (A65 §2.2, §2.3): `login` («Войти через WHOOP»), `link` (add WHOOP to the
 * signed-in account) or `reauth` (step-up with the WHOOP identity of the account). For `link` and
 * `reauth` the MAC also covers a [binding] — a hash of the account id and the session id — so a
 * code started by one session cannot be spent by another (CSRF binding).
 */
export type StateIntent = "login" | "link" | "reauth";

export interface StateBinding {
  intent: StateIntent;
  /** base64url(SHA-256(account_id | sid)); empty for `login`. */
  binding: string;
}

const LOGIN: StateBinding = { intent: "login", binding: "" };

export function isStateIntent(value: unknown): value is StateIntent {
  return value === "login" || value === "link" || value === "reauth";
}

/** The binding of a `link`/`reauth` state to the account session. */
export function sessionBinding(accountId: string, sid: string): Promise<string> {
  return sha256b64url(`hot-edge|oauth-binding|v1|${accountId}|${sid}`);
}

function macInput(payload: string, challenge: string, bind: StateBinding): string {
  // `login` keeps the format of slice 1, so states issued before the update stay valid.
  const base = `hot-edge|oauth-state|v1|${payload}|${challenge}`;
  return bind.intent === "login" ? base : `${base}|${bind.intent}|${bind.binding}`;
}

export async function issueState(key: CryptoKey, challenge: string, nowSeconds: number, bind: StateBinding = LOGIN): Promise<string> {
  const body = new Uint8Array(12);
  new DataView(body.buffer).setUint32(0, nowSeconds + STATE_TTL_SECONDS);
  body.set(randomBytes(8), 4);
  const payload = b64urlEncode(body);
  const mac = (await hmac(key, macInput(payload, challenge, bind))).slice(0, MAC_BYTES);
  return `${payload}.${b64urlEncode(mac)}`;
}

export type StateCheck = "ok" | "malformed" | "bad_signature" | "expired";

export async function verifyState(
  key: CryptoKey,
  state: unknown,
  verifier: unknown,
  nowSeconds: number,
  bind: StateBinding = LOGIN,
): Promise<StateCheck> {
  if (typeof state !== "string" || !isValidVerifier(verifier)) return "malformed";
  const parts = state.split(".");
  if (parts.length !== 2) return "malformed";
  const [payload, mac] = parts as [string, string];
  let body: Uint8Array;
  try {
    body = b64urlDecode(payload);
    b64urlDecode(mac);
  } catch {
    return "malformed";
  }
  if (body.length !== 12) return "malformed";
  const challenge = await sha256b64url(verifier);
  const expected = b64urlEncode((await hmac(key, macInput(payload, challenge, bind))).slice(0, MAC_BYTES));
  if (!timingSafeEqual(expected, mac)) return "bad_signature";
  const exp = new DataView(body.buffer, body.byteOffset, body.byteLength).getUint32(0);
  return nowSeconds > exp ? "expired" : "ok";
}
