package com.healthofatitan.app.core.model.safety

/**
 * The closed codes of the safety events in the event journal (`event_log`, A58 §8) — shared by
 * the store that writes them (:core:database) and the bus that projects them (:domain:safety).
 * Codes only, never words (A07 §4.7).
 */
object SafetyEventKinds {
    const val PREFIX = "safety."

    /** CrisisGuard set a level; payload: 1–3. */
    const val CRISIS = "safety.crisis"

    /** `ED_CARE` began; payload: the reason of entry (`signals | screen | self_report | body | specialist | self_choice`). */
    const val ED_CARE_ENTER = "safety.ed_care.enter"

    /** The person chose to leave `ED_CARE`. */
    const val ED_CARE_EXIT = "safety.ed_care.exit"

    /** The person chose a step of the return; payload: 1–3. */
    const val ED_CARE_STEP = "safety.ed_care.step"

    /** The person took one thing back on step 3; payload: `protein | photo_feedback | form | weight_trend`. */
    const val ED_CARE_RETURN = "safety.ed_care.return"

    const val REASON_SELF_REPORT = "self_report"
    const val REASON_BODY = "body"

    /**
     * The codes of the profile of specifics (A41, `medical_flags`) that start `ED_CARE`, and the
     * reason each stands for (A60 §3.4). Of several at once the longer cooldown wins (`body`).
     * `ed_signals` — самоотметка в анкете, не сигналы A19. Ревью Агента 3 · 2026-11-08: принято.
     */
    val ED_PROFILE_CODES: Map<String, String> = mapOf(
        "ed_history" to REASON_SELF_REPORT,
        "ed_signals" to REASON_SELF_REPORT,
        "underweight_reported" to REASON_BODY,
    )
}
