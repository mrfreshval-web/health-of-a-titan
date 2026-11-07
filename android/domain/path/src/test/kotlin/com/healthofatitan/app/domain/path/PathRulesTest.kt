package com.healthofatitan.app.domain.path

import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PathRulesTest {

    private val today = LocalDate.of(2026, 11, 7)

    /** 28 nights: [good] of every 7 are ≥ 7 h, the rest 6 h. */
    private fun nights(good: Int, days: Int = 28): Map<LocalDate, Double> =
        (0 until days).associate { i -> today.minusDays(i.toLong()) to if (i % 7 < good) 7.3 else 6.0 }

    @Test
    fun `sleep now is nights of 7 hours scaled to a week`() {
        assertEquals(3, PathRules.baseline(Sphere.SLEEP, PathEvidence(today, nights(3))))
        assertEquals(0, PathRules.baseline(Sphere.SLEEP, PathEvidence(today, nights(0))))
    }

    @Test
    fun `fewer than a week of nights gives no now`() {
        assertNull(PathRules.baseline(Sphere.SLEEP, PathEvidence(today, nights(5, days = 6))))
        assertNull(PathRules.baseline(Sphere.BODY, PathEvidence(today)))
    }

    @Test
    fun `body now is workout days per week of worn days`() {
        val training = (0 until 28 step 7).map { today.minusDays(it.toLong()) }.toSet() // once a week
        assertEquals(1, PathRules.baseline(Sphere.BODY, PathEvidence(today, nights(3), training)))
    }

    @Test
    fun `stages step one day of seven and hold the target`() {
        assertEquals(listOf(4, 5, 5), PathRules.stagesFrom(3, 6))
        assertEquals(listOf(6, 6, 6), PathRules.stagesFrom(5, 6)) // cap
        assertEquals(listOf(7, 7, 7), PathRules.stagesFrom(7, 6)) // already above: hold
    }

    @Test
    fun `sleep is offered first from data, and both spheres from data come before defaults`() {
        val ev = PathEvidence(today, nights(3), setOf(today))
        val proposals = PathRules.proposals(ev)
        assertEquals(listOf(Sphere.SLEEP, Sphere.BODY), proposals.map { it.sphere })
        val sleep = proposals.first()
        assertEquals(3, sleep.baseline)
        assertEquals(listOf(4, 5, 5), sleep.stages)
        assertTrue(sleep.fromData)
    }

    @Test
    fun `a sphere at its cap gives way to one with room`() {
        val ev = PathEvidence(today, nights(7), setOf(today))
        assertEquals(Sphere.BODY, PathRules.proposals(ev).first().sphere)
    }

    @Test
    fun `without data the default plan is offered and marked as such`() {
        val proposals = PathRules.proposals(PathEvidence(today))
        assertEquals(Sphere.SLEEP, proposals.first().sphere)
        assertFalse(proposals.first().fromData)
        assertEquals(listOf(4, 5, 5), proposals.first().stages)
    }

    @Test
    fun `week and stage of a 12-week goal`() {
        val start = today
        assertEquals(1, PathRules.weekOf(start, today.plusDays(6)))
        assertEquals(2, PathRules.weekOf(start, today.plusDays(7)))
        assertEquals(6, PathRules.weekOf(start, today.plusDays(36)))
        assertEquals(1, PathRules.stageOf(4))
        assertEquals(2, PathRules.stageOf(6))
        assertEquals(3, PathRules.stageOf(12))
    }

    @Test
    fun `week goal comes from the stage`() {
        val plan = GoalPlan(Sphere.SLEEP, 3, listOf(4, 5, 5))
        assertEquals(4, PathRules.weekTarget(plan, 1))
        assertEquals(5, PathRules.weekTarget(plan, 5))
        assertEquals(5, PathRules.weekTarget(plan, 12))
    }

    @Test
    fun `the mode sizes the step and never argues with the day`() {
        assertEquals(StepVersion.FULL, PathRules.stepVersion(PathRegime.BOOST))
        assertEquals(StepVersion.NORMAL, PathRules.stepVersion(PathRegime.STEADY))
        assertEquals(StepVersion.MINIMAL, PathRules.stepVersion(PathRegime.GENTLE))
        assertEquals(StepVersion.NORMAL, PathRules.stepVersion(null))
    }

    @Test
    fun `weeks light up, a missed week stays a contour, the current one is open`() {
        val start = today.minusDays(14) // week 3 today
        val goal = GoalRecord(1, GoalPlan(Sphere.SLEEP, 3, listOf(4, 5, 5)), start)
        val week1 = (0L..3L).map { start.plusDays(it) } // 4 of 4: lit
        val week2 = listOf(start.plusDays(7)) // 1 of 4: contour
        val week3 = listOf(today) // 1 so far
        val p = PathRules.progress(goal, today, (week1 + week2 + week3).toSet())
        assertEquals(3, p.week)
        assertEquals(1, p.stage)
        assertEquals(4, p.weekTarget)
        assertEquals(1, p.weekDone)
        assertEquals(listOf(WeekState.LIT, WeekState.MISSED, WeekState.CURRENT, WeekState.FUTURE), p.weeks.take(4).map { it.state })
        assertEquals(1, p.litWeeks)
        assertFalse(p.finished)
    }

    @Test
    fun `after twelve weeks the cycle is finished`() {
        val goal = GoalRecord(1, GoalPlan(Sphere.BODY, 1, listOf(2, 3, 3)), today.minusDays(84))
        val p = PathRules.progress(goal, today, emptySet())
        assertTrue(p.finished)
        assertEquals(12, p.week)
        assertTrue(p.weeks.none { it.state == WeekState.CURRENT || it.state == WeekState.FUTURE })
    }
}
