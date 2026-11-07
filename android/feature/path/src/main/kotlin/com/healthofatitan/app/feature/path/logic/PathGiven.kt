package com.healthofatitan.app.feature.path.logic

import com.healthofatitan.app.domain.path.ComponentKey
import com.healthofatitan.app.domain.path.GivenConstraint
import com.healthofatitan.app.domain.path.GivenLayer

/**
 * «Дано» from the profile of specifics (S4, content/profile/constraints.yaml): the codes of
 * injuries, life stage and medical flags become hard filters of the path (A54 §2.4, A56 §2.2).
 * Without the S4 consent the profile is empty and so is «Дано».
 *
 * **Черновик, ждёт ревью Агента 3:** which parts each code touches and the words of the safe
 * versions. No diagnosis is named: only what the path keeps safe.
 */
object PathGiven {
    private class Rule(val label: String, val affects: Set<ComponentKey>, val safe: String?)

    private val RULES: Map<String, Rule> = mapOf(
        "knee_injury" to Rule("колено", setOf(ComponentKey.CARDIO, ComponentKey.STRENGTH, ComponentKey.MOBILITY), "без прыжков и глубоких приседаний"),
        "ankle_injury" to Rule("голеностоп", setOf(ComponentKey.CARDIO, ComponentKey.MOBILITY), "без прыжков"),
        "hip_pain" to Rule("тазобедренный сустав", setOf(ComponentKey.STRENGTH, ComponentKey.MOBILITY), "без глубоких приседаний"),
        "lower_back_pain" to Rule("поясница", setOf(ComponentKey.STRENGTH, ComponentKey.MOBILITY), "без нагруженных наклонов"),
        "shoulder_injury" to Rule("плечо", setOf(ComponentKey.STRENGTH), "без работы над головой"),
        "hypertension" to Rule("давление", setOf(ComponentKey.CARDIO, ComponentKey.STRENGTH), "без задержки дыхания и высокой интенсивности"),
        "heart_bp_meds" to Rule("давление", setOf(ComponentKey.CARDIO, ComponentKey.STRENGTH), "без задержки дыхания и высокой интенсивности"),
        "osteoporosis_reported" to Rule("кости", setOf(ComponentKey.CARDIO, ComponentKey.STRENGTH), "без ударной нагрузки"),
        "pregnancy" to Rule("беременность", setOf(ComponentKey.CARDIO, ComponentKey.STRENGTH, ComponentKey.BODY), "мягкая нагрузка, по согласованию с врачом"),
        "breastfeeding" to Rule("кормление грудью", setOf(ComponentKey.BODY), null),
    )

    /** `ed_care` (D-043): no goals on weight, «Форма» and «Топливо» are hidden. */
    private val ED_CARE = setOf("ed_history", "ed_signals", "underweight_reported")
    private val MEDICAL = setOf("pregnancy", "breastfeeding", "heart_bp_meds", "hypertension", "other_chronic")

    fun from(codes: Set<String>): GivenLayer {
        val constraints = codes.sorted().mapNotNull { code ->
            val r = RULES[code] ?: return@mapNotNull null
            GivenConstraint(code, r.label, r.affects, r.safe)
        }.distinctBy { it.label }
        return GivenLayer(
            constraints = constraints,
            edCare = codes.any { it in ED_CARE },
            pregnancyOrMedical = codes.any { it in MEDICAL },
        )
    }

    /** The words of every rule, for the stop-dictionary test. */
    fun words(): List<String> = RULES.values.flatMap { listOfNotNull(it.label, it.safe) }
}
