package com.healthofatitan.app.domain.today.week

import com.healthofatitan.app.domain.today.CalendarWeek
import com.healthofatitan.app.domain.today.DayTask
import com.healthofatitan.app.domain.today.GoalMeasure
import com.healthofatitan.app.domain.today.GoalSource
import com.healthofatitan.app.domain.today.Sphere
import com.healthofatitan.app.domain.today.TaskKind
import com.healthofatitan.app.domain.today.TodayCopy
import com.healthofatitan.app.domain.today.WeekGoal
import com.healthofatitan.app.domain.today.WeekPlan
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.max
import kotlin.math.min

/** Where the goals of a week stand (A52 §2.2, §2.6). */
enum class PlanStatus {
    /** HoT proposed them; the days already take tasks from them; waiting for the Sunday ritual. */
    PROPOSED,

    /** The person approved or edited them in «Итог и сборка». */
    APPROVED,

    /** No approval 48 h after Sunday began: HoT assembled the week itself (W-06). */
    AUTO,
}

/** One row of «Цели недели»: the goal · the dots · «N из M» (A52 §2.2 «Вид»). */
data class GoalLine(
    val goal: WeekGoal,
    /** One dot per unit for small counts (≤ 7), null for minutes. */
    val dots: List<Boolean>?,
    val text: String,
    val done: Boolean,
)

/** How the last week went, for the adaptation of the next one (A52 §2.2 «Адаптация»). */
data class LastWeek(
    /** Mean share of each goal reached (0…1); null when there was no plan. */
    val completion: Double?,
    val gentleDays: Int,
    /** Заряд within the personal norm most of the week. */
    val chargeInNorm: Boolean,
)

/** What the ritual offers for the next week. */
data class Proposal(
    val plan: WeekPlan,
    /** The goal ids for which «+1 шаг» is offered (the person decides, nothing is applied). */
    val suggestUp: Set<String>,
    val note: String?,
)

object WeekGoalRules {
    const val ID_TRAINING = "body-training"
    const val ID_FOCUS = "work-focus"
    const val ID_ZONES = "body-zones"
    const val ID_SLEEP = "path-sleep"
    const val REMINDER_PREFIX = "reminder-"
    const val PATTERN_PREFIX = "pattern-"

    /** Task ids of week goals that are not plan items: «wg:<goalId>». */
    const val TASK_PREFIX = "wg:"

    const val MINUTES_STEP = 30
    const val LIGHTER_BELOW = 0.5
    const val UP_FROM = 0.9

    /** The working targets of a normal week (A31 §1 example: 3 силовые, 4 фокус-блока, 150 минут Z2). */
    fun defaults(monday: LocalDate, first: Boolean): WeekPlan {
        val f = if (first) 1 else 0
        return WeekPlan(
            monday,
            listOf(
                WeekGoal(ID_TRAINING, Sphere.BODY, TaskKind.TRAINING, WeekCopy.GOAL_TRAINING, 3 - f, WeekCopy.UNIT_TIMES, 0, GoalSource.PATH),
                WeekGoal(ID_FOCUS, Sphere.WORK, TaskKind.FOCUS, WeekCopy.GOAL_FOCUS, 4 - f, WeekCopy.UNIT_TIMES, 0, GoalSource.WORK),
                WeekGoal(
                    ID_ZONES, Sphere.BODY, TaskKind.TRAINING, WeekCopy.GOAL_ZONES, if (first) 90 else 150, WeekCopy.UNIT_MINUTES, 0,
                    GoalSource.PATH, GoalMeasure.MINUTES,
                ),
                WeekGoal(ID_SLEEP, Sphere.SLEEP, TaskKind.REMINDER, WeekCopy.GOAL_SLEEP, 5 - f, WeekCopy.UNIT_NIGHTS, 0, GoalSource.PATH, GoalMeasure.NIGHTS),
            ),
        )
    }

    fun line(goal: WeekGoal): GoalLine {
        val shown = min(goal.progress, goal.target)
        val dots = if (goal.measure != GoalMeasure.MINUTES && goal.target in 1..7) List(goal.target) { it < shown } else null
        val text = if (goal.measure == GoalMeasure.MINUTES) WeekCopy.minutes(goal.progress, goal.target) else WeekCopy.ofTotal(shown, goal.target)
        return GoalLine(goal, dots, text, done = goal.progress >= goal.target)
    }

    /** «Цели недели 7 из 11»: the counted goals (minutes are a line of their own). */
    fun totals(goals: List<WeekGoal>): Pair<Int, Int> {
        val counted = goals.filter { it.measure != GoalMeasure.MINUTES }
        return counted.sumOf { min(it.progress, it.target) } to counted.sumOf { it.target }
    }

    /** Mean share of each goal reached, 0…1 (for the adaptation of the next week). */
    fun completion(goals: List<WeekGoal>): Double? =
        goals.filter { it.target > 0 }.takeIf { it.isNotEmpty() }?.map { min(1.0, it.progress.toDouble() / it.target) }?.average()

    private fun step(g: WeekGoal): Int = if (g.measure == GoalMeasure.MINUTES) MINUTES_STEP else 1

    fun lighter(g: WeekGoal): WeekGoal = g.copy(target = max(step(g), g.target - step(g)))

    fun heavier(g: WeekGoal): WeekGoal = g.copy(target = if (g.measure == GoalMeasure.MINUTES) g.target + MINUTES_STEP else min(7, g.target + 1))

    /**
     * The goals of the week of [monday] (A52 §2.2): the targets of the previous plan (the person's
     * edits stay), the defaults for a first week; one step lighter after a week below 50 % or with
     * ≥ 3 days of Бережно; «+1 шаг» only offered after ≥ 90 % with Заряд in the norm. [extras] are
     * small goals the person took in the ritual (a pattern to try, what drags on). Progress is zero.
     */
    fun propose(monday: LocalDate, previous: WeekPlan?, last: LastWeek?, kind: WeekKind, extras: List<WeekGoal> = emptyList()): Proposal {
        require(monday.dayOfWeek == DayOfWeek.MONDAY)
        val first = kind == WeekKind.FIRST || previous == null || previous.goals.isEmpty()
        val base = if (first) defaults(monday, first = true).goals else previous!!.goals.filter { !it.id.startsWith(PATTERN_PREFIX) }
        val lighter = !first && last != null && ((last.completion ?: 1.0) < LIGHTER_BELOW || last.gentleDays >= 3)
        val easierReturn = kind == WeekKind.RETURN
        val goals = (base + extras.filter { e -> base.none { it.id == e.id } })
            .map { it.copy(progress = 0) }
            .map { if ((lighter || easierReturn) && !it.id.startsWith(REMINDER_PREFIX)) lighter(it) else it }
        val up = if (!first && !lighter && last != null && (last.completion ?: 0.0) >= UP_FROM && last.chargeInNorm) {
            goals.filter { !it.id.startsWith(REMINDER_PREFIX) && !it.id.startsWith(PATTERN_PREFIX) }.map { it.id }.toSet()
        } else {
            emptySet()
        }
        val note = when {
            first -> WeekCopy.GOAL_FIRST_WEEK
            lighter || easierReturn -> WeekCopy.GOAL_SUGGEST_DOWN
            up.isNotEmpty() -> WeekCopy.GOAL_SUGGEST_UP
            else -> null
        }
        return Proposal(WeekPlan(monday, goals), up, note)
    }

    /** The Sunday that closes the week of [monday]: the ritual opens at its 00:00. */
    fun sunday(monday: LocalDate): LocalDate = CalendarWeek.start(monday).plusDays(6)

    /**
     * W-06: the deadline of the approval of the week that starts on [nextMonday]: 48 h after the
     * Sunday before it began (the ritual stays open on Sunday and Monday, «до вторника»).
     */
    fun deadline(nextMonday: LocalDate): LocalDateTime = nextMonday.minusDays(1).atStartOfDay().plusHours(48)

    /** W-06: a proposal not approved by the deadline becomes the week HoT assembled itself. */
    fun status(stored: PlanStatus, nextMonday: LocalDate, now: LocalDateTime): PlanStatus =
        if (stored == PlanStatus.PROPOSED && !now.isBefore(deadline(nextMonday))) PlanStatus.AUTO else stored

    /**
     * The Monday of the week the ritual assembles at [now]: on Sunday the next one; on Monday and
     * until the deadline the current one (the ritual of the past week is still open); else null.
     */
    fun ritualFor(now: LocalDateTime): LocalDate? {
        val today = now.toLocalDate()
        return when {
            today.dayOfWeek == DayOfWeek.SUNDAY -> today.plusDays(1)
            now.isBefore(deadline(CalendarWeek.start(today))) -> CalendarWeek.start(today)
            else -> null
        }
    }

    fun taskId(goalId: String): String = TASK_PREFIX + goalId

    fun goalOfTask(taskId: String): String? = taskId.takeIf { it.startsWith(TASK_PREFIX) }?.removePrefix(TASK_PREFIX)

    /** Goals that become tasks of a day: workouts, focus blocks and reminders counted in times or days. */
    fun yieldsTasks(g: WeekGoal): Boolean =
        g.measure == GoalMeasure.TIMES || g.measure == GoalMeasure.DAYS

    /**
     * Candidates of [day] from the week (A51 §3.1, A52 §2.2 «Как дни берут дела»): goals still open,
     * the most needed first — what is left over the days left until Sunday — so the week stays even;
     * a goal done today stays in the list as done. «Не сегодня» (W-05) only takes the task off
     * today: its goal keeps what is left, and the next days offer it again.
     */
    fun candidates(plan: WeekPlan, day: LocalDate, doneToday: Set<String>, paused: Boolean = false): List<DayTask> {
        if (paused || !CalendarWeek.contains(plan.weekStart, day)) return emptyList()
        val daysLeft = (6 - (day.toEpochDay() - plan.weekStart.toEpochDay())).toInt() + 1
        return plan.goals.asSequence()
            .filter(::yieldsTasks)
            .filter { it.open || it.id in doneToday }
            .sortedWith(
                compareBy<WeekGoal> { it.id in doneToday }
                    .thenByDescending { (it.target - it.progress).toDouble() / daysLeft }
                    .thenBy { plan.goals.indexOf(it) },
            )
            .map { g ->
                DayTask(
                    id = taskId(g.id),
                    kind = g.kind,
                    title = g.title,
                    why = WeekCopy.BLOCK_GOALS.lowercase(),
                    weekGoalId = g.id,
                    week = TodayCopy.week(min(g.progress, g.target), g.target),
                    hasMini = g.kind != TaskKind.REMINDER,
                    done = g.id in doneToday,
                )
            }
            .toList()
    }
}
