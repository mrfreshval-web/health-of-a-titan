package com.healthofatitan.app.domain.today.week

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * «Фитнес-возраст» и темп (A52 §2.8, D-045): the non-exercise model of cardiorespiratory fitness of
 * the HUNT study (Nes et al., Med Sci Sports Exerc 2011;43:2024–30). Behind [WeekFlags.FITNESS_AGE],
 * off by default: the wording and the exclusions wait for Agents 3 and 10.
 *
 * **Ждёт сверки Агентом 3 с первоисточником:** the coefficients below are the published ones as the
 * team knows them; the norm table for the fitness age (the age for which an estimate is the
 * average of healthy HUNT participants; Nes et al. 2014) is a working draft of Agent 18.
 *
 * It is an estimate of cardio fitness, never «биологический возраст», never a diagnosis, never a
 * comparison with other people.
 */
enum class Sex { MALE, FEMALE }

/** Physical activity of the HUNT questionnaire, read from WHOOP workouts instead of a survey. */
data class Activity(
    /** Workouts per week, mean over the last 4 weeks. */
    val perWeek: Double,
    /** Mean minutes of a workout. */
    val meanMinutes: Double,
    /** Share of the workout time in heart-rate zones 4–5 and in 2–5 (WHOOP `zone_durations`). */
    val hardShare: Double,
    val moderateShare: Double,
)

data class FitnessInput(
    val ageYears: Int?,
    val sex: Sex?,
    val waistCm: Double?,
    /** Resting heart rate: mean of the last 7 WHOOP recoveries (memory only). */
    val restingHr: Double?,
    val activity: Activity?,
)

/** Why the number is not shown (A52 §2.8 conditions of Agent 3, A53 §3.3). */
data class FitnessGate(
    val flagOn: Boolean = WeekFlags.FITNESS_AGE,
    val sick: Boolean = false,
    val pregnancy: Boolean = false,
    val betaBlockers: Boolean = false,
    val edCare: Boolean = false,
    val stormNet: Boolean = false,
    /** A53 §3.3: only by the person's choice, after the screen «что это и чего не значит». */
    val optedIn: Boolean = false,
    /** The flag of health anxiety (A41): hidden by default. */
    val healthAnxiety: Boolean = false,
    /** Weeks with data so far; the first milestone comes after 4. */
    val weeksOfData: Int = 0,
) {
    /** Nothing closes it: the opt-in may be offered. */
    val offerable: Boolean get() = flagOn && !sick && !pregnancy && !betaBlockers && !edCare && !stormNet && !healthAnxiety
    val open: Boolean get() = offerable && optedIn && weeksOfData >= FitnessMilestones.EVERY
}

/** What a milestone keeps to tell the lever 4 weeks later (HoT-derived: the estimate and the PA index; the waist is typed). */
data class MilestoneInputs(val vo2: Double, val pa: Double, val waistCm: Double)

/**
 * A53 §3.3, D-050 (changes D-045 п. 2): the fitness age is not the number of the week — it is
 * updated at milestones every 4 weeks (where the waist is measured) and shown in «Тело за неделю»;
 * never in the push, the card or the first frame of the ritual. The main thing is the pace and the lever.
 */
object FitnessMilestones {
    const val EVERY = 4

    /** The milestone this week closes (1, 2, …) — the 4th, 8th… week since the first plan — or null. */
    fun number(firstWeek: LocalDate?, weekStart: LocalDate): Int? {
        firstWeek ?: return null
        val weeks = ChronoUnit.WEEKS.between(firstWeek, weekStart).toInt() + 1
        return if (weeks >= EVERY && weeks % EVERY == 0) weeks / EVERY else null
    }

    /**
     * «помогли: пульс покоя» — the input whose change raised the estimate most since the last
     * milestone: activity (PA index), waist, and the rest is the resting pulse. Null when nothing helped.
     */
    fun lever(sex: Sex, before: MilestoneInputs, now: MilestoneInputs): String? {
        val (paK, waistK) = when (sex) {
            Sex.MALE -> 0.226 to 0.369
            Sex.FEMALE -> 0.198 to 0.259
        }
        val activity = paK * (now.pa - before.pa)
        val waist = -waistK * (now.waistCm - before.waistCm)
        val pulse = (now.vo2 - before.vo2) - activity - waist
        val best = listOf(WeekCopy.FIT_LEVER_ACTIVITY to activity, WeekCopy.FIT_LEVER_WAIST to waist, WeekCopy.FIT_LEVER_RHR to pulse)
            .maxBy { it.second }
        return if (best.second > LEVER_MIN_VO2) WeekCopy.fitLever(best.first) else null
    }

    const val LEVER_MIN_VO2 = 0.2
}

data class FitnessView(
    val age: Int?,
    /** «за 4 недели −0,6 года»; null until there is an estimate 4 weeks back. */
    val pace: String?,
    val paceYears: Double?,
    val note: String,
    val drivers: String,
    /** Above the passport age: a gentle line instead of a number comparison (Agent 10). */
    val room: String?,
    /** What is missing for an estimate. */
    val missing: String?,
    val waistDue: Boolean,
    /** «39–43»: the model gives no more than ±2 years. */
    val range: String? = null,
    /** «помогли: пульс покоя». */
    val lever: String? = null,
    /** «веха 2». */
    val milestone: String? = null,
)

object FitnessAge {
    /** HUNT frequency score: never / < 1 a week 0; once 1; 2–3 times 2.5; almost every day 5. */
    fun frequencyScore(perWeek: Double): Double = when {
        perWeek < 0.75 -> 0.0
        perWeek < 1.75 -> 1.0
        perWeek < 4.0 -> 2.5
        else -> 5.0
    }

    /** HUNT intensity: «take it easy» 1, «heavy breath and sweat» 5, «near exhaustion» 10. */
    fun intensityScore(a: Activity): Double = when {
        a.hardShare >= 0.25 -> 10.0
        a.moderateShare >= 0.5 -> 5.0
        else -> 1.0
    }

    /** HUNT duration: < 15 min 0.1, 15–29 0.38, 30–60 0.75, > 60 1.0. */
    fun durationScore(minutes: Double): Double = when {
        minutes < 15 -> 0.1
        minutes < 30 -> 0.38
        minutes <= 60 -> 0.75
        else -> 1.0
    }

    /** PA index = frequency × intensity × duration, 0…50. */
    fun paIndex(a: Activity?): Double = a?.let { frequencyScore(it.perWeek) * intensityScore(it) * durationScore(it.meanMinutes) } ?: 0.0

    /** Estimated VO₂peak, ml/kg/min (Nes 2011). */
    fun vo2(sex: Sex, age: Int, waistCm: Double, restingHr: Double, pa: Double): Double = when (sex) {
        Sex.MALE -> 100.27 - 0.296 * age - 0.369 * waistCm - 0.155 * restingHr + 0.226 * pa
        Sex.FEMALE -> 74.74 - 0.247 * age - 0.259 * waistCm - 0.114 * restingHr + 0.198 * pa
    }

    /** Mean VO₂peak of healthy HUNT participants by age (draft of Agent 18, to be checked by Agent 3). */
    val NORM: Map<Sex, List<Pair<Double, Double>>> = mapOf(
        Sex.MALE to listOf(25.0 to 54.4, 35.0 to 49.4, 45.0 to 46.0, 55.0 to 41.6, 65.0 to 37.0, 75.0 to 33.0),
        Sex.FEMALE to listOf(25.0 to 43.0, 35.0 to 40.0, 45.0 to 37.0, 55.0 to 33.6, 65.0 to 30.0, 75.0 to 27.0),
    )

    const val MIN_AGE = 18.0
    const val MAX_AGE = 90.0

    /** The age at which [vo2] is the norm: the norm falls with age, so the inverse is a line between points. */
    fun ageFor(sex: Sex, vo2: Double): Double {
        val pts = NORM.getValue(sex)
        val seg = pts.zipWithNext().firstOrNull { (a, b) -> vo2 <= a.second && vo2 >= b.second }
            ?: if (vo2 > pts.first().second) pts[0] to pts[1] else pts[pts.size - 2] to pts.last()
        val (a, b) = seg
        val t = (vo2 - a.second) / (b.second - a.second)
        return (a.first + t * (b.first - a.first)).coerceIn(MIN_AGE, MAX_AGE)
    }

    /** The fitness age with one decimal; null when an input is missing. */
    fun estimate(i: FitnessInput): Double? {
        val age = i.ageYears ?: return null
        // TODO(A61 §2.2, D-055 п. 3, ⚑ Agent 3): without the sex for calculations the fitness age
        //  is to be shown as a range over both HUNT formulas, marked «оценка» — only after Agent 3
        //  checks it. Until then no sex means no estimate (FIT_NEED_INPUT).
        val sex = i.sex ?: return null
        val waist = i.waistCm?.takeIf { it in 40.0..200.0 } ?: return null
        val rhr = i.restingHr?.takeIf { it in 25.0..140.0 } ?: return null
        return (ageFor(sex, vo2(sex, age, waist, rhr, paIndex(i.activity))) * 10).roundToInt() / 10.0
    }

    /** «−0,6 года» with a real minus. */
    fun paceText(years: Double): String {
        val sign = when {
            years < -0.05 -> "−"
            years > 0.05 -> "+"
            else -> ""
        }
        return sign + String.format(Locale.ROOT, "%.1f", abs(years)).replace('.', ',') + " года"
    }

    const val WAIST_DAYS = 28

    /**
     * The view of the week: null when the gate is closed. The number is a whole year (the model
     * gives no more precision than that); the pace is the difference to the estimate 4 weeks back.
     */
    fun view(
        gate: FitnessGate,
        input: FitnessInput,
        fourWeeksAgo: Double?,
        waistAgeDays: Int?,
        /** The estimate of the last milestone: between milestones the number does not move. */
        atMilestone: Double? = null,
        milestone: Int? = null,
        lever: String? = null,
    ): FitnessView? {
        if (!gate.open) return null
        val now = atMilestone ?: estimate(input)
        val missing = if (input.ageYears == null || input.sex == null || input.waistCm == null) WeekCopy.FIT_NEED_INPUT else null
        val pace = if (now != null && fourWeeksAgo != null) now - fourWeeksAgo else null
        return FitnessView(
            age = now?.roundToInt(),
            pace = pace?.let { WeekCopy.fitPace(paceText(it)) },
            paceYears = pace,
            note = WeekCopy.FIT_NOTE,
            drivers = WeekCopy.FIT_DRIVERS,
            room = if (now != null && input.ageYears != null && now.roundToInt() > input.ageYears) WeekCopy.FIT_ROOM else null,
            missing = missing,
            waistDue = waistAgeDays == null || waistAgeDays >= WAIST_DAYS,
            range = now?.roundToInt()?.let(WeekCopy::fitRange),
            lever = lever,
            milestone = milestone?.let(WeekCopy::fitMilestone),
        )
    }
}
