package com.healthofatitan.app.feature.path

import com.healthofatitan.app.core.database.HotDatabase
import com.healthofatitan.app.core.database.dao.PathDao
import com.healthofatitan.app.core.database.entity.PathGoalEntity
import com.healthofatitan.app.core.database.entity.PathStepEntity
import com.healthofatitan.app.domain.engine.Regime
import com.healthofatitan.app.domain.metrics.Metrics
import com.healthofatitan.app.domain.path.GoalPlan
import com.healthofatitan.app.domain.path.GoalRecord
import com.healthofatitan.app.domain.path.GoalStatus
import com.healthofatitan.app.domain.path.PathEngine
import com.healthofatitan.app.domain.path.PathEvidence
import com.healthofatitan.app.domain.path.PathRegime
import com.healthofatitan.app.domain.path.PathState
import com.healthofatitan.app.domain.path.Sphere
import com.healthofatitan.app.domain.path.StepVersion
import com.healthofatitan.app.integration.whoop.day.DayPlanRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** What «Путь» and the step of the day need: the path, today's mode and bedtime. */
data class PathView(
    val state: PathState,
    val regime: PathRegime? = null,
    val bedtimeMin: Int? = null,
)

/**
 * The path from stored HoT data (A42 §3): goals and taps from `path_*`, sleep hours (SLP) and
 * workout days from the WHOOP sync, today's mode from the day plan (A09). Pure rules live in
 * `:domain:path`; nothing here talks to the network.
 */
class PathRepository @Inject constructor(
    private val dao: PathDao,
    private val plans: DayPlanRepository,
    private val clock: Clock,
) {
    private val user = HotDatabase.LOCAL_USER

    @OptIn(ExperimentalCoroutinesApi::class)
    val view: Flow<PathView> = flowOf(Unit).flatMapLatest {
        val today = LocalDate.now(clock)
        val from = today.minusDays(HISTORY_DAYS).toString()
        combine(
            dao.observeGoals(user),
            dao.observeSteps(user),
            dao.observeMetric(user, Metrics.SLP.id, Metrics.SLP.version, from),
            dao.observeTrainingDays(user, from),
            plans.latest,
        ) { goals, steps, sleep, training, plan ->
            val ev = PathEvidence(
                today = today,
                sleepHours = sleep.mapNotNull { row -> row.value?.let { v -> day(row.dayKey)?.let { it to v } } }.toMap(),
                trainingDays = training.mapNotNull(::day).toSet(),
            )
            val todayPlan = plan?.takeIf { it.dayKey == today }
            val regime = todayPlan?.regime?.toPath()
            val marks = steps.groupBy { it.goalId }.mapValues { (_, rows) -> rows.mapNotNull { day(it.dayKey) }.toSet() }
            PathView(
                state = PathEngine.state(today, goals.mapNotNull(::toRecord), marks, ev, regime),
                regime = regime,
                bedtimeMin = todayPlan?.bedtimeMin,
            )
        }
    }

    /** «Беру» (И8.3): the goal starts today; an active goal becomes history (A42 §3.2 п. 4). */
    suspend fun take(plan: GoalPlan) {
        val today = LocalDate.now(clock)
        dao.replaceActive(
            PathGoalEntity(
                userId = user,
                sphere = plan.sphere.id,
                startDay = today.toString(),
                baseline = plan.baseline,
                stage1 = plan.stages[0],
                stage2 = plan.stages[1],
                stage3 = plan.stages[2],
                status = PathDao.STATUS_ACTIVE,
                createdAt = clock.millis(),
                endedDay = null,
            ),
            endedDay = today.minusDays(1).toString(),
        )
    }

    /** A tap on the step of the day: done ⇄ open. Any version counts, the minimal one too. */
    suspend fun toggleStep(version: StepVersion) {
        val goal = dao.active(user) ?: return
        val today = LocalDate.now(clock).toString()
        if (dao.step(user, goal.id, today) != null) {
            dao.deleteStep(user, goal.id, today)
        } else {
            dao.upsertStep(PathStepEntity(user, goal.id, today, version.name, clock.millis()))
        }
    }

    private fun toRecord(e: PathGoalEntity): GoalRecord? {
        val sphere = Sphere.fromId(e.sphere) ?: return null
        val start = day(e.startDay) ?: return null
        return GoalRecord(
            id = e.id,
            plan = GoalPlan(sphere, e.baseline, listOf(e.stage1, e.stage2, e.stage3)),
            startDay = start,
            status = runCatching { GoalStatus.valueOf(e.status) }.getOrDefault(GoalStatus.REPLACED),
            endedDay = e.endedDay?.let(::day),
        )
    }

    private fun day(key: String): LocalDate? = runCatching { LocalDate.parse(key) }.getOrNull()

    private companion object {
        /** 12 weeks of a cycle plus the 28-day window of «now», with room. */
        const val HISTORY_DAYS = 120L
    }
}

fun Regime.toPath(): PathRegime = when (this) {
    Regime.BOOST -> PathRegime.BOOST
    Regime.STEADY -> PathRegime.STEADY
    Regime.GENTLE -> PathRegime.GENTLE
}
