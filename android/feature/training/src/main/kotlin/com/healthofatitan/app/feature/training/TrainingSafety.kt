package com.healthofatitan.app.feature.training

import com.healthofatitan.app.core.model.profile.ProfileDictionary
import com.healthofatitan.app.core.model.profile.UserConstraints
import com.healthofatitan.app.domain.safety.Restriction
import com.healthofatitan.app.domain.safety.SafetyConsumer
import com.healthofatitan.app.domain.safety.SafetyState
import com.healthofatitan.app.domain.training.TrainingFlag
import com.healthofatitan.app.domain.training.TrainingProfile
import com.healthofatitan.app.domain.training.TrainingRules

/**
 * What the profile of specifics (S4) and the safety bus add to the training of the day (audit
 * 08.11 Р-1, slice S1; A40 §4.1 layers «флаги» and «профиль»): the training codes of A40 §8 and the
 * gate flags. Before this slice the request always went with no codes and no bus flags, so a knee
 * injury or a pregnancy in «Я › Еда и тело» did not change the session.
 */
data class TrainingGuard(
    val codes: Set<String> = emptySet(),
    val flags: Set<TrainingFlag> = emptySet(),
) {
    companion object {
        val NONE = TrainingGuard()
    }
}

object TrainingSafety {

    /**
     * The code of TG-04 (`rules.yaml`): the gentle Classic — RPE ≤ 6, no heavy load, no straining,
     * no HIIT. Used for every «врач до интенсивности» until the screen «врач разрешил нагрузки»
     * exists (Р-14): the stricter reading, открытый вопрос Агенту 3 (S1 ⚑3).
     */
    const val GENTLE_CODE = "screening_not_cleared"

    /** The marker of «a yes in the screening» that the profile itself gives (no S-number). */
    private const val PROFILE_SCREEN = "profile"

    private const val ROUTE_DOCTOR_BEFORE_INTENSITY = "doctor_before_intensity"
    private const val ROUTE_DOCTOR = "doctor"
    private const val MODE_SAFE = "safe"

    /** acute is the strictest status of a region, chronic_stable the mildest. */
    private val STATUS_ORDER = listOf("acute", "recovering", "chronic_stable")

    /**
     * The shared profile → [TrainingProfile] (A40 §8). Without the dictionary nothing can be read,
     * so a non-empty profile then takes the gentle Classic (the safer reading).
     */
    fun profile(c: UserConstraints, dict: ProfileDictionary?): TrainingProfile {
        if (c.isEmpty) return TrainingProfile()
        if (dict == null) return TrainingProfile(screeningYes = setOf(PROFILE_SCREEN))
        val injuries = mutableMapOf<String, String>()
        for (id in c.injuries) {
            val preset = dict.injury(id) ?: continue
            val region = preset.region ?: continue
            val status = preset.status ?: continue
            val was = injuries[region]
            if (was == null || rank(status) < rank(was)) injuries[region] = status
        }
        val medical = c.medical.mapNotNull { dict.medical(it) }
        val stages = c.lifeStage.mapNotNull { dict.lifeStage(it) }
        val needsDoctor = medical.any { it.mode == MODE_SAFE || it.trainingRoute() == ROUTE_DOCTOR_BEFORE_INTENSITY } ||
            // A stage with «к врачу» but without «program: none» (lactation): gentle until the doctor.
            stages.any { it.trainingRoute() == ROUTE_DOCTOR }
        return TrainingProfile(
            injuries = injuries,
            movementLimits = dict.movementLimits(c),
            lifeStages = c.lifeStage,
            medicalFlags = c.medical,
            screeningYes = if (needsDoctor) setOf(PROFILE_SCREEN) else emptySet(),
        )
    }

    /** `training.no_extra_sessions` of a medical flag (`ed_history`): the TG-06 gate. */
    fun profileFlags(c: UserConstraints, dict: ProfileDictionary?): Set<TrainingFlag> {
        val noExtra = c.medical.mapNotNull { dict?.medical(it) }.any { it.training["no_extra_sessions"] == true }
        return if (noExtra) setOf(TrainingFlag.ED_CARE) else emptySet()
    }

    /** The bus → gate flags and codes of the training (column TRAINING of [com.healthofatitan.app.domain.safety.SafetyPolicy]). */
    fun bus(restrictions: Set<Restriction>): TrainingGuard {
        val flags = buildSet {
            if (Restriction.HELP_FIRST in restrictions) add(TrainingFlag.EMERGENCY)
            if (Restriction.NO_LOAD in restrictions) add(TrainingFlag.SICK)
            if (Restriction.HIDE_FOOD_TALK in restrictions) add(TrainingFlag.ED_CARE)
        }
        val codes = if (Restriction.INTENSITY_CAP in restrictions) setOf(GENTLE_CODE) else emptySet()
        return TrainingGuard(codes, flags)
    }

    /** Everything together: the strictest wins, nothing is subtracted (rule 1 of the bus). */
    fun guard(c: UserConstraints, dict: ProfileDictionary?, state: SafetyState, rules: TrainingRules): TrainingGuard {
        val fromBus = bus(state.restrictions(SafetyConsumer.TRAINING))
        return TrainingGuard(
            codes = profile(c, dict).codes(rules) + fromBus.codes,
            flags = profileFlags(c, dict) + fromBus.flags,
        )
    }

    private fun rank(status: String): Int = STATUS_ORDER.indexOf(status).let { if (it < 0) 0 else it }

    private fun ProfileDictionary.SafetyFlag.trainingRoute(): String? = training["route"] as? String
}
