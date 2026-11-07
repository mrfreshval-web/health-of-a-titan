package com.healthofatitan.app.feature.path

import android.app.Application
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthofatitan.app.core.datastore.MentorPrefs
import com.healthofatitan.app.core.model.mentor.MentorPersona
import com.healthofatitan.app.core.ui.theme.HotTheme
import com.healthofatitan.app.domain.path.GoalPlan
import com.healthofatitan.app.domain.path.GoalRecord
import com.healthofatitan.app.domain.path.PathEngine
import com.healthofatitan.app.domain.path.PathEvidence
import com.healthofatitan.app.domain.path.PathRegime
import com.healthofatitan.app.domain.path.PathState
import com.healthofatitan.app.domain.path.Sphere
import com.healthofatitan.app.domain.path.StepVersion
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.test.assertEquals

/** И8.3–И8.5 on screen: one-tap goal, the step sized by the mode, the mentor picked by a line. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w448dp-h998dp-xxhdpi")
class PathScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 7)

    private fun state(regime: PathRegime?, marks: Set<LocalDate> = emptySet()): PathState {
        val goal = GoalRecord(1, GoalPlan(Sphere.BODY, 1, listOf(2, 3, 3)), today.minusDays(37))
        return PathEngine.state(today, listOf(goal), mapOf(1L to marks), PathEvidence(today), regime)
    }

    @Test
    fun `no goal yet - the proposal is taken in one tap`() {
        var taken: GoalPlan? = null
        compose.setContent {
            HotTheme { PathScreen(PathView(PathState.empty(today)), PathActions(onTake = { taken = it })) }
        }
        compose.onNodeWithTag(PATH_TAKE_TAG).performScrollTo().performClick()
        assertEquals(Sphere.SLEEP, taken?.sphere)
    }

    @Test
    fun `other shows the next sphere`() {
        var taken: GoalPlan? = null
        compose.setContent {
            HotTheme { PathScreen(PathView(PathState.empty(today)), PathActions(onTake = { taken = it })) }
        }
        compose.onNodeWithTag(PATH_OTHER_TAG).performScrollTo().performClick()
        compose.onNodeWithTag(PATH_TAKE_TAG).performScrollTo().performClick()
        assertEquals(Sphere.BODY, taken?.sphere)
    }

    @Test
    fun `a gentle day offers the minimal step and a tap counts it`() {
        var toggled: StepVersion? = null
        compose.setContent {
            HotTheme { PathScreen(PathView(state(PathRegime.GENTLE), PathRegime.GENTLE), PathActions(onToggleStep = { toggled = it })) }
        }
        compose.onNodeWithText("этап 2 из 3 · неделя 6 из 12", ignoreCase = true).assertExists()
        compose.onNodeWithText("Прогулка 20 минут").assertExists()
        compose.onNodeWithTag(PATH_STEP_TAG).assertIsOff().performScrollTo().performClick()
        assertEquals(StepVersion.MINIMAL, toggled)
    }

    @Test
    fun `a marked step shows as done on today`() {
        compose.setContent {
            HotTheme { PathTodayStepContent(PathView(state(PathRegime.BOOST, setOf(today))), onToggle = {}, onOpenPath = {}) }
        }
        compose.onNodeWithText("Тренировка 45 минут").assertExists()
        compose.onNodeWithTag(PATH_STEP_TAG).assertIsOn()
    }

    @Test
    fun `without a goal today invites to the path`() {
        var opened = false
        compose.setContent {
            HotTheme { PathTodayStepContent(PathView(PathState.empty(today)), onToggle = {}, onOpenPath = { opened = true }) }
        }
        compose.onNodeWithTag(PATH_STEP_EMPTY_TAG).performClick()
        assertEquals(true, opened)
    }

    @Test
    fun `the mentor is chosen by a preview line, strong words stay off for bro`() {
        var picked: MentorPersona? = null
        compose.setContent {
            HotTheme { PersonaScreen(MentorPrefs(persona = MentorPersona.BRO), onPick = { picked = it }) }
        }
        compose.onNodeWithTag(PERSONA_STRONG_WORDS_TAG).assertIsOff()
        compose.onNodeWithTag(personaTag(MentorPersona.BUTLER)).performScrollTo().performClick()
        assertEquals(MentorPersona.BUTLER, picked)
    }
}
