package com.healthofatitan.app.feature.week

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthofatitan.app.core.ui.theme.HotTheme
import com.healthofatitan.app.domain.today.DayMode
import com.healthofatitan.app.domain.today.week.DayFact
import com.healthofatitan.app.domain.today.week.GoalDays
import com.healthofatitan.app.domain.today.week.PathWeek
import com.healthofatitan.app.domain.today.week.WeekCare
import com.healthofatitan.app.domain.today.week.WeekFace
import com.healthofatitan.app.domain.today.week.WeekKind
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

    private val path = PathWeek.Active("Сильнее к весне", listOf(WeekGoalRules.ID_TRAINING, WeekGoalRules.ID_SLEEP), monday.minusDays(14))

    private fun state(path: PathWeek = this.path, care: WeekCare = WeekCare()): WeekScreenState {
        val facts = (0..3).map { DayFact(monday.plusDays(it.toLong()), DayMode.ROVNO, listOf(0.9f, 0.8f, 0.75f, 0.3f, 0.2f), 1, false) }
        val plan = WeekGoalRules.propose(monday, null, null, WeekKind.NORMAL, path = path).plan
        val view = WeekBuilder.build(
            WeekInput(now = monday.plusDays(3).atTime(20, 0), facts = facts, plan = plan, planStatus = PlanStatus.APPROVED),
        )
        return WeekScreenState(
            view = view,
            path = path,
            care = care,
            goalDays = mapOf(WeekGoalRules.ID_TRAINING to GoalDays(setOf(monday))),
            nights = WeekFace.nights(listOf(6.5, 7.0, 6.9)),
            behind = WeekFace.behind(12, 30),
        )
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
    fun `with a Path the week shows the verdict, the Sculpture blank, the goal cards and the days`() {
        compose.setContent { HotTheme { WeekScreen(WeekUiState(state())) } }
        listOf(WEEK_SCREEN_TAG, WEEK_VERDICT_TAG, WEEK_SCULPTURE_TAG).forEach { compose.onNodeWithTag(it, useUnmergedTree = true).assertIsDisplayed() }
        listOf(WEEK_GOALS_TAG, WEEK_COLUMNS_TAG, WEEK_PATH_LINK_TAG, WEEK_GLANCE_TAG, WEEK_DAYS_TAG, WEEK_SUPPORTS_TAG, "week_goal_${WeekGoalRules.ID_TRAINING}")
            .forEach { compose.onNodeWithTag(it, useUnmergedTree = true).assertExists() }
        compose.onNodeWithTag(WEEK_NO_GOAL_TAG).assertDoesNotExist()
    }

    @Test
    fun `the Path link opens the tab Path`() {
        var opened = false
        compose.setContent { HotTheme { WeekScreen(WeekUiState(state()), WeekActions(onOpenPath = { opened = true })) } }
        compose.onNodeWithTag(WEEK_PATH_LINK_TAG).performScrollTo().performClick()
        assertTrue(opened)
    }

    @Test
    fun `without a Path the week asks to name a goal and invents no goals`() {
        var named = false
        compose.setContent { HotTheme { WeekScreen(WeekUiState(state(PathWeek.NoGoal)), WeekActions(onNameGoal = { named = true })) } }
        compose.onNodeWithTag(WEEK_NO_GOAL_TAG).assertIsDisplayed()
        listOf(WEEK_SEEN_NIGHTS_TAG, WEEK_SEEN_BEHIND_TAG).forEach { compose.onNodeWithTag(it).assertExists() }
        compose.onNodeWithTag(WEEK_GOALS_TAG).assertDoesNotExist()
        compose.onNodeWithTag(WEEK_VERDICT_TAG).assertDoesNotExist()
        compose.onNodeWithTag(WEEK_NAME_GOAL_TAG).performClick()
        assertTrue(named)
    }

    @Test
    fun `care comes before the Path - a sick week without a goal keeps its goals`() {
        compose.setContent { HotTheme { WeekScreen(WeekUiState(state(PathWeek.NoGoal, WeekCare(sick = true)))) } }
        compose.onNodeWithTag(WEEK_NO_GOAL_TAG).assertDoesNotExist()
        compose.onNodeWithTag(WEEK_GOALS_TAG, useUnmergedTree = true).assertExists()
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
