/**
 * Topic router of Винс (A62 §2, block 2 §9): the model is chosen by the cost of an error on the
 * topic, not by the length of the question. Deterministic and free: a dictionary of Russian and
 * English stems on the last message of the person, the mode of the conversation and the floor the
 * phone already reached in this session. «Лифт только вверх»: the tier never goes down inside one
 * conversation, only a new conversation starts low again.
 *
 * The Haiku classifier of block 2 §9 (≈ $0.0003 a turn) is a later step; this dictionary is the
 * $0 layer under it and stays as the floor when the classifier comes.
 */

export const TIERS = ["light", "mid", "midplus", "high"] as const;
export type Tier = (typeof TIERS)[number];

export const MODES = ["talk", "training", "food", "work", "path", "health"] as const;
export type Mode = (typeof MODES)[number];

/** A62 §2: the executor of each tier. Haiku → Sonnet on low confidence is not in this slice. */
export const TIER_MODEL: Record<Tier, string> = {
  light: "claude-haiku-5-5",
  mid: "claude-haiku-5-5",
  midplus: "claude-sonnet-5-5",
  high: "claude-opus-5-5",
};

export function tierRank(t: Tier): number {
  return TIERS.indexOf(t);
}

export function maxTier(...tiers: (Tier | undefined)[]): Tier {
  let best: Tier = "light";
  for (const t of tiers) if (t && tierRank(t) > tierRank(best)) best = t;
  return best;
}

export function isTier(v: unknown): v is Tier {
  return typeof v === "string" && (TIERS as readonly string[]).includes(v);
}

export function isMode(v: unknown): v is Mode {
  return typeof v === "string" && (MODES as readonly string[]).includes(v);
}

/** The topics a person can raise to the high tier («уровень эксперта по темам», A62 §2). */
export type ExpertTopic = "training";

interface Topic {
  tier: Tier;
  /** Training is high only for people who set it so (the founder by default, A62 §9.1). */
  training?: boolean;
  stems: RegExp;
}

/** Unicode-aware stems; `\p{L}` because `\w` and `\b` do not see Cyrillic. */
const rx = (stems: string[]) => new RegExp(`(?<![\\p{L}])(${stems.join("|")})`, "iu");

const TOPICS: Topic[] = [
  {
    // High: symptoms, labs, medicines, pain and injury, pregnancy, signals of an eating disorder,
    // psychological heaviness (block 2 §9 row «Высокий»).
    tier: "high",
    stems: rx([
      "симптом", "болит", "боль(?!ш)", "болел", "температур", "давлени", "пульс скач", "аритми", "сердц", "голова кружит", "головокруж",
      "в груди", "грудь давит", "одышк", "задыха", "тяжело дыш", "онеме", "немеет", "немеют", "сознани", "судорог", "инсулин",
      "тошн", "рвот", "кров(?!ат)", "обморок", "отёк", "отек", "сыпь", "анализ", "ферритин", "гемоглобин", "холестерин", "глюкоз", "сахар в крови",
      "лекарств", "таблет", "препарат", "антибиот", "антидепресс", "гормон", "доз(?!вон)", "бад(?!минт)", "добавк", "витамин",
      "травм", "растяж", "вывих", "перелом", "колено", "спина болит", "поясниц",
      "беремен", "кормлю грудью", "грудное вскармлив", "роды", "после родов",
      "голодаю", "не ем ", "переедаю", "срыв", "компульсив", "булими", "анорекс", "вызвать рвоту", "ненавижу своё тело", "ненавижу свое тело",
      "депресс", "паническ", "паник", "безнадёж", "безнадеж", "не вижу смысла", "диагноз", "врач",
      "symptom", "pain", "medication", "pills", "dose", "injur", "pregnan", "binge", "purge", "depress", "panic",
    ]),
  },
  {
    tier: "midplus",
    training: true,
    stems: rx([
      "трениров", "нагрузк", "присед", "жим", "тяга", "станов", "подход", "повтор", "кардио", "бег", "зал", "rpe", "восстановлен",
      "можно ли сегодня", "workout", "training", "squat", "bench",
    ]),
  },
  {
    // Middle plus: goals of the Path, WOOP, the Crossroads, stress and motivation, «Работа», and
    // the emotional heaviness of block 2 §9 (the psychologist's caveat: chatter → Sonnet on it).
    tier: "midplus",
    stems: rx([
      "цель", "путь", "рубеж", "woop", "перекрёст", "перекрест", "мотивац", "привычк",
      "стресс", "выгоран", "устал", "тревож", "тревог", "одинок", "тяжело", "грустн", "плохо на душе", "злюсь", "сорвал", "поссорил",
      "работ", "начальник", "дедлайн", "проект", "письмо",
      "stress", "burnout", "lonely", "anxious", "goal", "work",
    ]),
  },
  {
    tier: "mid",
    stems: rx([
      "ед[аыуе]", "питани", "белок", "белка", "углевод", "жир", "калори", "ккал", "рецепт", "завтрак", "обед", "ужин", "перекус",
      "голод", "сладк", "меню", "продукт", "food", "meal", "protein", "diet",
    ]),
  },
];

const MODE_FLOOR: Record<Mode, Tier> = {
  talk: "light",
  food: "mid",
  work: "midplus",
  path: "midplus",
  training: "midplus",
  health: "high",
};

export interface RouteInput {
  /** The last message of the person. */
  text: string;
  mode?: Mode;
  /** The highest tier this conversation already reached (from the phone). */
  floor?: Tier;
  expertTopics?: readonly ExpertTopic[];
  /** CrisisGuard level of the phone (0 or 1 here; ≥ 2 never reaches the model). */
  deviceCrisis?: number;
}

export interface Route {
  tier: Tier;
  model: string;
  /** Which rule lifted the tier — for tests and the metrics of block 9, never the words. */
  reason: "topic" | "mode" | "floor" | "crisis" | "default";
}

/** The tier of one turn: the maximum of the topic, the mode, the floor and the crisis signal. */
export function route(input: RouteInput): Route {
  const trainingHigh = input.expertTopics?.includes("training") ?? false;
  let topic: Tier = "light";
  for (const t of TOPICS) {
    if (!t.stems.test(input.text)) continue;
    const tier = t.training && trainingHigh ? "high" : t.tier;
    topic = maxTier(topic, tier);
  }
  let mode: Tier = input.mode ? MODE_FLOOR[input.mode] : "light";
  if (input.mode === "training" && trainingHigh) mode = "high";
  // Level 1 of CrisisGuard (distress, an ambiguous phrase) or the crisis window: Opus — the
  // psychological heaviness of the high tier (A62 §2; ревью 10, ответ на вопрос 2).
  const crisis: Tier = (input.deviceCrisis ?? 0) >= 1 ? "high" : "light";
  const floor = input.floor ?? "light";
  const tier = maxTier(topic, mode, floor, crisis);
  const reason: Route["reason"] =
    tier === "light" ? "default" : tier === topic ? "topic" : tier === crisis ? "crisis" : tier === mode ? "mode" : "floor";
  return { tier, model: TIER_MODEL[tier], reason };
}
