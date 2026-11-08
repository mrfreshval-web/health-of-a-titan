package com.healthofatitan.app.domain.today.week

import com.healthofatitan.app.domain.today.GoalMeasure
import com.healthofatitan.app.domain.today.GoalSource
import com.healthofatitan.app.domain.today.Sphere
import com.healthofatitan.app.domain.today.TaskKind
import com.healthofatitan.app.domain.today.WeekGoal
import com.healthofatitan.app.domain.today.WeekPlan
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A67 С-3/С-4: the bridge Путь → Неделя → Сегодня (the stage gives the week, the steps carry the goal). */
class WeekStageTest {

    private val monday = LocalDate.of(2026, 11, 9)
    private val cardio = PathWeek.Active("Пробежать 5 км", listOf(WeekGoalRules.ID_ZONES, WeekGoalRules.ID_SLEEP), monday.minusDays(20))

    @Test
    fun `old stored sources still read, the person's goals stay the person's`() {
        assertEquals(GoalSource.STAGE, GoalSource.parse("PATH"))
        assertEquals(GoalSource.EXPERIMENT, GoalSource.parse("PATTERN"))
        assertEquals(GoalSource.START, GoalSource.parse("WORK"))
        assertEquals(GoalSource.START, GoalSource.parse(null))
        assertEquals(GoalSource.PERSON, GoalSource.parse("PERSON"))
        assertEquals(GoalSource.PERSON, WeekGoalRules.sourceOf(WeekGoalRules.REMINDER_PREFIX + "1-5", "PATH"))
        assertEquals(GoalSource.PERSON, WeekGoalRules.sourceOf(RitualRules.CARRY_ID, "WORK"))
    }

    @Test
    fun `without a goal the week is HoT's starter, never marked as the Path`() {
        val p = WeekGoalRules.propose(monday, null, null, WeekKind.FIRST)
        assertTrue(p.plan.goals.all { it.source == GoalSource.START }, "${p.plan.goals}")
        assertTrue(p.plan.goals.none { "пут" in it.title.lowercase() }, "${p.plan.goals.map { it.title }}")
        val tasks = WeekGoalRules.candidates(p.plan, monday, emptySet(), goal = "Пробежать 5 км")
        assertTrue(tasks.all { it.goal == null }, "a starter goal carries no mark")
    }

    @Test
    fun `an active goal gives the week 2 to 3 goals, at least one of the stage, lever first`() {
        val p = WeekGoalRules.propose(monday, null, null, WeekKind.FIRST, path = cardio)
        val stage = p.plan.goals.filter { it.source == GoalSource.STAGE }
        assertEquals(listOf(WeekGoalRules.ID_ZONES, WeekGoalRules.ID_SLEEP), stage.map { it.id })
        assertTrue(p.plan.goals.size in WeekGoalRules.STAGE_MIN..WeekGoalRules.STAGE_MAX, "${p.plan.goals.map { it.id }}")
        assertEquals(WeekCopy.GOAL_FROM_PATH, p.note)
    }

    @Test
    fun `steps of the stage carry the short name of the goal`() {
        val p = WeekGoalRules.propose(monday, null, null, WeekKind.FIRST, path = PathWeek.Active("Сильнее", listOf(WeekGoalRules.ID_TRAINING), monday.minusDays(3)))
        val tasks = WeekGoalRules.candidates(p.plan, monday.plusDays(1), emptySet(), goal = "Сильнее")
        val training = tasks.firstOrNull { it.weekGoalId == WeekGoalRules.ID_TRAINING }
        assertNotNull(training)
        assertEquals("Сильнее", training.goal)
    }

    @Test
    fun `a goal on pause makes the week a step lighter`() {
        val prev = WeekGoalRules.defaults(monday.minusDays(7), first = false)
        val p = WeekGoalRules.propose(monday, prev, LastWeek(0.95, 0, true), WeekKind.NORMAL, path = PathWeek.Paused)
        assertEquals(WeekCopy.GOAL_PATH_PAUSED, p.note)
        val before = prev.goals.first { it.id == WeekGoalRules.ID_TRAINING }.target
        val after = p.plan.goals.first { it.id == WeekGoalRules.ID_TRAINING }.target
        assertTrue(after < before, "$before → $after")
    }

    @Test
    fun `a goal taken this week pours in once, from Thursday only as the first step`() {
        val plan = WeekGoalRules.defaults(monday, first = false)
        val thursday = cardio.copy(takenOn = monday.plusDays(3))
        val poured = assertNotNull(WeekGoalRules.pour(plan, thursday))
        val zones = poured.goals.first { it.id == WeekGoalRules.ID_ZONES }
        assertEquals(GoalSource.STAGE, zones.source)
        assertEquals(WeekGoalRules.MINUTES_STEP, zones.target, "first step only")
        assertNull(WeekGoalRules.pour(poured, thursday), "only once")
        assertNull(WeekGoalRules.pour(plan, cardio), "a goal taken in another week waits for Monday")
        assertNull(WeekGoalRules.pour(plan, PathWeek.NoGoal))
    }

    @Test
    fun `the person's reminder survives the pour`() {
        val reminder = WeekGoal(WeekGoalRules.REMINDER_PREFIX + "1-1", Sphere.MIND, TaskKind.REMINDER, "Позвонить маме", 2, WeekCopy.UNIT_DAYS, 0, GoalSource.PERSON, GoalMeasure.DAYS)
        val plan = WeekPlan(monday, WeekGoalRules.defaults(monday, first = false).goals + reminder)
        val poured = assertNotNull(WeekGoalRules.pour(plan, cardio.copy(takenOn = monday.plusDays(1))))
        assertTrue(poured.goals.any { it.id == reminder.id })
    }
}
