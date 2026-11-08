package com.healthofatitan.app.domain.today.week

import com.healthofatitan.app.domain.today.GoalMeasure
import com.healthofatitan.app.domain.today.GoalSource
import com.healthofatitan.app.domain.today.Sphere
import com.healthofatitan.app.domain.today.TaskKind
import com.healthofatitan.app.domain.today.WeekGoal
import com.healthofatitan.app.domain.today.WeekPlan
import com.healthofatitan.app.domain.today.week.DayDot.DONE
import com.healthofatitan.app.domain.today.week.DayDot.NOT_NEEDED
import com.healthofatitan.app.domain.today.week.DayDot.NO_DATA
import com.healthofatitan.app.domain.today.week.DayDot.PLANNED
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A66 §4, §0.3: the verdict «2 из 3», what is left, the dots of the days, the chips, the week without a Path. */
class WeekFaceTest {

    private val monday = LocalDate.of(2026, 11, 2)
    private val thursday = monday.plusDays(3)
    private fun day(i: Int) = monday.plusDays(i.toLong())

    private val path = PathWeek.Active("Сильнее к весне", listOf(WeekGoalRules.ID_TRAINING, WeekGoalRules.ID_SLEEP), monday.minusDays(14))

    private val training = WeekGoal(WeekGoalRules.ID_TRAINING, Sphere.BODY, TaskKind.TRAINING, "Силовые", 3, WeekCopy.UNIT_TIMES, 2, GoalSource.STAGE)
    private val sleep = WeekGoal(
        WeekGoalRules.ID_SLEEP, Sphere.SLEEP, TaskKind.REMINDER, "Сон 7 ч", 5, WeekCopy.UNIT_NIGHTS, 2, GoalSource.STAGE, GoalMeasure.NIGHTS,
    )
    private val walk = WeekGoal(WeekGoalRules.REMINDER_PREFIX + "walk", Sphere.BODY, TaskKind.REMINDER, "Прогулка 30 мин", 4, WeekCopy.UNIT_DAYS, 3, GoalSource.PERSON)

    private val days = mapOf(
        training.id to GoalDays(setOf(day(0), day(2))),
        sleep.id to GoalDays(setOf(day(0), day(3)), known = setOf(day(0), day(1), day(3))),
        walk.id to GoalDays(setOf(day(0), day(1), day(3))),
    )

    private fun view(goals: List<WeekGoal>, today: LocalDate = thursday, kind: WeekKind = WeekKind.NORMAL) =
        WeekBuilder.build(WeekInput(now = today.atTime(12, 0), facts = emptyList(), plan = WeekPlan(monday, goals), planStatus = PlanStatus.APPROVED, kind = kind))

    @Test
    fun `the dots of the mockup - done, not needed, planned, no data, today ringed`() {
        assertEquals(listOf(DONE, NOT_NEEDED, DONE, PLANNED, PLANNED, PLANNED, NOT_NEEDED), WeekFace.dots(training, days.getValue(training.id), monday, thursday))
        assertEquals(listOf(DONE, PLANNED, NO_DATA, DONE, PLANNED, PLANNED, PLANNED), WeekFace.dots(sleep, days.getValue(sleep.id), monday, thursday))
        val card = WeekFace.card(training, days.getValue(training.id), monday, thursday, WeekFace.stage(path, thursday))
        assertEquals(3, card.todayIndex)
    }

    @Test
    fun `Sunday is planned only when the days before it are not enough`() {
        val two = training.copy(progress = 0)
        val saturday = day(5)
        assertEquals(listOf(NOT_NEEDED, NOT_NEEDED, NOT_NEEDED, NOT_NEEDED, NOT_NEEDED, PLANNED, PLANNED), WeekFace.dots(two, GoalDays(), monday, saturday))
        val done = training.copy(progress = 3)
        assertTrue(WeekFace.dots(done, GoalDays(setOf(day(0), day(1), day(2))), monday, thursday).drop(3).all { it == NOT_NEEDED })
    }

    @Test
    fun `what is left on a card - one more, better on friday or saturday`() {
        val c = WeekFace.card(training, days.getValue(training.id), monday, thursday, null)
        assertEquals("ещё 1 — удобнее в пт или сб", c.left)
        assertEquals("2 из 3", c.count)
        val s = WeekFace.card(sleep, days.getValue(sleep.id), monday, thursday, null)
        assertEquals("ещё 3 ночи", s.left)
        val done = WeekFace.card(training.copy(progress = 3), GoalDays(setOf(day(0), day(1), day(2))), monday, thursday, null)
        assertEquals(WeekFaceCopy.DONE, done.left)
        assertTrue(done.done)
    }

    @Test
    fun `the last day left is today`() {
        val saturday = day(5)
        val g = training.copy(progress = 2)
        val dots = WeekFace.dots(g, GoalDays(setOf(day(0), day(2))), monday, saturday)
        assertEquals(listOf(saturday), WeekFace.suggested(g, dots, monday, saturday))
        assertEquals("ещё 1 — сегодня", WeekFace.left(g, listOf(saturday), saturday))
    }

    @Test
    fun `a goal holds when it keeps pace with the days lived`() {
        assertTrue(WeekFace.holds(training, monday, thursday))
        assertFalse(WeekFace.holds(sleep, monday, thursday), "2 of 5 nights after 3 days is behind pace")
        assertTrue(WeekFace.holds(walk, monday, thursday))
        assertTrue(WeekFace.holds(training.copy(progress = 0), monday, monday), "Monday: nothing is behind yet")
        assertFalse(WeekFace.holds(training.copy(progress = 0, target = 7), monday, day(2)), "7 left, 5 days: does not fit")
    }

    @Test
    fun `the verdict of the mockup - 2 of 3 hold, and what is left`() {
        val face = WeekFace.goals(view(listOf(training, sleep, walk)), days, path)
        assertEquals("2 из 3", face.verdict.count)
        assertEquals("держатся", face.verdict.word)
        assertEquals("Осталось: тренировка в пт или сб, три ночи сна и ещё 1 цель.", face.verdict.left)
        assertEquals("Путь · неделя 3 из 12", face.verdict.pathLink)
        assertEquals(4, face.carved)
    }

    @Test
    fun `what is left is deterministic and short`() {
        assertEquals("Осталось: тренировка в пт или сб и две ночи сна.", WeekFace.remainingPhrase(listOf("тренировка в пт или сб", "две ночи сна")))
        assertEquals("Осталось 4 дня: тренировка.", WeekFace.remainingPhrase(listOf("тренировка"), daysLeft = 4))
        assertNull(WeekFace.remainingPhrase(emptyList()))
        assertEquals("две тренировки", WeekFace.fragment(training.copy(progress = 1), emptyList(), thursday))
        assertEquals("два фокус-блока в пт", WeekFace.fragment(training.copy(id = WeekGoalRules.ID_FOCUS, progress = 2, target = 4), listOf(day(4)), thursday))
        assertEquals("5 ночей сна", WeekFace.fragment(sleep.copy(progress = 0), emptyList(), thursday))
    }

    @Test
    fun `never 0 of 3 - a week ahead, a first week gathering, care waits`() {
        val zero = listOf(training.copy(progress = 0), sleep.copy(progress = 0))
        val ahead = WeekFace.goals(view(zero), emptyMap(), path)
        assertNull(ahead.verdict.count)
        assertEquals(WeekFaceCopy.AHEAD, ahead.verdict.word)
        assertTrue(ahead.verdict.left!!.startsWith("Осталось 4 дня: "), ahead.verdict.left)
        val first = WeekFace.goals(view(zero, kind = WeekKind.FIRST), emptyMap(), path, kind = WeekKind.FIRST)
        assertEquals(WeekFaceCopy.GATHERING, first.verdict.word)
        val sick = WeekFace.goals(view(listOf(training, sleep)), days, path, WeekCare(sick = true))
        assertNull(sick.verdict.count)
        assertEquals(WeekFaceCopy.GOALS_WAIT, sick.verdict.left)
        val one = WeekFace.goals(view(listOf(training, sleep)), days, path)
        assertEquals("1 из 2", one.verdict.count)
        assertEquals("держится", one.verdict.word)
    }

    @Test
    fun `chips say where a goal comes from`() {
        val stage = WeekFace.stage(path, thursday)
        assertEquals(PathStage(3, 12, 1), stage)
        assertEquals("из Пути · Сила", WeekFace.chip(training, stage))
        assertEquals("из Пути · Сон", WeekFace.chip(sleep, stage))
        assertEquals("твоя · к вехе 1", WeekFace.chip(walk, stage))
        assertEquals("твоя", WeekFace.chip(walk, null))
        assertEquals("эксперимент", WeekFace.chip(walk.copy(source = GoalSource.EXPERIMENT), stage))
        assertEquals("стартовая неделя", WeekFace.chip(training.copy(source = GoalSource.START), null))
        assertEquals(2, WeekFace.stage(path.copy(takenOn = monday.minusWeeks(5)), thursday)!!.nextMilestone)
        assertEquals(6, WeekFace.stage(path.copy(takenOn = monday.minusWeeks(5)), thursday)!!.week)
        assertEquals(5, WeekFace.stage(path.copy(takenOn = monday.minusWeeks(5), pausedDays = 7), thursday)!!.week, "a pause stops the clock")
        assertNull(WeekFace.stage(PathWeek.NoGoal, thursday))
    }

    @Test
    fun `cards - 2 to 3, the stage first, one experiment at most`() {
        val exp = walk.copy(id = WeekGoalRules.PATTERN_PREFIX + "a", source = GoalSource.EXPERIMENT)
        val exp2 = exp.copy(id = WeekGoalRules.PATTERN_PREFIX + "b")
        val starter = training.copy(id = WeekGoalRules.ID_FOCUS, source = GoalSource.START)
        val picked = WeekFace.pick(listOf(exp, starter, exp2, sleep, training))
        assertEquals(listOf(sleep.id, training.id, exp.id), picked.map { it.id })
    }

    @Test
    fun `without a Path the week invents no goals, unless care comes first`() {
        assertEquals(WeekLayout.NO_PATH, WeekFace.layout(PathWeek.NoGoal, WeekCare()))
        assertEquals(WeekLayout.GOALS, WeekFace.layout(PathWeek.NoGoal, WeekCare(sick = true)))
        assertEquals(WeekLayout.GOALS, WeekFace.layout(PathWeek.NoGoal, WeekCare(crisis = true)))
        assertEquals(WeekLayout.GOALS, WeekFace.layout(PathWeek.NoGoal, WeekCare(storm = true)))
        assertEquals(WeekLayout.GOALS, WeekFace.layout(PathWeek.Paused, WeekCare()), "grief and a pause hold the Path")
        assertEquals(WeekLayout.GOALS, WeekFace.layout(path, WeekCare()))
        val face = WeekFace.build(view(listOf(training)), days, PathWeek.NoGoal, nights = WeekFace.nights(listOf(6.5, 7.0)), behind = WeekFace.behind(38, 90))
        val noPath = assertIs<WeekFaceView.NoPath>(face)
        assertEquals(NightsSeen(405, 2, 7), noPath.nights)
        assertEquals(BehindSeen(38, 90), noPath.behind)
    }

    @Test
    fun `what is seen already - honest lines, none without data`() {
        assertNull(WeekFace.nights(emptyList()))
        assertNull(WeekFace.behind(0, 90))
        assertEquals("Ночи за неделю: средняя 6:44", WeekFaceCopy.nights(404))
        assertEquals("За плечами: 38 тренировок за 90 дней", WeekFaceCopy.behind(38, 90))
        assertEquals("За плечами: 1 тренировка за 31 день", WeekFaceCopy.behind(1, 31))
        assertEquals("За плечами: 22 тренировки за 30 дней", WeekFaceCopy.behind(22, 30))
    }

    @Test
    fun `a card is one spoken node`() {
        val c = WeekFace.card(training, days.getValue(training.id), monday, thursday, WeekFace.stage(path, thursday))
        assertEquals(
            "Силовые. 2 из 3. из Пути · Сила. Понедельник, среда — сделано; четверг, пятница, суббота — запланировано. ещё 1 — удобнее в пт или сб",
            c.spoken,
        )
    }

    @Test
    fun `the words of the face pass the stop dictionary`() {
        WeekFaceCopy.all().forEach { assertEquals(emptyList(), WeekStopWords.hits(it), "«$it»") }
    }
}
