package com.healthofatitan.app.domain.nutrition

import com.healthofatitan.app.core.model.content.num
import com.healthofatitan.app.core.model.content.str
import com.healthofatitan.app.core.model.content.strings
import com.healthofatitan.app.core.model.profile.AllergySeverity
import com.healthofatitan.app.core.model.profile.UserConstraints
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * A41 §6 and `content/nutrition/_tests/profiles.yaml` (the 14 test profiles) on the Kotlin filter:
 * the same expectations `scripts/check_nutrition.py` checks on the Python side.
 */
class ProfileFilterTest {

    private val c = ContentFixture.content

    private fun fold(t: ContentFixture.TestProfile) = ProfileFold.fold(t.constraints, c.profile, consented = true)

    @Test
    fun `content is parsed in full`() {
        assertEquals(120, c.components.size)
        assertEquals(52, c.meals.size)
        assertEquals(10, c.templates.size)
        assertTrue(c.methods.getValue("energy_reference").blocked, "D-032: energy_reference stays blocked")
        assertTrue(c.methods.getValue("tre_window_8_10").blocked)
        assertEquals("Основа", c.levels[Level.BASE])
        assertEquals("Опора", c.levels[Level.STRUCTURED])
        assertTrue(c.draft)
    }

    @Test
    fun `every test profile folds to the expected mode, level cap, hidden numbers and forbidden methods`() {
        assertEquals(14, ContentFixture.testProfiles.size)
        for (t in ContentFixture.testProfiles) {
            val f = fold(t)
            val e = t.expect
            e.str("mode")?.let { assertEquals(it, f.mode.code, "${t.id} mode") }
            e.str("max_level")?.let { assertEquals(it, t.level.atMost(f.levelCap).code, "${t.id} level cap") }
            if (e.containsKey("hide_numbers")) assertEquals(e["hide_numbers"], f.hideNumbers, "${t.id} hide_numbers")
            val missing = e.strings("forbidden_methods").toSet() - f.forbidMethods
            assertTrue(missing.isEmpty(), "${t.id}: methods should be forbidden: $missing")
        }
    }

    @Test
    fun `nothing excluded survives the filter, even after swaps`() {
        for (t in ContentFixture.testProfiles) {
            val f = fold(t)
            for (m in c.meals) {
                val r = MealFilter.resolve(m, f, c) ?: continue
                for ((comp, _) in r.components) {
                    val v = MealFilter.violations(comp, f)
                    if (v.isNotEmpty()) fail("LEAK ${t.id}: ${m.id} keeps ${comp.id} ($v)")
                }
                assertTrue(!MealFilter.mealRuleViolated(r.components.map { it.first }, f), "${t.id}: ${m.id} breaks a meal rule")
            }
        }
    }

    @Test
    fun `each main slot keeps enough options at the effective level`() {
        for (t in ContentFixture.testProfiles) {
            val f = fold(t)
            val level = t.level.atMost(f.levelCap)
            val need = t.expect["min_options"].num()?.toInt() ?: 3
            for (slot in listOf("breakfast", "lunch", "dinner")) {
                val n = c.meals.count { m -> level in m.levels && slot in m.slots && MealFilter.resolve(m, f, c) != null }
                assertTrue(n >= need, "${t.id}: only $n options for $slot at ${level.code}, need >= $need")
            }
        }
    }

    @Test
    fun `the filter is not a no-op - must_reject_raw meals fail without swaps`() {
        for (t in ContentFixture.testProfiles) {
            val f = fold(t)
            for (id in t.expect.strings("must_reject_raw")) {
                val meal = c.meals.first { it.id == id }
                assertNull(MealFilter.resolve(meal, f, c, useSwaps = false), "${t.id}: $id passes without swaps")
            }
        }
    }

    @Test
    fun `swap keeps the meal - walnuts become pumpkin seeds for a nut allergy`() {
        val f = fold(ContentFixture.profile("P-nuts"))
        val r = assertNotNull(MealFilter.resolve(c.meals.first { it.id == "M002" }, f, c))
        assertEquals(listOf("walnuts" to "pumpkin_seeds"), r.swaps.map { it.from.id to it.to.id })
        assertTrue(r.allergens.none { it == "tree_nut" || it == "peanut" })
        assertTrue(r.mayContain.none { it == "tree_nut" || it == "peanut" })
        assertTrue(f.noGeneration && f.restaurantWarning, "anaphylaxis: no generated meals, restaurant warning")
    }

    @Test
    fun `avoid lets may-contain through with a note, allergy does not`() {
        val avoid = ProfileFold.fold(UserConstraints(allergies = mapOf("sesame" to AllergySeverity.AVOID)), c.profile, true)
        val allergy = ProfileFold.fold(UserConstraints(allergies = mapOf("sesame" to AllergySeverity.ALLERGY)), c.profile, true)
        val m003 = c.meals.first { it.id == "M003" } // rye bread: may contain sesame
        val withNote = assertNotNull(MealFilter.resolve(m003, avoid, c))
        assertEquals(setOf("sesame"), withNote.mayContainNotes)
        val strict = MealFilter.resolve(m003, allergy, c)
        assertTrue(strict == null || strict.components.none { "sesame" in it.first.mayContain })
    }

    @Test
    fun `no consent means no filter and Osnova only`() {
        val f = ProfileFold.fold(UserConstraints(allergies = mapOf("peanut" to AllergySeverity.ANAPHYLAXIS)), c.profile, consented = false)
        assertEquals(Level.BASE, f.levelCap)
        assertTrue(f.excludeAllergens.isEmpty())
        assertTrue(!f.consented)
    }

    @Test
    fun `there is no energy field anywhere in the components`() {
        val raw = ContentFixture.yaml("nutrition/components.yaml").toString()
        assertTrue(!raw.contains("kcal="), "components.yaml must not carry kcal (A41 §3, D-010)")
    }
}
