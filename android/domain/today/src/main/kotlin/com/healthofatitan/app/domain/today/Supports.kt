package com.healthofatitan.app.domain.today

import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Norms of the five supports and the water formula in one place (A46 §2.3).
 *
 * **Ждёт Агентов 12 и 3.** The numbers are the working draft of A46 v0.3 and its prototype: the
 * table of effort corridors is Agent 12's, the water formula Agent 3's. Nothing else in the app
 * holds a norm of a support; change them here only.
 */
object SupportNorms {
    const val REVIEW = "ждёт Агентов 12 и 3"

    /** Effort corridor of the day strain (WHOOP 0–21) per mode; A46: «например, Ровно 10–14». */
    fun effortCorridor(mode: DayMode?): ClosedFloatingPointRange<Double> = when (mode) {
        DayMode.RAZGON -> 14.0..18.0
        DayMode.BEREZHNO -> 6.0..10.0
        DayMode.ROVNO, null -> 10.0..14.0
    }

    /** Steps: 8–10 thousand before 60, 6–8 thousand after (Paluch 2022); the middle as the goal. */
    fun stepsGoal(ageYears: Int?): Int = if (ageYears != null && ageYears >= 60) 7_000 else 9_000

    /** An hour «with movement»: ≥ 250 steps in it (A46 §2.3). */
    const val MOVING_HOUR_STEPS = 250

    /** Waking hours counted for «часы с движением» («7 из 12»). */
    const val MOVING_HOURS_GOAL = 12

    /** Meals of a day plan (A41) until the plan of the day says otherwise. */
    const val MEALS_DEFAULT = 4

    const val GLASS_ML = 250

    /**
     * Glasses of water for the day: 30 ml per kg (2 l without a weight) plus a little for a hard day.
     * An estimate (`оценка`), never a prescription; Agent 3 owns the formula.
     */
    fun waterGlasses(weightKg: Double?, strain: Double?): Int {
        val base = weightKg?.takeIf { it in 30.0..250.0 }?.let { it * 30.0 } ?: 2_000.0
        val extra = when {
            strain == null -> 0.0
            strain >= 14.0 -> 500.0
            strain >= 10.0 -> 250.0
            else -> 0.0
        }
        return ceil((base + extra) / GLASS_ML).toInt().coerceIn(6, 14)
    }

    /** Default windows of sleep preparation when the plan has none (A46 §2.3, A51 §3.6). */
    const val CAFFEINE_UNTIL_MIN = 14 * 60
    const val TRAINING_UNTIL_MIN = 20 * 60
    const val DINNER_UNTIL_MIN = 21 * 60
    const val SCREENS_FROM_MIN = 22 * 60

    /** Share of the norm from which a support counts as filled for the day score (A52 §2.1). */
    const val FILLED = 0.7
    const val FILLED_GENTLE = 0.5
}

enum class SupportId(val key: String) {
    EFFORT("effort"),
    MOVEMENT("movement"),
    NUTRITION("nutrition"),
    WATER("water"),
    SLEEP_TONIGHT("sleep"),
    ;

    companion object {
        fun of(key: String?): SupportId? = entries.firstOrNull { it.key == key }
    }
}

/**
 * The tone of a column. There is no alarm tone at all (T-04): a support is never red; a deviation
 * is a quiet note with its consequence.
 */
enum class SupportTone { CALM, LIT, NOTE }

data class SupportView(
    val id: SupportId,
    val label: String,
    /** The number under the column, one line (T-10). */
    val value: String,
    val caption: String?,
    /** 0…1, how full the column is. */
    val fill: Float,
    /** Where the number comes from (T-11). */
    val source: String,
    /** The number is an estimate (T-11: «оценка»). */
    val estimate: Boolean,
    /** No data yet: a dashed column. */
    val pending: Boolean,
    val tone: SupportTone,
    val spoken: String,
)

/** The five sleep-preparation conditions of «сон сегодня». */
data class SleepPrep(
    val caffeineUntilMin: Int = SupportNorms.CAFFEINE_UNTIL_MIN,
    val trainingUntilMin: Int = SupportNorms.TRAINING_UNTIL_MIN,
    val dinnerUntilMin: Int = SupportNorms.DINNER_UNTIL_MIN,
    val screensFromMin: Int = SupportNorms.SCREENS_FROM_MIN,
    val bedtimeMin: Int? = null,
) {
    /**
     * Conditions held so far, an estimate: a window counts once its time has passed without a mark
     * against it (a workout ending later, a meal marked later). Screens are counted from their hour.
     */
    fun held(nowMin: Int, lastWorkoutEndMin: Int?, lastMealMin: Int?): Int {
        var n = 0
        if (nowMin >= caffeineUntilMin) n++
        if (nowMin >= trainingUntilMin && (lastWorkoutEndMin == null || lastWorkoutEndMin <= trainingUntilMin)) n++
        if (nowMin >= dinnerUntilMin && (lastMealMin == null || lastMealMin <= dinnerUntilMin)) n++
        if (nowMin >= screensFromMin) n++
        return n
    }

    companion object {
        const val TOTAL = 4
    }
}

data class SupportsInput(
    val mode: DayMode?,
    val scenario: Scenario,
    val nowMin: Int,
    /** WHOOP `cycle.score.strain`, memory only. */
    val strain: Double? = null,
    /** WHOOP `cycle.score.kilojoule`, memory only. */
    val kilojoule: Double? = null,
    /** WHOOP `cycle.step_count` (since 23.09.2026), memory only. */
    val whoopSteps: Int? = null,
    /** Phone steps of today from Health Connect, memory only. */
    val phoneSteps: Int? = null,
    /** Steps per hour of today from Health Connect (24 values), when available. */
    val hourlySteps: List<Int>? = null,
    val ageYears: Int? = null,
    val weightKg: Double? = null,
    val mealsMarked: Int = 0,
    val mealsPlanned: Int = SupportNorms.MEALS_DEFAULT,
    val water: Int = 0,
    val sleep: SleepPrep = SleepPrep(),
    val lastWorkoutEndMin: Int? = null,
    val lastMealMin: Int? = null,
)

object Supports {

    fun kcal(kilojoule: Double): Int = (kilojoule / 4.184).roundToInt()

    fun movingHours(hourly: List<Int>?): Int? = hourly?.count { it >= SupportNorms.MOVING_HOUR_STEPS }

    fun steps(i: SupportsInput): Int? = i.whoopSteps ?: i.phoneSteps

    private fun spaced(n: Int): String {
        val s = n.toString()
        return if (s.length <= 3) s else s.dropLast(3) + " " + s.takeLast(3)
    }

    private fun one(d: Double): String = String.format(java.util.Locale.ROOT, "%.1f", d).replace('.', ',')

    private fun range(r: ClosedFloatingPointRange<Double>): String = "${r.start.roundToInt()}–${r.endInclusive.roundToInt()}"

    /** The five columns in their order (A46 §2.3). Never a red tone (T-04), always a source (T-11). */
    fun build(i: SupportsInput): List<SupportView> {
        // No WHOOP (4.1, 4.1a): no strain; steps only from the phone's Health Connect.
        val noData = i.scenario == Scenario.NO_SOURCE || i.scenario == Scenario.SELF
        val corridor = SupportNorms.effortCorridor(i.mode)
        val effortHidden = i.scenario == Scenario.SAFETY
        val strain = i.strain?.takeIf { !effortHidden && !noData }
        val effort = SupportView(
            id = SupportId.EFFORT,
            label = TodayCopy.EFFORT,
            value = strain?.let(::one) ?: "—",
            caption = if (effortHidden) null else range(corridor),
            fill = strain?.let { (it / corridor.endInclusive).toFloat().coerceIn(0f, 1f) } ?: 0f,
            source = if (effortHidden) TodayCopy.EFFORT_HIDDEN else TodayCopy.SOURCE_WHOOP_CYCLE,
            estimate = false,
            pending = strain == null,
            tone = if (strain != null && strain > corridor.endInclusive) SupportTone.NOTE else if (strain != null) SupportTone.LIT else SupportTone.CALM,
            spoken = "${TodayCopy.EFFORT}: " + (strain?.let { "${one(it)}, коридор ${range(corridor)}" } ?: TodayCopy.NO_DATA) +
                (i.kilojoule?.takeIf { strain != null }?.let { ", ${kcal(it)} ккал" } ?: ""),
        )

        val steps = steps(i).takeIf { !noData || i.phoneSteps != null }
        val goal = SupportNorms.stepsGoal(i.ageYears)
        val hours = movingHours(i.hourlySteps)
        val movement = SupportView(
            id = SupportId.MOVEMENT,
            label = TodayCopy.MOVEMENT,
            value = steps?.let(::spaced) ?: "—",
            caption = hours?.let { "$it/${SupportNorms.MOVING_HOURS_GOAL} ч" },
            fill = steps?.let { (it.toFloat() / goal).coerceIn(0f, 1f) } ?: 0f,
            source = if (i.whoopSteps != null || i.hourlySteps == null) TodayCopy.SOURCE_STEPS else TodayCopy.SOURCE_STEPS_PHONE,
            estimate = false,
            pending = steps == null,
            tone = if (steps != null && steps >= goal) SupportTone.LIT else SupportTone.CALM,
            spoken = "${TodayCopy.MOVEMENT}: " + (steps?.let { "$it шагов из $goal" } ?: TodayCopy.NO_DATA) +
                (hours?.let { ", часов с движением $it из ${SupportNorms.MOVING_HOURS_GOAL}" } ?: ""),
        )

        val planned = i.mealsPlanned.coerceAtLeast(1)
        val meals = i.mealsMarked.coerceIn(0, planned)
        val nutrition = SupportView(
            id = SupportId.NUTRITION,
            label = TodayCopy.NUTRITION,
            value = TodayCopy.ofTotal(meals, planned),
            caption = null,
            fill = meals.toFloat() / planned,
            source = TodayCopy.SOURCE_MEALS,
            estimate = false,
            pending = false,
            tone = if (meals > 0) SupportTone.LIT else SupportTone.CALM,
            spoken = "${TodayCopy.NUTRITION}: ${TodayCopy.ofTotal(meals, planned)} приёмов",
        )

        val norm = SupportNorms.waterGlasses(i.weightKg, i.strain)
        val water = SupportView(
            id = SupportId.WATER,
            label = TodayCopy.WATER,
            value = "${i.water} / $norm",
            caption = null,
            fill = (i.water.toFloat() / norm).coerceIn(0f, 1f),
            source = TodayCopy.SOURCE_WATER,
            estimate = true,
            pending = false,
            tone = if (i.water > 0) SupportTone.LIT else SupportTone.CALM,
            spoken = "${TodayCopy.WATER}: ${i.water} из $norm стаканов",
        )

        val held = i.sleep.held(i.nowMin, i.lastWorkoutEndMin, i.lastMealMin)
        val sleep = SupportView(
            id = SupportId.SLEEP_TONIGHT,
            label = TodayCopy.SLEEP_TONIGHT,
            value = TodayCopy.ofTotal(held, SleepPrep.TOTAL),
            // the bedtime alone: «отбой 23:00» does not fit a fifth of 412 dp (T-10); the word is in «Ритм дня»
            caption = i.sleep.bedtimeMin?.let(::clock),
            fill = held.toFloat() / SleepPrep.TOTAL,
            source = TodayCopy.SOURCE_SLEEP_PREP,
            estimate = true,
            pending = false,
            tone = if (held > 0) SupportTone.LIT else SupportTone.CALM,
            spoken = "${TodayCopy.SLEEP_TONIGHT}: ${TodayCopy.ofTotal(held, SleepPrep.TOTAL)} условий",
        )
        return listOf(effort, movement, nutrition, water, sleep)
    }

    fun clock(min: Int): String {
        val m = ((min % 1440) + 1440) % 1440
        return "${(m / 60).toString().padStart(2, '0')}:${(m % 60).toString().padStart(2, '0')}"
    }
}
