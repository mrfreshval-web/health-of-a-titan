package com.healthofatitan.app.domain.mentor

/**
 * The gate before the model (A62 §8 layer 1, A07 §4.1): every message of the person passes
 * CrisisGuard on the phone first. The guard is passed in as a function so this module stays pure;
 * the app passes `CrisisGuard.assess(text).level.value`.
 */
/**
 * What a message inside the crisis window (SafetyBus: CrisisGuard ≥ 2 within 72 h) does. The two
 * variants of the review of Агенты 3/10/7/16 (08.11), until the founder chooses:
 *
 * - [CARD_ONCE] (временно, по умолчанию): no model; the full crisis card once per conversation, then a
 *   short local line with «Помощь». Nothing of the window reaches the model.
 * - [GENTLE_TALK] (вариант Б): the message goes to the model on the high tier (Opus) with
 *   `gentle_plus` → NO_COACHING and SOBER in the prompt; a message of level ≥ 2 never goes out.
 */
enum class CrisisWindowMode { CARD_ONCE, GENTLE_TALK }

/** The one switch of the crisis window (variant А/Б): change [CRISIS_WINDOW_MODE] only here. */
object MentorPolicy {
    val CRISIS_WINDOW_MODE: CrisisWindowMode = CrisisWindowMode.CARD_ONCE
}

/**
 * The gate before the model (A62 §8 layer 1, A07 §4.1): every message of the person passes
 * CrisisGuard on the phone first. The guard is passed in as a function so this module stays pure;
 * the app passes `CrisisGuard.assess(text).level.value`.
 */
sealed interface InputDecision {
    /** Level 2–3 now: Винс does not coach, he leads to help. The words never go to the model. */
    data class Sos(val level: Int) : InputDecision

    /** The bus still holds a crisis and the window is [CrisisWindowMode.CARD_ONCE]: no model. */
    data object Window : InputDecision

    /**
     * Send to the model; [crisisLevel] 0 or 1 goes with the request (1 → Opus, no day/month limit).
     * [care] — the message is inside the crisis window in [CrisisWindowMode.GENTLE_TALK].
     */
    data class Send(val crisisLevel: Int, val care: Boolean = false) : InputDecision
}

object MentorGate {
    const val SOS_LEVEL = 2

    /**
     * @param assess CrisisGuard level of the words (0–3)
     * @param crisisActive the safety bus holds CrisisGuard ≥ 2 within its window (NO_COACHING)
     */
    fun before(
        text: String,
        assess: (String) -> Int,
        crisisActive: Boolean,
        mode: CrisisWindowMode = MentorPolicy.CRISIS_WINDOW_MODE,
    ): InputDecision {
        val level = assess(text)
        return when {
            level >= SOS_LEVEL -> InputDecision.Sos(level)
            crisisActive && mode == CrisisWindowMode.CARD_ONCE -> InputDecision.Window
            crisisActive -> InputDecision.Send(maxOf(level, 1), care = true)
            else -> InputDecision.Send(level)
        }
    }

    /** The model layer may only raise (CrisisGuard.merge): the larger of the two acts. */
    fun merge(device: Int, model: Int?): Int = if (model != null && model > device) model else device

    private val yes = setOf("да", "ага", "угу", "бывают", "бывает", "есть", "иногда", "часто", "yes", "yeah", "yep")
    private val no = setOf("нет", "неа", "не", "no", "nope", "никогда")

    /**
     * A short consent to Text 1 of A07 §4.3 («бывают мысли навредить себе…?»): «да», «бывают»,
     * «есть», «иногда», «yes»… → `SafetyAnswer.YES`. A long message, or any «нет» in it, is not a
     * consent: the talk goes on with the gentle flag. Черновик — ждёт ревью Агентов 10 и 3.
     */
    fun consent(text: String): Boolean {
        val words = text.lowercase().replace('ё', 'е').split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }
        if (words.isEmpty() || words.size > MAX_CONSENT_WORDS) return false
        if (words.any { it in no }) return false
        return words.any { it in yes }
    }

    private const val MAX_CONSENT_WORDS = 5
}

/** Why an answer was replaced, for the metrics of block 9 — never the words. */
/**
 * Why an answer was replaced, for the metrics of block 9 — never the words. [ED_CARE] — the food
 * care of A07 §5 is on; [FOOD_HIDDEN] — only a restriction of the bus (e.g. HIDE_WEIGHT) hides it.
 */
enum class GuardReason { ED_CARE, FOOD_HIDDEN, DOSE, DIAGNOSIS }

sealed interface GuardResult {
    data class Pass(val text: String) : GuardResult

    data class Replaced(val reason: GuardReason) : GuardResult
}

/**
 * `ResponseGuard` (A62 §8 layer 4): the answer of the model is checked on the phone before the
 * person sees it. A hit replaces the whole answer by a fixed line of the app (never a half-cut
 * answer). The prompt asks for the same; the guard is there for when the model forgets.
 *
 * - `ED_CARE` or a food/weight restriction of the bus: no calories, body weight, dieting.
 * - Doses: no number with a unit of a dose (мг, мкг, МЕ, ЕД, mg, mcg, IU), no count of tablets,
 *   capsules or dragées, no мл or grams next to a supplement or a medicine (креатин, омега,
 *   магний, мелатонин, витамин, кофеин, сироп, капли…) — doses are with the doctor. Grams of food
 *   alone are not a dose.
 * - Diagnosis: no «у тебя <болезнь>», «это точно <болезнь>», «диагноз: …».
 *
 * Черновик — пороги и строки ждут ревью Агентов 3 и 10.
 */
object ResponseGuard {
    private val dose = Regex("(?iU)\\d+([.,]\\d+)?\\s?(мг|мкг|ме|ед|мл/кг|mg|mcg|µg|iu)(?!\\p{L})")

    private val pills = Regex(
        "(?iU)(?<!\\p{L})(\\d+([.,]\\d+)?|одн\\p{L}*|дв\\p{L}*|тр\\p{L}*|четыр\\p{L}*|пол|половин[уа])\\s?(-?х\\s)?(таблет|капсул|драже|пакетик|саше)",
    )

    /** An amount in мл or grams: a dose only next to a supplement or a medicine ([medicine]). */
    private val amount = Regex("(?iU)\\d+([.,]\\d+)?\\s?(мл|ml|г|гр|грамм\\p{L}*|g)(?!\\p{L})")

    private val medicine = Regex(
        "(?iU)(?<!\\p{L})(креатин|омег|omega|магни|magnes|мелатонин|melatonin|витамин|vitamin|кофеин|caffeine|сироп|капл|препарат|лекарств|добавк|бад(?!\\p{L})|жиросжигат|предтрен)",
    )

    private val diagnosis = Regex(
        "(?iU)(у (тебя|вас) (точно |скорее всего |наверняка )?(диабет|депресси\\w*|анорекси\\w*|булими\\w*|гипертони\\w*|аритми\\w*|инфаркт|инсульт|рак|опухол\\w*|грыж\\w*|разрыв\\w*)|" +
            "(это|похоже на) (точно )?(диабет|депресси\\w*|инфаркт|инсульт|рак|опухол\\w*)|" +
            "диагноз:|ставлю диагноз)",
    )

    fun check(text: String, restrictions: Set<String>, edCare: Boolean): GuardResult {
        val foodHidden = edCare || EdCareText.HIDING.any { it in restrictions }
        return when {
            foodHidden && EdCareText.mentionsHidden(text) ->
                GuardResult.Replaced(if (edCare) GuardReason.ED_CARE else GuardReason.FOOD_HIDDEN)
            namesDose(text) -> GuardResult.Replaced(GuardReason.DOSE)
            diagnosis.containsMatchIn(text.replace('ё', 'е')) -> GuardResult.Replaced(GuardReason.DIAGNOSIS)
            else -> GuardResult.Pass(text)
        }
    }

    /** A dose of a medicine or a supplement: units of a dose, pills, or мл/grams near a medicine. */
    fun namesDose(text: String): Boolean =
        dose.containsMatchIn(text) ||
            pills.containsMatchIn(text) ||
            amount.findAll(text).any { m -> medicine.containsMatchIn(text.substring(maxOf(0, m.range.first - NEAR), minOf(text.length, m.range.last + 1 + NEAR))) }

    private const val NEAR = 40
}
