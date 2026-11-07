package com.healthofatitan.app.feature.path.logic

import com.healthofatitan.app.domain.path.ComponentKey
import com.healthofatitan.app.domain.path.Element
import com.healthofatitan.app.domain.path.GoalGate
import com.healthofatitan.app.domain.path.GoalStatus
import com.healthofatitan.app.domain.path.PathFlags
import com.healthofatitan.app.domain.path.PathTemplates
import com.healthofatitan.app.domain.path.Role
import com.healthofatitan.app.domain.path.TakenItem
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** «Назови цель» over the tables (A56 §2): the same words give the same goal (P-07). */
class RitualLogicTest {

    private val today = LocalDate.of(2026, 11, 7)
    private val flags = PathFlags.DEFAULT

    @Test
    fun `a goal in words finds its template and builds a valid goal`() {
        val d = RitualDraft(text = "Полумарафон весной", deadline = DeadlineChoice.SUMMER, why = "ради лёгкости")
        val given = PathGiven.from(emptySet())
        assertEquals(PathTemplates.RACE, RitualLogic.template(d, flags, given)?.id)
        val dec = RitualLogic.decomposition(d, flags, given, emptyList(), emptyList())
        val goal = RitualLogic.build(d, today, 1L, flags, given, dec)
        assertEquals(GoalStatus.ACTIVE, goal.status)
        assertEquals(LocalDate.of(2027, 6, 1), goal.deadline)
        assertEquals(emptyList(), RitualLogic.violations(goal, given, flags, emptyList()))
        assertEquals(goal, RitualLogic.build(d, today, 1L, flags, given, RitualLogic.decomposition(d, flags, given, emptyList(), emptyList())))
    }

    @Test
    fun `a goal without a template starts from the energy parts and still validates`() {
        val d = RitualDraft(text = "Чтобы утро было моим")
        val given = PathGiven.from(emptySet())
        val dec = RitualLogic.decomposition(d, flags, given, emptyList(), emptyList())
        val goal = RitualLogic.build(d, today, 1L, flags, given, dec)
        assertEquals(PathTemplates.CUSTOM, goal.templateId)
        assertEquals("Чтобы утро было моим", goal.title)
        assertEquals(emptyList(), RitualLogic.violations(goal, given, flags, emptyList()))
    }

    @Test
    fun `weight words wait behind the flag`() {
        assertEquals(GoalGate.WEIGHT_LATER, RitualLogic.gate(RitualDraft(text = "Похудеть на 10 кг"), flags, PathGiven.from(emptySet())))
        assertEquals(GoalGate.WEIGHT_CLOSED, RitualLogic.gate(RitualDraft(text = "Похудеть на 10 кг"), PathFlags(weightAndStrength = true), PathGiven.from(setOf("ed_history"))))
    }

    @Test
    fun `deadline chips stay between 4 weeks and 2 years`() {
        assertEquals(LocalDate.of(2027, 1, 30), RitualLogic.deadline(DeadlineChoice.WEEKS_12, today))
        assertEquals(LocalDate.of(2026, 12, 31), RitualLogic.deadline(DeadlineChoice.YEAR_END, today))
        assertEquals(LocalDate.of(2027, 12, 31), RitualLogic.deadline(DeadlineChoice.YEAR_END, LocalDate.of(2026, 12, 20)))
        assertEquals(LocalDate.of(2027, 11, 7), RitualLogic.deadline(DeadlineChoice.ONE_YEAR, today))
    }

    @Test
    fun `water is never removed and a third lead never appears`() {
        val d = RitualDraft(templateId = PathTemplates.ENERGY)
        val parts = RitualLogic.decomposition(d, flags, PathGiven.from(emptySet()), emptyList(), emptyList()).components
        val water = parts.filter { it.key.element == Element.WATER }
        if (water.size == 1) assertNull(RitualLogic.remove(parts, water.single().key))
        var p = parts
        repeat(6) { p = RitualLogic.cycleRole(p, p.last().key) }
        assertTrue(p.count { it.role == Role.LEAD } in 1..2)
        val added = RitualLogic.add(parts, Element.SKY, flags, PathGiven.from(emptySet()))
        assertTrue(added.size <= 6)
    }

    @Test
    fun `given from the profile keeps the knee safe and closes weight in ed care`() {
        val g = PathGiven.from(setOf("knee_injury", "ed_history"))
        assertTrue(g.edCare)
        assertNotNull(g.constraintOf(ComponentKey.CARDIO)?.safeVersion)
        assertEquals("колено", g.constraints.single().label)
        val d = RitualDraft(templateId = PathTemplates.RACE)
        val dec = RitualLogic.decomposition(d, flags, g, emptyList(), emptyList())
        assertTrue(dec.components.filter { it.key == ComponentKey.CARDIO }.all { it.safeVersion != null })
        assertTrue(dec.components.none { it.key == ComponentKey.BODY })
    }

    @Test
    fun `the first fire flies from what is already taken`() {
        val d = RitualDraft(templateId = PathTemplates.RACE)
        val given = PathGiven.from(emptySet())
        val taken = listOf(TakenItem(ComponentKey.CARDIO, 2, "весна 2025"))
        val dec = RitualLogic.decomposition(d, flags, given, taken, emptyList())
        val goal = RitualLogic.build(d, today, 1L, flags, given, dec)
        assertEquals(ComponentKey.CARDIO, RitualLogic.firstFire(goal, taken))
    }
}
