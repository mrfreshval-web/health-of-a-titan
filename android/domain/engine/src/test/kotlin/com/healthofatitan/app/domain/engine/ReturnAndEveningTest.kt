package com.healthofatitan.app.domain.engine

import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** A18 И4.1 (close the day), И4.4 (return after a pause, RET-01 / D12) and A35 §4 (phone steps). */
class ReturnAndEveningTest {

    private val thursday = LocalDate.of(2026, 11, 12)

    @Test
    fun `silence counts whole days without opening`() {
        assertEquals(0, ReturnPolicy.silenceDays(null, thursday))
        assertEquals(0, ReturnPolicy.silenceDays(thursday, thursday))
        assertEquals(0, ReturnPolicy.silenceDays(thursday.minusDays(1), thursday))
        assertEquals(2, ReturnPolicy.silenceDays(thursday.minusDays(3), thursday))
        assertEquals(ReturnStage.NONE, ReturnPolicy.stage(1))
        assertEquals(ReturnStage.RETURN, ReturnPolicy.stage(2))
        assertEquals(ReturnStage.RETURN, ReturnPolicy.stage(6))
        assertEquals(ReturnStage.WELCOME_BACK, ReturnPolicy.stage(7))
    }

    @Test
    fun `the warm nudge comes once per pause, in the day`() {
        val monday = thursday.minusDays(3)
        val noon = thursday.atTime(12, 0)
        assertTrue(ReturnPolicy.nudgeDue(monday, null, noon))
        assertTrue(ReturnPolicy.nudgeDue(monday, monday.minusDays(10), noon), "an old nudge of an earlier pause")
        assertFalse(ReturnPolicy.nudgeDue(monday, thursday, noon), "already met this pause")
        assertFalse(ReturnPolicy.nudgeDue(monday, null, thursday.atTime(7, 0)), "not in the morning before 10")
        assertFalse(ReturnPolicy.nudgeDue(monday, null, thursday.atTime(21, 0)), "never at night")
        assertFalse(ReturnPolicy.nudgeDue(thursday.minusDays(2), null, noon), "one silent day is not a pause")
        assertFalse(ReturnPolicy.nudgeDue(null, null, noon), "a fresh install is not a return")
    }

    @Test
    fun `a new start is the nearest Monday or the 1st`() {
        assertEquals(LocalDate.of(2026, 11, 16), ReturnPolicy.newStart(thursday))
        assertEquals(LocalDate.of(2026, 11, 16), ReturnPolicy.newStart(LocalDate.of(2026, 11, 16)))
        assertEquals(LocalDate.of(2027, 1, 1), ReturnPolicy.newStart(LocalDate.of(2026, 12, 31)))
        assertEquals(LocalDate.of(2026, 12, 1), ReturnPolicy.newStart(LocalDate.of(2026, 12, 1)))
    }

    private fun d12(silence: Int, night: NightFacts = NightFacts(orsZ = 0.1, orsEwma7 = -0.2, sleepHours = 7.0, prevSleepHours = 6.5)) =
        DayEngine.plan(DayInput(thursday, night, yesterday = YesterdayFacts(regime = Regime.STEADY), silenceDays = silence))

    @Test
    fun `D12 three days of silence give the minimal version without a report`() {
        val p = d12(3)
        assertEquals(Regime.STEADY, p.regime)
        assertTrue(DayFlag.RETURN in p.flags)
        assertEquals(TrainingType.WALK, p.training.type)
        assertEquals(ReturnMode.MINIMAL_MINUTES, p.training.minutes)
        assertEquals(RuleSet.RET_01, p.items.first { it.id == DayEngine.Ids.TRAINING }.rule)
        val focus = p.items.first { it.id == DayEngine.Ids.FOCUS }
        assertEquals(DayTexts.focusDetail(1, ReturnMode.FOCUS_MINUTES), focus.detail)
        assertTrue(p.fired.any { it.ref == RuleSet.RET_01 })
        assertEquals(DayTexts.RETURN_HEADLINE, p.headline)
        assertTrue(p.headline.length <= 40 && p.summary.length <= 110)
        val words = (p.fired.map { it.why } + p.headline + p.summary + p.items.map { it.title + " " + it.detail }).joinToString(" ").lowercase()
        listOf("пропустил", "серия", "прервал").forEach { assertFalse(it in words, it) }
        assertTrue(Steps.isWalk(p.items.first { it.id == DayEngine.Ids.TRAINING }.rule))
    }

    @Test
    fun `no silence keeps the plan as it was`() {
        val p = d12(1)
        assertFalse(DayFlag.RETURN in p.flags)
        assertFalse(p.fired.any { it.ref == RuleSet.RET_01 })
        val plain = DayEngine.plan(DayInput(thursday, NightFacts(orsZ = 0.1, orsEwma7 = -0.2, sleepHours = 7.0, prevSleepHours = 6.5), yesterday = YesterdayFacts(regime = Regime.STEADY)))
        assertSame(plain.training.type, p.training.type)
    }

    @Test
    fun `a sick return keeps the rest of SAF-03`() {
        val sick = DayEngine.plan(
            DayInput(
                thursday,
                NightFacts(orsZ = 0.1, orsEwma7 = -0.2, sleepHours = 7.0, prevSleepHours = 6.5),
                yesterday = YesterdayFacts(regime = Regime.STEADY),
                evening = EveningFacts(body = Body.SICK),
                silenceDays = 9,
            ),
        )
        assertTrue(DayFlag.RETURN in sick.flags)
        assertTrue(DayFlag.SICK in sick.flags)
        assertFalse(sick.items.first { it.id == DayEngine.Ids.TRAINING }.rule == RuleSet.RET_01)
    }

    @Test
    fun `closing the day only lights things up`() {
        assertEquals(ItemStatus.DONE_SELF, Evening.trainingStatus(ItemStatus.OPEN, TrainingClose.DONE))
        assertEquals(ItemStatus.MODIFIED, Evening.trainingStatus(ItemStatus.OPEN, TrainingClose.CHANGED))
        assertEquals(ItemStatus.OPEN, Evening.trainingStatus(ItemStatus.DONE_SELF, TrainingClose.NOT_TODAY))
        assertEquals(ItemStatus.DONE_SELF, Evening.trainingStatus(ItemStatus.DONE_SELF, null))
        assertEquals(ItemStatus.DONE_WHOOP, Evening.trainingStatus(ItemStatus.DONE_WHOOP, TrainingClose.NOT_TODAY))
        assertEquals(ItemStatus.MODIFIED, Evening.trainingStatus(ItemStatus.MODIFIED, TrainingClose.DONE))
        assertTrue(Evening.trainingAsked(ItemStatus.OPEN))
        assertTrue(Evening.trainingAsked(ItemStatus.DONE_SELF))
        assertFalse(Evening.trainingAsked(ItemStatus.DONE_WHOOP))
        assertFalse(Evening.trainingAsked(null))

        assertEquals(Evening.LIGHT_FLOOR, Evening.light(emptyList()))
        assertEquals(Evening.LIGHT_FLOOR, Evening.light(listOf(ItemStatus.OPEN, ItemStatus.OPEN)))
        assertEquals(1f, Evening.light(listOf(ItemStatus.DONE_SELF, ItemStatus.DONE_WHOOP, ItemStatus.MODIFIED)))
        assertTrue(Evening.light(listOf(ItemStatus.DONE_SELF, ItemStatus.OPEN)) > Evening.LIGHT_FLOOR)

        assertEquals(0, Evening.answered(DayClose(), trainingAsked = true))
        assertEquals(4, Evening.answered(DayClose(TrainingClose.DONE, FoodClose.PLATE, Body.FINE, 0, true), trainingAsked = true))
        assertEquals(3, Evening.answered(DayClose(TrainingClose.DONE, FoodClose.PLATE, Body.FINE, 0, true), trainingAsked = false))
    }

    @Test
    fun `the steps goal is the personal median`() {
        assertEquals(Steps.DEFAULT_GOAL, Steps.goal(emptyList()))
        assertEquals(Steps.DEFAULT_GOAL, Steps.goal(listOf(9000, 300, 12000)), "a phone left on the table does not count")
        assertEquals(9000, Steps.goal(listOf(6000, 9000, 12000)))
        assertEquals(7600, Steps.goal(listOf(6000, 7000, 8100, 12000)))
        assertEquals(0.5f, Steps.fraction(4000, 8000))
        assertEquals(1f, Steps.fraction(12000, 8000))
        assertEquals(0f, Steps.fraction(100, 0))
    }

    @Test
    fun `phone steps confirm a planned walk`() {
        assertTrue(Steps.walkConfirmed(20, 1200))
        assertFalse(Steps.walkConfirmed(20, 900))
        assertFalse(Steps.walkConfirmed(20, null))
        assertFalse(Steps.walkConfirmed(0, 5000))
        assertTrue(Steps.isWalk(RuleSet.ACT_01))
        assertFalse(Steps.isWalk(RuleSet.ACT_03))
        assertFalse(Steps.isWalk(null))
        assertEquals(LocalDateTime.of(2026, 11, 12, 0, 0), thursday.atStartOfDay())
    }
}
