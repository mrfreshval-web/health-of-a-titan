package com.healthofatitan.app.domain.mentor

/**
 * What a message inside the crisis window (SafetyBus: CrisisGuard ≥ 2 within 72 h) does. The two
 * variants of the review of Агенты 3/10/7/16 (08.11); the founder chose [GENTLE_TALK] (D-063):
 *
 * - [CARD_ONCE] (вариант А, fallback): no model; the full crisis card once per conversation, then a
 *   short local line with «Помощь». Nothing of the window reaches the model.
 * - [GENTLE_TALK] (вариант Б, D-063): the message goes to the model on the high tier (Opus) with
 *   `gentle_plus` → NO_COACHING and SOBER in the prompt; a message of level ≥ 2 never goes out.
 */
enum class CrisisWindowMode { CARD_ONCE, GENTLE_TALK }

/** The one switch of the crisis window (variant А/Б): change [CRISIS_WINDOW_MODE] only here. */
object MentorPolicy {
    val CRISIS_WINDOW_MODE: CrisisWindowMode = CrisisWindowMode.GENTLE_TALK
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

    private val yes = setOf(
        "да", "ага", "угу", "бывают", "бывает", "бывало", "есть", "иногда", "часто", "периодически",
        "наверное", "возможно", "yes", "yeah", "yep", "sometimes", "maybe",
    )

    /** «не бывает», «не есть» — отрицание; «не часто», «не иногда» — всё ещё «да». */
    private val negatable = setOf("бывают", "бывает", "бывало", "есть")
    private val fillers = setOf("ну", "эм", "хм", "well")
    private val negations = setOf("нет", "неа", "не", "no", "nope", "никогда")

    private fun words(text: String): List<String> =
        text.lowercase().replace('ё', 'е').split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() && it !in fillers }

    /**
     * The answer to Text 1 of A07 §4.3 («бывают мысли навредить себе…?») is a consent → `SafetyAnswer.YES`
     * unless it is a clear «нет». Doubt («не знаю», «наверное», «может быть») goes up, not down
     * (ревью 10, 08.11). «да нет» is a colloquial «нет».
     */
    fun consent(text: String): Boolean {
        val w = words(text)
        if (w.isEmpty()) return false
        if (w.size >= 2 && w[0] == "да" && w[1] == "нет") return false
        val short = w.size <= MAX_CONSENT_WORDS
        val doubt = w.zipWithNext().any { (a, b) -> (a == "не" && (b == "знаю" || b.startsWith("уверен"))) || (a == "может" && b == "быть") }
        if (short && doubt) return true
        if (w[0] in yes) return true
        if (!short) return false
        return w.withIndex().any { (i, x) -> x in yes && !(x in negatable && i > 0 && w[i - 1] == "не") }
    }

    /**
     * The answer to Text 1 starts with «нет» (or «да нет»): the person said there are no such
     * thoughts. From this turn on the pack carries `checked` (ревью 10, П4).
     */
    fun saysNo(text: String): Boolean {
        val w = words(text)
        if (w.isEmpty() || consent(text) || declines(text)) return false
        return w[0] in negations || (w.size >= 2 && w[0] == "да" && w[1] == "нет")
    }

    /** A bare «нет» (≤ 3 words): shown, but not sent to the model, which answers the line before. */
    fun denial(text: String): Boolean = saysNo(text) && words(text).size <= MAX_DENIAL_WORDS

    private val declines = listOf(
        Regex("(?iU)не\\s+(хочу|буду|готов\\p{L}*)\\s+(отвечать|говорить|об этом)"),
        Regex("(?iU)не\\s+(твое|ваше)\\s+дело"),
        Regex("(?iU)(без комментариев|пропущу|пропустим|не скажу)"),
    )

    /** «не хочу отвечать», «не твоё дело»: `SafetyAnswer.DECLINE` (level 1) for the metrics of block 9. */
    fun declines(text: String): Boolean = !consent(text) && declines.any { it.containsMatchIn(text.replace('ё', 'е')) }

    private const val MAX_CONSENT_WORDS = 8
    private const val MAX_DENIAL_WORDS = 3
}

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
 *   capsules, dragées, drops, ampoules or shots, no amount (мл, г, единицы, пакетики, ложки) right
 *   next to a supplement or a medicine (креатин, омега, магний, инсулин, сироп…) — doses are with
 *   the doctor. Grams of food and ml of water are not a dose.
 * - Diagnosis: no «у тебя <болезнь>», «это точно <болезнь>», «диагноз: …».
 *
 * Черновик — пороги и строки ждут ревью Агентов 3 и 10.
 */
object ResponseGuard {
    private val dose = Regex("(?iU)\\d+([.,]\\d+)?\\s?(мг|мкг|ме|ед|мл/кг|mg|mcg|µg|iu)(?!\\p{L})")

    private val pills = Regex(
        "(?iU)(?<!\\p{L})(\\d+([.,]\\d+)?|один|одн\\p{L}*|дв\\p{L}*|тр\\p{L}*|четыр\\p{L}*|пят\\p{L}*|шест\\p{L}*|сем\\p{L}*|восем\\p{L}*|девят\\p{L}*|десят\\p{L}*|пол|половин[уа])\\s?(-?х\\s)?(таблет|капсул(?!\\p{L}*\\s+кофе)|драже|капел|кап(?!\\p{L})|ампул|укол|инъекц|впрыск)",
    )

    private const val MED = "(креатин|омег|omega|магни|magnes|мелатонин|melatonin|витамин|vitamin|кофеин|caffeine|сироп|капл|препарат|лекарств|добавк|бад(?!\\p{L})|жиросжигат|предтрен|инсулин|insulin)"
    private const val UNIT = "(мл|ml|г|гр|грамм\\p{L}*|g|единиц\\p{L}*|пакетик\\p{L}*|саше|ложк\\p{L}*|ложек)"
    private const val NUM = "\\d+([.,]\\d+)?(\\s?[–-]\\s?\\d+([.,]\\d+)?)?\\s?(\\p{L}+\\s)?"

    /**
     * An amount right next to a supplement or a medicine: «5 г креатина», «креатин — по 3–5 г»,
     * «10 единиц инсулина». Grams and ml of food or water elsewhere in the answer are not a dose.
     */
    private val supplement = Regex(
        "(?iU)(?<![\\p{L}\\d])$NUM$UNIT(?!\\p{L})\\s+(\\p{L}+\\s+)?$MED" +
            "|(?<!\\p{L})$MED\\p{L}*[^.!?\\n]{0,20}?(?<![\\p{L}\\d])$NUM$UNIT(?!\\p{L})",
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

    /** A dose of a medicine or a supplement: units of a dose, pills and drops, or an amount next to a supplement. */
    fun namesDose(text: String): Boolean =
        dose.containsMatchIn(text) || pills.containsMatchIn(text) || supplement.containsMatchIn(text)
}
