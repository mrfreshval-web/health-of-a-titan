package com.healthofatitan.app.feature.today

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthofatitan.app.core.model.nav.HotRoute
import com.healthofatitan.app.core.ui.component.TIME_CELL_SPAN_TAG
import com.healthofatitan.app.core.ui.component.TIME_CELL_START_TAG
import com.healthofatitan.app.core.ui.theme.HotTheme
import com.healthofatitan.app.domain.engine.ItemKind
import com.healthofatitan.app.domain.engine.ItemStatus
import com.healthofatitan.app.domain.engine.Regime
import com.healthofatitan.app.integration.whoop.day.DayItemView
import com.healthofatitan.app.integration.whoop.day.DayView
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * UX review of build 119 on a narrow phone (360 dp): R1 — the longest times of a plan stay on one
 * line in their column; R3 — a tap opens or unfolds an item and never marks it, a long press
 * marks it and «Отменить» takes the mark back.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h800dp-xxhdpi")
class TodayPolishTest {

    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 7)

    /** The founder's day of the review plus the longest slot a plan makes (a 180-minute nap). */
    private val plan = DayView(
        dayKey = today,
        regime = Regime.STEADY,
        flags = emptySet(),
        headline = Regime.STEADY.ru,
        summary = "",
        fired = emptyList(),
        items = listOf(
            DayItemView("light", ItemKind.LIGHT, "Дневной свет 10–20 мин", null, 10 * 60 + 22, 11 * 60 + 22, ItemStatus.OPEN),
            DayItemView("training", ItemKind.TRAINING, "Силовая · 60 мин", "Зал B · RPE ≤ 7, без отказа", 10 * 60 + 52, 11 * 60 + 52, ItemStatus.OPEN),
            DayItemView("focus", ItemKind.WORK, "Главная задача", "2 блока по 60 мин", 12 * 60 + 22, 14 * 60 + 22, ItemStatus.OPEN),
            DayItemView("nap", ItemKind.NAP, "Короткий сон", null, 13 * 60 + 40, 16 * 60 + 40, ItemStatus.OPEN),
            DayItemView("dinner", ItemKind.NUTRITION, "Ужин", null, 23 * 60 + 5, 23 * 60 + 50, ItemStatus.OPEN),
            DayItemView("bedtime", ItemKind.SLEEP, "Отбой", null, 2 * 60 + 50, null, ItemStatus.OPEN),
        ),
        bedtimeMin = 2 * 60 + 50,
        caffeineCutoffMin = 17 * 60 + 50,
        windDownMin = 60 + 50,
    )

    private fun SemanticsNode.layout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        return results.single()
    }

    @Test
    fun `the longest times stay on one line in the time column`() {
        compose.setContent { HotTheme { PlanCard(plan, rememberDayMarks { _, _ -> }, onOpen = {}, initiallyOpen = true) } }
        val starts = compose.onAllNodesWithTag(TIME_CELL_START_TAG, useUnmergedTree = true).fetchSemanticsNodes()
        val spans = compose.onAllNodesWithTag(TIME_CELL_SPAN_TAG, useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(6, starts.size)
        assertEquals(5, spans.size)
        (starts + spans).forEach { node ->
            val layout = node.layout()
            assertEquals(1, layout.lineCount, "one line: ${layout.layoutInput.text}")
            assertFalse(layout.didOverflowWidth, "fits the column: ${layout.layoutInput.text}")
        }
    }

    @Test
    fun `bedtime after midnight ends the day`() {
        assertEquals("bedtime", plan.items.maxBy { dayMin(it.startMin!!) }.id)
        assertEquals("dinner", nowItem(plan.items, 23 * 60 + 10)?.id)
        assertEquals("bedtime", nowItem(plan.items, 23 * 60 + 55)?.id)
        assertEquals("bedtime", nowItem(plan.items, 60)?.id)
    }

    @Test
    fun `items open their screens`() {
        assertEquals(HotRoute.TRAINING, itemRoute(plan.items[1]))
        assertEquals(HotRoute.nutritionMeal("dinner"), itemRoute(plan.items[4]))
        assertEquals(null, itemRoute(plan.items[0]))
    }

    @Test
    fun `a tap opens, a long press marks and can be undone`() {
        val toggled = mutableListOf<String>()
        val opened = mutableListOf<String>()
        compose.setContent {
            HotTheme {
                val marks = rememberDayMarks { _, id -> toggled += id }
                Box {
                    Column { PlanCard(plan, marks, onOpen = { opened += it }, initiallyOpen = true) }
                    UndoBar(marks)
                }
            }
        }
        compose.onNodeWithTag(DAY_ROW_TAG + "dinner").performClick()
        compose.onNodeWithTag(DAY_ROW_TAG + "training").performClick()
        compose.onNodeWithTag(DAY_ROW_TAG + "light").performClick()
        assertEquals(listOf(HotRoute.nutritionMeal("dinner"), HotRoute.TRAINING), opened)
        assertTrue(toggled.isEmpty(), "a tap never marks")

        compose.onNodeWithTag(DAY_ROW_TAG + "light").performTouchInput { longClick() }
        assertEquals(listOf("light"), toggled)
        compose.onNodeWithTag(DAY_UNDO_TAG).assertExists()
        compose.onNodeWithText(DayUiTexts.UNDO).performClick()
        assertEquals(listOf("light", "light"), toggled)
    }
}
