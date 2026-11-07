package com.healthofatitan.app.domain.path

import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PathEngineTest {

    private val today = LocalDate.of(2026, 11, 7)
    private val sleepPlan = GoalPlan(Sphere.SLEEP, 3, listOf(4, 5, 5))

    @Test
    fun `no goal yet shows proposals and contours`() {
        val s = PathEngine.state(today, emptyList(), emptyMap(), PathEvidence(today), null)
        assertNull(s.active)
        assertEquals(Sphere.SLEEP, s.proposals.first().sphere)
        assertEquals(6, s.spheres.size)
        assertTrue(s.spheres.all { it.share == null && !it.lit })
    }

    @Test
    fun `a gentle day's minimal step counts like any other`() {
        val goal = GoalRecord(1, sleepPlan, today.minusDays(2))
        val s = PathEngine.state(today, listOf(goal), mapOf(1L to setOf(today)), PathEvidence(today), PathRegime.GENTLE)
        assertEquals(StepVersion.MINIMAL, s.stepVersion)
        assertTrue(s.stepDone)
        assertTrue(s.stepMarked)
        assertEquals(1, s.active!!.weekDone)
    }

    @Test
    fun `data counts the step without a tap`() {
        val goal = GoalRecord(1, sleepPlan, today.minusDays(2))
        val ev = PathEvidence(today, mapOf(today to 7.4, today.minusDays(1) to 7.1, today.minusDays(2) to 5.0))
        val s = PathEngine.state(today, listOf(goal), emptyMap(), ev, PathRegime.STEADY)
        assertTrue(s.stepDone)
        assertFalse(s.stepMarked)
        assertEquals(2, s.active!!.weekDone)
    }

    @Test
    fun `changing the goal keeps the history of the old one`() {
        val old = GoalRecord(1, sleepPlan, today.minusDays(14), GoalStatus.REPLACED, endedDay = today.minusDays(1))
        val new = GoalRecord(2, GoalPlan(Sphere.BODY, 1, listOf(2, 3, 3)), today)
        val week1 = (0L..3L).map { old.startDay.plusDays(it) }.toSet()
        val s = PathEngine.state(today, listOf(old, new), mapOf(1L to week1), PathEvidence(today), null)
        assertEquals(Sphere.BODY, s.active!!.goal.plan.sphere)
        assertEquals(1, s.active!!.week)
        assertEquals(1, s.past.size)
        assertEquals(1, s.past.single().litWeeks)
        assertFalse(s.stepDone) // the old goal's marks do not count for the new one
    }

    @Test
    fun `a sphere is in light at 70 percent, at 50 percent in a gentle week`() {
        val four = (0L..3L).associate { today.minusDays(it) to 7.5 } + (4L..6L).associate { today.minusDays(it) to 6.0 }
        val ev = PathEvidence(today, four)
        val steady = PathEngine.spheres(ev, PathRegime.STEADY, null, emptySet()).first { it.sphere == Sphere.SLEEP }
        val gentle = PathEngine.spheres(ev, PathRegime.GENTLE, null, emptySet()).first { it.sphere == Sphere.SLEEP }
        assertEquals(4 / 7f, steady.share)
        assertFalse(steady.lit)
        assertTrue(gentle.lit)
        // spheres HoT cannot see yet stay contours, not zero
        assertNull(PathEngine.spheres(ev, null, null, emptySet()).first { it.sphere == Sphere.MIND }.share)
    }
}
