package com.healthofatitan.app.domain.mentor

/**
 * What goes to the model with a conversation (A62 §4, levels А/В; A65 §5.2; D-057 п. 5). The
 * class has a field for everything that may leave the phone and nothing else: there is no field
 * for photos, WHOOP or Health Connect numbers, the profile of specifics, the reasons of `ED_CARE`
 * or the words of a crisis, so none of them can be put in by mistake.
 *
 * In this slice:
 *  - always: the address form, the restriction codes of the safety bus (consumer MENTOR) and the
 *    flags `ed_care`, `recent_crisis`, `gentle` — names only;
 *  - the last summaries of past conversations (phone-only chronicle, A62 §11 п. 2), filtered by
 *    [EdCareText] while food and weight are hidden;
 *  - «О себе» only with the consent `about_mentor` (A65 §5.2), filtered the same way.
 * WHOOP and Health Connect levels are not sent: D-057 п. 5 asks for an opt-in first (Агент 7).
 *
 * @property addressForm `n`, `m` or `f` (D-055)
 */
data class ContextPack(
    val addressForm: String,
    val restrictions: List<String>,
    val flags: List<String>,
    val summaries: List<String>,
    val about: String? = null,
) {
    companion object {
        const val FLAG_ED_CARE = "ed_care"
        const val FLAG_RECENT_CRISIS = "recent_crisis"
        const val FLAG_GENTLE = "gentle"
        const val MAX_SUMMARIES = 3
        const val MAX_SUMMARY_CHARS = 600
        const val MAX_ABOUT_CHARS = 1000
    }
}

/** The consents that open more of the pack. All off by default. */
data class MentorConsents(
    /** A65 §5.2: «О себе» may go to Винс. Not granted anywhere in this slice. */
    val aboutMentor: Boolean = false,
)

/** What the phone knows when it builds the pack; plain values, no Android, no bus types. */
data class PackInputs(
    val addressForm: String = "n",
    /** Names of [com.healthofatitan.app.domain.safety.Restriction] for the consumer MENTOR. */
    val restrictions: Set<String> = emptySet(),
    val edCare: Boolean = false,
    /** CrisisGuard ≥ 2 within its window (the bus says so); details never leave the phone. */
    val recentCrisis: Boolean = false,
    /** STORM or GRIEF: the bus asks for a sober tone. */
    val gentle: Boolean = false,
    /** Newest last. */
    val summaries: List<String> = emptyList(),
    val about: String? = null,
    val consents: MentorConsents = MentorConsents(),
)

object ContextPackBuilder {
    fun build(inputs: PackInputs): ContextPack {
        val foodHidden = inputs.edCare || EdCareText.HIDING.any { it in inputs.restrictions }
        val keep: (String) -> Boolean = { !foodHidden || !EdCareText.mentionsHidden(it) }
        val summaries = inputs.summaries
            .map { it.trim().take(ContextPack.MAX_SUMMARY_CHARS) }
            .filter { it.isNotEmpty() && keep(it) }
            .takeLast(ContextPack.MAX_SUMMARIES)
        val about = inputs.about
            ?.takeIf { inputs.consents.aboutMentor }
            ?.trim()?.take(ContextPack.MAX_ABOUT_CHARS)
            ?.takeIf { it.isNotEmpty() && keep(it) }
        val flags = buildList {
            if (inputs.edCare) add(ContextPack.FLAG_ED_CARE)
            if (inputs.recentCrisis) add(ContextPack.FLAG_RECENT_CRISIS)
            if (inputs.gentle) add(ContextPack.FLAG_GENTLE)
        }
        return ContextPack(
            addressForm = inputs.addressForm.takeIf { it in setOf("n", "m", "f") } ?: "n",
            restrictions = inputs.restrictions.sorted(),
            flags = flags,
            summaries = summaries,
            about = about,
        )
    }
}

/**
 * Words about calories, body weight and dieting (A60 §3.4, A63 §5.2): under `ED_CARE` they do not
 * reach the model from the pack and do not reach the person from an answer ([ResponseGuard]).
 * Strict on purpose: «рабочий вес» in a training answer is caught too and replaced by a safe line;
 * a false alarm costs one line, a miss costs trust (ревью Агента 10).
 */
object EdCareText {
    /** Restrictions that mean «food and weight are hidden». */
    val HIDING = setOf("HIDE_CALORIES", "HIDE_WEIGHT", "HIDE_FOOD_TALK", "HIDE_NUTRIENTS")

    private val hidden = Regex(
        "(?iU)(калори\\w*|ккал|kcal|calori\\w*|" +
            "(?<!\\p{L})вес(а|у|ом|е)?(?!\\p{L})|весишь|взвес\\w*|похуд\\w*|сброс\\w* (\\d+ )?(кг|кило)|набра\\w* (\\d+ )?(кг|кило)|" +
            "имт|индекс массы|дефицит|профицит|диет\\w*|голодан\\w*|интервальн\\w* голод\\w*|" +
            "бжу|макрос\\w*|lose weight|weight loss|bmi)",
    )

    fun mentionsHidden(text: String): Boolean = hidden.containsMatchIn(text.replace('ё', 'е'))
}
