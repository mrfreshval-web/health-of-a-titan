import type { AccessResult } from "./vault";
import type { WhoopTokens } from "./whoop";

/**
 * Where the WHOOP tokens of a user live (D-025, A65 §7.2). The Worker talks to it only through
 * this interface, so variant Б (Yandex Cloud, slice Б1) can replace the Durable Object.
 *
 * `account*` operations are called after the Worker checked the account session; `legacy*`
 * operations authenticate the old device session `hs1` and disappear with `/auth/migrate`
 * (A65 §3.5: two weeks after the release).
 */
export interface TokenVault {
  /** Stores fresh tokens after a WHOOP login. Returns an `hs1` token only when [legacySession]. */
  connect(whoopUserId: string, tokens: WhoopTokens, legacySession?: boolean): Promise<string | null>;
  access(whoopUserId: string): Promise<AccessResult>;
  /** Revokes the grant at WHOOP (best effort) and wipes the tokens. */
  disconnect(whoopUserId: string): Promise<boolean>;

  legacyAccess(whoopUserId: string, sessionHash: string): Promise<AccessResult>;
  legacyDisconnect(whoopUserId: string, sessionHash: string): Promise<boolean>;
  /** The WHOOP user id of a valid `hs1` session, or null. */
  legacyCheck(whoopUserId: string, sessionHash: string): Promise<string | null>;
  legacyRetire(whoopUserId: string, sessionHash: string): Promise<boolean>;
}

/** [TokenVault] over the `WHOOP_VAULT` Durable Object namespace: one object per WHOOP user. */
export class DurableTokenVault implements TokenVault {
  constructor(private readonly ns: DurableObjectNamespace) {}

  private async call<T>(userId: string, op: string, body: unknown): Promise<T> {
    const stub = this.ns.get(this.ns.idFromName(`whoop-user:${userId}`));
    const res = await stub.fetch(`https://vault/${op}`, { method: "POST", body: JSON.stringify(body) });
    return (await res.json()) as T;
  }

  async connect(userId: string, tokens: WhoopTokens, legacySession = false): Promise<string | null> {
    return (await this.call<{ session?: string | null }>(userId, "connect", { userId, tokens, legacySession })).session ?? null;
  }
  access(userId: string): Promise<AccessResult> {
    return this.call<AccessResult>(userId, "account-access", {});
  }
  async disconnect(userId: string): Promise<boolean> {
    return (await this.call<{ ok?: boolean }>(userId, "account-disconnect", {})).ok === true;
  }
  legacyAccess(userId: string, sessionHash: string): Promise<AccessResult> {
    return this.call<AccessResult>(userId, "access", { sessionHash });
  }
  async legacyDisconnect(userId: string, sessionHash: string): Promise<boolean> {
    return (await this.call<{ ok?: boolean }>(userId, "disconnect", { sessionHash })).ok === true;
  }
  async legacyCheck(userId: string, sessionHash: string): Promise<string | null> {
    return (await this.call<{ userId?: string | null }>(userId, "legacy-check", { sessionHash })).userId ?? null;
  }
  async legacyRetire(userId: string, sessionHash: string): Promise<boolean> {
    return (await this.call<{ ok?: boolean }>(userId, "legacy-retire", { sessionHash })).ok === true;
  }
}
