package com.healthofatitan.app.domain.mentor

/**
 * The gate before the model (A62 §8 layer 1, A07 §4.1): every message of the person passes
 * CrisisGuard on the phone first. The guard is passed in as a function so this module stays pure;
 * the app passes `CrisisGuard.assess(text).level.value`.
 */
sealed interface InputDecision {
    /** Level 2–3 now, or the bus still holds a crisis: Винс does not coach, he leads to help. */
    data class Sos(val level: Int) : InputDecision

    /** Send to the model; [crisisLevel] 0 or 1 goes with the request (1 → at least Sonnet). */
    data class Send(val crisisLevel: Int) : InputDecision
}

object MentorGate {
    const val SOS_LEVEL = 2

    /**
     * @param assess CrisisGuard level of the words (0–3)
     * @param crisisActive the safety bus holds CrisisGuard ≥ 2 within its window (NO_COACHING)
     */
    fun before(text: String, assess: (String) -> Int, crisisActive: Boolean): InputDecision {
        val level = assess(text)
        return when {
            level >= SOS_LEVEL -> InputDecision.Sos(level)
            crisisActive -> InputDecision.Sos(SOS_LEVEL)
            else -> InputDecision.Send(level)
        }
    }

    /** The model layer may only raise (CrisisGuard.merge): the larger of the two acts. */
    fun merge(device: Int, model: Int?): Int = if (model != null && model > device) model else device
}

/** Why an answer was replaced, for the metrics of block 9 — never the words. */
enum class GuardReason { ED_CARE, DOSE, DIAGNOSIS }

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
 * - Doses: no number with a unit of a dose (мг, мкг, МЕ, mg, mcg, IU) — doses are with the doctor.
 * - Diagnosis: no «у тебя <болезнь>», «это точно <болезнь>», «диагноз: …».
 *
 * Черновик — пороги и строки ждут ревью Агентов 3 и 10.
 */
object ResponseGuard {
    private val dose = Regex("(?iU)\\d+([.,]\\d+)?\\s?(мг|мкг|ме|мл/кг|mg|mcg|µg|iu)(?!\\p{L})")

    private val diagnosis = Regex(
        "(?iU)(у (тебя|вас) (точно |скорее всего |наверняка )?(диабет|депресси\\w*|анорекси\\w*|булими\\w*|гипертони\\w*|аритми\\w*|инфаркт|инсульт|рак|опухол\\w*|грыж\\w*|разрыв\\w*)|" +
            "(это|похоже на) (точно )?(диабет|депресси\\w*|инфаркт|инсульт|рак|опухол\\w*)|" +
            "диагноз:|ставлю диагноз)",
    )

    fun check(text: String, restrictions: Set<String>, edCare: Boolean): GuardResult {
        val foodHidden = edCare || EdCareText.HIDING.any { it in restrictions }
        return when {
            foodHidden && EdCareText.mentionsHidden(text) -> GuardResult.Replaced(GuardReason.ED_CARE)
            dose.containsMatchIn(text) -> GuardResult.Replaced(GuardReason.DOSE)
            diagnosis.containsMatchIn(text.replace('ё', 'е')) -> GuardResult.Replaced(GuardReason.DIAGNOSIS)
            else -> GuardResult.Pass(text)
        }
    }
}
