package com.healthofatitan.app.feature.nutrition

import com.healthofatitan.app.core.model.profile.UserConstraints
import com.healthofatitan.app.domain.nutrition.Level
import com.healthofatitan.app.domain.nutrition.NutritionContent
import com.healthofatitan.app.domain.nutrition.NutritionDayPlan
import com.healthofatitan.app.domain.nutrition.NutritionEngine
import com.healthofatitan.app.domain.nutrition.ProfileFold
import com.healthofatitan.app.domain.safety.Restriction
import com.healthofatitan.app.domain.safety.SafetyConsumer
import com.healthofatitan.app.domain.safety.SafetyFlag
import com.healthofatitan.app.domain.safety.SafetyPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.io.File
import java.time.LocalDate

/** S3: the food plan → «Сегодня»: the main slots are counted; ED_CARE leaves no numbers and no times. */
class TodayFoodTest {

    private val content: NutritionContent by lazy {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "content/nutrition").isDirectory) dir = dir.parentFile
        val root = File(requireNotNull(dir), "content")
        fun yaml(path: String): Any? = Yaml(SafeConstructor(LoaderOptions())).load<Any?>(File(root, path).readText())
        NutritionContent.parse(
            mapOf(
                "constraints" to yaml("profile/constraints.yaml"),
                "components" to yaml("nutrition/components.yaml"),
                "meals" to yaml("nutrition/meals.yaml"),
                "templates" to yaml("nutrition/templates.yaml"),
                "methods" to yaml("nutrition/methods.yaml"),
                "rules" to yaml("nutrition/rules.yaml"),
            ),
        )
    }

    private fun plan(constraints: UserConstraints = UserConstraints.NONE): NutritionDayPlan {
        val folded = ProfileFold.fold(constraints, content.profile, consented = true)
        return NutritionEngine.plan(DayFacts.input(null, LocalDate.of(2026, 11, 7), Level.STRUCTURED, folded, emptySet(), emptyMap()), content)
    }

    @Test
    fun `meals planned are the main slots of the plan`() {
        val p = plan()
        val food = todayFood(p, emptySet())
        assertEquals(p.slots.count { !it.optional }, food.mealsPlanned)
        assertEquals(p.slots.map { it.slot }, food.meals.map { it.slot })
        assertEquals(p.template.ru, food.plate)
        assertTrue(food.meals.any { it.timeMin != null })
        assertFalse(food.hideCounts || food.hideTimes || food.hideCalories || food.quiet)
    }

    @Test
    fun `ed care of the bus hides counts, times, calories and food talk`() {
        val food = todayFood(plan(), SafetyPolicy.of(SafetyConsumer.TODAY, SafetyFlag.ED_CARE))
        assertTrue(food.quiet && food.hideCounts && food.hideTimes && food.hideCalories)
        assertTrue(food.meals.all { it.timeMin == null })
    }

    @Test
    fun `after the exit calories and windows stay hidden, the counts too`() {
        val food = todayFood(plan(), setOf(Restriction.HIDE_CALORIES, Restriction.HIDE_FOOD_WINDOWS))
        assertFalse(food.quiet)
        assertTrue(food.hideCounts && food.hideTimes)
        assertTrue(food.meals.all { it.timeMin == null })
    }

    @Test
    fun `the profile's own ed care mode hides counts even without the bus`() {
        val food = todayFood(plan(UserConstraints(medical = setOf("ed_history"))), emptySet())
        assertTrue(food.hideCounts && food.hideCalories && food.hideTimes)
        assertTrue(food.meals.all { it.timeMin == null })
    }

    @Test
    fun `pregnancy hides the windows, not the count`() {
        val food = todayFood(plan(), SafetyPolicy.of(SafetyConsumer.TODAY, SafetyFlag.PREGNANT))
        assertTrue(food.hideTimes)
        assertFalse(food.hideCounts)
    }

    @Test
    fun `no plan, no meals`() {
        val food = todayFood(null, emptySet())
        assertNull(food.mealsPlanned)
        assertTrue(food.meals.isEmpty())
    }
}
