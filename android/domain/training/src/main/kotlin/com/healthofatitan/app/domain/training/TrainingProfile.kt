package com.healthofatitan.app.domain.training

/**
 * What the user told us about the body (shared profile, content/profile/constraints.yaml §5–§6,
 * A41). Nothing here is a diagnosis: these are self-reports that switch the training into a
 * safer mode. The training codes of A40 §8 are **derived** from it by `rules.profile_codes`;
 * the user never sees the codes.
 *
 * @param injuries region (`injury_regions`) → status (`acute`, `recovering`, `chronic_stable`)
 * @param screeningYes the A19 §6.1 questions answered «да» (S1…S9)
 * @param doctorCleared «врач разрешил нагрузки» after a «да» in the screening
 * @param selfReports codes reported directly (`eye_pressure`, `hernia`)
 */
data class TrainingProfile(
    val injuries: Map<String, String> = emptyMap(),
    val movementLimits: Set<String> = emptySet(),
    val lifeStages: Set<String> = emptySet(),
    val medicalFlags: Set<String> = emptySet(),
    val screeningYes: Set<String> = emptySet(),
    val doctorCleared: Boolean = false,
    val selfReports: Set<String> = emptySet(),
    val extremeConsent: Boolean = false,
) {
    /** The training codes this profile switches on (A40 §8), in a stable order. */
    fun codes(rules: TrainingRules): Set<String> = rules.profileCodes.values
        .filter { matches(it) }
        .map { it.id }
        .toSortedSet()

    private fun matches(rule: ProfileCodeRule): Boolean {
        val from = rule.from
        if (from.isEmpty()) return false
        // Every source of a code is an alternative: one hit is enough (the safer reading).
        from.str("injury")?.let { region ->
            val statuses = from.strings("status")
            val status = injuries[region]
            if (status != null && (statuses.isEmpty() || status in statuses)) return true
        }
        from.str("movement_limit")?.let { if (it in movementLimits) return true }
        from.strings("life_stage").let { stages -> if (stages.any { it in lifeStages }) return true }
        from.str("medical_flag")?.let { if (it in medicalFlags) return true }
        from.str("screening")?.let { s ->
            when {
                s.matches(Regex("S\\d+")) -> if (s in screeningYes) return true
                // «any yes without врач разрешил нагрузки»
                else -> if (screeningYes.isNotEmpty() && !doctorCleared) return true
            }
        }
        if (from.containsKey("self_report") && rule.id in selfReports) return true
        if (from.containsKey("consent") && !extremeConsent) return true
        return false
    }
}
