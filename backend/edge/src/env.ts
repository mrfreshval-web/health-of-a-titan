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
  /** Founder bootstrap until invites replace it (A65 §2.5); removed together with /auth/migrate. */
  ALLOWED_WHOOP_USER_IDS?: string;
  /** 32 random bytes, base64 (`openssl rand -base64 32`): root key for token sealing, OAuth state, nonces, refresh hashes. */
  TOKEN_ENC_KEY?: string;
  /** Ed25519 private JWK with `kid` that signs account access tokens (A65 §3.1, §8). */
  AUTH_SIGNING_KEY?: string;
  /** Optional: the previous signing key after a rotation, accepted until its tokens expire. */
  AUTH_SIGNING_KEY_PREVIOUS?: string;
  /** Google OAuth Web client id = `aud` of Google ID tokens (A65 §2.1). Not secret, but kept out of code. */
  GOOGLE_WEB_CLIENT_ID?: string;
  ANTHROPIC_API_KEY?: string;
  GOOGLE_TTS_API_KEY?: string;
  FCM_SERVICE_ACCOUNT_JSON?: string;

  // bindings
  AI?: Ai;
  HOT_KV?: KVNamespace;
  /** Durable Object: the single owner of each user's WHOOP refresh token (D-025). */
  WHOOP_VAULT?: DurableObjectNamespace;
  /** Durable Object: the account store — accounts, identities, sessions, invites (A65 §4.1). */
  ACCOUNT_VAULT?: DurableObjectNamespace;
  /** Workers rate limiting binding for the unauthenticated OAuth routes (optional). */
  RATE_LIMITER?: RateLimit;
}
