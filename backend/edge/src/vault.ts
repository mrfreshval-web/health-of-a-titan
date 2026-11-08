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
 *  - Since D-062 (A65) the phone signs in to a HoT account; the Worker checks the account
 *    session and calls the `account-*` ops, which trust the Worker (the DO is not reachable from
 *    outside). The legacy device session `hs1` (only its SHA-256 is stored) keeps working for
 *    `/whoop/access` and `/whoop/disconnect` until `/auth/migrate` retires it (A65 §3.5).
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
  /** SHA-256 of the legacy `hs1` session; "" when the tokens belong to an account session. */
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
          const session = await this.connect(String(body.userId), body.tokens as WhoopTokens, body.legacySession === true);
          return Response.json({ session });
        }
        case "/account-access":
          return Response.json(await this.accountAccess());
        case "/account-disconnect":
          return Response.json({ ok: await this.accountDisconnect() });
        case "/legacy-check":
          return Response.json({ userId: await this.legacyCheck(String(body.sessionHash)) });
        case "/legacy-retire":
          return Response.json({ ok: await this.legacyRetire(String(body.sessionHash)) });
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

  /**
   * Stores fresh tokens after login (any older `hs1` session dies). With [legacySession] it also
   * returns a new `hs1` token (tests of the migration path); account logins get null.
   */
  async connect(userId: string, tokens: WhoopTokens, legacySession = false): Promise<string | null> {
    const key = await this.key();
    const session = legacySession ? newSessionToken(userId) : null;
    const now = this.now();
    const record: VaultRecord = {
      v: 1,
      userId,
      refresh: await seal(key, tokens.refreshToken, this.aad(userId, "refresh")),
      access: await seal(key, tokens.accessToken, this.aad(userId, "access")),
      accessExpiresAt: tokens.expiresAt,
      sessionHash: session ? await sessionHash(session) : "",
      connectedAt: now,
      refreshedAt: now,
    };
    this.inflight = null;
    await this.storage.put(RECORD_KEY, record);
    return session;
  }

  private async authorized(hash: string): Promise<VaultRecord | null> {
    const record = await this.storage.get<VaultRecord>(RECORD_KEY);
    // An account-mode record has no legacy session: "" never matches.
    if (!record || !record.sessionHash || !hash || !timingSafeEqual(record.sessionHash, hash)) return null;
    return record;
  }

  /** `/auth/migrate`: the WHOOP user id of a valid legacy session, or null. */
  async legacyCheck(hash: string): Promise<string | null> {
    return (await this.authorized(hash))?.userId ?? null;
  }

  /** `/auth/migrate` done: the legacy session is spent; the tokens stay for the account. */
  async legacyRetire(hash: string): Promise<boolean> {
    const record = await this.authorized(hash);
    if (!record) return false;
    await this.storage.put(RECORD_KEY, { ...record, sessionHash: "" });
    return true;
  }

  /** Access token for the account that owns this WHOOP identity (checked by the Worker). */
  async accountAccess(): Promise<AccessResult> {
    const record = await this.storage.get<VaultRecord>(RECORD_KEY);
    if (!record) return { status: "reauth_required" };
    return this.accessFor(record);
  }

  /** «Отключить WHOOP» from an account session: revoke at WHOOP (best effort) and wipe. */
  async accountDisconnect(): Promise<boolean> {
    const record = await this.storage.get<VaultRecord>(RECORD_KEY);
    if (record) await this.revokeAndWipe(record);
    return true;
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
    return this.accessFor(record);
  }

  private async accessFor(record: VaultRecord): Promise<AccessResult> {
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
    await this.revokeAndWipe(record);
    return true;
  }

  private async revokeAndWipe(record: VaultRecord): Promise<void> {
    try {
      const key = await this.key();
      let accessToken: string | null = null;
      if (record.accessExpiresAt > this.now()) {
        accessToken = await open(key, record.access, this.aad(record.userId, "access"));
      } else {
        const refreshed = await this.accessFor(record);
        if (refreshed.status === "ok") accessToken = refreshed.accessToken;
      }
      if (accessToken) await this.client().revoke(accessToken);
    } catch {
      // Revocation is best effort; the local wipe below always happens.
    }
    this.inflight = null;
    await this.storage.deleteAll();
  }
}
