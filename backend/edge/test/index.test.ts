import { describe, expect, it, vi } from "vitest";
import worker, { handle, ROUTES, VERSION } from "../src/index";
import { bearerToken, isAllowedModel, isAllowedWhoopUser, parseList } from "../src/allowlist";
import type { Env } from "../src/env";

const env: Env = { LLM_MODELS: "claude-opus-5-5", ALLOWED_WHOOP_USER_IDS: "12345, 67890" };
const req = (method: string, path: string, headers: Record<string, string> = {}) =>
  new Request(`https://edge.test${path}`, { method, headers });

describe("GET /health", () => {
  it("returns 200 with version and no-store", async () => {
    const res = await handle(req("GET", "/health"), env);
    expect(res.status).toBe(200);
    expect(res.headers.get("Cache-Control")).toBe("no-store");
    expect(await res.json()).toEqual({ status: "ok", service: "hot-edge", version: VERSION });
  });

  it("rejects other methods", async () => {
    expect((await handle(req("POST", "/health"), env)).status).toBe(405);
  });
});

describe("A14 §5 endpoint stubs", () => {
  const paths = ["/whoop/token", "/whoop/refresh", "/whoop/webhook", "/push/register", "/llm/anthropic/v1/messages", "/stt", "/tts"];

  it.each(paths)("POST %s is a 501 stub (with bearer where AppAuth)", async (path) => {
    const res = await handle(req("POST", path, { Authorization: "Bearer test-token" }), env);
    expect(res.status).toBe(501);
    expect(res.headers.get("Cache-Control")).toBe("no-store");
    expect(await res.json()).toMatchObject({ error: "not_implemented" });
  });

  it.each(ROUTES.filter((r) => r.auth === "app").map((r) => (r.path.endsWith("/") ? `${r.path}anthropic/v1/messages` : r.path)))(
    "AppAuth endpoint %s requires a bearer token",
    async (path) => {
      expect((await handle(req("POST", path), env)).status).toBe(401);
    },
  );

  it("GET on a POST endpoint is 405, unknown path is 404", async () => {
    expect((await handle(req("GET", "/whoop/token"), env)).status).toBe(405);
    expect((await handle(req("POST", "/nope"), env)).status).toBe(404);
    expect((await handle(req("POST", "/llm/"), env)).status).toBe(404);
  });
});

describe("allowlist", () => {
  it("parses lists", () => {
    expect(parseList(" a, b\nc ")).toEqual(["a", "b", "c"]);
    expect(parseList(undefined)).toEqual([]);
  });

  it("fails closed", () => {
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
  it("never logs bodies, tokens or paths with queries", async () => {
    const spy = vi.spyOn(console, "log").mockImplementation(() => {});
    await worker.fetch(req("POST", "/stt?secret=1", { Authorization: "Bearer very-secret" }), env);
    const logged = spy.mock.calls.flat().join(" ");
    expect(logged).not.toContain("very-secret");
    expect(logged).not.toContain("secret=1");
    spy.mockRestore();
  });
});
