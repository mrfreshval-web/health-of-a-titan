package com.healthofatitan.app.screenshots

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthofatitan.app.core.database.ProfileSnapshot
import com.healthofatitan.app.core.model.profile.AllergySeverity
import com.healthofatitan.app.core.model.profile.UserConstraints
import com.healthofatitan.app.core.ui.component.HotBackground
import com.healthofatitan.app.core.ui.component.HotSpacing
import com.healthofatitan.app.core.ui.component.HotTopBar
import com.healthofatitan.app.core.ui.theme.HotTheme
import com.healthofatitan.app.domain.engine.ItemKind
import com.healthofatitan.app.domain.engine.ItemStatus
import com.healthofatitan.app.domain.engine.Regime
import com.healthofatitan.app.domain.nutrition.DayCondition
import com.healthofatitan.app.domain.nutrition.Level
import com.healthofatitan.app.domain.nutrition.NutritionContent
import com.healthofatitan.app.domain.nutrition.NutritionEngine
import com.healthofatitan.app.domain.nutrition.ProfileFold
import com.healthofatitan.app.feature.nutrition.DayFacts
import com.healthofatitan.app.feature.nutrition.MealCardScreen
import com.healthofatitan.app.feature.nutrition.NutritionLevelScreen
import com.healthofatitan.app.feature.nutrition.NutritionProfileScreen
import com.healthofatitan.app.feature.nutrition.NutritionState
import com.healthofatitan.app.feature.nutrition.NutritionTexts
import com.healthofatitan.app.feature.nutrition.PlateActions
import com.healthofatitan.app.feature.nutrition.PlateOfDayCard
import com.healthofatitan.app.feature.nutrition.ProfileActions
import com.healthofatitan.app.integration.whoop.day.DayItemView
import com.healthofatitan.app.integration.whoop.day.DayView
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.io.File
import java.time.LocalDate

/**
 * Screenshots of the food screens of stream В (A41 §10) in «Титаниум», like [TitaniumScreenshots]:
 * opt-in with HOT_SCREENSHOTS, Pixel 9 Pro XL sized, content read from the repository YAML.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w448dp-h998dp-xxhdpi")
class NutritionTitaniumScreenshots {

    @get:Rule
    val compose = createComposeRule()

    private val out = File("build/screenshots")

    @Before
    fun optIn() {
        assumeTrue(System.getenv("HOT_SCREENSHOTS") != null)
        out.mkdirs()
    }

    private fun shoot(name: String, dark: Boolean = true, content: @Composable () -> Unit) {
        compose.setContent { HotTheme(darkTheme = dark) { content() } }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

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

    private val today = LocalDate.now()

    private fun day(regime: Regime) = DayView(
        dayKey = today,
        regime = regime,
        flags = emptySet(),
        headline = regime.ru,
        summary = "",
        fired = emptyList(),
        items = listOf(
            DayItemView("light", ItemKind.LIGHT, "Свет утром", null, 7 * 60 + 30, 7 * 60 + 40, ItemStatus.DONE_SELF),
            DayItemView("training", ItemKind.TRAINING, "Силовая", "45 минут", 18 * 60, 18 * 60 + 60, ItemStatus.OPEN),
            DayItemView("sleep", ItemKind.SLEEP, "Отбой", null, 23 * 60, null, ItemStatus.OPEN),
        ),
        bedtimeMin = 23 * 60,
        caffeineCutoffMin = 14 * 60,
        windDownMin = 22 * 60,
    )

    private val nutAllergy = UserConstraints(
        allergies = mapOf("tree_nut" to AllergySeverity.ANAPHYLAXIS, "peanut" to AllergySeverity.ALLERGY),
        intolerances = setOf("lactose"),
        diets = setOf("omnivore"),
    )

    private fun state(
        constraints: UserConstraints = nutAllergy,
        consented: Boolean = true,
        level: Level = Level.STRUCTURED,
        regime: Regime = Regime.STEADY,
        chips: Set<DayCondition> = emptySet(),
        eveningDue: Boolean = false,
    ): NutritionState {
        val folded = ProfileFold.fold(constraints, content.profile, consented)
        val plan = NutritionEngine.plan(DayFacts.input(day(regime), today, level, folded, chips, emptyMap()), content)
        return NutritionState(
            content = content,
            profile = ProfileSnapshot(consented = consented, constraints = if (consented) constraints else UserConstraints.NONE, nutritionLevel = level.code),
            plan = plan,
            chips = chips,
            eveningDue = eveningDue,
            levelCap = folded.levelCap,
            loaded = true,
        )
    }

    @Composable
    private fun Screen(title: String?, overline: String? = null, body: @Composable () -> Unit) {
        HotBackground {
            Column(Modifier.fillMaxSize()) {
                HotTopBar(title = title, overline = overline, onBack = if (title != null) ({}) else null)
                body()
            }
        }
    }

    @Composable
    private fun Card(s: NutritionState) = Screen(null) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = HotSpacing.edge, vertical = 8.dp)) {
            PlateOfDayCard(s, PlateActions())
        }
    }

    @Test
    fun plateOfDay() = shoot("c01-plate-of-day-rovno") { Card(state()) }

    @Test
    fun plateOfDayEvening() = shoot("c02-plate-of-day-evening") { Card(state(eveningDue = true)) }

    @Test
    fun plateOfDayBerezhno() = shoot("c03-plate-of-day-berezhno") { Card(state(regime = Regime.GENTLE, chips = setOf(DayCondition.NO_TIME))) }

    @Test
    fun plateOfDayEdCare() = shoot("c04-plate-of-day-ed-care") { Card(state(constraints = UserConstraints(medical = setOf("ed_history")))) }

    @Test
    fun plateOfDayLight() = shoot("c05-plate-of-day-light", dark = false) { Card(state()) }

    @Test
    fun mealCard() = shoot("c06-meal-card") {
        val s = state()
        val slot = s.plan?.slots?.firstOrNull { it.meal?.swaps?.isNotEmpty() == true }?.slot ?: s.plan?.slots?.first()?.slot.orEmpty()
        Screen(NutritionTexts.PLATE_OF_DAY) { MealCardScreen(s, slot, onReplace = {}) }
    }

    @Test
    fun profileConsent() = shoot("c07-profile-consent") {
        Screen(NutritionTexts.SET_UP) { NutritionProfileScreen(state(consented = false, constraints = UserConstraints.NONE), ProfileActions()) }
    }

    @Test
    fun profileFilled() = shoot("c08-profile-filled") {
        Screen(NutritionTexts.SET_UP) { NutritionProfileScreen(state(), ProfileActions()) }
    }

    @Test
    fun level() = shoot("c09-level") {
        Screen(NutritionTexts.LEVEL_TITLE) { NutritionLevelScreen(state(), onPick = {}) }
    }
}
