package com.healthofatitan.app.domain.today.week

import com.healthofatitan.app.domain.today.Corridor
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * What the week knows of its supports (A52 §2.3). Every value is HoT-derived or a WHOOP value held
 * in memory only; every field may be missing.
 */
data class SupportsWeekInput(
    /** «Весы» (LRB, z) of each day so far: + = load piles up over recovery. */
    val balanceZ: List<Double> = emptyList(),
    /** Заряд within the personal norm most of the week (for «недогрузка»). */
    val chargeInNorm: Boolean = false,
    val stepDays: Int? = null,
    /** Days of the week with a step count at all. */
    val stepDaysKnown: Int = 0,
    /** WHOOP workouts `zone_durations`, zones 2–5, this week (memory only). */
    val zoneMinutes: Int? = null,
    val mealDays: Int? = null,
    val mealDaysKnown: Int = 0,
    /** Days with protein under 70 % of its norm; null until photos of food (A46 §2.3) exist. */
    val lowProteinDays: Int? = null,
    /** Mean share of the drink norm, 0…1+. */
    val drinkShare: Double? = null,
    /** Sleep debt of the last 7 days, hours (SLPDEBT). */
    val sleepDebtHours: Double? = null,
    /** WHOOP sleep consistency, mean %, memory only. */
    val regularityPct: Double? = null,
    val nightsAtNeed: Int? = null,
    val nightsKnown: Int = 0,
)

enum class SupportWeekId { BALANCE, MOVEMENT, NUTRITION, DRINK, SLEEP, WEIGHT }

/** A skew (A52 §2.3): a pictogram of «Весы» and one phrase, never red, never a grade. */
data class Skew(val support: SupportWeekId, val text: String)

data class SupportWeekRow(val id: SupportWeekId, val label: String, val lines: List<String>, val skew: Skew?)

/** Why negative skews are hidden this week (W-04). */
data class Shelter(val gentleWeek: Boolean, val returnWeek: Boolean, val stormNetWithin7Days: Boolean) {
    val active: Boolean get() = gentleWeek || returnWeek || stormNetWithin7Days
}

/** W-07: weight on «Неделя» — only with an open weight goal (D-043, behind its flag) and never with `ed_care`. */
data class WeightInput(
    val flagOn: Boolean = WeekFlags.WEIGHT_GOALS,
    val goalOpen: Boolean = false,
    val edCare: Boolean = false,
    val meanKg: Double? = null,
    val trendKg: Double? = null,
)

object WeightRules {
    fun visible(w: WeightInput): Boolean = w.flagOn && w.goalOpen && !w.edCare
}

/** Feature flags of the module; all off by default until the reviews (D-043, D-045). */
object WeekFlags {
    /** D-043 п. 1: goals of weight with ranges, after Agents 3, 10, 7. */
    const val WEIGHT_GOALS = false

    /** D-045: «фитнес-возраст», after Agents 3 and 10. */
    const val FITNESS_AGE = false
}

object WeekSupports {
    const val PILING_Z = 1.0
    const val PILING_DAYS = 3
    const val STEPS_MIN_DAYS = 3
    const val PROTEIN_DAYS = 4
    const val DRINK_MIN = 0.6
    const val DEBT_HOURS = 5.0
    const val REGULARITY_MIN = 60.0

    fun clock(hours: Double): String {
        val m = (hours * 60).roundToInt().coerceAtLeast(0)
        return "${m / 60}:${(m % 60).toString().padStart(2, '0')}"
    }

    /**
     * The supports of the week in their order (A52 §2.3) with at most one skew each. W-04: in a
     * week of Бережно, of return and 7 days after `StormNet` no negative skew is shown at all.
     * W-07: the weight row only where [WeightRules.visible].
     */
    fun rows(i: SupportsWeekInput, shelter: Shelter, weight: WeightInput, daysSoFar: Int): List<SupportWeekRow> {
        val z = i.balanceZ
        val inBalance = z.count { abs(it) <= PILING_Z }
        val piling = z.count { it > PILING_Z } >= PILING_DAYS
        val under = z.size >= 5 && z.all { it < -PILING_Z } && i.chargeInNorm
        val balance = SupportWeekRow(
            SupportWeekId.BALANCE, WeekCopy.S_BALANCE,
            if (z.isEmpty()) listOf(WeekCopy.S_NO_DATA) else listOf(WeekCopy.balanceDays(inBalance, z.size)),
            when {
                piling -> Skew(SupportWeekId.BALANCE, WeekCopy.SKEW_PILING)
                under -> Skew(SupportWeekId.BALANCE, WeekCopy.SKEW_UNDERLOAD)
                else -> null
            },
        )
        val moveLines = listOfNotNull(
            i.stepDays?.let { WeekCopy.stepsDays(it, i.stepDaysKnown.coerceAtLeast(it)) },
            i.zoneMinutes?.let(WeekCopy::zones),
        ).ifEmpty { listOf(WeekCopy.S_NO_DATA) }
        val movement = SupportWeekRow(
            SupportWeekId.MOVEMENT, WeekCopy.S_MOVEMENT, moveLines,
            Skew(SupportWeekId.MOVEMENT, WeekCopy.SKEW_STEPS).takeIf { i.stepDays != null && i.stepDaysKnown >= 5 && i.stepDays < STEPS_MIN_DAYS },
        )
        val nutrition = SupportWeekRow(
            SupportWeekId.NUTRITION, WeekCopy.S_NUTRITION,
            listOfNotNull(i.mealDays?.let { WeekCopy.mealsDays(it, i.mealDaysKnown.coerceAtLeast(it)) }).ifEmpty { listOf(WeekCopy.S_NO_DATA) },
            Skew(SupportWeekId.NUTRITION, WeekCopy.SKEW_PROTEIN).takeIf { (i.lowProteinDays ?: 0) >= PROTEIN_DAYS },
        )
        val drink = SupportWeekRow(
            SupportWeekId.DRINK, WeekCopy.S_DRINK,
            listOf(i.drinkShare?.let { WeekCopy.drinkPct((it * 100).roundToInt()) } ?: WeekCopy.S_NO_DATA),
            Skew(SupportWeekId.DRINK, WeekCopy.SKEW_DRINK).takeIf { i.drinkShare != null && daysSoFar >= 3 && i.drinkShare < DRINK_MIN },
        )
        val sleep = SupportWeekRow(
            SupportWeekId.SLEEP, WeekCopy.S_SLEEP,
            listOfNotNull(
                i.sleepDebtHours?.let { WeekCopy.sleepDebt(clock(it)) },
                i.regularityPct?.let { WeekCopy.regularity(it.roundToInt()) },
                i.nightsAtNeed?.let { WeekCopy.nightsAtNeed(it, i.nightsKnown.coerceAtLeast(it)) },
            ).ifEmpty { listOf(WeekCopy.S_NO_DATA) },
            when {
                (i.sleepDebtHours ?: 0.0) >= DEBT_HOURS -> Skew(SupportWeekId.SLEEP, WeekCopy.SKEW_DEBT)
                i.regularityPct != null && i.nightsKnown >= 3 && i.regularityPct < REGULARITY_MIN -> Skew(SupportWeekId.SLEEP, WeekCopy.SKEW_REGULARITY)
                else -> null
            },
        )
        val rows = listOf(balance, movement, nutrition, drink, sleep).map { if (shelter.active) it.copy(skew = null) else it }
        val weightRow = if (WeightRules.visible(weight)) {
            SupportWeekRow(
                SupportWeekId.WEIGHT, WeekCopy.S_WEIGHT,
                listOf(weight.meanKg?.let { WeekCopy.weightAvg(String.format(java.util.Locale.ROOT, "%.1f", it).replace('.', ',')) } ?: WeekCopy.WEIGHT_NO_DATA),
                null,
            )
        } else {
            null
        }
        return rows + listOfNotNull(weightRow)
    }
}

/** One of the five body signals of the week (A52 §2.4). */
data class BodySeries(val name: String, val days: List<Double?>, val corridor: Corridor?)

enum class BodyArrow { UP, DOWN, SAME }

data class BodyRow(val name: String, val arrow: BodyArrow?, val words: String, val days: List<Double?>, val spoken: String)

data class BodyWeek(val rows: List<BodyRow>, val redFlag: Boolean, val empty: Boolean)

object BodyRules {
    /** A52 §2.4: resting pulse ≥ +2 SD of the corridor three nights in a row is a red flag, not a skew. */
    const val FLAG_SD = 2.0
    const val FLAG_NIGHTS = 3
    const val ARROW_SD = 1.0

    fun arrow(series: BodySeries): BodyArrow? {
        val c = series.corridor ?: return null
        val values = series.days.filterNotNull().takeIf { it.isNotEmpty() } ?: return null
        val mean = values.average()
        val sd = c.sd.takeIf { it > 0 } ?: return BodyArrow.SAME
        return when {
            mean > c.mean + ARROW_SD * sd -> BodyArrow.UP
            mean < c.mean - ARROW_SD * sd -> BodyArrow.DOWN
            else -> BodyArrow.SAME
        }
    }

    fun redFlag(restingHr: BodySeries?): Boolean {
        val c = restingHr?.corridor ?: return false
        if (c.sd <= 0) return false
        var run = 0
        restingHr.days.forEach { v ->
            run = if (v != null && v >= c.mean + FLAG_SD * c.sd) run + 1 else 0
            if (run >= FLAG_NIGHTS) return true
        }
        return false
    }

    fun build(series: List<BodySeries>, restingHrName: String = WeekCopy.B_RHR): BodyWeek {
        val rows = series.map { s ->
            val a = arrow(s)
            val words = when (a) {
                BodyArrow.UP -> WeekCopy.B_UP
                BodyArrow.DOWN -> WeekCopy.B_DOWN
                BodyArrow.SAME -> WeekCopy.B_SAME
                null -> WeekCopy.S_NO_DATA
            }
            BodyRow(s.name, a, words, s.days, "${s.name}: $words")
        }
        return BodyWeek(rows, redFlag(series.firstOrNull { it.name == restingHrName }), empty = rows.all { it.arrow == null })
    }
}
