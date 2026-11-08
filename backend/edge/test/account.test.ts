import { describe, expect, it } from "vitest";
import type { Env } from "../src/env";
import { GoogleIdTokenVerifier } from "../src/google";
import { handle, type HandleOptions } from "../src/index";
import { FakeDevice, FakeGoogle, makeEnv, pkcePair } from "./helpers";

/** Slices А3 (identities, devices, step-up) and А5 (deletion) of A65. */

const T0 = 1_800_000_000; // seconds

const post = (path: string, body?: unknown, token?: string) =>
  new Request(`https://edge.test${path}`, {
    method: "POST",
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: body === undefined ? undefined : JSON.stringify(body),
  });

const get = (path: string, token?: string) =>
  new Request(`https://edge.test${path}`, { method: "GET", headers: token ? { Authorization: `Bearer ${token}` } : {} });

interface Session {
  access: string;
  refresh: string;
  account_id: string;
  identities: Array<{ provider: string; subject: string; linked_device?: string }>;
}

interface AccountView {
  account_id: string;
  identities: Array<{ provider: string; subject: string; linked_at: number; linked_device?: string }>;
  devices: Array<{ id: string; name: string; current: boolean; created_at: number; last_used_at: number }>;
  step_up_until: number;
}

async function world(overrides: Partial<Env> = {}) {
  const google = await FakeGoogle.create();
  const { env, whoop, ns, accounts } = makeEnv(overrides);
  let now = T0;
  const verifier = new GoogleIdTokenVerifier(google.fetch, () => now * 1000);
  const opts = (): HandleOptions => ({ nowSeconds: now, google: verifier, fetch: whoop.fetch });
  const call = (r: Request) => handle(r, env, opts());
  const device = await FakeDevice.create();
  const nonce = async () => ((await (await call(post("/auth/nonce"))).json()) as { nonce: string }).nonce;
  const idToken = async (sub?: string) => {
    const n = await nonce();
    return { n, token: await google.idToken(google.claims(n, now, sub)) };
  };
  const googleLogin = async (extra: Record<string, unknown> = {}, sub?: string, dev = device) => {
    const { n, token } = await idToken(sub);
    return call(post("/auth/google", { id_token: token, ...(await dev.login(n)), ...extra }));
  };
  const refresh = async (token: string, dev = device) => call(post("/auth/refresh", await dev.refresh(token, now)));
  const account = async (access: string) => (await (await call(get("/auth/account", access))).json()) as AccountView;
  /** The WHOOP browser round trip with an intent, as the phone does it with a session. */
  const whoopIntent = async (intent: "link" | "reauth", access: string, as = access) => {
    const { verifier: v, challenge } = await pkcePair();
    const a = await call(post("/whoop/authorize", { code_challenge: challenge, intent }, access));
    if (a.status !== 200) return a;
    const { state } = (await a.json()) as { state: string };
    return call(post("/whoop/token", { code: "good-code", state, code_verifier: v, intent }, as));
  };
  return {
    env,
    whoop,
    ns,
    accounts,
    google,
    device,
    call,
    nonce,
    idToken,
    googleLogin,
    refresh,
    account,
    whoopIntent,
    advance: (s: number) => {
      now += s;
    },
    now: () => now,
  };
}

type World = Awaited<ReturnType<typeof world>>;

/** The founder: WHOOP 4242, migrated from hs1 (inviter). */
async function founder(w: World, dev = w.device): Promise<Session> {
  w.whoop.validAccess.add("whoop-access-0");
  w.whoop.validRefresh.add("whoop-refresh-0");
  const tokens = { accessToken: "whoop-access-0", refreshToken: "whoop-refresh-0", expiresAt: Date.now() + 3600_000, scope: "offline" };
  const hs1 = (await w.ns.entry("whoop-user:4242").vault.connect("4242", tokens, true))!;
  const n = await w.nonce();
  const res = await w.call(post("/auth/migrate", { nonce: n, ...(await dev.login(n)) }, hs1));
  expect(res.status).toBe(200);
  return (await res.json()) as Session;
}

/** A Google-only account made with the founder's invite. */
async function googleAccount(w: World, f: Session, sub = "g-friend"): Promise<Session> {
  const { code } = (await (await w.call(post("/auth/invites", undefined, f.access))).json()) as { code: string };
  const friend = await FakeDevice.create();
  const res = await w.googleLogin({ invite: code }, sub, friend);
  expect(res.status).toBe(200);
  return (await res.json()) as Session;
}

describe("GET /auth/account (A65 §3.4)", () => {
  it("lists identities and devices; the current one is marked; no session ids or e-mails", async () => {
    const w = await world();
    const a = await founder(w);
    const tablet = await FakeDevice.create();
    w.advance(5);
    const b = await founder(w, tablet);
    const view = await w.account(a.access);
    expect(view.account_id).toBe(a.account_id);
    expect(view.identities).toEqual([expect.objectContaining({ provider: "whoop", subject: "4242" })]);
    expect(view.devices).toHaveLength(2);
    expect(view.devices.filter((d) => d.current)).toHaveLength(1);
    expect(view.devices[0]!.current).toBe(true);
    expect(view.devices[0]!.name).toBe("Pixel 9 Pro XL");
    const sid = a.refresh.split(".")[1]!;
    expect(JSON.stringify(view)).not.toContain(sid);
    expect(JSON.stringify(view)).not.toContain("private@example.com");
    expect(view.step_up_until).toBe(T0 * 1000 + 600_000);
    expect((await w.account(b.access)).devices.find((d) => d.current)!.id).toBe(view.devices[1]!.id);
    expect((await w.call(get("/auth/account"))).status).toBe(401);
  });

  it("«Завершить» ends one device; a device of another account is not found", async () => {
    const w = await world();
    const a = await founder(w);
    const tablet = await FakeDevice.create();
    const b = await founder(w, tablet);
    const other = await googleAccount(w, a);
    const view = await w.account(a.access);
    const tabletId = view.devices.find((d) => !d.current)!.id;
    expect((await w.call(post("/auth/devices/revoke", { id: tabletId }, other.access))).status).toBe(404);
    expect((await w.call(post("/auth/devices/revoke", { id: tabletId }, a.access))).status).toBe(204);
    expect((await w.refresh(b.refresh, tablet)).status).toBe(401);
    expect((await w.refresh(a.refresh)).status).toBe(200);
    expect((await w.account(a.access)).devices).toHaveLength(1);
    expect((await w.call(post("/auth/devices/revoke", { id: 42 }, a.access))).status).toBe(400);
  });
});

describe("linking (A65 §2.3)", () => {
  it("a fresh WHOOP session links Google; then Google signs in to the same account", async () => {
    const w = await world();
    const f = await founder(w);
    const { token } = await w.idToken("g-founder");
    const res = await w.call(post("/auth/link/google", { id_token: token }, f.access));
    expect(res.status).toBe(200);
    const body = (await res.json()) as { identities: Session["identities"]; linked: boolean };
    expect(body.linked).toBe(true);
    expect(body.identities.map((i) => i.provider).sort()).toEqual(["google", "whoop"]);
    expect(body.identities.find((i) => i.provider === "google")!.linked_device).toBe("Pixel 9 Pro XL");
    const login = await w.googleLogin({}, "g-founder");
    expect(login.status).toBe(200);
    expect(((await login.json()) as Session).account_id).toBe(f.account_id);
    expect(w.accounts.storage.dump()).toContain("identity.linked");
  });

  it("after 10 minutes linking needs a step-up; WHOOP reauth gives it", async () => {
    const w = await world();
    const f = await founder(w);
    w.advance(11 * 60);
    const fresh = (await (await w.refresh(f.refresh)).json()) as Session;
    const { token } = await w.idToken("g-founder");
    const stale = await w.call(post("/auth/link/google", { id_token: token }, fresh.access));
    expect(stale.status).toBe(403);
    expect(await stale.json()).toEqual({ error: "step_up_required" });

    const reauth = await w.whoopIntent("reauth", fresh.access);
    expect(reauth.status).toBe(200);
    const r = (await reauth.json()) as { step_up_until: number; whoop_access_token: string };
    expect(r.step_up_until).toBe(w.now() * 1000 + 600_000);
    expect(r.whoop_access_token).toMatch(/^whoop-access-/);

    const second = await w.idToken("g-founder");
    expect((await w.call(post("/auth/link/google", { id_token: second.token }, fresh.access))).status).toBe(200);
  });

  it("the nonce of a link is used once", async () => {
    const w = await world();
    const f = await founder(w);
    const { token } = await w.idToken("g-founder");
    await w.call(post("/auth/link/google", { id_token: token }, f.access));
    const again = await w.call(post("/auth/link/google", { id_token: token }, f.access));
    expect(again.status).toBe(401);
    expect(await again.json()).toEqual({ error: "nonce_used" });
  });

  it("a Google of another account is 409 identity_in_use; nothing merges", async () => {
    const w = await world();
    const f = await founder(w);
    const g = await googleAccount(w, f, "g-taken");
    const { token } = await w.idToken("g-taken");
    const res = await w.call(post("/auth/link/google", { id_token: token }, f.access));
    expect(res.status).toBe(409);
    expect(await res.json()).toEqual({ error: "identity_in_use", provider: "google" });
    expect((await w.account(f.access)).identities).toHaveLength(1);
    expect((await w.account(g.access)).identities).toHaveLength(1);
  });

  it("one identity per provider: a second Google is 409 provider_linked", async () => {
    const w = await world();
    const f = await founder(w);
    await w.call(post("/auth/link/google", { id_token: (await w.idToken("g-1")).token }, f.access));
    const res = await w.call(post("/auth/link/google", { id_token: (await w.idToken("g-2")).token }, f.access));
    expect(res.status).toBe(409);
    expect(await res.json()).toEqual({ error: "provider_linked", provider: "google" });
  });

  it("a Google account connects WHOOP (intent=link) and gets its tokens", async () => {
    const w = await world();
    const f = await founder(w);
    const g = await googleAccount(w, f);
    w.whoop.userId = "5151";
    const res = await w.whoopIntent("link", g.access);
    expect(res.status).toBe(200);
    const body = (await res.json()) as { identities: Session["identities"]; whoop_user_id: string; whoop_access_token: string };
    expect(body.whoop_user_id).toBe("5151");
    expect(body.identities.map((i) => i.provider).sort()).toEqual(["google", "whoop"]);
    const access = await w.call(post("/whoop/access", undefined, g.access));
    expect(access.status).toBe(200);
    // the same WHOOP again just reconnects its tokens
    const again = await w.whoopIntent("link", g.access);
    expect(again.status).toBe(200);
    expect(((await again.json()) as { linked: boolean }).linked).toBe(false);
  });

  it("a stale Google session must step up before WHOOP is linked: refused at authorize", async () => {
    const w = await world();
    const f = await founder(w);
    const g = await googleAccount(w, f);
    w.advance(11 * 60);
    w.whoop.userId = "5151";
    const res = await w.whoopIntent("link", g.access);
    expect(res.status).toBe(403);
    expect(await res.json()).toEqual({ error: "step_up_required" });
    const { token } = await w.idToken("g-friend");
    expect((await w.call(post("/auth/reauth/google", { id_token: token }, g.access))).status).toBe(200);
    expect((await w.whoopIntent("link", g.access)).status).toBe(200);
  });

  it("a WHOOP of another account is 409 and its grant stays (the other account still syncs)", async () => {
    const w = await world();
    const f = await founder(w);
    const g = await googleAccount(w, f);
    const res = await w.whoopIntent("link", g.access); // WHOOP 4242 belongs to the founder
    expect(res.status).toBe(409);
    expect(await res.json()).toEqual({ error: "identity_in_use", provider: "whoop" });
    expect(w.whoop.revoked).toEqual([]);
    expect((await w.call(post("/whoop/access", undefined, f.access))).status).toBe(200);
    expect((await w.call(post("/whoop/access", undefined, g.access))).status).toBe(409);
  });

  it("the state of a link is bound to the session that started it", async () => {
    const w = await world();
    const f = await founder(w);
    const g = await googleAccount(w, f);
    w.whoop.userId = "5151";
    const stolen = await w.whoopIntent("link", g.access, f.access);
    expect(stolen.status).toBe(400);
    expect(await stolen.json()).toEqual({ error: "invalid_state" });
    const { challenge } = await pkcePair();
    expect((await w.call(post("/whoop/authorize", { code_challenge: challenge, intent: "link" }))).status).toBe(401);
    expect((await w.call(post("/whoop/authorize", { code_challenge: challenge, intent: "merge" }, g.access))).status).toBe(400);
  });

  it("reauth with a foreign identity is refused", async () => {
    const w = await world();
    const f = await founder(w);
    const g = await googleAccount(w, f);
    const { token } = await w.idToken("someone-else");
    const res = await w.call(post("/auth/reauth/google", { id_token: token }, g.access));
    expect(res.status).toBe(403);
    expect(await res.json()).toEqual({ error: "identity_mismatch" });
    // WHOOP reauth needs an account with WHOOP
    expect((await w.whoopIntent("reauth", g.access)).status).toBe(409);
  });
});

describe("unlinking (A65 §2.3)", () => {
  it("the last identity cannot go", async () => {
    const w = await world();
    const f = await founder(w);
    const res = await w.call(post("/auth/unlink", { provider: "whoop" }, f.access));
    expect(res.status).toBe(409);
    expect(await res.json()).toEqual({ error: "last_identity" });
  });

  it("unlinking WHOOP revokes and wipes its tokens; Google stays the way in", async () => {
    const w = await world();
    const f = await founder(w);
    await w.call(post("/auth/link/google", { id_token: (await w.idToken("g-founder")).token }, f.access));
    const res = await w.call(post("/auth/unlink", { provider: "whoop" }, f.access));
    expect(res.status).toBe(200);
    expect(((await res.json()) as { identities: unknown[] }).identities).toEqual([expect.objectContaining({ provider: "google" })]);
    expect(w.whoop.revoked.length).toBe(1);
    expect((await w.call(post("/whoop/access", undefined, f.access))).status).toBe(409);
    expect((await w.googleLogin({}, "g-founder")).status).toBe(200);
    expect((await w.call(post("/auth/unlink", { provider: "whoop" }, f.access))).status).toBe(404);
    expect((await w.call(post("/auth/unlink", { provider: "apple" }, f.access))).status).toBe(400);
  });

  it("needs a step-up", async () => {
    const w = await world();
    const f = await founder(w);
    await w.call(post("/auth/link/google", { id_token: (await w.idToken("g-founder")).token }, f.access));
    w.advance(11 * 60);
    const next = (await (await w.refresh(f.refresh)).json()) as Session;
    expect((await w.call(post("/auth/unlink", { provider: "google" }, next.access))).status).toBe(403);
  });
});

describe("deletion (A65 §6, slice А5)", () => {
  it("needs a step-up, then deletes everything; the phone hears account_deleted", async () => {
    const w = await world();
    const f = await founder(w);
    await w.call(post("/auth/link/google", { id_token: (await w.idToken("g-founder")).token }, f.access));
    const tablet = await FakeDevice.create();
    const t = await founder(w, tablet);
    w.advance(11 * 60);
    const next = (await (await w.refresh(f.refresh)).json()) as Session;
    expect((await w.call(post("/auth/delete", undefined, next.access))).status).toBe(403);
    expect((await w.call(post("/auth/reauth/google", { id_token: (await w.idToken("g-founder")).token }, next.access))).status).toBe(200);

    const res = await w.call(post("/auth/delete", undefined, next.access));
    expect(res.status).toBe(200);
    expect(await res.json()).toEqual({ deleted: true });

    // WHOOP: revoked at WHOOP and wiped
    expect(w.whoop.revoked.length).toBe(1);
    // every device of the account learns it on its next refresh
    for (const [refresh, dev] of [
      [next.refresh, w.device],
      [t.refresh, tablet],
    ] as const) {
      const r = await w.refresh(refresh, dev);
      expect(r.status).toBe(401);
      expect(await r.json()).toEqual({ error: "account_deleted" });
    }
    expect((await w.call(get("/auth/account", next.access))).status).toBe(401);
    // the identities are free: no account for this Google any more
    expect((await w.googleLogin({}, "g-founder")).status).toBe(404);
    // the store keeps no subject and no account id
    const dump = w.accounts.storage.dump();
    for (const s of ["4242", "g-founder", f.account_id, "Pixel 9 Pro XL"]) expect(dump).not.toContain(s);
    expect(dump).toContain("account.deleted");
  });

  it("a tombstone lives past the last refresh token, then the answer is a plain invalid_grant", async () => {
    const w = await world();
    const f = await founder(w);
    await w.call(post("/auth/link/google", { id_token: (await w.idToken("g-founder")).token }, f.access));
    w.advance(11 * 60);
    const next = (await (await w.refresh(f.refresh)).json()) as Session;
    expect((await w.call(post("/auth/reauth/google", { id_token: (await w.idToken("g-founder")).token }, next.access))).status).toBe(200);
    expect((await w.call(post("/auth/delete", undefined, next.access))).status).toBe(200);
    w.advance((180 + 29) * 24 * 60 * 60);
    expect(await (await w.refresh(next.refresh)).json()).toEqual({ error: "account_deleted" });
    w.advance(2 * 24 * 60 * 60);
    expect(await (await w.refresh(next.refresh)).json()).toEqual({ error: "invalid_grant" });
    expect(w.accounts.storage.dump()).not.toContain("tomb:");
  });

  it("a failing WHOOP vault leaves the account in place; a retry wipes the tokens and deletes", async () => {
    const w = await world();
    const f = await founder(w);
    const vault = w.ns.entry("whoop-user:4242").storage;
    expect(vault.dump()).not.toBe("[]");
    const real = w.ns.get.bind(w.ns);
    w.ns.get = () => ({
      fetch: async () => {
        throw new Error("vault unavailable");
      },
    });
    const failed = await w.call(post("/auth/delete", undefined, f.access)).catch(() => null);
    expect(failed === null || failed.status >= 500).toBe(true);
    // nothing is deleted: the session works, so the deletion can be repeated
    expect((await w.call(get("/auth/account", f.access))).status).toBe(200);
    w.ns.get = real;
    const res = await w.call(post("/auth/delete", undefined, f.access));
    expect(res.status).toBe(200);
    // no WHOOP tokens stay on the server
    expect(vault.dump()).toBe("[]");
    expect(w.whoop.revoked.length).toBe(1);
  });

  it("the founder's used invite forgets the deleted account", async () => {
    const w = await world();
    const f = await founder(w);
    const g = await googleAccount(w, f);
    const { code } = (await (await w.call(post("/auth/invites", undefined, f.access))).json()) as { code: string };
    expect(code).toBeTruthy();
    expect((await w.call(post("/auth/delete", undefined, g.access))).status).toBe(200);
    expect(w.accounts.storage.dump()).not.toContain(g.account_id);
    // the founder is untouched
    expect((await w.account(f.access)).identities).toHaveLength(1);
  });
});

describe("web page of deletion (Google Play)", () => {
  it("is static, strict and asks for nothing but a Google sign-in", async () => {
    const w = await world();
    const page = await w.call(get("/account/delete"));
    expect(page.status).toBe(200);
    expect(page.headers.get("Content-Type")).toContain("text/html");
    const csp = page.headers.get("Content-Security-Policy") ?? "";
    expect(csp).toContain("default-src 'none'");
    expect(csp).toContain("frame-ancestors 'none'");
    expect(page.headers.get("Set-Cookie")).toBeNull();
    const html = await page.text();
    expect(html).toContain('data-client-id="test-web-client.apps.googleusercontent.com"');
    expect(html).not.toMatch(/<input[^>]+type="(text|email|tel)"/);
    expect(html).not.toMatch(/<script>(?!<\/script>)/);
    const js = await w.call(get("/account/delete.js"));
    expect(js.headers.get("Content-Type")).toContain("javascript");
    expect(await js.text()).toContain("/account/delete");
  });

  it("without the Google client id the page only explains the way through the app", async () => {
    const w = await world({ GOOGLE_WEB_CLIENT_ID: undefined });
    const html = await (await w.call(get("/account/delete"))).text();
    expect(html).toContain('data-client-id=""');
    expect(html).not.toContain("gsi/client");
  });

  it("deletes the account of the Google that signed in on the page", async () => {
    const w = await world();
    const f = await founder(w);
    const g = await googleAccount(w, f, "g-web");
    const { token } = await w.idToken("g-web");
    const logins = (s: string) => s.split("login.google").length - 1;
    const before = logins(w.accounts.storage.dump());
    const res = await w.call(post("/account/delete", { id_token: token }));
    expect(res.status).toBe(200);
    // the page signs nobody in: no new session, no «login» line in the audit
    expect(logins(w.accounts.storage.dump())).toBe(before);
    const r = await w.call(post("/auth/refresh", await w.device.refresh(g.refresh, w.now())));
    expect(r.status).toBe(401);
    const replay = await w.call(post("/account/delete", { id_token: token }));
    expect(replay.status).toBe(401);
    const none = await w.call(post("/account/delete", { id_token: (await w.idToken("nobody")).token }));
    expect(none.status).toBe(404);
    expect((await w.call(post("/account/delete", { id_token: "garbage" }))).status).toBe(401);
  });

  it("GET and POST share the path; other methods are 405", async () => {
    const w = await world();
    const res = await w.call(new Request("https://edge.test/account/delete", { method: "PUT" }));
    expect(res.status).toBe(405);
    expect(res.headers.get("Allow")).toBe("GET, POST");
  });
});
