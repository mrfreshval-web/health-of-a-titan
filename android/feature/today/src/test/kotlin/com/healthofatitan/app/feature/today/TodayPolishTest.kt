package com.healthofatitan.app.feature.today

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthofatitan.app.core.model.nav.HotRoute
import com.healthofatitan.app.core.ui.component.HotSpacing
import com.healthofatitan.app.core.ui.theme.HotTheme
import com.healthofatitan.app.core.ui.theme.HotType
import com.healthofatitan.app.domain.engine.DayFlag
import com.healthofatitan.app.domain.engine.ItemKind
import com.healthofatitan.app.domain.engine.ItemStatus
import com.healthofatitan.app.domain.engine.Regime
import com.healthofatitan.app.domain.today.DayTask
import com.healthofatitan.app.domain.today.Safety
import com.healthofatitan.app.domain.today.Scenario
import com.healthofatitan.app.domain.today.SupportTone
import com.healthofatitan.app.domain.today.TaskKind
import com.healthofatitan.app.domain.today.TaskMove
import com.healthofatitan.app.domain.today.TodayBlock
import com.healthofatitan.app.domain.today.TodayCopy
import com.healthofatitan.app.domain.today.TodayLayout
import com.healthofatitan.app.domain.today.WhyBuilder
import com.healthofatitan.app.domain.today.WhyInput
import com.healthofatitan.app.integration.whoop.WhoopGlance
import com.healthofatitan.app.integration.whoop.WhoopStatus
import com.healthofatitan.app.integration.whoop.WorkoutGlance
import com.healthofatitan.app.integration.whoop.day.DayItemView
import com.healthofatitan.app.integration.whoop.day.DayView
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * «Сегодня» v3 on the Pixel 9 Pro XL width (412 dp): the blocks in their order (T-01), one ▶
 * (T-02), never red (T-04), help one tap away in the sober scenario (T-06), «Не сегодня» back to
 * the week (T-08), one seal per workout (T-09), the numbers in one line (T-10), «нет данных» with a
 * source (T-11). A tap opens, a swipe marks, «Отменить» takes it back.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class TodayPolishTest {

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
        items = listOf(
            DayItemView("training", ItemKind.TRAINING, "Силовая · 60 мин", "Зал B · RPE ≤ 7", 10 * 60 + 52, 11 * 60 + 52, ItemStatus.OPEN),
            DayItemView("focus", ItemKind.WORK, "Главная задача", "2 блока по 60 мин", 12 * 60 + 22, 14 * 60 + 22, ItemStatus.OPEN),
        ),
        bedtimeMin = 23 * 60,
        caffeineCutoffMin = 14 * 60,
        windDownMin = 22 * 60,
    )

    private val training = DayTask("training", TaskKind.TRAINING, "Силовая · 60 мин", weekGoalId = "body-training", week = "неделя 1 из 3", hasMini = true, route = HotRoute.TRAINING)
    private val focus = DayTask("focus", TaskKind.FOCUS, "Главная задача", weekGoalId = "work-focus", week = "неделя 2 из 4", hasMini = true)

    private fun day(at: LocalDateTime = today.atTime(11, 0), safety: Safety = Safety.NONE, candidates: List<DayTask> = listOf(training, focus)) =
        DayUiState(plan = plan, now = at, loaded = true, candidates = candidates, safety = safety)

    @Test
    fun `T-01 the four blocks stand in one order morning, day and evening`() {
        listOf(7, 13, 21).forEach { hour ->
            val view = todayView(TodayUiState(whoop = whoop), day(today.atTime(hour, 5)))
            // A66 §3.2 (mockup 05b): the scene, «Сейчас» with the tasks, the day, the rhythm
            assertEquals(listOf(TodayBlock.ME, TodayBlock.TASKS, TodayBlock.DAY, TodayBlock.RHYTHM), view.blocks)
        }
        compose.setContent { HotTheme { TodayScreen(TodayUiState(whoop = whoop), onConnectWhoop = {}, day = day()) } }
        // positionInRoot, not boundsInRoot: the scene pushes «Ритм дня» below the fold, where bounds clip to 0
        val tops = TodayLayout.ORDER.map { compose.onNodeWithTag(it.tag).fetchSemanticsNode().positionInRoot.y }
        assertEquals(tops.sorted(), tops)
    }

    @Test
    fun `scenarios follow the data`() {
        assertEquals(Scenario.NO_SOURCE, todayView(TodayUiState(), null).scenario)
        assertEquals(Scenario.DAY, todayView(TodayUiState(whoop = whoop), day()).scenario)
        assertEquals(Scenario.SAFETY, todayView(TodayUiState(whoop = whoop), day(safety = Safety.CRISIS)).scenario)
        assertEquals(Scenario.RETURN, todayView(TodayUiState(whoop = whoop), day().copy(returning = true)).scenario)
        val noWhoop = todayView(TodayUiState(), null)
        assertEquals(TaskKind.CONNECT_WHOOP, noWhoop.tasks.last().kind)
    }

    /** S2: the MANUAL day without WHOOP is shown — the week's tasks, «Закрыть день», the mode as an estimate. */
    private val manual = plan.copy(flags = setOf(DayFlag.MANUAL, DayFlag.DATA_LIMITED), regime = Regime.BOOST)

    @Test
    fun `S2 a day without WHOOP is lived by the MANUAL plan`() {
        val noWhoop = TodayUiState(whoop = whoop.copy(connected = false, baselineNights = 0))
        val morning = todayView(noWhoop, day().copy(plan = manual))
        assertEquals(Scenario.NO_SOURCE, morning.scenario)
        assertEquals(listOf(TaskKind.TRAINING, TaskKind.FOCUS, TaskKind.CONNECT_WHOOP), morning.tasks.map { it.kind })
        assertEquals(TodayCopy.TASK_CONNECT_SOURCE, morning.tasks.last().title)
        assertTrue(morning.rhythm.windows.isNotEmpty(), "the windows of the default day")
        assertTrue(morning.rhythm.estimate)
        assertNull(morning.me.mode, "4.1: the mode waits for a source")

        val self = todayView(noWhoop, day().copy(plan = manual, self = 3))
        assertEquals(Scenario.SELF, self.scenario)
        assertEquals(com.healthofatitan.app.domain.today.DayMode.ROVNO, self.me.mode, "never above Ровно without WHOOP")
        assertTrue(self.me.estimate)

        val evening = day(today.atTime(20, 30)).copy(plan = manual)
        assertEquals(today, evening.eveningFor, "«Закрыть день» is offered")
        val eveningView = todayView(noWhoop, evening)
        assertTrue(eveningView.rhythm.dots.first { it.ritual == com.healthofatitan.app.domain.today.Ritual.CLOSE_DAY }.active)

        assertEquals(Scenario.SAFETY, todayView(noWhoop, day(safety = Safety.CRISIS).copy(plan = manual, self = 3)).scenario)
    }

    @Test
    fun `T-02 only the first task has the play button`() {
        compose.setContent { HotTheme { TodayScreen(TodayUiState(whoop = whoop), onConnectWhoop = {}, day = day()) } }
        assertEquals(1, compose.onAllNodesWithTag(TASK_PLAY_TAG, useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    fun `T-04 the day has no red, a deviation is a quiet note`() {
        SupportPalette.all().forEach { assertFalse(SupportPalette.reddish(it), "red in «День»: $it") }
        assertTrue(SupportPalette.reddish(Color(0xFFE5484D)), "the check itself knows red")
        assertNotEquals(SupportPalette.of(SupportTone.NOTE, true), SupportPalette.of(SupportTone.LIT, true))
    }

    @Test
    fun `T-06 the sober day puts help first, one tap away`() {
        var help = 0
        compose.setContent {
            HotTheme {
                TodayScreen(TodayUiState(whoop = whoop), onConnectWhoop = {}, day = day(safety = Safety.SAF_01), actions = DayActions(onOpenHelp = { help++ }))
            }
        }
        compose.onNodeWithTag(TASK_TAG + "help").performScrollTo().performClick()
        assertEquals(1, help)
        compose.onAllNodesWithTag(ME_HAIKU_TAG).fetchSemanticsNodes().let { assertTrue(it.isEmpty(), "no haiku on the sober screen") }
    }

    @Test
    fun `a tap opens a task and never marks it`() {
        val opened = mutableListOf<String>()
        val done = mutableListOf<Boolean>()
        compose.setContent {
            HotTheme {
                TodayScreen(
                    TodayUiState(whoop = whoop),
                    onConnectWhoop = {},
                    day = day(),
                    actions = DayActions(onOpen = { opened += it }, onTaskDone = { _, _, d -> done += d }),
                )
            }
        }
        compose.onNodeWithTag(TASK_TAG + "training").performScrollTo().performClick()
        assertEquals(listOf(HotRoute.TRAINING), opened)
        assertTrue(done.isEmpty())
    }

    @Test
    fun `a swipe marks the task and undo takes it back`() {
        val done = mutableListOf<Boolean>()
        compose.setContent {
            HotTheme {
                // «Отменить» is the screen's own bar (DayMarks inside TodayScreen)
                TodayScreen(TodayUiState(whoop = whoop), onConnectWhoop = {}, day = day(), actions = DayActions(onTaskDone = { _, _, d -> done += d }))
            }
        }
        // «focus» is folded under «Сейчас» (A66 §3.4): «ещё 1 дело ›» unfolds it in place
        compose.onNodeWithTag(TASKS_MORE_TAG).performScrollTo().performClick()
        compose.onNodeWithTag(TASK_TAG + "focus").performScrollTo().performTouchInput { swipeRight() }
        assertEquals(listOf(true), done)
        compose.onNodeWithTag(DAY_UNDO_TAG).assertExists()
        compose.onNodeWithText(DayUiTexts.UNDO).performClick()
        assertEquals(listOf(true, false), done)
    }

    @Test
    fun `T-08 not today sends the task back to its week goal, never to tomorrow`() {
        val moves = mutableListOf<TaskMove>()
        val back = mutableListOf<String>()
        compose.setContent {
            HotTheme {
                TodayScreen(
                    TodayUiState(whoop = whoop),
                    onConnectWhoop = {},
                    day = day(),
                    actions = DayActions(onTaskToWeek = { moves += it }, onTaskBack = { _, id -> back += id }),
                )
            }
        }
        compose.onNodeWithTag(TASKS_MORE_TAG).performScrollTo().performClick()
        compose.onNodeWithTag(TASK_TAG + "focus").performScrollTo().performTouchInput { swipeLeft() }
        assertEquals(1, moves.size)
        assertEquals("work-focus", moves.single().weekGoalId)
        assertEquals(today, moves.single().day)
        assertFalse(moves.single().toTomorrow)
        compose.onNodeWithText(TodayCopy.BACK_TO_WEEK).assertExists()
        compose.onNodeWithText(DayUiTexts.UNDO).performClick()
        assertEquals(listOf("focus"), back)
    }

    @Test
    fun `T-09 one seal per confirmed workout`() {
        val confirmed = plan.copy(items = plan.items.map { if (it.id == "training") it.copy(status = ItemStatus.DONE_WHOOP) else it })
        val zone = ZoneId.of("UTC")
        fun glance(hour: Int) = WhoopGlance(0L, null, null, WorkoutGlance(today.atTime(hour, 0).atZone(zone).toInstant(), 50, "Weightlifting", 80.0))
        val morning = sealKey(confirmed, glance(9), zone)
        val evening = sealKey(confirmed, glance(18), zone)
        assertTrue(morning != null && evening != null)
        assertNotEquals(morning, evening)
        assertEquals(morning, sealKey(confirmed, glance(9), zone))
        assertNull(sealKey(plan, glance(9), zone), "no seal without WHOOP's confirmation")
        assertNull(sealKey(confirmed, null, zone))
    }

    @Test
    fun `T-10 the longest numbers stay in one line at 412 dp`() {
        var fits = emptyList<Pair<String, Boolean>>()
        compose.setContent {
            HotTheme {
                val measurer = rememberTextMeasurer()
                val density = LocalDensity.current
                val card = 412.dp - HotSpacing.edge * 2 - HotSpacing.cardPadding * 2
                // a row of «День»: the label, the narrowest bar, a gap and the number (A66 §3.5)
                val column = with(density) { (card - DAY_LABEL_WIDTH - DAY_BAR_MIN - 8.dp).toPx() }
                val number = with(density) { ((card - 12.dp * 2) / 3).toPx() }
                val supports = listOf("20,9 из 14–18", "120 мин активности", "12 из 12 приёмов", "день закрыт ✓").map {
                    it to (measurer.measure(it, MaterialTheme.typography.labelLarge, softWrap = false).size.width <= column)
                }
                val me = listOf("95", "11:59", "5 / 5").map {
                    it to (measurer.measure(it, HotType.ringValue, softWrap = false).size.width <= number)
                }
                fits = supports + me
            }
        }
        compose.waitForIdle()
        assertEquals(7, fits.size)
        fits.forEach { (text, ok) -> assertTrue(ok, "one line: $text") }
    }

    @Test
    fun `T-11 why says no data and keeps the source`() {
        compose.setContent { HotTheme { WhyScreen(WhyBuilder.build(WhyInput()), onAsk = {}) } }
        assertTrue(compose.onAllNodesWithText(TodayCopy.NO_DATA, substring = true).fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithTag(WHY_SECTION_TAG + "sleep").assertExists()
        compose.onNodeWithTag(WHY_SECTION_TAG + "you_vs_whoop").performScrollTo()
    }

    @Test
    fun `the day adds a glass at once`() {
        val water = mutableListOf<Int>()
        compose.setContent { HotTheme { TodayScreen(TodayUiState(whoop = whoop), onConnectWhoop = {}, day = day(), actions = DayActions(onWater = { _, d -> water += d })) } }
        compose.onNodeWithTag(WATER_ADD_TAG).performScrollTo().performClick()
        assertEquals(listOf(1), water)
    }
}
