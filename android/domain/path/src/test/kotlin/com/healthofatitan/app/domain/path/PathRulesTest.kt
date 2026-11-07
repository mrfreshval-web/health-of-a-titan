package com.healthofatitan.app.domain.path

import com.healthofatitan.app.domain.path.PathFixtures.DAY
import com.healthofatitan.app.domain.path.PathFixtures.NOW
import com.healthofatitan.app.domain.path.PathFixtures.TODAY
import com.healthofatitan.app.domain.path.PathFixtures.applied
import com.healthofatitan.app.domain.path.PathFixtures.ctx
import com.healthofatitan.app.domain.path.PathFixtures.goal
import com.healthofatitan.app.domain.path.PathFixtures.proposal
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The checkable rules of A56 §4, one test (or more) per rule. P-21 (the goal stays on the device)
 * is checked over the sources in the feature module (PathPrivacyTest).
 */
class PathRulesTest {

    @Test
    fun `P-01 one live goal in the MVP and one draft`() {
        assertEquals(emptyList(), PathValidator.canStart(emptyList()))
        assertEquals(listOf(Violation.TOO_MANY_GOALS), PathValidator.canStart(listOf(goal())))
        assertEquals(listOf(Violation.TOO_MANY_GOALS), PathValidator.canStart(listOf(goal(status = GoalStatus.PAUSED))))
        assertEquals(emptyList(), PathValidator.canStart(listOf(goal(status = GoalStatus.TAKEN))))
        assertEquals(emptyList(), PathValidator.canStart(listOf(goal(), goal()), limit = PathValidator.MAX_GOALS))
        assertEquals(listOf(Violation.TOO_MANY_DRAFTS), PathValidator.canDraft(listOf(goal(status = GoalStatus.DRAFT))))
    }

    @Test
    fun `P-02 two to six parts, one or two leads, exactly one lever`() {
        assertEquals(emptyList(), PathValidator.goal(goal()))
        val one = goal(listOf(GoalComponent(ComponentKey.SLEEP, Role.LEAD, isLever = true)))
        assertTrue(Violation.TOO_FEW_COMPONENTS in PathValidator.goal(one))
        val seven = goal(ComponentKey.entries.filter { !it.weightOrStrength }.take(7).mapIndexed { i, k -> GoalComponent(k, if (i == 0) Role.LEAD else Role.PROP, isLever = i == 0) })
        assertTrue(Violation.TOO_MANY_COMPONENTS in PathValidator.goal(seven))
        val noLead = goal(goal().components.map { it.copy(role = Role.PROP) })
        assertTrue(Violation.NO_LEAD in PathValidator.goal(noLead))
        val threeLeads = goal(goal().components.map { if (it.key == ComponentKey.SLEEP) it else it.copy(role = Role.LEAD) })
        assertTrue(Violation.TOO_MANY_LEADS in PathValidator.goal(threeLeads))
        val twoLevers = goal(goal().components.map { it.copy(isLever = true) })
        assertTrue(Violation.LEVER_NOT_ONE in PathValidator.goal(twoLevers))
        val noLever = goal(goal().components.map { it.copy(isLever = false) })
        assertTrue(Violation.LEVER_NOT_ONE in PathValidator.goal(noLever))
        // one lever per person: another live goal with a lever breaks it
        assertTrue(Violation.LEVER_NOT_ONE in PathValidator.goal(goal(), others = listOf(goal())))
    }

    @Test
    fun `P-03 water in every goal and the last water is replaced, never removed`() {
        val dry = goal(goal().components.filter { it.key.element != Element.WATER }.map { it.copy(isLever = it.role == Role.LEAD) })
        assertTrue(Violation.NO_WATER in PathValidator.goal(dry))
        val g = goal(goal().components.filter { it.key != ComponentKey.REST })
        assertFalse(PathValidator.removable(g, ComponentKey.SLEEP))
        val r = ChangeEngine.propose(g, GoalChange.RemoveComponent(ComponentKey.SLEEP), Initiator.PERSON, ctx())
        assertEquals(Refusal.LAST_WATER, (r as ChangeResult.Refused).reason)
        val replaced = ChangeEngine.propose(g, GoalChange.ReplaceComponent(ComponentKey.SLEEP, ComponentKey.PEOPLE), Initiator.PERSON, ctx()).proposal()
        assertTrue(replaced.after.has(ComponentKey.PEOPLE))
        assertEquals(1, replaced.after.components.count { it.isLever })
        // every template keeps water in the role «бережёт» or higher
        PathTemplates.ALL.forEach { t -> assertTrue(t.components.any { (k, _) -> k.element == Element.WATER }, t.id) }
    }

    @Test
    fun `P-04 marks only on a ladder, never on a mirror`() {
        val feelings = goal(goal().components + GoalComponent(ComponentKey.FEELINGS, Role.GUARD, targetMark = 3))
        assertTrue(Violation.MARK_WITHOUT_LADDER in PathValidator.goal(feelings))
        val mastery = goal(goal().components + GoalComponent(ComponentKey.MASTERY, Role.PROP, targetMark = 3))
        assertTrue(Violation.MARK_WITHOUT_LADDER in PathValidator.goal(mastery), "the MVP has marks only for Земля, Сон, Внимание")
        listOf(ComponentKey.OUTLOOK, ComponentKey.FEELINGS, ComponentKey.FUEL).forEach { assertFalse(it.hasMarks) }
        val hrv = goal().copy(criteria = listOf(Criterion("вариабельность", 40.0, 50.0, "мс", PathMetric.HRV)))
        assertTrue(Violation.MIRROR_AS_GOAL in PathValidator.goal(hrv))
        val r = ChangeEngine.propose(goal(), GoalChange.SetTargetMark(ComponentKey.ATTENTION, 3), Initiator.PERSON, ctx())
        assertIs<ChangeResult.Proposed>(r)
        val r2 = ChangeEngine.propose(goal(goal().components + GoalComponent(ComponentKey.FEELINGS, Role.GUARD)), GoalChange.SetTargetMark(ComponentKey.FEELINGS, 2), Initiator.PERSON, ctx())
        assertEquals(listOf(Violation.MARK_WITHOUT_LADDER), (r2 as ChangeResult.Refused).violations)
    }

    @Test
    fun `P-05 a constrained part is out or safe and no change lifts it`() {
        val given = GivenLayer(listOf(PathFixtures.knee))
        val unsafe = goal()
        assertTrue(Violation.GIVEN_NOT_SAFE in PathValidator.goal(unsafe, given))
        val safe = ChangeEngine.applySelf(unsafe, GoalChange.ApplyGiven(given), ctx(given = given)).applied()
        assertEquals("без прыжков", safe.goal.component(ComponentKey.CARDIO)?.safeVersion)
        assertEquals(ChangeType.SAFETY, safe.version.changeType)
        assertEquals(Initiator.HOT, safe.version.initiator)
        assertEquals(emptyList(), PathValidator.goal(safe.goal, given))
        // a later change cannot take the safe version off: the validator stops it
        val lifted = safe.goal.copy(components = safe.goal.components.map { it.copy(safeVersion = null) })
        assertTrue(Violation.GIVEN_NOT_SAFE in PathValidator.goal(lifted, given))
        // and the safety change is never undone
        val undo = ChangeEngine.undo(safe.goal, listOf(safe.version), NOW + 1000)
        assertIs<ChangeResult.Refused>(undo)
        // a constraint without a safe version excludes the part
        val strict = GivenLayer(listOf(PathFixtures.knee.copy(safeVersion = null)))
        val out = ChangeEngine.safe(goal(), strict)
        assertFalse(out.has(ComponentKey.CARDIO))
        assertEquals(emptyList(), PathValidator.goal(out, strict))
    }

    @Test
    fun `P-06 deadline from 4 weeks to 24 months, ranges, a step of at most 30 percent`() {
        assertEquals(Violation.DEADLINE_TOO_SOON, PathValidator.deadline(TODAY, TODAY.plusWeeks(3)))
        assertNull(PathValidator.deadline(TODAY, TODAY.plusWeeks(4)))
        assertNull(PathValidator.deadline(TODAY, TODAY.plusMonths(24)))
        assertEquals(Violation.DEADLINE_TOO_FAR, PathValidator.deadline(TODAY, TODAY.plusMonths(24).plusDays(1)))
        val point = goal().copy(criteria = listOf(Criterion("силовые", 3.0, 3.0, "", PathMetric.STRENGTH_SESSIONS)))
        assertTrue(Violation.CRITERION_NOT_RANGE in PathValidator.goal(point, flags = PathFixtures.FLAGS_ON))
        assertTrue(Violation.CRITERIA_COUNT in PathValidator.goal(goal().copy(criteria = emptyList())))
        assertTrue(RealismRules.stepOk(10.0, 13.0))
        assertFalse(RealismRules.stepOk(10.0, 13.5))
        assertEquals(13.0, RealismRules.raise(10.0))
        // a target mark above one stage's reach is «целиком», not now
        val r = ChangeEngine.propose(goal(), GoalChange.SetTargetMark(ComponentKey.CARDIO, 5), Initiator.PERSON, ctx())
        assertIs<ChangeResult.Refused>(r)
        // every template's criteria are ranges
        PathTemplates.ALL.flatMap { it.criteria }.forEach { assertTrue(it.low < it.high, it.label) }
    }

    @Test
    fun `P-07 the same input gives the same decomposition`() {
        val input = DecompositionInput(
            template = PathTemplates.byId(PathTemplates.FORM),
            given = GivenLayer(listOf(PathFixtures.knee)),
            taken = listOf(TakenItem(ComponentKey.STRENGTH, 2, "весна 2025")),
            sufficiency = listOf(Sufficiency(ComponentKey.SLEEP, 6.2, 7.0), Sufficiency(ComponentKey.FUEL, 4.0, 5.0)),
            flags = PathFixtures.FLAGS_ON,
        )
        val a = PathDecomposer.decompose(input)
        val b = PathDecomposer.decompose(input.copy(sufficiency = input.sufficiency.reversed()))
        assertEquals(a, b)
        assertEquals(a, PathDecomposer.decompose(input))
    }

    @Test
    fun `P-08 calibration week is free, then one rebuild a week, the second asks`() {
        val g = goal(takenAt = NOW)
        val add = GoalChange.AddComponent(ComponentKey.PEOPLE, Role.PROP)
        val v1 = GoalVersion(g.id, 2, g, ChangeType.COMPOSITION, Initiator.PERSON, at = NOW + DAY)
        // calibration: no limit
        assertFalse(ChangeEngine.propose(g, add, Initiator.PERSON, ctx(nowMs = NOW + 2 * DAY, today = TODAY.plusDays(2), versions = listOf(v1))).proposal().soft)
        // after calibration: the second structural change of the week is soft-limited
        val day10 = NOW + 10 * DAY
        val earlier = GoalVersion(g.id, 2, g, ChangeType.COMPOSITION, Initiator.PERSON, at = day10 - 3600_000)
        val p = ChangeEngine.propose(g, add, Initiator.PERSON, ctx(nowMs = day10, today = TODAY.plusDays(10), versions = listOf(earlier))).proposal()
        assertTrue(p.soft)
        assertEquals(Refusal.SOFT_LIMIT, (ChangeEngine.apply(g, p, Initiator.PERSON, day10) as ChangeResult.Refused).reason)
        assertIs<ChangeResult.Applied>(ChangeEngine.apply(g, p, Initiator.PERSON, day10, override = true))
        // pause and release are never limited
        val pause = ChangeEngine.propose(g, GoalChange.Pause(PauseReason.VACATION, TODAY.plusDays(17)), Initiator.PERSON, ctx(nowMs = day10, today = TODAY.plusDays(10), versions = listOf(earlier))).proposal()
        assertFalse(pause.soft)
        // a new week: free again
        val nextWeek = NOW + 17 * DAY
        assertFalse(ChangeEngine.propose(g, add, Initiator.PERSON, ctx(nowMs = nextWeek, today = TODAY.plusDays(17), versions = listOf(earlier))).proposal().soft)
    }

    @Test
    fun `P-09 HoT changes the lever not more often than every 4 weeks`() {
        val g = goal()
        val hotLever = GoalVersion(g.id, 2, g, ChangeType.LEVER, Initiator.HOT, at = NOW)
        val r = ChangeEngine.propose(g, GoalChange.SetLever(ComponentKey.REST, LeverReason.HOT_PROPOSAL), Initiator.HOT, ctx(nowMs = NOW + 10 * DAY, versions = listOf(hotLever)))
        assertEquals(Refusal.LEVER_TOO_SOON, (r as ChangeResult.Refused).reason)
        assertIs<ChangeResult.Proposed>(ChangeEngine.propose(g, GoalChange.SetLever(ComponentKey.REST, LeverReason.HOT_PROPOSAL), Initiator.HOT, ctx(nowMs = NOW + 29 * DAY, versions = listOf(hotLever))))
        // safety: sleep off the lever is HoT's own and immediate
        val off = ChangeEngine.applySelf(g, GoalChange.SetLever(ComponentKey.CARDIO, LeverReason.SLEEP_STEP_DOWN), ctx(nowMs = NOW + DAY, versions = listOf(hotLever))).applied()
        assertEquals(ComponentKey.CARDIO, off.goal.lever?.key)
    }

    @Test
    fun `P-10 the third move of the deadline offers a talk and blocks nothing`() {
        val g = goal()
        val moves = (1..2).map { GoalVersion(g.id, it + 1, g, ChangeType.DEADLINE, Initiator.PERSON, at = NOW - it * 30 * DAY) }
        val second = ChangeEngine.propose(g, GoalChange.MoveDeadline(g.deadline.plusWeeks(2)), Initiator.PERSON, ctx(versions = moves.take(1))).proposal()
        assertFalse(second.suggestTalk)
        val third = ChangeEngine.propose(g, GoalChange.MoveDeadline(g.deadline.plusWeeks(2)), Initiator.PERSON, ctx(versions = moves)).proposal()
        assertTrue(third.suggestTalk)
        assertIs<ChangeResult.Applied>(ChangeEngine.apply(g, third, Initiator.PERSON, NOW))
    }

    @Test
    fun `P-11 every change makes a version with its initiator`() {
        val g = goal()
        val p = ChangeEngine.propose(g, GoalChange.SetMode(GoalMode.FULL), Initiator.MENTOR, ctx()).proposal()
        val a = ChangeEngine.apply(g, p, Initiator.PERSON, NOW).applied()
        assertEquals(2, a.goal.version)
        assertEquals(2, a.version.v)
        assertEquals(Initiator.MENTOR, a.version.initiator)
        assertEquals(ChangeType.MODE, a.version.changeType)
        val rename = ChangeEngine.applyCosmetic(a.goal, GoalChange.Rename("Силы на лето"), ctx()).applied()
        assertEquals(3, rename.version.v)
        assertEquals(Initiator.PERSON, rename.version.initiator)
        assertEquals(GoalCodec.encode(rename.goal), GoalCodec.encode(GoalCodec.decode(GoalCodec.encode(rename.goal), rename.goal.id, 3)!!))
    }

    @Test
    fun `P-12 undo 24 hours after a change and a release, 7 days after the crossroads`() {
        val g = goal()
        val v1 = GoalVersion(g.id, 1, g, ChangeType.CREATE, Initiator.PERSON, at = NOW - 30 * DAY)
        val moved = ChangeEngine.apply(g, ChangeEngine.propose(g, GoalChange.MoveDeadline(g.deadline.plusWeeks(4)), Initiator.PERSON, ctx()).proposal(), Initiator.PERSON, NOW).applied()
        val undone = ChangeEngine.undo(moved.goal, listOf(v1, moved.version), NOW + 23 * 3600_000).applied()
        assertEquals(g.deadline, undone.goal.deadline)
        assertEquals(ChangeType.UNDO, undone.version.changeType)
        assertEquals(Refusal.UNDO_WINDOW_OVER, (ChangeEngine.undo(moved.goal, listOf(v1, moved.version), NOW + 25 * 3600_000) as ChangeResult.Refused).reason)

        val cross = CrossroadsRules.change(g, CrossroadsOption.SHIFT_DEADLINE)!!
        val chosen = ChangeEngine.apply(g, ChangeEngine.propose(g, cross, Initiator.PERSON, ctx()).proposal(), Initiator.PERSON, NOW).applied()
        assertIs<ChangeResult.Applied>(ChangeEngine.undo(chosen.goal, listOf(v1, chosen.version), NOW + 6 * DAY))
        assertIs<ChangeResult.Refused>(ChangeEngine.undo(chosen.goal, listOf(v1, chosen.version), NOW + 8 * DAY))

        val released = ChangeEngine.apply(g, ChangeEngine.propose(g, GoalChange.Release(), Initiator.PERSON, ctx()).proposal(), Initiator.PERSON, NOW).applied()
        assertEquals(GoalStatus.RELEASED, released.goal.status)
        assertEquals(GoalStatus.ACTIVE, ChangeEngine.undo(released.goal, listOf(v1, released.version), NOW + 20 * 3600_000).applied().goal.status)
    }

    @Test
    fun `P-13 today changes only by safety, the week from Monday or now`() {
        val e = ChangeEngine.effects(GoalChange.AddComponent(ComponentKey.PEOPLE, Role.PROP), TODAY, weekNow = false)
        assertFalse(e.todayChanges)
        assertEquals(TODAY.plusDays(2), e.weekFrom) // Saturday → next Monday
        assertEquals(TODAY, ChangeEngine.effects(GoalChange.AddComponent(ComponentKey.PEOPLE, Role.PROP), TODAY, weekNow = true).weekFrom)
        assertTrue(ChangeEngine.effects(GoalChange.ApplyGiven(GivenLayer.NONE), TODAY, weekNow = false).todayChanges)
        assertNull(ChangeEngine.effects(GoalChange.Rename("x"), TODAY, false).weekFrom)
    }

    @Test
    fun `P-14 what was done is never rolled back and fires do not return`() {
        val g = goal()
        val moved = HorizonRules.transfers(g, mapOf(ComponentKey.CARDIO to 3), emptySet())
        assertEquals(setOf(ComponentKey.CARDIO), moved)
        // the target goes up later: the fire stays with the person
        val raised = g.copy(components = g.components.map { if (it.key == ComponentKey.CARDIO) it.copy(targetMark = 4) else it })
        assertEquals(setOf(ComponentKey.CARDIO), HorizonRules.transfers(raised, mapOf(ComponentKey.CARDIO to 3), moved))
        // even the part removed: the fire is not taken back
        assertTrue(ComponentKey.CARDIO in HorizonRules.transfers(goal(g.components.filter { it.key != ComponentKey.CARDIO }), emptyMap(), moved))
        // the overview keeps the filled marks whatever the goal says
        val o = PathOverviewBuilder.build(TODAY, raised.copy(status = GoalStatus.PAUSED, pauseFrom = TODAY), mapOf(ComponentKey.CARDIO to 3), moved, GivenLayer.NONE)
        assertEquals(3, o.parts.first { it.key == ComponentKey.CARDIO }.mark)
    }

    @Test
    fun `P-15 the experiment does not change in the middle of a week`() {
        val monday = StageRules.weekStart(TODAY.plusDays(3))
        val g = goal()
        val e = ExperimentRules.forWeek(g, monday, gentle = false, stormNet = false)!!
        assertEquals(ComponentKey.SLEEP, e.key)
        val changed = g.copy(components = g.components.filter { it.key != ComponentKey.SLEEP } + GoalComponent(ComponentKey.PEOPLE, Role.GUARD, isLever = true))
        assertEquals(ExperimentOutcome.PARTLY, ExperimentRules.outcome(e, changed))
        assertEquals(ExperimentOutcome.LEARNED, ExperimentRules.outcome(e, g))
        assertNull(ExperimentRules.forWeek(g, monday, gentle = true, stormNet = false))
        assertNull(ExperimentRules.forWeek(g, monday, gentle = false, stormNet = true))
        assertEquals(PathCopy.EFFECT_EXPERIMENT_SAME, ChangeEngine.effects(GoalChange.SetLever(ComponentKey.REST), TODAY, true).experiment)
    }

    @Test
    fun `P-16 a pause neither resets the stage nor burns weeks, the horizon stands`() {
        val start = TODAY.minusWeeks(5)
        val g = goal(start = start)
        assertEquals(6, StageRules.week(g, TODAY))
        val paused = ChangeEngine.apply(g, ChangeEngine.propose(g, GoalChange.Pause(PauseReason.UNWELL, TODAY.plusWeeks(2)), Initiator.PERSON, ctx()).proposal(), Initiator.PERSON, NOW).applied().goal
        val later = TODAY.plusWeeks(2)
        assertEquals(6, StageRules.week(paused, later))
        assertEquals(HorizonRules.step(start, TODAY, 0), HorizonRules.step(start, later, StageRules.pausedDays(paused, later)))
        assertTrue(HorizonRules.build(paused, later, emptySet()).seated)
        assertEquals(StageRules.milestone(g, 8, TODAY).plusWeeks(2), StageRules.milestone(paused, 8, later))
        assertFalse(AfloatRules.counts(paused))
        assertTrue(AfloatRules.counts(paused.copy(pauseReason = PauseReason.TOO_MUCH)))
        val back = ChangeEngine.apply(paused, ChangeEngine.propose(paused, GoalChange.Resume, Initiator.PERSON, ctx(today = later)).proposal(), Initiator.PERSON, NOW).applied().goal
        assertEquals(14, back.pausedDays)
        assertEquals(6, StageRules.week(back, later))
        assertEquals(7, StageRules.week(back, later.plusWeeks(1)))
    }

    @Test
    fun `P-17 no change without a card, except HoT's own rows`() {
        val g = goal()
        val p = ChangeEngine.propose(g, GoalChange.SetRole(ComponentKey.REST, Role.LEAD), Initiator.HOT, ctx()).proposal()
        // HoT cannot apply its own proposal: a person's tap is needed
        assertEquals(Refusal.NOT_ALLOWED, (ChangeEngine.apply(g, p, Initiator.HOT, NOW) as ChangeResult.Refused).reason)
        assertEquals(Refusal.NOT_ALLOWED, (ChangeEngine.apply(g, p, Initiator.MENTOR, NOW) as ChangeResult.Refused).reason)
        assertIs<ChangeResult.Applied>(ChangeEngine.apply(g, p, Initiator.PERSON, NOW))
        // only safety, hold and sleep off the lever go without a card
        assertEquals(Refusal.NOT_ALLOWED, (ChangeEngine.applySelf(g, GoalChange.SetMode(GoalMode.FULL), ctx()) as ChangeResult.Refused).reason)
        assertEquals(Refusal.NOT_ALLOWED, (ChangeEngine.applyCosmetic(g, GoalChange.MoveDeadline(g.deadline.plusWeeks(1)), ctx()) as ChangeResult.Refused).reason)
        // the goal, its date and «Зачем» belong to the person: HoT may not even propose them
        listOf(GoalChange.MoveDeadline(g.deadline.plusWeeks(1)), GoalChange.SetWhy("x"), GoalChange.Release(), GoalChange.SetMode(GoalMode.FULL)).forEach {
            assertEquals(Refusal.NOT_ALLOWED, (ChangeEngine.propose(g, it, Initiator.HOT, ctx()) as ChangeResult.Refused).reason, it.toString())
        }
        // a stale card is not applied
        val moved = ChangeEngine.apply(g, p, Initiator.PERSON, NOW).applied().goal
        assertEquals(Refusal.STALE, (ChangeEngine.apply(moved, p, Initiator.PERSON, NOW) as ChangeResult.Refused).reason)
        assertEquals(Refusal.EXPIRED, (ChangeEngine.apply(g, p, Initiator.PERSON, NOW + 2 * DAY) as ChangeResult.Refused).reason)
    }

    @Test
    fun `P-18 one push only, the return after a pause, never why or the letter`() {
        assertEquals(setOf(PathPush.RETURN_AFTER_PAUSE), PathPushes.ALLOWED)
        val g = goal().copy(status = GoalStatus.PAUSED, pauseFrom = TODAY, pauseReason = PauseReason.VACATION, pauseUntil = TODAY.plusWeeks(1))
        assertNull(PathPushes.due(g, TODAY.plusDays(3)))
        assertEquals(PathPush.RETURN_AFTER_PAUSE, PathPushes.due(g, TODAY.plusWeeks(1)))
        assertNull(PathPushes.due(g.copy(pauseReason = PauseReason.GRIEF, pauseUntil = null), TODAY.plusWeeks(5)))
        val text = PathPushes.text(PathPush.RETURN_AFTER_PAUSE)
        assertFalse(text.contains(g.why!!))
        val letter = Letter(g.id, "Ты справишься", LetterOpen.MILESTONE_8, TODAY)
        assertFalse(LetterRules.opens(letter, g.copy(start = TODAY.minusWeeks(10)), TODAY), "never on a pause")
        assertTrue(LetterRules.opens(letter, goal(start = TODAY.minusWeeks(8)), TODAY))
        assertFalse(LetterRules.opens(letter, goal(start = TODAY.minusWeeks(6)), TODAY))
        assertFalse(LetterRules.valid("x".repeat(301)))
    }

    @Test
    fun `P-19 no shaming words, no sum, the lag is never a distance`() {
        val hits = PathCopy.all().flatMap { t -> PathCopy.FORBIDDEN.filter { t.lowercase().contains(it) }.map { "$it in «$t»" } }
        assertEquals(emptyList(), hits)
        // the horizon step takes no progress: two goals with different marks, same calendar
        val a = HorizonRules.build(goal(), TODAY.plusWeeks(3), emptySet())
        val b = HorizonRules.build(goal(), TODAY.plusWeeks(3), setOf(ComponentKey.CARDIO))
        assertEquals(a.step, b.step)
        // the balance is a word, there is no aggregate in the API
        val o = PathOverviewBuilder.build(TODAY, goal(), emptyMap(), emptySet(), GivenLayer.NONE, listOf(Sufficiency(ComponentKey.SLEEP, 6.1, 7.0)))
        assertEquals(BalanceKind.LEVER, o.balance.kind)
        assertFalse(o.balance.title.any { it.isDigit() })
        val names = PathOverview::class.java.declaredFields.map { it.name.lowercase() } + Balance::class.java.declaredFields.map { it.name.lowercase() }
        assertTrue(names.none { it.contains("score") || it.contains("average") || it.contains("sum") || it.contains("percent") }, names.toString())
    }

    @Test
    fun `P-20 no medical goals, no weight in ed_care`() {
        assertEquals(GoalGate.MEDICAL, GoalGuard.check("Снизить давление к лету"))
        assertEquals(GoalGate.MEDICAL, GoalGuard.check("ферритин в норму"))
        assertEquals(GoalGate.MEDICAL, GoalGuard.check("слезть с таблеток"))
        assertEquals(GoalGate.HARMFUL, GoalGuard.check("спать 4 часа, чтобы успевать"))
        assertEquals(GoalGate.HARMFUL, GoalGuard.check("голодание 5 дней"))
        assertEquals(GoalGate.WEIGHT_CLOSED, GoalGuard.check("похудеть на 10 кг", GivenLayer(edCare = true), PathFixtures.FLAGS_ON))
        assertEquals(GoalGate.OK, GoalGuard.check("полумарафон весной"))
        assertEquals(GoalGate.OK, GoalGuard.check("силы на вечер и детей"))
        val form = PathTemplates.byId(PathTemplates.FORM)!!
        assertFalse(form in PathTemplates.offered(PathFixtures.FLAGS_ON, GivenLayer(edCare = true)))
        val withBody = goal(goal().components + GoalComponent(ComponentKey.BODY, Role.PROP))
        assertTrue(Violation.WEIGHT_CLOSED in PathValidator.goal(withBody, GivenLayer(edCare = true), PathFixtures.FLAGS_ON))
    }

    @Test
    fun `P-22 a goal is seen in exactly one layer`() {
        assertEquals(GoalLayer.CARRYING, GoalLifecycle.layer(GoalStatus.QUEUED))
        listOf(GoalStatus.ACTIVE, GoalStatus.PAUSED, GoalStatus.HELD, GoalStatus.REVIEW).forEach { assertEquals(GoalLayer.PATH, GoalLifecycle.layer(it)) }
        assertEquals(GoalLayer.TAKEN, GoalLifecycle.layer(GoalStatus.TAKEN))
        assertEquals(GoalLayer.NONE, GoalLifecycle.layer(GoalStatus.RELEASED))
        assertEquals(GoalLayer.NONE, GoalLifecycle.layer(GoalStatus.DRAFT))
        assertNull(PathOverviewBuilder.build(TODAY, goal(status = GoalStatus.QUEUED), emptyMap(), emptySet(), GivenLayer.NONE).goal)
    }

    @Test
    fun `weight and strength goals stay behind the flag, off by default`() {
        assertFalse(PathFlags.DEFAULT.weightAndStrength)
        val offered = PathTemplates.offered(PathFlags.DEFAULT, GivenLayer.NONE).map { it.id }
        assertFalse(PathTemplates.FORM in offered)
        assertFalse(PathTemplates.PULLUP in offered)
        assertFalse(PathTemplates.STRONG in offered)
        assertEquals(7, offered.size)
        assertEquals(10, PathTemplates.offered(PathFixtures.FLAGS_ON, GivenLayer.NONE).size)
        assertEquals(GoalGate.WEIGHT_LATER, GoalGuard.check("первое подтягивание"))
        assertEquals(GoalGate.WEIGHT_LATER, GoalGuard.check("сбросить 5 кг"))
        val strong = goal(goal().components + GoalComponent(ComponentKey.STRENGTH, Role.PROP))
        assertTrue(Violation.WEIGHT_LATER in PathValidator.goal(strong))
        assertNotNull(PathTemplates.match("первое подтягивание", PathFixtures.FLAGS_ON))
        assertNull(PathTemplates.match("первое подтягивание"))
    }
}
