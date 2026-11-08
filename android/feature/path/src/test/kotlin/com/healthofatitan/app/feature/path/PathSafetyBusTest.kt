package com.healthofatitan.app.feature.path

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthofatitan.app.core.database.HotDatabase
import com.healthofatitan.app.domain.engine.CrisisAssessment
import com.healthofatitan.app.domain.engine.CrisisLevel
import com.healthofatitan.app.domain.engine.CrisisReason
import com.healthofatitan.app.domain.engine.CrisisSource
import com.healthofatitan.app.domain.path.BalanceKind
import com.healthofatitan.app.domain.path.ComponentKey
import com.healthofatitan.app.domain.path.Criterion
import com.healthofatitan.app.domain.path.GivenLayer
import com.healthofatitan.app.domain.path.GoalComponent
import com.healthofatitan.app.domain.path.PathMetric
import com.healthofatitan.app.domain.path.PathOverviewBuilder
import com.healthofatitan.app.domain.path.PathTemplates
import com.healthofatitan.app.domain.path.PracticalGoal
import com.healthofatitan.app.domain.path.Role
import com.healthofatitan.app.domain.safety.EdCareReason
import com.healthofatitan.app.domain.safety.Restriction
import com.healthofatitan.app.domain.safety.SafetyConsumer
import com.healthofatitan.app.domain.safety.SafetyState
import com.healthofatitan.app.feature.path.logic.PathBus
import com.healthofatitan.app.integration.whoop.day.DailyLoopRepository
import com.healthofatitan.app.integration.whoop.safety.SafetyBus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * One safety state for the three tabs (D-051, A60 §3, L-01, L-13): a crisis CrisisGuard logs in
 * the evening reaches the journal, «Неделя» sees it (no Sunday push, help first) and «Путь» holds
 * with one hand — from the same bus, not from a copy of its own. `ED_CARE` leaves only by the
 * person's choice.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class PathSafetyBusTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val db = Room.inMemoryDatabaseBuilder(context, HotDatabase::class.java).allowMainThreadQueries().build()
    private val today = LocalDate.of(2026, 11, 7)

    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = now
    }

    private val clock = MutableClock(today.atTime(21, 0).toInstant(ZoneOffset.UTC))
    private val bus = SafetyBus(db.eventLogDao(), clock)

    @After
    fun close() = db.close()

    private val goal = PracticalGoal(
        id = 1, title = "Силы на вечер", why = "ради сил на вечер", templateId = PathTemplates.ENERGY,
        start = today.minusDays(30), deadline = today.plusDays(54),
        criteria = listOf(Criterion("ночи от 7 ч", 4.0, 5.0, "из 7", PathMetric.SLEEP_NIGHTS)),
        components = listOf(GoalComponent(ComponentKey.SLEEP, Role.LEAD, 3, 2, isLever = true)),
    )

    private fun pathOf(state: SafetyState) =
        PathOverviewBuilder.build(today, goal, emptyMap(), emptySet(), PathBus.given(GivenLayer.NONE, state), safety = PathBus.safety(state))

    @Test
    fun `without flags the path is as the goal and the week say`() = runTest {
        val state = bus.state.first()
        assertEquals(SafetyState.NORMAL, state)
        assertFalse(pathOf(state).balance.kind == BalanceKind.HEAVY)
    }

    @Test
    fun `a crisis of the evening is seen by the week and by the path, and ends with its window`() = runTest {
        val loop = DailyLoopRepository(db.dailyLoopDao(), clock, bus)
        loop.logSafety(CrisisAssessment(CrisisLevel.PASSIVE, CrisisReason.INDIRECT), CrisisSource.EVENING)

        val state = bus.current()
        assertTrue(state.crisis)
        // «Неделя»: the Sunday push stays silent and help stands first (WeekSignals.crisis reads this)
        assertTrue(Restriction.NO_PUSH in state.restrictions(SafetyConsumer.WEEK))
        assertTrue(Restriction.HELP_FIRST in state.restrictions(SafetyConsumer.WEEK))
        // «Сегодня»: the sober day with help first
        assertTrue(Restriction.HELP_FIRST in state.restrictions(SafetyConsumer.TODAY))
        // «Путь»: the same bus holds the path with one hand, «Зачем» is not pushed on top
        val path = pathOf(state)
        assertEquals(BalanceKind.HEAVY, path.balance.kind)
        assertFalse(path.whyVisible)
        assertEquals(state, bus.state.first(), "the flow and the snapshot are one state")

        clock.now = clock.now.plus(Duration.ofHours(73))
        assertFalse(bus.current().crisis)
        assertFalse(pathOf(bus.current()).balance.kind == BalanceKind.HEAVY)
    }

    @Test
    fun `ED_CARE closes weight on the path and leaves only by the person's choice after the cooldown`() = runTest {
        bus.enterEdCare(EdCareReason.SCREEN)
        assertTrue(pathOf(bus.current()).lightOnly)

        clock.now = clock.now.plus(Duration.ofDays(13))
        assertFalse(bus.chooseEdCareExit(), "before 14 days the choice changes nothing")
        clock.now = clock.now.plus(Duration.ofDays(60))
        assertTrue(bus.current().edCare.active, "HoT never ends it by itself")
        assertTrue(bus.chooseEdCareExit())
        val after = bus.current()
        assertFalse(after.edCare.active)
        assertTrue(Restriction.HIDE_WEIGHT in after.restrictions(SafetyConsumer.PATH), "weight does not come back by itself")
        assertTrue(Restriction.HIDE_BODY_PHOTO in after.restrictions(SafetyConsumer.PATH))
        assertTrue(pathOf(after).lightOnly)
    }
}
