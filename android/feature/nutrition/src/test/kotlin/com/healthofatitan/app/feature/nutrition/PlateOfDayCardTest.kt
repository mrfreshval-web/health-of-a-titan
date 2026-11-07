package com.healthofatitan.app.feature.nutrition

import android.app.Application
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.SemanticsMatcher
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthofatitan.app.core.database.ProfileSnapshot
import com.healthofatitan.app.core.model.profile.UserConstraints
import com.healthofatitan.app.core.ui.theme.HotTheme
import com.healthofatitan.app.domain.nutrition.Level
import com.healthofatitan.app.domain.nutrition.NutritionContent
import com.healthofatitan.app.domain.nutrition.NutritionEngine
import com.healthofatitan.app.domain.nutrition.ProfileFold
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.io.File
import java.time.LocalDate

/** The «Тарелка дня» card: ed_care shows no numbers at all (A41 §6.4); the normal card does. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class PlateOfDayCardTest {

    @get:Rule
    val compose = createComposeRule()

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

    private fun state(constraints: UserConstraints): NutritionState {
        val today = LocalDate.of(2026, 11, 7)
        val folded = ProfileFold.fold(constraints, content.profile, consented = true)
        val plan = NutritionEngine.plan(DayFacts.input(null, today, Level.STRUCTURED, folded, emptySet(), emptyMap()), content)
        return NutritionState(content, ProfileSnapshot(consented = true, constraints = constraints), plan, levelCap = folded.levelCap, loaded = true)
    }

    private fun texts(): List<String> = compose.onAllNodes(SemanticsMatcher("any") { true }).fetchSemanticsNodes().flatMap { node ->
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
            node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
    }

    @Test
    fun `ed care card has no digits`() {
        compose.setContent { HotTheme { PlateOfDayCard(state(UserConstraints(medical = setOf("ed_history"))), PlateActions()) } }
        compose.onNodeWithTag(PLATE_CARD_TAG).assertIsDisplayed()
        val all = texts()
        assertTrue("no texts rendered", all.isNotEmpty())
        val withDigits = all.filter { t -> t.any(Char::isDigit) }
        assertTrue("digits in ed_care: $withDigits", withDigits.isEmpty())
    }

    @Test
    fun `normal card shows meal times`() {
        compose.setContent { HotTheme { PlateOfDayCard(state(UserConstraints.NONE), PlateActions()) } }
        assertTrue(texts().any { t -> Regex("\\d{1,2}:\\d{2}").containsMatchIn(t) })
    }
}
