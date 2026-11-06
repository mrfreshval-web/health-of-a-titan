/**
 * WHOOP OAuth and the two API calls the Worker needs (A14 §5, §10). The Worker never reads
 * health data: only the token endpoint, the basic profile (for `user_id`) and access revocation.
 *
 * [Факт: developer.whoop.com/docs/developing/oauth] authorize and token URLs, `scope=offline` on
 * refresh, refresh tokens rotate (the previous access token dies on refresh), `expires_in` in s.
 * [Проверить на первом входе] v2 paths of the profile and of revocation.
 */

export const WHOOP_AUTHORIZE_URL = "https://api.prod.whoop.com/oauth/oauth2/auth";
export const WHOOP_TOKEN_URL = "https://api.prod.whoop.com/oauth/oauth2/token";
export const WHOOP_PROFILE_URL = "https://api.prod.whoop.com/developer/v2/user/profile/basic";
export const WHOOP_REVOKE_URL = "https://api.prod.whoop.com/developer/v2/user/access";

/** A14 §10 step 3. `offline` is required for a refresh token. */
export const WHOOP_SCOPES = "offline read:recovery read:cycles read:sleep read:workout read:profile read:body_measurement";

export const DEFAULT_REDIRECT_URI = "com.healthofatitan.app://oauth/whoop";

export interface WhoopTokens {
  accessToken: string;
  refreshToken: string;
  /** epoch ms */
  expiresAt: number;
  scope: string;
}

/**
 * Upstream failure. `kind` decides what the caller does:
 *  - "invalid_grant": the refresh token or code is dead → wipe and ask the user to log in again;
 *  - "rejected": WHOOP refused our request for another reason (e.g. `invalid_client` after a
 *    wrong WHOOP_CLIENT_SECRET) → keep the user's tokens, it is our configuration;
 *  - "unavailable": WHOOP or the network failed → keep everything, the phone retries later.
 * No token or body text is kept in the error.
 */
export class WhoopError extends Error {
  constructor(
    readonly kind: "invalid_grant" | "rejected" | "unavailable",
    readonly status: number,
  ) {
    super(`whoop ${kind} (${status})`);
    this.name = "WhoopError";
  }
}

export interface WhoopClientConfig {
  clientId: string;
  clientSecret: string;
  redirectUri: string;
  fetch?: typeof fetch;
  now?: () => number;
}

export class WhoopClient {
  private readonly http: typeof fetch;
  private readonly now: () => number;

  constructor(private readonly config: WhoopClientConfig) {
    this.http = config.fetch ?? ((input, init) => fetch(input, init));
    this.now = config.now ?? Date.now;
  }

  exchangeCode(code: string): Promise<WhoopTokens> {
    return this.token({
      grant_type: "authorization_code",
      code,
      redirect_uri: this.config.redirectUri,
    });
  }

  refresh(refreshToken: string): Promise<WhoopTokens> {
    return this.token({ grant_type: "refresh_token", refresh_token: refreshToken, scope: "offline" });
  }

  /** WHOOP `user_id` of the token owner. Name and e-mail of the profile are dropped at once. */
  async userId(accessToken: string): Promise<string> {
    let res: Response;
    try {
      res = await this.http(WHOOP_PROFILE_URL, { headers: { Authorization: `Bearer ${accessToken}`, Accept: "application/json" } });
    } catch {
      throw new WhoopError("unavailable", 0);
    }
    if (res.status === 401 || res.status === 403) throw new WhoopError("invalid_grant", res.status);
    if (!res.ok) throw new WhoopError("unavailable", res.status);
    const body = (await res.json().catch(() => null)) as { user_id?: unknown } | null;
    const id = body?.user_id;
    if ((typeof id !== "number" && typeof id !== "string") || String(id).length === 0) {
      throw new WhoopError("unavailable", res.status);
    }
    return String(id);
  }

  /** Best effort: the user's grant for this app is revoked at WHOOP. Never throws. */
  async revoke(accessToken: string): Promise<boolean> {
    try {
      const res = await this.http(WHOOP_REVOKE_URL, { method: "DELETE", headers: { Authorization: `Bearer ${accessToken}` } });
      return res.ok;
    } catch {
      return false;
    }
  }

  private async token(params: Record<string, string>): Promise<WhoopTokens> {
    const body = new URLSearchParams({ ...params, client_id: this.config.clientId, client_secret: this.config.clientSecret });
    let res: Response;
    try {
      res = await this.http(WHOOP_TOKEN_URL, {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded", Accept: "application/json" },
        body,
      });
    } catch {
      throw new WhoopError("unavailable", 0);
    }
    if (res.status >= 400 && res.status < 500) {
      const error = ((await res.json().catch(() => null)) as { error?: unknown } | null)?.error;
      throw new WhoopError(error === "invalid_grant" ? "invalid_grant" : "rejected", res.status);
    }
    if (!res.ok) throw new WhoopError("unavailable", res.status);
    const json = (await res.json().catch(() => null)) as Record<string, unknown> | null;
    const access = json?.access_token;
    const refresh = json?.refresh_token;
    const expiresIn = Number(json?.expires_in);
    if (typeof access !== "string" || typeof refresh !== "string" || !access || !refresh || !Number.isFinite(expiresIn)) {
      throw new WhoopError("unavailable", res.status);
    }
    return {
      accessToken: access,
      refreshToken: refresh,
      expiresAt: this.now() + expiresIn * 1000,
      scope: typeof json?.scope === "string" ? json.scope : "",
    };
  }
}

export function authorizeUrl(clientId: string, redirectUri: string, state: string): string {
  const url = new URL(WHOOP_AUTHORIZE_URL);
  url.searchParams.set("response_type", "code");
  url.searchParams.set("client_id", clientId);
  url.searchParams.set("redirect_uri", redirectUri);
  url.searchParams.set("scope", WHOOP_SCOPES);
  url.searchParams.set("state", state);
  return url.toString();
}
