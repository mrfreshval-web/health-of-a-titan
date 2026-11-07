package com.healthofatitan.app.feature.week

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthofatitan.app.core.ui.theme.HotTheme
import com.healthofatitan.app.domain.today.DayMode
import com.healthofatitan.app.domain.today.week.DayFact
import com.healthofatitan.app.domain.today.week.PlanStatus
import com.healthofatitan.app.domain.today.week.WeekBuilder
import com.healthofatitan.app.domain.today.week.WeekGoalRules
import com.healthofatitan.app.domain.today.week.WeekInput
import com.healthofatitan.app.integration.whoop.week.WeekScreenState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min
import kotlin.test.assertTrue

/** «Неделя» on screen: the blocks of A52 are there, and no colour of the screen is red but «Помощь» (W-02). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class WeekScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val monday = LocalDate.of(2026, 11, 2)

    private fun state(): WeekScreenState {
        val facts = (0..3).map { DayFact(monday.plusDays(it.toLong()), DayMode.ROVNO, listOf(0.9f, 0.8f, 0.75f, 0.3f, 0.2f), 1, false) }
        val view = WeekBuilder.build(
            WeekInput(now = monday.plusDays(3).atTime(20, 0), facts = facts, plan = WeekGoalRules.defaults(monday, first = false), planStatus = PlanStatus.APPROVED),
        )
        return WeekScreenState(view = view)
    }

    private fun isRed(c: Color): Boolean {
        val mx = max(c.red, max(c.green, c.blue))
        val mn = min(c.red, min(c.green, c.blue))
        val delta = mx - mn
        if (mx == 0f || delta / mx < 0.25f) return false
        if (mx != c.red) return false
        val hue = (60f * ((c.green - c.blue) / delta) + 360f) % 360f
        return hue < 15f || hue > 345f
    }

    @Test
    fun `the week shows its glance, days in support, goals and supports`() {
        compose.setContent { HotTheme { WeekScreen(WeekUiState(state())) } }
        listOf(WEEK_SCREEN_TAG, WEEK_GLANCE_TAG, WEEK_COLUMNS_TAG, WEEK_DAYS_TAG).forEach { compose.onNodeWithTag(it, useUnmergedTree = true).assertIsDisplayed() }
        listOf(WEEK_GOALS_TAG, WEEK_SUPPORTS_TAG).forEach { compose.onNodeWithTag(it, useUnmergedTree = true).assertExists() }
    }

    @Test
    fun `a week still loading shows the forming Titan, not an empty screen`() {
        compose.setContent { HotTheme { WeekScreen(WeekUiState()) } }
        compose.onNodeWithTag(WEEK_SCREEN_TAG).assertIsDisplayed()
    }

    @Test
    fun `W-02 no colour of the week is red, only help`() {
        var used: Map<String, Color> = emptyMap()
        var help = Color.Unspecified
        compose.setContent {
            HotTheme {
                used = WeekPalette.used()
                help = WeekPalette.help()
            }
        }
        compose.waitForIdle()
        assertTrue(used.isNotEmpty())
        used.forEach { (name, c) -> assertTrue(!isRed(c), "$name looks red; red is only for help (A52 W-02)") }
        assertTrue(isRed(help) || help.red > help.blue, "help keeps the alert colour")
    }
}
