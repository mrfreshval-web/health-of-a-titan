/**
 * Worker bindings (A14 §5). Secrets are optional at type level: each endpoint checks what it
 * needs and answers 503 `not_configured` instead of failing obscurely.
 */
export interface Env {
  // vars (wrangler.toml)
  LLM_MODELS?: string;
  DAILY_TOKEN_CAP?: string;
  FCM_PROJECT_ID?: string;
  /** Custom-scheme redirect registered in the WHOOP app (A14 §10 step 3). */
  WHOOP_REDIRECT_URI?: string;

  // secrets (wrangler secret put / GitHub Secrets) — never in code
  WHOOP_CLIENT_ID?: string;
  WHOOP_CLIENT_SECRET?: string;
  ALLOWED_WHOOP_USER_IDS?: string;
  /** 32 random bytes, base64 (`openssl rand -base64 32`): root key for token sealing and OAuth state. */
  TOKEN_ENC_KEY?: string;
  ANTHROPIC_API_KEY?: string;
  GOOGLE_TTS_API_KEY?: string;
  FCM_SERVICE_ACCOUNT_JSON?: string;

  // bindings
  AI?: Ai;
  HOT_KV?: KVNamespace;
  /** Durable Object: the single owner of each user's WHOOP refresh token (D-025). */
  WHOOP_VAULT?: DurableObjectNamespace;
  /** Workers rate limiting binding for the unauthenticated OAuth routes (optional). */
  RATE_LIMITER?: RateLimit;
}
