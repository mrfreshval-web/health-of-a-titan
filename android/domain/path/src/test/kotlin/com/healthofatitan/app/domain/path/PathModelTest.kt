package com.healthofatitan.app.domain.path

import com.healthofatitan.app.domain.path.PathFixtures.TODAY
import com.healthofatitan.app.domain.path.PathFixtures.goal
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** R1–R8 of A54 §4, the state model of A56 §5, the Titan of the horizon of A57 §4. */
class PathModelTest {

    @Test
    fun `14 components, 5 + 4 + 5, marks in the MVP only for Earth, Sleep and Attention`() {
        assertEquals(14, ComponentKey.entries.size)
        assertEquals(5, ComponentKey.of(Element.EARTH).size)
        assertEquals(4, ComponentKey.of(Element.SKY).size)
        assertEquals(5, ComponentKey.of(Element.WATER).size)
        val markable = ComponentKey.entries.filter { it.markable }.toSet()
        assertEquals(ComponentKey.of(Element.EARTH).filter { it.hasMarks }.toSet() + ComponentKey.SLEEP + ComponentKey.ATTENTION, markable)
        // one dictionary of names: every key has a working and a model name
        ComponentKey.entries.forEach { assertTrue(PathLexicon.name(it).isNotBlank() && PathLexicon.modelName(it).isNotBlank()) }
        assertEquals(listOf("начало", "база", "уверенно", "сильно", "свободно"), (1..5).map(PathLexicon::mark))
        assertEquals(listOf("ведёт", "подпирает", "бережёт"), Role.entries.map(PathLexicon::role))
    }

    @Test
    fun `migration of statuses - REPLACED is RELEASED, DONE is TAKEN`() {
        assertEquals(GoalStatus.RELEASED, GoalStatus.parse("REPLACED"))
        assertEquals(GoalStatus.TAKEN, GoalStatus.parse("DONE"))
        assertEquals(GoalStatus.ACTIVE, GoalStatus.parse("ACTIVE"))
        assertNull(GoalStatus.parse("ANY"))
        assertEquals(8, GoalStatus.entries.size)
    }

    @Test
    fun `R1 a template by the dictionary, no hit is a goal of one's own`() {
        assertEquals(PathTemplates.RACE, PathTemplates.match("Полумарафон весной")?.id)
        assertEquals(PathTemplates.PROMOTION, PathTemplates.match("Стать руководителем отдела к концу года", PathFixtures.ALL_TEMPLATES)?.id)
        assertEquals(PathTemplates.LANGUAGE, PathTemplates.match("Английский до B2", PathFixtures.ALL_TEMPLATES)?.id)
        assertEquals(PathTemplates.ENERGY, PathTemplates.match("чтобы хватало сил на детей вечером")?.id)
        assertEquals(PathTemplates.CLOSER, PathTemplates.match("ближе с семьёй", PathFixtures.ALL_TEMPLATES)?.id)
        assertNull(PathTemplates.match("научиться играть на гитаре", PathFixtures.ALL_TEMPLATES))
        assertNull(PathTemplates.match("Английский до B2"), "D-051: only the 3 of the MVP by default")
        // «вес» is a word, not the start of «весной»
        assertEquals(GoalGate.OK, GoalGuard.check("марафон весной"))
    }

    @Test
    fun `R2-R5 decomposition - template, Given, start from Taken, the lever`() {
        val d = PathDecomposer.decompose(
            DecompositionInput(
                template = PathTemplates.byId(PathTemplates.RACE),
                given = GivenLayer(listOf(PathFixtures.knee)),
                taken = listOf(TakenItem(ComponentKey.CARDIO, 2, "весна 2025")),
                sufficiency = listOf(Sufficiency(ComponentKey.SLEEP, 6.2, 7.0), Sufficiency(ComponentKey.REST, 2.0, 2.0)),
            ),
        )
        assertEquals(listOf(ComponentKey.CARDIO, ComponentKey.MOBILITY, ComponentKey.REST, ComponentKey.SLEEP), d.components.map { it.key })
        assertEquals("без прыжков", d.components.first { it.key == ComponentKey.CARDIO }.safeVersion)
        assertEquals(listOf("колено: без прыжков"), d.givenNotes)
        assertEquals(2, d.components.first().startMark)
        assertEquals(3, d.components.first().targetMark)
        assertEquals(ComponentKey.SLEEP, d.lever?.key)
        assertEquals(WaterCheck.IMPROVE, d.waterCheck)
        assertEquals("весна 2025", d.startFrom?.since)
        val goal = goal(d.components)
        assertEquals(emptyList(), PathValidator.goal(goal, GivenLayer(listOf(PathFixtures.knee))))
    }

    @Test
    fun `R5 without deficits the lever is the first lead, water holds`() {
        val d = PathDecomposer.decompose(DecompositionInput(PathTemplates.byId(PathTemplates.LANGUAGE)))
        assertEquals(ComponentKey.MASTERY, d.lever?.key)
        assertEquals(WaterCheck.HOLD, d.waterCheck)
    }

    @Test
    fun `R5 sleep steps down in no-sleep-numbers mode and after two stages`() {
        val suff = listOf(Sufficiency(ComponentKey.SLEEP, 5.5, 7.0), Sufficiency(ComponentKey.CLARITY, 1.8, 2.0))
        val t = PathTemplates.byId(PathTemplates.LANGUAGE)
        assertEquals(ComponentKey.SLEEP, PathDecomposer.decompose(DecompositionInput(t, sufficiency = suff)).lever?.key)
        assertEquals(ComponentKey.CLARITY, PathDecomposer.decompose(DecompositionInput(t, sufficiency = suff, noSleepNumbers = true)).lever?.key)
        assertEquals(ComponentKey.CLARITY, PathDecomposer.decompose(DecompositionInput(t, sufficiency = suff, sleepLeverStages = 2)).lever?.key)
    }

    @Test
    fun `R2 own goal - water added by HoT, at most two leads`() {
        val d = PathDecomposer.decompose(
            DecompositionInput(
                null,
                chosen = listOf(ComponentKey.MASTERY to Role.LEAD, ComponentKey.ATTENTION to Role.LEAD, ComponentKey.CLARITY to Role.LEAD),
            ),
        )
        assertEquals(PathTemplates.CUSTOM, d.templateId)
        assertTrue(d.components.any { it.key.element == Element.WATER })
        assertEquals(2, d.components.count { it.role == Role.LEAD })
        assertEquals(emptyList(), PathValidator.goal(goal(d.components)))
    }

    @Test
    fun `R2 weight and strength parts drop out while the flag is off`() {
        val d = PathDecomposer.decompose(DecompositionInput(PathTemplates.byId(PathTemplates.FORM)))
        assertFalse(d.components.any { it.key.weightOrStrength })
        assertTrue(d.components.any { it.role == Role.LEAD })
        val on = PathDecomposer.decompose(DecompositionInput(PathTemplates.byId(PathTemplates.FORM), flags = PathFixtures.FLAGS_ON))
        assertTrue(on.components.any { it.key == ComponentKey.STRENGTH })
    }

    @Test
    fun `R7 realism - no pace, on track, split with a month-rounded full date`() {
        assertEquals(Realism.NoPace, RealismRules.assess(4.0..6.0, 8, TODAY, null, null))
        assertIs<Realism.OnTrack>(RealismRules.assess(4.0..6.0, 12, TODAY, null, 1.0))
        val s = RealismRules.assess(4.0..6.0, 8, TODAY, 0.8, 1.0) as Realism.Split
        assertEquals(4.5, s.byDate.endInclusive, 0.01) // 0.8 × 8 × 0.7
        assertEquals(1, s.fullBy.dayOfMonth)
        assertTrue(s.fullBy.isAfter(TODAY.plusWeeks(8)))
        // the personal pace caps the safe one
        val capped = RealismRules.assess(4.0..6.0, 8, TODAY, 2.0, 0.5) as Realism.Split
        assertEquals(2.8, capped.byDate.endInclusive, 0.01)
    }

    @Test
    fun `R8 stage of 12 weeks with milestones 4, 8, 12`() {
        val g = goal(start = TODAY)
        assertEquals(1, StageRules.week(g, TODAY))
        assertEquals(TODAY.plusDays(27), StageRules.milestone(g, 4, TODAY))
        assertEquals(4 to 27L, StageRules.nextMilestone(g, TODAY))
        assertEquals(12, StageRules.week(g, TODAY.plusWeeks(20)))
        assertTrue(StageRules.stageOver(g, TODAY.plusWeeks(12)))
        assertEquals(2, StageRules.stagesTotal(g))
    }

    @Test
    fun `state model of A56 §5`() {
        val p = Initiator.PERSON
        val h = Initiator.HOT
        assertEquals(GoalStatus.ACTIVE, GoalLifecycle.next(GoalStatus.DRAFT, GoalEvent.TAKE, p))
        assertNull(GoalLifecycle.next(GoalStatus.DRAFT, GoalEvent.TAKE, h))
        assertEquals(GoalStatus.DRAFT, GoalLifecycle.next(GoalStatus.QUEUED, GoalEvent.FROM_QUEUE, p))
        assertEquals(GoalStatus.PAUSED, GoalLifecycle.next(GoalStatus.ACTIVE, GoalEvent.PAUSE, p))
        assertNull(GoalLifecycle.next(GoalStatus.ACTIVE, GoalEvent.PAUSE, h), "HoT only asks")
        assertEquals(GoalStatus.HELD, GoalLifecycle.next(GoalStatus.PAUSED, GoalEvent.HOLD, h))
        assertEquals(GoalStatus.REVIEW, GoalLifecycle.next(GoalStatus.ACTIVE, GoalEvent.STAGE_END, h))
        assertEquals(GoalStatus.ACTIVE, GoalLifecycle.next(GoalStatus.REVIEW, GoalEvent.NEXT_STAGE, h))
        assertEquals(GoalStatus.TAKEN, GoalLifecycle.next(GoalStatus.REVIEW, GoalEvent.CLOSE, p))
        assertEquals(GoalStatus.RELEASED, GoalLifecycle.next(GoalStatus.PAUSED, GoalEvent.RELEASE, p))
        assertEquals(GoalStatus.QUEUED, GoalLifecycle.next(GoalStatus.RELEASED, GoalEvent.TO_QUEUE, h))
        assertEquals(GoalStatus.ACTIVE, GoalLifecycle.next(GoalStatus.RELEASED, GoalEvent.UNDO_RELEASE, p))
        assertNull(GoalLifecycle.next(GoalStatus.TAKEN, GoalEvent.RELEASE, p))
        assertTrue(GoalLifecycle.draftExpired(TODAY, TODAY.plusDays(7)))
        assertFalse(GoalLifecycle.draftExpired(TODAY, TODAY.plusDays(6)))
        assertTrue(GoalLifecycle.holdMayEnd(TODAY, TODAY.plusDays(7)))
    }

    @Test
    fun `Titan of the horizon - one step every Sunday, fires and shared halos`() {
        // TODAY is a Saturday: the first step is tomorrow
        assertEquals(0, HorizonRules.step(TODAY, TODAY, 0))
        assertEquals(1, HorizonRules.step(TODAY, TODAY.plusDays(1), 0))
        assertEquals(1, HorizonRules.step(TODAY, TODAY.plusDays(7), 0))
        assertEquals(2, HorizonRules.step(TODAY, TODAY.plusDays(8), 0))
        assertEquals(12, HorizonRules.step(TODAY, TODAY.plusWeeks(30), 0))
        val h = HorizonRules.build(goal(), TODAY, emptySet())
        assertEquals("Тот, у кого есть силы на лето", h.name)
        assertEquals(
            listOf(HorizonFire(ComponentKey.CARDIO, HorizonLight.ON_HORIZON), HorizonFire(ComponentKey.ATTENTION, HorizonLight.SHARED), HorizonFire(ComponentKey.REST, HorizonLight.SHARED)),
            h.fires,
        )
        assertEquals("Ты к ${PathCopy.date(goal().deadline)}", HorizonRules.build(goal().copy(beName = " "), TODAY, emptySet()).name)
        assertEquals(ComponentKey.CARDIO, HorizonRules.firstFromTaken(goal(), listOf(TakenItem(ComponentKey.CARDIO, 2, "весна"))))
        assertTrue(HorizonRules.build(goal(start = TODAY.minusWeeks(13)), TODAY, emptySet()).meeting)
    }

    @Test
    fun `crossroads - nothing chosen in advance, options become changes`() {
        val g = goal()
        assertEquals(3, CrossroadsRules.AFTER_PAUSE.size)
        assertEquals(5, CrossroadsRules.LAG.size)
        assertNull(CrossroadsRules.change(g, CrossroadsOption.AS_IS))
        val less = CrossroadsRules.change(g, CrossroadsOption.LESS_BY_DATE) as GoalChange.Crossroads
        val crit = (less.inner as GoalChange.SetCriteria).criteria.single()
        assertTrue(crit.low < crit.high && crit.high < 4.0)
        assertIs<GoalChange.RestartFromToday>((CrossroadsRules.change(g, CrossroadsOption.REBUILD_FROM_TODAY) as GoalChange.Crossroads).inner)
        assertEquals(GoalChange.SetLever(ComponentKey.ATTENTION), (CrossroadsRules.change(g, CrossroadsOption.SWITCH_LEVER) as GoalChange.Crossroads).inner)
    }

    @Test
    fun `overview of the slice - states, light only with Form or ed_care, empty path`() {
        val o = PathOverviewBuilder.build(TODAY, goal(), mapOf(ComponentKey.SLEEP to 2), emptySet(), GivenLayer(listOf(PathFixtures.knee.copy(affects = setOf(ComponentKey.MOBILITY)))))
        assertEquals(14, o.parts.size)
        assertEquals(PartState.MARKS, o.parts.first { it.key == ComponentKey.CARDIO }.state)
        assertEquals(PartState.PRACTICE, o.parts.first { it.key == ComponentKey.REST }.state)
        assertEquals(PartState.OFF, o.parts.first { it.key == ComponentKey.MOBILITY }.state)
        assertEquals("без прыжков", o.parts.first { it.key == ComponentKey.MOBILITY }.safeVersion)
        assertTrue(o.parts.first { it.key == ComponentKey.SLEEP }.lever)
        assertEquals(BalanceKind.EVEN, o.balance.kind)
        assertFalse(o.lightOnly)
        assertTrue(PathOverviewBuilder.build(TODAY, goal(), emptyMap(), emptySet(), GivenLayer(edCare = true)).lightOnly)
        val empty = PathOverview.empty(TODAY)
        assertEquals(BalanceKind.NO_GOAL, empty.balance.kind)
        assertTrue(empty.parts.all { it.state == PartState.OFF })
        val heavy = PathOverviewBuilder.build(TODAY, goal(), emptyMap(), emptySet(), GivenLayer.NONE, heavy = true)
        assertFalse(heavy.whyVisible)
        assertEquals(PathCopy.STATUS_HOLDS, heavy.elements.first { it.element == Element.WATER }.status)
    }

    @Test
    fun `codec round trip keeps every field`() {
        val g = goal().copy(
            obstacle = Obstacle("вечером | ем = «на нервах»\nи всё", "если 21:00, то чай"),
            pauseFrom = TODAY, pauseReason = PauseReason.GRIEF, status = GoalStatus.PAUSED, pausedDays = 3,
        )
        assertEquals(g.copy(version = 4), GoalCodec.decode(GoalCodec.encode(g), g.id, 4))
        val crit = listOf(Criterion("талия", -6.0, -4.0, "см", PathMetric.WAIST), Criterion("a|b", 1.0, 2.0, "", null))
        assertEquals(crit, GoalCodec.decodeCriteria(GoalCodec.encodeCriteria(crit)))
        assertNull(GoalCodec.decode("garbage", 1, 1))
    }

    @Test
    fun `D-051 holds come through one safety seam, the local one reads the goal and the week`() {
        assertEquals(SafetyHold.NONE, PathSafety.LOCAL.hold(goal(), heavyWeek = false))
        assertTrue(PathSafety.LOCAL.hold(goal(status = GoalStatus.PAUSED), heavyWeek = false).paused)
        assertTrue(PathSafety.LOCAL.hold(goal(), heavyWeek = true).heavy)
        val bus = PathSafety { _, _ -> SafetyHold(held = true) }
        val o = PathOverviewBuilder.build(TODAY, goal(), emptyMap(), emptySet(), GivenLayer.NONE, safety = bus)
        assertTrue(o.paused)
        assertFalse(o.whyVisible)
        assertEquals(BalanceKind.HELD, o.balance.kind)
    }
}
