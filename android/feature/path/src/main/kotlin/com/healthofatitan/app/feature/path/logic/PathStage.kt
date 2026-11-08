package com.healthofatitan.app.feature.path.logic

import com.healthofatitan.app.domain.path.PathFlags
import com.healthofatitan.app.domain.path.PathLexicon
import com.healthofatitan.app.domain.path.PracticalGoal
import com.healthofatitan.app.domain.path.StageRules
import com.healthofatitan.app.domain.today.GoalMeasure
import com.healthofatitan.app.domain.today.GoalSource
import com.healthofatitan.app.domain.today.WeekGoal
import com.healthofatitan.app.domain.today.WeekPlan
import java.time.LocalDate
import kotlin.math.min
import kotlin.math.pow

/** Russian dates and counts for the screens of v3 (no Locale: the words are fixed, A66 §5.5). */
object RuWords {
    private val MONTHS_GEN = listOf("января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря")
    private val MONTHS = listOf("Январь", "Февраль", "Март", "Апрель", "Май", "Июнь", "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь")
    private val MONTHS_SHORT = listOf("янв", "фев", "мар", "апр", "май", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")
    private val WEEKDAYS = listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")

    fun weekday(d: LocalDate): String = WEEKDAYS[d.dayOfWeek.value - 1]

    /** «15 ноября». */
    fun dayMonth(d: LocalDate): String = "${d.dayOfMonth} ${MONTHS_GEN[d.monthValue - 1]}"

    /** «вс, 15 ноября». */
    fun dated(d: LocalDate): String = "${weekday(d)}, ${dayMonth(d)}"

    fun month(m: Int): String = MONTHS[m - 1]

    fun monthShort(m: Int): String = MONTHS_SHORT[m - 1]

    /** Words a search may use for [d]: the month in both cases, its short form, the weekday and the year. */
    fun dateWords(d: LocalDate): String =
        listOf(dayMonth(d), month(d.monthValue), monthShort(d.monthValue), weekday(d), d.year.toString(), d.toString()).joinToString(" ")

    /** «2–8 ноя» or «30 ноя – 6 дек». */
    fun range(from: LocalDate, to: LocalDate): String =
        if (from.month == to.month) "${from.dayOfMonth}–${to.dayOfMonth} ${monthShort(to.monthValue)}"
        else "${from.dayOfMonth} ${monthShort(from.monthValue)} – ${to.dayOfMonth} ${monthShort(to.monthValue)}"

    /** The Russian plural: [one] for 1, 21…; [few] for 2–4, 22…; [many] for the rest. */
    fun plural(n: Int, one: String, few: String, many: String): String {
        val a = kotlin.math.abs(n)
        val d = a % 10
        val dd = a % 100
        return when {
            d == 1 && dd != 11 -> one
            d in 2..4 && dd !in 12..14 -> few
            else -> many
        }
    }

    fun days(n: Int): String = "$n ${plural(n, "день", "дня", "дней")}"
}

/** The card «Следующая веха» (A66 §5.2): when, what is measured. Paused and held paths show no dates (В4). */
data class MilestoneView(
    /** «Веха 1 · через 10 дней», «Веха 2 · сегодня». */
    val title: String,
    /** «вс, 15 ноября». */
    val date: String,
    /** «Замер: ночи от 7 ч»; null — the goal has no measurable criterion yet. */
    val measure: String?,
    /** The day of the milestone: the meeting with the Titan of the horizon. */
    val meeting: Boolean,
)

/** The lever of the stage in one line (A66 §5.2): «Рычаг этапа — сон» · «2 из 5 ночей на этой неделе». */
data class LeverView(val title: String, val line: String?)

/** One of the 12 weeks of the stage (A66 §5.2 «Недели этапа»). */
enum class StageCell {
    /** Counted by the result of «Неделя». */
    COUNTED,

    /** The current week. */
    NOW,

    /** Not yet. */
    AHEAD,

    /** Behind, not counted: a neutral outlined cell — never red, never dimmed (§5.4, §6.5 п. 8). */
    PAST,

    /** The current week on pause: the calendar stands (hatched). */
    PAUSE,
}

data class StageWeekCell(val week: Int, val state: StageCell, val milestone: Boolean)

/**
 * @param counted «2 засчитаны»; null while none is counted (no shaming zero, A66 Агент 10 п. 6)
 * @param spoken one line for TalkBack: «Засчитаны недели 1, 2 и 4; неделя 3 — не в этот раз; идёт 5»
 */
data class StageWeeksView(val cells: List<StageWeekCell>, val counted: String?, val spoken: String)

/** A mark on the trail of the scene: [depth] 0 = the foreground, 1 = the horizon. */
data class TrailMark(val week: Int, val depth: Float, val scale: Float, val side: Int)

/**
 * The pure rules of «Путь» v3 (A66 §5, mockup 07): the milestone card, the lever line, the link to
 * the week goals, the 12 weeks of the stage and where the stelae and «ты» stand on the trail.
 */
object PathStage {
    /** How long the trail is: the stage of 12 weeks (D-057: a stage of 4 or 8 weeks has fewer stelae). */
    val WEEKS: Int get() = StageRules.WEEKS

    // ── The milestone ──────────────────────────────────────────────────────────────────────────

    /** The number of the milestone at [week] of the stage: 4 → 1, 8 → 2, 12 → 3. */
    fun milestoneNumber(week: Int): Int = StageRules.MILESTONES.indexOf(week) + 1

    fun milestone(goal: PracticalGoal, today: LocalDate, next: Pair<Int, Long>?): MilestoneView? {
        val (week, days) = next ?: return null
        val date = StageRules.milestone(goal, week, today)
        val n = milestoneNumber(week)
        val title = if (days <= 0L) PathV3Copy.milestoneToday(n) else PathV3Copy.milestoneIn(n, days.toInt())
        val measure = goal.criteria.firstOrNull()?.label?.trim()?.takeIf { it.isNotEmpty() }?.let(PathV3Copy::measure)
        return MilestoneView(title, RuWords.dated(date), measure, meeting = days <= 0L)
    }

    // ── The lever and the week ─────────────────────────────────────────────────────────────────

    /**
     * The lever of the goal and, when the week has its goal, how far it is this week. The numbers
     * are the week's own (A52), nothing is computed anew here.
     */
    fun lever(goal: PracticalGoal, plan: WeekPlan?, flags: PathFlags): LeverView? {
        val lever = goal.lever ?: return null
        val title = PathV3Copy.leverTitle(PathLexicon.name(lever.key))
        val id = StageWeek.goalId(lever.key, flags)
        val wg = plan?.goals?.firstOrNull { it.id == id }
        return LeverView(title, wg?.let(::leverLine))
    }

    fun leverLine(g: WeekGoal): String =
        if (g.measure == GoalMeasure.MINUTES) PathV3Copy.leverMinutes(g.progress, g.target)
        else PathV3Copy.leverCount(min(g.progress, g.target), g.target, g.unit)

    /** The goals of the week the link counts: the experiment of the week is not a goal of the stage (A53 §3.2). */
    fun weekGoals(plan: WeekPlan?): List<WeekGoal> = plan?.goals.orEmpty().filter { it.source != GoalSource.EXPERIMENT }

    /** «Цели недели · 2 из 3 ›»; without one goal done yet — no «0 из 3» (A66 Агент 10 п. 6). Null: no plan. */
    fun weekLink(plan: WeekPlan?): String? {
        val goals = weekGoals(plan).takeIf { it.isNotEmpty() } ?: return null
        val done = goals.count { !it.open }
        return if (done == 0) PathV3Copy.WEEK_LINK else PathV3Copy.weekLink(done, goals.size)
    }

    /**
     * A week is counted by the result of «Неделя»: at least two of three goals (A66 §5.2 [Оценка],
     * open question 2 §10 to Agent 1). For another number of goals the same share: ⌈2/3⌉ of them.
     */
    fun counted(goals: List<WeekGoal>): Boolean {
        val g = goals.filter { it.source != GoalSource.EXPERIMENT }
        if (g.isEmpty()) return false
        val done = g.count { !it.open }
        return done >= 1 && done * 3 >= g.size * 2
    }

    /** The calendar day by which week [week] of the stage is judged: its last day (pauses aside). */
    fun stageWeekEnd(goal: PracticalGoal, week: Int): LocalDate = goal.start.plusDays(week * 7L - 1)

    // ── The weeks of the stage ─────────────────────────────────────────────────────────────────

    fun weeks(current: Int, counted: Set<Int>, paused: Boolean, total: Int = WEEKS, milestones: List<Int> = StageRules.MILESTONES): StageWeeksView {
        val cells = (1..total).map { w ->
            val state = when {
                w == current && paused -> StageCell.PAUSE
                w == current -> StageCell.NOW
                w < current && w in counted -> StageCell.COUNTED
                w < current -> StageCell.PAST
                else -> StageCell.AHEAD
            }
            StageWeekCell(w, state, w in milestones)
        }
        val n = cells.count { it.state == StageCell.COUNTED }
        return StageWeeksView(cells, if (n == 0) null else PathV3Copy.counted(n), spoken(cells))
    }

    private fun list(ws: List<Int>): String = when (ws.size) {
        0 -> ""
        1 -> ws[0].toString()
        else -> ws.dropLast(1).joinToString(", ") + " и " + ws.last()
    }

    private fun spoken(cells: List<StageWeekCell>): String {
        val counted = cells.filter { it.state == StageCell.COUNTED }.map { it.week }
        val past = cells.filter { it.state == StageCell.PAST }.map { it.week }
        val now = cells.firstOrNull { it.state == StageCell.NOW || it.state == StageCell.PAUSE }
        return buildList {
            if (counted.isNotEmpty()) add(PathV3Copy.A11Y_COUNTED + " " + list(counted))
            if (past.isNotEmpty()) add((if (past.size == 1) PathV3Copy.A11Y_WEEK else PathV3Copy.A11Y_WEEKS) + " " + list(past) + " — " + PathV3Copy.NOT_THIS_TIME)
            now?.let { add(if (it.state == StageCell.PAUSE) PathV3Copy.a11yPause(it.week) else PathV3Copy.a11yNow(it.week)) }
        }.joinToString("; ").ifEmpty { PathV3Copy.STAGE_WEEKS }
    }

    // ── The trail of the scene ─────────────────────────────────────────────────────────────────

    /**
     * The perspective of the trail: the share of the ground below the horizon at [week] of [total]
     * (1 — at the bottom edge, 0 — on the horizon). Mockup 07: week 4 ≈ 0.7, week 8 ≈ 0.4, 12 = 0.
     */
    fun ground(week: Float, total: Int = WEEKS): Float {
        val left = (1f - week / total).coerceIn(0f, 1f)
        return left.toDouble().pow(GROUND_POWER).toFloat()
    }

    /** The stelae of the milestones: right, left, right — smaller towards the horizon. */
    fun stelae(total: Int = WEEKS, milestones: List<Int> = StageRules.MILESTONES): List<TrailMark> =
        milestones.filter { it <= total }.mapIndexed { i, w ->
            val g = ground(w.toFloat(), total)
            TrailMark(w, 1f - g, scale(g), if (i % 2 == 0) 1 else -1)
        }

    /** «ты · нед N» stands in the middle of the current week, left of the trail; null without a week. */
    fun you(week: Int, total: Int = WEEKS): TrailMark? {
        if (week < 1) return null
        val g = ground(week - 0.5f, total)
        return TrailMark(week, 1f - g, scale(g), -1)
    }

    private fun scale(ground: Float): Float = MIN_SCALE + (1f - MIN_SCALE) * ground

    private const val GROUND_POWER = 0.85
    private const val MIN_SCALE = 0.35f
}

/** The draft texts of «Путь» v3 and «За плечами» (A66 §5.5, §6.8): on review of Agents 16 and 10. */
object PathV3Copy {
    const val STAGE_WEEKS = "Недели этапа"
    const val PARTS = "Из чего состоит цель"
    const val WEEK_LINK = "Цели недели ›"
    const val YOU = "ты"
    const val HORIZON = "Титан горизонта"
    const val NOT_THIS_TIME = "не в этот раз"
    const val A11Y_COUNTED = "Засчитаны недели"
    const val A11Y_WEEK = "неделя"
    const val A11Y_WEEKS = "недели"
    const val MEETING = "Встреча с Титаном горизонта · 5 мин"
    const val STAGE_BEHIND = "Вехи этапа позади. Впереди — Встреча."
    const val NO_GOAL_WEEK = "Неделя соберётся из цели."
    const val PAUSED_LINE = "Путь подождёт. Пауза ничего не отнимает."

    fun milestoneIn(n: Int, days: Int) = "Веха $n · через ${RuWords.days(days)}"

    fun milestoneToday(n: Int) = "Веха $n · сегодня"

    fun measure(label: String) = "Замер: $label"

    fun leverTitle(name: String) = "Рычаг этапа — ${name.lowercase()}"

    fun leverCount(done: Int, target: Int, unit: String) = "$done из $target $unit на этой неделе".replace("  ", " ")

    fun leverMinutes(done: Int, target: Int) = "$done из $target мин на этой неделе"

    fun weekLink(done: Int, total: Int) = "Цели недели · $done из $total ›"

    fun counted(n: Int) = "$n ${RuWords.plural(n, "засчитана", "засчитаны", "засчитано")}"

    fun youLabel(week: Int) = "$YOU · нед $week"

    fun a11yNow(week: Int) = "идёт $week"

    fun a11yPause(week: Int) = "неделя $week на паузе"

    /** The scene as one node (A66 §5.7): «Цель: пробежать 10 км. Неделя 3 из 12. До вехи 1 — 10 дней». */
    fun scene(goal: String?, week: Int, total: Int, next: Pair<Int, Long>?): String = when (goal) {
        null -> "Путь ждёт цели. На горизонте мягкий свет."
        else -> buildList {
            add("Цель: $goal")
            if (week > 0) add("Неделя $week из $total")
            next?.let { (w, d) -> add(if (d <= 0L) "Веха ${PathStage.milestoneNumber(w)} сегодня" else "До вехи ${PathStage.milestoneNumber(w)} — ${RuWords.days(d.toInt())}") }
        }.joinToString(". ")
    }

    fun all(): List<String> = listOf(
        STAGE_WEEKS, PARTS, WEEK_LINK, YOU, HORIZON, NOT_THIS_TIME, A11Y_COUNTED, MEETING, STAGE_BEHIND, NO_GOAL_WEEK, PAUSED_LINE,
        milestoneIn(1, 10), milestoneToday(2), measure("ночи от 7 ч"), leverTitle("Сон"), leverCount(2, 5, "ночей"), leverMinutes(40, 150),
        weekLink(2, 3), counted(1), counted(2), counted(5), youLabel(3), a11yNow(5), a11yPause(5),
        scene("Подтянуться 10 раз", 3, 12, 4 to 10L), scene(null, 0, 12, null),
    ) + BehindCopy.all()
}
