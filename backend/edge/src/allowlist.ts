import type { Env } from "./env";

/** Comma/whitespace separated list from a var or secret; empty → []. */
export function parseList(raw: string | undefined): string[] {
  if (!raw) return [];
  return raw
    .split(/[\s,]+/)
    .map((s) => s.trim())
    .filter((s) => s.length > 0);
}

/**
 * AppAuth allowlist (A14 §5, ADR-04): only WHOOP user ids listed in ALLOWED_WHOOP_USER_IDS may
 * use the Worker. Fails closed: an empty or missing list allows nobody.
 * Slice 1 adds the token → user_id lookup (WHOOP profile) with a 50-minute KV cache.
 */
export function isAllowedWhoopUser(userId: string | number | undefined, env: Env): boolean {
  if (userId === undefined || userId === "") return false;
  return parseList(env.ALLOWED_WHOOP_USER_IDS).includes(String(userId));
}

/** LLM proxy allowlist (A14 §5): only models from LLM_MODELS. Fails closed. */
export function isAllowedModel(model: unknown, env: Env): boolean {
  return typeof model === "string" && parseList(env.LLM_MODELS).includes(model);
}

/** Extracts the bearer token, or null. The token itself is never logged. */
export function bearerToken(request: Request): string | null {
  const header = request.headers.get("Authorization") ?? "";
  const match = /^Bearer\s+(\S+)$/i.exec(header);
  return match?.[1] ?? null;
}
