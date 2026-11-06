/**
 * Worker bindings (A14 §5). Secrets are optional at type level: slice 0 runs without any of
 * them, and each endpoint checks what it needs when it is implemented.
 */
export interface Env {
  // vars (wrangler.toml)
  LLM_MODELS?: string;
  DAILY_TOKEN_CAP?: string;
  FCM_PROJECT_ID?: string;

  // secrets (wrangler secret put / GitHub Secrets) — never in code
  WHOOP_CLIENT_ID?: string;
  WHOOP_CLIENT_SECRET?: string;
  ALLOWED_WHOOP_USER_IDS?: string;
  ANTHROPIC_API_KEY?: string;
  GOOGLE_TTS_API_KEY?: string;
  FCM_SERVICE_ACCOUNT_JSON?: string;

  // bindings
  AI?: Ai;
  HOT_KV?: KVNamespace;
}
