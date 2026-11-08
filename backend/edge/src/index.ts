import {
  authAccount,
  authDelete,
  authLinkGoogle,
  authReauthGoogle,
  authRevokeDevice,
  authUnlink,
  isFresh,
  linkAnswer,
  stepUpRequired,
  webDelete,
} from "./account-routes";
import { type AccountStore, stepUpUntil } from "./account-store";
import { bearerToken, isAllowedWhoopUser } from "./allowlist";
import {
  accessClaims,
  authContext,
  type AuthContext,
  authGoogle,
  authInvites,
  authLogout,
  authLogoutAll,
  authMigrate,
  authNonce,
  authRefresh,
  authSignup,
  deviceProof,
  inviteOf,
  issueSession,
  json,
  type LiveCaller,
  liveCaller,
  noContent,
  NotConfigured,
  unauthorized,
  whoopSubject,
} from "./auth";
import { ConfigError, deriveKeys } from "./crypto";
import type { Env } from "./env";
import type { GoogleIdTokenVerifier } from "./google";
import { deletePage, DELETE_PAGE_SCRIPT, deletePageScript } from "./delete-page";
import { isStateIntent, isValidChallenge, issueState, sessionBinding, type StateBinding, verifyState } from "./oauth-state";
import { isValidUserId, sessionHash, sessionUserId } from "./session";
import { DurableTokenVault, type TokenVault } from "./token-vault";
import type { AccessResult } from "./vault";
import { authorizeUrl, DEFAULT_REDIRECT_URI, WHOOP_AUTHORIZE_URL, WHOOP_SCOPES, WhoopClient, WhoopError } from "./whoop";

export { WhoopVault } from "./vault";
export { AccountVault } from "./account-store";
export { json };

export const VERSION = "0.1.0";

/**
 * - "rate-limit": no credential yet (login, refresh), limited per IP when RATE_LIMITER is bound;
 * - "session": the account access token (`Bearer <JWS>`) and a live session in the store, or —
 *   until the migration window closes — the legacy device session `Bearer hs1.…` (A65 §3.5);
 * - "account": the account access token, signature only (≤ 15 min, A65 §3.4);
 * - "account-live": the access token and a live session in the store (sensitive routes);
 * - "app": every app endpoint (A14 §5 AppAuth): the account access token, signature only;
 * - "webhook-hmac": WHOOP signature (slice 3).
 */
type Auth = "none" | "rate-limit" | "session" | "account" | "account-live" | "app" | "webhook-hmac" | "public";

interface Route {
  method: "GET" | "POST";
  /** Exact path, or a prefix ending with "/" for provider proxies. */
  path: string;
  auth: Auth;
  /** Slice of A14 §9 in which the endpoint is implemented. */
  slice: string;
  implemented?: boolean;
}

/**
 * Endpoints of A14 §5 (v0.3). `/whoop/refresh` is gone (D-025): the refresh token never leaves
 * the Durable Object, the phone asks `/whoop/access` instead.
 */
export const ROUTES: readonly Route[] = [
  // HoT account (A65 §10, D-062): slice А1 (core) and А2 (Google + WHOOP login).
  { method: "POST", path: "/auth/nonce", auth: "rate-limit", slice: "А1", implemented: true },
  { method: "POST", path: "/auth/google", auth: "rate-limit", slice: "А2", implemented: true },
  { method: "POST", path: "/auth/signup", auth: "rate-limit", slice: "А2", implemented: true },
  { method: "POST", path: "/auth/refresh", auth: "rate-limit", slice: "А1", implemented: true },
  { method: "POST", path: "/auth/migrate", auth: "rate-limit", slice: "А1", implemented: true },
  { method: "POST", path: "/auth/logout", auth: "account", slice: "А1", implemented: true },
  { method: "POST", path: "/auth/logout-all", auth: "account-live", slice: "А1", implemented: true },
  { method: "POST", path: "/auth/invites", auth: "account-live", slice: "А1", implemented: true },
  // Slice А3: identities, devices, step-up (A65 §2.3, §3.4).
  { method: "GET", path: "/auth/account", auth: "account-live", slice: "А3", implemented: true },
  { method: "POST", path: "/auth/devices/revoke", auth: "account-live", slice: "А3", implemented: true },
  { method: "POST", path: "/auth/reauth/google", auth: "account-live", slice: "А3", implemented: true },
  { method: "POST", path: "/auth/link/google", auth: "account-live", slice: "А3", implemented: true },
  { method: "POST", path: "/auth/unlink", auth: "account-live", slice: "А3", implemented: true },
  // Slice А5: deletion in the app and on the web page for Google Play (A65 §6).
  { method: "POST", path: "/auth/delete", auth: "account-live", slice: "А5", implemented: true },
  { method: "GET", path: "/account/delete", auth: "public", slice: "А5", implemented: true },
  { method: "GET", path: DELETE_PAGE_SCRIPT, auth: "public", slice: "А5", implemented: true },
  { method: "POST", path: "/account/delete", auth: "rate-limit", slice: "А5", implemented: true },
  { method: "POST", path: "/whoop/authorize", auth: "rate-limit", slice: "1", implemented: true },
  { method: "POST", path: "/whoop/token", auth: "rate-limit", slice: "1", implemented: true },
  { method: "POST", path: "/whoop/access", auth: "session", slice: "1", implemented: true },
  { method: "POST", path: "/whoop/disconnect", auth: "session", slice: "1", implemented: true },
  { method: "POST", path: "/whoop/webhook", auth: "webhook-hmac", slice: "3" },
  { method: "POST", path: "/push/register", auth: "app", slice: "3" },
  { method: "POST", path: "/push/ack", auth: "app", slice: "3" },
  { method: "POST", path: "/llm/", auth: "app", slice: "5a" }, // /llm/<provider>/…, e.g. /llm/anthropic/v1/messages
  { method: "POST", path: "/stt", auth: "app", slice: "5b" },
  { method: "POST", path: "/tts", auth: "app", slice: "5b" },
];

/** The routes of [path] (one per method). */
function matchRoutes(path: string): Route[] {
  return ROUTES.filter((r) => (r.path.endsWith("/") ? path.startsWith(r.path) && path.length > r.path.length : path === r.path));
}

/** Small JSON bodies only on the WHOOP and account routes. */
const MAX_JSON_BYTES = 16 * 1024;

async function readJson(request: Request): Promise<Record<string, unknown> | null> {
  const length = Number(request.headers.get("Content-Length") ?? "0");
  if (length > MAX_JSON_BYTES) return null;
  const text = await request.text().catch(() => "");
  if (text.length > MAX_JSON_BYTES) return null;
  try {
    const value: unknown = JSON.parse(text || "{}");
    return value && typeof value === "object" && !Array.isArray(value) ? (value as Record<string, unknown>) : null;
  } catch {
    return null;
  }
}

const notConfigured = () => json(503, { error: "not_configured" });
const invalidRequest = () => json(400, { error: "invalid_request" });

// ---------------------------------------------------------------------------------------------
// WHOOP routes

function whoopConfigured(env: Env): boolean {
  return Boolean(env.WHOOP_CLIENT_ID && env.WHOOP_CLIENT_SECRET && env.TOKEN_ENC_KEY && env.WHOOP_VAULT);
}

/**
 * For `intent=link | reauth` (A65 §2.2–2.3) the state is bound to the caller's account session;
 * `login` needs no session. Returns the binding, or the answer that refuses the request.
 */
async function stateBinding(request: Request, ctx: AuthContext | null, intent: unknown): Promise<{ bind: StateBinding; caller: LiveCaller | null } | Response> {
  if (intent === undefined || intent === null || intent === "login") return { bind: { intent: "login", binding: "" }, caller: null };
  if (!isStateIntent(intent)) return invalidRequest();
  if (!ctx) return notConfigured();
  const caller = await liveCaller(ctx, request);
  if (!caller) return unauthorized();
  return { bind: { intent, binding: await sessionBinding(caller.account.id, caller.session.sid) }, caller };
}

async function whoopAuthorize(request: Request, env: Env, nowSeconds: number, ctx: AuthContext | null): Promise<Response> {
  if (!whoopConfigured(env)) return notConfigured();
  const body = await readJson(request);
  if (!body || !isValidChallenge(body.code_challenge)) return invalidRequest();
  const bound = await stateBinding(request, ctx, body.intent);
  if (bound instanceof Response) return bound;
  const { bind, caller } = bound;
  if (caller && ctx) {
    const whoopId = whoopSubject(caller.account);
    // reauth: only an account with WHOOP; link of a new WHOOP: the step-up comes first (A65 §2.3)
    if (bind.intent === "reauth" && !whoopId) return json(409, { error: "whoop_not_linked" });
    if (bind.intent === "link" && !whoopId && !isFresh(ctx, caller.session)) return stepUpRequired();
  }
  const keys = await deriveKeys(env.TOKEN_ENC_KEY);
  const state = await issueState(keys.stateMac, body.code_challenge, nowSeconds, bind);
  const redirectUri = env.WHOOP_REDIRECT_URI || DEFAULT_REDIRECT_URI;
  const clientId = env.WHOOP_CLIENT_ID!;
  return json(200, {
    authorization_endpoint: WHOOP_AUTHORIZE_URL,
    client_id: clientId,
    redirect_uri: redirectUri,
    scope: WHOOP_SCOPES,
    state,
    authorize_url: authorizeUrl(clientId, redirectUri, state),
  });
}

/**
 * «Войти через WHOOP» (A65 §2.2): the PKCE + signed-state flow of slice 1, unchanged on the wire,
 * plus the device key. The answer is now an account session instead of `hs1`. A WHOOP user
 * without an account gets one with a valid invite, or when on ALLOWED_WHOOP_USER_IDS (the
 * founder, until the allowlist is retired); otherwise nothing is kept and the grant is revoked.
 */
async function whoopToken(request: Request, ctx: AuthContext, http?: typeof fetch): Promise<Response> {
  const { env, nowSeconds } = ctx;
  const body = await readJson(request);
  const code = body?.code;
  if (!body || typeof code !== "string" || code.length < 1 || code.length > 2048) return invalidRequest();
  const bound = await stateBinding(request, ctx, body.intent);
  if (bound instanceof Response) return bound;
  const keys = await deriveKeys(env.TOKEN_ENC_KEY);
  const check = await verifyState(keys.stateMac, body.state, body.code_verifier, nowSeconds, bound.bind);
  if (check === "expired") return json(400, { error: "state_expired" });
  if (check !== "ok") return json(400, { error: "invalid_state" });
  if (bound.caller) return whoopLinkOrReauth(ctx, bound.caller, bound.bind.intent, code, http);
  const device = await deviceProof(body, String(body.state));
  if (!device) return json(400, { error: "invalid_device_proof" });
  const invite = await inviteOf(body);
  if (!ctx.vault) return notConfigured();

  const whoop = new WhoopClient({
    clientId: env.WHOOP_CLIENT_ID!,
    clientSecret: env.WHOOP_CLIENT_SECRET!,
    redirectUri: env.WHOOP_REDIRECT_URI || DEFAULT_REDIRECT_URI,
    fetch: http,
  });
  try {
    const tokens = await whoop.exchangeCode(code);
    const userId = await whoop.userId(tokens.accessToken);
    if (!isValidUserId(userId)) return json(502, { error: "whoop_unavailable" });
    const login =
      invite === "invalid"
        ? ({ status: "invite_invalid" } as const)
        : await ctx.store.login({
            provider: "whoop",
            subject: userId,
            inviteHash: invite,
            bootstrap: isAllowedWhoopUser(userId, env),
            now: nowSeconds * 1000,
          });
    if (login.status !== "ok") {
      // No account and no (valid) invite: keep nothing, close the grant at WHOOP. The user id is
      // not a secret; the app shows it so the founder can invite this person.
      await whoop.revoke(tokens.accessToken);
      return json(403, { error: login.status === "no_account" ? "invite_required" : "invite_invalid", whoop_user_id: userId });
    }
    await ctx.vault.connect(userId, tokens);
    const session = await issueSession(ctx, login.account, device);
    await ctx.store.audit("login.whoop", login.account.id, nowSeconds * 1000);
    return json(200, {
      ...session,
      whoop_user_id: userId,
      whoop_access_token: tokens.accessToken,
      whoop_expires_at: tokens.expiresAt,
    });
  } catch (e) {
    if (e instanceof WhoopError) {
      if (e.kind === "invalid_grant") return json(400, { error: "invalid_code" });
      return json(502, { error: e.kind === "rejected" ? "whoop_rejected" : "whoop_unavailable" });
    }
    throw e;
  }
}

/**
 * `intent=link`: WHOOP joins the signed-in account (a Google account connects WHOOP, A65 §2.3);
 * the same WHOOP again just reconnects its tokens. `intent=reauth`: the step-up of a session with
 * the account's own WHOOP. No new session either way. A WHOOP that belongs to another account is
 * `409 identity_in_use`: its tokens are not stored, and its grant is left alone (it serves that
 * account); a grant of a WHOOP user that nobody owns is revoked.
 */
async function whoopLinkOrReauth(ctx: AuthContext, caller: LiveCaller, intent: "login" | "link" | "reauth", code: string, http?: typeof fetch): Promise<Response> {
  if (!ctx.vault) return notConfigured();
  const whoop = new WhoopClient({
    clientId: ctx.env.WHOOP_CLIENT_ID!,
    clientSecret: ctx.env.WHOOP_CLIENT_SECRET!,
    redirectUri: ctx.env.WHOOP_REDIRECT_URI || DEFAULT_REDIRECT_URI,
    fetch: http,
  });
  const now = ctx.nowSeconds * 1000;
  try {
    const tokens = await whoop.exchangeCode(code);
    const userId = await whoop.userId(tokens.accessToken);
    if (!isValidUserId(userId)) return json(502, { error: "whoop_unavailable" });
    const owner = await ctx.store.ownerOf("whoop", userId);
    const mine = owner === caller.account.id;
    const drop = async () => {
      if (!owner) await whoop.revoke(tokens.accessToken);
    };
    const whoopBody = { whoop_user_id: userId, whoop_access_token: tokens.accessToken, whoop_expires_at: tokens.expiresAt };
    if (intent === "reauth") {
      if (!mine) {
        await drop();
        return json(403, { error: "identity_mismatch" });
      }
      await ctx.vault.connect(userId, tokens);
      const session = await ctx.store.stepUp(caller.session.sid, now);
      if (!session) return unauthorized();
      return json(200, { step_up_until: stepUpUntil(session), ...whoopBody });
    }
    if (owner && !mine) return json(409, { error: "identity_in_use", provider: "whoop" });
    if (!mine && !isFresh(ctx, caller.session)) {
      await drop();
      return stepUpRequired();
    }
    const answer = await linkAnswer(ctx, caller, "whoop", userId, whoopBody);
    if (answer.status === 200) await ctx.vault.connect(userId, tokens);
    else await drop();
    return answer;
  } catch (e) {
    if (e instanceof WhoopError) {
      if (e.kind === "invalid_grant") return json(400, { error: "invalid_code" });
      return json(502, { error: e.kind === "rejected" ? "whoop_rejected" : "whoop_unavailable" });
    }
    throw e;
  }
}

const ACCESS_STATUS: Record<Exclude<AccessResult["status"], "ok">, [number, string]> = {
  unauthorized: [401, "unauthorized"],
  reauth_required: [401, "reauth_required"],
  not_allowed: [403, "not_allowed"],
  unavailable: [502, "whoop_unavailable"],
  misconfigured: [503, "not_configured"],
};

/** Who asks for WHOOP tokens: an account session (its WHOOP identity) or a legacy `hs1` session. */
type WhoopCaller = { kind: "account"; whoopUserId: string | null } | { kind: "legacy"; whoopUserId: string; hash: string } | null;

async function whoopCaller(request: Request, ctx: AuthContext | null): Promise<WhoopCaller> {
  const token = bearerToken(request);
  const legacyUser = sessionUserId(token);
  if (token && legacyUser) return { kind: "legacy", whoopUserId: legacyUser, hash: await sessionHash(token) };
  if (!ctx) return null;
  const caller = await liveCaller(ctx, request);
  return caller ? { kind: "account", whoopUserId: whoopSubject(caller.account) } : null;
}

async function whoopAccess(request: Request, vault: TokenVault, ctx: AuthContext | null): Promise<Response> {
  const caller = await whoopCaller(request, ctx);
  if (!caller) return unauthorized();
  if (!caller.whoopUserId) return json(409, { error: "whoop_not_linked" });
  const result =
    caller.kind === "legacy" ? await vault.legacyAccess(caller.whoopUserId, caller.hash) : await vault.access(caller.whoopUserId);
  if (result.status === "ok") return json(200, { access_token: result.accessToken, expires_at: result.expiresAt });
  const [status, error] = ACCESS_STATUS[result.status] ?? [502, "vault_unavailable"];
  return status === 401 ? unauthorized(error) : json(status, { error });
}

/**
 * «Отключить WHOOP»: the tokens are revoked and wiped; the WHOOP identity of the account stays
 * (logging in with WHOOP again reconnects). Unlinking identities is slice А3.
 */
async function whoopDisconnect(request: Request, vault: TokenVault, ctx: AuthContext | null): Promise<Response> {
  const caller = await whoopCaller(request, ctx);
  if (!caller) return unauthorized();
  if (!caller.whoopUserId) return noContent();
  const ok =
    caller.kind === "legacy" ? await vault.legacyDisconnect(caller.whoopUserId, caller.hash) : await vault.disconnect(caller.whoopUserId);
  return ok ? noContent() : unauthorized();
}

// ---------------------------------------------------------------------------------------------

export interface HandleOptions {
  /** Outbound fetch to WHOOP (tests). */
  fetch?: typeof fetch;
  nowSeconds?: number;
  /** Test doubles of the account store and the Google verifier. */
  store?: AccountStore;
  google?: GoogleIdTokenVerifier;
}

export async function handle(request: Request, env: Env, options: HandleOptions = {}): Promise<Response> {
  const { pathname } = new URL(request.url);
  const nowSeconds = options.nowSeconds ?? Math.floor(Date.now() / 1000);

  if (pathname === "/health") {
    if (request.method !== "GET") return json(405, { error: "method_not_allowed" }, { Allow: "GET" });
    return json(200, { status: "ok", service: "hot-edge", version: VERSION });
  }

  const routes = matchRoutes(pathname);
  if (routes.length === 0) return json(404, { error: "not_found" });
  const route = routes.find((r) => r.method === request.method);
  if (!route) return json(405, { error: "method_not_allowed" }, { Allow: routes.map((r) => r.method).join(", ") });

  // The web page of deletion (A65 §6): static, no cookies, no forms, nothing collected.
  if (route.auth === "public") {
    return route.path === DELETE_PAGE_SCRIPT ? deletePageScript() : deletePage(env.GOOGLE_WEB_CLIENT_ID);
  }

  if (route.auth === "rate-limit" && env.RATE_LIMITER) {
    const ip = request.headers.get("CF-Connecting-IP") ?? "unknown";
    const { success } = await env.RATE_LIMITER.limit({ key: `${route.path}|${ip}` });
    if (!success) return json(429, { error: "rate_limited" }, { "Retry-After": "60" });
  }

  const ctx = authContext(env, nowSeconds, options);
  try {
    if (route.path.startsWith("/auth/")) {
      if (!ctx) return notConfigured();
      switch (route.path) {
        case "/auth/nonce":
          return await authNonce(ctx);
        case "/auth/logout":
          return await authLogout(ctx, request);
        case "/auth/logout-all":
          return await authLogoutAll(ctx, request);
        case "/auth/invites":
          return await authInvites(ctx, request);
        case "/auth/account":
          return await authAccount(ctx, request);
        case "/auth/delete":
          return await authDelete(ctx, request);
      }
      const body = await readJson(request);
      if (!body) return invalidRequest();
      switch (route.path) {
        case "/auth/google":
          return await authGoogle(ctx, body);
        case "/auth/signup":
          return await authSignup(ctx, body);
        case "/auth/refresh":
          return await authRefresh(ctx, body);
        case "/auth/migrate":
          return await authMigrate(ctx, request, body);
        case "/auth/devices/revoke":
          return await authRevokeDevice(ctx, request, body);
        case "/auth/reauth/google":
          return await authReauthGoogle(ctx, request, body);
        case "/auth/link/google":
          return await authLinkGoogle(ctx, request, body);
        case "/auth/unlink":
          return await authUnlink(ctx, request, body);
      }
    }

    if (route.path === "/account/delete") {
      if (!ctx) return notConfigured();
      const body = await readJson(request);
      if (!body) return invalidRequest();
      return await webDelete(ctx, body);
    }

    switch (route.path) {
      case "/whoop/authorize":
        return await whoopAuthorize(request, env, nowSeconds, ctx);
      case "/whoop/token":
        if (!whoopConfigured(env) || !ctx) return notConfigured();
        return await whoopToken(request, ctx, options.fetch);
      case "/whoop/access":
      case "/whoop/disconnect": {
        if (!whoopConfigured(env)) return notConfigured();
        // Without the account secrets the legacy `hs1` calls still work during the rollout.
        const vault = ctx?.vault ?? new DurableTokenVault(env.WHOOP_VAULT!);
        return route.path === "/whoop/access" ? await whoopAccess(request, vault, ctx) : await whoopDisconnect(request, vault, ctx);
      }
    }

    // AppAuth (A14 §5, A65 §3.4): a valid account access token is required before anything else,
    // also for endpoints that are still stubs.
    if (route.auth === "app") {
      if (!ctx || !(await accessClaims(ctx, request))) return unauthorized();
    }
  } catch (e) {
    if (e instanceof ConfigError || e instanceof NotConfigured) return notConfigured();
    return json(502, { error: "upstream_error" });
  }

  return json(501, { error: "not_implemented", slice: route.slice });
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const started = Date.now();
    const response = await handle(request, env);
    // Log only method, status and duration — never bodies, tokens, user ids or query strings.
    console.log(JSON.stringify({ m: request.method, s: response.status, ms: Date.now() - started }));
    return response;
  },
} satisfies ExportedHandler<Env>;
