/**
 * Money of Винс (A62 §2 «Лимиты», block 2 §6, D-056: $3 average, hard ceiling $10 a month).
 *
 *  - soft daily threshold $0.40 → the turn goes to Haiku (high-tier topics stay on Opus, shorter);
 *  - hard daily threshold $1.50 → no model, the phone shows a template;
 *  - monthly ceiling $10 → no model until the next month;
 *  - a burst limit of requests per minute against a stuck client;
 *  - SOS and crisis are outside all limits: a crisis turn never reaches the model at all (the
 *    phone opens «Помощь» itself), so nothing of the crisis path is ever refused for money.
 *
 * The counter lives in the Durable Object `MentorMeter`, one per user: only sums of money, the
 * day and the month — no words, no topics, no tiers. Days and months are UTC in this slice.
 */

/** USD per 1M tokens [Факт: справочник Claude API, сверено 08.11.2026]. Cache write = 1.25 × input (5 min). */
export const PRICES: Record<string, { input: number; output: number; cacheRead: number }> = {
  "claude-opus-5-5": { input: 4, output: 20, cacheRead: 0.2 },
  "claude-sonnet-5-5": { input: 2, output: 10, cacheRead: 0.2 },
  "claude-haiku-5-5": { input: 0.1, output: 0.5, cacheRead: 0.01 },
};

export interface Usage {
  input_tokens?: number | null;
  output_tokens?: number | null;
  cache_read_input_tokens?: number | null;
  cache_creation_input_tokens?: number | null;
}

/** Cost of one response in USD. An unknown model is priced as Opus (never under-count). */
export function costUsd(model: string, usage: Usage): number {
  const p = PRICES[model] ?? PRICES["claude-opus-5-5"]!;
  const m = 1_000_000;
  return (
    ((usage.input_tokens ?? 0) * p.input +
      (usage.cache_creation_input_tokens ?? 0) * p.input * 1.25 +
      (usage.cache_read_input_tokens ?? 0) * p.cacheRead +
      (usage.output_tokens ?? 0) * p.output) /
    m
  );
}

export interface Limits {
  softDayUsd: number;
  hardDayUsd: number;
  monthUsd: number;
  perMinute: number;
}

export const DEFAULT_LIMITS: Limits = { softDayUsd: 0.4, hardDayUsd: 1.5, monthUsd: 10, perMinute: 12 };

export type BudgetState = "ok" | "soft" | "day_exhausted" | "month_exhausted" | "rate_limited";

export interface MeterRecord {
  day: string; // YYYY-MM-DD (UTC)
  dayUsd: number;
  month: string; // YYYY-MM
  monthUsd: number;
  minute: number; // epoch minute
  minuteCount: number;
}

export function emptyRecord(now: number): MeterRecord {
  const iso = new Date(now).toISOString();
  return { day: iso.slice(0, 10), dayUsd: 0, month: iso.slice(0, 7), monthUsd: 0, minute: Math.floor(now / 60_000), minuteCount: 0 };
}

/** Rolls the record over to the current day, month and minute. */
export function roll(rec: MeterRecord, now: number): MeterRecord {
  const fresh = emptyRecord(now);
  return {
    day: fresh.day,
    dayUsd: rec.day === fresh.day ? rec.dayUsd : 0,
    month: fresh.month,
    monthUsd: rec.month === fresh.month ? rec.monthUsd : 0,
    minute: fresh.minute,
    minuteCount: rec.minute === fresh.minute ? rec.minuteCount : 0,
  };
}

export function stateOf(rec: MeterRecord, limits: Limits): BudgetState {
  if (rec.monthUsd >= limits.monthUsd) return "month_exhausted";
  if (rec.dayUsd >= limits.hardDayUsd) return "day_exhausted";
  if (rec.minuteCount >= limits.perMinute) return "rate_limited";
  if (rec.dayUsd >= limits.softDayUsd) return "soft";
  return "ok";
}

/** The subset of DurableObjectStorage the meter uses (tests use an in-memory map). */
export interface MeterStorage {
  get<T>(key: string): Promise<T | undefined>;
  put<T>(key: string, value: T): Promise<void>;
}

const KEY = "meter";

/**
 * Durable Object `MentorMeter` (one per user, `idFromName("mentor-user:<id>")`). Internal
 * protocol, Worker → DO only: `POST https://meter/reserve` → `{state}` (counts the request for the
 * burst limit when allowed) and `POST https://meter/charge {usd}` → `{state}`.
 */
export class MentorMeter {
  private readonly storage: MeterStorage;
  private readonly now: () => number;

  constructor(
    state: { storage: MeterStorage },
    _env?: unknown,
    deps: { now?: () => number; limits?: Limits } = {},
  ) {
    this.storage = state.storage;
    this.now = deps.now ?? Date.now;
    this.limits = deps.limits ?? DEFAULT_LIMITS;
  }

  private readonly limits: Limits;

  private async load(): Promise<MeterRecord> {
    const rec = await this.storage.get<MeterRecord>(KEY);
    return roll(rec ?? emptyRecord(this.now()), this.now());
  }

  async reserve(): Promise<BudgetState> {
    const rec = await this.load();
    const state = stateOf(rec, this.limits);
    if (state === "ok" || state === "soft") {
      rec.minuteCount += 1;
      await this.storage.put(KEY, rec);
    }
    return state;
  }

  async charge(usd: number): Promise<BudgetState> {
    const rec = await this.load();
    const add = Number.isFinite(usd) && usd > 0 ? usd : 0;
    rec.dayUsd += add;
    rec.monthUsd += add;
    await this.storage.put(KEY, rec);
    return stateOf(rec, this.limits);
  }

  async fetch(request: Request): Promise<Response> {
    const op = new URL(request.url).pathname;
    if (op === "/reserve") return Response.json({ state: await this.reserve() });
    if (op === "/charge") {
      const body = (await request.json().catch(() => ({}))) as { usd?: unknown };
      return Response.json({ state: await this.charge(Number(body.usd)) });
    }
    return Response.json({ error: "not_found" }, { status: 404 });
  }
}
