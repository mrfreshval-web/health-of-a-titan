package com.healthofatitan.app.screenshots

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthofatitan.app.core.model.mentor.MentorPersona
import com.healthofatitan.app.core.ui.component.HotBackground
import com.healthofatitan.app.core.ui.component.HotMode
import com.healthofatitan.app.core.ui.component.HotTopBar
import com.healthofatitan.app.core.ui.theme.HotTheme
import com.healthofatitan.app.domain.path.ComponentKey
import com.healthofatitan.app.domain.path.Criterion
import com.healthofatitan.app.domain.path.GivenLayer
import com.healthofatitan.app.domain.path.GoalChange
import com.healthofatitan.app.domain.path.GoalComponent
import com.healthofatitan.app.domain.path.GoalStatus
import com.healthofatitan.app.domain.path.Initiator
import com.healthofatitan.app.domain.path.ChangeContext
import com.healthofatitan.app.domain.path.ChangeEngine
import com.healthofatitan.app.domain.path.ChangeResult
import com.healthofatitan.app.domain.path.PathMetric
import com.healthofatitan.app.domain.path.PathOverviewBuilder
import com.healthofatitan.app.domain.path.PathTemplates
import com.healthofatitan.app.domain.path.PauseReason
import com.healthofatitan.app.domain.path.PracticalGoal
import com.healthofatitan.app.domain.path.Role
import com.healthofatitan.app.domain.path.Sufficiency
import com.healthofatitan.app.feature.path.CrossroadsSheet
import com.healthofatitan.app.feature.path.DiffCard
import com.healthofatitan.app.feature.path.PathActions
import com.healthofatitan.app.feature.path.PathData
import com.healthofatitan.app.feature.path.PathScreen
import com.healthofatitan.app.feature.path.PathUi
import com.healthofatitan.app.feature.path.RitualActions
import com.healthofatitan.app.feature.path.RitualScreen
import com.healthofatitan.app.feature.path.RitualUi
import com.healthofatitan.app.feature.path.WorldSlice
import com.healthofatitan.app.feature.path.logic.DeadlineChoice
import com.healthofatitan.app.feature.path.logic.PathGiven
import com.healthofatitan.app.feature.path.logic.RitualDraft
import com.healthofatitan.app.feature.path.logic.RitualStep
import com.healthofatitan.app.navigation.AvatarMenu
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * «Путь» v1 (A55, A56, A57, A58-VP): the screen with «Срез мира», the 7 screens of «Назови цель»,
 * the Titan of the horizon, the card «было → станет» and «Перекрёсток». Robolectric native
 * graphics on a Pixel 9 Pro XL width, opt-in with HOT_SCREENSHOTS; still frames (no motion).
 * The numbers are illustrative.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w412dp-h1900dp-xxhdpi")
class PathV1Screenshots {

    @get:Rule
    val compose = createComposeRule()

    private val out = File("build/screenshots")

    @Before
    fun optIn() {
        assumeTrue(System.getenv("HOT_SCREENSHOTS") != null)
        out.mkdirs()
    }

    private fun shoot(name: String, dark: Boolean = true, content: @Composable () -> Unit) {
        compose.setContent { HotTheme(darkTheme = dark) { HotBackground { content() } } }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val today = LocalDate.of(2026, 11, 7)
    private val knee = PathGiven.from(setOf("knee_injury"))

    /** «Силы на вечер», week 5: sleep is the lever (6.1 h of 7), the fire of «Выносливость» is with the person. */
    private fun goal(start: LocalDate = today.minusDays(30), status: GoalStatus = GoalStatus.ACTIVE, body: Boolean = false) = PracticalGoal(
        id = 1,
        title = "Силы на вечер",
        why = "ради сил на вечер с детьми",
        beName = "Тот, у кого есть силы на лето",
        templateId = PathTemplates.ENERGY,
        start = start,
        deadline = start.plusWeeks(12),
        status = status,
        criteria = listOf(Criterion("ночи от 7 ч", 4.0, 5.0, "из 7", PathMetric.SLEEP_NIGHTS)),
        components = listOfNotNull(
            GoalComponent(ComponentKey.SLEEP, Role.LEAD, targetMark = 3, startMark = 2, isLever = true),
            GoalComponent(ComponentKey.CARDIO, Role.PROP, targetMark = 3, startMark = 2, safeVersion = knee.constraintOf(ComponentKey.CARDIO)?.safeVersion),
            GoalComponent(ComponentKey.ATTENTION, Role.PROP, targetMark = 2, startMark = 1),
            GoalComponent(ComponentKey.FUEL, Role.PROP),
            GoalComponent(ComponentKey.FEELINGS, Role.GUARD),
            if (body) GoalComponent(ComponentKey.BODY, Role.PROP) else null,
        ),
        pauseFrom = if (status == GoalStatus.PAUSED) today.minusDays(3) else null,
        pauseReason = if (status == GoalStatus.PAUSED) PauseReason.VACATION else null,
        takenAt = 1L,
    )

    private fun data(g: PracticalGoal?, given: GivenLayer = knee, heavy: Boolean = false): PathData {
        val marks = mapOf(ComponentKey.SLEEP to 2, ComponentKey.CARDIO to 3, ComponentKey.ATTENTION to 1)
        val suff = listOf(Sufficiency(ComponentKey.SLEEP, 6.1, 7.0))
        val ov = PathOverviewBuilder.build(today, g, marks, setOf(ComponentKey.CARDIO), given, suff, heavy)
        return PathData(today, ov, listOfNotNull(g), emptyList(), given, HotMode.ROVNO, 6.1, marks)
    }

    @Composable
    private fun Header() {
        HotTopBar(title = null, overline = "Путь", avatar = { AvatarMenu(MentorPersona.SENSEI, onHelp = {}, onMentor = {}, onSettings = {}) })
    }

    private fun path(name: String, d: PathData, dark: Boolean = true) = shoot(name, dark) {
        Column(Modifier.fillMaxSize()) {
            Header()
            PathScreen(PathUi(d), PathActions(), still = true)
        }
    }

    @Test fun pathLever() = path("p01-path-lever-dark", data(goal()))

    @Test fun pathLight() = path("p02-path-lever-light", data(goal()), dark = false)

    @Test fun pathEmpty() = path("p03-path-empty", data(null))

    @Test fun pathPaused() = path("p04-path-paused", data(goal(status = GoalStatus.PAUSED)))

    @Test fun pathHeavy() = path("p05-path-heavy-week", data(goal(), heavy = true))

    @Test fun pathEdCare() = path("p06-path-ed-care-pillar", data(goal(), given = PathGiven.from(setOf("ed_history", "knee_injury"))))

    // «Назови цель», 7 screens (A56 §2, A58-VP §5.2).
    private val d1 = RitualDraft(step = RitualStep.GOAL, templateId = PathTemplates.ENERGY)
    private val d2 = d1.copy(step = RitualStep.TRUTH, deadline = DeadlineChoice.WEEKS_12, why = "ради сил на вечер с детьми")
    private val d3 = d2.copy(step = RitualStep.BE, beName = "Тот, у кого есть силы на лето")
    private val d4 = d3.copy(step = RitualStep.PARTS)
    private val d5 = d4.copy(step = RitualStep.OBSTACLE, obstacle = "в 21:00 тянет к телефону", ifThen = "Если в 21:00 тянет к телефону, то кладу его на кухне")
    private val d6 = d5.copy(step = RitualStep.REAL)
    private val d7 = d6.copy(step = RitualStep.TAKE)

    private fun ritual(name: String, d: RitualDraft) = shoot(name) {
        RitualScreen(RitualUi(d, data(null)), RitualActions(), still = true)
    }

    @Test fun ritual1() = ritual("p11-ritual-e1-goal", d1)

    @Test fun ritual2() = ritual("p12-ritual-e2-truth-why", d2)

    @Test fun ritual3() = ritual("p13-ritual-e3-be", d3)

    @Test fun ritual4() = ritual("p14-ritual-e4-parts", d4)

    @Test fun ritual5() = ritual("p15-ritual-e5-obstacle", d5)

    @Test fun ritual6() = ritual("p16-ritual-e6-real", d6)

    @Test fun ritual7() = ritual("p17-ritual-e7-take", d7)

    // The Titan of the horizon (A57 §4, A58-VP §2): week 1, week 11, seated, the pillar of light.
    private fun horizon(name: String, vararg views: PathData, dark: Boolean = true) = shoot(name, dark) {
        Column(Modifier.fillMaxSize().padding(top = 12.dp)) {
            views.forEach { WorldSlice(it.overview, Modifier.padding(horizontal = 12.dp), mode = it.mode, still = true) }
        }
    }

    @Test
    fun horizonWeeks() = horizon("p21-horizon-week-1-and-11", data(goal(start = today.minusDays(6))), data(goal(start = today.minusDays(75))))

    @Test
    fun horizonSeatedAndPillar() = horizon(
        "p22-horizon-seated-and-pillar",
        data(goal(status = GoalStatus.PAUSED)),
        data(goal(body = true), given = PathGiven.from(setOf("knee_injury")).copy(edCare = false)),
    )

    @Test
    fun horizonLight() = horizon("p23-horizon-light", data(goal(start = today.minusDays(40))), dark = false)

    // The card «было → станет» and «Перекрёсток» (A58-VP §5.3, §5.4), as the sheets show them.
    @Test
    fun diffCard() = shoot("p31-diff-card") {
        val g = goal()
        val p = (ChangeEngine.propose(g, GoalChange.MoveDeadline(g.deadline.plusWeeks(4)), Initiator.PERSON, ChangeContext(nowMs = 2L, today = today, given = knee)) as ChangeResult.Proposed).proposal
        Column(Modifier.padding(24.dp)) { DiffCard(p, null, PathActions()) }
    }

    @Test
    fun crossroads() = shoot("p32-crossroads") {
        val g = goal().copy(pausedDays = 10)
        Column(Modifier.padding(24.dp)) { CrossroadsSheet(g, after = true, propose = {}, onLater = {}) }
    }
}
