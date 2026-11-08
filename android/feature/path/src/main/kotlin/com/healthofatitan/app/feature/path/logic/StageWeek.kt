package com.healthofatitan.app.feature.path.logic

import com.healthofatitan.app.domain.path.ComponentKey
import com.healthofatitan.app.domain.path.GoalStatus
import com.healthofatitan.app.domain.path.PathFlags
import com.healthofatitan.app.domain.path.PracticalGoal
import com.healthofatitan.app.domain.path.Role
import com.healthofatitan.app.domain.today.week.PathWeek
import com.healthofatitan.app.domain.today.week.WeekGoalRules

/**
 * The stage of a goal → the goals of the week (A67 С-3, мост ①). Only the existing week goals of
 * [WeekGoalRules.defaults] are used, no new norms (A67 «Принятые допущения»):
 * Сердце и дыхание → minutes in zones, Сила → workouts (behind the flag of weight and strength,
 * D-047), Внимание → focus blocks, Сон → nights. Every other component has no week goal in the
 * MVP. The lever comes first, then the leading components; supporting and guarding ones do not
 * make goals of the week.
 */
object StageWeek {
    /** The mark of the goal on a step of the day is short (A67 §1.3, design council #53 В2). */
    const val NAME_MAX = 28

    fun goalId(key: ComponentKey, flags: PathFlags): String? = when (key) {
        ComponentKey.CARDIO -> WeekGoalRules.ID_ZONES
        ComponentKey.STRENGTH -> WeekGoalRules.ID_TRAINING.takeIf { flags.weightAndStrength }
        ComponentKey.ATTENTION -> WeekGoalRules.ID_FOCUS
        ComponentKey.SLEEP -> WeekGoalRules.ID_SLEEP
        else -> null
    }

    /**
     * What [goal] gives the weeks: an [GoalStatus.ACTIVE] goal its stage; a goal on pause or held
     * by the safety bus a pause (the calendar stands, A60 §2); anything else — no goal (A67 §4).
     */
    fun of(goal: PracticalGoal?, flags: PathFlags): PathWeek = when (goal?.status) {
        GoalStatus.ACTIVE -> PathWeek.Active(name(goal.title), goalIds(goal, flags), goal.start, goal.pausedDays)
        GoalStatus.PAUSED, GoalStatus.HELD -> PathWeek.Paused
        else -> PathWeek.NoGoal
    }

    fun goalIds(goal: PracticalGoal, flags: PathFlags): List<String> =
        goal.components
            .filter { it.isLever || it.role == Role.LEAD }
            .sortedBy { if (it.isLever) 0 else 1 }
            .mapNotNull { goalId(it.key, flags) }
            .distinct()
            .take(WeekGoalRules.STAGE_MAX)

    /** The short name of the goal for the mark: the person's own words, cut at a word when long. */
    fun name(title: String): String {
        val t = title.trim().replace(Regex("\\s+"), " ")
        if (t.length <= NAME_MAX) return t
        val cut = t.take(NAME_MAX - 1)
        val word = cut.substringBeforeLast(' ', cut).takeIf { it.length >= NAME_MAX / 2 } ?: cut
        return word.trimEnd(' ', ',', '.', '—', '-') + "…"
    }
}
