package com.healthofatitan.app.feature.path

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthofatitan.app.core.datastore.MentorPrefs
import com.healthofatitan.app.core.model.mentor.MentorPersona
import com.healthofatitan.app.core.ui.component.HotMode
import com.healthofatitan.app.core.ui.theme.HotTheme
import com.healthofatitan.app.domain.path.ComponentKey
import com.healthofatitan.app.domain.path.Criterion
import com.healthofatitan.app.domain.path.GoalComponent
import com.healthofatitan.app.domain.path.GoalStatus
import com.healthofatitan.app.domain.path.PathCopy
import com.healthofatitan.app.domain.path.PathMetric
import com.healthofatitan.app.domain.path.PathOverviewBuilder
import com.healthofatitan.app.domain.path.PathTemplates
import com.healthofatitan.app.domain.path.PracticalGoal
import com.healthofatitan.app.domain.path.Role
import com.healthofatitan.app.domain.path.Sufficiency
import com.healthofatitan.app.feature.path.logic.PathGiven
import com.healthofatitan.app.feature.path.logic.RitualDraft
import com.healthofatitan.app.feature.path.logic.RitualStep
import com.healthofatitan.app.feature.path.slice.SliceGeometry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** «Путь» v1 on screen: the slice and its 17 nodes, the empty path, the ritual with help on every screen. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w412dp-h1900dp-xxhdpi")
class PathScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 7)
    private val knee = PathGiven.from(setOf("knee_injury"))

    private fun goal(status: GoalStatus = GoalStatus.ACTIVE) = PracticalGoal(
        id = 1, title = "Силы на вечер", why = "ради сил на вечер", templateId = PathTemplates.ENERGY,
        start = today.minusDays(30), deadline = today.plusDays(54), status = status,
        criteria = listOf(Criterion("ночи от 7 ч", 4.0, 5.0, "из 7", PathMetric.SLEEP_NIGHTS)),
        components = listOf(
            GoalComponent(ComponentKey.SLEEP, Role.LEAD, 3, 2, isLever = true),
            GoalComponent(ComponentKey.CARDIO, Role.PROP, 3, 2, safeVersion = knee.constraintOf(ComponentKey.CARDIO)?.safeVersion),
            GoalComponent(ComponentKey.FEELINGS, Role.GUARD),
        ),
        pauseFrom = if (status == GoalStatus.PAUSED) today.minusDays(2) else null,
    )

    private fun data(g: PracticalGoal?): PathData {
        val ov = PathOverviewBuilder.build(today, g, mapOf(ComponentKey.SLEEP to 2), emptySet(), knee, listOf(Sufficiency(ComponentKey.SLEEP, 6.1, 7.0)))
        return PathData(today, ov, listOfNotNull(g), emptyList(), knee, HotMode.ROVNO, 6.1)
    }

    @Test
    fun `an empty path waits for a goal and names it in one tap`() {
        var named = false
        compose.setContent { HotTheme { PathScreen(PathUi(data(null)), PathActions(onNameGoal = { named = true }), still = true) } }
        compose.onNodeWithText(PathCopy.EMPTY_TITLE).assertExists()
        compose.onNodeWithTag(PATH_NAME_GOAL_TAG).performScrollTo().performClick()
        assertTrue(named)
    }

    @Test
    fun `behind is the reverse plan of the path, one tap on look back`() {
        var back = false
        compose.setContent { HotTheme { PathScreen(PathUi(data(goal())), PathActions(onLookBack = { back = true }), still = true) } }
        compose.onNodeWithTag(PATH_LOOK_BACK_TAG).performScrollTo().assertTextEquals(PathCopy.LOOK_BACK).performClick()
        assertTrue(back)
    }

    @Test
    fun `the slice has 17 talkback nodes and never names a colour`() {
        compose.setContent { HotTheme { PathScreen(PathUi(data(goal())), PathActions(), still = true) } }
        val tags = listOf("path_star") + (SliceGeometry.ARCS + SliceGeometry.SLABS + SliceGeometry.CURRENTS).map(::partTag) +
            listOf("path_given", PATH_HORIZON_TAG)
        assertEquals(17, tags.size)
        val colours = listOf("красн", "зелён", "зелен", "синий", "синяя", "синего", "жёлт", "желт", "янтар", "цвет")
        tags.forEach { t ->
            val node = compose.onNodeWithTag(t, useUnmergedTree = true).assertExists().fetchSemanticsNode()
            val text = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString(" ").lowercase()
            assertTrue(text.isNotBlank(), t)
            assertTrue(colours.none { it in text }, "$t: $text")
        }
        val sleep = compose.onNodeWithTag(partTag(ComponentKey.SLEEP), useUnmergedTree = true).fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString()
        assertTrue("Рычаг" in sleep && "Ведёт" in sleep, sleep)
    }

    @Test
    fun `a paused goal keeps the light and seats the titan of the horizon`() {
        compose.setContent { HotTheme { PathScreen(PathUi(data(goal(GoalStatus.PAUSED))), PathActions(), still = true) } }
        compose.onNodeWithText(PathCopy.PAUSED_TITLE).performScrollTo().assertExists()
        compose.onNodeWithText(PathCopy.HORIZON_SEATED).performScrollTo().assertExists()
    }

    @Test
    fun `help is one tap away on every screen of the ritual`() {
        val step = mutableStateOf(RitualStep.GOAL)
        var help = 0
        compose.setContent {
            HotTheme {
                RitualScreen(
                    RitualUi(RitualDraft(step = step.value, templateId = PathTemplates.ENERGY), data(null)),
                    RitualActions(onHelp = { help++ }), still = true,
                )
            }
        }
        RitualStep.entries.forEachIndexed { i, s ->
            step.value = s
            compose.waitForIdle()
            compose.onNodeWithTag(ritualTag(s)).assertExists()
            compose.onNodeWithTag(RITUAL_HELP_TAG).performClick()
            assertEquals(i + 1, help, s.name)
        }
    }

    @Test
    fun `a weight goal waits behind the flag and the next step stays closed`() {
        compose.setContent {
            HotTheme { RitualScreen(RitualUi(RitualDraft(text = "Похудеть на 10 кг"), data(null)), RitualActions(), still = true) }
        }
        compose.onNodeWithText(PathCopy.GATE_WEIGHT_LATER).performScrollTo().assertExists()
        compose.onNodeWithTag(RITUAL_NEXT_TAG).assertIsNotEnabled()
    }

    @Test
    fun `crisis words in the ritual keep help in sight`() {
        compose.setContent {
            HotTheme { RitualScreen(RitualUi(RitualDraft(text = "…"), data(null), crisis = 1), RitualActions(), still = true) }
        }
        compose.onNodeWithTag(RITUAL_CRISIS_TAG).performScrollTo().assertExists()
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
