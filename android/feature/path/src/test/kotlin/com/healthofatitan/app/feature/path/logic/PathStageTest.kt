package com.healthofatitan.app.feature.path.logic

import com.healthofatitan.app.domain.path.ComponentKey
import com.healthofatitan.app.domain.path.Criterion
import com.healthofatitan.app.domain.path.GoalComponent
import com.healthofatitan.app.domain.path.PathFlags
import com.healthofatitan.app.domain.path.PathMetric
import com.healthofatitan.app.domain.path.PracticalGoal
import com.healthofatitan.app.domain.path.Role
import com.healthofatitan.app.domain.path.StageRules
import com.healthofatitan.app.domain.today.GoalMeasure
import com.healthofatitan.app.domain.today.GoalSource
import com.healthofatitan.app.domain.today.Sphere
import com.healthofatitan.app.domain.today.TaskKind
import com.healthofatitan.app.domain.today.WeekGoal
import com.healthofatitan.app.domain.today.WeekPlan
import com.healthofatitan.app.domain.today.week.WeekGoalRules
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** «Путь» v3 (A66 §5): the milestone card, the lever line, the week link, the 12 weeks, the trail. */
class PathStageTest {
    private val today = LocalDate.of(2026, 11, 5) // Thursday

    private fun goal(start: LocalDate = today.minusDays(15)) = PracticalGoal(
        id = 1, title = "Подтянуться 10 раз", why = "Чтобы снова чувствовать себя сильным",
        start = start, deadline = start.plusWeeks(12),
        criteria = listOf(Criterion("ночи от 7 ч", 4.0, 5.0, "из 7", PathMetric.SLEEP_NIGHTS)),
        components = listOf(GoalComponent(ComponentKey.SLEEP, Role.LEAD, 3, 2, isLever = true)),
    )

    private fun wg(id: String, progress: Int, target: Int, source: GoalSource = GoalSource.STAGE, measure: GoalMeasure = GoalMeasure.TIMES, unit: String = "раз") =
        WeekGoal(id, Sphere.BODY, TaskKind.TRAINING, id, target, unit, progress, source, measure)

    @Test
    fun `the milestone card names the milestone, the days to it, its date and the measurement`() {
        val g = goal()
        val next = StageRules.nextMilestone(g, today)
        assertEquals(4, next?.first)
        val m = assertNotNull(PathStage.milestone(g, today, next))
        assertEquals("Веха 1 · через 12 дней", m.title)
        assertEquals(RuWords.dated(StageRules.milestone(g, 4, today)), m.date)
        assertEquals("Замер: ночи от 7 ч", m.measure)
        assertFalse(m.meeting)
    }

    @Test
    fun `on the day of the milestone it is the meeting, after week 12 there is none`() {
        val g = goal()
        val m = assertNotNull(PathStage.milestone(g, today, 8 to 0L))
        assertEquals("Веха 2 · сегодня", m.title)
        assertTrue(m.meeting)
        assertNull(PathStage.milestone(g, today, null))
    }

    @Test
    fun `russian dates and plurals`() {
        assertEquals("вс, 15 ноября", RuWords.dated(LocalDate.of(2026, 11, 15)))
        assertEquals("1 день", RuWords.days(1))
        assertEquals("3 дня", RuWords.days(3))
        assertEquals("10 дней", RuWords.days(10))
        assertEquals("11 дней", RuWords.days(11))
        assertEquals("21 день", RuWords.days(21))
        assertEquals("2–8 ноя", RuWords.range(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 8)))
        assertEquals("30 ноя – 6 дек", RuWords.range(LocalDate.of(2026, 11, 30), LocalDate.of(2026, 12, 6)))
    }

    @Test
    fun `the lever line reads the week goal of the lever, the week's own numbers`() {
        val plan = WeekPlan(
            LocalDate.of(2026, 11, 2),
            listOf(wg(WeekGoalRules.ID_SLEEP, 2, 5, measure = GoalMeasure.NIGHTS, unit = "ночей"), wg(WeekGoalRules.ID_FOCUS, 1, 4)),
        )
        val l = assertNotNull(PathStage.lever(goal(), plan, PathFlags.DEFAULT))
        assertEquals("Рычаг этапа — сон", l.title)
        assertEquals("2 из 5 ночей на этой неделе", l.line)
        assertNull(PathStage.lever(goal(), null, PathFlags.DEFAULT)?.line, "no plan: no invented numbers")
        assertNull(PathStage.lever(goal().copy(components = emptyList()), plan, PathFlags.DEFAULT))
    }

    @Test
    fun `the week link counts done goals and never shows a shaming zero`() {
        val monday = LocalDate.of(2026, 11, 2)
        val two = WeekPlan(monday, listOf(wg("a", 3, 3), wg("b", 5, 4), wg("c", 0, 2), wg("x", 0, 1, GoalSource.EXPERIMENT)))
        assertEquals("Цели недели · 2 из 3 ›", PathStage.weekLink(two))
        val none = WeekPlan(monday, listOf(wg("a", 0, 3), wg("b", 1, 4)))
        assertEquals(PathV3Copy.WEEK_LINK, PathStage.weekLink(none))
        assertNull(PathStage.weekLink(null))
        assertNull(PathStage.weekLink(WeekPlan(monday, emptyList())))
    }

    @Test
    fun `a week is counted at two of three goals`() {
        assertTrue(PathStage.counted(listOf(wg("a", 3, 3), wg("b", 4, 4), wg("c", 0, 2))))
        assertFalse(PathStage.counted(listOf(wg("a", 3, 3), wg("b", 1, 4), wg("c", 0, 2))))
        assertTrue(PathStage.counted(listOf(wg("a", 3, 3), wg("b", 4, 4))))
        assertFalse(PathStage.counted(listOf(wg("a", 3, 3), wg("b", 1, 4))))
        assertFalse(PathStage.counted(emptyList()))
        assertFalse(PathStage.counted(listOf(wg("x", 1, 1, GoalSource.EXPERIMENT))), "the experiment is not a goal of the stage")
    }

    @Test
    fun `twelve cells - counted, a neutral past week, the current one, the milestones`() {
        val v = PathStage.weeks(current = 5, counted = setOf(1, 2, 4), paused = false)
        assertEquals(12, v.cells.size)
        assertEquals(listOf(StageCell.COUNTED, StageCell.COUNTED, StageCell.PAST, StageCell.COUNTED, StageCell.NOW), v.cells.take(5).map { it.state })
        assertTrue(v.cells.drop(5).all { it.state == StageCell.AHEAD })
        assertEquals(listOf(4, 8, 12), v.cells.filter { it.milestone }.map { it.week })
        assertEquals("3 засчитаны", v.counted)
        assertEquals("Засчитаны недели 1, 2 и 4; неделя 3 — не в этот раз; идёт 5", v.spoken)
    }

    @Test
    fun `no counted week says nothing, a pause stands the calendar`() {
        val v = PathStage.weeks(current = 1, counted = emptySet(), paused = true)
        assertNull(v.counted)
        assertEquals(StageCell.PAUSE, v.cells.first().state)
        assertEquals("неделя 1 на паузе", v.spoken)
        assertEquals("1 засчитана", PathStage.weeks(3, setOf(1), false).counted)
        assertEquals("5 засчитано", PathStage.weeks(7, setOf(1, 2, 3, 4, 5), false).counted)
    }

    @Test
    fun `the stelae go smaller towards the horizon and alternate sides, the twelfth stands on it`() {
        val s = PathStage.stelae()
        assertEquals(listOf(4, 8, 12), s.map { it.week })
        assertTrue(s[0].depth < s[1].depth && s[1].depth < s[2].depth)
        assertTrue(s[0].scale > s[1].scale && s[1].scale > s[2].scale)
        assertEquals(1f, s[2].depth, 1e-4f)
        assertEquals(listOf(1, -1, 1), s.map { it.side })
        assertEquals(0.7f, 1f - s[0].depth, 0.05f)
    }

    @Test
    fun `you walk the trail with the weeks`() {
        assertNull(PathStage.you(0))
        val w3 = assertNotNull(PathStage.you(3))
        val w9 = assertNotNull(PathStage.you(9))
        assertTrue(w3.depth < w9.depth)
        assertTrue(w3.scale > w9.scale)
        assertEquals(-1, w3.side)
        assertTrue(PathStage.ground(0f) == 1f && PathStage.ground(12f) == 0f)
    }

    @Test
    fun `the stage week is judged by its last day`() {
        val g = goal(start = LocalDate.of(2026, 10, 21))
        assertEquals(LocalDate.of(2026, 10, 27), PathStage.stageWeekEnd(g, 1))
        assertEquals(LocalDate.of(2026, 11, 3), PathStage.stageWeekEnd(g, 2))
    }

    @Test
    fun `the scene is one sentence for talkback`() {
        assertEquals("Цель: Подтянуться 10 раз. Неделя 3 из 12. До вехи 1 — 10 дней", PathV3Copy.scene("Подтянуться 10 раз", 3, 12, 4 to 10L))
        assertEquals("Цель: Подтянуться 10 раз. Неделя 3 из 12", PathV3Copy.scene("Подтянуться 10 раз", 3, 12, null))
    }
}
