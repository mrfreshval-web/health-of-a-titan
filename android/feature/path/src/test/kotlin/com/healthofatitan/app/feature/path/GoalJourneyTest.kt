package com.healthofatitan.app.feature.path

import android.app.Application
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthofatitan.app.core.database.HotDatabase
import com.healthofatitan.app.core.database.ProfileStore
import com.healthofatitan.app.core.ui.theme.HotTheme
import com.healthofatitan.app.feature.path.logic.RitualDraft
import com.healthofatitan.app.feature.path.logic.RitualStep
import com.healthofatitan.app.integration.whoop.day.DayPlanRepository
import com.healthofatitan.app.integration.whoop.safety.SafetyBus
import com.healthofatitan.app.integration.whoop.week.WeekRepository
import com.healthofatitan.app.integration.whoop.week.WeekSignals
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertNotNull

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

    @Test
    fun `a goal typed by hand goes through the ritual and the path shows it`() {
        val ritual = RitualViewModel(repository)
        val path = PathViewModel(repository, PathStageSource(week, db.weekDao(), clock))
        var taken by mutableStateOf(false)
        compose.setContent {
            HotTheme {
                if (!taken) {
                    val ui by ritual.ui.collectAsState()
                    val state = ui ?: RitualUi(RitualDraft(), PathData.empty(LocalDate.now(clock)))
                    RitualScreen(
                        state,
                        RitualActions(
                            onDraft = ritual::set,
                            onText = ritual::text,
                            onNext = ritual::next,
                            onBack = ritual::back,
                            onTake = { ritual.take { taken = true } },
                        ),
                    )
                } else {
                    val ui by path.ui.collectAsState()
                    PathScreen(ui ?: PathUi(PathData.empty(LocalDate.now(clock))), PathActions())
                }
            }
        }
        compose.waitUntil(10_000) { ritual.ui.value != null }
        compose.onAllNodesWithTag(RITUAL_FIELD_TAG).onFirst().performTextInput("Подтянуться 10 раз")
        compose.waitForIdle()
        RitualStep.entries.forEach { step ->
            compose.waitUntil(10_000) { ritual.ui.value?.draft?.step == step }
            compose.onNodeWithTag(ritualTag(step)).assertExistsSafely()
            compose.onNodeWithTag(RITUAL_NEXT_TAG).performScrollTo().performClick()
            compose.waitForIdle()
        }
        compose.waitUntil(15_000) { taken }
        compose.waitUntil(15_000) { path.ui.value?.data?.goal != null }
        compose.waitForIdle()
        compose.onNodeWithTag(PATH_SCENE_TAG).assertExistsSafely()
        assertNotNull(path.ui.value?.data?.goal)
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertExistsSafely() = assertExists()
}
