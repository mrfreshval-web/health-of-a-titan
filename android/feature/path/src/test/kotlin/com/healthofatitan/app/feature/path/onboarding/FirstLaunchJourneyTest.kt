package com.healthofatitan.app.feature.path.onboarding

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthofatitan.app.core.database.HotDatabase
import com.healthofatitan.app.core.database.ProfileStore
import com.healthofatitan.app.core.datastore.FirstLaunchPrefs
import com.healthofatitan.app.core.datastore.FirstLaunchSettings
import com.healthofatitan.app.domain.engine.DayClose
import com.healthofatitan.app.domain.today.GoalSource
import com.healthofatitan.app.feature.path.PathRepository
import com.healthofatitan.app.feature.path.PathWeekBridge
import com.healthofatitan.app.integration.whoop.day.DayPlanRepository
import com.healthofatitan.app.integration.whoop.safety.SafetyBus
import com.healthofatitan.app.integration.whoop.week.WeekRepository
import com.healthofatitan.app.integration.whoop.week.WeekSignals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The whole first launch as one system (A66 §0.2, A67 С-3): Приветствие → «Откуда ты идёшь» →
 * «Куда ты идёшь» (the goal is taken into the Path) → «Первая неделя из Пути» (the week pours the
 * goals of the stage) → «Сегодня» is the home, and the evening «Закрыть день» is kept for that day.
 * Real Room, real repositories; only the first-launch store is in memory.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class FirstLaunchJourneyTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val db = Room.inMemoryDatabaseBuilder(context, HotDatabase::class.java).allowMainThreadQueries().build()
    private val today = LocalDate.of(2026, 11, 10)
    private val clock = Clock.fixed(today.atTime(9, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

    private class MemorySettings : FirstLaunchSettings {
        val state = MutableStateFlow(FirstLaunchPrefs())
        override val prefs: Flow<FirstLaunchPrefs> = state

        override suspend fun setStep(step: String) = state.update { it.copy(step = step) }

        override suspend fun setAnswers(tried: Set<String>, worked: Set<String>, different: Set<String>) =
            state.update { it.copy(tried = tried, worked = worked, different = different) }

        override suspend fun finish() = state.update { it.copy(done = true) }
    }

    private val settings = MemorySettings()
    private val bus = SafetyBus(db.eventLogDao(), clock)
    private val plans = DayPlanRepository(db.metricDao(), db.dayDao(), { null }, clock)
    private val path = PathRepository(db.pathDao(), plans, ProfileStore(db.nutritionDao(), db.consentDao()), clock, bus)
    private val week = WeekRepository(
        db.weekDao(), db.todayDao(), db.dayDao(), db.metricDao(), db.checkinDao(), plans, MutableStateFlow(null), WeekSignals.None, clock,
        path = PathWeekBridge(db.pathDao()),
    )
    private val gate = FirstLaunchGateSource(settings, db.pathDao())

    @Before
    fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun close() {
        Dispatchers.resetMain()
        db.close()
    }

    /** Waits in real time: Room answers on its own threads. */
    private suspend fun <T> await(flow: Flow<T>, what: String, ok: (T) -> Boolean): T =
        withContext(Dispatchers.Default) {
            runCatching { withTimeout(15_000) { flow.first(ok) } }.getOrElse { throw AssertionError("not reached: $what (last: ${flow.first()})", it) }
        }

    @Test
    fun `first launch takes the goal, assembles the week and hands over to Today`() = runTest {
        assertEquals(FirstLaunchGate.SHOW, await(gate.gate, "gate shows") { true })

        val vm = OnboardingViewModel(settings, path, week, db.pathDao(), clock)
        backgroundScope.launch { vm.ui.collect {} }
        await(vm.ui, "welcome") { it?.step == OnboardingStep.WELCOME }

        vm.next {}
        await(vm.ui, "behind") { it?.step == OnboardingStep.BEHIND }
        vm.toggle(0, "Турник")
        vm.toggle(2, OnboardingCopy.DIFF_SLEEP)
        vm.next {}
        await(vm.ui, "path") { it?.step == OnboardingStep.PATH }
        assertEquals(setOf(OnboardingCopy.DIFF_SLEEP), settings.state.value.different)

        vm.goalText("Подтянуться 10 раз")
        vm.why("Чтобы снова чувствовать себя сильным")
        vm.weeks(12)
        val ready = await(vm.ui, "goal can be taken") { it != null && it.goal != null && !it.busy }!!
        assertTrue(ready.canGoOn, "stops: ${ready.stops}")
        assertEquals(12, ready.suggestion.milestones.last().week)

        vm.next {}
        val first = await(vm.ui, "first week") { it?.step == OnboardingStep.WEEK && it.week.isNotEmpty() }!!
        // The goal is in the Path, and the first launch does not vanish under the person on step 3.
        assertNotNull(path.data.first().goal)
        assertEquals(FirstLaunchGate.SHOW, gate.gate.first())
        assertTrue(first.week.size <= OnboardingRules.WEEK_GOALS_MAX)
        assertTrue(first.week.any { it.source == GoalSource.STAGE }, "week: ${first.week}")

        var home = false
        vm.next { home = true }
        await(gate.gate, "today is the home") { it == FirstLaunchGate.DONE }
        assertTrue(home)

        // «Сегодня» → «Закрыть день»: the evening is kept for the day.
        plans.closeDay(today, DayClose(planHelped = true))
        assertEquals(true, db.dayDao().evening(HotDatabase.LOCAL_USER, today.toString())?.planHelped)
    }
}
