import Anthropic from "@anthropic-ai/sdk";
import { isAllowedModel } from "../allowlist";
import type { Env } from "../env";
import { costUsd, type BudgetState } from "./budget";
import { contextPrompt, isPersona, personaPrompt, SOS_MARKER, SOS_MED_MARKER, STABLE_PROMPT, SUMMARY_PROMPT, type ContextPack, type Persona } from "./prompt";
import { isMode, isTier, route, TIER_MODEL, type ExpertTopic, type Mode, type Tier } from "./router";

/**
 * `POST /llm/mentor` — one turn of the conversation with Винс (A62, D-056), behind the device
 * session and the allowlist (AppAuth, checked by the caller). The Worker keeps nothing: the
 * conversation, the memory and the ContextPack live on the phone (A62 §4, D-019) and come with
 * each request; only sums of money go to `MentorMeter`. Nothing of the request or the answer is
 * logged.
 *
 * Safety (A07 §4.1, A62 §8): the phone runs CrisisGuard before sending; a level ≥ 2 never comes
 * here (and is refused here again). The model is the second layer: on a risk to life it answers
 * only `[[SOS]]`, the Worker turns that into `{type:"crisis"}` and the phone opens «Помощь».
 */

export const MENTOR_LIMITS = {
  maxMessages: 40,
  maxMessageChars: 4000,
  maxTotalChars: 40_000,
  maxSummaries: 3,
  maxSummaryChars: 600,
  maxAboutChars: 1000,
  maxRestrictions: 24,
} as const;

/** Codes the phone may send (A60 §3.2 `Restriction`, flags of A62 §4). Anything else is dropped. */
const RESTRICTIONS = new Set([
  "SOBER", "HELP_FIRST", "NO_COACHING", "NO_PUSH", "HIDE_SKEWS", "NO_LOAD", "INTENSITY_CAP", "HIDE_CALORIES", "HIDE_NUTRIENTS",
  "HIDE_FOOD_TALK", "HIDE_WEIGHT", "HIDE_FOOD_WINDOWS", "HIDE_BODY_PHOTO", "NO_FITNESS_AGE", "PATH_GENTLE", "PATH_STANDS",
]);
const FLAGS = new Set(["food_gentle", "gentle_plus", "gentle"]);

/** Output budget per tier. Thinking counts here too (adaptive thinking is on for these models). */
const MAX_TOKENS: Record<Tier, number> = { light: 2000, mid: 3000, midplus: 4000, high: 8000 };
const EFFORT: Record<Tier, "low" | "medium"> = { light: "low", mid: "low", midplus: "low", high: "medium" };

interface Turn {
  role: "user" | "assistant";
  content: string;
}

export interface MentorRequest {
  task: "reply" | "summary";
  persona: Persona;
  mode?: Mode;
  floor?: Tier;
  expertTopics: ExpertTopic[];
  deviceCrisis: number;
  context: ContextPack;
  messages: Turn[];
}

type Parsed = { ok: true; value: MentorRequest } | { ok: false; error: string };

const str = (v: unknown, max: number): string | null => (typeof v === "string" && v.trim().length > 0 && v.length <= max ? v : null);

/** Strict validation: unknown fields are ignored, wrong ones refuse the request. */
export function parseMentorRequest(body: Record<string, unknown> | null): Parsed {
  if (!body || body.v !== 1) return { ok: false, error: "invalid_request" };
  const task = body.task ?? "reply";
  if (task !== "reply" && task !== "summary") return { ok: false, error: "invalid_request" };
  if (!isPersona(body.persona)) return { ok: false, error: "invalid_persona" };
  if (body.mode !== undefined && !isMode(body.mode)) return { ok: false, error: "invalid_mode" };
  if (body.tier_floor !== undefined && !isTier(body.tier_floor)) return { ok: false, error: "invalid_request" };
  const deviceCrisis = Number(body.crisis_level ?? 0);
  if (!Number.isInteger(deviceCrisis) || deviceCrisis < 0 || deviceCrisis > 3) return { ok: false, error: "invalid_request" };
  const expertTopics = Array.isArray(body.expert_topics) ? body.expert_topics.filter((t): t is ExpertTopic => t === "training") : [];

  if (!Array.isArray(body.messages) || body.messages.length < 1 || body.messages.length > MENTOR_LIMITS.maxMessages) {
    return { ok: false, error: "invalid_messages" };
  }
  const messages: Turn[] = [];
  let total = 0;
  for (const m of body.messages as unknown[]) {
    const r = m as { role?: unknown; content?: unknown };
    const content = str(r?.content, MENTOR_LIMITS.maxMessageChars);
    if ((r?.role !== "user" && r?.role !== "assistant") || content === null) return { ok: false, error: "invalid_messages" };
    total += content.length;
    messages.push({ role: r.role, content });
  }
  if (total > MENTOR_LIMITS.maxTotalChars) return { ok: false, error: "too_long" };
  // The Messages API wants user first and alternating roles; a reply turn ends with the person.
  if (messages[0]!.role !== "user") return { ok: false, error: "invalid_messages" };
  for (let i = 1; i < messages.length; i++) if (messages[i]!.role === messages[i - 1]!.role) return { ok: false, error: "invalid_messages" };
  if (task === "reply" && messages[messages.length - 1]!.role !== "user") return { ok: false, error: "invalid_messages" };

  const c = (body.context ?? {}) as Record<string, unknown>;
  const address = c.address_form === "m" || c.address_form === "f" ? c.address_form : "n";
  const restrictions = Array.isArray(c.restrictions)
    ? [...new Set(c.restrictions.filter((x): x is string => typeof x === "string" && RESTRICTIONS.has(x)))].slice(0, MENTOR_LIMITS.maxRestrictions)
    : [];
  const flags = Array.isArray(c.flags) ? [...new Set(c.flags.filter((x): x is string => typeof x === "string" && FLAGS.has(x)))] : [];
  const summaries = Array.isArray(c.summaries)
    ? c.summaries.map((s) => str(s, MENTOR_LIMITS.maxSummaryChars)).filter((s): s is string => s !== null).slice(-MENTOR_LIMITS.maxSummaries)
    : [];
  const about = str(c.about, MENTOR_LIMITS.maxAboutChars) ?? undefined;

  return {
    ok: true,
    value: {
      task,
      persona: body.persona,
      mode: body.mode as Mode | undefined,
      floor: body.tier_floor as Tier | undefined,
      expertTopics,
      deviceCrisis,
      context: { address_form: address, restrictions, flags, summaries, ...(about ? { about } : {}) },
      messages,
    },
  };
}

export interface Meter {
  reserve(): Promise<BudgetState>;
  charge(usd: number): Promise<BudgetState>;
}

export type MentorResult =
  | { status: 200; body: Record<string, unknown> }
  | { status: 400 | 429 | 502 | 503; body: { error: string; [k: string]: unknown } };

export interface MentorDeps {
  meter: Meter;
  /** Outbound fetch to the Anthropic API (tests). */
  fetch?: typeof fetch;
}

/** Concatenated text of the answer; thinking blocks are not shown to the person. */
function textOf(content: readonly { type: string }[]): string {
  return content
    .filter((b): b is { type: "text"; text: string } => b.type === "text")
    .map((b) => b.text)
    .join("")
    .trim();
}

export async function mentorTurn(req: MentorRequest, env: Env, deps: MentorDeps): Promise<MentorResult> {
  // A level ≥ 2 of CrisisGuard opens «Помощь» on the phone; the model is never asked (A07 §4.3).
  if (req.deviceCrisis >= 2) return { status: 200, body: { type: "crisis", level: req.deviceCrisis, source: "device" } };
  if (!env.ANTHROPIC_API_KEY) return { status: 503, body: { error: "not_configured", missing: "ANTHROPIC_API_KEY" } };

  // A62 §2 «SOS и кризис вне лимитов» (ревью 10, М3): a turn with CrisisGuard ≥ 1 (or the
  // window of a crisis, which the phone sends as level 1) is never refused or degraded for money;
  // only the burst limit stays.
  const care = req.deviceCrisis >= 1;
  const budget = await deps.meter.reserve();
  if (budget === "rate_limited") return { status: 429, body: { error: "rate_limited" } };
  if (!care && (budget === "day_exhausted" || budget === "month_exhausted")) {
    return { status: 429, body: { error: "budget_exhausted", scope: budget === "day_exhausted" ? "day" : "month" } };
  }

  const lastUser = [...req.messages].reverse().find((m) => m.role === "user")?.content ?? "";
  const r =
    req.task === "summary"
      ? { tier: "light" as Tier, model: TIER_MODEL.light }
      : route({ text: lastUser, mode: req.mode, floor: req.floor, expertTopics: req.expertTopics, deviceCrisis: req.deviceCrisis });
  // Soft threshold: the chat goes to Haiku; topics with a high cost of error stay on Opus.
  const degraded = !care && budget !== "ok" && r.tier !== "high";
  const model = degraded ? TIER_MODEL.light : r.model;
  // LLM_MODELS (wrangler.toml) is the allowlist of the proxy (A14 §5): fail closed.
  if (!isAllowedModel(model, env)) return { status: 503, body: { error: "not_configured", missing: "LLM_MODELS" } };
  const maxTokens = degraded ? MAX_TOKENS.light : MAX_TOKENS[r.tier];

  const client = new Anthropic({ apiKey: env.ANTHROPIC_API_KEY, fetch: deps.fetch, maxRetries: 1, timeout: 90_000 });
  const system: Anthropic.Beta.BetaTextBlockParam[] =
    req.task === "summary"
      ? [{ type: "text", text: SUMMARY_PROMPT }]
      : [
          { type: "text", text: STABLE_PROMPT },
          { type: "text", text: personaPrompt(req.persona), cache_control: { type: "ephemeral" } },
          { type: "text", text: contextPrompt(req.context, r.tier), cache_control: { type: "ephemeral" } },
        ];
  const messages: Anthropic.Beta.BetaMessageParam[] =
    req.task === "summary"
      ? [{ role: "user", content: req.messages.map((m) => `${m.role === "user" ? "Человек" : "Винс"}: ${m.content}`).join("\n\n") }]
      : req.messages.map((m) => ({ role: m.role, content: m.content }));
  // Server-side refusal fallback where the API offers it (Opus and Sonnet on the Claude API).
  const fallback = model !== TIER_MODEL.light;

  let response: Anthropic.Beta.BetaMessage;
  try {
    response = await client.beta.messages.create({
      model,
      max_tokens: maxTokens,
      system,
      messages,
      output_config: { effort: EFFORT[degraded ? "light" : r.tier] },
      ...(fallback ? { betas: ["server-side-fallback-2026-07-01"], fallbacks: "default" as const } : {}),
    });
  } catch (e) {
    if (e instanceof Anthropic.AuthenticationError || e instanceof Anthropic.PermissionDeniedError) {
      return { status: 503, body: { error: "not_configured", missing: "ANTHROPIC_API_KEY" } };
    }
    if (e instanceof Anthropic.RateLimitError) return { status: 429, body: { error: "upstream_busy" } };
    return { status: 502, body: { error: "upstream_error" } };
  }

  await deps.meter.charge(costUsd(response.model ?? model, response.usage));

  if (response.stop_reason === "refusal") return { status: 200, body: { type: "refused", tier: r.tier } };
  const text = textOf(response.content);
  if (req.task === "reply" && text.includes(SOS_MARKER)) {
    // The model layer may only raise the level (CrisisGuard.merge): level 2 → «Помощь».
    return { status: 200, body: { type: "crisis", level: 2, source: "model" } };
  }
  if (req.task === "reply" && text.includes(SOS_MED_MARKER)) {
    // Acute physical symptoms (М1): the urgent line and «Помощь», not a crisis of CrisisGuard.
    return { status: 200, body: { type: "emergency", source: "model" } };
  }
  if (!text) return { status: 502, body: { error: "empty_answer" } };
  return {
    status: 200,
    body: {
      type: req.task === "summary" ? "summary" : "reply",
      text,
      tier: r.tier,
      model: response.model ?? model,
      degraded: degraded ? "soft" : null,
      truncated: response.stop_reason === "max_tokens",
    },
  };
}
