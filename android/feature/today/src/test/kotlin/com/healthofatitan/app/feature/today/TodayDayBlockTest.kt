package com.healthofatitan.app.feature.today

import android.app.Application
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthofatitan.app.core.model.nav.HotRoute
import com.healthofatitan.app.core.ui.theme.HotTheme
import com.healthofatitan.app.domain.engine.DayFlag
import com.healthofatitan.app.domain.engine.FiredRule
import com.healthofatitan.app.domain.engine.ItemKind
import com.healthofatitan.app.domain.engine.ItemStatus
import com.healthofatitan.app.domain.engine.Regime
import com.healthofatitan.app.domain.engine.RuleRef
import com.healthofatitan.app.domain.today.DayCopy
import com.healthofatitan.app.domain.today.DayRowId
import com.healthofatitan.app.domain.today.FoodMeal
import com.healthofatitan.app.domain.today.Safety
import com.healthofatitan.app.domain.today.TodayBlock
import com.healthofatitan.app.domain.today.TodayFood
import com.healthofatitan.app.integration.whoop.CycleGlance
import com.healthofatitan.app.integration.whoop.WhoopGlance
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
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * «День» on «Сегодня» (A66 §3.5, §3.8): Нагрузка · Питание · Отдых replace «Опоры»; the reset line
 * stands in the header; the rows open their places; the conditions of Agent 3 hold on screen.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class TodayDayBlockTest {

    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 7)
    private val zone = ZoneId.systemDefault()
    private val whoop = WhoopStatus(configured = true, connected = true, whoopUserId = "1", lastSyncAt = 0L, baselineNights = 28)

    private val plan = DayView(
        dayKey = today,
        regime = Regime.STEADY,
        flags = emptySet(),
        headline = Regime.STEADY.ru,
        summary = "",
        fired = emptyList(),
        items = listOf(
            DayItemView("training", ItemKind.TRAINING, "Силовая · 60 мин", "", 10 * 60, 11 * 60, ItemStatus.OPEN),
            DayItemView("dinner", ItemKind.NUTRITION, "Ужин", "", 19 * 60 + 15, 20 * 60, ItemStatus.OPEN),
        ),
        bedtimeMin = 23 * 60 + 5,
        caffeineCutoffMin = 14 * 60,
        windDownMin = 22 * 60,
    )

    private val food = TodayFood(
        plate = "Рабочий день",
        meals = listOf(FoodMeal("breakfast", "Завтрак", 8 * 60), FoodMeal("lunch", "Обед", 13 * 60), FoodMeal("dinner", "Ужин", 20 * 60 + 30)),
    )

    private fun glance(strain: Double?) = WhoopGlance(
        fetchedAt = 0L, sleep = null, recovery = null, workout = null,
        cycle = CycleGlance(today.atTime(6, 0).atZone(zone).toInstant(), strain, null, null),
    )

    private fun state(strain: Double? = 9.8, connected: Boolean = true) =
        TodayUiState(whoop = whoop.copy(connected = connected), glance = glance(strain).takeIf { connected })

    private fun day(
        at: LocalDateTime = today.atTime(11, 0),
        plan: DayView = this.plan,
        food: TodayFood = this.food,
        safety: Safety = Safety.NONE,
    ) = DayUiState(plan = plan, now = at, loaded = true, marks = TodayMarks(meals = setOf("breakfast"), water = 2), food = food, safety = safety)

    private fun show(state: TodayUiState, day: DayUiState, actions: DayActions = DayActions()) {
        compose.setContent { HotTheme { TodayScreen(state, onConnectWhoop = {}, day = day, actions = actions) } }
    }

    private fun texts(): List<String> = compose.onAllNodes(SemanticsMatcher("any") { true }, useUnmergedTree = true).fetchSemanticsNodes().flatMap { node ->
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
            node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
    }

    private fun exists(tag: String): Boolean = compose.onAllNodes(SemanticsMatcher("tag") { it.config.getOrNull(SemanticsProperties.TestTag) == tag })
        .fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `the day replaces the supports, resets after sleep and opens each row`() {
        val opened = mutableListOf<String>()
        show(state(), day(), DayActions(onOpen = { opened += it }))
        compose.onNodeWithTag(TodayBlock.DAY.tag).performScrollTo()
        compose.onNodeWithTag(DAY_RESETS_TAG).assertExists()
        compose.onNodeWithText(DayCopy.RESETS).assertExists()
        assertFalse(texts().any { "Опоры" in it || "держу за тебя" in it }, "the supports are gone")

        val view = todayView(state(), day())
        val load = view.day.rows.first { it.id == DayRowId.LOAD }
        assertEquals("9,8 из 10–14", load.value)
        assertEquals(DayCopy.toCorridor("0,2"), load.hint, "no past walks stored: no minutes (condition 1)")
        assertEquals("1 из 3 приёмов", view.day.rows.first { it.id == DayRowId.FOOD }.value)
        assertEquals("экраны с 22:00 · отбой 23:05", view.day.rows.first { it.id == DayRowId.REST }.hint)
        assertEquals(view.day.share, view.dayShare)

        compose.onNodeWithTag(DAY_ROW_TAG + "load").performScrollTo().performClick()
        compose.onNodeWithTag(DAY_ROW_TAG + "food").performScrollTo().performClick()
        compose.onNodeWithTag(DAY_ROW_TAG + "rest").performScrollTo().performClick()
        assertEquals(listOf(HotRoute.TRAINING, HotRoute.NUTRITION_DAY, HotRoute.BREATHER), opened)
    }

    @Test
    fun `plus food marks the next meal of the plan`() {
        val meals = mutableListOf<Pair<String, Boolean>>()
        show(state(), day(), DayActions(onMeal = { _, slot, eaten -> meals += slot to eaten }))
        compose.onNodeWithTag(DAY_FOOD_ADD_TAG).performScrollTo().performClick()
        assertEquals(listOf("lunch" to true), meals)
    }

    @Test
    fun `in the evening rest opens «Закрыть день» in the rhythm`() {
        show(state(), day(at = today.atTime(20, 30)))
        assertFalse(exists(CLOSE_DAY_TAG))
        compose.onNodeWithTag(DAY_ROW_TAG + "rest").performScrollTo().performClick()
        compose.onNodeWithTag(CLOSE_DAY_TAG).performScrollTo()
    }

    @Test
    fun `without WHOOP the load is minutes of activity with «оценка», no strain corridor`() {
        val manual = plan.copy(flags = setOf(DayFlag.MANUAL), items = plan.items.map { if (it.id == "training") it.copy(status = ItemStatus.DONE_SELF) else it })
        val view = todayView(state(connected = false), day(plan = manual))
        val load = view.day.rows.first { it.id == DayRowId.LOAD }
        assertEquals(DayCopy.activity(60), load.value)
        assertTrue(load.estimate)
        assertFalse(load.counted)
        show(state(connected = false), day(plan = manual))
        val all = texts()
        assertTrue(all.any { it.startsWith(DayCopy.LOAD) && DayCopy.ESTIMATE in it }, "the load is read as an estimate: $all")
        assertFalse(all.any { "10–14" in it }, "no strain corridor without WHOOP")
    }

    @Test
    fun `gentle puts rest first and says by feel, never how much is left`() {
        val gentle = plan.copy(regime = Regime.GENTLE)
        show(state(strain = 4.0), day(plan = gentle))
        val rest = compose.onNodeWithTag(DAY_ROW_TAG + "rest").fetchSemanticsNode().positionInRoot.y
        val load = compose.onNodeWithTag(DAY_ROW_TAG + "load").fetchSemanticsNode().positionInRoot.y
        assertTrue(rest < load, "rest first in Бережно")
        val all = texts()
        assertTrue(all.any { DayCopy.BY_FEEL in it })
        assertFalse(all.any { "до коридора" in it })
    }

    @Test
    fun `a red flag or sickness never says how much is left`() {
        val red = plan.copy(fired = listOf(FiredRule(RuleRef("SAF-04", 1), "")))
        show(state(strain = 4.0), day(plan = red))
        assertFalse(texts().any { "до коридора" in it })
        val sick = plan.copy(flags = setOf(DayFlag.SICK))
        val view = todayView(state(strain = 4.0), day(plan = sick))
        assertEquals(listOf(DayRowId.REST), view.day.rows.map { it.id })
        assertEquals(DayCopy.drink(2), view.day.drink)
    }

    @Test
    fun `ed care has no food row, no plus food, the drink hint stays`() {
        val edCare = food.copy(meals = food.meals.map { it.copy(timeMin = null) }, hideTimes = true, hideCounts = true, hideCalories = true, quiet = true)
        val view = todayView(state(), day(food = edCare))
        assertEquals(listOf(DayRowId.LOAD, DayRowId.REST), view.day.rows.map { it.id })
        show(state(), day(food = edCare))
        assertFalse(exists(DAY_ROW_TAG + "food"))
        assertFalse(exists(DAY_FOOD_ADD_TAG))
        compose.onNodeWithTag(DAY_DRINK_TAG).performScrollTo()
        assertTrue(texts().none { "приём" in it }, "no meal count under ED_CARE")
    }

    @Test
    fun `the sober screen hides the day`() {
        show(state(), day(safety = Safety.SAF_01))
        assertFalse(exists(TodayBlock.DAY.tag))
        assertFalse(todayView(state(), day(safety = Safety.CARE)).day.shown)
    }
}
