import { isAllowedWhoopUser } from "./allowlist";
import { ConfigError, deriveKeys, open, seal, timingSafeEqual } from "./crypto";
import type { Env } from "./env";
import { newSessionToken, sessionHash } from "./session";
import { DEFAULT_REDIRECT_URI, WhoopClient, WhoopError, type WhoopTokens } from "./whoop";

/**
 * Durable Object `WhoopVault` — the single owner of a user's WHOOP refresh token (D-025,
 * A18 И1.1a). One instance per WHOOP user (`idFromName("whoop-user:<id>")`).
 *
 *  - Tokens are stored sealed with AES-256-GCM (key derived from TOKEN_ENC_KEY); the AAD binds
 *    each value to its user and field, so a sealed value cannot be moved elsewhere.
 *  - Refresh is single-flight: concurrent `/access` calls share one WHOOP refresh. This matters
 *    because WHOOP rotates refresh tokens — two parallel refreshes would kill the session.
 *  - The phone authenticates with its session token; only its SHA-256 is stored.
 *  - Nothing here logs; errors leave the object as plain status codes.
 *
 * Internal protocol (Worker → DO only, never exposed): `POST https://vault/<op>` with JSON.
 */

export interface VaultRecord {
  v: 1;
  userId: string;
  refresh: string; // sealed
  access: string; // sealed
  accessExpiresAt: number; // epoch ms
  sessionHash: string;
  connectedAt: number;
  refreshedAt: number;
}

export type AccessResult =
  | { status: "ok"; accessToken: string; expiresAt: number }
  | { status: "unauthorized" | "reauth_required" | "not_allowed" | "unavailable" | "misconfigured" };

/** The subset of DurableObjectStorage the vault uses (lets tests use an in-memory map). */
export interface VaultStorage {
  get<T>(key: string): Promise<T | undefined>;
  put<T>(key: string, value: T): Promise<void>;
  deleteAll(): Promise<void>;
}

export interface VaultDeps {
  fetch?: typeof fetch;
  now?: () => number;
}

const RECORD_KEY = "vault";
/** Hand out an access token only if it lives at least this long; otherwise refresh first. */
export const ACCESS_MIN_TTL_MS = 5 * 60 * 1000;

export class WhoopVault {
  private readonly storage: VaultStorage;
  private readonly now: () => number;
  private inflight: Promise<AccessResult> | null = null;

  constructor(
    state: { storage: VaultStorage },
    private readonly env: Env,
    private readonly deps: VaultDeps = {},
  ) {
    this.storage = state.storage;
    this.now = deps.now ?? Date.now;
  }

  /** Durable Object entry point (internal protocol). */
  async fetch(request: Request): Promise<Response> {
    const op = new URL(request.url).pathname;
    const body = (await request.json().catch(() => ({}))) as Record<string, unknown>;
    try {
      switch (op) {
        case "/connect": {
          const session = await this.connect(String(body.userId), body.tokens as WhoopTokens);
          return Response.json({ session });
        }
        case "/access":
          return Response.json(await this.access(String(body.sessionHash)));
        case "/verify":
          return Response.json({ userId: await this.verify(String(body.sessionHash)) });
        case "/disconnect":
          return Response.json({ ok: await this.disconnect(String(body.sessionHash)) });
        default:
          return Response.json({ error: "not_found" }, { status: 404 });
      }
    } catch (e) {
      const status = e instanceof ConfigError ? "misconfigured" : "unavailable";
      return Response.json({ status }, { status: 500 });
    }
  }

  private client(): WhoopClient {
    const { WHOOP_CLIENT_ID, WHOOP_CLIENT_SECRET } = this.env;
    if (!WHOOP_CLIENT_ID || !WHOOP_CLIENT_SECRET) throw new ConfigError("WHOOP client is not configured");
    return new WhoopClient({
      clientId: WHOOP_CLIENT_ID,
      clientSecret: WHOOP_CLIENT_SECRET,
      redirectUri: this.env.WHOOP_REDIRECT_URI || DEFAULT_REDIRECT_URI,
      fetch: this.deps.fetch,
      now: this.now,
    });
  }

  private async key(): Promise<CryptoKey> {
    return (await deriveKeys(this.env.TOKEN_ENC_KEY)).tokenAead;
  }

  private aad(userId: string, field: "refresh" | "access"): string {
    return `hot-edge|whoop-vault|v1|${userId}|${field}`;
  }

  /** Stores fresh tokens after login and returns a new session token (old session dies). */
  async connect(userId: string, tokens: WhoopTokens): Promise<string> {
    const key = await this.key();
    const session = newSessionToken(userId);
    const now = this.now();
    const record: VaultRecord = {
      v: 1,
      userId,
      refresh: await seal(key, tokens.refreshToken, this.aad(userId, "refresh")),
      access: await seal(key, tokens.accessToken, this.aad(userId, "access")),
      accessExpiresAt: tokens.expiresAt,
      sessionHash: await sessionHash(session),
      connectedAt: now,
      refreshedAt: now,
    };
    this.inflight = null;
    await this.storage.put(RECORD_KEY, record);
    return session;
  }

  private async authorized(hash: string): Promise<VaultRecord | null> {
    const record = await this.storage.get<VaultRecord>(RECORD_KEY);
    if (!record || !timingSafeEqual(record.sessionHash, hash)) return null;
    return record;
  }

  async verify(hash: string): Promise<string | null> {
    const record = await this.authorized(hash);
    return record && isAllowedWhoopUser(record.userId, this.env) ? record.userId : null;
  }

  /** A valid access token for the phone; refreshes once for all concurrent callers. */
  async access(hash: string): Promise<AccessResult> {
    const record = await this.authorized(hash);
    if (!record) return { status: "unauthorized" };
    if (!isAllowedWhoopUser(record.userId, this.env)) return { status: "not_allowed" };
    if (record.accessExpiresAt - this.now() > ACCESS_MIN_TTL_MS) {
      return { status: "ok", accessToken: await open(await this.key(), record.access, this.aad(record.userId, "access")), expiresAt: record.accessExpiresAt };
    }
    if (!this.inflight) {
      this.inflight = this.refresh(record).finally(() => {
        this.inflight = null;
      });
    }
    return this.inflight;
  }

  private async refresh(stale: VaultRecord): Promise<AccessResult> {
    const key = await this.key();
    // Re-read inside the single flight: a caller that loaded the record before the previous
    // refresh finished must not spend the already-rotated refresh token.
    const record = await this.storage.get<VaultRecord>(RECORD_KEY);
    if (!record || record.sessionHash !== stale.sessionHash) return { status: "unauthorized" };
    if (record.accessExpiresAt - this.now() > ACCESS_MIN_TTL_MS) {
      return { status: "ok", accessToken: await open(key, record.access, this.aad(record.userId, "access")), expiresAt: record.accessExpiresAt };
    }
    const refreshToken = await open(key, record.refresh, this.aad(record.userId, "refresh"));
    let tokens: WhoopTokens;
    try {
      tokens = await this.client().refresh(refreshToken);
    } catch (e) {
      if (e instanceof WhoopError && e.kind === "invalid_grant") {
        // The grant is gone at WHOOP (revoked, expired or rotated elsewhere): nothing to keep.
        await this.storage.deleteAll();
        return { status: "reauth_required" };
      }
      if (e instanceof ConfigError) return { status: "misconfigured" };
      return { status: "unavailable" };
    }
    const updated: VaultRecord = {
      ...record,
      refresh: await seal(key, tokens.refreshToken, this.aad(record.userId, "refresh")),
      access: await seal(key, tokens.accessToken, this.aad(record.userId, "access")),
      accessExpiresAt: tokens.expiresAt,
      refreshedAt: this.now(),
    };
    // Persist the rotated refresh token before anyone sees the new access token.
    await this.storage.put(RECORD_KEY, updated);
    return { status: "ok", accessToken: tokens.accessToken, expiresAt: tokens.expiresAt };
  }

  /** «Отключить»: revokes the grant at WHOOP (best effort) and wipes the vault. */
  async disconnect(hash: string): Promise<boolean> {
    const record = await this.authorized(hash);
    if (!record) return false;
    try {
      const key = await this.key();
      let accessToken: string | null = null;
      if (record.accessExpiresAt > this.now()) {
        accessToken = await open(key, record.access, this.aad(record.userId, "access"));
      } else {
        const refreshed = await this.access(hash);
        if (refreshed.status === "ok") accessToken = refreshed.accessToken;
      }
      if (accessToken) await this.client().revoke(accessToken);
    } catch {
      // Revocation is best effort; the local wipe below always happens.
    }
    this.inflight = null;
    await this.storage.deleteAll();
    return true;
  }
}
