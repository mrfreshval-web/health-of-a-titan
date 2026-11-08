import { describe, expect, it } from "vitest";
import { handle } from "../src/index";
import type { Env } from "../src/env";
import { costUsd, DEFAULT_LIMITS, MentorMeter } from "../src/mentor/budget";
import { mentorTurn, parseMentorRequest, type Meter } from "../src/mentor/mentor";
import { contextPrompt, STABLE_PROMPT } from "../src/mentor/prompt";
import { route } from "../src/mentor/router";
import { makeEnv, MemoryStorage, pkcePair } from "./helpers";

const post = (path: string, body?: unknown, token?: string) =>
  new Request(`https://edge.test${path}`, {
    method: "POST",
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: body === undefined ? undefined : JSON.stringify(body),
  });

const turn = (text: string, extra: Record<string, unknown> = {}) => ({
  v: 1,
  persona: "bro",
  crisis_level: 0,
  context: { address_form: "m", restrictions: [], flags: [], summaries: [] },
  messages: [{ role: "user", content: text }],
  ...extra,
});

/** A fake Anthropic Messages API that records each request body. */
class FakeAnthropic {
  calls: Record<string, unknown>[] = [];
  headers: Headers[] = [];
  reply = "Присед 4×6, отдых 150 с. Держи спину.";
  status = 200;
  stopReason = "end_turn";
  readonly fetch = async (input: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
    const request = new Request(input, init);
    expect(new URL(request.url).hostname).toBe("api.anthropic.com");
    this.headers.push(request.headers);
    const body = (await request.json()) as Record<string, unknown>;
    this.calls.push(body);
    if (this.status !== 200) return Response.json({ type: "error", error: { type: "authentication_error", message: "x" } }, { status: this.status });
    return Response.json({
      id: "msg_1",
      type: "message",
      role: "assistant",
      model: body.model,
      content: [
        { type: "thinking", thinking: "", signature: "s" },
        { type: "text", text: this.reply },
      ],
      stop_reason: this.stopReason,
      stop_sequence: null,
      usage: { input_tokens: 1000, output_tokens: 200, cache_read_input_tokens: 0, cache_creation_input_tokens: 0 },
    });
  };
}

class FakeMeter implements Meter {
  state: Awaited<ReturnType<Meter["reserve"]>> = "ok";
  charged: number[] = [];
  async reserve() {
    return this.state;
  }
  async charge(usd: number) {
    this.charged.push(usd);
    return this.state;
  }
}

const parsed = (body: Record<string, unknown>) => {
  const p = parseMentorRequest(body);
  if (!p.ok) throw new Error(p.error);
  return p.value;
};

const env: Env = { ANTHROPIC_API_KEY: "test-key-not-real", LLM_MODELS: "claude-opus-5-5,claude-sonnet-5-5,claude-haiku-5-5" };

describe("topic routing (A62 §2, block 2 §9)", () => {
  it("chatter goes to Haiku", () => {
    expect(route({ text: "Привет! Как дела?" })).toMatchObject({ tier: "light", model: "claude-haiku-5-5" });
    // «больше» is not «боль», «кровать» is not «кровь»
    expect(route({ text: "Хочу больше спать, кровать новая" }).tier).toBe("light");
  });

  it("health, medicines, ED signals and heaviness go to Opus", () => {
    for (const text of ["Болит колено после бега", "Какие анализы сдать?", "Можно пить эти таблетки?", "Я опять голодаю весь день", "Кажется, у меня депрессия"]) {
      expect(route({ text }).model, text).toBe("claude-opus-5-5");
    }
  });

  it("goals, stress and work go to Sonnet; food to Haiku", () => {
    expect(route({ text: "Устал от работы, стресс" }).model).toBe("claude-sonnet-5-5");
    expect(route({ text: "Что съесть на ужин?" }).model).toBe("claude-haiku-5-5");
    expect(route({ text: "Что съесть на ужин?" }).tier).toBe("mid");
  });

  it("training is Sonnet by default and Opus with the expert setting (A62 §9.1)", () => {
    expect(route({ text: "Какая тренировка сегодня?" }).model).toBe("claude-sonnet-5-5");
    expect(route({ text: "Какая тренировка сегодня?", expertTopics: ["training"] }).model).toBe("claude-opus-5-5");
  });

  it("the lift goes only up: the floor of the session holds", () => {
    expect(route({ text: "спасибо", floor: "high" })).toMatchObject({ tier: "high", reason: "floor" });
    expect(route({ text: "спасибо", mode: "health" }).tier).toBe("high");
    expect(route({ text: "ну ладно", deviceCrisis: 1 })).toMatchObject({ tier: "midplus", reason: "crisis" });
  });
});

describe("request validation", () => {
  it("refuses a bad persona, roles out of order and a reply that does not end with the person", () => {
    expect(parseMentorRequest(turn("hi", { persona: "boss" }))).toEqual({ ok: false, error: "invalid_persona" });
    expect(parseMentorRequest(turn("hi", { messages: [{ role: "assistant", content: "x" }] })).ok).toBe(false);
    expect(
      parseMentorRequest(turn("hi", { messages: [{ role: "user", content: "a" }, { role: "assistant", content: "b" }] })).ok,
    ).toBe(false);
    expect(parseMentorRequest({ ...turn("hi"), v: 2 }).ok).toBe(false);
  });

  it("drops unknown context fields and codes: nothing but the pack reaches the prompt", () => {
    const p = parsed(
      turn("hi", {
        context: {
          address_form: "x",
          restrictions: ["HIDE_CALORIES", "DROP TABLE"],
          flags: ["ed_care", "diagnosis:anorexia"],
          summaries: ["Обсудили сон."],
          photo: "base64…",
          whoop: { recovery: 31 },
        },
      }),
    );
    expect(p.context).toEqual({ address_form: "n", restrictions: ["HIDE_CALORIES"], flags: ["ed_care"], summaries: ["Обсудили сон."] });
    const prompt = contextPrompt(p.context, "light");
    expect(prompt).not.toContain("base64");
    expect(prompt).not.toContain("31");
    expect(prompt).not.toContain("anorexia");
  });
});

describe("mentorTurn", () => {
  it("no ANTHROPIC_API_KEY → 503 not_configured with the name of the secret, no call", async () => {
    const ai = new FakeAnthropic();
    const meter = new FakeMeter();
    const r = await mentorTurn(parsed(turn("Привет")), { LLM_MODELS: env.LLM_MODELS }, { meter, fetch: ai.fetch });
    expect(r).toEqual({ status: 503, body: { error: "not_configured", missing: "ANTHROPIC_API_KEY" } });
    expect(ai.calls).toHaveLength(0);
  });

  it("a crisis level ≥ 2 from the phone never reaches the model, even without a key or money", async () => {
    const ai = new FakeAnthropic();
    const meter = new FakeMeter();
    meter.state = "month_exhausted";
    const r = await mentorTurn(parsed(turn("…", { crisis_level: 3 })), {}, { meter, fetch: ai.fetch });
    expect(r).toEqual({ status: 200, body: { type: "crisis", level: 3, source: "device" } });
    expect(ai.calls).toHaveLength(0);
  });

  it("the model's [[SOS]] marker becomes {type: crisis} and its words are not passed on", async () => {
    const ai = new FakeAnthropic();
    ai.reply = "[[SOS]]";
    const r = await mentorTurn(parsed(turn("мне очень плохо")), env, { meter: new FakeMeter(), fetch: ai.fetch });
    expect(r).toEqual({ status: 200, body: { type: "crisis", level: 2, source: "model" } });
  });

  it("routes, sends the stable prompt first and the pack after it, charges the meter", async () => {
    const ai = new FakeAnthropic();
    const meter = new FakeMeter();
    const r = await mentorTurn(parsed(turn("Болит плечо после жима")), env, { meter, fetch: ai.fetch });
    expect(r.status).toBe(200);
    expect(r.body).toMatchObject({ type: "reply", tier: "high", model: "claude-opus-5-5", degraded: null });
    const call = ai.calls[0]!;
    expect(call.model).toBe("claude-opus-5-5");
    expect(call.output_config).toEqual({ effort: "medium" });
    expect(call.fallbacks).toBe("default");
    expect(ai.headers[0]!.get("anthropic-beta")).toContain("server-side-fallback-2026-07-01");
    const system = call.system as { text: string }[];
    expect(system[0]!.text).toBe(STABLE_PROMPT);
    expect(system[2]!.text).toContain("КОНТЕКСТ");
    expect(meter.charged).toHaveLength(1);
    expect(meter.charged[0]).toBeCloseTo(costUsd("claude-opus-5-5", { input_tokens: 1000, output_tokens: 200 }));
  });

  it("Haiku gets no fallbacks parameter", async () => {
    const ai = new FakeAnthropic();
    await mentorTurn(parsed(turn("Привет")), env, { meter: new FakeMeter(), fetch: ai.fetch });
    expect(ai.calls[0]!.model).toBe("claude-haiku-5-5");
    expect(ai.calls[0]!.fallbacks).toBeUndefined();
  });

  it("soft threshold: chat goes to Haiku, health stays on Opus; hard threshold: no call", async () => {
    const ai = new FakeAnthropic();
    const meter = new FakeMeter();
    meter.state = "soft";
    const a = await mentorTurn(parsed(turn("Устал, стресс на работе")), env, { meter, fetch: ai.fetch });
    expect(a.body).toMatchObject({ model: "claude-haiku-5-5", degraded: "soft" });
    const b = await mentorTurn(parsed(turn("Болит в боку")), env, { meter, fetch: ai.fetch });
    expect(b.body).toMatchObject({ model: "claude-opus-5-5", degraded: null });
    meter.state = "day_exhausted";
    const c = await mentorTurn(parsed(turn("Привет")), env, { meter, fetch: ai.fetch });
    expect(c).toEqual({ status: 429, body: { error: "budget_exhausted", scope: "day" } });
    expect(ai.calls).toHaveLength(2);
  });

  it("a key the API rejects is reported as not_configured, not as a crash", async () => {
    const ai = new FakeAnthropic();
    ai.status = 401;
    const r = await mentorTurn(parsed(turn("Привет")), env, { meter: new FakeMeter(), fetch: ai.fetch });
    expect(r).toEqual({ status: 503, body: { error: "not_configured", missing: "ANTHROPIC_API_KEY" } });
  });

  it("a refusal is a typed answer, not text", async () => {
    const ai = new FakeAnthropic();
    ai.stopReason = "refusal";
    const r = await mentorTurn(parsed(turn("Привет")), env, { meter: new FakeMeter(), fetch: ai.fetch });
    expect(r.body).toMatchObject({ type: "refused" });
  });

  it("a model outside LLM_MODELS is refused (fail closed)", async () => {
    const ai = new FakeAnthropic();
    const r = await mentorTurn(parsed(turn("Привет")), { ...env, LLM_MODELS: "claude-opus-5-5" }, { meter: new FakeMeter(), fetch: ai.fetch });
    expect(r).toEqual({ status: 503, body: { error: "not_configured", missing: "LLM_MODELS" } });
    expect(ai.calls).toHaveLength(0);
  });

  it("summary uses Haiku with the summary prompt and accepts a conversation ending with Винс", async () => {
    const ai = new FakeAnthropic();
    ai.reply = "Обсудили сон.\nДоговорились ложиться до 23:30.";
    const body = turn("", {
      task: "summary",
      messages: [
        { role: "user", content: "Плохо сплю" },
        { role: "assistant", content: "Давай ложиться до 23:30." },
      ],
    });
    const r = await mentorTurn(parsed(body), env, { meter: new FakeMeter(), fetch: ai.fetch });
    expect(r.body).toMatchObject({ type: "summary", model: "claude-haiku-5-5" });
    expect((ai.calls[0]!.system as { text: string }[])[0]!.text).toContain("выжимку");
  });
});

describe("MentorMeter (D-056 limits)", () => {
  it("soft at $0.40, day closed at $1.50, month closed at $10; days roll over", async () => {
    let now = Date.parse("2026-11-08T10:00:00Z");
    const meter = new MentorMeter({ storage: new MemoryStorage() }, undefined, { now: () => now });
    expect(await meter.reserve()).toBe("ok");
    expect(await meter.charge(0.45)).toBe("soft");
    expect(await meter.charge(1.1)).toBe("day_exhausted");
    expect(await meter.reserve()).toBe("day_exhausted");
    now = Date.parse("2026-11-09T10:00:00Z");
    expect(await meter.reserve()).toBe("ok");
    for (let i = 0; i < 6; i++) await meter.charge(1.4);
    now = Date.parse("2026-11-10T10:00:00Z");
    expect(await meter.charge(1.2)).toBe("month_exhausted");
    now = Date.parse("2026-12-01T10:00:00Z");
    expect(await meter.reserve()).toBe("ok");
  });

  it("burst limit per minute", async () => {
    const now = Date.parse("2026-11-08T10:00:00Z");
    const meter = new MentorMeter({ storage: new MemoryStorage() }, undefined, { now: () => now });
    for (let i = 0; i < DEFAULT_LIMITS.perMinute; i++) expect(await meter.reserve()).toBe("ok");
    expect(await meter.reserve()).toBe("rate_limited");
  });
});

describe("POST /llm/mentor through the Worker", () => {
  async function session(e: Env, whoop: ReturnType<typeof makeEnv>["whoop"]) {
    const { verifier, challenge } = await pkcePair();
    const a = await handle(post("/whoop/authorize", { code_challenge: challenge }), e);
    const { state } = (await a.json()) as { state: string };
    const res = await handle(post("/whoop/token", { code: "good-code", state, code_verifier: verifier }), e, { fetch: whoop.fetch });
    return ((await res.json()) as { session: string }).session;
  }

  class MeterNs {
    readonly meters = new Map<string, MentorMeter>();
    idFromName(name: string) {
      return { name };
    }
    get(id: { name: string }) {
      let m = this.meters.get(id.name);
      if (!m) this.meters.set(id.name, (m = new MentorMeter({ storage: new MemoryStorage() })));
      return { fetch: (input: RequestInfo | URL, init?: RequestInit) => m!.fetch(new Request(input, init)) };
    }
  }

  it("needs the device session (AppAuth)", async () => {
    const { env: e } = makeEnv({ ANTHROPIC_API_KEY: "k" });
    expect((await handle(post("/llm/mentor", turn("hi")), e)).status).toBe(401);
  });

  it("answers with a session, keeps only money in the meter, logs nothing of the words", async () => {
    const ns = new MeterNs();
    const { env: e, whoop } = makeEnv({
      ANTHROPIC_API_KEY: "k",
      LLM_MODELS: env.LLM_MODELS,
      MENTOR_METER: ns as unknown as DurableObjectNamespace,
    });
    const token = await session(e, whoop);
    const ai = new FakeAnthropic();
    const res = await handle(post("/llm/mentor", turn("Привет, как дела?"), token), e, { fetch: ai.fetch });
    expect(res.status).toBe(200);
    expect(await res.json()).toMatchObject({ type: "reply", tier: "light" });
    const stored = JSON.stringify([...ns.meters.values()].map((m) => (m as unknown as { storage: MemoryStorage }).storage.dump()));
    expect(stored).not.toContain("Привет");
    expect(stored).toContain("dayUsd");
  });

  it("without the key: 503 with the secret's name; without the meter: 503 MENTOR_METER", async () => {
    const { env: e, whoop } = makeEnv({ LLM_MODELS: env.LLM_MODELS, MENTOR_METER: new MeterNs() as unknown as DurableObjectNamespace });
    const token = await session(e, whoop);
    const res = await handle(post("/llm/mentor", turn("hi"), token), e);
    expect(res.status).toBe(503);
    expect(await res.json()).toEqual({ error: "not_configured", missing: "ANTHROPIC_API_KEY" });

    const { env: e2, whoop: w2 } = makeEnv({ ANTHROPIC_API_KEY: "k" });
    const t2 = await session(e2, w2);
    const r2 = await handle(post("/llm/mentor", turn("hi"), t2), e2);
    expect(await r2.json()).toEqual({ error: "not_configured", missing: "MENTOR_METER" });
  });

  it("refuses a malformed body with 400", async () => {
    const { env: e, whoop } = makeEnv({ ANTHROPIC_API_KEY: "k", MENTOR_METER: new MeterNs() as unknown as DurableObjectNamespace });
    const token = await session(e, whoop);
    expect((await handle(post("/llm/mentor", { v: 1 }, token), e)).status).toBe(400);
  });
});
