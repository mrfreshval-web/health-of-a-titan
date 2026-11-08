package com.healthofatitan.app.feature.path

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthofatitan.app.core.ui.theme.HotTheme
import com.healthofatitan.app.domain.today.DayMode
import com.healthofatitan.app.feature.path.logic.BehindCopy
import com.healthofatitan.app.feature.path.logic.BehindFilter
import com.healthofatitan.app.feature.path.logic.BehindRecord
import com.healthofatitan.app.feature.path.logic.DayRecord
import com.healthofatitan.app.feature.path.logic.RecordKind
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.test.assertEquals

/** «За плечами» on screen (A66 §6): the starting point, the facts as buttons, hard periods and help. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w412dp-h1900dp-xxhdpi")
class BehindScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 5)

    private fun data(hard: Boolean) = BehindData(
        today = today,
        days = mapOf(today to DayRecord(today, DayMode.ROVNO, training = true)),
        records = listOf(
            BehindRecord(RecordKind.TRAINING, today, BehindCopy.TRAINING_HOT, "40 мин"),
            BehindRecord(RecordKind.WEEK, today.minusDays(3), BehindCopy.week(45, today.minusDays(3)), "Тренировки"),
        ),
        since = today.minusDays(20),
        hardAvailable = hard,
    )

    @Test
    fun `the first day is the starting point, not zeros`() {
        compose.setContent { HotTheme { BehindScreen(BehindData.empty(today), onHelp = {}) } }
        compose.onNodeWithTag(BEHIND_TAG).assertExists()
        compose.onNodeWithTag(BEHIND_EMPTY_TAG).assertExists()
        compose.onNodeWithTag(BEHIND_CALENDAR_TAG).assertExists()
    }

    @Test
    fun `a fact opens its records`() {
        compose.setContent { HotTheme { BehindScreen(data(hard = true), onHelp = {}) } }
        compose.onNodeWithText(BehindCopy.trainings(1)).performClick()
        compose.onNodeWithText("40 мин").performScrollTo().assertExists()
    }

    @Test
    fun `hard periods are offered only outside hard states`() {
        compose.setContent { HotTheme { BehindScreen(data(hard = false), onHelp = {}, initialFilter = BehindFilter.HARD) } }
        compose.onNodeWithTag(behindFilterTag(BehindFilter.HARD)).assertDoesNotExist()
        compose.onNodeWithTag(behindFilterTag(BehindFilter.WEEKS)).assertExists()
    }

    @Test
    fun `hard periods are a chip like the others when the state allows`() {
        compose.setContent { HotTheme { BehindScreen(data(hard = true), onHelp = {}) } }
        compose.onNodeWithTag(behindFilterTag(BehindFilter.HARD)).assertExists()
    }

    @Test
    fun `crisis words in the search keep help in sight`() {
        var help = 0
        compose.setContent { HotTheme { BehindScreen(data(hard = true), onHelp = { help++ }, initialQuery = "не хочу жить") } }
        compose.onNodeWithTag(BEHIND_CRISIS_TAG).assertExists()
        compose.waitForIdle()
        assertEquals(true, help >= 1, "a crisis of level 2 opens help at once")
    }

    @Test
    fun `the search finds a workout and says when nothing is found`() {
        compose.setContent { HotTheme { BehindScreen(data(hard = true), onHelp = {}, initialQuery = "тренировка") } }
        compose.onNodeWithTag(BEHIND_RESULTS_TAG).assertExists()
        compose.onNodeWithText("40 мин").assertExists()
    }
}
