import { b64urlEncode, randomBytes } from "./crypto";

/**
 * Account store of HoT (A65 §4.1, §7.2, D-062). The Worker talks to it only through
 * [AccountStore], so the storage can move (variant Б: Yandex Cloud ru-central1, slice Б1) without
 * touching the routes.
 *
 * Variant А (now): one Durable Object `AccountVault` with SQLite-backed storage. Every operation
 * runs under one lock, so «identity is unique», «an invite is used once» and «a refresh token
 * rotates once» hold even for concurrent requests (single-flight, A65 §3.2).
 *
 * What is stored (A65 §4.1): account id, status, `session_epoch`; identities (provider, subject,
 * dates); sessions (device public key, keyed hashes of the current and previous refresh token,
 * dates, revocation); invite hashes; spent nonce ids; an audit trail (event, time, account) for
 * 90 days. No names, e-mails, pictures, health data, tokens of Google or IP addresses.
 *
 * Slice А3 adds linking and unlinking of identities, the list of devices with «Завершить» and the
 * step-up time of a session; slice А5 deletes an account: its identities, sessions and invites go,
 * and each of its session ids leaves only a tombstone (`tomb:<sid>` → time), so a phone that comes
 * back later hears `account_deleted` and wipes itself (A65 §6). The tombstone holds no account id.
 */

export type Provider = "google" | "whoop";

export interface Identity {
  provider: Provider;
  subject: string;
  linkedAt: number;
  lastLoginAt: number;
  /** «Привязан Google · 08.11 13:40 · Pixel 9 Pro XL» (A65 §2.3): the device that linked it. */
  linkedDevice?: string;
}

export interface Account {
  id: string;
  createdAt: number;
  status: "active";
  sessionEpoch: number;
  /** May create invites: accounts that came from the founder's allowlist (migration, A65 §2.5). */
  inviter: boolean;
  identities: Identity[];
}

export interface Session {
  sid: string;
  accountId: string;
  devicePub: string;
  deviceName: string;
  createdAt: number;
  lastUsedAt: number;
  slidingExpiresAt: number;
  absoluteExpiresAt: number;
  /** HMAC(pepper, refresh) of the current and of the previous token. */
  current: string;
  previous: string | null;
  rotatedAt: number;
  epoch: number;
  revokedAt: number | null;
  /** Last proof of the person (sign-in or step-up, A65 §2.3); sessions of slice А1 read createdAt. */
  authAt?: number;
}

export type LoginResult =
  | { status: "ok"; account: Account; created: boolean }
  | { status: "no_account" }
  /** Only with `createOnly`: the identity already has an account (sign-up ticket replayed). */
  | { status: "exists" }
  | { status: "invite_invalid" };

export type RotateResult =
  | { status: "rotated" | "retry"; session: Session; account: Account }
  /** An old token came back after the grace window: the whole session was revoked (A65 §3.2). */
  | { status: "reuse" }
  | { status: "invalid" };

export type AuditEvent =
  | "account.created"
  | "login.google"
  | "login.whoop"
  | "login.migrate"
  | "session.refresh_reuse"
  | "session.logout"
  | "session.logout_all"
  | "invite.created"
  | "identity.linked"
  | "identity.unlinked"
  | "session.revoked"
  | "session.step_up"
  | "account.deleted";

export type LinkResult =
  | { status: "ok"; account: Account; linked: boolean }
  /** The identity belongs to another account: no automatic merge (A65 §2.3). */
  | { status: "in_use" }
  /** The account already has another identity of this provider: unlink it first. */
  | { status: "provider_linked" }
  | { status: "no_account" };

export type UnlinkResult =
  | { status: "ok"; account: Account; removed: Identity }
  /** The last identity cannot go: the account would have no way in (A65 §2.3). */
  | { status: "last_identity" }
  | { status: "not_linked" }
  | { status: "no_account" };

export const SLIDING_TTL_MS = 30 * 24 * 60 * 60 * 1000;
export const ABSOLUTE_TTL_MS = 180 * 24 * 60 * 60 * 1000;
/** A retried refresh with the same old token within this window gets the same answer. */
export const REFRESH_GRACE_MS = 30 * 1000;
export const INVITE_TTL_MS = 14 * 24 * 60 * 60 * 1000;
export const AUDIT_TTL_MS = 90 * 24 * 60 * 60 * 1000;
/**
 * A tombstone outlives every refresh token of the deleted account (absolute TTL) with a margin;
 * after that the phone hears a plain `401 invalid_grant` instead of `account_deleted`.
 */
export const TOMB_TTL_MS = ABSOLUTE_TTL_MS + 30 * 24 * 60 * 60 * 1000;
/** Step-up (A65 §2.3): linking, unlinking and deletion need a proof of the person this fresh. */
export const STEP_UP_WINDOW_MS = 10 * 60 * 1000;

/** When the last proof of the person in [session] stops counting as fresh. */
export function stepUpUntil(session: Session): number {
  return (session.authAt ?? session.createdAt) + STEP_UP_WINDOW_MS;
}

export interface AccountStore {
  /** Spends a login nonce: true the first time, false after that. */
  consumeNonce(id: string, expSeconds: number, now: number): Promise<boolean>;
  /**
   * The account of the identity; without one, a new account is created when [inviteHash] names a
   * live invite (spent here) or when [bootstrap] is true (migration of `hs1`, the allowlist).
   */
  login(input: { provider: Provider; subject: string; inviteHash?: string | null; bootstrap?: boolean; createOnly?: boolean; now: number }): Promise<LoginResult>;
  createSession(input: { sid: string; accountId: string; devicePub: string; deviceName: string; refreshHash: string; now: number }): Promise<Session>;
  /** Live session with its account, or null (unknown, revoked, expired, epoch moved). */
  liveSession(sid: string, now: number): Promise<{ session: Session; account: Account } | null>;
  rotate(input: { sid: string; presentedHash: string; nextHash: string; now: number }): Promise<RotateResult>;
  revokeSession(sid: string, now: number): Promise<boolean>;
  /** «Выйти везде»: every session of the account is revoked, `session_epoch` moves on. */
  revokeAll(accountId: string, now: number): Promise<number>;
  createInvite(input: { hash: string; createdBy: string; now: number }): Promise<{ expiresAt: number }>;
  audit(event: AuditEvent, accountId: string | null, now: number): Promise<void>;

  // Slice А3: identities, devices, step-up
  /** The account id that owns the identity, or null. */
  ownerOf(provider: Provider, subject: string): Promise<string | null>;
  /** Live sessions of the account (the list of devices), oldest first. */
  sessions(accountId: string, now: number): Promise<Session[]>;
  /** «Завершить» on a device: only a session of [accountId]. */
  revokeDevice(accountId: string, sid: string, now: number): Promise<boolean>;
  /** A fresh proof of the person in this session (sign-in again with an identity of the account). */
  stepUp(sid: string, now: number): Promise<Session | null>;
  link(input: { accountId: string; provider: Provider; subject: string; device: string; now: number }): Promise<LinkResult>;
  unlink(input: { accountId: string; provider: Provider; now: number }): Promise<UnlinkResult>;

  // Slice А5: deletion
  /** Deletes the account (identities, sessions, invites); returns the identities it had, or null. */
  deleteAccount(accountId: string, now: number): Promise<Identity[] | null>;
  /** True when [sid] belonged to an account deleted less than [TOMB_TTL_MS] ago. */
  isTombstoned(sid: string, now: number): Promise<boolean>;
}

// ---------------------------------------------------------------------------------------------
// Variant А: key-value storage of a Durable Object

/** The subset of DurableObjectStorage the store uses (tests use an in-memory map). */
export interface KvStorage {
  get<T>(key: string): Promise<T | undefined>;
  put<T>(key: string, value: T): Promise<void>;
  delete(key: string): Promise<boolean>;
  list<T>(options: { prefix: string; limit?: number }): Promise<Map<string, T>>;
}

/** What stands in an invite instead of the id of a deleted account. */
const DELETED = "deleted";

interface InviteRecord {
  expiresAt: number;
  createdBy: string;
  usedBy: string | null;
}

const K = {
  account: (id: string) => `acct:${id}`,
  identity: (p: Provider, s: string) => `ident:${p}:${s}`,
  session: (sid: string) => `sess:${sid}`,
  accountSession: (a: string, sid: string) => `asess:${a}:${sid}`,
  nonce: (exp: number, id: string) => `nonce:${String(exp).padStart(10, "0")}:${id}`,
  invite: (h: string) => `invite:${h}`,
  tomb: (sid: string) => `tomb:${sid}`,
  audit: (at: number) => `audit:${String(at).padStart(15, "0")}:${b64urlEncode(randomBytes(6))}`,
};

/** UUIDv4 for `account_id`. */
export function newAccountId(): string {
  const b = randomBytes(16);
  b[6] = (b[6]! & 0x0f) | 0x40;
  b[8] = (b[8]! & 0x3f) | 0x80;
  const h = Array.from(b, (x) => x.toString(16).padStart(2, "0")).join("");
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
}

export class KvAccountStore implements AccountStore {
  private lock: Promise<unknown> = Promise.resolve();

  constructor(private readonly kv: KvStorage) {}

  /** Runs [fn] after every earlier operation finished: one writer at a time. */
  private serial<T>(fn: () => Promise<T>): Promise<T> {
    const run = this.lock.then(fn, fn);
    this.lock = run.catch(() => undefined);
    return run;
  }

  consumeNonce(id: string, expSeconds: number, now: number): Promise<boolean> {
    return this.serial(async () => {
      await this.prune("nonce:", (key) => Number(key.split(":")[1]) * 1000 < now);
      const key = K.nonce(expSeconds, id);
      if ((await this.kv.get<number>(key)) !== undefined) return false;
      await this.kv.put(key, expSeconds);
      return true;
    });
  }

  login(input: { provider: Provider; subject: string; inviteHash?: string | null; bootstrap?: boolean; createOnly?: boolean; now: number }): Promise<LoginResult> {
    return this.serial(async () => {
      const { provider, subject, now } = input;
      const existing = await this.kv.get<string>(K.identity(provider, subject));
      if (existing && input.createOnly) return { status: "exists" };
      if (existing) {
        const account = await this.kv.get<Account>(K.account(existing));
        if (account) {
          const identities = account.identities.map((i) => (i.provider === provider && i.subject === subject ? { ...i, lastLoginAt: now } : i));
          const updated = { ...account, identities };
          await this.kv.put(K.account(account.id), updated);
          return { status: "ok", account: updated, created: false };
        }
      }
      let inviter = false;
      if (input.bootstrap) {
        inviter = true;
      } else if (input.inviteHash) {
        const invite = await this.kv.get<InviteRecord>(K.invite(input.inviteHash));
        if (!invite || invite.usedBy || invite.expiresAt <= now) return { status: "invite_invalid" };
      } else {
        return { status: "no_account" };
      }
      const account: Account = {
        id: newAccountId(),
        createdAt: now,
        status: "active",
        sessionEpoch: 0,
        inviter,
        identities: [{ provider, subject, linkedAt: now, lastLoginAt: now }],
      };
      if (!input.bootstrap && input.inviteHash) {
        const invite = (await this.kv.get<InviteRecord>(K.invite(input.inviteHash)))!;
        await this.kv.put(K.invite(input.inviteHash), { ...invite, usedBy: account.id });
      }
      await this.kv.put(K.account(account.id), account);
      await this.kv.put(K.identity(provider, subject), account.id);
      await this.writeAudit("account.created", account.id, now);
      return { status: "ok", account, created: true };
    });
  }

  createSession(input: { sid: string; accountId: string; devicePub: string; deviceName: string; refreshHash: string; now: number }): Promise<Session> {
    return this.serial(async () => {
      const account = await this.kv.get<Account>(K.account(input.accountId));
      if (!account) throw new Error("no account");
      const { now } = input;
      const session: Session = {
        sid: input.sid,
        accountId: account.id,
        devicePub: input.devicePub,
        deviceName: input.deviceName,
        createdAt: now,
        lastUsedAt: now,
        slidingExpiresAt: now + SLIDING_TTL_MS,
        absoluteExpiresAt: now + ABSOLUTE_TTL_MS,
        current: input.refreshHash,
        previous: null,
        rotatedAt: now,
        epoch: account.sessionEpoch,
        revokedAt: null,
      };
      await this.kv.put(K.session(session.sid), session);
      await this.kv.put(K.accountSession(account.id, session.sid), now);
      return session;
    });
  }

  private async live(sid: string, now: number): Promise<{ session: Session; account: Account } | null> {
    const session = await this.kv.get<Session>(K.session(sid));
    if (!session || session.revokedAt !== null) return null;
    if (now >= session.slidingExpiresAt || now >= session.absoluteExpiresAt) return null;
    const account = await this.kv.get<Account>(K.account(session.accountId));
    if (!account || account.status !== "active" || account.sessionEpoch !== session.epoch) return null;
    return { session, account };
  }

  liveSession(sid: string, now: number): Promise<{ session: Session; account: Account } | null> {
    return this.serial(() => this.live(sid, now));
  }

  rotate(input: { sid: string; presentedHash: string; nextHash: string; now: number }): Promise<RotateResult> {
    return this.serial(async () => {
      const { sid, presentedHash, nextHash, now } = input;
      const found = await this.live(sid, now);
      if (!found) return { status: "invalid" };
      const { session, account } = found;
      if (presentedHash === session.current) {
        const updated: Session = {
          ...session,
          previous: session.current,
          current: nextHash,
          rotatedAt: now,
          lastUsedAt: now,
          slidingExpiresAt: Math.min(now + SLIDING_TTL_MS, session.absoluteExpiresAt),
        };
        await this.kv.put(K.session(sid), updated);
        return { status: "rotated", session: updated, account };
      }
      if (session.previous !== null && presentedHash === session.previous) {
        // The same old token again: a network retry inside the grace window gets the same pair;
        // later it means two holders of one token — the family is revoked.
        if (now - session.rotatedAt <= REFRESH_GRACE_MS && nextHash === session.current) {
          return { status: "retry", session, account };
        }
        await this.kv.put(K.session(sid), { ...session, revokedAt: now });
        await this.writeAudit("session.refresh_reuse", account.id, now);
        return { status: "reuse" };
      }
      // Unknown token for this sid (forged or older than the previous one): refused, nothing revoked,
      // so a guessed sid cannot be used to log someone out.
      return { status: "invalid" };
    });
  }

  revokeSession(sid: string, now: number): Promise<boolean> {
    return this.serial(async () => {
      const session = await this.kv.get<Session>(K.session(sid));
      if (!session || session.revokedAt !== null) return false;
      await this.kv.put(K.session(sid), { ...session, revokedAt: now });
      await this.writeAudit("session.logout", session.accountId, now);
      return true;
    });
  }

  revokeAll(accountId: string, now: number): Promise<number> {
    return this.serial(async () => {
      const account = await this.kv.get<Account>(K.account(accountId));
      if (!account) return 0;
      const sids = await this.kv.list<number>({ prefix: `asess:${accountId}:` });
      for (const key of sids.keys()) {
        const sid = key.slice(`asess:${accountId}:`.length);
        const session = await this.kv.get<Session>(K.session(sid));
        if (session && session.revokedAt === null) await this.kv.put(K.session(sid), { ...session, revokedAt: now });
      }
      const epoch = account.sessionEpoch + 1;
      await this.kv.put(K.account(accountId), { ...account, sessionEpoch: epoch });
      await this.writeAudit("session.logout_all", accountId, now);
      return epoch;
    });
  }

  createInvite(input: { hash: string; createdBy: string; now: number }): Promise<{ expiresAt: number }> {
    return this.serial(async () => {
      const expiresAt = input.now + INVITE_TTL_MS;
      await this.kv.put<InviteRecord>(K.invite(input.hash), { expiresAt, createdBy: input.createdBy, usedBy: null });
      await this.writeAudit("invite.created", input.createdBy, input.now);
      return { expiresAt };
    });
  }

  audit(event: AuditEvent, accountId: string | null, now: number): Promise<void> {
    return this.serial(() => this.writeAudit(event, accountId, now));
  }

  ownerOf(provider: Provider, subject: string): Promise<string | null> {
    return this.serial(async () => (await this.kv.get<string>(K.identity(provider, subject))) ?? null);
  }

  private async sessionIds(accountId: string): Promise<string[]> {
    const prefix = `asess:${accountId}:`;
    return [...(await this.kv.list<number>({ prefix })).keys()].map((k) => k.slice(prefix.length));
  }

  sessions(accountId: string, now: number): Promise<Session[]> {
    return this.serial(async () => {
      const out: Session[] = [];
      for (const sid of await this.sessionIds(accountId)) {
        const found = await this.live(sid, now);
        if (found && found.account.id === accountId) out.push(found.session);
      }
      return out.sort((a, b) => a.createdAt - b.createdAt);
    });
  }

  revokeDevice(accountId: string, sid: string, now: number): Promise<boolean> {
    return this.serial(async () => {
      const session = await this.kv.get<Session>(K.session(sid));
      if (!session || session.accountId !== accountId || session.revokedAt !== null) return false;
      await this.kv.put(K.session(sid), { ...session, revokedAt: now });
      await this.writeAudit("session.revoked", accountId, now);
      return true;
    });
  }

  stepUp(sid: string, now: number): Promise<Session | null> {
    return this.serial(async () => {
      const found = await this.live(sid, now);
      if (!found) return null;
      const updated: Session = { ...found.session, authAt: now };
      await this.kv.put(K.session(sid), updated);
      await this.writeAudit("session.step_up", found.account.id, now);
      return updated;
    });
  }

  link(input: { accountId: string; provider: Provider; subject: string; device: string; now: number }): Promise<LinkResult> {
    return this.serial(async () => {
      const { accountId, provider, subject, now } = input;
      const account = await this.kv.get<Account>(K.account(accountId));
      if (!account || account.status !== "active") return { status: "no_account" };
      const owner = await this.kv.get<string>(K.identity(provider, subject));
      if (owner && owner !== accountId) return { status: "in_use" };
      if (owner === accountId) {
        const identities = account.identities.map((i) => (i.provider === provider && i.subject === subject ? { ...i, lastLoginAt: now } : i));
        const updated = { ...account, identities };
        await this.kv.put(K.account(accountId), updated);
        return { status: "ok", account: updated, linked: false };
      }
      if (account.identities.some((i) => i.provider === provider)) return { status: "provider_linked" };
      const updated: Account = {
        ...account,
        identities: [...account.identities, { provider, subject, linkedAt: now, lastLoginAt: now, linkedDevice: input.device }],
      };
      await this.kv.put(K.account(accountId), updated);
      await this.kv.put(K.identity(provider, subject), accountId);
      await this.writeAudit("identity.linked", accountId, now);
      return { status: "ok", account: updated, linked: true };
    });
  }

  unlink(input: { accountId: string; provider: Provider; now: number }): Promise<UnlinkResult> {
    return this.serial(async () => {
      const { accountId, provider, now } = input;
      const account = await this.kv.get<Account>(K.account(accountId));
      if (!account || account.status !== "active") return { status: "no_account" };
      const removed = account.identities.find((i) => i.provider === provider);
      if (!removed) return { status: "not_linked" };
      if (account.identities.length < 2) return { status: "last_identity" };
      const updated: Account = { ...account, identities: account.identities.filter((i) => i.provider !== provider) };
      await this.kv.put(K.account(accountId), updated);
      await this.kv.delete(K.identity(provider, removed.subject));
      await this.writeAudit("identity.unlinked", accountId, now);
      return { status: "ok", account: updated, removed };
    });
  }

  deleteAccount(accountId: string, now: number): Promise<Identity[] | null> {
    return this.serial(async () => {
      const account = await this.kv.get<Account>(K.account(accountId));
      if (!account) return null;
      for (const i of account.identities) {
        if ((await this.kv.get<string>(K.identity(i.provider, i.subject))) === accountId) await this.kv.delete(K.identity(i.provider, i.subject));
      }
      for (const sid of await this.sessionIds(accountId)) {
        await this.kv.delete(K.session(sid));
        await this.kv.delete(K.accountSession(accountId, sid));
        await this.kv.put(K.tomb(sid), now);
      }
      // Invites: the unused ones of this account go; a used one forgets who made or used it.
      const invites = await this.kv.list<InviteRecord>({ prefix: "invite:" });
      for (const [key, invite] of invites) {
        if (invite.createdBy === accountId && !invite.usedBy) {
          await this.kv.delete(key);
        } else if (invite.createdBy === accountId || invite.usedBy === accountId) {
          await this.kv.put<InviteRecord>(key, {
            ...invite,
            createdBy: invite.createdBy === accountId ? DELETED : invite.createdBy,
            usedBy: invite.usedBy === accountId ? DELETED : invite.usedBy,
          });
        }
      }
      await this.kv.delete(K.account(accountId));
      // Older audit lines of this account lose the id; the deletion itself is a line without one.
      const audit = await this.kv.list<{ e: string; a: string | null; at: number }>({ prefix: "audit:" });
      for (const [key, line] of audit) if (line.a === accountId) await this.kv.put(key, { ...line, a: null });
      await this.writeAudit("account.deleted", null, now);
      return account.identities;
    });
  }

  isTombstoned(sid: string, now: number): Promise<boolean> {
    return this.serial(async () => {
      const at = await this.kv.get<number>(K.tomb(sid));
      if (at === undefined) return false;
      if (now - at <= TOMB_TTL_MS) return true;
      await this.kv.delete(K.tomb(sid));
      return false;
    });
  }

  private async writeAudit(event: AuditEvent, accountId: string | null, now: number): Promise<void> {
    await this.prune("audit:", (key) => Number(key.split(":")[1]) < now - AUDIT_TTL_MS);
    await this.kv.put(K.audit(now), { e: event, a: accountId, at: now });
  }

  /** Deletes the oldest entries under [prefix] while [expired] says so (keys sort by time). */
  private async prune(prefix: string, expired: (key: string) => boolean): Promise<void> {
    const oldest = await this.kv.list<unknown>({ prefix, limit: 20 });
    for (const key of oldest.keys()) {
      if (!expired(key)) break;
      await this.kv.delete(key);
    }
  }
}

// ---------------------------------------------------------------------------------------------
// The Durable Object and the Worker-side client

const OPS = [
  "consumeNonce",
  "login",
  "createSession",
  "liveSession",
  "rotate",
  "revokeSession",
  "revokeAll",
  "createInvite",
  "audit",
  "ownerOf",
  "sessions",
  "revokeDevice",
  "stepUp",
  "link",
  "unlink",
  "deleteAccount",
  "isTombstoned",
] as const;
type Op = (typeof OPS)[number];

/**
 * Durable Object `AccountVault`: one instance (`idFromName("accounts")`) holds the account store.
 * Internal protocol (Worker → DO only): `POST https://accounts/<op>` with the JSON argument list.
 */
export class AccountVault {
  private readonly store: KvAccountStore;

  constructor(state: { storage: KvStorage }, _env?: unknown) {
    this.store = new KvAccountStore(state.storage);
  }

  async fetch(request: Request): Promise<Response> {
    const op = new URL(request.url).pathname.slice(1) as Op;
    if (!OPS.includes(op)) return Response.json({ error: "not_found" }, { status: 404 });
    try {
      const args = (await request.json()) as unknown[];
      const method = this.store[op] as (...a: unknown[]) => Promise<unknown>;
      const result = await method.apply(this.store, Array.isArray(args) ? args : []);
      return Response.json({ result: result ?? null });
    } catch {
      return Response.json({ error: "store_error" }, { status: 500 });
    }
  }
}

/** [AccountStore] over the `ACCOUNT_VAULT` Durable Object namespace. */
export class DurableAccountStore implements AccountStore {
  private readonly stub: { fetch(input: string, init?: RequestInit): Promise<Response> };

  constructor(ns: DurableObjectNamespace) {
    this.stub = ns.get(ns.idFromName("accounts"));
  }

  private async call<T>(op: Op, ...args: unknown[]): Promise<T> {
    const res = await this.stub.fetch(`https://accounts/${op}`, { method: "POST", body: JSON.stringify(args) });
    if (!res.ok) throw new Error(`account store ${res.status}`);
    return ((await res.json()) as { result: T }).result;
  }

  consumeNonce(id: string, expSeconds: number, now: number) {
    return this.call<boolean>("consumeNonce", id, expSeconds, now);
  }
  login(input: Parameters<AccountStore["login"]>[0]) {
    return this.call<LoginResult>("login", input);
  }
  createSession(input: Parameters<AccountStore["createSession"]>[0]) {
    return this.call<Session>("createSession", input);
  }
  liveSession(sid: string, now: number) {
    return this.call<{ session: Session; account: Account } | null>("liveSession", sid, now);
  }
  rotate(input: Parameters<AccountStore["rotate"]>[0]) {
    return this.call<RotateResult>("rotate", input);
  }
  revokeSession(sid: string, now: number) {
    return this.call<boolean>("revokeSession", sid, now);
  }
  revokeAll(accountId: string, now: number) {
    return this.call<number>("revokeAll", accountId, now);
  }
  createInvite(input: Parameters<AccountStore["createInvite"]>[0]) {
    return this.call<{ expiresAt: number }>("createInvite", input);
  }
  audit(event: AuditEvent, accountId: string | null, now: number) {
    return this.call<void>("audit", event, accountId, now);
  }
  ownerOf(provider: Provider, subject: string) {
    return this.call<string | null>("ownerOf", provider, subject);
  }
  sessions(accountId: string, now: number) {
    return this.call<Session[]>("sessions", accountId, now);
  }
  revokeDevice(accountId: string, sid: string, now: number) {
    return this.call<boolean>("revokeDevice", accountId, sid, now);
  }
  stepUp(sid: string, now: number) {
    return this.call<Session | null>("stepUp", sid, now);
  }
  link(input: Parameters<AccountStore["link"]>[0]) {
    return this.call<LinkResult>("link", input);
  }
  unlink(input: Parameters<AccountStore["unlink"]>[0]) {
    return this.call<UnlinkResult>("unlink", input);
  }
  deleteAccount(accountId: string, now: number) {
    return this.call<Identity[] | null>("deleteAccount", accountId, now);
  }
  isTombstoned(sid: string, now: number) {
    return this.call<boolean>("isTombstoned", sid, now);
  }
}
