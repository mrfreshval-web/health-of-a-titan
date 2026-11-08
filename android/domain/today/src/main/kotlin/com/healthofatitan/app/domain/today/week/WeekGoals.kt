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

/**
 * What the Path gives the weeks (the bridge Путь → Неделя, A67 §2.1 ①, С-3). Only an
 * active goal gives stage goals; a goal on pause or held by the safety bus gives
 * [Paused]; any other state is [NoGoal] (A67 §4: С1 knows no «Держим» yet).
 */
sealed interface PathWeek {
    /** No active goal: the week runs on HoT's starter goals ([GoalSource.START], П-1). */
    data object NoGoal : PathWeek

    /** The goal is on pause or held: the calendar stands; the week keeps the starter goals a step lighter. */
    data object Paused : PathWeek

    /**
     * An active goal: [name] is its short name for the mark on the steps of the day; [goalIds] are
     * the week goals of its lever and leading components ([WeekGoalRules] ids, lever first);
     * [takenOn] is the day of «Беру путь» (the pour into the current week, A56 §2); [pausedDays] are
     * the closed pauses of the stage, so «Неделя» can say «Путь · неделя N из 12» (A66 §4.2).
     */
    data class Active(val name: String, val goalIds: List<String>, val takenOn: LocalDate, val pausedDays: Int = 0) : PathWeek
}

object WeekGoalRules {
    const val ID_TRAINING = "body-training"
    const val ID_FOCUS = "work-focus"
    const val ID_ZONES = "body-zones"
    const val ID_SLEEP = "path-sleep"
    const val REMINDER_PREFIX = "reminder-"
    const val PATTERN_PREFIX = "pattern-"

    /** Task ids of week goals that are not plan items: «wg:<goalId>». */
    const val TASK_PREFIX = "wg:"

    /** A67 Расх.-4, П-1: a week with an active goal shows 2–3 goals, at least one of the stage. */
    const val STAGE_MIN = 2
    const val STAGE_MAX = 3

    const val MINUTES_STEP = 30
    const val LIGHTER_BELOW = 0.5
    const val UP_FROM = 0.9

    /**
     * The working targets of a normal week (A31 §1 example: 3 силовые, 4 фокус-блока, 150 минут Z2).
     * They are HoT's starter goals ([GoalSource.START]): without an active goal they are not the
     * Path and are never marked as such (A67 С-3).
     */
    fun defaults(monday: LocalDate, first: Boolean): WeekPlan {
        val f = if (first) 1 else 0
        return WeekPlan(
            monday,
            listOf(
                WeekGoal(ID_TRAINING, Sphere.BODY, TaskKind.TRAINING, WeekCopy.GOAL_TRAINING, 3 - f, WeekCopy.UNIT_TIMES, 0, GoalSource.START),
                WeekGoal(ID_FOCUS, Sphere.WORK, TaskKind.FOCUS, WeekCopy.GOAL_FOCUS, 4 - f, WeekCopy.UNIT_TIMES, 0, GoalSource.START),
                WeekGoal(
                    ID_ZONES, Sphere.BODY, TaskKind.TRAINING, WeekCopy.GOAL_ZONES, if (first) 90 else 150, WeekCopy.UNIT_MINUTES, 0,
                    GoalSource.START, GoalMeasure.MINUTES,
                ),
                WeekGoal(ID_SLEEP, Sphere.SLEEP, TaskKind.REMINDER, WeekCopy.GOAL_SLEEP, 5 - f, WeekCopy.UNIT_NIGHTS, 0, GoalSource.START, GoalMeasure.NIGHTS),
            ),
        )
    }

    /**
     * The source of a stored goal: the names before A45 §17.2 read through [GoalSource.parse]; the
     * goals the person added (reminders, a skew, what drags on) were stored as `PATH` or `WORK` and
     * are the person's ([GoalSource.PERSON]).
     */
    fun sourceOf(goalId: String, stored: String?): GoalSource = when {
        goalId.startsWith(REMINDER_PREFIX) || goalId.startsWith(RitualRules.SKEW_ID_PREFIX) || goalId == RitualRules.CARRY_ID -> GoalSource.PERSON
        goalId.startsWith(PATTERN_PREFIX) -> GoalSource.EXPERIMENT
        else -> GoalSource.parse(stored)
    }

    /**
     * The goals of the stage for the week of [monday] (A67 С-3): the starter goal of each id the
     * Path gives, as [GoalSource.STAGE], at most [STAGE_MAX]. A goal taken on Thursday or later
     * pours into its own week as the first step only (A56 §2 «Что меняется после Э7»).
     */
    fun stageGoals(monday: LocalDate, first: Boolean, path: PathWeek.Active): List<WeekGoal> {
        val all = defaults(monday, first).goals
        val firstStep = CalendarWeek.contains(monday, path.takenOn) && path.takenOn.dayOfWeek >= DayOfWeek.THURSDAY
        return path.goalIds.distinct()
            .mapNotNull { id -> all.firstOrNull { it.id == id } }
            .take(STAGE_MAX)
            .map { it.copy(source = GoalSource.STAGE) }
            .map { if (firstStep) it.copy(target = step(it)) else it }
    }

    /** [stage] and, while it has fewer than [STAGE_MIN], the first of [others] the stage does not cover. */
    private fun withStage(stage: List<WeekGoal>, others: List<WeekGoal>): List<WeekGoal> {
        val ids = stage.map { it.id }.toSet()
        return stage + others.filter { it.id !in ids }.take(max(0, STAGE_MIN - stage.size))
    }

    /**
     * «Беру путь» in the middle of a week (A56 §2, A67 С-3): the goals of the stage pour into the
     * current [plan] once — when the goal was taken this week and the plan has no stage goal yet.
     * The person's goals and the experiment stay; the starter goals make room. Null: nothing to pour
     * (a change of the Path in the middle of the week waits for Monday, P-15).
     */
    fun pour(plan: WeekPlan, path: PathWeek): WeekPlan? {
        if (path !is PathWeek.Active || !CalendarWeek.contains(plan.weekStart, path.takenOn)) return null
        if (plan.goals.any { it.source == GoalSource.STAGE }) return null
        val stage = stageGoals(plan.weekStart, first = false, path = path).map { s ->
            // the person's own target of the same goal stays (A52 §2.2 «Тап»), within the first step
            plan.goal(s.id)?.let { s.copy(target = min(s.target, it.target)) } ?: s
        }
        if (stage.isEmpty()) return null
        val starter = plan.goals.filter { it.source == GoalSource.START }
        val rest = plan.goals.filter { it.source == GoalSource.PERSON || it.source == GoalSource.EXPERIMENT }
        return WeekPlan(plan.weekStart, withStage(stage, starter) + rest.filter { r -> stage.none { it.id == r.id } })
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
    fun propose(
        monday: LocalDate,
        previous: WeekPlan?,
        last: LastWeek?,
        kind: WeekKind,
        extras: List<WeekGoal> = emptyList(),
        path: PathWeek = PathWeek.NoGoal,
    ): Proposal {
        require(monday.dayOfWeek == DayOfWeek.MONDAY)
        val first = kind == WeekKind.FIRST || previous == null || previous.goals.isEmpty()
        val kept = if (first) emptyList() else previous!!.goals.filter { !it.id.startsWith(PATTERN_PREFIX) }
        val person = kept.filter { it.source == GoalSource.PERSON }
        val stage = if (path is PathWeek.Active) {
            // A67 С-3: the stage gives the week; the person's targets of the same goals stay
            stageGoals(monday, first, path).map { s -> kept.firstOrNull { it.id == s.id && it.source == GoalSource.STAGE }?.let { s.copy(target = it.target) } ?: s }
        } else {
            emptyList()
        }
        val base = if (stage.isNotEmpty()) {
            withStage(stage, defaults(monday, first).goals) + person.filter { p -> stage.none { it.id == p.id } }
        } else if (first) {
            defaults(monday, first = true).goals
        } else {
            // without an active goal nothing is the Path: stage goals of a past goal go back to the starter
            kept.map { if (it.source == GoalSource.STAGE) it.copy(source = GoalSource.START) else it }
                .ifEmpty { defaults(monday, first = false).goals }
        }
        val lighter = !first && last != null && ((last.completion ?: 1.0) < LIGHTER_BELOW || last.gentleDays >= 3)
        val easierReturn = kind == WeekKind.RETURN || path == PathWeek.Paused
        val goals = (base + extras.filter { e -> base.none { it.id == e.id } })
            .map { it.copy(progress = 0) }
            .map { if ((lighter || easierReturn) && !it.id.startsWith(REMINDER_PREFIX)) lighter(it) else it }
        val up = if (!first && !lighter && last != null && (last.completion ?: 0.0) >= UP_FROM && last.chargeInNorm) {
            goals.filter { !it.id.startsWith(REMINDER_PREFIX) && !it.id.startsWith(PATTERN_PREFIX) }.map { it.id }.toSet()
        } else {
            emptySet()
        }
        val note = when {
            path == PathWeek.Paused -> WeekCopy.GOAL_PATH_PAUSED
            stage.isNotEmpty() -> WeekCopy.GOAL_FROM_PATH
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

    /**
     * The mark of the Path goal on a step from [g] (A67 С-4): only a stage goal of an active goal
     * carries it; a starter goal never does.
     */
    fun goalMark(g: WeekGoal?, goal: String?): String? = goal?.takeIf { g?.source == GoalSource.STAGE }

    /** Goals that become tasks of a day: workouts, focus blocks and reminders counted in times or days. */
    fun yieldsTasks(g: WeekGoal): Boolean =
        g.measure == GoalMeasure.TIMES || g.measure == GoalMeasure.DAYS

    /**
     * Candidates of [day] from the week (A51 §3.1, A52 §2.2 «Как дни берут дела»): goals still open,
     * the most needed first — what is left over the days left until Sunday — so the week stays even;
     * a goal done today stays in the list as done. «Не сегодня» (W-05) only takes the task off
     * today: its goal keeps what is left, and the next days offer it again.
     */
    fun candidates(plan: WeekPlan, day: LocalDate, doneToday: Set<String>, paused: Boolean = false, goal: String? = null): List<DayTask> {
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
                    goal = goalMark(g, goal),
                )
            }
            .toList()
    }
}
