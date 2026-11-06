import { bearerToken } from "./allowlist";
import type { Env } from "./env";

export const VERSION = "0.0.1";

type Auth = "none" | "rate-limit" | "webhook-hmac" | "app";

interface Route {
  method: "GET" | "POST";
  /** Exact path, or a prefix ending with "/" for provider proxies. */
  path: string;
  auth: Auth;
  /** Slice of A14 §9 in which the endpoint is implemented. */
  slice: string;
}

/** Endpoints of A14 §5. Everything except /health is a 501 stub in slice 0. */
export const ROUTES: readonly Route[] = [
  { method: "POST", path: "/whoop/token", auth: "rate-limit", slice: "1" },
  { method: "POST", path: "/whoop/refresh", auth: "rate-limit", slice: "1" },
  { method: "POST", path: "/whoop/webhook", auth: "webhook-hmac", slice: "3" },
  { method: "POST", path: "/push/register", auth: "app", slice: "3" },
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

function matchRoute(path: string): Route | undefined {
  return ROUTES.find((r) => (r.path.endsWith("/") ? path.startsWith(r.path) && path.length > r.path.length : path === r.path));
}

export async function handle(request: Request, env: Env): Promise<Response> {
  const { pathname } = new URL(request.url);

  if (pathname === "/health") {
    if (request.method !== "GET") return json(405, { error: "method_not_allowed" }, { Allow: "GET" });
    return json(200, { status: "ok", service: "hot-edge", version: VERSION });
  }

  const route = matchRoute(pathname);
  if (!route) return json(404, { error: "not_found" });
  if (request.method !== route.method) {
    return json(405, { error: "method_not_allowed" }, { Allow: route.method });
  }

  // AppAuth (A14 §5): a WHOOP bearer token is required before anything else. Allowlist
  // verification of the token's user_id arrives in slice 1 (see allowlist.ts).
  if (route.auth === "app" && bearerToken(request) === null) {
    return json(401, { error: "unauthorized" }, { "WWW-Authenticate": "Bearer" });
  }

  void env; // secrets are not read in slice 0
  return json(501, { error: "not_implemented", slice: route.slice });
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const started = Date.now();
    const response = await handle(request, env);
    // Log only method, route status and duration — never bodies, tokens or query strings.
    console.log(JSON.stringify({ m: request.method, s: response.status, ms: Date.now() - started }));
    return response;
  },
} satisfies ExportedHandler<Env>;
