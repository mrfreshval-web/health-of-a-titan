package com.healthofatitan.app.domain.today

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** «День» (A66 §3.5, §3.8) and the conditions of Agent 3 (2026-11-08). */
class DayRulesTest {

    private val base = DayInput(scenario = Scenario.DAY, mode = DayMode.ROVNO, whoopConnected = true)

    private fun load(i: DayInput) = DayRules.build(i).rows.first { it.id == DayRowId.LOAD }
    private fun row(i: DayInput, id: DayRowId) = DayRules.build(i).rows.firstOrNull { it.id == id }

    @Test
    fun `three rows in their order, the reset line is always in the header`() {
        val v = DayRules.build(base.copy(strain = 9.8))
        assertTrue(v.shown)
        assertEquals(listOf(DayRowId.LOAD, DayRowId.FOOD, DayRowId.REST), v.rows.map { it.id })
        assertEquals("обнулится после сна", DayCopy.RESETS)
    }

    @Test
    fun `the load is the strain to the corridor of the mode from the existing table`() {
        val r = load(base.copy(strain = 9.8))
        assertEquals("9,8 из 10–14", r.value)
        assertEquals(DayCopy.toCorridor("0,2"), r.hint)
        assertNotNull(r.bar).let {
            assertEquals(10f / 21f, it.from!!, 0.001f)
            assertEquals(14f / 21f, it.to!!, 0.001f)
        }
        assertEquals(0.98f, r.fill, 0.001f)
        assertEquals(DayCopy.IN_CORRIDOR, load(base.copy(strain = 12.0)).hint)
        assertEquals(DayCopy.ABOVE_CORRIDOR, load(base.copy(strain = 15.1)).hint)
        assertTrue(load(base.copy(strain = 15.1)).bar!!.above)
        assertEquals("16,0 из 14–18", load(base.copy(mode = DayMode.RAZGON, strain = 16.0)).value)
    }

    @Test
    fun `condition 1 - minutes of walking only from five or more own walks`() {
        val four = List(4) { 0.05 }
        assertEquals(DayCopy.toCorridor("2,0"), load(base.copy(strain = 8.0, walkStrainPerMinute = four)).hint)
        val five = List(5) { 0.1 }
        assertEquals(DayCopy.toCorridorWalk(20), load(base.copy(strain = 8.0, walkStrainPerMinute = five)).hint)
        assertNull(DayRules.walkMinutes(2.0, four))
        assertNull(DayRules.walkMinutes(2.0, List(5) { 0.0 }), "a walk without strain is not a walk to count")
        assertEquals(5, DayRules.walkMinutes(0.1, five))
    }

    @Test
    fun `condition 2 - without WHOOP no strain corridor, only minutes of activity as an estimate`() {
        val noWhoop = base.copy(whoopConnected = false, scenario = Scenario.SELF, strain = 9.8, activityMinutes = 40)
        val r = load(noWhoop)
        assertEquals(DayCopy.activity(40), r.value)
        assertTrue(r.estimate)
        assertNull(r.bar)
        assertFalse(r.counted, "minutes have no corridor yet: they do not count in the share")
        assertTrue(r.hint == null || "коридор" !in r.hint!!)
        val nothing = load(noWhoop.copy(activityMinutes = null))
        assertEquals(DayCopy.NO_VALUE, nothing.value)
        assertTrue(nothing.estimate)
    }

    @Test
    fun `condition 3 - gentle, sickness, storm and red flags never say how much is left`() {
        val gentle = load(base.copy(mode = DayMode.BEREZHNO, scenario = Scenario.GENTLE, strain = 3.0, walkStrainPerMinute = List(6) { 0.1 }))
        assertEquals(DayCopy.BY_FEEL, gentle.hint)
        assertEquals("3,0 из 6–10", gentle.value, "Бережно keeps its lowered corridor (A66 §3.8)")
        val red = load(base.copy(strain = 3.0, redFlag = true))
        assertEquals(DayCopy.BY_FEEL, red.hint)
        assertEquals("3,0", red.value)
        assertNull(red.bar!!.from)
        assertFalse(red.counted)
        val noWhoopGentle = load(base.copy(whoopConnected = false, mode = DayMode.BEREZHNO, scenario = Scenario.GENTLE))
        assertEquals(DayCopy.BY_FEEL, noWhoopGentle.hint)
        listOf(gentle, red, noWhoopGentle).forEach { assertFalse("до коридора" in it.spoken, it.spoken) }
    }

    @Test
    fun `gentle puts rest first`() {
        val v = DayRules.build(base.copy(mode = DayMode.BEREZHNO, scenario = Scenario.GENTLE, strain = 4.0))
        assertEquals(listOf(DayRowId.REST, DayRowId.LOAD, DayRowId.FOOD), v.rows.map { it.id })
    }

    @Test
    fun `sickness leaves only rest, counted, and the drink hint`() {
        val v = DayRules.build(base.copy(sick = true, strain = 9.0, water = 2))
        assertEquals(listOf(DayRowId.REST), v.rows.map { it.id })
        assertEquals(1f, v.share)
        assertEquals(DayCopy.drink(2), v.drink)
        assertNull(v.nextMeal)
    }

    @Test
    fun `the sober screen hides the block`() {
        Safety.entries.filter { it.sober }.forEach { s ->
            assertFalse(DayRules.build(base.copy(scenario = Scenario.SAFETY, safety = s)).shown)
        }
    }

    @Test
    fun `ed care has no food row and the share is by two rows`() {
        val ed = TodayFood(meals = listOf(FoodMeal("lunch", "Обед")), hideCounts = true, quiet = true)
        val v = DayRules.build(base.copy(strain = 10.0, food = ed, dayClosed = true, mealsMarked = 0, nextMeal = "lunch"))
        assertEquals(listOf(DayRowId.LOAD, DayRowId.REST), v.rows.map { it.id })
        assertEquals(1f, v.share, 0.001f)
        assertNull(v.nextMeal, "no «+ Еда» without the food row")
        assertTrue(v.rows.none { "приём" in it.spoken })
    }

    @Test
    fun `food counts the plan's meals with honest plurals and no total on return`() {
        val plan = TodayFood(meals = listOf(FoodMeal("b", "Завтрак"), FoodMeal("l", "Обед"), FoodMeal("s", "Перекус", optional = true), FoodMeal("d", "Ужин")))
        val f = row(base.copy(food = plan, mealsMarked = 2), DayRowId.FOOD)!!
        assertEquals("2 из 3 приёмов", f.value)
        assertEquals(listOf(DaySeg.DONE, DaySeg.DONE, DaySeg.OPEN), f.segments)
        assertEquals(DayTap.NUTRITION, f.tap)
        assertEquals("2 из 4 приёмов", row(base.copy(mealsMarked = 2), DayRowId.FOOD)!!.value)
        assertEquals("1 из 1 приёма", DayCopy.meals(1, 1))
        assertEquals("2 приёма", row(base.copy(scenario = Scenario.RETURN, mealsMarked = 2), DayRowId.FOOD)!!.value)
        assertEquals("9,8", load(base.copy(scenario = Scenario.RETURN, strain = 9.8)).value)
    }

    @Test
    fun `rest counts what is recorded, sleep counts in the morning, nothing restores`() {
        val open = row(base.copy(screensFromMin = 22 * 60, bedtimeMin = 23 * 60 + 5), DayRowId.REST)!!
        assertEquals(listOf(DaySeg.UNKNOWN, DaySeg.OPEN, DaySeg.LATER), open.segments)
        assertNull(open.value, "nothing done: no caption, not a reproach")
        assertEquals("экраны с 22:00 · отбой 23:05", open.hint)
        assertEquals(0f, open.fill)
        assertEquals(DayTap.BREATHER, open.tap)
        val closed = row(base.copy(dayClosed = true, breatherDone = true), DayRowId.REST)!!
        assertEquals(DayCopy.DAY_CLOSED, closed.value)
        assertEquals("Отдых, передышка, день закрыт", closed.spoken)
        assertEquals(1f, closed.fill)
        assertEquals(DayTap.CLOSE_DAY, row(base.copy(closeDue = true), DayRowId.REST)!!.tap)
        assertEquals(DayTap.BREATHER, row(base.copy(closeDue = true, dayClosed = true), DayRowId.REST)!!.tap)
        assertNull(row(base, DayRowId.REST)!!.hint, "no plan, no windows")
    }

    @Test
    fun `the load row opens the training of the day or why`() {
        assertEquals(DayTap.TRAINING, load(base.copy(trainingToday = true)).tap)
        assertEquals(DayTap.WHY, load(base).tap)
    }

    @Test
    fun `the share is the mean of the counted rows`() {
        val v = DayRules.build(base.copy(strain = 10.0, mealsMarked = 2, dayClosed = true))
        // load 1, food 0.5, rest 1 (only «Закрыть день» is recorded)
        assertEquals(2.5f / 3f, v.share, 0.001f)
        assertEquals(0f, DayRules.share(emptyList()))
        val pending = DayRules.build(base.copy(strain = null))
        assertTrue(pending.rows.first { it.id == DayRowId.LOAD }.counted)
        assertEquals(DayCopy.NO_VALUE, pending.rows.first { it.id == DayRowId.LOAD }.value)
    }

    @Test
    fun `the drink hint is not counted and says no prescription`() {
        assertEquals(DayCopy.DRINK_BY_THIRST, DayRules.build(base).drink)
        assertEquals("питьё: 1 стакан", DayCopy.drink(1))
        assertEquals("питьё: 5 стаканов", DayCopy.drink(5))
    }

    @Test
    fun `T-13 the words of the day pass the stop dictionary and never promise to restore`() {
        val generated = Scenario.entries.flatMap { s ->
            listOf(true, false).flatMap { w ->
                DayRules.build(base.copy(scenario = s, whoopConnected = w, strain = 8.0, dayClosed = true, breatherDone = true, sick = s == Scenario.GENTLE))
                    .rows.flatMap { listOfNotNull(it.spoken, it.value, it.hint) }
            }
        }
        (DayCopy.all() + generated).forEach { text ->
            assertEquals(emptyList(), StopWords.hits(text), "«$text»")
            assertFalse("восстан" in text.lowercase(), "«$text»")
        }
    }
}
