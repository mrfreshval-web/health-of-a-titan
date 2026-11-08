package com.healthofatitan.app.screenshots

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthofatitan.app.core.ui.component.HotBackground
import com.healthofatitan.app.core.ui.theme.HotTheme
import com.healthofatitan.app.domain.today.Corridor
import com.healthofatitan.app.domain.today.DayMode
import com.healthofatitan.app.domain.today.WeekPlan
import com.healthofatitan.app.domain.today.week.BodySeries
import com.healthofatitan.app.domain.today.week.DayFact
import com.healthofatitan.app.domain.today.week.Experiment
import com.healthofatitan.app.domain.today.week.ExperimentMetric
import com.healthofatitan.app.domain.today.week.ExperimentPoint
import com.healthofatitan.app.domain.today.week.ExperimentRules
import com.healthofatitan.app.domain.today.week.ExperimentSeed
import com.healthofatitan.app.domain.today.week.ExperimentStatus
import com.healthofatitan.app.domain.today.week.GoalDays
import com.healthofatitan.app.domain.today.week.LastWeek
import com.healthofatitan.app.domain.today.week.PathWeek
import com.healthofatitan.app.domain.today.week.WeekFace
import com.healthofatitan.app.domain.today.week.Pattern
import com.healthofatitan.app.domain.today.week.PlanStatus
import com.healthofatitan.app.domain.today.week.RitualRules
import com.healthofatitan.app.domain.today.week.RitualState
import com.healthofatitan.app.domain.today.week.RitualStep
import com.healthofatitan.app.domain.today.week.SupportsWeekInput
import com.healthofatitan.app.domain.today.week.WeekBuilder
import com.healthofatitan.app.domain.today.week.WeekCopy
import com.healthofatitan.app.domain.today.week.WeekGoalRules
import com.healthofatitan.app.domain.today.week.WeekInput
import com.healthofatitan.app.domain.today.week.WeekKind
import com.healthofatitan.app.feature.week.WeekRitualScreen
import com.healthofatitan.app.feature.week.WeekScreen
import com.healthofatitan.app.feature.week.WeekSeal
import com.healthofatitan.app.feature.week.WeekTitanFigure
import com.healthofatitan.app.feature.week.WeekUiState
import com.healthofatitan.app.integration.whoop.week.WeekScreenState
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * «Неделя» v1 (A52 v0.1, D-044, D-045): a normal week, a week of «Бережно», the first week without
 * data, the Sunday ritual and the Titan of the week, on the Pixel 9 Pro XL width. Placeholder
 * graphics (TitanLottie) until Agents 4 and 17. Opt-in (HOT_SCREENSHOTS).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w412dp-h2200dp-xxhdpi")
class WeekV1Screenshots {

    @get:Rule
    val compose = createComposeRule()

    private val out = File("build/screenshots")

    @Before
    fun optIn() {
        assumeTrue(System.getenv("HOT_SCREENSHOTS") != null)
        out.mkdirs()
    }

    private fun shoot(name: String, content: @Composable () -> Unit) {
        compose.setContent { HotTheme { HotBackground(Modifier.fillMaxSize()) { content() } } }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(3_000)
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val monday = LocalDate.of(2026, 11, 2)
    private val friday: LocalDateTime = monday.plusDays(4).atTime(20, 0)
    private val sunday: LocalDateTime = monday.plusDays(6).atTime(19, 0)

    private val full = listOf(0.9f, 0.8f, 0.75f, 0.5f, 0.4f)
    private val thin = listOf(0.6f, 0.4f, 0.3f, 0.2f, 0.1f)

    private fun fact(offset: Int, fills: List<Float>? = full, tasks: Int = 1, mode: DayMode? = DayMode.ROVNO) =
        DayFact(monday.plusDays(offset.toLong()), mode, fills, tasks, sick = false)

    private fun plan(): WeekPlan {
        val base = WeekGoalRules.defaults(monday, first = false)
        val progress = mapOf(WeekGoalRules.ID_TRAINING to 2, WeekGoalRules.ID_FOCUS to 3, WeekGoalRules.ID_ZONES to 95, WeekGoalRules.ID_SLEEP to 3)
        return base.copy(goals = base.goals.map { it.copy(progress = progress[it.id] ?: 0) })
    }

    private fun series(name: String, values: List<Double?>, mean: Double, sd: Double) = BodySeries(name, values, Corridor(mean, sd, 28))

    private val body = listOf(
        series(WeekCopy.B_RHR, listOf(55.0, 54.0, 56.0, 53.0, 54.0, null, null), 55.0, 2.0),
        series(WeekCopy.B_HRV, listOf(50.0, 58.0, 61.0, 59.0, 62.0, null, null), 52.0, 6.0),
        series(WeekCopy.B_RESP, listOf(14.5, 14.4, 14.6, 14.4, 14.5, null, null), 14.4, 0.4),
        series(WeekCopy.B_SPO2, listOf(96.0, 96.5, 96.0, 95.8, 96.2, null, null), 96.0, 0.8),
        series(WeekCopy.B_SKIN, listOf(33.3, 33.2, 33.4, 33.3, 33.3, null, null), 33.3, 0.3),
    )

    private val patterns = listOf(
        Pattern("LIGHT_CHARGE", "В утра со светом в первые полчаса бодрость выше", evidenceN = 9, effect = 0.6, action = "Свет в первые 30 минут"),
        Pattern("SHOWER_CHARGE", "В утра с холодным душем Заряд чаще выше", evidenceN = 7, effect = 0.4, action = "Холодный душ утром"),
    )

    private val supports = SupportsWeekInput(
        balanceZ = listOf(0.8, 1.3, 1.2, 1.1, 0.4), chargeInNorm = true, stepDays = 4, stepDaysKnown = 5, zoneMinutes = 95,
        drinkShare = 0.7, sleepDebtHours = 2.5, regularityPct = 78.0, nightsKnown = 5,
    )

    private fun normalInput(now: LocalDateTime) = WeekInput(
        now = now,
        facts = listOf(fact(0), fact(1, tasks = 2), fact(2, thin), fact(3), fact(4, mode = DayMode.RAZGON)) +
            if (now.toLocalDate() == sunday.toLocalDate()) listOf(fact(5), fact(6, thin, tasks = 0)) else emptyList(),
        plan = plan(),
        planStatus = PlanStatus.APPROVED,
        supports = supports,
        supportFills = listOf(0.82f, 0.7f, 0.66f, 0.45f, 0.35f),
        body = body,
        patterns = patterns,
        previousDaysInSupport = 3,
        pathSteps = 1,
        experiment = Experiment(monday, seed, ExperimentStatus.TAKEN),
        experimentDays = setOf(monday, monday.plusDays(1), monday.plusDays(3), monday.plusDays(4)),
    )

    private val seed = ExperimentSeed("exp-screens_sleep", "Экран убран в 22:30 → сон лучше?", "Экран убран в 22:30", ExperimentMetric.SLEEP)
    private val nextSeed = ExperimentSeed("exp-light_charge", "Свет в первые 30 минут → Заряд лучше?", "Свет в первые 30 минут", ExperimentMetric.CHARGE)

    private val answer = ExperimentRules.result(
        seed,
        listOf(440.0, 450.0, 430.0, 445.0, 460.0, 400.0).mapIndexed { i, v -> ExperimentPoint(monday.plusDays(i.toLong()), true, v) } +
            listOf(420.0, 425.0, 415.0).mapIndexed { i, v -> ExperimentPoint(monday.minusDays(i + 1L), false, v) },
    )

    private fun state(input: WeekInput, withRitual: Boolean = false): WeekScreenState {
        val view = WeekBuilder.build(input)
        val frames = (1..5).map { view.titan.copy(weekStart = monday.minusWeeks(it.toLong()), reveal = 1f) }.reversed() + view.titan
        val ritual = if (withRitual) {
            val proposal = WeekGoalRules.propose(monday.plusDays(7), input.plan, LastWeek(0.8, 0, chargeInNorm = true), WeekKind.NORMAL)
            RitualRules.withBeforeAfter(RitualRules.view(view, proposal, answer, listOf(nextSeed, seed)), input.previousDaysInSupport, view.daysInSupport)
        } else {
            null
        }
        return WeekScreenState(
            view = view, frames = frames, ritual = ritual, suggestUp = ritual?.proposal?.suggestUp.orEmpty(),
            path = path, goalDays = goalDays, nights = WeekFace.nights(listOf(6.4, 7.1, 6.9, 6.5, 6.8)), behind = WeekFace.behind(38, 90),
        )
    }

    /** A66 §4: the stage of a goal of the Path (week 3 of 12) and the days each goal was done. */
    private val path = PathWeek.Active("Сильнее к весне", listOf(WeekGoalRules.ID_TRAINING, WeekGoalRules.ID_SLEEP), monday.minusDays(14))
    private val goalDays = mapOf(
        WeekGoalRules.ID_TRAINING to GoalDays(setOf(monday, monday.plusDays(2))),
        WeekGoalRules.ID_SLEEP to GoalDays(setOf(monday, monday.plusDays(1), monday.plusDays(3)), known = (0..4L).map { monday.plusDays(it) }.toSet()),
        WeekGoalRules.ID_FOCUS to GoalDays(setOf(monday, monday.plusDays(1), monday.plusDays(3))),
    )

    @Test
    fun weekNoPath() = shoot("week_v3_no_path") {
        WeekScreen(WeekUiState(state(normalInput(friday)).copy(path = PathWeek.NoGoal)))
    }

    @Test
    fun weekNormal() = shoot("week_v1_normal") {
        WeekScreen(WeekUiState(state(normalInput(friday))))
    }

    @Test
    fun weekBerezhno() = shoot("week_v1_berezhno") {
        val gentle = (0..4).map { fact(it, thin, tasks = if (it == 2) 0 else 1, mode = DayMode.BEREZHNO) }
        val input = normalInput(friday).copy(
            facts = gentle,
            supports = supports.copy(sleepDebtHours = 6.0, regularityPct = 52.0),
            supportFills = listOf(0.55f, 0.5f, 0.45f, 0.3f, 0.2f),
        )
        WeekScreen(WeekUiState(state(input)))
    }

    @Test
    fun weekFirst() = shoot("week_v1_first_week") {
        val input = WeekInput(
            now = monday.plusDays(1).atTime(10, 0),
            facts = emptyList(),
            plan = WeekGoalRules.defaults(monday, first = true),
            planStatus = PlanStatus.PROPOSED,
            kind = WeekKind.FIRST,
            whoopConnected = false,
        )
        val view = WeekBuilder.build(input)
        WeekScreen(WeekUiState(WeekScreenState(view = view, whoopConnected = false)))
    }

    @Test
    fun weekSundayCard() = shoot("week_v1_sunday") {
        WeekScreen(WeekUiState(state(normalInput(sunday), withRitual = true)))
    }

    @Test
    fun weekRitualFrame() = shoot("week_v1_ritual_frame") {
        WeekRitualScreen(state(normalInput(sunday), withRitual = true), emptyList(), onDone = {}, onClose = {}, onShare = {}, startAt = RitualStep.FRAME)
    }

    @Test
    fun weekRitualNext() = shoot("week_v1_ritual_next") {
        WeekRitualScreen(state(normalInput(sunday), withRitual = true), emptyList(), onDone = {}, onClose = {}, onShare = {}, startAt = RitualStep.NEXT)
    }

    @Test
    fun weekRitualExperiment() = shoot("week_v1_ritual_experiment") {
        WeekRitualScreen(state(normalInput(sunday), withRitual = true), emptyList(), onDone = {}, onClose = {}, onShare = {}, startAt = RitualStep.EXPERIMENT)
    }

    @Test
    fun weekSeal() = shoot("week_v1_seal_thursday") {
        val look = WeekBuilder.build(normalInput(monday.plusDays(3).atTime(9, 0))).titan
        Box(Modifier.fillMaxSize().padding(32.dp)) {
            WeekSeal(look, Modifier.fillMaxWidth())
        }
    }

    @Test
    fun weekTitan() = shoot("week_v1_titan") {
        val look = WeekBuilder.build(normalInput(sunday).copy(ritual = RitualState(doneAt = 1L, pride = "первое подтягивание"))).titan
        Box(Modifier.fillMaxSize().padding(16.dp)) {
            WeekTitanFigure(look, Modifier.fillMaxWidth().height(560.dp), revealOverride = 1f)
        }
    }
}
