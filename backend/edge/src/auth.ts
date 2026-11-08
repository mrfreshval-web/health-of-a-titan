import { type AccessClaims, loadSigningKeys, signAccessToken, verifyAccessToken } from "./access-token";
import { type Account, type AccountStore, DurableAccountStore, type Session } from "./account-store";
import { bearerToken } from "./allowlist";
import {
  checkNonce,
  checkTicket,
  DEVICE_SIG_WINDOW_SECONDS,
  inviteHash,
  isPlausibleDevicePub,
  issueNonce,
  issueTicket,
  loginMessage,
  newInviteCode,
  newRefreshToken,
  newSid,
  nextRefreshToken,
  normaliseInvite,
  refreshHash,
  refreshMessage,
  refreshSid,
  verifyDeviceSignature,
} from "./auth-tokens";
import { ConfigError, deriveKeys, type EdgeKeys } from "./crypto";
import type { Env } from "./env";
import { type GoogleIdTokenVerifier, sharedGoogleVerifier } from "./google";
import { sessionHash, sessionUserId } from "./session";
import { DurableTokenVault, type TokenVault } from "./token-vault";

/**
 * Routes of the HoT account (A65 §2–3, D-062; slices А1 and А2):
 *
 *   POST /auth/nonce       → one-use login nonce (Google `nonce`, migration)
 *   POST /auth/google      → Google ID token + device key → account session
 *   POST /auth/signup      → sign-up ticket + invite → new account (Google without an account)
 *   POST /auth/refresh     → rotation of the refresh token, signed by the device key
 *   POST /auth/logout      → this session is revoked
 *   POST /auth/logout-all  → every session of the account is revoked («Выйти везде»)
 *   POST /auth/migrate     → the old device session `hs1` becomes an account session (A65 §3.5)
 *   POST /auth/invites     → an invite code (only for accounts from the founder's allowlist)
 *
 * The WHOOP login (`/whoop/token`) answers with the same session (see index.ts).
 * Answers carry no e-mail, name or picture; logs carry only method, status and duration.
 */

export interface AuthContext {
  env: Env;
  nowSeconds: number;
  store: AccountStore;
  vault: TokenVault | null;
  google: GoogleIdTokenVerifier;
}

export class NotConfigured extends Error {}

export function json(status: number, body: unknown, extra: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store", "X-Content-Type-Options": "nosniff", ...extra },
  });
}

export const noContent = () => new Response(null, { status: 204, headers: { "Cache-Control": "no-store" } });
export const unauthorized = (error = "unauthorized") => json(401, { error }, { "WWW-Authenticate": "Bearer" });

export function authConfigured(env: Env): boolean {
  return Boolean(env.TOKEN_ENC_KEY && env.AUTH_SIGNING_KEY && env.ACCOUNT_VAULT);
}

/** The context of the account routes, or null when the Worker lacks the secrets/bindings. */
export function authContext(
  env: Env,
  nowSeconds: number,
  overrides: { store?: AccountStore; vault?: TokenVault; google?: GoogleIdTokenVerifier } = {},
): AuthContext | null {
  if (!authConfigured(env)) return null;
  return {
    env,
    nowSeconds,
    store: overrides.store ?? new DurableAccountStore(env.ACCOUNT_VAULT!),
    vault: overrides.vault ?? (env.WHOOP_VAULT ? new DurableTokenVault(env.WHOOP_VAULT) : null),
    google: overrides.google ?? sharedGoogleVerifier(),
  };
}

const keys = (ctx: AuthContext): Promise<EdgeKeys> => deriveKeys(ctx.env.TOKEN_ENC_KEY);
const signing = (ctx: AuthContext) => loadSigningKeys(ctx.env.AUTH_SIGNING_KEY, ctx.env.AUTH_SIGNING_KEY_PREVIOUS);
const nowMs = (ctx: AuthContext) => ctx.nowSeconds * 1000;

/** «Pixel 9 Pro XL» for the list of devices (A3); free text is cut and stripped of controls. */
function deviceName(raw: unknown): string {
  if (typeof raw !== "string") return "Android";
  const clean = raw.replace(/[\u0000-\u001f\u007f]/g, "").trim().slice(0, 64);
  return clean || "Android";
}

export interface DeviceProof {
  pub: string;
  name: string;
}

/** Checks the proof of possession of the device key over [binding] (A65 §3.3). */
export async function deviceProof(body: Record<string, unknown>, binding: string): Promise<DeviceProof | null> {
  if (!isPlausibleDevicePub(body.device_pub)) return null;
  if (!(await verifyDeviceSignature(body.device_pub, loginMessage(binding), body.device_sig))) return null;
  return { pub: body.device_pub, name: deviceName(body.device_name) };
}

/** Optional invite in a login body: absent → null, present but malformed → "invalid". */
export async function inviteOf(body: Record<string, unknown>): Promise<string | null | "invalid"> {
  if (body.invite === undefined || body.invite === null || body.invite === "") return null;
  const code = normaliseInvite(body.invite);
  return code ? inviteHash(code) : "invalid";
}

/** A new session for [account] on the device: the pair of tokens and the account summary. */
export async function issueSession(ctx: AuthContext, account: Account, device: DeviceProof): Promise<Record<string, unknown>> {
  const k = await keys(ctx);
  const sk = await signing(ctx);
  const sid = newSid();
  const refresh = newRefreshToken(sid);
  await ctx.store.createSession({
    sid,
    accountId: account.id,
    devicePub: device.pub,
    deviceName: device.name,
    refreshHash: await refreshHash(k.refreshPepper, refresh),
    now: nowMs(ctx),
  });
  const access = await signAccessToken(sk, account.id, sid, ctx.nowSeconds);
  return sessionBody(account, access, refresh);
}

export function sessionBody(account: Account, access: { token: string; exp: number }, refresh: string): Record<string, unknown> {
  return {
    access: access.token,
    access_expires_at: access.exp * 1000,
    refresh,
    account_id: account.id,
    identities: identitiesBody(account),
  };
}

/** The identities of an account as the phone sees them (never an e-mail or a name). */
export function identitiesBody(account: Account): Array<Record<string, unknown>> {
  return account.identities.map((i) => ({
    provider: i.provider,
    subject: i.subject,
    linked_at: i.linkedAt,
    last_login_at: i.lastLoginAt,
    ...(i.linkedDevice ? { linked_device: i.linkedDevice } : {}),
  }));
}

/** Claims of the bearer access token (signature and expiry only), or null. */
export async function accessClaims(ctx: AuthContext, request: Request): Promise<AccessClaims | null> {
  return verifyAccessToken(await signing(ctx), bearerToken(request), ctx.nowSeconds);
}

/** Access token **and** a live session in the store: for sensitive routes (A65 §3.4). */
export async function liveCaller(ctx: AuthContext, request: Request): Promise<LiveCaller | null> {
  const claims = await accessClaims(ctx, request);
  if (!claims) return null;
  const live = await ctx.store.liveSession(claims.sid, nowMs(ctx));
  if (!live || live.account.id !== claims.sub) return null;
  return { claims, account: live.account, session: live.session };
}

export interface LiveCaller {
  claims: AccessClaims;
  account: Account;
  session: Session;
}

// ---------------------------------------------------------------------------------------------
// Handlers

export async function authNonce(ctx: AuthContext): Promise<Response> {
  const { nonce, exp } = await issueNonce((await keys(ctx)).nonceMac, ctx.nowSeconds);
  return json(200, { nonce, expires_at: exp * 1000 });
}

/** Checks and spends a login nonce; null when it is fine, else the error answer. */
export async function spendNonce(ctx: AuthContext, nonce: unknown): Promise<Response | null> {
  const check = await checkNonce((await keys(ctx)).nonceMac, nonce, ctx.nowSeconds);
  if (!check.ok) return unauthorized(check.reason === "expired" ? "nonce_expired" : "invalid_nonce");
  if (!(await ctx.store.consumeNonce(check.id, check.exp, nowMs(ctx)))) return unauthorized("nonce_used");
  return null;
}

export async function authGoogle(ctx: AuthContext, body: Record<string, unknown>): Promise<Response> {
  const audience = ctx.env.GOOGLE_WEB_CLIENT_ID;
  if (!audience) throw new NotConfigured();
  const checked = await ctx.google.verify(body.id_token, audience);
  if (!checked.ok) return checked.reason === "jwks_unavailable" ? json(502, { error: "google_unavailable" }) : unauthorized("invalid_token");
  const device = await deviceProof(body, checked.nonce);
  if (!device) return json(400, { error: "invalid_device_proof" });
  const spent = await spendNonce(ctx, checked.nonce);
  if (spent) return spent;
  const invite = await inviteOf(body);
  const result =
    invite === "invalid"
      ? ({ status: "invite_invalid" } as const)
      : await ctx.store.login({ provider: "google", subject: checked.sub, inviteHash: invite, now: nowMs(ctx) });
  if (result.status !== "ok") {
    // «У тебя уже есть HoT? Войти через WHOOP» or an invite: the proven sub waits in a ticket.
    const ticket = await issueTicket((await keys(ctx)).nonceMac, "google", checked.sub, ctx.nowSeconds);
    return json(result.status === "no_account" ? 404 : 403, { error: result.status, signup_ticket: ticket });
  }
  await ctx.store.audit("login.google", result.account.id, nowMs(ctx));
  return json(200, await issueSession(ctx, result.account, device));
}

export async function authSignup(ctx: AuthContext, body: Record<string, unknown>): Promise<Response> {
  const ticket = await checkTicket((await keys(ctx)).nonceMac, body.signup_ticket, ctx.nowSeconds);
  if (!ticket) return unauthorized("invalid_ticket");
  const device = await deviceProof(body, String(body.signup_ticket));
  if (!device) return json(400, { error: "invalid_device_proof" });
  const invite = await inviteOf(body);
  if (invite === null || invite === "invalid") return json(403, { error: "invite_invalid" });
  const result = await ctx.store.login({ provider: ticket.provider, subject: ticket.subject, inviteHash: invite, createOnly: true, now: nowMs(ctx) });
  if (result.status === "exists") return json(409, { error: "account_exists" });
  if (result.status !== "ok") return json(403, { error: "invite_invalid" });
  await ctx.store.audit("login.google", result.account.id, nowMs(ctx));
  return json(200, await issueSession(ctx, result.account, device));
}

export async function authRefresh(ctx: AuthContext, body: Record<string, unknown>): Promise<Response> {
  const refresh = body.refresh;
  const sid = refreshSid(refresh);
  const ts = body.ts;
  if (!sid || typeof refresh !== "string" || typeof ts !== "number" || !Number.isInteger(ts)) return json(400, { error: "invalid_request" });
  if (Math.abs(ts - ctx.nowSeconds) > DEVICE_SIG_WINDOW_SECONDS) return json(400, { error: "clock_skew" });
  const live = await ctx.store.liveSession(sid, nowMs(ctx));
  if (!live) return unauthorized((await ctx.store.isTombstoned(sid)) ? "account_deleted" : "invalid_grant");
  if (!(await verifyDeviceSignature(live.session.devicePub, await refreshMessage(refresh, ts), body.sig))) {
    return unauthorized("invalid_device_proof");
  }
  const k = await keys(ctx);
  const next = await nextRefreshToken(k.refreshRotate, refresh);
  const rotated = await ctx.store.rotate({
    sid,
    presentedHash: await refreshHash(k.refreshPepper, refresh),
    nextHash: await refreshHash(k.refreshPepper, next),
    now: nowMs(ctx),
  });
  if (rotated.status === "reuse") return unauthorized("session_revoked");
  if (rotated.status === "invalid") return unauthorized("invalid_grant");
  const access = await signAccessToken(await signing(ctx), rotated.account.id, sid, ctx.nowSeconds);
  return json(200, sessionBody(rotated.account, access, next));
}

export async function authLogout(ctx: AuthContext, request: Request): Promise<Response> {
  const claims = await accessClaims(ctx, request);
  if (!claims) return unauthorized();
  await ctx.store.revokeSession(claims.sid, nowMs(ctx));
  return noContent();
}

export async function authLogoutAll(ctx: AuthContext, request: Request): Promise<Response> {
  const caller = await liveCaller(ctx, request);
  if (!caller) return unauthorized();
  await ctx.store.revokeAll(caller.account.id, nowMs(ctx));
  return noContent();
}

export async function authInvites(ctx: AuthContext, request: Request): Promise<Response> {
  const caller = await liveCaller(ctx, request);
  if (!caller) return unauthorized();
  if (!caller.account.inviter) return json(403, { error: "forbidden" });
  const code = newInviteCode();
  const { expiresAt } = await ctx.store.createInvite({ hash: await inviteHash(code), createdBy: caller.account.id, now: nowMs(ctx) });
  return json(201, { code, expires_at: expiresAt });
}

/**
 * A65 §3.5: the app of slice 1 holds `hs1.<whoop_user_id>.…`. It proves the device key over a
 * fresh nonce and gets an account (identity WHOOP) and a session; then `hs1` is spent. The WHOOP
 * tokens stay where they are. The route and ALLOWED_WHOOP_USER_IDS go away two weeks after release.
 */
export async function authMigrate(ctx: AuthContext, request: Request, body: Record<string, unknown>): Promise<Response> {
  const token = bearerToken(request);
  const userId = sessionUserId(token);
  if (!token || !userId || !ctx.vault) return unauthorized();
  if (typeof body.nonce !== "string") return json(400, { error: "invalid_request" });
  const device = await deviceProof(body, body.nonce);
  if (!device) return json(400, { error: "invalid_device_proof" });
  const spent = await spendNonce(ctx, body.nonce);
  if (spent) return spent;
  const hash = await sessionHash(token);
  if ((await ctx.vault.legacyCheck(userId, hash)) !== userId) return unauthorized();
  const result = await ctx.store.login({ provider: "whoop", subject: userId, bootstrap: true, now: nowMs(ctx) });
  if (result.status !== "ok") return json(502, { error: "store_unavailable" });
  const session = await issueSession(ctx, result.account, device);
  await ctx.vault.legacyRetire(userId, hash);
  await ctx.store.audit("login.migrate", result.account.id, nowMs(ctx));
  return json(200, { ...session, whoop_user_id: userId });
}

/** The WHOOP identity of an account, if it has one. */
export function whoopSubject(account: Account): string | null {
  return account.identities.find((i) => i.provider === "whoop")?.subject ?? null;
}

export { ConfigError };
