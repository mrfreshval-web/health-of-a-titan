package com.healthofatitan.app.feature.path

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthofatitan.app.core.database.HotDatabase
import com.healthofatitan.app.core.database.ProfileStore
import com.healthofatitan.app.core.ui.theme.HotTheme
import com.healthofatitan.app.domain.path.PathCopy
import com.healthofatitan.app.domain.today.CalendarWeek
import com.healthofatitan.app.feature.path.logic.RitualDraft
import com.healthofatitan.app.feature.path.onboarding.BehindAnswers
import com.healthofatitan.app.feature.path.onboarding.OnboardingActions
import com.healthofatitan.app.feature.path.onboarding.OnboardingCopy
import com.healthofatitan.app.feature.path.onboarding.OnboardingRules
import com.healthofatitan.app.feature.path.onboarding.OnboardingScreen
import com.healthofatitan.app.feature.path.onboarding.OnboardingStep
import com.healthofatitan.app.feature.path.onboarding.OnboardingUi
import com.healthofatitan.app.feature.path.logic.RitualLogic
import com.healthofatitan.app.feature.path.logic.RitualStep
import com.healthofatitan.app.integration.whoop.day.DayPlanRepository
import com.healthofatitan.app.integration.whoop.safety.SafetyBus
import com.healthofatitan.app.integration.whoop.week.WeekRepository
import com.healthofatitan.app.integration.whoop.week.WeekSignals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Clock
import java.time.ZoneOffset
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The founder's path (08.11, «при попытке внести цель в Пути — вылетает»): «Назови цель» typed by
 * hand through every step, «Беру путь», and then «Путь» with the goal — the real view models over
 * the real Room and repositories, in motion (not `still`).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w448dp-h998dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GoalJourneyTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val db = Room.inMemoryDatabaseBuilder(context, HotDatabase::class.java).allowMainThreadQueries().build()
    private val clock = Clock.system(ZoneOffset.UTC)
    private val bus = SafetyBus(db.eventLogDao(), clock)
    private val plans = DayPlanRepository(db.metricDao(), db.dayDao(), { null }, clock)
    private val repository = PathRepository(db.pathDao(), plans, ProfileStore(db.nutritionDao(), db.consentDao()), clock, bus)
    private val week = WeekRepository(
        db.weekDao(), db.todayDao(), db.dayDao(), db.metricDao(), db.checkinDao(), plans, MutableStateFlow(null), WeekSignals.None, clock,
        path = PathWeekBridge(db.pathDao()),
    )

    @After
    fun close() = db.close()

    private fun <T> io(block: suspend () -> T): T = runBlocking { withContext(Dispatchers.Default) { withTimeout(15_000) { block() } } }

    @Test
    fun `a goal typed by hand goes through every ritual screen, is taken, and the path in motion shows it`() {
        val text = "Подтянуться 10 раз"
        val empty = io { repository.data.first() }
        var draft by mutableStateOf(RitualDraft(text = text, why = "Чтобы снова чувствовать себя сильным", beName = "Сильный"))
        var shown by mutableStateOf<PathUi?>(null)
        compose.setContent {
            HotTheme {
                val p = shown
                if (p == null) RitualScreen(RitualUi(draft, empty), RitualActions()) else PathScreen(p, PathActions())
            }
        }
        RitualStep.entries.forEach { step ->
            draft = draft.copy(step = step)
            compose.waitForIdle()
            compose.onNodeWithTag(ritualTag(step)).assertExists()
            compose.onRoot().captureToImage() // draws: a crash in a Canvas shows here
        }
        val goal = build(draft, empty)
        assertTrue(RitualLogic.violations(goal, empty.given, PathFeatureFlags.current, empty.goals).isEmpty(), "violations")
        io { repository.start(goal, RitualLogic.firstFire(goal, emptyList()), draft.letter) }
        val data = io { repository.data.first { it.goal != null } }
        val stage = io { PathStageSource(week, db.weekDao(), clock).progress(data.goal).first() }
        shown = PathUi(data, stage = stage)
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithTag(PATH_SCENE_TAG).assertExists()
        compose.onRoot().captureToImage()
        compose.onNodeWithTag(PATH_PARTS_TAG).performScrollTo().performClick()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2_000)
        compose.onRoot().captureToImage()
        assertNotNull(data.goal)
    }


    @Test
    fun `the first launch draws every step with a typed goal and its real first week`() {
        val today = java.time.LocalDate.now(clock)
        val empty = io { repository.data.first() }
        var ui by mutableStateOf(OnboardingUi(step = OnboardingStep.WELCOME, today = today))
        compose.setContent { HotTheme { OnboardingScreen(ui, OnboardingActions()) } }
        compose.onRoot().captureToImage()
        ui = ui.copy(step = OnboardingStep.BEHIND, answers = BehindAnswers(tried = setOf("Турник"), different = setOf(OnboardingCopy.DIFF_SLEEP)))
        compose.waitForIdle()
        compose.onRoot().captureToImage()
        val text = "Подтянуться 10 раз"
        (1..text.length).map { text.take(it) }.forEach { typed ->
            val goal = runCatching { build(RitualDraft(text = typed, why = "Чтобы"), empty) }.getOrNull()?.let { g ->
                g.copy(components = OnboardingRules.withLever(g.components, OnboardingRules.suggestedLever(ui.answers, g.components)), deadline = OnboardingRules.deadline(today, 12))
            }
            val stops = goal?.let { RitualLogic.violations(it, empty.given, PathFeatureFlags.current, empty.goals).map { v -> PathCopy.violation(v) } }.orEmpty()
            ui = ui.copy(step = OnboardingStep.PATH, goalText = typed, why = "Чтобы", goal = goal, suggestion = OnboardingRules.suggestion(goal, ui.answers, today, 12), stops = stops)
            compose.waitForIdle()
            compose.onRoot().captureToImage()
        }
        val goal = assertNotNull(ui.goal)
        io { repository.start(goal, RitualLogic.firstFire(goal, emptyList()), null) }
        val plan = io { week.ensurePlan(CalendarWeek.start(today)).first }
        ui = ui.copy(step = OnboardingStep.WEEK, week = OnboardingRules.weekGoals(plan.goals))
        compose.waitForIdle()
        compose.onRoot().captureToImage()
        assertTrue(ui.week.isNotEmpty(), "the week is poured: ${plan.goals}")
    }
}
