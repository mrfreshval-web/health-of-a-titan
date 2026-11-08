import { afterEach, describe, expect, it, vi } from "vitest";
import worker, { handle, ROUTES, VERSION } from "../src/index";
import { bearerToken, isAllowedModel, isAllowedWhoopUser, parseList } from "../src/allowlist";
import type { Env } from "../src/env";
import { FakeDevice, makeEnv, pkcePair } from "./helpers";

const req = (method: string, path: string, headers: Record<string, string> = {}, body?: unknown) =>
  new Request(`https://edge.test${path}`, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });

const post = (path: string, body?: unknown, token?: string) =>
  req("POST", path, token ? { Authorization: `Bearer ${token}` } : {}, body);

interface SessionBody {
  access: string;
  refresh: string;
  account_id: string;
  whoop_user_id: string;
  whoop_access_token: string;
  identities: Array<{ provider: string; subject: string }>;
}

/** Full login through the Worker: /whoop/authorize → (WHOOP redirect) → /whoop/token. */
async function login(
  env: Env,
  opts: { whoop?: ReturnType<typeof makeEnv>["whoop"]; code?: string; device?: FakeDevice; invite?: string; noProof?: boolean } = {},
) {
  const { verifier, challenge } = await pkcePair();
  const a = await handle(post("/whoop/authorize", { code_challenge: challenge }), env);
  const { state } = (await a.json()) as { state: string };
  const device = opts.device ?? (await FakeDevice.create());
  const proof = opts.noProof ? {} : await device.login(state);
  const res = await handle(
    post("/whoop/token", { code: opts.code ?? "good-code", state, code_verifier: verifier, ...proof, ...(opts.invite ? { invite: opts.invite } : {}) }),
    env,
    { fetch: opts.whoop?.fetch },
  );
  return res;
}

/** A legacy `hs1` session of slice 1, as the app before D-062 holds it. */
async function legacySession(ns: ReturnType<typeof makeEnv>["ns"], whoop: ReturnType<typeof makeEnv>["whoop"]): Promise<string> {
  whoop.validAccess.add("whoop-access-0");
  whoop.validRefresh.add("whoop-refresh-0");
  const tokens = { accessToken: "whoop-access-0", refreshToken: "whoop-refresh-0", expiresAt: Date.now() + 3600_000, scope: "offline" };
  return (await ns.entry("whoop-user:4242").vault.connect("4242", tokens, true))!;
}

describe("GET /health", () => {
  it("returns 200 with version and no-store", async () => {
    const res = await handle(req("GET", "/health"), {});
    expect(res.status).toBe(200);
    expect(res.headers.get("Cache-Control")).toBe("no-store");
    expect(await res.json()).toEqual({ status: "ok", service: "hot-edge", version: VERSION });
  });

  it("rejects other methods", async () => {
    expect((await handle(req("POST", "/health"), {})).status).toBe(405);
  });
});

describe("routes", () => {
  it("/whoop/refresh is removed: the refresh token never leaves the Durable Object (D-025)", async () => {
    const { env } = makeEnv();
    expect((await handle(post("/whoop/refresh", {}), env)).status).toBe(404);
    expect(ROUTES.some((r) => r.path === "/whoop/refresh")).toBe(false);
  });

  it("GET on a POST endpoint is 405, unknown path is 404", async () => {
    const { env } = makeEnv();
    expect((await handle(req("GET", "/whoop/token"), env)).status).toBe(405);
    expect((await handle(post("/nope"), env)).status).toBe(404);
    expect((await handle(post("/llm/"), env)).status).toBe(404);
  });

  it("WHOOP routes answer 503 not_configured without secrets", async () => {
    const { env } = makeEnv({ TOKEN_ENC_KEY: undefined });
    for (const path of ["/whoop/authorize", "/whoop/token", "/whoop/access", "/whoop/disconnect"]) {
      const res = await handle(post(path, {}), env);
      expect(res.status).toBe(503);
      expect(await res.json()).toEqual({ error: "not_configured" });
    }
  });

  it("account routes answer 503 without the signing key or the account store", async () => {
    for (const missing of [{ AUTH_SIGNING_KEY: undefined }, { ACCOUNT_VAULT: undefined }]) {
      const { env } = makeEnv(missing);
      for (const r of ROUTES.filter((x) => x.path.startsWith("/auth/"))) {
        expect((await handle(post(r.path, {}), env)).status).toBe(503);
      }
      expect((await handle(post("/whoop/token", {}), env)).status).toBe(503);
    }
  });

  it("the webhook is still a 501 stub (slice 3)", async () => {
    const { env } = makeEnv();
    expect((await handle(post("/whoop/webhook", {}), env)).status).toBe(501);
  });
});

describe("POST /whoop/authorize", () => {
  it("returns client id, scopes, redirect and a signed state", async () => {
    const { env } = makeEnv();
    const { challenge } = await pkcePair();
    const res = await handle(post("/whoop/authorize", { code_challenge: challenge }), env);
    expect(res.status).toBe(200);
    const body = (await res.json()) as Record<string, string>;
    expect(body.client_id).toBe("test-client");
    expect(body.redirect_uri).toBe("com.healthofatitan.app://oauth/whoop");
    expect(body.scope).toBe("offline read:recovery read:cycles read:sleep read:workout read:profile read:body_measurement");
    expect(body.authorization_endpoint).toBe("https://api.prod.whoop.com/oauth/oauth2/auth");
    const url = new URL(body.authorize_url!);
    expect(url.searchParams.get("state")).toBe(body.state);
    expect(url.searchParams.get("client_id")).toBe("test-client");
    expect(JSON.stringify(body)).not.toContain("test-secret");
  });

  it("needs a S256 challenge", async () => {
    const { env } = makeEnv();
    expect((await handle(post("/whoop/authorize", {}), env)).status).toBe(400);
    expect((await handle(post("/whoop/authorize", { code_challenge: "plain" }), env)).status).toBe(400);
  });

  it("is rate limited per IP when the binding exists", async () => {
    const limit = vi.fn(async () => ({ success: false }));
    const { env } = makeEnv({ RATE_LIMITER: { limit } as unknown as RateLimit });
    const res = await handle(req("POST", "/whoop/authorize", { "CF-Connecting-IP": "203.0.113.7" }, {}), env);
    expect(res.status).toBe(429);
    expect(limit).toHaveBeenCalledWith({ key: "/whoop/authorize|203.0.113.7" });
  });
});

describe("POST /whoop/token — «Войти через WHOOP» (A65 §2.2)", () => {
  it("exchanges the code, stores WHOOP tokens in the vault and returns an account session", async () => {
    const { env, whoop, ns } = makeEnv();
    const res = await login(env, { whoop });
    expect(res.status).toBe(200);
    const body = (await res.json()) as SessionBody & Record<string, unknown>;
    expect(body.whoop_user_id).toBe("4242");
    expect(body.whoop_access_token).toBe("whoop-access-1");
    expect(body.refresh).toMatch(/^hr1\./);
    expect(body.access.split(".")).toHaveLength(3);
    expect(body.identities).toEqual([expect.objectContaining({ provider: "whoop", subject: "4242" })]);
    expect(body).not.toHaveProperty("session");
    expect(body).not.toHaveProperty("refresh_token");
    expect(JSON.stringify(body)).not.toContain("private@example.com");
    const call = whoop.tokenCalls[0]!;
    expect(call.get("client_secret")).toBe("test-secret");
    expect(call.get("redirect_uri")).toBe("com.healthofatitan.app://oauth/whoop");
    const stored = ns.entry("whoop-user:4242").storage.dump();
    expect(stored).not.toContain("whoop-refresh-1");
    expect(stored.length).toBeGreaterThan(10);
  });

  it("a second WHOOP login lands in the same account", async () => {
    const { env, whoop } = makeEnv();
    const a = (await (await login(env, { whoop })).json()) as SessionBody;
    const b = (await (await login(env, { whoop })).json()) as SessionBody;
    expect(b.account_id).toBe(a.account_id);
    expect(b.refresh).not.toBe(a.refresh);
  });

  it("needs the device proof over the state", async () => {
    const { env, whoop } = makeEnv();
    const res = await login(env, { whoop, noProof: true });
    expect(res.status).toBe(400);
    expect(await res.json()).toEqual({ error: "invalid_device_proof" });
    expect(whoop.tokenCalls).toHaveLength(0);
  });

  it("refuses a code without the matching verifier (intercepted redirect)", async () => {
    const { env, whoop } = makeEnv();
    const { challenge } = await pkcePair();
    const thief = await pkcePair();
    const a = await handle(post("/whoop/authorize", { code_challenge: challenge }), env);
    const { state } = (await a.json()) as { state: string };
    const res = await handle(post("/whoop/token", { code: "good-code", state, code_verifier: thief.verifier }), env, { fetch: whoop.fetch });
    expect(res.status).toBe(400);
    expect(await res.json()).toEqual({ error: "invalid_state" });
    expect(whoop.tokenCalls).toHaveLength(0);
  });

  it("a new WHOOP user without an invite gets only the user id; nothing is stored, the grant is revoked", async () => {
    const { env, whoop, ns, accounts } = makeEnv({ ALLOWED_WHOOP_USER_IDS: "" });
    const res = await login(env, { whoop });
    expect(res.status).toBe(403);
    expect(await res.json()).toEqual({ error: "invite_required", whoop_user_id: "4242" });
    expect(ns.vaults.size).toBe(0);
    expect(whoop.revoked).toEqual(["whoop-access-1"]);
    expect(accounts.storage.dump()).not.toContain("4242");
  });

  it("a bad invite is 403 invite_invalid", async () => {
    const { env, whoop } = makeEnv({ ALLOWED_WHOOP_USER_IDS: "" });
    const res = await login(env, { whoop, invite: "0123456789ABCDEFGHJKMNPQRS" });
    expect(res.status).toBe(403);
    expect(await res.json()).toMatchObject({ error: "invite_invalid" });
  });

  it("a bad code is 400 invalid_code, WHOOP down is 502", async () => {
    const { env, whoop } = makeEnv();
    expect((await login(env, { whoop, code: "bad" })).status).toBe(400);
    whoop.failNext = "down";
    const res = await login(env, { whoop });
    expect(res.status).toBe(502);
    expect(await res.json()).toEqual({ error: "whoop_unavailable" });
  });
});

describe("WHOOP session routes", () => {
  it("/whoop/access returns an access token for the account session and 401 otherwise", async () => {
    const { env, whoop } = makeEnv();
    const { access } = (await (await login(env, { whoop })).json()) as SessionBody;
    const ok = await handle(post("/whoop/access", undefined, access), env);
    expect(ok.status).toBe(200);
    expect(await ok.json()).toMatchObject({ access_token: "whoop-access-1" });
    expect((await handle(post("/whoop/access"), env)).status).toBe(401);
    expect((await handle(post("/whoop/access", undefined, "whoop-access-1"), env)).status).toBe(401);
    expect((await handle(post("/whoop/access", undefined, access.slice(0, -2) + (access.endsWith("AA") ? "BB" : "AA")), env)).status).toBe(401);
  });

  it("«Выйти везде» stops /whoop/access at once (live session check)", async () => {
    const { env, whoop } = makeEnv();
    const { access } = (await (await login(env, { whoop })).json()) as SessionBody;
    expect((await handle(post("/auth/logout-all", undefined, access), env)).status).toBe(204);
    expect((await handle(post("/whoop/access", undefined, access), env)).status).toBe(401);
  });

  it("/whoop/disconnect wipes the vault; the account stays and WHOOP is just not connected", async () => {
    const { env, whoop, ns } = makeEnv();
    const { access } = (await (await login(env, { whoop })).json()) as SessionBody;
    const res = await handle(post("/whoop/disconnect", undefined, access), env);
    expect(res.status).toBe(204);
    expect(ns.entry("whoop-user:4242").storage.map.size).toBe(0);
    expect(whoop.revoked).toEqual(["whoop-access-1"]);
    const again = await handle(post("/whoop/access", undefined, access), env);
    expect(again.status).toBe(401);
    expect(await again.json()).toEqual({ error: "reauth_required" });
  });

  it("legacy hs1 sessions keep working until they migrate (A65 §3.5)", async () => {
    const { env, whoop, ns } = makeEnv();
    const hs1 = await legacySession(ns, whoop);
    const ok = await handle(post("/whoop/access", undefined, hs1), env);
    expect(ok.status).toBe(200);
    env.ALLOWED_WHOOP_USER_IDS = "1";
    const res = await handle(post("/whoop/access", undefined, hs1), env);
    expect(res.status).toBe(403);
    expect(await res.json()).toEqual({ error: "not_allowed" });
  });

  const appPaths = ROUTES.filter((r) => r.auth === "app" && !r.implemented).map((r) => (r.path.endsWith("/") ? `${r.path}anthropic/v1/messages` : r.path));

  it.each(appPaths)("AppAuth endpoint %s needs an account access token, then is a 501 stub", async (path) => {
    const { env, whoop, ns } = makeEnv();
    expect((await handle(post(path), env)).status).toBe(401);
    expect((await handle(post(path, undefined, "some-whoop-access-token"), env)).status).toBe(401);
    expect((await handle(post(path, undefined, await legacySession(ns, whoop)), env)).status).toBe(401);
    const { access } = (await (await login(env, { whoop })).json()) as SessionBody;
    const res = await handle(post(path, undefined, access), env);
    expect(res.status).toBe(501);
    expect(await res.json()).toMatchObject({ error: "not_implemented" });
  });
});

describe("allowlist", () => {
  it("parses lists", () => {
    expect(parseList(" a, b\nc ")).toEqual(["a", "b", "c"]);
    expect(parseList(undefined)).toEqual([]);
  });

  it("fails closed", () => {
    const env: Env = { LLM_MODELS: "claude-opus-5-5", ALLOWED_WHOOP_USER_IDS: "12345, 67890" };
    expect(isAllowedWhoopUser(12345, env)).toBe(true);
    expect(isAllowedWhoopUser("99999", env)).toBe(false);
    expect(isAllowedWhoopUser("12345", {})).toBe(false);
    expect(isAllowedModel("claude-opus-5-5", env)).toBe(true);
    expect(isAllowedModel("other-model", env)).toBe(false);
    expect(isAllowedModel(undefined, env)).toBe(false);
  });

  it("extracts bearer tokens", () => {
    expect(bearerToken(req("GET", "/", { Authorization: "Bearer abc" }))).toBe("abc");
    expect(bearerToken(req("GET", "/", { Authorization: "Basic abc" }))).toBeNull();
    expect(bearerToken(req("GET", "/"))).toBeNull();
  });
});

describe("logging", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it("never logs bodies, tokens, user ids or query strings — through a whole login and refresh", async () => {
    const lines: string[] = [];
    for (const m of ["log", "info", "warn", "error", "debug"] as const) {
      vi.spyOn(console, m).mockImplementation((...args: unknown[]) => {
        lines.push(args.map(String).join(" "));
      });
    }
    const { env, whoop } = makeEnv();
    vi.stubGlobal("fetch", whoop.fetch);
    const device = await FakeDevice.create();
    const { verifier, challenge } = await pkcePair();
    const a = await worker.fetch(post("/whoop/authorize?secret=1", { code_challenge: challenge }), env);
    const { state } = (await a.json()) as { state: string };
    const t = await worker.fetch(post("/whoop/token", { code: "good-code", state, code_verifier: verifier, ...(await device.login(state)) }), env);
    const { access, refresh, account_id } = (await t.json()) as SessionBody;
    await worker.fetch(post("/whoop/access", undefined, access), env);
    await worker.fetch(post("/stt", undefined, access), env);
    const r = await worker.fetch(post("/auth/refresh", await device.refresh(refresh, Math.floor(Date.now() / 1000))), env);
    expect(r.status).toBe(200);
    await worker.fetch(post("/whoop/disconnect", undefined, access), env);
    await worker.fetch(post("/auth/logout", undefined, access), env);

    const logged = lines.join("\n");
    expect(lines.length).toBeGreaterThan(0);
    for (const secret of ["secret=1", "good-code", state, verifier, access, refresh, account_id, device.pub, "whoop-access-1", "whoop-refresh-1", "test-secret", "4242"]) {
      expect(logged).not.toContain(secret);
    }
  });
});
