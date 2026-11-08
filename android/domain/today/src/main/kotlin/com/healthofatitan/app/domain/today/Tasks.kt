package com.healthofatitan.app.domain.today

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** The calendar week, Monday to Sunday (A52 §4, D-044). */
object CalendarWeek {
    fun start(day: LocalDate): LocalDate = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun end(day: LocalDate): LocalDate = start(day).plusDays(6)

    fun contains(weekStart: LocalDate, day: LocalDate): Boolean = !day.isBefore(weekStart) && !day.isAfter(weekStart.plusDays(6))
}

/** Spheres of the week goals (A52 §2.2). */
enum class Sphere { BODY, WORK, SLEEP, FOOD, MIND }

/**
 * Where a week goal comes from (A45 §17.2, A67 С-3). The names before A45 (`PATH`, `WORK`,
 * `PATTERN`) still read through [parse]: no Room migration is needed.
 */
enum class GoalSource {
    /** The stage of the active Path goal (the bridge Путь → Неделя, A67 §2.1 ①). */
    STAGE,

    /** HoT's starter goals while there is no active goal (П-1, W-10): never shown as the Path. */
    START,

    /** A pattern of the week or the experiment (A52 §3). */
    EXPERIMENT,

    /** Taken by the person: a reminder, a skew of the ritual, what drags on. */
    PERSON,
    ;

    companion object {
        /** Stored value → source: `PATH` → [STAGE], `PATTERN` → [EXPERIMENT], `WORK` → [START]; unknown → [START]. */
        fun parse(stored: String?): GoalSource = when (stored) {
            "PATH" -> STAGE
            "PATTERN" -> EXPERIMENT
            "WORK" -> START
            else -> entries.firstOrNull { it.name == stored } ?: START
        }
    }
}

/** What kind of thing a task of the day is (A46 §2.4); [TaskKind.playVerb] is what ▶ does. */
enum class TaskKind {
    /** A workout: ▶ opens the player. */
    TRAINING,

    /** A focus block: ▶ starts the timer. */
    FOCUS,

    /** A reminder: ▶ is its action. */
    REMINDER,

    /** Scenario tasks of A46 §4. */
    CONNECT_WHOOP,
    START_QUESTIONS,
    START_SMALL,
    HELP,
    DOCTOR,
    ASSEMBLE_WEEK,
}

/** How a week goal is counted (A52 §2.2): times, minutes (zones), nights (sleep), days (reminders). */
enum class GoalMeasure { TIMES, MINUTES, NIGHTS, DAYS }

/** One process goal of the week (A52 §2.2): «Силовые 2 из 3». */
data class WeekGoal(
    val id: String,
    val sphere: Sphere,
    val kind: TaskKind,
    val title: String,
    val target: Int,
    val unit: String,
    val progress: Int,
    val source: GoalSource,
    val measure: GoalMeasure = GoalMeasure.TIMES,
) {
    val open: Boolean get() = progress < target
}

/** The week of [weekStart] (a Monday) and its goals (A52 §4). */
data class WeekPlan(val weekStart: LocalDate, val goals: List<WeekGoal>) {
    init {
        require(weekStart.dayOfWeek == DayOfWeek.MONDAY) { "a week starts on Monday: $weekStart" }
    }

    fun goal(id: String?): WeekGoal? = goals.firstOrNull { it.id == id }

    companion object {
        fun empty(day: LocalDate): WeekPlan = WeekPlan(CalendarWeek.start(day), emptyList())
    }
}

/** One task of the day (A46 §2.4): what · why (the goal) · full / mini · the week's progress. */
data class DayTask(
    val id: String,
    val kind: TaskKind,
    val title: String,
    val why: String? = null,
    /** The week goal it moves (A52 §4); null for scenario tasks. */
    val weekGoalId: String? = null,
    /** «2 из 3» of its week goal. */
    val week: String? = null,
    /** A mini version exists («полная / мини»). */
    val hasMini: Boolean = false,
    /** Mini by default (Бережно, A46 §2.4). */
    val mini: Boolean = false,
    val done: Boolean = false,
    /** Where a tap leads (a route); null unfolds the card in place. */
    val route: String? = null,
    /** Days in a row it was moved (A46 §2.4 «Сдвинуто 3 раза»). */
    val moved: Int = 0,
    /**
     * The short name of the Path goal this step serves (A57 §4.3, A67 С-4); null when the step is
     * not from the stage of an active goal. A mark only: the mode of the day still decides full or
     * mini, and the safety bus still decides whether there is a step at all.
     */
    val goal: String? = null,
)

/** Where «Не сегодня» sends a task (T-08): back to its week goal, never to tomorrow. */
data class TaskMove(val taskId: String, val weekGoalId: String?, val day: LocalDate) {
    val toTomorrow: Boolean get() = false
}

object TaskRules {
    const val MAX = 3
    const val MAX_GENTLE = 2

    /** A46 §2.4: after three moves HoT offers the mini version or the week, without reproach. */
    const val MOVED_OFFER = 3

    fun limit(scenario: Scenario, mode: DayMode?): Int =
        if (scenario == Scenario.GENTLE || mode == DayMode.BEREZHNO) MAX_GENTLE else MAX

    /**
     * The 1–3 tasks of the day (T-02): scenario tasks first where A46 §4 asks for them; else the
     * candidates the week offers, open before done, without those sent back to the week today;
     * Бережно takes two and mini by default; nothing at all → «Собрать неделю».
     */
    fun pick(scenario: Scenario, mode: DayMode?, candidates: List<DayTask>, returnedToday: Set<String>): List<DayTask> {
        val fixed: List<DayTask>? = when (scenario) {
            Scenario.SAFETY -> listOf(
                DayTask("help", TaskKind.HELP, TodayCopy.TASK_HELP),
                DayTask("doctor", TaskKind.DOCTOR, TodayCopy.TASK_DOCTOR),
            )
            Scenario.NO_WHOOP -> listOf(DayTask("connect_whoop", TaskKind.CONNECT_WHOOP, TodayCopy.TASK_CONNECT_WHOOP))
            Scenario.RETURN -> listOf(DayTask("start_small", TaskKind.START_SMALL, TodayCopy.TASK_START_SMALL, why = TodayCopy.TASK_START_SMALL_WHY, mini = true))
            else -> null
        }
        if (fixed != null) return fixed
        val gentle = scenario == Scenario.GENTLE || mode == DayMode.BEREZHNO
        val pool = candidates
            .filter { it.id !in returnedToday }
            .distinctBy { it.title }
            .sortedBy { it.done }
            .map { if (gentle && it.hasMini) it.copy(mini = true) else it }
        val head = if (scenario == Scenario.NIGHT_PENDING) {
            listOf(DayTask("start_questions", TaskKind.START_QUESTIONS, TodayCopy.TASK_START_QUESTIONS))
        } else {
            emptyList()
        }
        val picked = (head + pool).take(limit(scenario, mode))
        return picked.ifEmpty {
            listOf(DayTask("assemble_week", TaskKind.ASSEMBLE_WEEK, TodayCopy.TASK_ASSEMBLE_WEEK, why = TodayCopy.TASK_ASSEMBLE_WEEK_WHY))
        }
    }

    /** T-02: only the first task has ▶, and only while it is open. */
    fun playable(tasks: List<DayTask>, index: Int): Boolean = index == 0 && tasks.isNotEmpty() && !tasks[0].done

    /** T-08: «Не сегодня» returns the task to its week goal. */
    fun notToday(task: DayTask, day: LocalDate): TaskMove = TaskMove(task.id, task.weekGoalId, day)

    /** Scenario tasks cannot be sent back to the week (they are not from it). */
    fun movable(task: DayTask): Boolean = task.weekGoalId != null

    fun offerMini(task: DayTask): Boolean = task.moved >= MOVED_OFFER && task.hasMini
}

/** The day as the week counts it (A52 §2.1, §4); counted at 04:00 the next day by the week. */
data class DayScore(val dayInSupport: Boolean, val supportsFilled: Int, val tasksDone: Int)

object DayScores {
    /**
     * A day in support: ≥ 1 task done (mini counts) and ≥ 3 of 5 supports at ≥ 70 % of their norm
     * (≥ 50 % in Бережно). A sick day counts as rest.
     */
    fun of(supportFills: List<Float>, tasksDone: Int, gentle: Boolean, sick: Boolean = false): DayScore {
        val bar = if (gentle) SupportNorms.FILLED_GENTLE else SupportNorms.FILLED
        val filled = supportFills.count { it >= bar.toFloat() }
        return DayScore(dayInSupport = sick || (tasksDone >= 1 && filled >= 3), supportsFilled = filled, tasksDone = tasksDone)
    }
}
