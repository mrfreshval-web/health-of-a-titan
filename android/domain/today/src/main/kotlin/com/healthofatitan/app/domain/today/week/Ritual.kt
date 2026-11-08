package com.healthofatitan.app.domain.today.week

import com.healthofatitan.app.domain.today.GoalMeasure
import com.healthofatitan.app.domain.today.GoalSource
import com.healthofatitan.app.domain.today.Sphere
import com.healthofatitan.app.domain.today.TaskKind
import com.healthofatitan.app.domain.today.WeekGoal
import com.healthofatitan.app.domain.today.WeekPlan

/** The six steps of «Итог и сборка» (A52 §2.6), 3–5 minutes. */
/** A53 §3.4: the Sculpture, the answer of the experiment, work, one skew, the next week (a new experiment and goals), forward. */
enum class RitualStep { FRAME, EXPERIMENT, WORKED, WORK, SKEW, NEXT, FORWARD }

/** «Беру · Как есть · Полегче» on the one skew of the week (A52 §2.6 п. 4). */
enum class SkewChoice { TAKE, AS_IS, EASIER }

/** What the ritual shows; built from the week and the proposal of the next one. */
data class RitualView(
    val steps: List<RitualStep>,
    val beforeAfter: String?,
    val worked: String,
    val skew: Skew?,
    val proposal: Proposal,
    /** The answer of the experiment of the closing week («узнали»), or null when there was none. */
    val experimentResult: ExperimentResult? = null,
    /** At most 3 experiments for the next week; the first is the one HoT proposes. */
    val experimentOffers: List<ExperimentSeed> = emptyList(),
)

/** What the person answered. */
data class RitualAnswers(
    val pride: String? = null,
    val carry: String? = null,
    val carryToGoal: Boolean = false,
    val skew: SkewChoice = SkewChoice.AS_IS,
    /** Targets the person changed by one tap: goal id → new target; 0 removes the goal. */
    val targets: Map<String, Int> = emptyMap(),
    /** Patterns taken as «Попробовать на неделе». */
    val tried: List<WeekGoal> = emptyList(),
    /** The experiment of the next week; null — «не сейчас». */
    val experiment: ExperimentSeed? = null,
)

object RitualRules {
    const val CARRY_ID = "reminder-carry"
    const val SKEW_ID_PREFIX = "reminder-skew-"

    /** Small reminders «Беру» makes of a skew (ждёт ревью 3, 10, 16). */
    val SKEW_TITLES: Map<SupportWeekId, String> = mapOf(
        SupportWeekId.MOVEMENT to "Прогулка после еды",
        SupportWeekId.DRINK to "Стакан воды к приёму пищи",
        SupportWeekId.SLEEP to "Отбой на 30 минут раньше",
        SupportWeekId.BALANCE to "Один лёгкий день",
        SupportWeekId.NUTRITION to "Белок к завтраку",
    )

    fun view(week: WeekView, proposal: Proposal, result: ExperimentResult? = null, offers: List<ExperimentSeed> = emptyList()): RitualView {
        val skew = week.supports.firstNotNullOfOrNull { it.skew }
        val worked = week.patterns.firstOrNull()?.pattern?.text
            ?: week.titan.detail?.words?.replaceFirstChar { it.uppercase() }
            ?: WeekCopy.R_WORKED_NONE
        val steps = RitualStep.entries.filter { (it != RitualStep.SKEW || skew != null) && (it != RitualStep.EXPERIMENT || result != null) }
        return RitualView(
            steps = steps,
            beforeAfter = null,
            worked = worked,
            skew = skew,
            proposal = proposal,
            experimentResult = result,
            experimentOffers = offers,
        )
    }

    fun withBeforeAfter(v: RitualView, before: Int?, after: Int): RitualView =
        v.copy(beforeAfter = before?.let { WeekCopy.beforeAfter(it, after) })

    /** «Беру» turns the skew into one small reminder of the next week. */
    fun skewGoal(skew: Skew): WeekGoal? {
        val title = SKEW_TITLES[skew.support] ?: return null
        return WeekGoal(SKEW_ID_PREFIX + skew.support.name.lowercase(), Sphere.BODY, TaskKind.REMINDER, title, 3, WeekCopy.UNIT_DAYS, 0, GoalSource.PERSON, GoalMeasure.DAYS)
    }

    /** The goal «Полегче» softens for a skew. */
    private fun related(skew: Skew): String? = when (skew.support) {
        SupportWeekId.BALANCE, SupportWeekId.MOVEMENT -> WeekGoalRules.ID_TRAINING
        SupportWeekId.SLEEP -> WeekGoalRules.ID_SLEEP
        else -> null
    }

    /** The week the ritual assembles: the proposal with the person's taps applied. */
    fun apply(proposal: Proposal, skew: Skew?, a: RitualAnswers): WeekPlan {
        var goals = proposal.plan.goals
        goals = goals.mapNotNull { g ->
            when (val t = a.targets[g.id]) {
                null -> g
                0 -> null
                else -> g.copy(target = t.coerceAtLeast(1))
            }
        }
        if (skew != null) {
            when (a.skew) {
                SkewChoice.TAKE -> skewGoal(skew)?.let { sg -> if (goals.none { it.id == sg.id }) goals = goals + sg }
                SkewChoice.EASIER -> related(skew)?.let { id -> goals = goals.map { if (it.id == id) WeekGoalRules.lighter(it) else it } }
                SkewChoice.AS_IS -> Unit
            }
        }
        a.tried.forEach { t -> if (goals.none { it.id == t.id }) goals = goals + t }
        val carry = a.carry?.trim()?.takeIf { it.isNotEmpty() && a.carryToGoal }
        if (carry != null && goals.none { it.id == CARRY_ID }) {
            goals = goals + WeekGoal(CARRY_ID, Sphere.WORK, TaskKind.REMINDER, carry.take(60), 2, WeekCopy.UNIT_DAYS, 0, GoalSource.PERSON, GoalMeasure.DAYS)
        }
        return WeekPlan(proposal.plan.weekStart, goals)
    }
}
