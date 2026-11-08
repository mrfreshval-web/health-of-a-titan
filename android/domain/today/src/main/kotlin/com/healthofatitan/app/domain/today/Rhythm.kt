package com.healthofatitan.app.domain.today

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/** The rituals of the day (A49, D-041, D-043) as points of «Ритм дня». Передышка is a button, not a point. */
enum class Ritual(val word: String) {
    DAWN(TodayCopy.DAWN),
    TRANSITION(TodayCopy.TRANSITION),
    CLOSE_DAY(TodayCopy.CLOSE_DAY),
    LANDING(TodayCopy.LANDING),

    /** «Итог и сборка» on Sundays (A52 §2.6). */
    WEEK_ASSEMBLY(TodayCopy.WEEK_ASSEMBLY),
}

/** Which rituals are on (D-043): Рассвет, Передышка, Закрыть день by default; Переход and Приземление are offered on day 7. */
data class RitualSettings(val transition: Boolean = false, val landing: Boolean = false) {
    /** D-043: «Работа сегодня» lives in Переход when it is on, else in «Закрыть день». */
    val workTodayIn: Ritual get() = if (transition) Ritual.TRANSITION else Ritual.CLOSE_DAY

    fun enabled(day: LocalDate): List<Ritual> = buildList {
        add(Ritual.DAWN)
        if (transition) add(Ritual.TRANSITION)
        add(Ritual.CLOSE_DAY)
        if (landing) add(Ritual.LANDING)
        if (day.dayOfWeek == DayOfWeek.SUNDAY) add(Ritual.WEEK_ASSEMBLY)
    }
}

data class RitualDot(val ritual: Ritual, val done: Boolean, val active: Boolean)

enum class BodyWindowKind(val word: String) {
    COFFEE(TodayCopy.WIN_COFFEE),
    DINNER(TodayCopy.WIN_DINNER),
    SCREENS(TodayCopy.WIN_SCREENS),
    BEDTIME(TodayCopy.WIN_BEDTIME),
}

data class BodyWindow(val kind: BodyWindowKind, val min: Int) {
    val text: String get() = "${kind.word} ${Supports.clock(min)}"
}

data class RhythmView(
    val dots: List<RitualDot>,
    val windows: List<BodyWindow>,
    /** Бережно (A46 §4.7): «Передышка» is lit. */
    val breatherLit: Boolean,
    /** The windows come from a default day (MANUAL: no scored night or no WHOOP): «оценка». */
    val estimate: Boolean = false,
)

data class RhythmInput(
    val now: LocalDateTime,
    val scenario: Scenario,
    val settings: RitualSettings = RitualSettings(),
    val dawnDone: Boolean = false,
    val dayClosed: Boolean = false,
    val sleep: SleepPrep? = null,
    /** The plan behind [sleep] is the MANUAL default day (A09 REG-05). */
    val estimate: Boolean = false,
)

object RhythmRules {
    const val CLOSE_DAY_FROM_HOUR = 18

    /**
     * The rituals of D-043 in every scenario, with or without WHOOP (S2): a day without the strap
     * still has its «Рассвет» (the wake deck is the self-report) and its «Закрыть день».
     */
    fun build(i: RhythmInput): RhythmView {
        val rituals = i.settings.enabled(i.now.toLocalDate())
        val evening = i.now.hour >= CLOSE_DAY_FROM_HOUR || i.now.hour < 4
        val dots = rituals.map { r ->
            when (r) {
                Ritual.DAWN -> RitualDot(r, done = i.dawnDone, active = !i.dawnDone && !evening)
                Ritual.CLOSE_DAY -> RitualDot(r, done = i.dayClosed, active = evening && !i.dayClosed)
                else -> RitualDot(r, done = false, active = false)
            }
        }
        val windows = i.sleep?.let { s ->
            listOfNotNull(
                BodyWindow(BodyWindowKind.COFFEE, s.caffeineUntilMin),
                BodyWindow(BodyWindowKind.DINNER, s.dinnerUntilMin),
                BodyWindow(BodyWindowKind.SCREENS, s.screensFromMin),
                s.bedtimeMin?.let { BodyWindow(BodyWindowKind.BEDTIME, it) },
            )
        }.orEmpty()
        return RhythmView(dots, windows, breatherLit = i.scenario == Scenario.GENTLE, estimate = i.estimate && windows.isNotEmpty())
    }
}

/**
 * «Рассвет» is one ritual with one trigger (D-040, A46 §5): the former three screens (the blind
 * deck, the reveal «ты ↔ WHOOP», the dawn with the haiku) are its steps, entered at the first step
 * not yet passed today.
 */
enum class RassvetStep { DECK, REVEAL, DAWN }

object Rassvet {
    const val FROM_HOUR = 5

    /**
     * The one trigger: once a day, after 05:00, when the night is scored, if nothing of the ritual
     * has been offered today. Returns the step it opens at, or null.
     */
    fun trigger(now: LocalDateTime, nightReady: Boolean, offeredToday: Boolean, deckDone: Boolean, revealPending: Boolean, dawnShown: Boolean): RassvetStep? {
        if (offeredToday || !nightReady || now.hour < FROM_HOUR) return null
        return next(deckDone, revealPending, dawnShown)
    }

    /** The step after the current one (also what a tap on the «Рассвет» point opens). */
    fun next(deckDone: Boolean, revealPending: Boolean, dawnShown: Boolean): RassvetStep? = when {
        !deckDone -> RassvetStep.DECK
        revealPending -> RassvetStep.REVEAL
        !dawnShown -> RassvetStep.DAWN
        else -> null
    }
}

/** Why the app may leave «Сегодня» by itself (T-05): only these two. */
enum class AutoOpenReason { RASSVET, CRISIS }

object AutoOpen {
    /** CrisisGuard level from which the crisis card opens by itself (A07 §4.1). */
    const val CRISIS_LEVEL = 2

    /**
     * T-05: without a user action the route stays `TODAY`; the exceptions are the «Рассвет» trigger
     * (once a day) and CrisisGuard ≥ 2. Help wins over the morning.
     */
    fun reason(crisisLevel: Int, rassvet: RassvetStep?): AutoOpenReason? = when {
        crisisLevel >= CRISIS_LEVEL -> AutoOpenReason.CRISIS
        rassvet != null -> AutoOpenReason.RASSVET
        else -> null
    }
}
