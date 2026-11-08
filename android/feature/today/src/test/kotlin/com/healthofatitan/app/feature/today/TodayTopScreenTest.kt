package com.healthofatitan.app.feature.today

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthofatitan.app.core.model.nav.HotRoute
import com.healthofatitan.app.core.ui.theme.HotTheme
import com.healthofatitan.app.domain.engine.ItemKind
import com.healthofatitan.app.domain.engine.ItemStatus
import com.healthofatitan.app.domain.engine.Regime
import com.healthofatitan.app.domain.today.DayTask
import com.healthofatitan.app.domain.today.Safety
import com.healthofatitan.app.domain.today.TaskKind
import com.healthofatitan.app.domain.today.TodayCopy
import com.healthofatitan.app.integration.whoop.FacetValue
import com.healthofatitan.app.integration.whoop.TodayMetrics
import com.healthofatitan.app.integration.whoop.WhoopStatus
import com.healthofatitan.app.integration.whoop.day.DayItemView
import com.healthofatitan.app.integration.whoop.day.DayView
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The top of «Сегодня» by mockup 05b on 412 × 915 dp: the scene opens «Почему так», the three
 * tiles, «Сейчас» with its line of the link and «ещё N дела ›» unfolding the rest in place; the
 * sober screen has no scene and no tiles.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class TodayTopScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 5)
    private val whoop = WhoopStatus(configured = true, connected = true, whoopUserId = "1", lastSyncAt = 0L, baselineNights = 28)
    private val metrics = TodayMetrics(today, charge = FacetValue(68.0, 1.2, 0, 1.0), balance = null, recovery = null, sleepHours = null, sleepDebt = null)

    private val plan = DayView(
        dayKey = today,
        regime = Regime.BOOST,
        flags = emptySet(),
        headline = Regime.BOOST.ru,
        summary = "",
        fired = emptyList(),
        items = listOf(DayItemView("training", ItemKind.TRAINING, "Силовая · подтягивания", null, 15 * 60, 16 * 60, ItemStatus.OPEN)),
        bedtimeMin = 23 * 60,
        caffeineCutoffMin = 14 * 60,
        windDownMin = 22 * 60,
    )

    private val tasks = listOf(
        DayTask("training", TaskKind.TRAINING, "Силовая · подтягивания", weekGoalId = "body-training", week = TodayCopy.week(2, 3), route = HotRoute.TRAINING),
        DayTask("focus", TaskKind.FOCUS, "Главная задача", weekGoalId = "work-focus", week = TodayCopy.week(1, 4)),
        DayTask("walk", TaskKind.REMINDER, "Прогулка", weekGoalId = "body-walk", week = TodayCopy.week(0, 2)),
    )

    private val haiku = listOf("Ночь была долгой.", "Ладони помнят металл.", "Сегодня — выше.")

    private fun day(safety: Safety = Safety.NONE) = DayUiState(
        plan = plan,
        now = today.atTime(9, 41),
        loaded = true,
        candidates = tasks,
        safety = safety,
        self = 4,
        haiku = haiku,
        dawnShown = true,
    )

    @Test
    fun `the top reads mode, sun, tiles and now`() {
        val opened = mutableListOf<String>()
        compose.setContent {
            HotTheme {
                TodayScreen(TodayUiState(whoop = whoop, metrics = metrics), onConnectWhoop = {}, day = day(), actions = DayActions(onOpen = { opened += it }))
            }
        }
        val view = todayView(TodayUiState(whoop = whoop, metrics = metrics), day())
        assertEquals("68", view.tiles.first().shown)
        assertTrue(view.sceneSpoken.startsWith("Режим Разгон. Заряд 68, выше обычного"), view.sceneSpoken)

        compose.onNodeWithTag(ME_HAIKU_TAG).assertExists()
        compose.onNodeWithTag(ME_TILE_TAG + "charge").assertIsDisplayed()
        compose.onNodeWithTag(ME_TILE_TAG + "night").assertExists()
        compose.onNodeWithTag(ME_TILE_TAG + "you").assertExists()
        // «Сейчас» stays above the fold (A66 §3.2, T-10)
        compose.onNodeWithTag(TASK_TAG + "training").assertIsDisplayed()
        compose.onNodeWithText("→ ${TodayCopy.week(2, 3)}").assertExists()

        // only «Сейчас» shows; the rest unfolds in place
        assertTrue(compose.onAllNodesWithTag(TASK_TAG + "focus").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText(TodayTopCopy.more(2), substring = true).assertExists()
        compose.onNodeWithTag(TASKS_MORE_TAG).performScrollTo().performClick()
        compose.onNodeWithTag(TASK_TAG + "focus").assertExists()
        compose.onNodeWithTag(TASK_TAG + "walk").assertExists()
        assertEquals(1, compose.onAllNodesWithTag(TASK_PLAY_TAG, useUnmergedTree = true).fetchSemanticsNodes().size)

        compose.onNodeWithTag(ME_WHY_TAG).performClick()
        compose.onNodeWithTag(ME_TILE_TAG + "charge").performClick()
        assertEquals(listOf(HotRoute.WHY, HotRoute.WHY), opened)
    }

    @Test
    fun `the sober screen has no scene, no haiku and no tiles`() {
        compose.setContent { HotTheme { TodayScreen(TodayUiState(whoop = whoop, metrics = metrics), onConnectWhoop = {}, day = day(Safety.CRISIS)) } }
        assertTrue(compose.onAllNodesWithTag(ME_HAIKU_TAG).fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithTag(ME_NUMBERS_TAG).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag(ME_WHY_TAG).assertExists()
        compose.onNodeWithTag(TASK_TAG + "help").assertExists()
    }
}
