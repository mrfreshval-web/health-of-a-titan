package com.healthofatitan.app.domain.today

/** One meal of the food plan as «Сегодня» shows it (A41 §10): the plan's slot id and its word. */
data class FoodMeal(
    val slot: String,
    val title: String,
    /** Minutes from 00:00; null when the food windows are hidden. */
    val timeMin: Int? = null,
    /** A situational slot (a snack): not counted by the «питание» support. */
    val optional: Boolean = false,
)

/**
 * The food of the day for «Сегодня» (A41 §10, audit 2026-11-08 S3): the plate's name, the meals of
 * `NutritionDayPlan` and what the safety bus hides (A60 §3.4). The nutrition module builds it;
 * this module holds no food rule, only how «Сегодня» shows it.
 */
data class TodayFood(
    /** The name of the day's plate («Рабочий день»); null while there is no plan. */
    val plate: String? = null,
    val meals: List<FoodMeal> = emptyList(),
    /** `HIDE_FOOD_WINDOWS` or `HIDE_FOOD_TALK`: no clock time of a meal, no «ужин до». */
    val hideTimes: Boolean = false,
    /** `ED_CARE` (`HIDE_FOOD_TALK` or `HIDE_CALORIES`): no «N из M» on the «питание» support. */
    val hideCounts: Boolean = false,
    /** `HIDE_CALORIES`: no kcal in «усердие». */
    val hideCalories: Boolean = false,
    /** `HIDE_FOOD_TALK`: no plate line on «Сегодня» and no list of meals. */
    val quiet: Boolean = false,
) {
    /** The main (not situational) meals of the plan. */
    val main: List<FoodMeal> get() = meals.filterNot { it.optional }

    /** How many meals the «питание» support counts: the main slots of the plan; null without a plan. */
    val mealsPlanned: Int? get() = main.size.takeIf { it > 0 }

    /** Marked meals that the support counts: only the main slots of the plan. */
    fun marked(slots: Set<String>): Int = if (mealsPlanned == null) slots.size else main.count { it.slot in slots }

    /**
     * The meal the compact line names at [nowMin]: by the clock the first main meal not yet an
     * hour behind; without times the first one not marked. Null when there is nothing to name.
     */
    fun next(nowMin: Int, marked: Set<String>): FoodMeal? {
        val open = main.filter { it.slot !in marked }
        if (open.isEmpty()) return null
        if (hideTimes || open.any { it.timeMin == null }) return open.first()
        return open.firstOrNull { (it.timeMin ?: 0) + NEXT_SLACK_MIN >= nowMin } ?: open.last()
    }

    companion object {
        /** A meal stays «next» for an hour after its time. */
        const val NEXT_SLACK_MIN = 60
    }
}
