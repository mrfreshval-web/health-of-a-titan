package com.healthofatitan.app.core.model.profile

import com.healthofatitan.app.core.model.content.ContentJson
import com.healthofatitan.app.core.model.content.Tree
import org.junit.Test
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The shared profile (A41 §6, A40) against the real `content/profile/constraints.yaml`. */
class ProfileModelTest {

    private fun yaml(path: String): Tree {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "content").isDirectory) dir = dir.parentFile
        val file = File(requireNotNull(dir) { "content/ not found" }, "content/$path")
        return Yaml(SafeConstructor(LoaderOptions())).load<Any?>(file.readText())
    }

    private val dict = ProfileDictionary.parse(yaml("profile/constraints.yaml"))

    @Test
    fun `dictionary has the 16 allergen codes, 10 diets and the injury presets`() {
        assertEquals(16, dict.allergens.size)
        assertEquals(listOf("gluten"), dict.allergen("wheat")?.implies)
        assertEquals(10, dict.diets.size)
        assertEquals(listOf("crustacean", "mollusc"), dict.diet("kosher")?.excludeAllergens)
        assertEquals("no_meat_with_dairy", dict.diet("kosher")?.mealRule)
        assertEquals("ed_care", dict.medical("ed_history")?.mode)
        assertTrue(dict.medical("ed_history")!!.hideNumbers)
        assertEquals(listOf("gluten_free"), dict.medical("celiac")?.impliesDiets)
        assertEquals(-2, dict.intolerance("caffeine")?.caffeineShiftH)
        assertEquals("low", dict.intolerance("lactose")?.lactoseMax)
        assertTrue(dict.disclaimer.startsWith("HoT подбирает блюда"))
    }

    @Test
    fun `movement limits for training come from injuries, life stage and medical flags`() {
        val p = UserConstraints(injuries = setOf("knee_injury"), medical = setOf("heart_bp_meds"))
        assertEquals(setOf("no_impact", "no_deep_knee_flexion", "no_breath_hold", "no_high_intensity"), dict.movementLimits(p))
        assertEquals(emptySet(), dict.movementLimits(UserConstraints.NONE))
        assertEquals("knee", dict.injury("knee_injury")?.region)
    }

    @Test
    fun `stored codes round-trip by category`() {
        val p = UserConstraints(
            allergies = mapOf("peanut" to AllergySeverity.ANAPHYLAXIS, "milk" to AllergySeverity.AVOID),
            diets = setOf("vegan", "gluten_free"),
            medical = setOf("diabetes"),
            injuries = setOf("knee_injury"),
        )
        val stored = ProfileCategory.entries.associateWith { p.codes(it) }
        assertEquals(p, UserConstraints.fromCodes(stored))
        assertEquals(listOf("milk:avoid", "peanut:anaphylaxis"), p.codes(ProfileCategory.ALLERGENS))
        val noMedical = p.without(ProfileCategory.MEDICAL)
        assertFalse(noMedical.has(ProfileCategory.MEDICAL))
        assertEquals(p.diets, noMedical.diets)
    }

    @Test
    fun `claude gets codes and a bare safe flag, never medical names`() {
        val p = UserConstraints(allergies = mapOf("peanut" to AllergySeverity.ALLERGY), medical = setOf("ed_history"))
        val codes = p.llmCodes()
        assertEquals(true, codes["safe_mode"])
        assertFalse(codes.toString().contains("ed_history"))
    }

    @Test
    fun `severity cycles in one tap`() {
        assertEquals(AllergySeverity.ALLERGY, AllergySeverity.AVOID.next())
        assertEquals(null, AllergySeverity.ANAPHYLAXIS.next())
    }

    @Test
    fun `json reader reads what contentCompile writes`() {
        val t = ContentJson.parse("""{"a":[1,2.5,"x\n\"y\"",true,null],"b":{"c":-3}}""")
        assertEquals(mapOf("a" to listOf(1L, 2.5, "x\n\"y\"", true, null), "b" to mapOf("c" to -3L)), t)
    }
}
