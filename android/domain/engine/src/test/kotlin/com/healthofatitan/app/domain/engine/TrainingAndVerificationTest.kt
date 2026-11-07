package com.healthofatitan.app.domain.engine

import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A19 §1.2 weekly queue and modifiers, A35 §4 matching, the morning push timing. */
class TrainingAndVerificationTest {

    private val wednesday = LocalDate.of(2026, 11, 4)
    private val boostNight = NightFacts(orsZ = 0.9, orsEwma7 = 0.3, sleepHours = 7.8, prevSleepHours = 7.5)

    private fun session(daysAgo: Long, kind: SessionKind, intensity: Intensity, minutes: Int = 45, startMin: Int = 18 * 60, id: String = "w$daysAgo$kind") =
        Session(id, wednesday.minusDays(daysAgo), startMin, minutes, kind, intensity)

    /** Strength twice a week for four weeks: level «средний», last strength yesterday. */
    private val regular = (1L..28L).filter { it % 3L == 1L }.map { session(it, SessionKind.STRENGTH, Intensity.MIXED) }

    private fun plan(history: List<Session>, schedule: ScheduleFacts = ScheduleFacts(), night: NightFacts = boostNight) =
        DayEngine.plan(DayInput(wednesday, night, history = history, schedule = schedule, yesterday = YesterdayFacts(regime = Regime.BOOST)))

    @Test
    fun `BOOST with a regular strength routine asks for intervals`() {
        val p = plan(regular)
        assertEquals(Regime.BOOST, p.regime)
        assertEquals(TrainingType.INTERVALS, p.training.type)
        assertEquals("T11", p.training.templateId)
    }

    @Test
    fun `a sore body replaces the queued intervals with Zone 2 and says so`() {
        val p = DayEngine.plan(
            DayInput(wednesday, boostNight, history = regular, yesterday = YesterdayFacts(regime = Regime.BOOST), evening = EveningFacts(body = Body.SORE)),
        )
        assertEquals(Regime.BOOST, p.regime)
        assertEquals(TrainingType.ZONE2, p.training.type)
        assertEquals(TrainingType.INTERVALS, p.training.replaced)
        assertTrue(p.fired.any { it.ref == RuleSet.A19_QUEUE && it.why.contains("вместо") })
    }

    @Test
    fun `a late usual slot turns intensive work into Zone 2 (TR-07)`() {
        val p = plan(regular, ScheduleFacts(trainingStartMin = 21 * 60, bedtimeMin = 23 * 60))
        assertEquals(TrainingType.ZONE2, p.training.type)
        assertTrue(p.fired.any { it.ref == RuleSet.TR_07 })
        assertTrue(p.training.startMin!! + p.training.minutes <= 22 * 60, "ends before the wind-down hour")
    }

    @Test
    fun `no strength this week by Saturday asks for strength`() {
        val saturday = LocalDate.of(2026, 11, 7)
        val cardio = listOf(1L, 2L, 3L).map { Session("c$it", saturday.minusDays(it), 7 * 60, 40, SessionKind.CARDIO, Intensity.ZONE2) }
        val p = DayEngine.plan(DayInput(saturday, boostNight, history = cardio))
        assertEquals(TrainingType.STRENGTH, p.training.type)
    }

    @Test
    fun `six training days in a row give a recovery day`() {
        val streak = (1L..6L).map { session(it, SessionKind.CARDIO, Intensity.ZONE2, id = "z$it") }
        val p = plan(streak)
        assertEquals(TrainingType.MOBILITY, p.training.type)
    }

    @Test
    fun `the usual slot and duration come from WHOOP history`() {
        val p = plan(emptyList(), ScheduleFacts(trainingStartMin = 7 * 60 + 30, trainingMinutes = 60, wakeMin = 6 * 60 + 30))
        assertEquals(7 * 60 + 30, p.training.startMin)
        assertEquals(60, p.training.minutes)
        assertTrue(p.fired.any { it.ref == RuleSet.ACT_03 && it.why.contains("07:30") })
    }

    @Test
    fun `WHOOP strength in the slot confirms, a short run is changed, nothing is null`() {
        val planned = TrainingChoice(TrainingType.STRENGTH, TrainingLevel.L1, "T05", 45, 18 * 60)
        val strength = Session("s", wednesday, 18 * 60 + 40, 40, SessionKind.STRENGTH, Intensity.MIXED)
        assertEquals(ItemStatus.DONE_WHOOP, WorkoutMatcher.verify(planned, listOf(strength))?.status)
        val run = Session("r", wednesday, 7 * 60, 25, SessionKind.CARDIO, Intensity.ZONE2)
        assertEquals(Verification(ItemStatus.MODIFIED, "r"), WorkoutMatcher.verify(planned, listOf(run)))
        assertNull(WorkoutMatcher.verify(planned, emptyList()))
        assertNull(WorkoutMatcher.verify(planned, listOf(run.copy(minutes = 5))), "auto-detected 5 min is not a session")
    }

    @Test
    fun `intervals recorded as Zone 2 are changed, Zone 2 long enough is confirmed`() {
        val intervals = TrainingChoice(TrainingType.INTERVALS, TrainingLevel.L0, "T11", 40, 18 * 60)
        val z2 = Session("z", wednesday, 18 * 60, 40, SessionKind.CARDIO, Intensity.ZONE2)
        assertEquals(ItemStatus.MODIFIED, WorkoutMatcher.verify(intervals, listOf(z2))?.status)
        val zone2 = intervals.copy(type = TrainingType.ZONE2)
        assertEquals(ItemStatus.DONE_WHOOP, WorkoutMatcher.verify(zone2, listOf(z2))?.status)
        assertEquals(ItemStatus.MODIFIED, WorkoutMatcher.verify(zone2, listOf(z2.copy(minutes = 20)))?.status)
        assertEquals(ItemStatus.DONE_SELF, WorkoutMatcher.merge(ItemStatus.DONE_SELF, null))
        assertEquals(ItemStatus.DONE_WHOOP, WorkoutMatcher.merge(ItemStatus.DONE_SELF, Verification(ItemStatus.DONE_WHOOP, "z")))
    }

    @Test
    fun `the session in the slot wins over a longer one far away`() {
        val planned = TrainingChoice(TrainingType.ZONE2, TrainingLevel.L1, "T09", 40, 18 * 60)
        val morning = Session("m", wednesday, 7 * 60, 90, SessionKind.CARDIO, Intensity.MIXED)
        val evening = Session("e", wednesday, 18 * 60 + 15, 40, SessionKind.CARDIO, Intensity.ZONE2)
        assertEquals(Verification(ItemStatus.DONE_WHOOP, "e"), WorkoutMatcher.verify(planned, listOf(morning, evening)))
    }

    @Test
    fun `morning push waits for 06_00, retries until the night is scored and stops at noon`() {
        fun at(h: Int, m: Int) = wednesday.atTime(h, m)
        val early = MorningPolicy.next(at(3, 0), planReadyToday = false, notifiedToday = false)
        assertIs<MorningPolicy.Step.Wait>(early)
        assertEquals(90, early.delay.toMinutes())
        assertEquals(MorningPolicy.Step.Wait(java.time.Duration.ofMinutes(15)), MorningPolicy.next(at(5, 0), false, false))
        val quiet = MorningPolicy.next(at(5, 10), planReadyToday = true, notifiedToday = false)
        assertEquals(50, (quiet as MorningPolicy.Step.Wait).delay.toMinutes())
        assertEquals(MorningPolicy.Step.NotifyNow, MorningPolicy.next(at(7, 5), true, false))
        val done = MorningPolicy.next(at(7, 5), true, true) as MorningPolicy.Step.Wait
        assertEquals(21 * 60 + 25, done.delay.toMinutes())
        val late = MorningPolicy.next(at(13, 0), true, false) as MorningPolicy.Step.Wait
        assertEquals(15 * 60 + 30, late.delay.toMinutes())
    }
}
