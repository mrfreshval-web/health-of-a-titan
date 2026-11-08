import { describe, expect, it } from "vitest";
import { loadSigningKeys, signAccessToken, verifyAccessToken } from "../src/access-token";
import { ABSOLUTE_TTL_MS, KvAccountStore, SLIDING_TTL_MS } from "../src/account-store";
import { derToRawP256, newInviteCode, normaliseInvite } from "../src/auth-tokens";
import type { Env } from "../src/env";
import { GoogleIdTokenVerifier } from "../src/google";
import { handle, type HandleOptions } from "../src/index";
import { FakeDevice, FakeGoogle, makeEnv, MemoryStorage, newSigningJwk, rawToDer, TEST_GOOGLE_AUD, TEST_SIGNING_KEY } from "./helpers";

const T0 = 1_800_000_000; // seconds

const post = (path: string, body?: unknown, token?: string) =>
  new Request(`https://edge.test${path}`, {
    method: "POST",
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: body === undefined ? undefined : JSON.stringify(body),
  });

interface Session {
  access: string;
  access_expires_at: number;
  refresh: string;
  account_id: string;
  identities: Array<{ provider: string; subject: string }>;
}

/** A Worker with a fake Google, a controllable clock and one phone. */
async function world(overrides: Partial<Env> = {}) {
  const google = await FakeGoogle.create();
  const { env, whoop, ns, accounts } = makeEnv(overrides);
  let now = T0;
  const verifier = new GoogleIdTokenVerifier(google.fetch, () => now * 1000);
  const opts = (): HandleOptions => ({ nowSeconds: now, google: verifier, fetch: whoop.fetch });
  const call = (r: Request) => handle(r, env, opts());
  const device = await FakeDevice.create();
  const nonce = async () => ((await (await call(post("/auth/nonce"))).json()) as { nonce: string }).nonce;
  const googleLogin = async (extra: Record<string, unknown> = {}, sub?: string, dev = device) => {
    const n = await nonce();
    const idToken = await google.idToken(google.claims(n, now, sub));
    return call(post("/auth/google", { id_token: idToken, ...(await dev.login(n)), ...extra }));
  };
  const refresh = async (token: string, dev = device, ts = now) => call(post("/auth/refresh", await dev.refresh(token, ts)));
  return {
    env,
    whoop,
    ns,
    accounts,
    google,
    device,
    call,
    nonce,
    googleLogin,
    refresh,
    advance: (s: number) => {
      now += s;
    },
    now: () => now,
  };
}

/** The founder's account: created by migration of the old hs1 session (inviter). */
async function founder(w: Awaited<ReturnType<typeof world>>): Promise<Session> {
  w.whoop.validAccess.add("whoop-access-0");
  w.whoop.validRefresh.add("whoop-refresh-0");
  const tokens = { accessToken: "whoop-access-0", refreshToken: "whoop-refresh-0", expiresAt: w.now() * 1000 + 3600_000, scope: "offline" };
  const hs1 = (await w.ns.entry("whoop-user:4242").vault.connect("4242", tokens, true))!;
  const n = await w.nonce();
  const res = await w.call(post("/auth/migrate", { nonce: n, ...(await w.device.login(n)) }, hs1));
  expect(res.status).toBe(200);
  return (await res.json()) as Session;
}

describe("access token (A65 §3.1)", () => {
  it("is EdDSA, lives 15 minutes and carries only iss, sub, sid, iat, exp", async () => {
    const keys = await loadSigningKeys(TEST_SIGNING_KEY);
    const { token, exp } = await signAccessToken(keys, "acc-1", "sid-1", T0);
    expect(exp - T0).toBe(900);
    const [h, p] = token.split(".");
    expect(JSON.parse(atob(h!.replace(/-/g, "+").replace(/_/g, "/")))).toEqual({ alg: "EdDSA", typ: "at+jwt", kid: "test-1" });
    expect(Object.keys(JSON.parse(atob(p!.replace(/-/g, "+").replace(/_/g, "/")))).sort()).toEqual(["exp", "iat", "iss", "sid", "sub"]);
    expect(await verifyAccessToken(keys, token, T0 + 899)).toMatchObject({ sub: "acc-1", sid: "sid-1" });
    expect(await verifyAccessToken(keys, token, T0 + 900)).toBeNull();
  });

  it("key rotation: the previous key verifies until its tokens expire; a foreign key never", async () => {
    const old = await loadSigningKeys(TEST_SIGNING_KEY);
    const { token } = await signAccessToken(old, "a", "s", T0);
    const next = await newSigningJwk("test-2");
    expect(await verifyAccessToken(await loadSigningKeys(next, TEST_SIGNING_KEY), token, T0 + 10)).not.toBeNull();
    expect(await verifyAccessToken(await loadSigningKeys(next), token, T0 + 10)).toBeNull();
    const forged = await signAccessToken(await loadSigningKeys(await newSigningJwk("test-1")), "a", "s", T0);
    expect(await verifyAccessToken(old, forged.token, T0 + 10)).toBeNull();
  });

  it("fails closed on a malformed key", async () => {
    await expect(loadSigningKeys('{"kty":"OKP","crv":"Ed25519"}')).rejects.toThrow(/kid/);
    await expect(loadSigningKeys("not json")).rejects.toThrow();
    await expect(loadSigningKeys(undefined)).rejects.toThrow();
  });
});

describe("Google ID token (A65 §2.1 step 4)", () => {
  async function check(mutate: (c: Record<string, unknown>) => void, header: Record<string, unknown> = {}) {
    const google = await FakeGoogle.create();
    const v = new GoogleIdTokenVerifier(google.fetch, () => T0 * 1000);
    const claims = google.claims("n1.x.y", T0);
    mutate(claims);
    return v.verify(await google.idToken(claims, header), TEST_GOOGLE_AUD);
  }

  it("accepts a valid token and returns only sub and nonce", async () => {
    expect(await check(() => {})).toEqual({ ok: true, sub: "109876543210", nonce: "n1.x.y" });
    expect(await check((c) => (c.iss = "accounts.google.com"))).toMatchObject({ ok: true });
  });

  it.each([
    ["foreign audience", (c: Record<string, unknown>) => (c.aud = "someone-else.apps.googleusercontent.com"), "bad_audience"],
    ["foreign issuer", (c: Record<string, unknown>) => (c.iss = "https://evil.example"), "bad_issuer"],
    ["expired", (c: Record<string, unknown>) => (c.exp = T0 - 61), "expired"],
    ["issued too long ago", (c: Record<string, unknown>) => (c.iat = T0 - 11 * 60), "expired"],
    ["issued in the future", (c: Record<string, unknown>) => (c.iat = T0 + 120), "expired"],
    ["no nonce", (c: Record<string, unknown>) => delete c.nonce, "no_nonce"],
  ])("refuses a token with %s", async (_name, mutate, reason) => {
    expect(await check(mutate)).toEqual({ ok: false, reason });
  });

  it("refuses an unknown kid, a wrong alg and a bad signature", async () => {
    expect(await check(() => {}, { kid: "other" })).toEqual({ ok: false, reason: "unknown_key" });
    expect(await check(() => {}, { alg: "none" })).toEqual({ ok: false, reason: "malformed" });
    const google = await FakeGoogle.create();
    const other = await FakeGoogle.create();
    const v = new GoogleIdTokenVerifier(google.fetch, () => T0 * 1000);
    expect(await v.verify(await other.idToken(other.claims("n", T0)), TEST_GOOGLE_AUD)).toEqual({ ok: false, reason: "bad_signature" });
  });

  it("caches the JWKS per Cache-Control", async () => {
    const google = await FakeGoogle.create();
    let now = T0 * 1000;
    const v = new GoogleIdTokenVerifier(google.fetch, () => now);
    const token = await google.idToken(google.claims("n", T0));
    await v.verify(token, TEST_GOOGLE_AUD);
    await v.verify(token, TEST_GOOGLE_AUD);
    expect(google.jwksCalls).toBe(1);
    now += 3601_000;
    await v.verify(token, TEST_GOOGLE_AUD);
    expect(google.jwksCalls).toBe(2);
  });
});

describe("POST /auth/google and /auth/signup", () => {
  it("a Google identity without an account gets no_account and a sign-up ticket; nothing is stored", async () => {
    const w = await world();
    const res = await w.googleLogin();
    expect(res.status).toBe(404);
    const body = (await res.json()) as { error: string; signup_ticket: string };
    expect(body.error).toBe("no_account");
    expect(body.signup_ticket).toMatch(/^t1\./);
    expect(w.accounts.storage.dump()).not.toContain("109876543210");
  });

  it("the founder invites, the new person signs up with Google and logs in again", async () => {
    const w = await world();
    const f = await founder(w);
    const inv = await w.call(post("/auth/invites", undefined, f.access));
    expect(inv.status).toBe(201);
    const { code } = (await inv.json()) as { code: string };
    expect(normaliseInvite(code)).toBe(code);

    const { signup_ticket } = (await (await w.googleLogin()).json()) as { signup_ticket: string };
    const friend = await FakeDevice.create();
    const signup = await w.call(post("/auth/signup", { signup_ticket, invite: code.toLowerCase(), ...(await friend.login(signup_ticket)) }));
    expect(signup.status).toBe(200);
    const s = (await signup.json()) as Session;
    expect(s.account_id).not.toBe(f.account_id);
    expect(s.identities).toEqual([expect.objectContaining({ provider: "google", subject: "109876543210" })]);
    expect(JSON.stringify(s)).not.toContain("private@example.com");
    expect(w.accounts.storage.dump()).not.toContain("private@example.com");

    // the invite is spent
    const again = await w.call(post("/auth/signup", { signup_ticket, invite: code, ...(await friend.login(signup_ticket)) }));
    expect(again.status).toBe(409); // the ticket cannot mint a second sign-up

    // next time: straight into the same account
    const second = await w.googleLogin({}, undefined, friend);
    expect(second.status).toBe(200);
    expect(((await second.json()) as Session).account_id).toBe(s.account_id);
  });

  it("an invite can also come with the Google login itself", async () => {
    const w = await world();
    const f = await founder(w);
    const { code } = (await (await w.call(post("/auth/invites", undefined, f.access))).json()) as { code: string };
    const res = await w.googleLogin({ invite: code }, "555");
    expect(res.status).toBe(200);
  });

  it("only inviter accounts create invites", async () => {
    const w = await world();
    const f = await founder(w);
    const { code } = (await (await w.call(post("/auth/invites", undefined, f.access))).json()) as { code: string };
    const s = (await (await w.googleLogin({ invite: code }, "777")).json()) as Session;
    expect((await w.call(post("/auth/invites", undefined, s.access))).status).toBe(403);
    expect((await w.call(post("/auth/invites"))).status).toBe(401);
  });

  it("the nonce is ours, fresh and used once", async () => {
    const w = await world();
    await founder(w);
    const n = await w.nonce();
    const token = await w.google.idToken(w.google.claims(n, w.now(), "4242-google"));
    const body = { id_token: token, ...(await w.device.login(n)) };
    expect((await w.call(post("/auth/google", body))).status).toBe(404); // spent here
    const replay = await w.call(post("/auth/google", body));
    expect(replay.status).toBe(401);
    expect(await replay.json()).toEqual({ error: "nonce_used" });

    const foreign = await w.google.idToken(w.google.claims("n1.AAAAAAAAAAAAAAAAAAAAAA.AAAAAAAAAAAAAAAAAAAAAA", w.now()));
    const f = await w.call(post("/auth/google", { id_token: foreign, ...(await w.device.login("n1.AAAAAAAAAAAAAAAAAAAAAA.AAAAAAAAAAAAAAAAAAAAAA")) }));
    expect(f.status).toBe(401);

    const late = await w.nonce();
    w.advance(301);
    const lateToken = await w.google.idToken(w.google.claims(late, w.now()));
    const l = await w.call(post("/auth/google", { id_token: lateToken, ...(await w.device.login(late)) }));
    expect(await l.json()).toEqual({ error: "nonce_expired" });
  });

  it("needs the device proof over the nonce, and the configured audience", async () => {
    const w = await world();
    const n = await w.nonce();
    const token = await w.google.idToken(w.google.claims(n, w.now()));
    const other = await FakeDevice.create();
    const res = await w.call(post("/auth/google", { id_token: token, device_pub: w.device.pub, device_sig: await other.sign(`hot-login|v1|${n}`) }));
    expect(res.status).toBe(400);
    const noAud = await world({ GOOGLE_WEB_CLIENT_ID: undefined });
    expect((await noAud.googleLogin()).status).toBe(503);
  });
});

describe("POST /auth/refresh (A65 §3.2, §3.3)", () => {
  it("rotates: a new pair each time, the old refresh is spent", async () => {
    const w = await world();
    const s = await founder(w);
    w.advance(60);
    const r1 = await w.refresh(s.refresh);
    expect(r1.status).toBe(200);
    const n1 = (await r1.json()) as Session;
    expect(n1.refresh).not.toBe(s.refresh);
    expect(n1.account_id).toBe(s.account_id);
    const r2 = await w.refresh(n1.refresh);
    expect(r2.status).toBe(200);
    expect(w.accounts.storage.dump()).not.toContain(n1.refresh);
  });

  it("a network retry with the same old token within 30 s gets the same refresh token", async () => {
    const w = await world();
    const s = await founder(w);
    const a = (await (await w.refresh(s.refresh)).json()) as Session;
    w.advance(20);
    const b = await w.refresh(s.refresh);
    expect(b.status).toBe(200);
    expect(((await b.json()) as Session).refresh).toBe(a.refresh);
  });

  it("reuse after the grace window revokes the whole session (family)", async () => {
    const w = await world();
    const s = await founder(w);
    const a = (await (await w.refresh(s.refresh)).json()) as Session;
    w.advance(31);
    const stolen = await w.refresh(s.refresh);
    expect(stolen.status).toBe(401);
    expect(await stolen.json()).toEqual({ error: "session_revoked" });
    expect((await w.refresh(a.refresh)).status).toBe(401);
    expect((await w.call(post("/whoop/access", undefined, a.access))).status).toBe(401);
    expect(w.accounts.storage.dump()).toContain("session.refresh_reuse");
  });

  it("is bound to the device key: a stolen token is useless on another phone", async () => {
    const w = await world();
    const s = await founder(w);
    const thief = await FakeDevice.create();
    const res = await w.refresh(s.refresh, thief);
    expect(res.status).toBe(401);
    expect(await res.json()).toEqual({ error: "invalid_device_proof" });
    expect((await w.refresh(s.refresh)).status).toBe(200); // the owner is not logged out
  });

  it("refuses a stale timestamp, garbage and a forged token for a real sid without revoking", async () => {
    const w = await world();
    const s = await founder(w);
    expect((await w.refresh(s.refresh, w.device, w.now() - 301)).status).toBe(400);
    expect((await w.call(post("/auth/refresh", { refresh: "nope", ts: w.now(), sig: "x" }))).status).toBe(400);
    const forged = s.refresh.slice(0, -43) + "A".repeat(43);
    expect((await w.refresh(forged)).status).toBe(401);
    expect((await w.refresh(s.refresh)).status).toBe(200);
  });

  it("accepts the raw (P1363) signature form too", async () => {
    const w = await world();
    const s = await founder(w);
    const body = await w.device.refresh(s.refresh, w.now());
    const raw = await w.device.sign(
      `hot-refresh|v1|${btoa(String.fromCharCode(...new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s.refresh))))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "")}|${w.now()}`,
      false,
    );
    expect((await w.call(post("/auth/refresh", { ...body, sig: raw }))).status).toBe(200);
  });

  it("sliding 30 days, absolute 180 days", async () => {
    const storage = new MemoryStorage();
    const store = new KvAccountStore(storage);
    const t = 1_000_000;
    const login = await store.login({ provider: "whoop", subject: "1", bootstrap: true, now: t });
    if (login.status !== "ok") throw new Error();
    await store.createSession({ sid: "s", accountId: login.account.id, devicePub: "p", deviceName: "d", refreshHash: "h0", now: t });
    expect(await store.liveSession("s", t + SLIDING_TTL_MS - 1)).not.toBeNull();
    expect(await store.liveSession("s", t + SLIDING_TTL_MS)).toBeNull();
    // keep using it every 20 days: alive until the absolute end
    let now = t;
    let h = "h0";
    for (let i = 1; now + 20 * 86_400_000 < t + ABSOLUTE_TTL_MS; i++) {
      now += 20 * 86_400_000;
      const r = await store.rotate({ sid: "s", presentedHash: h, nextHash: `h${i}`, now });
      expect(r.status).toBe("rotated");
      h = `h${i}`;
    }
    expect(await store.liveSession("s", t + ABSOLUTE_TTL_MS)).toBeNull();
  });
});

describe("logout (A65 §3.4)", () => {
  it("«Выйти»: this session only", async () => {
    const w = await world();
    const a = await founder(w);
    const b = await founder(w); // second phone of the same account (same WHOOP identity)
    expect(b.account_id).toBe(a.account_id);
    expect((await w.call(post("/auth/logout", undefined, a.access))).status).toBe(204);
    expect((await w.refresh(a.refresh)).status).toBe(401);
    expect((await w.refresh(b.refresh)).status).toBe(200);
  });

  it("«Выйти везде»: every session, session_epoch moves on", async () => {
    const w = await world();
    const a = await founder(w);
    const b = await founder(w);
    expect((await w.call(post("/auth/logout-all", undefined, b.access))).status).toBe(204);
    expect((await w.refresh(a.refresh)).status).toBe(401);
    expect((await w.refresh(b.refresh)).status).toBe(401);
    expect((await w.call(post("/auth/logout-all", undefined, b.access))).status).toBe(401);
    expect(w.accounts.storage.dump()).toContain('"sessionEpoch":1');
  });
});

describe("POST /auth/migrate (A65 §3.5)", () => {
  it("turns hs1 into an account with the WHOOP identity; hs1 is spent, the WHOOP tokens stay", async () => {
    const w = await world({ ALLOWED_WHOOP_USER_IDS: "" });
    w.whoop.validAccess.add("whoop-access-0");
    const tokens = { accessToken: "whoop-access-0", refreshToken: "whoop-refresh-0", expiresAt: w.now() * 1000 + 3600_000, scope: "offline" };
    const hs1 = (await w.ns.entry("whoop-user:4242").vault.connect("4242", tokens, true))!;
    const n = await w.nonce();
    const res = await w.call(post("/auth/migrate", { nonce: n, ...(await w.device.login(n)) }, hs1));
    expect(res.status).toBe(200);
    const s = (await res.json()) as Session & { whoop_user_id: string };
    expect(s.whoop_user_id).toBe("4242");
    expect(s.identities).toEqual([expect.objectContaining({ provider: "whoop", subject: "4242" })]);
    expect((await w.call(post("/whoop/access", undefined, hs1))).status).toBe(401);
    const acc = await w.call(post("/whoop/access", undefined, s.access));
    expect(acc.status).toBe(200);
    expect(await acc.json()).toMatchObject({ access_token: "whoop-access-0" });
    // a second migration with the same hs1 fails
    const n2 = await w.nonce();
    expect((await w.call(post("/auth/migrate", { nonce: n2, ...(await w.device.login(n2)) }, hs1))).status).toBe(401);
  });

  it("refuses without hs1, with a forged hs1 or without the device proof", async () => {
    const w = await world();
    const n = await w.nonce();
    expect((await w.call(post("/auth/migrate", { nonce: n, ...(await w.device.login(n)) }))).status).toBe(401);
    expect((await w.call(post("/auth/migrate", { nonce: n, ...(await w.device.login(n)) }, "hs1.4242." + "A".repeat(43)))).status).toBe(401);
    const n2 = await w.nonce();
    expect((await w.call(post("/auth/migrate", { nonce: n2 }, "hs1.4242." + "A".repeat(43)))).status).toBe(400);
  });

  it("a Google account and the WHOOP account of the same person stay separate (no merge by e-mail)", async () => {
    const w = await world();
    const f = await founder(w);
    const { code } = (await (await w.call(post("/auth/invites", undefined, f.access))).json()) as { code: string };
    const g = (await (await w.googleLogin({ invite: code })).json()) as Session;
    expect(g.account_id).not.toBe(f.account_id);
    expect((await w.call(post("/whoop/access", undefined, g.access))).status).toBe(409);
  });
});

describe("small pieces", () => {
  it("invite codes: 26 Crockford symbols, typing slips are forgiven", () => {
    const code = newInviteCode();
    expect(code).toMatch(/^[0-9A-HJKMNP-TV-Z]{26}$/);
    expect(normaliseInvite(`${code.slice(0, 13).toLowerCase()}-${code.slice(13)}`)).toBe(code);
    expect(normaliseInvite("short")).toBeNull();
    expect(new Set(Array.from({ length: 50 }, newInviteCode)).size).toBe(50);
  });

  it("DER → raw ECDSA signature", () => {
    const raw = Uint8Array.from({ length: 64 }, (_, i) => (i === 0 || i === 32 ? 0x80 : i));
    expect(Array.from(derToRawP256(rawToDer(raw))!)).toEqual(Array.from(raw));
    const small = new Uint8Array(64);
    small[31] = 1;
    small[63] = 2;
    expect(Array.from(derToRawP256(rawToDer(small))!)).toEqual(Array.from(small));
    expect(derToRawP256(new Uint8Array([1, 2, 3]))).toBeNull();
  });

  it("the account store keeps no names, e-mails or tokens", async () => {
    const w = await world();
    const f = await founder(w);
    const dump = w.accounts.storage.dump();
    for (const s of [f.access, f.refresh, "private@example.com", "Private"]) expect(dump).not.toContain(s);
  });
});
