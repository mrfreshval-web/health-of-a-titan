package com.healthofatitan.app.domain.nutrition

import com.healthofatitan.app.core.model.profile.UserConstraints
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A41 §8–§10 scenarios: template by level × load, rules and overlays, slots, safety. */
class NutritionEngineTest {

    private val c = ContentFixture.content
    private val day = LocalDate.of(2026, 11, 10)
    private val nobody = ProfileFold.fold(UserConstraints.NONE, c.profile, consented = true)

    private fun plan(
        level: Level = Level.STRUCTURED,
        profile: FoldedProfile = nobody,
        load: LoadClass = LoadClass.MODERATE,
        start: Int? = 18 * 60,
        minutes: Int? = 60,
        mode: DayMode = DayMode.RAVNO,
        conditions: Set<DayCondition> = emptySet(),
        tomorrow: LoadClass? = null,
        sleep: Double? = 7.5,
        replaced: Map<String, Int> = emptyMap(),
    ) = NutritionEngine.plan(
        NutritionDayInput(
            dayKey = day,
            chosenLevel = level,
            profile = profile,
            mode = mode,
            conditions = conditions,
            loadToday = load,
            loadTomorrow = tomorrow,
            wakeMin = 7 * 60,
            bedtimeMin = 23 * 60,
            trainingStartMin = start,
            trainingMinutes = minutes,
            sleepHours = sleep,
            replaced = replaced,
        ),
        c,
    )

    @Test
    fun `exactly one template per level and load class`() {
        for (level in Level.entries) {
            for (load in LoadClass.entries) {
                assertEquals(1, c.templates.count { it.level == level && load in it.loads }, "${level.code} × ${load.code}")
            }
        }
        assertEquals("TD-base-day", c.template(Level.BASE, LoadClass.MODERATE).id)
        assertEquals("TD-base-training", c.template(Level.BASE, LoadClass.HARD).id)
        assertEquals("TD-str-moderate", c.template(Level.STRUCTURED, LoadClass.MODERATE).id)
        assertEquals("TD-str-hard", c.template(Level.STRUCTURED, LoadClass.LONG).id)
    }

    @Test
    fun `working day on Opora - three meals and the meal after an evening session, every slot filled`() {
        val p = plan()
        assertEquals("TD-str-moderate", p.template.id)
        assertEquals("Рабочий день", p.plateOfDay.ru)
        val ids = p.slots.map { it.slot }
        assertTrue(ids.containsAll(listOf("breakfast", "lunch", "dinner")), "$ids")
        assertTrue(p.slots.any { it.afterTraining || it.slot == "post_training" }, "post-training meal is planned")
        assertEquals(p.slots.mapNotNull { it.timeMin }, p.slots.mapNotNull { it.timeMin }.sorted(), "slots on the line of the day")
        p.slots.filter { it.slot in setOf("breakfast", "lunch", "dinner") }.forEach { assertNotNull(it.meal, it.slot) }
        assertEquals(14 * 60, p.caffeineCutoffMin, "NR-21: bedtime − 9 h")
        assertEquals(p.slots.mapNotNull { it.meal?.meal?.id }.size, p.slots.mapNotNull { it.meal?.meal?.id }.toSet().size, "no repeats in a day")
    }

    @Test
    fun `the same input gives the same plan, a replace tap changes only that slot`() {
        assertEquals(plan(), plan())
        val a = plan()
        val b = plan(replaced = mapOf("lunch" to 1))
        val lunchA = a.slots.first { it.slot == "lunch" }
        val lunchB = b.slots.first { it.slot == "lunch" }
        assertTrue(lunchA.options > 1)
        assertNotEquals(lunchA.meal?.meal?.id, lunchB.meal?.meal?.id)
        assertEquals(a.slots.first { it.slot == "breakfast" }.meal?.meal?.id, b.slots.first { it.slot == "breakfast" }.meal?.meal?.id)
    }

    @Test
    fun `ed_care - Osnova, no numbers anywhere, no methods with numbers`() {
        val ed = ProfileFold.fold(ContentFixture.profile("P-ed").constraints, c.profile, true)
        val p = plan(level = Level.PRECISE, profile = ed, load = LoadClass.HARD)
        assertEquals(Level.BASE, p.level)
        assertTrue(p.lowered)
        assertTrue(p.hideNumbers)
        assertNull(p.caffeineCutoffMin)
        p.slots.forEach {
            assertNull(it.timeMin, "${it.slot} time")
            assertNull(it.hands, "${it.slot} hands")
        }
        p.methods.forEach { assertTrue(c.methods.getValue(it).numbers in setOf("none", "time_only"), it) }
        assertTrue(p.why.any { it.ruleId == "NR-01" })
        assertTrue(p.overlays.any { it.id == "OV-ed-care" })
        assertTrue(p.hints.isNotEmpty(), "H-ED is shown")
    }

    @Test
    fun `energy is never shown - blocked methods and kcal never reach a plan`() {
        for (level in Level.entries) {
            for (load in LoadClass.entries) {
                val p = plan(level = level, load = load)
                assertFalse("energy_reference" in p.methods)
                assertFalse("tre_window_8_10" in p.methods)
                assertTrue(p.methods.none { c.methods.getValue(it).numbers == "kcal_range" })
            }
        }
    }

    @Test
    fun `gentle day keeps three meals and caps the plate`() {
        val p = plan(mode = DayMode.BEREZHNO, load = LoadClass.HARD)
        assertTrue(p.overlays.any { it.id == "OV-gentle" })
        listOf("breakfast", "lunch", "dinner").forEach { s -> assertTrue(p.slots.any { it.slot == s && !it.optional }, s) }
        assertTrue(p.slots.none { it.plate.id == "hard" }, "a gentle day never raises the plate to «тяжёлый»")
        assertTrue(p.why.any { it.ruleId == "NR-10" })
    }

    @Test
    fun `sick day - one easy plate and portions without numbers`() {
        val p = plan(conditions = setOf(DayCondition.SICK))
        assertTrue(p.slots.filter { it.plate.id != "snack" }.all { it.plate.id == "easy" })
        p.slots.forEach { assertNull(it.hands) }
        assertTrue(p.why.first().ruleId == "NR-03")
    }

    @Test
    fun `razgon and a hard session - meal before and a hard plate after`() {
        val p = plan(mode = DayMode.RAZGON, load = LoadClass.HARD, start = 17 * 60, minutes = 60)
        assertTrue(p.slots.any { it.slot == "pre_training" && !it.optional })
        val after = p.slots.first { it.afterTraining || it.slot == "post_training" }
        assertEquals("hard", after.plate.id)
        assertTrue(p.why.any { it.ruleId == "NR-11" })
    }

    @Test
    fun `long session on Opora adds a during-training snack, tomorrow hard adds starch to dinner`() {
        val p = plan(load = LoadClass.LONG, start = 10 * 60, minutes = 100, tomorrow = LoadClass.HARD)
        assertTrue(p.slots.any { it.slot == "during_training" })
        val dinner = p.slots.first { it.slot == "dinner" }
        assertEquals((dinner.plate.hands["starch"] ?: 0.0) + 1.0, dinner.hands?.get("starch"))
        assertTrue(p.why.any { it.ruleId == "NR-34" })
    }

    @Test
    fun `late training merges with dinner`() {
        val p = plan(start = 20 * 60, minutes = 60)
        val dinner = p.slots.first { it.slot == "dinner" }
        assertTrue(dinner.afterTraining)
        assertEquals(21 * 60 + 30, dinner.timeMin)
        assertEquals("moderate", dinner.plate.id)
        assertTrue(p.slots.none { it.slot == "post_training" })
        assertTrue(p.why.any { it.ruleId == "NR-37" }, p.why.toString())
    }

    @Test
    fun `no time - quick meals only when the library has them`() {
        val p = plan(conditions = setOf(DayCondition.NO_TIME))
        p.slots.mapNotNull { it.meal }.forEach { assertTrue((it.meal.prepMin ?: 0) <= 10, "${it.meal.id} ${it.meal.prepMin}") }
    }

    @Test
    fun `nut anaphylaxis - every planned meal is free of nuts, swaps shown, shield lists the allergens`() {
        val f = ProfileFold.fold(ContentFixture.profile("P-nuts").constraints, c.profile, true)
        for (load in LoadClass.entries) {
            for (k in 0..4) {
                val p = plan(profile = f, load = load, replaced = mapOf("breakfast" to k, "lunch" to k, "dinner" to k, "snack" to k))
                assertEquals(listOf("peanut", "tree_nut"), p.shieldAllergens)
                p.slots.mapNotNull { it.meal }.forEach { r ->
                    assertTrue(r.allergens.none { it in setOf("peanut", "tree_nut") }, r.meal.id)
                    assertTrue(r.mayContain.none { it in setOf("peanut", "tree_nut") }, r.meal.id)
                }
            }
        }
    }

    @Test
    fun `safety lowers the level but keeps the chosen one`() {
        val kidney = ProfileFold.fold(ContentFixture.profile("P-kidney").constraints, c.profile, true)
        val p = plan(level = Level.STRUCTURED, profile = kidney)
        assertEquals(Level.BASE, p.level)
        assertEquals(Level.STRUCTURED, p.chosenLevel)
        assertTrue(p.lowered)
        assertFalse("protein_distribution" in p.methods)
        assertTrue(p.why.any { it.ruleId == "NR-02" })
    }

    @Test
    fun `without consent - Osnova without filters`() {
        val none = ProfileFold.fold(UserConstraints.NONE, c.profile, consented = false)
        val p = plan(level = Level.STRUCTURED, profile = none)
        assertEquals(Level.BASE, p.level)
        assertFalse(p.consented)
        assertTrue(p.shieldAllergens.isEmpty())
    }
}
