import { bearerToken, isAllowedWhoopUser } from "./allowlist";
import { ConfigError, deriveKeys } from "./crypto";
import type { Env } from "./env";
import { isValidChallenge, issueState, verifyState } from "./oauth-state";
import { isValidUserId, sessionHash, sessionUserId } from "./session";
import type { AccessResult } from "./vault";
import { MentorMeter, type BudgetState } from "./mentor/budget";
import { mentorTurn, parseMentorRequest, type Meter } from "./mentor/mentor";
import { authorizeUrl, DEFAULT_REDIRECT_URI, WHOOP_AUTHORIZE_URL, WHOOP_SCOPES, WhoopClient, WhoopError } from "./whoop";

export { WhoopVault } from "./vault";
export { MentorMeter };

export const VERSION = "0.1.0";

/**
 * - "rate-limit": no credential yet (login), limited per IP when RATE_LIMITER is bound;
 * - "session": the phone's device session (`Bearer hs1.…`), verified by the user's vault;
 * - "app": the same session plus the allowlist — every future app endpoint (A14 §5 AppAuth);
 * - "webhook-hmac": WHOOP signature (slice 3).
 */
type Auth = "none" | "rate-limit" | "session" | "app" | "webhook-hmac";

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
  { method: "POST", path: "/whoop/authorize", auth: "rate-limit", slice: "1", implemented: true },
  { method: "POST", path: "/whoop/token", auth: "rate-limit", slice: "1", implemented: true },
  { method: "POST", path: "/whoop/access", auth: "session", slice: "1", implemented: true },
  { method: "POST", path: "/whoop/disconnect", auth: "session", slice: "1", implemented: true },
  { method: "POST", path: "/whoop/webhook", auth: "webhook-hmac", slice: "3" },
  { method: "POST", path: "/push/register", auth: "app", slice: "3" },
  { method: "POST", path: "/push/ack", auth: "app", slice: "3" },
  // Винс v1 (A62, D-056): one turn of the conversation, routed by topic, with the money meter.
  { method: "POST", path: "/llm/mentor", auth: "app", slice: "5a", implemented: true },
  { method: "POST", path: "/llm/", auth: "app", slice: "5a" }, // /llm/<provider>/…, e.g. /llm/anthropic/v1/messages
  { method: "POST", path: "/stt", auth: "app", slice: "5b" },
  { method: "POST", path: "/tts", auth: "app", slice: "5b" },
];

const BASE_HEADERS = {
  "Content-Type": "application/json; charset=utf-8",
  "Cache-Control": "no-store",
  "X-Content-Type-Options": "nosniff",
} as const;

export function json(status: number, body: unknown, extra: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(body), { status, headers: { ...BASE_HEADERS, ...extra } });
}

const unauthorized = (error = "unauthorized") => json(401, { error }, { "WWW-Authenticate": "Bearer" });

function matchRoute(path: string): Route | undefined {
  return ROUTES.find((r) => (r.path.endsWith("/") ? path.startsWith(r.path) && path.length > r.path.length : path === r.path));
}

/** Small JSON bodies only on the WHOOP routes. */
const MAX_JSON_BYTES = 8 * 1024;

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

// ---------------------------------------------------------------------------------------------
// Vault access (Worker → Durable Object)

interface VaultClient {
  call<T>(op: "connect" | "access" | "verify" | "disconnect", body: unknown): Promise<T>;
}

function vaultFor(env: Env, userId: string): VaultClient | null {
  const ns = env.WHOOP_VAULT;
  if (!ns) return null;
  const stub = ns.get(ns.idFromName(`whoop-user:${userId}`));
  return {
    async call<T>(op: string, body: unknown): Promise<T> {
      const res = await stub.fetch(`https://vault/${op}`, { method: "POST", body: JSON.stringify(body) });
      return (await res.json()) as T;
    },
  };
}

/** Resolves the device session to its WHOOP user id, or null. */
async function authenticateSession(request: Request, env: Env): Promise<{ userId: string; hash: string; vault: VaultClient } | null> {
  const token = bearerToken(request);
  const userId = sessionUserId(token);
  if (!token || !userId) return null;
  const vault = vaultFor(env, userId);
  if (!vault) return null;
  return { userId, hash: await sessionHash(token), vault };
}

// ---------------------------------------------------------------------------------------------
// WHOOP routes

function whoopConfigured(env: Env): boolean {
  return Boolean(env.WHOOP_CLIENT_ID && env.WHOOP_CLIENT_SECRET && env.TOKEN_ENC_KEY && env.WHOOP_VAULT);
}

const notConfigured = () => json(503, { error: "not_configured" });

async function whoopAuthorize(request: Request, env: Env, nowSeconds: number): Promise<Response> {
  if (!whoopConfigured(env)) return notConfigured();
  const body = await readJson(request);
  if (!body || !isValidChallenge(body.code_challenge)) return json(400, { error: "invalid_request" });
  const keys = await deriveKeys(env.TOKEN_ENC_KEY);
  const state = await issueState(keys.stateMac, body.code_challenge, nowSeconds);
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

async function whoopToken(request: Request, env: Env, nowSeconds: number, http?: typeof fetch): Promise<Response> {
  if (!whoopConfigured(env)) return notConfigured();
  const body = await readJson(request);
  const code = body?.code;
  if (!body || typeof code !== "string" || code.length < 1 || code.length > 2048) return json(400, { error: "invalid_request" });
  const keys = await deriveKeys(env.TOKEN_ENC_KEY);
  const check = await verifyState(keys.stateMac, body.state, body.code_verifier, nowSeconds);
  if (check === "expired") return json(400, { error: "state_expired" });
  if (check !== "ok") return json(400, { error: "invalid_state" });

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
    if (!isAllowedWhoopUser(userId, env)) {
      // Not on the allowlist: keep nothing and close the grant at WHOOP. The user id is not a
      // secret; the app shows it so the founder can add it to ALLOWED_WHOOP_USER_IDS.
      await whoop.revoke(tokens.accessToken);
      return json(403, { error: "not_allowed", whoop_user_id: userId });
    }
    const vault = vaultFor(env, userId)!;
    const { session } = await vault.call<{ session?: string }>("connect", { userId, tokens });
    if (!session) return json(502, { error: "vault_unavailable" });
    return json(200, { session, access_token: tokens.accessToken, expires_at: tokens.expiresAt, whoop_user_id: userId });
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

async function whoopAccess(request: Request, env: Env): Promise<Response> {
  if (!whoopConfigured(env)) return notConfigured();
  const auth = await authenticateSession(request, env);
  if (!auth) return unauthorized();
  const result = await auth.vault.call<AccessResult>("access", { sessionHash: auth.hash });
  if (result.status === "ok") return json(200, { access_token: result.accessToken, expires_at: result.expiresAt });
  const [status, error] = ACCESS_STATUS[result.status] ?? [502, "vault_unavailable"];
  return status === 401 ? unauthorized(error) : json(status, { error });
}

async function whoopDisconnect(request: Request, env: Env): Promise<Response> {
  if (!whoopConfigured(env)) return notConfigured();
  const auth = await authenticateSession(request, env);
  if (!auth) return unauthorized();
  const { ok } = await auth.vault.call<{ ok?: boolean }>("disconnect", { sessionHash: auth.hash });
  return ok ? new Response(null, { status: 204, headers: { "Cache-Control": "no-store" } }) : unauthorized();
}

// ---------------------------------------------------------------------------------------------
// Винс (A62): /llm/mentor

/** Larger than the WHOOP bodies: a conversation window plus the ContextPack (A62 §4). */
const MAX_MENTOR_BYTES = 64 * 1024;

function meterFor(env: Env, userId: string): Meter | null {
  const ns = env.MENTOR_METER;
  if (!ns) return null;
  const stub = ns.get(ns.idFromName(`mentor-user:${userId}`));
  const call = async (op: string, body: unknown): Promise<BudgetState> => {
    const res = await stub.fetch(`https://meter/${op}`, { method: "POST", body: JSON.stringify(body) });
    return ((await res.json()) as { state: BudgetState }).state;
  };
  return { reserve: () => call("reserve", {}), charge: (usd) => call("charge", { usd }) };
}

async function mentor(request: Request, env: Env, userId: string, http?: typeof fetch): Promise<Response> {
  const length = Number(request.headers.get("Content-Length") ?? "0");
  if (length > MAX_MENTOR_BYTES) return json(413, { error: "too_long" });
  const text = await request.text().catch(() => "");
  if (text.length > MAX_MENTOR_BYTES) return json(413, { error: "too_long" });
  let body: Record<string, unknown> | null = null;
  try {
    const value: unknown = JSON.parse(text || "{}");
    body = value && typeof value === "object" && !Array.isArray(value) ? (value as Record<string, unknown>) : null;
  } catch {
    body = null;
  }
  const parsed = parseMentorRequest(body);
  if (!parsed.ok) return json(400, { error: parsed.error });
  const meter = meterFor(env, userId);
  // Without the meter there is no money limit: refuse rather than spend without a ceiling (D-056).
  if (!meter) return json(503, { error: "not_configured", missing: "MENTOR_METER" });
  const result = await mentorTurn(parsed.value, env, { meter, fetch: http });
  return json(result.status, result.body);
}

// ---------------------------------------------------------------------------------------------

export interface HandleOptions {
  /** Outbound fetch to WHOOP (tests). */
  fetch?: typeof fetch;
  nowSeconds?: number;
}

export async function handle(request: Request, env: Env, options: HandleOptions = {}): Promise<Response> {
  const { pathname } = new URL(request.url);
  const nowSeconds = options.nowSeconds ?? Math.floor(Date.now() / 1000);

  if (pathname === "/health") {
    if (request.method !== "GET") return json(405, { error: "method_not_allowed" }, { Allow: "GET" });
    return json(200, { status: "ok", service: "hot-edge", version: VERSION });
  }

  const route = matchRoute(pathname);
  if (!route) return json(404, { error: "not_found" });
  if (request.method !== route.method) {
    return json(405, { error: "method_not_allowed" }, { Allow: route.method });
  }

  if (route.auth === "rate-limit" && env.RATE_LIMITER) {
    const ip = request.headers.get("CF-Connecting-IP") ?? "unknown";
    const { success } = await env.RATE_LIMITER.limit({ key: `${route.path}|${ip}` });
    if (!success) return json(429, { error: "rate_limited" }, { "Retry-After": "60" });
  }

  try {
    switch (route.path) {
      case "/whoop/authorize":
        return await whoopAuthorize(request, env, nowSeconds);
      case "/whoop/token":
        return await whoopToken(request, env, nowSeconds, options.fetch);
      case "/whoop/access":
        return await whoopAccess(request, env);
      case "/whoop/disconnect":
        return await whoopDisconnect(request, env);
    }

    // AppAuth (A14 §5, refined in slice 1): a valid device session of an allowed user is required
    // before anything else, also for endpoints that are still stubs.
    if (route.auth === "app") {
      const auth = await authenticateSession(request, env);
      if (!auth) return unauthorized();
      const { userId } = await auth.vault.call<{ userId: string | null }>("verify", { sessionHash: auth.hash });
      if (!userId) return unauthorized();
      if (route.path === "/llm/mentor") return await mentor(request, env, userId, options.fetch);
    }
  } catch (e) {
    if (e instanceof ConfigError) return notConfigured();
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
