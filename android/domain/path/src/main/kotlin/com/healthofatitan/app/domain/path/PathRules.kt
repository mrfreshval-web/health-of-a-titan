package com.healthofatitan.app.domain.path

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Rules of the cycle (A42 §3.2–3.4). Simple and explainable on purpose, until the agents 3 and 8
 * check the «shortest lever» estimate (A42 §3.2 [Оценка]):
 *  - a goal is «N days of 7»; now = the share of measured days over the last 28, scaled to 7;
 *  - stages: now + 1 → now + 2 → hold now + 2, never above the sphere's cap: a step between
 *    stages is at most one day of 7 (A42 §3.2 п. 3), so milestone 1 is nearly certain;
 *  - the shortest lever is the measured sphere with room to grow; sleep before body, because sleep
 *    feeds Заряд directly (ECAP has SLP inside, A25 §4.2);
 *  - weeks are 7-day windows from the goal's first day; the week goal comes from its stage.
 */
object PathRules {
    const val CYCLE_WEEKS = 12
    const val STAGE_WEEKS = 4
    const val STAGES = 3

    /** Sleep goal unit: a night of at least 7 hours (A42 §3.2 table, A09 `sleep.debt7_h`). */
    const val SLEEP_HOURS = 7.0

    /** Days the «now» is read from (the personal norm window, A25). */
    const val WINDOW_DAYS = 28

    /** Fewer measured nights than this: no «now», the default plan is offered and marked so. */
    const val MIN_NIGHTS = 7

    /** «В свете» threshold of a sphere (A37 §8): 70 %, in «Бережно» 50 %. */
    const val LIGHT = 0.7f
    const val LIGHT_GENTLE = 0.5f

    /** Order of spheres when the lever is equal: sleep, then body (A42 §3.2). */
    private val LEVER_ORDER = listOf(Sphere.SLEEP, Sphere.BODY)

    /** Highest target per sphere: realistic for 12 weeks of process goals [Оценка, ⚑ Agent 3]. */
    fun cap(sphere: Sphere): Int = when (sphere) {
        Sphere.SLEEP -> 6
        Sphere.BODY -> 4
        else -> 5
    }

    /** Without a week of data (A42 §3.2 examples): sleep 4 → 5 → 5, body 2 → 3 → 3. */
    fun defaultStages(sphere: Sphere): List<Int> = when (sphere) {
        Sphere.SLEEP -> listOf(4, 5, 5)
        Sphere.BODY -> listOf(2, 3, 3)
        else -> listOf(3, 4, 5)
    }

    /** «Now» in days of 7 over the last [WINDOW_DAYS], or null with fewer than [MIN_NIGHTS] nights. */
    fun baseline(sphere: Sphere, ev: PathEvidence): Int? {
        if (!ev.measured(sphere)) return null
        val from = ev.today.minusDays(WINDOW_DAYS - 1L)
        val nights = ev.sleepHours.keys.filter { it >= from && it <= ev.today }
        if (nights.size < MIN_NIGHTS) return null
        val hits = when (sphere) {
            Sphere.SLEEP -> nights.count { ev.met(Sphere.SLEEP, it) }
            Sphere.BODY -> ev.trainingDays.count { it >= from && it <= ev.today }
            else -> 0
        }
        return min(7, floor(hits * 7.0 / nights.size + 1e-9).toInt())
    }

    fun stagesFrom(baseline: Int, cap: Int): List<Int> {
        val first = max(baseline, min(baseline + 1, cap))
        val second = max(baseline, min(baseline + 2, cap))
        return listOf(first, second, second)
    }

    fun headroom(plan: GoalPlan): Int = cap(plan.sphere) - (plan.baseline ?: 0)

    /** One proposal per sphere HoT can measure, the shortest lever first (A42 §3.2, И8.3). */
    fun proposals(ev: PathEvidence): List<GoalPlan> = LEVER_ORDER
        .map { sphere ->
            val now = baseline(sphere, ev)
            if (now == null) GoalPlan(sphere, null, defaultStages(sphere)) else GoalPlan(sphere, now, stagesFrom(now, cap(sphere)))
        }
        .sortedWith(compareBy<GoalPlan>({ !it.fromData }, { headroom(it) <= 0 }, { LEVER_ORDER.indexOf(it.sphere) }))

    /** Week 1… of a goal that started on [start]; day 0–6 is week 1. */
    fun weekOf(start: LocalDate, day: LocalDate): Int = (ChronoUnit.DAYS.between(start, day).coerceAtLeast(0) / 7 + 1).toInt()

    /** Stage 1–3 of week 1–12. */
    fun stageOf(week: Int): Int = (week.coerceIn(1, CYCLE_WEEKS) - 1) / STAGE_WEEKS + 1

    /** The week goal comes from the stage (A42 §3.3, И8.5). */
    fun weekTarget(plan: GoalPlan, week: Int): Int = plan.stages[stageOf(week) - 1]

    /** The step of the day by the mode: Разгон full, Ровно normal, Бережно the minimal version. */
    fun stepVersion(regime: PathRegime?): StepVersion = when (regime) {
        PathRegime.BOOST -> StepVersion.FULL
        PathRegime.GENTLE -> StepVersion.MINIMAL
        PathRegime.STEADY, null -> StepVersion.NORMAL
    }

    /**
     * The 12 weeks of [goal] as of [today]. [counted] are the days that count: the data met the
     * goal, or the user marked the step (in «Бережно» the minimal version counts the same).
     */
    fun progress(goal: GoalRecord, today: LocalDate, counted: Set<LocalDate>): GoalProgress {
        val end = goal.endedDay ?: today
        val rawWeek = weekOf(goal.startDay, end)
        val active = goal.status == GoalStatus.ACTIVE
        val weeks = (1..CYCLE_WEEKS).map { w ->
            val from = goal.startDay.plusDays((w - 1) * 7L)
            val to = minOf(from.plusDays(6), end)
            val target = weekTarget(goal.plan, w)
            val done = if (from > end) 0 else counted.count { it >= from && it <= to }
            val state = when {
                w > rawWeek -> WeekState.FUTURE
                done >= target -> WeekState.LIT
                w == rawWeek && active -> WeekState.CURRENT
                else -> WeekState.MISSED
            }
            WeekMark(w, state, done, target)
        }
        val week = rawWeek.coerceAtMost(CYCLE_WEEKS)
        return GoalProgress(
            goal = goal,
            week = week,
            stage = stageOf(week),
            weekTarget = weekTarget(goal.plan, week),
            weekDone = weeks[week - 1].done,
            weeks = weeks,
            finished = rawWeek > CYCLE_WEEKS,
        )
    }
}
