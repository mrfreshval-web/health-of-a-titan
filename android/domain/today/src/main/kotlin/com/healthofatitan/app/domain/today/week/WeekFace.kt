package com.healthofatitan.app.domain.today.week

import com.healthofatitan.app.domain.today.GoalMeasure
import com.healthofatitan.app.domain.today.GoalSource
import com.healthofatitan.app.domain.today.WeekGoal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** One dot of a day on a goal card (A66 §1.1): ● done · ○ planned · «·» not needed · ◌ no data. */
enum class DayDot { DONE, PLANNED, NOT_NEEDED, NO_DATA }

/**
 * The days of this week a goal was done. [known] are the days a sensor goal (sleep, minutes in
 * zones) has data for; null for a goal counted from the person's marks (every past day is known).
 */
data class GoalDays(val done: Set<LocalDate> = emptySet(), val known: Set<LocalDate>? = null)

/** The week of the stage of the active goal: «Путь · неделя 3 из 12», the next milestone 1…3. */
data class PathStage(val week: Int, val weeks: Int = WeekFace.STAGE_WEEKS, val nextMilestone: Int)

/** What overrides the rule «no Path — no week» (A66 §0.3 «Исключения — забота важнее структуры»). */
data class WeekCare(val sick: Boolean = false, val crisis: Boolean = false, val storm: Boolean = false) {
    val any: Boolean get() = sick || crisis || storm
}

/** «Ночи за неделю: средняя 6:44 · 5 из 7» (A66 §0.3, mockup 09). */
data class NightsSeen(val meanMinutes: Int, val nights: Int, val of: Int = 7)

/** «За плечами: 38 тренировок за 90 дней». */
data class BehindSeen(val workouts: Int, val days: Int)

/** A card of a goal of the week (A66 §4.3, 116 dp). */
data class GoalCard(
    val goal: WeekGoal,
    /** «2 из 3»; «90 из 150 мин» for minutes. */
    val count: String,
    /** «из Пути · Сила», «твоя · к вехе 1», «эксперимент», «стартовая неделя». */
    val chip: String,
    /** Monday…Sunday. */
    val dots: List<DayDot>,
    val todayIndex: Int,
    /** «ещё 1 — удобнее в пт или сб» or «Сделано. Дальше — по желанию». */
    val left: String,
    val done: Boolean,
    val holds: Boolean,
    /** The card as one TalkBack node (A66 §4.9). */
    val spoken: String,
)

/**
 * «Цели недели · 2 из 3 · держатся · Осталось: …» (A66 §4.2). [count] null: a phrase stands
 * instead of a number (a first week, a week ahead, care) — never «0 из 3» (Б4).
 */
data class Verdict(val count: String?, val word: String?, val left: String?, val pathLink: String?)

/** What the tab draws above the fold. */
sealed interface WeekFaceView {
    /** No goal of the Path and no care: «Назови цель», what is already seen (A66 §0.3, mockup 09). */
    data class NoPath(val nights: NightsSeen?, val behind: BehindSeen?) : WeekFaceView

    /** The verdict, the blank of the Sculpture and 2–3 goal cards (mockup 06). */
    data class Goals(val verdict: Verdict, val cards: List<GoalCard>, val carved: Int, val stage: PathStage?) : WeekFaceView
}

/** Which of the two faces the week shows. */
enum class WeekLayout { NO_PATH, GOALS }

/**
 * The face of «Неделя» v3 (A66 §4, §0.3): pure rules, no Android. The goals and their progress
 * come from [WeekView]; the days each goal was done from [GoalDays]; the Path from [PathWeek].
 */
object WeekFace {
    /** A56 §1: a stage is 12 weeks with milestones at 4, 8 and 12 (mirrors `StageRules` of `:domain:path`). */
    const val STAGE_WEEKS = 12
    val MILESTONES = listOf(4, 8, 12)

    /** A66 §4.3: 2–3 goal cards, the experiment at most one of them. */
    const val CARDS_MAX = 3

    /**
     * A66 §0.3: without a goal of the Path the week does not invent goals from data — unless care
     * comes first (sickness, STORM, crisis; grief and a pause hold the Path as [PathWeek.Paused]).
     */
    fun layout(path: PathWeek, care: WeekCare): WeekLayout =
        if (path == PathWeek.NoGoal && !care.any) WeekLayout.NO_PATH else WeekLayout.GOALS

    /** The week of the stage at [today]: the clock of the stage stops for the closed pauses (P-16). */
    fun stage(path: PathWeek, today: LocalDate): PathStage? {
        if (path !is PathWeek.Active) return null
        val days = ChronoUnit.DAYS.between(path.takenOn, today.minusDays(path.pausedDays.toLong())).coerceAtLeast(0)
        val week = (days / 7 + 1).toInt().coerceIn(1, STAGE_WEEKS)
        val milestone = MILESTONES.indexOfFirst { it >= week } + 1
        return PathStage(week, STAGE_WEEKS, milestone)
    }

    /**
     * The component of the Path a week goal comes from, by its working name (`PathLexicon.name`,
     * A55 §3; the mapping of `StageWeek.goalId`).
     */
    fun component(goalId: String): String? = when (goalId) {
        WeekGoalRules.ID_TRAINING -> "Сила"
        WeekGoalRules.ID_ZONES -> "Выносливость"
        WeekGoalRules.ID_FOCUS -> "Внимание"
        WeekGoalRules.ID_SLEEP -> "Сон"
        else -> null
    }

    /** The chip of the source (A66 §0.3, §4.3): every goal says where it comes from. */
    fun chip(goal: WeekGoal, stage: PathStage?): String = when (goal.source) {
        GoalSource.STAGE -> component(goal.id)?.let { "${WeekFaceCopy.CHIP_PATH} · $it" } ?: WeekFaceCopy.CHIP_PATH
        GoalSource.PERSON -> stage?.let { "${WeekFaceCopy.CHIP_OWN} · ${WeekFaceCopy.toMilestone(it.nextMilestone)}" } ?: WeekFaceCopy.CHIP_OWN
        GoalSource.EXPERIMENT -> WeekFaceCopy.CHIP_EXPERIMENT
        GoalSource.START -> WeekFaceCopy.CHIP_START
    }

    /** The 2–3 goals of the cards: the stage first, then the person's, one experiment, the starter ones. */
    fun pick(goals: List<WeekGoal>): List<WeekGoal> {
        val rank = mapOf(GoalSource.STAGE to 0, GoalSource.PERSON to 1, GoalSource.EXPERIMENT to 2, GoalSource.START to 3)
        val sorted = goals.withIndex().sortedWith(compareBy({ rank.getValue(it.value.source) }, { it.index })).map { it.value }
        val oneExperiment = sorted.filter { it.source == GoalSource.EXPERIMENT }.take(1)
        return sorted.filter { it.source != GoalSource.EXPERIMENT || it in oneExperiment }.take(CARDS_MAX)
    }

    private fun daily(goal: WeekGoal): Boolean = goal.measure == GoalMeasure.NIGHTS || goal.measure == GoalMeasure.DAYS

    private fun remaining(goal: WeekGoal): Int = (goal.target - goal.progress).coerceAtLeast(0)

    /**
     * The seven dots of a goal (A66 §1.1, §4.3). A done day is ●. A past day without data of a
     * sensor goal is ◌. Ahead (today included): ○ while something is left, «·» when nothing is; a
     * goal counted in times leaves Sunday «·» unless the days before it are not enough. A past day
     * not done is «·» («не нужно» — no reproach); a goal of every night or day keeps it ○.
     */
    fun dots(goal: WeekGoal, days: GoalDays, monday: LocalDate, today: LocalDate): List<DayDot> {
        val left = remaining(goal)
        val week = (0..6).map { monday.plusDays(it.toLong()) }
        val open = week.filter { !it.isBefore(today) && it !in days.done }
        val needSunday = left > open.count { it.dayOfWeek != DayOfWeek.SUNDAY }
        return week.map { d ->
            when {
                d in days.done -> DayDot.DONE
                d.isBefore(today) -> when {
                    days.known != null && d !in days.known -> DayDot.NO_DATA
                    daily(goal) -> DayDot.PLANNED
                    else -> DayDot.NOT_NEEDED
                }
                left == 0 -> DayDot.NOT_NEEDED
                daily(goal) -> DayDot.PLANNED
                d.dayOfWeek == DayOfWeek.SUNDAY && !needSunday -> DayDot.NOT_NEEDED
                else -> DayDot.PLANNED
            }
        }
    }

    /** «удобнее в пт или сб»: the next two planned days after today; today alone when it is the last. */
    fun suggested(goal: WeekGoal, dots: List<DayDot>, monday: LocalDate, today: LocalDate): List<LocalDate> {
        if (daily(goal) || remaining(goal) == 0) return emptyList()
        val week = (0..6).map { monday.plusDays(it.toLong()) }
        val ahead = week.filterIndexed { i, d -> d.isAfter(today) && dots[i] == DayDot.PLANNED }.take(2)
        if (ahead.isNotEmpty()) return ahead
        val i = week.indexOf(today)
        return if (i >= 0 && dots[i] == DayDot.PLANNED) listOf(today) else emptyList()
    }

    /**
     * A goal holds (A66 §4.1 «2 из 3 держатся»): it is done, or what is left still fits the days
     * left and the progress keeps pace with the days already lived (progress / target ≥ days / 7).
     */
    fun holds(goal: WeekGoal, monday: LocalDate, today: LocalDate): Boolean {
        if (goal.target <= 0 || goal.progress >= goal.target) return true
        val lived = ChronoUnit.DAYS.between(monday, today).toInt().coerceIn(0, 6)
        val fits = goal.measure == GoalMeasure.MINUTES || remaining(goal) <= 7 - lived
        return fits && goal.progress.toLong() * 7 >= goal.target.toLong() * lived
    }

    private fun when_(days: List<LocalDate>, today: LocalDate): String? = when {
        days.isEmpty() -> null
        days == listOf(today) -> WeekFaceCopy.TODAY
        else -> "в " + days.joinToString(" или ") { WeekScoring.short(it) }
    }

    /** The line under the dots (A66 §4.3): one line, 13 sp. */
    fun left(goal: WeekGoal, suggested: List<LocalDate>, today: LocalDate): String {
        val n = remaining(goal)
        if (n == 0) return WeekFaceCopy.DONE
        val more = when (goal.measure) {
            GoalMeasure.MINUTES -> "ещё $n ${WeekCopy.UNIT_MINUTES}"
            GoalMeasure.NIGHTS -> "ещё $n ${WeekFaceCopy.plural(n, "ночь", "ночи", "ночей")}"
            GoalMeasure.DAYS -> "ещё $n ${WeekFaceCopy.plural(n, "день", "дня", "дней")}"
            GoalMeasure.TIMES -> "ещё $n"
        }
        val hint = when_(suggested, today)?.let { if (it == WeekFaceCopy.TODAY) it else "удобнее $it" }
        return if (hint != null) "$more — $hint" else more
    }

    /** One piece of «Осталось: …»: «силовая в пт или сб», «две ночи сна», «60 мин в зонах». */
    fun fragment(goal: WeekGoal, suggested: List<LocalDate>, today: LocalDate): String {
        val n = remaining(goal)
        val what = when (goal.id) {
            WeekGoalRules.ID_TRAINING -> WeekFaceCopy.counted(n, "тренировка", "тренировки", "тренировок", feminine = true)
            WeekGoalRules.ID_FOCUS -> WeekFaceCopy.counted(n, "фокус-блок", "фокус-блока", "фокус-блоков", feminine = false)
            WeekGoalRules.ID_SLEEP -> WeekFaceCopy.counted(n, "ночь сна", "ночи сна", "ночей сна", feminine = true)
            WeekGoalRules.ID_ZONES -> "$n ${WeekCopy.UNIT_MINUTES} в зонах"
            else -> if (n == 1) "«${goal.title}»" else "«${goal.title}» ×$n"
        }
        return when_(suggested, today)?.let { "$what $it" } ?: what
    }

    /** «Осталось: силовая в пт или сб и две ночи сна.» — at most two pieces, then «и ещё N цели». */
    fun remainingPhrase(open: List<String>, daysLeft: Int? = null): String? {
        if (open.isEmpty()) return null
        val shown = open.take(2)
        val rest = open.size - shown.size
        val body = when {
            rest > 0 -> shown.joinToString(", ") + " и ещё $rest ${WeekFaceCopy.plural(rest, "цель", "цели", "целей")}"
            else -> shown.joinToString(" и ")
        }
        val head = daysLeft?.let { "Осталось $it ${WeekFaceCopy.plural(it, "день", "дня", "дней")}" } ?: "Осталось"
        return "$head: $body."
    }

    private val FULL_DAYS = listOf("понедельник", "вторник", "среда", "четверг", "пятница", "суббота", "воскресенье")

    /** «Понедельник, среда — сделано; четверг, пятница — запланировано». */
    fun spokenDots(dots: List<DayDot>): String = listOf(
        DayDot.DONE to WeekFaceCopy.DOT_DONE,
        DayDot.PLANNED to WeekFaceCopy.DOT_PLANNED,
        DayDot.NO_DATA to WeekFaceCopy.DOT_NO_DATA,
    ).mapNotNull { (kind, word) ->
        dots.withIndex().filter { it.value == kind }.takeIf { it.isNotEmpty() }
            ?.joinToString(", ") { FULL_DAYS[it.index] }?.let { "$it — $word" }
    }.joinToString("; ").replaceFirstChar { it.uppercase() }

    fun card(goal: WeekGoal, days: GoalDays, monday: LocalDate, today: LocalDate, stage: PathStage?): GoalCard {
        val dots = dots(goal, days, monday, today)
        val suggested = suggested(goal, dots, monday, today)
        val shown = minOf(goal.progress, goal.target)
        val count = if (goal.measure == GoalMeasure.MINUTES) {
            "${WeekCopy.ofTotal(goal.progress, goal.target)} ${WeekCopy.UNIT_MINUTES}"
        } else {
            WeekCopy.ofTotal(shown, goal.target)
        }
        val chip = chip(goal, stage)
        val left = left(goal, suggested, today)
        val spoken = listOf(goal.title, count, chip, spokenDots(dots), left).filter { it.isNotBlank() }.joinToString(". ")
        val todayIndex = ChronoUnit.DAYS.between(monday, today).toInt().coerceIn(0, 6)
        return GoalCard(goal, count, chip, dots, todayIndex, left, goal.progress >= goal.target, holds(goal, monday, today), spoken)
    }

    /**
     * The verdict and the cards of the week (A66 §4.2, §4.6): «2 из 3 держатся» and what is left;
     * a first week before any mark «Неделя собирается»; nothing holding yet «Неделя впереди» with
     * the days left; care says the goals wait, without a number.
     */
    fun goals(
        view: WeekView,
        goalDays: Map<String, GoalDays>,
        path: PathWeek,
        care: WeekCare = WeekCare(),
        kind: WeekKind = WeekKind.NORMAL,
    ): WeekFaceView.Goals {
        val monday = view.weekStart
        val today = view.columns.firstOrNull { it.today }?.day ?: monday
        val stage = stage(path, today)
        val cards = pick(view.goals.map { it.goal }).map { card(it, goalDays[it.id] ?: GoalDays(), monday, today, stage) }
        val link = stage?.let { WeekFaceCopy.pathLink(it.week, it.weeks) }
        val open = cards.filter { !it.done }.map { c ->
            fragment(c.goal, suggested(c.goal, c.dots, monday, today), today)
        }
        val holding = cards.count { it.holds }
        val daysLeft = 7 - ChronoUnit.DAYS.between(monday, today).toInt().coerceIn(0, 6)
        val verdict = when {
            care.sick -> Verdict(null, WeekFaceCopy.RECOVERY, WeekFaceCopy.GOALS_WAIT, link)
            care.crisis -> Verdict(null, null, WeekFaceCopy.GOALS_WAIT, link)
            cards.isEmpty() -> Verdict(null, null, WeekCopy.GOAL_EMPTY, link)
            cards.all { it.done } -> Verdict(WeekCopy.ofTotal(cards.size, cards.size), WeekFaceCopy.word(cards.size), WeekFaceCopy.ALL_DONE, link)
            kind == WeekKind.FIRST && cards.all { it.goal.progress == 0 } -> Verdict(null, WeekFaceCopy.GATHERING, WeekFaceCopy.FIRST_WEEK, link)
            holding == 0 -> Verdict(null, WeekFaceCopy.AHEAD, remainingPhrase(open, daysLeft), link)
            else -> Verdict(WeekCopy.ofTotal(holding, cards.size), WeekFaceCopy.word(holding), remainingPhrase(open), link)
        }
        val carved = ChronoUnit.DAYS.between(monday, today).toInt().coerceIn(0, 6) + 1
        return WeekFaceView.Goals(verdict, cards, carved, stage)
    }

    /** The face of the week: «Назови цель» without a Path (A66 §0.3), the goals otherwise. */
    fun build(
        view: WeekView,
        goalDays: Map<String, GoalDays>,
        path: PathWeek,
        care: WeekCare = WeekCare(),
        kind: WeekKind = WeekKind.NORMAL,
        nights: NightsSeen? = null,
        behind: BehindSeen? = null,
    ): WeekFaceView = when (layout(path, care)) {
        WeekLayout.NO_PATH -> WeekFaceView.NoPath(nights, behind)
        WeekLayout.GOALS -> goals(view, goalDays, path, care, kind)
    }

    /** The nights of the last 7 days with data (hours each); null without any. */
    fun nights(hours: List<Double>, of: Int = 7): NightsSeen? {
        val known = hours.filter { it > 0.0 }.takeLast(of)
        if (known.isEmpty()) return null
        return NightsSeen((known.average() * 60).roundToInt(), known.size, of)
    }

    /** Workouts behind the person; null when none are known (the line is not shown). */
    fun behind(workouts: Int, days: Int): BehindSeen? = if (workouts > 0 && days > 0) BehindSeen(workouts, days) else null
}

/** The words of the face of «Неделя» v3 (A66 §4.7, §0.3): drafts for the review of Agents 16 and 10. */
object WeekFaceCopy {
    const val VERDICT_OVERLINE = "Цели недели"
    const val HOLD_ONE = "держится"
    const val HOLD_MANY = "держатся"
    const val DONE = "Сделано. Дальше — по желанию"
    const val ALL_DONE = "Цели недели сделаны. Дальше — по желанию."
    const val GATHERING = "Неделя собирается"
    const val FIRST_WEEK = "Небольшие цели, чтобы присмотреться."
    const val AHEAD = "Неделя впереди"
    const val RECOVERY = "Неделя на восстановление"
    const val GOALS_WAIT = "Цели подождут."
    const val TODAY = "сегодня"
    const val CHIP_PATH = "из Пути"
    const val CHIP_OWN = "твоя"
    const val CHIP_EXPERIMENT = "эксперимент"
    const val CHIP_START = "стартовая неделя"
    const val SCULPTURE = "Скульптура"
    const val DOT_DONE = "сделано"
    const val DOT_PLANNED = "запланировано"
    const val DOT_NO_DATA = "нет данных"
    const val NO_GOAL_OVERLINE = "Пока нет цели"
    const val NO_GOAL_TITLE = "Назови цель — неделя соберётся из неё"
    const val NO_GOAL_TEXT = "Без цели HoT не придумывает план из данных. 90 секунд."
    const val NO_GOAL_BUTTON = "Назвать цель · 90 с"
    const val SEEN_NOW = "Что видно уже сейчас"
    const val DAYS_ROW = "Дни недели"
    const val MORE_GOALS = "Ещё на неделе"

    fun word(holding: Int): String = if (holding == 1) HOLD_ONE else HOLD_MANY

    fun toMilestone(n: Int): String = "к вехе $n"

    fun pathLink(week: Int, weeks: Int): String = "Путь · неделя $week из $weeks"

    fun sculpture(carved: Int): String = "$SCULPTURE · $carved/7"

    fun sculptureSpoken(carved: Int): String = "Скульптура недели: обтёсано $carved из 7 дней"

    /** «Ночи за неделю: средняя 6:44». */
    fun nights(meanMinutes: Int): String = "Ночи за неделю: средняя ${meanMinutes / 60}:${(meanMinutes % 60).toString().padStart(2, '0')}"

    /** «За плечами: 38 тренировок за 90 дней». */
    fun behind(workouts: Int, days: Int): String =
        "За плечами: $workouts ${plural(workouts, "тренировка", "тренировки", "тренировок")} за $days ${plural(days, "день", "дня", "дней")}"

    /** Russian plural: 1 ночь, 2 ночи, 5 ночей, 21 ночь. */
    fun plural(n: Int, one: String, few: String, many: String): String {
        val m10 = n % 10
        val m100 = n % 100
        return when {
            m10 == 1 && m100 != 11 -> one
            m10 in 2..4 && m100 !in 12..14 -> few
            else -> many
        }
    }

    /** «тренировка», «две тренировки», «5 тренировок»: words for 2–4, digits from 5. */
    fun counted(n: Int, one: String, few: String, many: String, feminine: Boolean): String = when (n) {
        1 -> one
        2 -> (if (feminine) "две " else "два ") + few
        3 -> "три $few"
        4 -> "четыре $few"
        else -> "$n ${plural(n, one, few, many)}"
    }

    /** Every user-facing string above, for the stop-dictionary test. */
    fun all(): List<String> = listOf(
        VERDICT_OVERLINE, HOLD_ONE, HOLD_MANY, DONE, ALL_DONE, GATHERING, FIRST_WEEK, AHEAD, RECOVERY, GOALS_WAIT, TODAY,
        CHIP_PATH, CHIP_OWN, CHIP_EXPERIMENT, CHIP_START, SCULPTURE, DOT_DONE, DOT_PLANNED, DOT_NO_DATA,
        NO_GOAL_OVERLINE, NO_GOAL_TITLE, NO_GOAL_TEXT, NO_GOAL_BUTTON, SEEN_NOW, DAYS_ROW, MORE_GOALS,
        toMilestone(1), pathLink(3, 12), sculpture(4), sculptureSpoken(4), nights(404), behind(38, 90),
    )
}
