package com.healthofatitan.app.domain.safety

import com.healthofatitan.app.core.model.safety.SafetyEventKinds
import java.time.Duration

/**
 * Why `ED_CARE` began (A60 §3.4, D-057 variant В, `A63-open/ed-care.md` §3–§5). The reason sets
 * the cooldown before the person may choose to leave; HoT never leaves by itself.
 *
 * **Сроки охлаждения ждут клинического ревью Агента 3** (и Агентов 10, 12; до кода их проверяет
 * живой клинический специалист, A63-open/ed-care.md §11). They are constants here so that the
 * review changes one line.
 */
enum class EdCareReason(val code: String, val cooldown: Duration) {
    /** ≥ 2 different weak signals in 14 days or N19 on 10 of 14 days. */
    SIGNALS("signals", Duration.ofDays(EdCareRules.COOLDOWN_SIGNALS_DAYS)),

    /** SCOFF ≥ 2. */
    SCREEN("screen", Duration.ofDays(EdCareRules.COOLDOWN_SCREEN_DAYS)),

    /** Знакомство Ч3 №4 or the profile of specifics (`ed_history`, `ed_signals`). */
    SELF_REPORT("self_report", Duration.ofDays(EdCareRules.COOLDOWN_SELF_REPORT_DAYS)),

    /** Signs of the body (`underweight_reported`). */
    BODY("body", Duration.ofDays(EdCareRules.COOLDOWN_BODY_DAYS)),

    /** «Работаю со специалистом». */
    SPECIALIST("specialist", Duration.ofDays(EdCareRules.COOLDOWN_SPECIALIST_DAYS)),

    /** «Еда без цифр» in the settings: the person's own choice, the exit is open at any moment. */
    SELF_CHOICE("self_choice", Duration.ZERO),
    ;

    companion object {
        fun parse(code: String?): EdCareReason? = entries.firstOrNull { it.code == code }
    }
}

/** What may come back on step 3, each by its own choice (A60 §3.4). Calories are never here. */
enum class EdCareReturn(val code: String) {
    PROTEIN("protein"),
    PHOTO_FEEDBACK("photo_feedback"),
    FORM("form"),
    WEIGHT_TREND("weight_trend"),
    ;

    companion object {
        fun parse(code: String?): EdCareReturn? = entries.firstOrNull { it.code == code }
    }
}

object EdCareRules {
    // Ждёт клинического ревью Агента 3 (D-057 п. 2): 7 / 14 / 28 дней по причине входа.
    const val COOLDOWN_SIGNALS_DAYS = 7L
    const val COOLDOWN_SCREEN_DAYS = 14L
    const val COOLDOWN_SELF_REPORT_DAYS = 14L
    const val COOLDOWN_BODY_DAYS = 28L
    const val COOLDOWN_SPECIALIST_DAYS = 28L

    /** The steps of the return (A60 §3.4): 0 — in the mode; 1 — food without numbers; 2 — load and «Топливо» without numbers; 3 — by separate choice. */
    const val STEP_IN_MODE = 0
    const val STEP_FOOD_NO_NUMBERS = 1
    const val STEP_LAST = 3

    /** The profile codes of A41 that start `ED_CARE`, and the reason each one stands for. */
    val PROFILE_CODES: Map<String, EdCareReason> =
        SafetyEventKinds.ED_PROFILE_CODES.mapValues { (_, reason) -> requireNotNull(EdCareReason.parse(reason)) }
}

/**
 * `ED_CARE` as the journal left it.
 *
 * @property active the mode is on; only the person's choice turns it off, after [cooldownEndsAt]
 * @property history the mode was ever on (`ed_care_history`): food windows and body photo stay closed
 * @property step the step of the return after the exit (A60 §3.4), chosen by the person
 * @property returned what the person took back on step 3, one by one
 */
data class EdCareState(
    val active: Boolean = false,
    val reason: EdCareReason? = null,
    val since: Long? = null,
    val cooldownEndsAt: Long? = null,
    val history: Boolean = false,
    val step: Int = EdCareRules.STEP_IN_MODE,
    val returned: Set<EdCareReturn> = emptySet(),
) {
    /** The cooldown is over: HoT may offer the three questions of the exit; the choice stays the person's. */
    fun exitOpen(nowMs: Long): Boolean = active && (cooldownEndsAt ?: Long.MAX_VALUE) <= nowMs

    /**
     * What stays hidden after the exit: nothing comes back by itself (D-057 п. 2). Calories, food
     * windows and body photo stay closed while the history is there; weight and nutrients only by
     * the person's separate choice on step 3.
     */
    fun afterExit(): Set<Restriction> {
        if (active || !history) return emptySet()
        return buildSet {
            add(Restriction.HIDE_CALORIES)
            add(Restriction.HIDE_FOOD_WINDOWS)
            add(Restriction.HIDE_BODY_PHOTO)
            if (step < EdCareRules.STEP_FOOD_NO_NUMBERS) add(Restriction.HIDE_FOOD_TALK)
            if (EdCareReturn.PROTEIN !in returned) add(Restriction.HIDE_NUTRIENTS)
            if (EdCareReturn.WEIGHT_TREND !in returned) add(Restriction.HIDE_WEIGHT)
            // The fitness age is a number about the body: HUNT reads the waist too (A52 §2.8, П-3).
            if (EdCareReturn.WEIGHT_TREND !in returned || EdCareReturn.FORM !in returned) add(Restriction.NO_FITNESS_AGE)
        }
    }

    companion object {
        val NONE = EdCareState()
    }
}
