package com.healthofatitan.app.domain.today

import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** S3 (audit 2026-11-08, Р-3, Р-16): «питание» counts the plan's main meals; ED_CARE shows no numbers. */
class SupportsTest {

    private val plan = TodayFood(
        plate = "Рабочий день",
        meals = listOf(
            FoodMeal("breakfast", "Завтрак", 8 * 60),
            FoodMeal("lunch", "Обед", 13 * 60),
            FoodMeal("snack", "Перекус", 16 * 60, optional = true),
            FoodMeal("dinner", "Ужин", 20 * 60 + 30),
        ),
    )

    private val edCare = plan.copy(
        meals = plan.meals.map { it.copy(timeMin = null) },
        hideTimes = true,
        hideCounts = true,
        hideCalories = true,
        quiet = true,
    )

    private fun build(food: TodayFood?, marked: Int = 1, sleep: SleepPrep = SleepPrep(bedtimeMin = 23 * 60)) = Supports.build(
        SupportsInput(
            mode = DayMode.ROVNO,
            scenario = Scenario.DAY,
            nowMin = 21 * 60 + 30,
            strain = 12.0,
            kilojoule = 9000.0,
            whoopSteps = 6000,
            mealsMarked = marked,
            water = 2,
            sleep = sleep,
            food = food,
        ),
    )

    private fun nutrition(food: TodayFood?, marked: Int = 1) = build(food, marked).first { it.id == SupportId.NUTRITION }

    @Test
    fun `meals planned are the main slots of the plan, not the constant`() {
        assertEquals(3, plan.mealsPlanned)
        assertEquals("1 из 3", nutrition(plan).value)
        assertEquals("1 из 3 приёмов", nutrition(plan).spoken.substringAfter(": "))
        assertEquals("3 из 3", nutrition(plan, marked = 5).value)
    }

    @Test
    fun `without a plan the support keeps its default`() {
        assertNull(TodayFood().mealsPlanned)
        assertEquals("1 из ${SupportNorms.MEALS_DEFAULT}", nutrition(null).value)
        assertEquals("1 из ${SupportNorms.MEALS_DEFAULT}", nutrition(TodayFood()).value)
    }

    @Test
    fun `only main slots of the plan are counted as marked`() {
        assertEquals(2, plan.marked(setOf("breakfast", "snack", "dinner")))
        assertEquals(2, TodayFood().marked(setOf("breakfast", "snack")))
    }

    @Test
    fun `ed care shows no digits, no count and no calories on the supports of food`() {
        val views = build(edCare, marked = 2)
        val food = views.first { it.id == SupportId.NUTRITION }
        listOf(food.value, food.spoken, food.source).forEach { assertFalse(it.any(Char::isDigit), "«$it»") }
        assertEquals(TodayCopy.NUTRITION_GENTLE, food.value)
        assertEquals(0f, food.fill)
        val effort = views.first { it.id == SupportId.EFFORT }
        assertFalse("ккал" in effort.spoken, effort.spoken)
        assertFalse("калори" in effort.source, effort.source)
        assertTrue(views.none { "ккал" in it.spoken || "калори" in it.source })
    }

    @Test
    fun `without ed care the calories stay`() {
        val effort = build(plan).first { it.id == SupportId.EFFORT }
        assertTrue("ккал" in effort.spoken)
        assertEquals(TodayCopy.SOURCE_WHOOP_CYCLE, effort.source)
    }

    @Test
    fun `hidden food windows drop the dinner condition and its window`() {
        val prep = SleepPrep(bedtimeMin = 23 * 60, foodWindows = false)
        assertEquals(3, prep.total)
        assertEquals(1, prep.held(15 * 60, null, null))
        // a late meal does not count against a window that is not there
        assertEquals(3, prep.held(22 * 60 + 10, 18 * 60, 22 * 60))
        assertEquals("2 из 3", build(edCare, sleep = prep).first { it.id == SupportId.SLEEP_TONIGHT }.value)
        val noon = LocalDate.of(2026, 11, 7).atTime(12, 0)
        val rhythm = RhythmRules.build(RhythmInput(noon, Scenario.DAY, dawnDone = true, sleep = prep))
        assertTrue(rhythm.windows.none { it.kind == BodyWindowKind.DINNER })
        val shown = RhythmRules.build(RhythmInput(noon, Scenario.DAY, dawnDone = true, sleep = prep.copy(foodWindows = true)))
        assertTrue(shown.windows.any { it.kind == BodyWindowKind.DINNER })
    }

    @Test
    fun `the compact line names the next main meal`() {
        assertEquals("lunch", plan.next(12 * 60, emptySet())?.slot)
        assertEquals("breakfast", plan.next(8 * 60 + 30, emptySet())?.slot)
        assertEquals("dinner", plan.next(14 * 60 + 30, emptySet())?.slot)
        assertEquals("dinner", plan.next(23 * 60, emptySet())?.slot)
        assertEquals("lunch", plan.next(8 * 60 + 30, setOf("breakfast"))?.slot)
        assertNull(plan.next(12 * 60, setOf("breakfast", "lunch", "dinner")))
        // without times: the first one not marked
        assertEquals("lunch", edCare.next(8 * 60, setOf("breakfast"))?.slot)
    }
}
