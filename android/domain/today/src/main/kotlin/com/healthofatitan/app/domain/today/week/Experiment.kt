package com.healthofatitan.app.domain.today.week

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * «Эксперимент недели» (A53 §3.2, D-050): one hypothesis to check on oneself, taken on Sunday,
 * filled day by day Monday to Saturday, answered next Sunday. Every outcome is «узнали», never
 * «не получилось». Rules of Agents 9 and 10: one at a time; ≥ 5 points; «связь, не причина»; none
 * in a week of «Бережно» or after StormNet; nothing about food or weight with `ed_care`.
 *
 * The goal of «Путь» can hand its lever over as the experiment (A57 §4.3): a seed with
 * [ExperimentSource.PATH] wins over the patterns.
 */
enum class ExperimentMetric(val words: String, val unit: String, val minShift: Double, val higherIsBetter: Boolean) {
    /** Minutes of sleep (WHOOP, memory only). */
    SLEEP(WeekCopy.EXP_M_SLEEP, WeekCopy.EXP_U_MIN, 15.0, true),

    /** Заряд (ECAP, HoT-derived, 0…100). */
    CHARGE(WeekCopy.EXP_M_CHARGE, WeekCopy.EXP_U_POINTS, 5.0, true),

    /** Resting pulse (WHOOP, memory only). */
    PULSE(WeekCopy.EXP_M_PULSE, WeekCopy.EXP_U_BPM, 2.0, false),
}

enum class ExperimentSource { PATTERN, PATH }

/** What can be checked: the hypothesis in the person's words, the action of a day, the measure. */
data class ExperimentSeed(
    val id: String,
    val hypothesis: String,
    val action: String,
    val metric: ExperimentMetric,
    val source: ExperimentSource = ExperimentSource.PATTERN,
    /** About food or weight: never offered with `ed_care`. */
    val food: Boolean = false,
)

enum class ExperimentStatus { TAKEN, SKIPPED }

/** The experiment of a week as stored. */
data class Experiment(val weekStart: LocalDate, val seed: ExperimentSeed, val status: ExperimentStatus)

/** One day of the experiment: the action done or not, and the measure of that day (null — unknown). */
data class ExperimentPoint(val day: LocalDate, val did: Boolean, val value: Double?)

enum class ExperimentOutcome { YES, OPPOSITE, NO_LINK, TOO_FEW }

data class ExperimentResult(val outcome: ExperimentOutcome, val headline: String, val text: String, val points: Int)

/** The scale on «Неделя» and the quiet bridge on «Сегодня»: «Эксперимент · 3 из 5 ›». */
data class ExperimentScale(val done: Int, val goal: Int, val days: List<Boolean?>) {
    val bridge: String get() = WeekCopy.expBridge(done, goal)
}

/** What closes the experiment for a week (A53 §3.2). */
data class ExperimentGate(
    val gentleWeek: Boolean = false,
    val stormNet: Boolean = false,
    val edCare: Boolean = false,
    val crisis: Boolean = false,
) {
    val open: Boolean get() = !gentleWeek && !stormNet && !crisis
}

object ExperimentRules {
    const val MIN_POINTS = 5
    const val MIN_BASELINE = 2

    /** Days of the scale: Monday to Saturday. */
    const val DAYS = 6

    private val METRIC_BY_SUFFIX = mapOf("_SLEEP" to ExperimentMetric.SLEEP, "_CHARGE" to ExperimentMetric.CHARGE, "_PULSE" to ExperimentMetric.PULSE)
    private val FOOD = setOf("SWEETS", "COFFEE", "ALCOHOL", "MEAL", "PROTEIN", "WEIGHT")

    /** A pattern becomes a seed when it names an action and a measure HoT can count. */
    fun seedOf(p: Pattern): ExperimentSeed? {
        val action = p.action ?: return null
        val key = p.id.uppercase()
        val metric = METRIC_BY_SUFFIX.entries.firstOrNull { key.endsWith(it.key) }?.value ?: return null
        return ExperimentSeed(
            id = "exp-" + p.id.lowercase(),
            hypothesis = WeekCopy.expHypothesis(action, metric),
            action = action,
            metric = metric,
            food = FOOD.any { key.startsWith(it) },
        )
    }

    /**
     * The offers of Sunday, best first (at most 3, the first is the one HoT proposes): the lever of
     * the goal of «Путь», then the patterns by strength. Empty when the gate is closed.
     */
    fun offers(patterns: List<Pattern>, pathLever: ExperimentSeed?, gate: ExperimentGate, previous: ExperimentSeed? = null): List<ExperimentSeed> {
        if (!gate.open) return emptyList()
        val fromPatterns = PatternRules.select(patterns).mapNotNull { seedOf(it.pattern) }
        return (listOfNotNull(pathLever) + fromPatterns)
            .filter { !(gate.edCare && it.food) }
            .distinctBy { it.id }
            // the same question twice in a row only when the person asks («повторим?»)
            .sortedBy { if (it.id == previous?.id) 1 else 0 }
            .take(3)
    }

    /** Monday to Saturday of the week, each true / false / null (not yet or future). */
    fun scale(weekStart: LocalDate, today: LocalDate, did: Set<LocalDate>): ExperimentScale {
        val days = (0 until DAYS).map { i ->
            val d = weekStart.plusDays(i.toLong())
            when {
                d in did -> true
                d.isBefore(today) -> false
                else -> null
            }
        }
        return ExperimentScale(done = did.count { !it.isBefore(weekStart) && it.isBefore(weekStart.plusDays(DAYS.toLong())) }, goal = MIN_POINTS, days = days)
    }

    /**
     * The answer of the next Sunday: the mean of the measure on the days with the action against
     * the days without it (this week and the one before). Too few points → «Данных мало — повторим?».
     * Every outcome is something learned.
     */
    fun result(seed: ExperimentSeed, points: List<ExperimentPoint>): ExperimentResult {
        val withAction = points.filter { it.did && it.value != null }
        val without = points.filter { !it.did && it.value != null }
        if (withAction.size < MIN_POINTS || without.size < MIN_BASELINE) {
            return ExperimentResult(ExperimentOutcome.TOO_FEW, WeekCopy.EXP_LEARNED, WeekCopy.EXP_TOO_FEW, withAction.size)
        }
        val shift = withAction.mapNotNull { it.value }.average() - without.mapNotNull { it.value }.average()
        val m = seed.metric
        val better = if (m.higherIsBetter) shift else -shift
        val days = withAction.count { p -> val v = p.value!!; if (m.higherIsBetter) v > without.mapNotNull { it.value }.average() else v < without.mapNotNull { it.value }.average() }
        val outcome = when {
            abs(shift) < m.minShift -> ExperimentOutcome.NO_LINK
            better > 0 -> ExperimentOutcome.YES
            else -> ExperimentOutcome.OPPOSITE
        }
        val text = when (outcome) {
            ExperimentOutcome.YES -> WeekCopy.expYes(signed(shift, m), days, withAction.size)
            ExperimentOutcome.OPPOSITE -> WeekCopy.expOpposite(m)
            ExperimentOutcome.NO_LINK -> WeekCopy.EXP_NO_LINK
            ExperimentOutcome.TOO_FEW -> WeekCopy.EXP_TOO_FEW
        }
        return ExperimentResult(outcome, WeekCopy.EXP_LEARNED, text, withAction.size)
    }

    /** «+18 мин», «−3 уд/мин» with a real minus. */
    fun signed(shift: Double, m: ExperimentMetric): String {
        val v = shift.roundToInt()
        val sign = if (v > 0) "+" else if (v < 0) "−" else ""
        return "$sign${abs(v)} ${m.unit}"
    }

    /** The experiment is answered in the ritual that closes its week (Sunday) or later. */
    fun answerable(weekStart: LocalDate, today: LocalDate): Boolean =
        !today.isBefore(weekStart.with(DayOfWeek.SUNDAY))
}

/**
 * What «Неделя» says to «Путь» on Sunday (A57 §4.3): the Sculpture was revealed — its lights fly
 * to the details of «Срез», the Titan of the horizon takes a step. «Путь» (claude/path-v1) listens.
 */
data class SculptureRevealed(val weekStart: LocalDate, val daysInSupport: Int, val lights: Int, val experiment: ExperimentResult?)
