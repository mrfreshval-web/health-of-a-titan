package com.healthofatitan.app.domain.path

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round

/**
 * R7 «Как реально» (A54 §4, A56 Э6): `достижимо = сейчас + безопасный_темп × недели × 0,7`, the
 * pace never above the personal pace of «Взято», 0,7 for the planning fallacy (Buehler 2010).
 * The safe pace tables (Agents 3, 12, 8) are a blocker (D-047): until they exist, [safePacePerWeek]
 * is null and only the personal pace counts; with neither there is no forecast («Темп узнаем на
 * вехе 1») and the goal stays a process goal.
 */
sealed interface Realism {
    /** No pace: «Темп узнаем на вехе 1», process criteria only. */
    data object NoPace : Realism

    /** «Успеваем в твоём темпе». */
    data class OnTrack(val achievable: Double) : Realism

    /**
     * Two cards: «к сроку» — the achievable range by the deadline; «целиком» — the full bar by a
     * computed date rounded to a month. A dangerous pace is never offered (A56 §2.2).
     */
    data class Split(val byDate: ClosedFloatingPointRange<Double>, val fullBy: LocalDate) : Realism

    /** Even the safe minimum does not fit: only a process goal (A56 §2.2). */
    data object ProcessOnly : Realism
}

object RealismRules {
    const val PLANNING_FACTOR = 0.7

    /** The step between stages: not more than 20–30 % (A54 R7, P-06); the code holds the upper bound. */
    const val MAX_STEP = 0.30

    /**
     * @param needed the change the criterion asks for (a range, P-06), in its units
     * @param weeks weeks to the deadline
     */
    fun assess(
        needed: ClosedFloatingPointRange<Double>,
        weeks: Int,
        today: LocalDate,
        safePacePerWeek: Double?,
        personalPacePerWeek: Double?,
    ): Realism {
        val pace = listOfNotNull(safePacePerWeek, personalPacePerWeek).minOrNull() ?: return Realism.NoPace
        if (pace <= 0.0 || weeks <= 0) return Realism.ProcessOnly
        val achievable = pace * weeks * PLANNING_FACTOR
        if (achievable + EPS >= needed.endInclusive) return Realism.OnTrack(round1(achievable))
        val low = round1(achievable * BY_DATE_LOW)
        if (low <= 0.0) return Realism.ProcessOnly
        val fullWeeks = ceil(needed.endInclusive / (pace * PLANNING_FACTOR)).toLong()
        val fullBy = today.plusWeeks(fullWeeks).let { d -> if (d.dayOfMonth == 1) d else d.with(TemporalAdjusters.firstDayOfNextMonth()) }
        return Realism.Split(low..round1(achievable), fullBy)
    }

    /** A step from [from] to [to] fits the 30 % rule (P-06); a mark step of one is always fine. */
    fun stepOk(from: Double, to: Double): Boolean = from <= 0.0 || to <= from * (1 + MAX_STEP) + EPS

    /** The raised bar «Поднять планку»: at most 30 % above (A56 §3 «Взял раньше срока»). */
    fun raise(current: Double): Double = floor(current * (1 + MAX_STEP) * 10.0) / 10.0

    fun weeksBetween(from: LocalDate, to: LocalDate): Int = (ChronoUnit.DAYS.between(from, to) / 7).toInt()

    private fun round1(v: Double) = round(v * 10.0) / 10.0

    private const val BY_DATE_LOW = 0.75
    private const val EPS = 1e-9
}
