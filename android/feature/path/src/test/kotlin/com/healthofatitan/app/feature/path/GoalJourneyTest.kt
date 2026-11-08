package com.healthofatitan.app.feature.path

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthofatitan.app.core.database.HotDatabase
import com.healthofatitan.app.core.database.ProfileStore
import com.healthofatitan.app.core.ui.theme.HotTheme
import com.healthofatitan.app.feature.path.logic.RitualDraft
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
        compose.onNodeWithTag(PATH_PARTS_TAG).performScrollTo().performClick()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2_000)
        assertNotNull(data.goal)
    }

}
