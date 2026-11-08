import { type Account, type Identity, type Provider, type Session, stepUpUntil } from "./account-store";
import { type AuthContext, identitiesBody, json, type LiveCaller, liveCaller, noContent, NotConfigured, spendNonce, unauthorized } from "./auth";
import { sha256b64url } from "./crypto";

/**
 * Slices А3 and А5 of the HoT account (A65 §2.3, §3.4, §6, D-062):
 *
 *   GET  /auth/account          → identities, devices and the step-up window of this session
 *   POST /auth/devices/revoke   → «Завершить» one device of the account
 *   POST /auth/reauth/google    → step-up: a fresh Google sign-in with an identity of the account
 *   POST /auth/link/google      → link a Google identity (step-up ≤ 10 min, fresh nonce)
 *   POST /auth/unlink           → unlink an identity (never the last one); WHOOP → tokens revoked
 *   POST /auth/delete           → delete the account (step-up ≤ 10 min)
 *   POST /account/delete        → the web page of deletion: a fresh Google sign-in, same deletion
 *
 * WHOOP linking and the WHOOP step-up go through `/whoop/authorize` + `/whoop/token` with
 * `intent=link | reauth` (index.ts). Answers carry no e-mail, name or picture.
 */

const nowMs = (ctx: AuthContext) => ctx.nowSeconds * 1000;

const PROVIDERS: readonly Provider[] = ["google", "whoop"];

export function isProvider(value: unknown): value is Provider {
  return typeof value === "string" && (PROVIDERS as readonly string[]).includes(value);
}

/** True when the last proof of the person in this session is ≤ 10 min old (A65 §2.3). */
export function isFresh(ctx: AuthContext, session: Session): boolean {
  return nowMs(ctx) <= stepUpUntil(session);
}

/** `403 step_up_required`: sign in again with an identity of the account, then retry. */
export const stepUpRequired = () => json(403, { error: "step_up_required" });

/** The list of devices shows an id derived from the session id, never the session id itself. */
export function deviceId(sid: string): Promise<string> {
  return sha256b64url(`hot-edge|device|v1|${sid}`).then((h) => h.slice(0, 22));
}

async function accountBody(ctx: AuthContext, caller: LiveCaller): Promise<Record<string, unknown>> {
  const sessions = await ctx.store.sessions(caller.account.id, nowMs(ctx));
  const devices = await Promise.all(
    sessions.map(async (s) => ({
      id: await deviceId(s.sid),
      name: s.deviceName,
      created_at: s.createdAt,
      last_used_at: s.lastUsedAt,
      current: s.sid === caller.session.sid,
    })),
  );
  return {
    account_id: caller.account.id,
    identities: identitiesBody(caller.account),
    devices,
    step_up_until: stepUpUntil(caller.session),
  };
}

export async function authAccount(ctx: AuthContext, request: Request): Promise<Response> {
  const caller = await liveCaller(ctx, request);
  if (!caller) return unauthorized();
  return json(200, await accountBody(ctx, caller));
}

export async function authRevokeDevice(ctx: AuthContext, request: Request, body: Record<string, unknown>): Promise<Response> {
  const caller = await liveCaller(ctx, request);
  if (!caller) return unauthorized();
  if (typeof body.id !== "string" || body.id.length > 64) return json(400, { error: "invalid_request" });
  for (const s of await ctx.store.sessions(caller.account.id, nowMs(ctx))) {
    if ((await deviceId(s.sid)) === body.id) {
      await ctx.store.revokeDevice(caller.account.id, s.sid, nowMs(ctx));
      return noContent();
    }
  }
  return json(404, { error: "not_found" });
}

/** A fresh Google sign-in with its nonce spent: the `sub`, or the error answer. */
async function freshGoogle(ctx: AuthContext, idToken: unknown): Promise<{ sub: string } | Response> {
  const audience = ctx.env.GOOGLE_WEB_CLIENT_ID;
  if (!audience) throw new NotConfigured();
  const checked = await ctx.google.verify(idToken, audience);
  if (!checked.ok) return checked.reason === "jwks_unavailable" ? json(502, { error: "google_unavailable" }) : unauthorized("invalid_token");
  const spent = await spendNonce(ctx, checked.nonce);
  if (spent) return spent;
  return { sub: checked.sub };
}

/** Step-up with Google: the ID token must belong to the Google identity of this account. */
export async function authReauthGoogle(ctx: AuthContext, request: Request, body: Record<string, unknown>): Promise<Response> {
  const caller = await liveCaller(ctx, request);
  if (!caller) return unauthorized();
  const google = await freshGoogle(ctx, body.id_token);
  if (google instanceof Response) return google;
  if (!caller.account.identities.some((i) => i.provider === "google" && i.subject === google.sub)) {
    return json(403, { error: "identity_mismatch" });
  }
  const session = await ctx.store.stepUp(caller.session.sid, nowMs(ctx));
  if (!session) return unauthorized();
  return json(200, { step_up_until: stepUpUntil(session) });
}

/** «Привязать Google» (A65 §2.3): live session, step-up ≤ 10 min, a fresh Google sign-in. */
export async function authLinkGoogle(ctx: AuthContext, request: Request, body: Record<string, unknown>): Promise<Response> {
  const caller = await liveCaller(ctx, request);
  if (!caller) return unauthorized();
  if (!isFresh(ctx, caller.session)) return stepUpRequired();
  const google = await freshGoogle(ctx, body.id_token);
  if (google instanceof Response) return google;
  return linkAnswer(ctx, caller, "google", google.sub);
}

/** Links [subject] to the caller's account; the answer of every link route. */
export async function linkAnswer(ctx: AuthContext, caller: LiveCaller, provider: Provider, subject: string, extra: Record<string, unknown> = {}): Promise<Response> {
  const result = await ctx.store.link({ accountId: caller.account.id, provider, subject, device: caller.session.deviceName, now: nowMs(ctx) });
  if (result.status === "in_use") return json(409, { error: "identity_in_use", provider });
  if (result.status === "provider_linked") return json(409, { error: "provider_linked", provider });
  if (result.status !== "ok") return unauthorized();
  return json(200, { account_id: result.account.id, identities: identitiesBody(result.account), linked: result.linked, ...extra });
}

/** «Отвязать» (A65 §2.3): never the last identity; WHOOP → its tokens are revoked and wiped. */
export async function authUnlink(ctx: AuthContext, request: Request, body: Record<string, unknown>): Promise<Response> {
  const caller = await liveCaller(ctx, request);
  if (!caller) return unauthorized();
  if (!isProvider(body.provider)) return json(400, { error: "invalid_request" });
  if (!isFresh(ctx, caller.session)) return stepUpRequired();
  const result = await ctx.store.unlink({ accountId: caller.account.id, provider: body.provider, now: nowMs(ctx) });
  if (result.status === "last_identity") return json(409, { error: "last_identity" });
  if (result.status === "not_linked") return json(404, { error: "not_linked" });
  if (result.status !== "ok") return unauthorized();
  if (result.removed.provider === "whoop" && ctx.vault) await ctx.vault.disconnect(result.removed.subject);
  return json(200, { account_id: result.account.id, identities: identitiesBody(result.account) });
}

/**
 * The deletion itself (A65 §6), for the app and the web page: WHOOP tokens revoked at WHOOP and
 * wiped, then identities, sessions and invites; the audit keeps one line without an id. Backups
 * (slice А6) do not exist yet; when they do, their objects are deleted here too. Google gave us
 * no tokens, so there is nothing to revoke there.
 */
export async function deleteAccount(ctx: AuthContext, account: Account): Promise<Identity[] | null> {
  const whoop = account.identities.find((i) => i.provider === "whoop");
  if (whoop && ctx.vault) await ctx.vault.disconnect(whoop.subject);
  return ctx.store.deleteAccount(account.id, nowMs(ctx));
}

export async function authDelete(ctx: AuthContext, request: Request): Promise<Response> {
  const caller = await liveCaller(ctx, request);
  if (!caller) return unauthorized();
  if (!isFresh(ctx, caller.session)) return stepUpRequired();
  await deleteAccount(ctx, caller.account);
  return json(200, { deleted: true });
}

/**
 * The web page of deletion (Google Play «Account deletion»): the person signs in with Google on
 * the page (a fresh ID token with our nonce = the step-up) and the account of that `sub` is
 * deleted. Nothing else is asked or kept. WHOOP on the web needs an https redirect at WHOOP (later).
 */
export async function webDelete(ctx: AuthContext, body: Record<string, unknown>): Promise<Response> {
  const google = await freshGoogle(ctx, body.id_token);
  if (google instanceof Response) return google;
  const owner = await ctx.store.ownerOf("google", google.sub);
  if (!owner) return json(404, { error: "no_account" });
  const login = await ctx.store.login({ provider: "google", subject: google.sub, now: nowMs(ctx) });
  if (login.status !== "ok") return json(404, { error: "no_account" });
  await deleteAccount(ctx, login.account);
  return json(200, { deleted: true });
}
