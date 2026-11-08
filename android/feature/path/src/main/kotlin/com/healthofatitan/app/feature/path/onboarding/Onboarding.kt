package com.healthofatitan.app.feature.path.onboarding

import com.healthofatitan.app.domain.path.ComponentKey
import com.healthofatitan.app.domain.path.GoalComponent
import com.healthofatitan.app.domain.path.PathLexicon
import com.healthofatitan.app.domain.path.PracticalGoal
import com.healthofatitan.app.domain.today.CalendarWeek
import com.healthofatitan.app.domain.today.GoalSource
import com.healthofatitan.app.domain.today.WeekGoal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The first launch of A66 §0.2 (v3 mockups 01–04): Приветствие → «Откуда ты идёшь» → «Куда ты
 * идёшь» → «Первая неделя из Пути» → «Сегодня». Pure rules here; the screens and the store are next
 * to it. Numbers come from stored data only (P-07); free text never leaves the phone (P-21).
 */
enum class OnboardingStep { WELCOME, BEHIND, PATH, WEEK }

/** Where the first launch stands for this phone. */
enum class FirstLaunchGate {
    /** Not read yet: nothing of the first launch is drawn. */
    LOADING,

    /** The first launch is shown over the app (after the gate 18+ and «Вход»). */
    SHOW,

    /** Passed or put off by «Позже»: the home is «Сегодня» (D-065). */
    DONE,
}

/** Three questions of «Откуда ты идёшь»: multiple choice, no score (A66 §0.2 step 3, §6.5). */
data class BehindAnswers(
    val tried: Set<String> = emptySet(),
    val worked: Set<String> = emptySet(),
    val different: Set<String> = emptySet(),
)

/** What the stored history says (WHOOP or Health Connect), for the card of step 1. */
data class BehindImport(
    val from: LocalDate,
    val to: LocalDate,
    val trainings: Int,
    val nightHours: Double?,
    val charge: Int?,
)

/** A milestone of the stage: every 4 weeks, ending on a Sunday (A66 §5). */
data class Milestone(val index: Int, val week: Int, val date: LocalDate, val label: String)

/** «HoT предлагает» on step 2: the lever of the stage and the milestones. */
data class StageSuggestion(val lever: ComponentKey?, val reason: String?, val milestones: List<Milestone>)

object OnboardingRules {
    /** The stage lengths of step 2: 4, 8 or 12 weeks (A66 §0.2 step 4). */
    val STAGES = listOf(4, 8, 12)
    const val DEFAULT_STAGE = 12
    const val MILESTONE_EVERY = 4

    /** The history the card of step 1 looks back on (A66 §0.2: up to 90 days, as the source gives). */
    const val IMPORT_DAYS = 90L

    /** At most this many goals of the week on step 3 (A66 §4: 2–3 goals). */
    const val WEEK_GOALS_MAX = 3

    /** «Где пошло иначе» → the part of the goal that becomes the lever of the stage. */
    private val leverOf = mapOf(
        OnboardingCopy.DIFF_SLEEP to ComponentKey.SLEEP,
        OnboardingCopy.DIFF_INJURY to ComponentKey.MOBILITY,
        OnboardingCopy.DIFF_WORK to ComponentKey.REST,
    )

    /**
     * The first launch is over when it was passed or put off, or when the person already had a goal
     * of the Path before it started (taken in «Назови цель»: not walked through again).
     */
    fun passed(done: Boolean, started: Boolean, hasGoal: Boolean): Boolean = done || (hasGoal && !started)

    /** The next step; WEEK is the last one (its button closes the first launch). */
    fun next(step: OnboardingStep): OnboardingStep? = OnboardingStep.entries.getOrNull(step.ordinal + 1)

    fun back(step: OnboardingStep): OnboardingStep? = OnboardingStep.entries.getOrNull(step.ordinal - 1)

    /** «1 из 3» for the three steps after the welcome. */
    fun position(step: OnboardingStep): Int = step.ordinal

    fun toggle(set: Set<String>, chip: String): Set<String> = if (chip in set) set - chip else set + chip

    /**
     * The lever HoT suggests from «Где пошло иначе»: the first answer that names a part of the goal.
     * Null keeps the lever of the decomposition.
     */
    fun suggestedLever(answers: BehindAnswers, parts: List<GoalComponent>): ComponentKey? {
        val keys = parts.map { it.key }.toSet()
        return OnboardingCopy.DIFFERENT.firstNotNullOfOrNull { chip -> leverOf[chip]?.takeIf { chip in answers.different && it in keys } }
    }

    /** The parts with [lever] as the one lever; unchanged when [lever] is null or not a part. */
    fun withLever(parts: List<GoalComponent>, lever: ComponentKey?): List<GoalComponent> {
        if (lever == null || parts.none { it.key == lever }) return parts
        return parts.map { it.copy(isLever = it.key == lever) }
    }

    /** The deadline of a stage of [weeks]: the Sunday that ends its last calendar week. */
    fun deadline(today: LocalDate, weeks: Int): LocalDate = CalendarWeek.start(today).plusWeeks(weeks.toLong()).minusDays(1)

    /**
     * Milestones every 4 weeks up to [weeks]. A measured criterion gives the number of each step
     * (linear from its low to its high, P-07: from the table, no AI); without one, the week.
     */
    fun milestones(goal: PracticalGoal?, today: LocalDate, weeks: Int): List<Milestone> {
        val count = (weeks / MILESTONE_EVERY).coerceAtLeast(1)
        val c = goal?.criteria?.firstOrNull()
        return (1..count).map { i ->
            val week = i * MILESTONE_EVERY
            val label = if (c != null) {
                val v = c.low + (c.high - c.low) * i / count
                "${number(v)} ${c.unit}".trim()
            } else {
                OnboardingCopy.milestoneWeek(week)
            }
            Milestone(i, week, deadline(today, week), label)
        }
    }

    fun suggestion(goal: PracticalGoal?, answers: BehindAnswers, today: LocalDate, weeks: Int): StageSuggestion {
        val lever = goal?.lever?.key
        val named = goal?.components?.let { suggestedLever(answers, it) }
        val reason = when {
            lever == null -> null
            named != null && named == lever -> OnboardingCopy.leverFromBehind(PathLexicon.name(lever).lowercase(Locale.ROOT))
            else -> OnboardingCopy.leverDefault(PathLexicon.name(lever).lowercase(Locale.ROOT))
        }
        return StageSuggestion(lever, reason, milestones(goal, today, weeks))
    }

    /** Step 3: the goals of the stage first, then the others; at most three. */
    fun weekGoals(goals: List<WeekGoal>): List<WeekGoal> =
        goals.sortedBy { if (it.source == GoalSource.STAGE) 0 else 1 }.take(WEEK_GOALS_MAX)

    /** The chip of a goal of the week on step 3 (A66 §0.3: every goal names its source). */
    fun chip(goal: WeekGoal, lever: ComponentKey?): String = when (goal.source) {
        GoalSource.STAGE -> lever?.let { OnboardingCopy.chipLever(PathLexicon.name(it)) } ?: OnboardingCopy.CHIP_PATH
        GoalSource.PERSON -> OnboardingCopy.CHIP_OWN
        else -> OnboardingCopy.CHIP_START
    }

    private fun number(v: Double): String = if (v == v.roundToInt().toDouble()) v.roundToInt().toString() else String.format(Locale.ROOT, "%.1f", v)
}

/** Texts of the first launch (v3 mockups; drafts for Agents 16 and 10, health wording — Agent 3). */
object OnboardingCopy {
    const val WORDMARK = "HEALTH OF A TITAN"
    const val VALUE = "Твоя цель — в посильную неделю. Каждый день одно дело, которое её приближает, с поправкой на то, как ты восстановился."
    const val START = "Начать"
    const val NEXT = "Дальше"
    const val LATER = "Позже"
    const val ASSEMBLE = "Собрать неделю"
    const val BACK = "Назад"
    const val HELP = "Помощь"

    fun stepOf(n: Int): String = "ШАГ $n ИЗ 3"

    const val BEHIND_TITLE = "Откуда ты идёшь"
    const val BEHIND_SUB = "Всё прожитое — опора. Посмотрим, что уже за плечами."
    const val IMPORT_NONE = "Истории пока нет — ответь на вопросы, этого хватит."
    const val TRIED = "Что уже пробовал(а)?"
    const val WORKED = "Что получилось?"
    const val DIFFERENT_Q = "Где пошло иначе?"
    const val NO_SCORE = "Без оценок. Это нужно, чтобы подсказать рычаг этапа."
    val TRIED_CHIPS = listOf("Зал", "Бег", "Турник", "Плавание", "Йога")
    val WORKED_CHIPS = listOf("Сила росла", "Режим", "Сон", "Вес")
    const val DIFF_SLEEP = "Сон"
    const val DIFF_TIME = "Время"
    const val DIFF_INJURY = "Травма"
    const val DIFF_WORK = "Работа"
    val DIFFERENT = listOf(DIFF_SLEEP, DIFF_TIME, DIFF_INJURY, DIFF_WORK)

    const val TRAININGS = "тренировок"
    const val NIGHT = "средняя ночь"
    const val CHARGE = "средний Заряд"
    const val SOURCE = "История подключена"

    fun period(from: LocalDate, to: LocalDate): String {
        val f = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("ru"))
        return "${from.format(f)} – ${to.format(f)}"
    }

    fun hours(h: Double): String {
        val m = (h * 60).roundToInt()
        return "${m / 60}:${(m % 60).toString().padStart(2, '0')}"
    }

    const val PATH_TITLE = "Куда ты идёшь"
    const val PATH_SUB = "Цель своими словами и зачем она тебе."
    const val GOAL = "Цель"
    const val GOAL_HINT = "Например, подтянуться 10 раз"
    const val WHY = "Зачем"
    const val WHY_HINT = "Чтобы снова чувствовать себя сильным"
    const val STAGE = "Этап"

    fun weeks(n: Int): String = "$n ${if (n == 4) "недели" else "недель"}"

    const val SUGGESTS = "HOT ПРЕДЛАГАЕТ"

    fun leverTitle(name: String): String = "Рычаг этапа — ${name.lowercase(Locale.ROOT)}"

    fun leverFromBehind(name: String): String = "В прошлый раз пошло иначе как раз здесь: $name. С него и начнём."

    fun leverDefault(name: String): String = "С этой детали цель сдвигается легче всего: $name."

    fun milestoneWeek(week: Int): String = "конец недели $week"

    fun milestone(i: Int, label: String): String = "Веха $i · $label"

    val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("ru"))

    const val WEEK_TITLE = "Первая неделя из Пути"
    const val WEEK_SUB = "Цели от твоей нормы. Поправь, если много."
    const val WEEK_NOTE = "Неделя засчитывается, если держатся 2 из 3. Первое дело появится на «Сегодня» сразу."
    const val WEEK_EMPTY = "Неделя собирается — цели появятся через минуту."
    const val CHIP_PATH = "из Пути"
    const val CHIP_OWN = "твоя"
    const val CHIP_START = "старт"

    fun chipLever(name: String): String = "рычаг «$name»"

    const val PER_WEEK = "в неделю"
    const val LESS = "Меньше"
    const val MORE = "Больше"
}
