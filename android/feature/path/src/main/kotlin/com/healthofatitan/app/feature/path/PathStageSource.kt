package com.healthofatitan.app.feature.path

import com.healthofatitan.app.core.database.HotDatabase
import com.healthofatitan.app.core.database.dao.WeekDao
import com.healthofatitan.app.domain.path.PracticalGoal
import com.healthofatitan.app.domain.path.StageRules
import com.healthofatitan.app.domain.today.CalendarWeek
import com.healthofatitan.app.domain.today.WeekPlan
import com.healthofatitan.app.feature.path.logic.PathStage
import com.healthofatitan.app.integration.whoop.day.WeekPlanSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * What «Неделя» says about the stage (A66 §5.2): the plan of this week with its progress — for the
 * lever line and «Цели недели · 2 из 3 ›» — and which past weeks of the stage were counted.
 */
data class StageProgress(val plan: WeekPlan? = null, val counted: Set<Int> = emptySet())

/**
 * Reads the week the way «Неделя» counts it ([WeekPlanSource], the same numbers). A past week is
 * judged only when its plan is stored: no plan is ever made for the past from here, and a week
 * without one stays a neutral cell (§5.4: «не в этот раз», never red). No Room migration.
 */
class PathStageSource @Inject constructor(
    private val weeks: WeekPlanSource,
    private val weekDao: WeekDao,
    private val clock: Clock,
) {
    private val user = HotDatabase.LOCAL_USER

    fun progress(goal: PracticalGoal?): Flow<StageProgress> {
        if (goal == null) return flowOf(StageProgress())
        val today = LocalDate.now(clock)
        val plan: Flow<WeekPlan?> = weeks.week(today).map<WeekPlan, WeekPlan?> { it }.catch { emit(null) }
        val counted: Flow<Set<Int>> = flow { emit(counted(goal, today)) }.catch { emit(emptySet()) }
        return combine(plan, counted) { p, c -> StageProgress(p, c) }
    }

    private suspend fun counted(goal: PracticalGoal, today: LocalDate): Set<Int> {
        val current = StageRules.week(goal, today)
        return (1 until current).filterTo(mutableSetOf()) { w ->
            val end = PathStage.stageWeekEnd(goal, w)
            if (end.isAfter(today)) return@filterTo false
            val monday = CalendarWeek.start(end)
            if (runCatching { weekDao.plan(user, monday.toString()) }.getOrNull() == null) return@filterTo false
            val plan = runCatching { weeks.week(end).first() }.getOrNull() ?: return@filterTo false
            PathStage.counted(plan.goals)
        }
    }
}
