package com.healthofatitan.app.feature.today

import android.app.Application
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthofatitan.app.core.model.nav.HotRoute
import com.healthofatitan.app.core.ui.theme.HotTheme
import com.healthofatitan.app.domain.engine.ItemKind
import com.healthofatitan.app.domain.engine.ItemStatus
import com.healthofatitan.app.domain.engine.Regime
import com.healthofatitan.app.domain.today.BodyWindowKind
import com.healthofatitan.app.domain.today.FoodMeal
import com.healthofatitan.app.domain.today.SupportId
import com.healthofatitan.app.domain.today.TodayCopy
import com.healthofatitan.app.domain.today.TodayFood
import com.healthofatitan.app.integration.whoop.WhoopStatus
import com.healthofatitan.app.integration.whoop.day.DayItemView
import com.healthofatitan.app.integration.whoop.day.DayView
import com.healthofatitan.app.integration.whoop.day.TodayMarks
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * S3 (audit 2026-11-08, Р-3, Р-16): «Тарелка дня» is back on «Сегодня» as one line beside «+ стакан»
 * and opens the next meal's card; under `ED_CARE` «Сегодня» shows no meal count, no kcal, no
 * meal time and no dinner window.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class TodayPlateTest {

    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 7)
    private val whoop = WhoopStatus(configured = true, connected = true, whoopUserId = "1", lastSyncAt = 0L, baselineNights = 28)

    private val plan = DayView(
        dayKey = today,
        regime = Regime.STEADY,
        flags = emptySet(),
        headline = Regime.STEADY.ru,
        summary = "",
        fired = emptyList(),
        items = listOf(DayItemView("dinner", ItemKind.NUTRITION, "Ужин", "", 19 * 60 + 15, 20 * 60, ItemStatus.OPEN)),
        bedtimeMin = 23 * 60,
        caffeineCutoffMin = 14 * 60,
        windDownMin = 22 * 60,
    )

    private val food = TodayFood(
        plate = "Рабочий день",
        meals = listOf(
            FoodMeal("breakfast", "Завтрак", 8 * 60),
            FoodMeal("lunch", "Обед", 13 * 60),
            FoodMeal("snack", "Перекус", 16 * 60, optional = true),
            FoodMeal("dinner", "Ужин", 20 * 60 + 30),
        ),
    )

    private val edCare = food.copy(meals = food.meals.map { it.copy(timeMin = null) }, hideTimes = true, hideCounts = true, hideCalories = true, quiet = true)

    private fun day(food: TodayFood, at: LocalDateTime = today.atTime(11, 0), meals: Set<String> = setOf("breakfast")) =
        DayUiState(plan = plan, now = at, loaded = true, marks = TodayMarks(meals = meals), food = food)

    private fun texts(): List<String> = compose.onAllNodes(SemanticsMatcher("any") { true }, useUnmergedTree = true).fetchSemanticsNodes().flatMap { node ->
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
            node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
    }

    @Test
    fun `the support counts the main meals of the plan`() {
        val view = todayView(TodayUiState(whoop = whoop), day(food, meals = setOf("breakfast", "snack")))
        assertEquals("1 из 3", view.supports.first { it.id == SupportId.NUTRITION }.value)
    }

    @Test
    fun `the plate line names the next meal and opens its card`() {
        val view = todayView(TodayUiState(whoop = whoop), day(food))
        assertEquals(PlateLine("${TodayCopy.PLATE_OF_DAY} · Обед 13:00", HotRoute.nutritionMeal("lunch")), view.plate)
        val all = todayView(TodayUiState(whoop = whoop), day(food, meals = setOf("breakfast", "lunch", "dinner")))
        assertEquals(HotRoute.NUTRITION_DAY, all.plate?.route)
        assertNull(todayView(TodayUiState(whoop = whoop), day(TodayFood())).plate, "no plan, no line")

        val opened = mutableListOf<String>()
        compose.setContent {
            HotTheme { TodayScreen(TodayUiState(whoop = whoop), onConnectWhoop = {}, day = day(food), actions = DayActions(onOpen = { opened += it })) }
        }
        compose.onNodeWithTag(PLATE_LINE_TAG).performScrollTo().performClick()
        assertEquals(listOf(HotRoute.nutritionMeal("lunch")), opened)
    }

    @Test
    fun `ed care leaves no plate line, no count, no kcal and no dinner window`() {
        val view = todayView(TodayUiState(whoop = whoop), day(edCare))
        assertNull(view.plate)
        val nutrition = view.supports.first { it.id == SupportId.NUTRITION }
        assertTrue(nutrition.value.none(Char::isDigit) && nutrition.spoken.none(Char::isDigit), nutrition.spoken)
        assertTrue(view.supports.none { "ккал" in it.spoken || "калори" in it.source })
        assertTrue(view.rhythm.windows.none { it.kind == BodyWindowKind.DINNER })

        compose.setContent { HotTheme { TodayScreen(TodayUiState(whoop = whoop), onConnectWhoop = {}, day = day(edCare)) } }
        compose.onNodeWithTag(PLATE_LINE_TAG).assertDoesNotExist()
        val all = texts()
        assertTrue(all.none { (it.startsWith(TodayCopy.NUTRITION) && it.any(Char::isDigit)) || "ккал" in it || "приём" in it }, "food numbers on Today: $all")
        assertTrue(all.none { it.startsWith(TodayCopy.WIN_DINNER) }, "a dinner window on Today: $all")
    }

    @Test
    fun `the nutrition support lists the plan's meals and opens a meal card, quiet under ed care`() {
        val opened = mutableListOf<String>()
        val support = todayView(TodayUiState(whoop = whoop), day(food)).supports.first { it.id == SupportId.NUTRITION }
        compose.setContent { HotTheme { SupportScreen(support, day(food), SupportActions(onOpen = { opened += it })) } }
        compose.onNodeWithTag(SUPPORT_MEAL_OPEN_TAG + "lunch").performClick()
        compose.onNodeWithTag(SUPPORT_PLATE_TAG).performClick()
        assertEquals(listOf(HotRoute.nutritionMeal("lunch"), HotRoute.NUTRITION_DAY), opened)
    }

    @Test
    fun `under hide food talk the nutrition support has no list and no numbers`() {
        val support = todayView(TodayUiState(whoop = whoop), day(edCare)).supports.first { it.id == SupportId.NUTRITION }
        compose.setContent { HotTheme { SupportScreen(support, day(edCare), SupportActions()) } }
        compose.onNodeWithTag(SUPPORT_MEAL_OPEN_TAG + "lunch").assertDoesNotExist()
        compose.onNodeWithTag(SUPPORT_PLATE_TAG).assertDoesNotExist()
        val digits = texts().filter { t -> t.any(Char::isDigit) }
        assertTrue(digits.isEmpty(), "digits on the support: $digits")
    }
}
