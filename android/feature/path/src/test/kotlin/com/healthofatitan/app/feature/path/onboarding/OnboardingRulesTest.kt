package com.healthofatitan.app.feature.path.onboarding

import com.healthofatitan.app.domain.path.ComponentKey
import com.healthofatitan.app.domain.path.Criterion
import com.healthofatitan.app.domain.path.GoalComponent
import com.healthofatitan.app.domain.path.PracticalGoal
import com.healthofatitan.app.domain.path.Role
import com.healthofatitan.app.domain.today.GoalSource
import com.healthofatitan.app.domain.today.Sphere
import com.healthofatitan.app.domain.today.TaskKind
import com.healthofatitan.app.domain.today.WeekGoal
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A66 §0.2: the rules of the first launch, without screens. */
class OnboardingRulesTest {

    private val sunday = LocalDate.of(2026, 11, 8)
    private val parts = listOf(
        GoalComponent(ComponentKey.STRENGTH, Role.LEAD, isLever = true),
        GoalComponent(ComponentKey.SLEEP, Role.PROP),
        GoalComponent(ComponentKey.MOBILITY, Role.GUARD),
    )
    private val goal = PracticalGoal(
        id = 1,
        title = "Подтянуться 10 раз",
        start = sunday,
        deadline = sunday.plusWeeks(12),
        criteria = listOf(Criterion("подтягивания", 4.0, 10.0, "подряд")),
        components = parts,
    )

    @Test
    fun `the steps go welcome, behind, path, week and back`() {
        assertEquals(OnboardingStep.BEHIND, OnboardingRules.next(OnboardingStep.WELCOME))
        assertEquals(OnboardingStep.WEEK, OnboardingRules.next(OnboardingStep.PATH))
        assertNull(OnboardingRules.next(OnboardingStep.WEEK))
        assertEquals(OnboardingStep.PATH, OnboardingRules.back(OnboardingStep.WEEK))
        assertEquals(listOf(1, 2, 3), listOf(OnboardingStep.BEHIND, OnboardingStep.PATH, OnboardingStep.WEEK).map(OnboardingRules::position))
    }

    @Test
    fun `a goal taken on step 2 does not end the first launch, an older goal does`() {
        assertFalse(OnboardingRules.passed(done = false, started = true, hasGoal = true))
        assertTrue(OnboardingRules.passed(done = false, started = false, hasGoal = true))
        assertTrue(OnboardingRules.passed(done = true, started = true, hasGoal = false))
        assertFalse(OnboardingRules.passed(done = false, started = false, hasGoal = false))
    }

    @Test
    fun `where it went differently names the lever, only if it is a part of the goal`() {
        val sleep = BehindAnswers(different = setOf(OnboardingCopy.DIFF_SLEEP))
        assertEquals(ComponentKey.SLEEP, OnboardingRules.suggestedLever(sleep, parts))
        assertNull(OnboardingRules.suggestedLever(BehindAnswers(different = setOf(OnboardingCopy.DIFF_WORK)), parts))
        assertNull(OnboardingRules.suggestedLever(BehindAnswers(different = setOf(OnboardingCopy.DIFF_TIME)), parts))
        val moved = OnboardingRules.withLever(parts, ComponentKey.SLEEP)
        assertEquals(listOf(ComponentKey.SLEEP), moved.filter { it.isLever }.map { it.key })
        assertEquals(parts, OnboardingRules.withLever(parts, ComponentKey.REST))
    }

    @Test
    fun `milestones come every four weeks on a Sunday, numbers from the criterion`() {
        val m = OnboardingRules.milestones(goal, sunday, 12)
        assertEquals(listOf(4, 8, 12), m.map { it.week })
        assertTrue(m.all { it.date.dayOfWeek == DayOfWeek.SUNDAY }, "${m.map { it.date }}")
        assertEquals(LocalDate.of(2026, 11, 29), m.first().date)
        assertEquals(listOf("6 подряд", "8 подряд", "10 подряд"), m.map { it.label })
        assertEquals(1, OnboardingRules.milestones(goal, sunday, 4).size)
        assertEquals(OnboardingCopy.milestoneWeek(8), OnboardingRules.milestones(null, sunday, 8).last().label)
    }

    @Test
    fun `the lever of the stage says why when it came from the history`() {
        val withSleep = goal.copy(components = OnboardingRules.withLever(parts, ComponentKey.SLEEP))
        val s = OnboardingRules.suggestion(withSleep, BehindAnswers(different = setOf(OnboardingCopy.DIFF_SLEEP)), sunday, 12)
        assertEquals(ComponentKey.SLEEP, s.lever)
        assertTrue(s.reason!!.contains("прошлый раз"), s.reason)
        assertNull(OnboardingRules.suggestion(null, BehindAnswers(), sunday, 12).lever)
    }

    @Test
    fun `the first week shows the goals of the stage first, three at most, each with its source`() {
        fun g(id: String, source: GoalSource) = WeekGoal(id, Sphere.BODY, TaskKind.TRAINING, id, 3, "в неделю", 0, source)
        val week = OnboardingRules.weekGoals(listOf(g("a", GoalSource.START), g("b", GoalSource.STAGE), g("c", GoalSource.PERSON), g("d", GoalSource.STAGE)))
        assertEquals(listOf("b", "d", "a"), week.map { it.id })
        assertEquals(OnboardingCopy.chipLever("Сила"), OnboardingRules.chip(week.first(), ComponentKey.STRENGTH))
        assertEquals(OnboardingCopy.CHIP_OWN, OnboardingRules.chip(g("c", GoalSource.PERSON), null))
    }
}
