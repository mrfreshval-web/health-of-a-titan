import { describe, expect, it, vi } from "vitest";
import { sessionHash } from "../src/session";
import { ACCESS_MIN_TTL_MS, WhoopVault } from "../src/vault";
import type { WhoopTokens } from "../src/whoop";
import { FakeWhoop, MemoryStorage, TEST_KEY } from "./helpers";
import type { Env } from "../src/env";

function setup(envOverrides: Partial<Env> = {}) {
  const whoop = new FakeWhoop();
  const storage = new MemoryStorage();
  let now = 1_800_000_000_000;
  const env: Env = {
    WHOOP_CLIENT_ID: "test-client",
    WHOOP_CLIENT_SECRET: "test-secret",
    ALLOWED_WHOOP_USER_IDS: "4242",
    TOKEN_ENC_KEY: TEST_KEY,
    ...envOverrides,
  };
  const vault = new WhoopVault({ storage }, env, { fetch: whoop.fetch, now: () => now });
  const clock = {
    advance: (ms: number) => {
      now += ms;
    },
    now: () => now,
  };
  return { whoop, storage, vault, env, clock };
}

/** Tokens as WHOOP would hand them out after login. */
function loginTokens(whoop: FakeWhoop, now: number): WhoopTokens {
  whoop.validRefresh.add("whoop-refresh-0");
  whoop.validAccess.add("whoop-access-0");
  return { accessToken: "whoop-access-0", refreshToken: "whoop-refresh-0", expiresAt: now + 3600_000, scope: "offline" };
}

describe("WhoopVault", () => {
  it("stores tokens only sealed and the session only as a hash", async () => {
    const { vault, storage, whoop, clock } = setup();
    const session = await vault.connect("4242", loginTokens(whoop, clock.now()));
    const dump = storage.dump();
    expect(dump).not.toContain("whoop-refresh-0");
    expect(dump).not.toContain("whoop-access-0");
    expect(dump).not.toContain(session);
    expect(dump).toContain(await sessionHash(session));
  });

  it("returns the cached access token while it is fresh", async () => {
    const { vault, whoop, clock } = setup();
    const session = await vault.connect("4242", loginTokens(whoop, clock.now()));
    const r = await vault.access(await sessionHash(session));
    expect(r).toEqual({ status: "ok", accessToken: "whoop-access-0", expiresAt: clock.now() + 3600_000 });
    expect(whoop.tokenCalls).toHaveLength(0);
  });

  it("refreshes once for many concurrent callers (single-flight) and keeps the rotated token", async () => {
    const { vault, whoop, clock } = setup();
    const hash = await sessionHash(await vault.connect("4242", loginTokens(whoop, clock.now())));
    clock.advance(3600_000 - ACCESS_MIN_TTL_MS + 1); // inside the refresh margin
    whoop.delayMs = 20;
    const results = await Promise.all(Array.from({ length: 5 }, () => vault.access(hash)));
    expect(whoop.tokenCalls).toHaveLength(1);
    expect(whoop.tokenCalls[0]!.get("grant_type")).toBe("refresh_token");
    expect(whoop.tokenCalls[0]!.get("scope")).toBe("offline");
    for (const r of results) expect(r).toMatchObject({ status: "ok", accessToken: "whoop-access-1" });

    // The rotated refresh token is the one used next time; the old one is dead at WHOOP.
    clock.advance(3600_000);
    const again = await vault.access(hash);
    expect(again).toMatchObject({ status: "ok", accessToken: "whoop-access-2" });
    expect(whoop.tokenCalls[1]!.get("refresh_token")).toBe("whoop-refresh-1");
  });

  it("a late caller does not spend an already rotated refresh token", async () => {
    const { vault, whoop, clock } = setup();
    const hash = await sessionHash(await vault.connect("4242", loginTokens(whoop, clock.now())));
    clock.advance(3600_000);
    await vault.access(hash);
    await vault.access(hash);
    expect(whoop.tokenCalls).toHaveLength(1);
  });

  it("wipes the vault when WHOOP says invalid_grant", async () => {
    const { vault, whoop, clock, storage } = setup();
    const hash = await sessionHash(await vault.connect("4242", loginTokens(whoop, clock.now())));
    clock.advance(3600_000);
    whoop.failNext = "invalid_grant";
    expect(await vault.access(hash)).toEqual({ status: "reauth_required" });
    expect(storage.map.size).toBe(0);
    expect(await vault.access(hash)).toEqual({ status: "unauthorized" });
  });

  it("keeps the tokens when WHOOP is down or rejects our client", async () => {
    const { vault, whoop, clock, storage } = setup();
    const hash = await sessionHash(await vault.connect("4242", loginTokens(whoop, clock.now())));
    clock.advance(3600_000);
    whoop.failNext = "down";
    expect(await vault.access(hash)).toEqual({ status: "unavailable" });
    whoop.failNext = "invalid_client";
    expect(await vault.access(hash)).toEqual({ status: "unavailable" });
    expect(storage.map.size).toBe(1);
    expect(await vault.access(hash)).toMatchObject({ status: "ok" });
  });

  it("rejects a wrong session and a removed user", async () => {
    const { vault, whoop, clock, env } = setup();
    const session = await vault.connect("4242", loginTokens(whoop, clock.now()));
    expect(await vault.access(await sessionHash(session + "x"))).toEqual({ status: "unauthorized" });
    env.ALLOWED_WHOOP_USER_IDS = "";
    expect(await vault.access(await sessionHash(session))).toEqual({ status: "not_allowed" });
    expect(await vault.verify(await sessionHash(session))).toBeNull();
  });

  it("a new login replaces the old session", async () => {
    const { vault, whoop, clock } = setup();
    const first = await vault.connect("4242", loginTokens(whoop, clock.now()));
    const second = await vault.connect("4242", loginTokens(whoop, clock.now()));
    expect(await vault.access(await sessionHash(first))).toEqual({ status: "unauthorized" });
    expect(await vault.access(await sessionHash(second))).toMatchObject({ status: "ok" });
  });

  it("disconnect revokes at WHOOP and wipes everything", async () => {
    const { vault, whoop, clock, storage } = setup();
    const hash = await sessionHash(await vault.connect("4242", loginTokens(whoop, clock.now())));
    expect(await vault.disconnect(hash)).toBe(true);
    expect(whoop.revoked).toEqual(["whoop-access-0"]);
    expect(storage.map.size).toBe(0);
    expect(await vault.disconnect(hash)).toBe(false);
  });

  it("disconnect with an expired access token refreshes first, then revokes", async () => {
    const { vault, whoop, clock, storage } = setup();
    const hash = await sessionHash(await vault.connect("4242", loginTokens(whoop, clock.now())));
    clock.advance(2 * 3600_000);
    expect(await vault.disconnect(hash)).toBe(true);
    expect(whoop.revoked).toEqual(["whoop-access-1"]);
    expect(storage.map.size).toBe(0);
  });

  it("never logs", async () => {
    const spies = (["log", "info", "warn", "error", "debug"] as const).map((m) => vi.spyOn(console, m).mockImplementation(() => {}));
    const { vault, whoop, clock } = setup();
    const hash = await sessionHash(await vault.connect("4242", loginTokens(whoop, clock.now())));
    clock.advance(3600_000);
    await vault.access(hash);
    whoop.failNext = "invalid_grant";
    clock.advance(3600_000);
    await vault.access(hash);
    for (const s of spies) {
      expect(s).not.toHaveBeenCalled();
      s.mockRestore();
    }
  });

  it("answers misconfigured without TOKEN_ENC_KEY", async () => {
    const { vault } = setup({ TOKEN_ENC_KEY: undefined });
    const res = await vault.fetch(new Request("https://vault/connect", { method: "POST", body: JSON.stringify({ userId: "4242", tokens: {} }) }));
    expect(res.status).toBe(500);
    expect(await res.json()).toEqual({ status: "misconfigured" });
  });
});
