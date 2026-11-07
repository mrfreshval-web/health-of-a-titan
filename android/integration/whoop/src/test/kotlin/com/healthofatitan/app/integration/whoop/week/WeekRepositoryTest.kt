package com.healthofatitan.app.integration.whoop.week

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthofatitan.app.core.database.HotDatabase
import com.healthofatitan.app.core.database.ProfileSettingsStore
import com.healthofatitan.app.core.model.profile.AddressForm
import com.healthofatitan.app.core.model.profile.ProfileSettings
import com.healthofatitan.app.core.model.profile.SexForCalc
import com.healthofatitan.app.domain.today.week.Sex
import com.healthofatitan.app.core.database.entity.TodayMarkEntity
import com.healthofatitan.app.domain.today.DayMode
import com.healthofatitan.app.domain.today.Scenario
import com.healthofatitan.app.domain.today.TaskRules
import com.healthofatitan.app.domain.today.week.ExperimentMetric
import com.healthofatitan.app.domain.today.week.ExperimentOutcome
import com.healthofatitan.app.domain.today.week.ExperimentSeed
import com.healthofatitan.app.domain.today.week.PlanStatus
import com.healthofatitan.app.domain.today.week.RitualStep
import com.healthofatitan.app.domain.today.week.RitualAnswers
import com.healthofatitan.app.domain.today.week.WeekCopy
import com.healthofatitan.app.domain.today.week.WeekGoalRules
import com.healthofatitan.app.integration.whoop.WhoopDay
import com.healthofatitan.app.integration.whoop.WhoopGlance
import com.healthofatitan.app.integration.whoop.WorkoutZones
import com.healthofatitan.app.integration.whoop.day.DayPlanRepository
import com.healthofatitan.app.integration.whoop.day.TodayMarksRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A clock the test moves by hand. */
private class TestClock(var now: LocalDateTime, private val zone: ZoneId = ZoneOffset.UTC) : Clock() {
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = TestClock(now, zone)
    override fun instant(): Instant = now.atZone(zone).toInstant()
}

/**
 * The real [com.healthofatitan.app.integration.whoop.day.WeekPlanSource] (A52 §2.2, §4) over an
 * in-memory Room 11: «Не сегодня» back to the week goal (W-05), the auto-assembly after 48 h
 * (W-06), the Sunday ritual, the one push, `zone_durations` from WHOOP memory and the day score
 * stored at 04:00 the next day.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class WeekRepositoryTest {

    private val monday = LocalDate.of(2026, 11, 2)
    private lateinit var db: HotDatabase
    private lateinit var clock: TestClock
    private val glance = MutableStateFlow<WhoopGlance?>(null)
    private lateinit var repo: WeekRepository
    private lateinit var marks: TodayMarksRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Application>(), HotDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        clock = TestClock(monday.atTime(9, 0))
        val plans = DayPlanRepository(db.metricDao(), db.dayDao(), { null }, clock)
        repo = WeekRepository(db.weekDao(), db.todayDao(), db.dayDao(), db.metricDao(), db.checkinDao(), plans, glance, WeekSignals.None, clock)
        marks = TodayMarksRepository(db.todayDao(), clock)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `W-05 not today sends the task back to its week goal and the next day offers it again`() = runTest {
        val wednesday = monday.plusDays(2)
        clock.now = wednesday.atTime(10, 0)
        repo.addReminder("Витамин D по этикетке", 3)
        val id = db.weekDao().goals("local", monday.toString()).first { it.goalId.startsWith(WeekGoalRules.REMINDER_PREFIX) }.goalId
        val task = repo.candidates(wednesday).first().first { it.weekGoalId == id }
        assertEquals(WeekGoalRules.taskId(id), task.id)

        val move = TaskRules.notToday(task, wednesday)
        marks.toWeek(move)
        val returned = marks.observe(wednesday).first().toWeek
        assertTrue(task.id in returned)
        assertTrue(TaskRules.pick(Scenario.DAY, DayMode.ROVNO, repo.candidates(wednesday).first(), returned).none { it.id == task.id })
        assertEquals("$id", db.todayDao().day("local", wednesday.toString()).single { it.kind == TodayMarkEntity.KIND_TASK_TO_WEEK }.goal)
        // the goal keeps everything that is left
        val goal = repo.week(wednesday).first().goal(id)!!
        assertEquals(0, goal.progress)
        assertEquals(3, goal.target)
        assertEquals(1, repo.load(clock.now, null).view.returnedToday)

        // Thursday: offered again; done → the goal moves
        val thursday = wednesday.plusDays(1)
        clock.now = thursday.atTime(9, 0)
        val again = repo.candidates(thursday).first().first { it.weekGoalId == id }
        assertFalse(again.done)
        marks.taskDone(thursday, again.id, true)
        assertEquals(1, repo.week(thursday).first().goal(id)!!.progress)
        assertTrue(repo.candidates(thursday).first().first { it.weekGoalId == id }.done)
    }

    @Test
    fun `W-06 a week not approved is assembled by HoT 48 hours after Sunday began, with a note`() = runTest {
        repo.ensurePlan(monday)
        assertEquals(PlanStatus.PROPOSED.name, db.weekDao().plan("local", monday.toString())!!.status)
        assertEquals(WeekCopy.GOAL_FIRST_WEEK, repo.load(clock.now, null).view.goalsNote)

        val next = monday.plusDays(7)
        clock.now = next.atTime(9, 0)
        assertEquals(WeekCopy.GOAL_PROPOSED, repo.load(clock.now, null).view.goalsNote)
        assertEquals(PlanStatus.PROPOSED.name, db.weekDao().plan("local", next.toString())!!.status)

        clock.now = next.plusDays(1).atTime(8, 0)
        val tuesday = repo.load(clock.now, null)
        assertEquals(WeekCopy.GOAL_AUTO, tuesday.view.goalsNote)
        assertEquals(PlanStatus.AUTO.name, db.weekDao().plan("local", next.toString())!!.status)
        assertNull(tuesday.ritual)
    }

    @Test
    fun `the Sunday ritual approves the next week, keeps the pride and reveals the Titan`() = runTest {
        repo.ensurePlan(monday)
        clock.now = monday.plusDays(6).atTime(19, 0)
        val sunday = repo.load(clock.now, null)
        assertTrue(sunday.view.ritualCard)
        val ritual = assertNotNull(sunday.ritual)
        assertEquals(monday.plusDays(7), ritual.proposal.plan.weekStart)

        repo.finishRitual(ritual, ritual.skew, RitualAnswers(pride = "первое подтягивание", targets = mapOf(WeekGoalRules.ID_FOCUS to 2)))
        val next = db.weekDao().plan("local", monday.plusDays(7).toString())!!
        assertEquals(PlanStatus.APPROVED.name, next.status)
        assertEquals(2, db.weekDao().goals("local", monday.plusDays(7).toString()).first { it.goalId == WeekGoalRules.ID_FOCUS }.target)
        assertEquals("первое подтягивание", db.weekDao().plan("local", monday.toString())!!.pride)

        val after = repo.load(clock.now, null)
        assertTrue(after.view.ritualDone)
        assertFalse(after.view.ritualCard)
        assertEquals(1f, after.view.titan.reveal)
        assertNotNull(db.weekDao().frame("local", monday.toString())!!.revealedAt)
    }

    @Test
    fun `D-050 the ritual takes an experiment, the days fill its scale, Sunday answers and tells the Path`() = runTest {
        repo.ensurePlan(monday)
        clock.now = monday.plusDays(6).atTime(19, 0)
        val ritual = assertNotNull(repo.load(clock.now, null).ritual)
        val seed = ExperimentSeed("exp-screens_sleep", "Экран убран в 22:30 → сон лучше?", "Экран убран в 22:30", ExperimentMetric.SLEEP)
        repo.finishRitual(ritual, ritual.skew, RitualAnswers(experiment = seed))
        val event = repo.revealed.first()
        assertEquals(monday, event.weekStart)
        val next = monday.plusDays(7)
        assertEquals("TAKEN", db.weekDao().experiment("local", next.toString())!!.status)

        // the week runs: two days with the action
        clock.now = next.atTime(21, 0)
        repo.markExperiment(next, true)
        clock.now = next.plusDays(1).atTime(21, 0)
        repo.markExperiment(next.plusDays(1), true)
        val tuesday = repo.load(clock.now, null).view
        val e = assertNotNull(tuesday.experiment)
        assertEquals(2, e.scale.done)
        assertTrue(e.doneToday)
        assertEquals("Эксперимент · 2 из 5 ›", repo.experimentBridge(next.plusDays(1)).first())

        // next Sunday without WHOOP memory: too few points, and that is still «узнали»
        clock.now = next.plusDays(6).atTime(19, 0)
        val sunday = assertNotNull(repo.load(clock.now, null).ritual)
        val result = assertNotNull(sunday.experimentResult)
        assertEquals(ExperimentOutcome.TOO_FEW, result.outcome)
        assertEquals(WeekCopy.EXP_LEARNED, result.headline)
        assertEquals(RitualStep.EXPERIMENT, sunday.steps[1])
        repo.finishRitual(sunday, sunday.skew, RitualAnswers())
        assertEquals(ExperimentOutcome.TOO_FEW.name, db.weekDao().experiment("local", next.toString())!!.outcome)
    }

    @Test
    fun `D-050 the fitness age waits for the choice of the person`() = runTest {
        repo.ensurePlan(monday)
        repo.setFitnessOptIn(true)
        val state = repo.load(clock.now, null)
        assertTrue(state.fitnessOptedIn)
        assertNull(state.view.fitness, "the flag is off by default and the first milestone comes after 4 weeks")
        repo.setFitnessOptIn(false)
        assertFalse(repo.load(clock.now, null).fitnessOptedIn)
    }

    @Test
    fun `one push a week, on Sunday evening`() = runTest {
        repo.ensurePlan(monday)
        var shown = 0
        val notifier = WeekNotifier { title, text ->
            assertEquals(WeekCopy.PUSH_TITLE, title)
            assertFalse(text.any { it.isDigit() })
            shown++
            true
        }
        clock.now = monday.plusDays(5).atTime(19, 0)
        assertFalse(repo.pushIfDue(notifier))
        clock.now = monday.plusDays(6).atTime(19, 0)
        assertTrue(repo.pushIfDue(notifier))
        clock.now = monday.plusDays(6).atTime(20, 0)
        assertFalse(repo.pushIfDue(notifier))
        assertEquals(1, shown)
    }

    @Test
    fun `zone durations of WHOOP workouts fill the zones goal and a closed day is stored at 04_00`() = runTest {
        val start = monday.atTime(18, 0).toInstant(ZoneOffset.UTC)
        glance.value = WhoopGlance(
            fetchedAt = 0L, sleep = null, recovery = null, workout = null,
            days = listOf(
                WhoopDay(monday, strain = 12.0, steps = 9_500, workouts = listOf(WorkoutZones(start, 50, listOf(2, 8, 20, 12, 6, 2)))),
                WhoopDay(monday.minusDays(1), strain = 9.0, workouts = listOf(WorkoutZones(start.minusSeconds(86_400), 30, listOf(0, 0, 30, 0, 0, 0)))),
            ),
        )
        marks.taskDone(monday, WeekGoalRules.taskId(WeekGoalRules.ID_FOCUS), true)
        repeat(8) { marks.water(monday, +1) }
        TodayMarkEntity.KIND_MEAL.let { listOf("breakfast", "lunch", "snack", "dinner").forEach { slot -> marks.meal(monday, slot, true) } }

        clock.now = monday.plusDays(1).atTime(3, 0)
        repo.load(clock.now, glance.value)
        assertEquals(0, db.weekDao().scores("local", monday.toString(), monday.toString()).size, "not before 04:00")

        clock.now = monday.plusDays(1).atTime(5, 0)
        val state = repo.load(clock.now, glance.value)
        val zones = state.view.goals.first { it.goal.id == WeekGoalRules.ID_ZONES }
        assertEquals(40, zones.goal.progress) // zones 2–5 of Monday only, Sunday before is another week
        val stored = db.weekDao().scores("local", monday.toString(), monday.toString()).single()
        assertEquals(1, stored.tasksDone)
        assertTrue(stored.supportsFilled >= 3)
        assertTrue(stored.inSupport)
        assertEquals(1, state.view.daysInSupport)

        // after a restart (no WHOOP memory) the stored score of Monday holds
        val cold = repo.load(clock.now, null)
        assertTrue(cold.view.columns.first().inSupport)
    }

    @Test
    fun `age and sex of the fitness age live in the profile and its choice wins`() = runTest {
        // A61 §2.1, D-055: one place for the year of birth and the sex for calculations.
        val profile = ProfileSettingsStore(db.personProfileDao())
        val plans = DayPlanRepository(db.metricDao(), db.dayDao(), { null }, clock)
        val withProfile = WeekRepository(
            db.weekDao(), db.todayDao(), db.dayDao(), db.metricDao(), db.checkinDao(), plans, glance, WeekSignals.None, clock,
            profile = profile,
        )
        assertEquals(BodyInputs(), withProfile.bodyInputs())

        withProfile.saveBodyInputs(38, Sex.MALE, 88.0)
        assertEquals(ProfileSettings(birthYear = 1988, sexForCalc = SexForCalc.MALE), profile.current())
        val inputs = withProfile.bodyInputs()
        assertEquals(38, inputs.ageYears)
        assertEquals(Sex.MALE, inputs.sex)
        assertEquals(88.0, inputs.waistCm)

        // «не указывать» in the profile: the old row does not bring the sex back
        profile.setSexForCalc(SexForCalc.UNSPECIFIED)
        assertNull(withProfile.bodyInputs().sex)
        profile.setSexForCalc(SexForCalc.FEMALE)
        assertEquals(Sex.FEMALE, withProfile.bodyInputs().sex)
        // the address form is a separate field and changes nothing here
        profile.setAddressForm(AddressForm.MASCULINE)
        assertEquals(Sex.FEMALE, withProfile.bodyInputs().sex)
    }
}

