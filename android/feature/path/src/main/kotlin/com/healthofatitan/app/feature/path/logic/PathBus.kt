package com.healthofatitan.app.feature.path.logic

import com.healthofatitan.app.domain.path.GivenLayer
import com.healthofatitan.app.domain.path.PathSafety
import com.healthofatitan.app.domain.safety.Restriction
import com.healthofatitan.app.domain.safety.SafetyConsumer
import com.healthofatitan.app.domain.safety.SafetyState

/**
 * «Путь» reads the safety bus (D-051, A60 §3): the column PATH of the matrix becomes the holds of
 * the Path. The goal's own pause and hold and the hard week stay as they were ([PathSafety.LOCAL]);
 * the bus only adds — the strictest wins, it never releases a hold.
 * - `PATH_GENTLE` (crisis, STORM, light week): the Titan holds the vault with one hand, Water «держим»;
 * - `PATH_STANDS` (SICK, GRIEF): the goal is held, the calendar stands;
 * - `HIDE_WEIGHT` (`ED_CARE`, also after its exit until weight is taken back): «Дано» closes
 *   weight goals, «Форма» and «Топливо» as for `ed_care` of the profile.
 */
object PathBus {
    fun safety(state: SafetyState): PathSafety {
        val r = state.restrictions(SafetyConsumer.PATH)
        return PathSafety { goal, heavyWeek ->
            val local = PathSafety.LOCAL.hold(goal, heavyWeek)
            local.copy(
                held = local.held || Restriction.PATH_STANDS in r,
                heavy = local.heavy || Restriction.PATH_GENTLE in r,
            )
        }
    }

    fun given(given: GivenLayer, state: SafetyState): GivenLayer =
        if (!given.edCare && Restriction.HIDE_WEIGHT in state.restrictions(SafetyConsumer.PATH)) given.copy(edCare = true) else given
}
