package com.healthofatitan.app.domain.today.week

import com.healthofatitan.app.domain.today.CalendarWeek
import com.healthofatitan.app.domain.today.Corridor
import com.healthofatitan.app.domain.today.DayMode
import com.healthofatitan.app.domain.today.DayScore
import com.healthofatitan.app.domain.today.GoalMeasure
import com.healthofatitan.app.domain.today.Scenario
import com.healthofatitan.app.domain.today.TaskRules
import com.healthofatitan.app.domain.today.WeekPlan
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A52 §5: the rules W-01…W-08 of «Неделя», and the rules of the Titan of the week and the fitness age. */
class WeekRulesTest {

    private val monday = LocalDate.of(2026, 11, 2)
    private val friday = monday.plusDays(4)
    private val sunday = monday.plusDays(6)

    private val full = listOf(0.9f, 0.8f, 0.75f, 0.3f, 0.2f) // three supports ≥ 70 %
    private val half = listOf(0.6f, 0.55f, 0.5f, 0.1f, 0f) // three ≥ 50 %, none ≥ 70 %

    private fun fact(offset: Int, fills: List<Float>? = full, tasks: Int = 1, mode: DayMode? = DayMode.ROVNO, sick: Boolean = false) =
        DayFact(monday.plusDays(offset.toLong()), mode, fills, tasks, sick)

    private fun input(now: LocalDateTime, facts: List<DayFact>, plan: WeekPlan = WeekGoalRules.defaults(monday, first = false), status: PlanStatus = PlanStatus.APPROVED) =
        WeekInput(now = now, facts = facts, plan = plan, planStatus = status)

    // ── W-01 ─────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `W-01 a day is in support with one task and three supports at 70 percent, mini counts`() {
        assertTrue(WeekScoring.score(fact(0))!!.dayInSupport)
        // a mini version is a done task like any other
        assertTrue(WeekScoring.score(fact(0, tasks = 1))!!.dayInSupport)
        assertFalse(WeekScoring.score(fact(0, tasks = 0))!!.dayInSupport)
        assertFalse(WeekScoring.score(fact(0, fills = listOf(0.9f, 0.8f, 0.69f, 0.3f, 0.2f)))!!.dayInSupport)
        assertFalse(WeekScoring.score(fact(0, fills = half))!!.dayInSupport)
        // Бережно: ≥ 50 %
        assertTrue(WeekScoring.score(fact(0, fills = half, mode = DayMode.BEREZHNO))!!.dayInSupport)
        // SICK counts as rest
        assertTrue(WeekScoring.score(fact(0, fills = null, tasks = 0, sick = true))!!.dayInSupport)
        // a stored score (04:00 the next day) wins
        assertFalse(WeekScoring.score(fact(0).copy(score = DayScore(false, 2, 1)))!!.dayInSupport)
        // no data and no task: not counted, nothing subtracted
        assertNull(WeekScoring.score(fact(0, fills = null, tasks = 0)))

        val facts = listOf(fact(0), fact(1, tasks = 0), fact(2, fills = half, mode = DayMode.BEREZHNO), fact(3, fills = null, tasks = 0), fact(4))
        val view = WeekBuilder.build(input(friday.atTime(20, 0), facts))
        assertEquals(3, view.daysInSupport)
        assertEquals(4, view.mark)
        assertEquals(listOf(true, false, true, false, true, false, false), view.columns.map { it.inSupport })
        assertEquals(0.6f, view.columns[0].fill)
        assertTrue(view.columns[3].noData)
        assertTrue(view.columns[4].today)
        assertTrue(view.columns[5].future && view.columns[6].future)
    }

    @Test
    fun `W-01 the mark is 4, 3 in the first week, 2 in a week of return, and the North Star needs a path step`() {
        assertEquals(4, WeekScoring.mark(WeekKind.NORMAL))
        assertEquals(3, WeekScoring.mark(WeekKind.FIRST))
        assertEquals(2, WeekScoring.mark(WeekKind.RETURN))
        assertTrue(WeekScoring.weekInSupport(4, 1))
        assertFalse(WeekScoring.weekInSupport(4, 0))
        assertFalse(WeekScoring.weekInSupport(3, 2))
        val closedAt = monday.plusDays(1).atTime(4, 0)
        assertTrue(WeekScoring.closed(monday, closedAt))
        assertFalse(WeekScoring.closed(monday, monday.plusDays(1).atTime(3, 59)))
    }

    // ── W-02 ─────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `W-02 no text of the week has words of shame, a miss, a failure or a streak`() {
        WeekCopy.all().forEach { assertEquals(emptyList(), WeekStopWords.hits(it), "«$it»") }
        assertEquals(listOf("провал"), WeekStopWords.hits("Провал недели").distinct())
        assertTrue(WeekStopWords.hits("серия из 5 дней").isNotEmpty())
        assertTrue(WeekStopWords.hits("пропуск тренировки").isNotEmpty())
        // generated texts of a hard week too
        val facts = (0..6).map { fact(it, fills = half, tasks = 0, mode = DayMode.BEREZHNO) }
        val v = WeekBuilder.build(input(sunday.atTime(10, 0), facts))
        val generated = v.supports.flatMap { it.lines + it.label + listOfNotNull(it.skew?.text) } +
            v.goals.map { it.text } + listOfNotNull(v.goalsNote, v.shelterLine, v.patternsEmpty, v.titan.spoken)
        generated.forEach { assertEquals(emptyList(), WeekStopWords.hits(it), "«$it»") }
    }

    // ── W-03 ─────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `W-03 at most three patterns, each with six points and the label link not cause, never only-you`() {
        val all = listOf(
            Pattern("a", "A", 6, 0.3), Pattern("b", "B", 12, 0.9), Pattern("c", "C", 5, 2.0), Pattern("d", "D", 8, 0.5),
            Pattern("e", "E", 9, 0.4), Pattern("s5", "S5", 30, 3.0, onlyYou = true), Pattern("x", "X", 30, 1.5, steady = false),
        )
        val shown = PatternRules.select(all)
        assertEquals(3, shown.size)
        assertEquals(listOf("b", "d", "e"), shown.map { it.pattern.id })
        shown.forEach {
            assertTrue(it.pattern.evidenceN >= 6)
            assertEquals(WeekCopy.PATTERN_LINK, it.label)
        }
        val view = WeekBuilder.build(input(friday.atTime(9, 0), emptyList()).copy(patterns = all))
        assertEquals(3, view.patterns.size)
        assertNull(view.patternsEmpty)
        assertEquals(WeekCopy.PATTERN_EMPTY, WeekBuilder.build(input(friday.atTime(9, 0), emptyList())).patternsEmpty)
        val action = PatternRules.actionGoal(Pattern("p", "P", 7, 1.0, action = "Кофе до 14:00"))!!
        assertTrue(action.id.startsWith(WeekGoalRules.PATTERN_PREFIX))
    }

    // ── W-04 ─────────────────────────────────────────────────────────────────────────────────────

    private val skewed = SupportsWeekInput(
        balanceZ = listOf(1.5, 1.4, 1.2, 0.2, 0.1), stepDays = 1, stepDaysKnown = 5, drinkShare = 0.4, sleepDebtHours = 6.0,
        regularityPct = 50.0, nightsKnown = 5,
    )

    @Test
    fun `W-04 skews are hidden in a week of Berezhno, of return and after StormNet`() {
        val normal = WeekSupports.rows(skewed, Shelter(false, false, false), WeightInput(), 5)
        assertEquals(4, normal.count { it.skew != null })
        listOf(Shelter(true, false, false), Shelter(false, true, false), Shelter(false, false, true)).forEach { s ->
            assertTrue(WeekSupports.rows(skewed, s, WeightInput(), 5).all { it.skew == null }, "$s")
        }
        val gentle = (0..4).map { fact(it, mode = DayMode.BEREZHNO) }
        val v = WeekBuilder.build(input(friday.atTime(20, 0), gentle).copy(supports = skewed))
        assertTrue(v.supports.all { it.skew == null })
        assertEquals(WeekCopy.SKEW_HIDDEN, v.shelterLine)
        val storm = WeekBuilder.build(input(friday.atTime(20, 0), listOf(fact(0))).copy(supports = skewed, stormNetWithin7Days = true))
        assertTrue(storm.supports.all { it.skew == null })
        val back = WeekBuilder.build(input(friday.atTime(20, 0), listOf(fact(0))).copy(supports = skewed, kind = WeekKind.RETURN))
        assertTrue(back.supports.all { it.skew == null })
        assertEquals(2, back.mark)
    }

    // ── W-05 ─────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `W-05 not today returns the task to its week goal and the next days offer it again`() {
        val plan = WeekGoalRules.defaults(monday, first = false)
        val wednesday = monday.plusDays(2)
        val today = WeekGoalRules.candidates(plan, wednesday, doneToday = emptySet())
        val training = today.first { it.weekGoalId == WeekGoalRules.ID_TRAINING }
        val move = TaskRules.notToday(training, wednesday)
        assertEquals(WeekGoalRules.ID_TRAINING, move.weekGoalId)
        assertFalse(move.toTomorrow)
        // today it is gone from the day…
        val picked = TaskRules.pick(Scenario.DAY, DayMode.ROVNO, today, setOf(move.taskId))
        assertTrue(picked.none { it.id == training.id })
        // …the goal keeps what is left (nothing is lost or counted against)…
        assertEquals(3, plan.goal(WeekGoalRules.ID_TRAINING)!!.target - plan.goal(WeekGoalRules.ID_TRAINING)!!.progress)
        // …and Thursday offers it again
        val thursday = WeekGoalRules.candidates(plan, wednesday.plusDays(1), doneToday = emptySet())
        assertTrue(thursday.any { it.id == training.id })
        assertEquals(WeekGoalRules.ID_TRAINING, WeekGoalRules.goalOfTask(training.id))
        // goals of minutes and nights are counted from data, not offered as tasks
        assertTrue(today.none { it.weekGoalId == WeekGoalRules.ID_ZONES || it.weekGoalId == WeekGoalRules.ID_SLEEP })
    }

    @Test
    fun `days take the most needed goal first and a done goal stays as done`() {
        val plan = WeekPlan(
            monday,
            WeekGoalRules.defaults(monday, false).goals.map {
                when (it.id) {
                    WeekGoalRules.ID_TRAINING -> it.copy(progress = 2)
                    WeekGoalRules.ID_FOCUS -> it.copy(progress = 0)
                    else -> it
                }
            },
        )
        val c = WeekGoalRules.candidates(plan, monday.plusDays(4), doneToday = setOf(WeekGoalRules.ID_TRAINING))
        assertEquals(WeekGoalRules.ID_FOCUS, c.first().weekGoalId)
        assertTrue(c.last().done)
        assertEquals(emptyList(), WeekGoalRules.candidates(plan, monday.plusDays(4), emptySet(), paused = true))
        assertEquals(emptyList(), WeekGoalRules.candidates(plan, monday.plusDays(7), emptySet()))
    }

    // ── W-06 ─────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `W-06 without approval the week is assembled automatically 48 hours after Sunday began, with a note`() {
        val next = monday.plusDays(7)
        assertEquals(LocalDateTime.of(2026, 11, 10, 0, 0), WeekGoalRules.deadline(next))
        assertEquals(PlanStatus.PROPOSED, WeekGoalRules.status(PlanStatus.PROPOSED, next, next.atTime(23, 59)))
        assertEquals(PlanStatus.AUTO, WeekGoalRules.status(PlanStatus.PROPOSED, next, next.plusDays(1).atTime(0, 0)))
        assertEquals(PlanStatus.APPROVED, WeekGoalRules.status(PlanStatus.APPROVED, next, next.plusDays(3).atTime(9, 0)))
        // the ritual: Sunday for the next week, Monday still for this one, Tuesday no more
        assertEquals(next, WeekGoalRules.ritualFor(sunday.atTime(19, 0)))
        assertEquals(next, WeekGoalRules.ritualFor(next.atTime(22, 0)))
        assertNull(WeekGoalRules.ritualFor(next.plusDays(1).atTime(8, 0)))
        assertNull(WeekGoalRules.ritualFor(friday.atTime(19, 0)))

        val plan = WeekGoalRules.defaults(next, false)
        val tuesday = WeekBuilder.build(WeekInput(now = next.plusDays(1).atTime(8, 0), facts = emptyList(), plan = plan, planStatus = PlanStatus.PROPOSED))
        assertEquals(WeekCopy.GOAL_AUTO, tuesday.goalsNote)
        val mondayView = WeekBuilder.build(WeekInput(now = next.atTime(8, 0), facts = emptyList(), plan = plan, planStatus = PlanStatus.PROPOSED))
        assertEquals(WeekCopy.GOAL_PROPOSED, mondayView.goalsNote)
    }

    @Test
    fun `the next week adapts lighter after a hard week, offers more after an easy one, and the ritual applies taps`() {
        val next = monday.plusDays(7)
        val prev = WeekGoalRules.defaults(monday, false)
        val hard = WeekGoalRules.propose(next, prev, LastWeek(0.4, 0, true), WeekKind.NORMAL)
        assertEquals(2, hard.plan.goal(WeekGoalRules.ID_TRAINING)!!.target)
        assertEquals(120, hard.plan.goal(WeekGoalRules.ID_ZONES)!!.target)
        assertEquals(WeekCopy.GOAL_SUGGEST_DOWN, hard.note)
        val gentle = WeekGoalRules.propose(next, prev, LastWeek(0.95, 3, true), WeekKind.NORMAL)
        assertEquals(2, gentle.plan.goal(WeekGoalRules.ID_TRAINING)!!.target)
        val easy = WeekGoalRules.propose(next, prev, LastWeek(0.95, 0, true), WeekKind.NORMAL)
        assertEquals(3, easy.plan.goal(WeekGoalRules.ID_TRAINING)!!.target) // offered, not applied
        assertTrue(WeekGoalRules.ID_TRAINING in easy.suggestUp)
        val first = WeekGoalRules.propose(next, null, null, WeekKind.FIRST)
        assertEquals(WeekCopy.GOAL_FIRST_WEEK, first.note)
        assertEquals(2, first.plan.goal(WeekGoalRules.ID_TRAINING)!!.target)
        assertTrue(first.plan.goals.all { it.progress == 0 })

        val skew = Skew(SupportWeekId.SLEEP, WeekCopy.SKEW_DEBT)
        val taken = RitualRules.apply(easy, skew, RitualAnswers(skew = SkewChoice.TAKE, carry = "Отчёт по проекту", carryToGoal = true, targets = mapOf(WeekGoalRules.ID_FOCUS to 0)))
        assertNotNull(taken.goal(RitualRules.SKEW_ID_PREFIX + "sleep"))
        assertNotNull(taken.goal(RitualRules.CARRY_ID))
        assertNull(taken.goal(WeekGoalRules.ID_FOCUS))
        val easier = RitualRules.apply(easy, skew, RitualAnswers(skew = SkewChoice.EASIER))
        assertEquals(4, easier.goal(WeekGoalRules.ID_SLEEP)!!.target)
    }

    // ── W-07 ─────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `W-07 weight is seen only with an open weight goal and never with ed_care`() {
        assertFalse(WeekFlags.WEIGHT_GOALS, "D-043: the weight flag is off by default")
        assertFalse(WeightRules.visible(WeightInput(goalOpen = true)))
        assertTrue(WeightRules.visible(WeightInput(flagOn = true, goalOpen = true)))
        assertFalse(WeightRules.visible(WeightInput(flagOn = true, goalOpen = false)))
        assertFalse(WeightRules.visible(WeightInput(flagOn = true, goalOpen = true, edCare = true)))
        fun rows(w: WeightInput) = WeekSupports.rows(SupportsWeekInput(), Shelter(false, false, false), w, 3).map { it.id }
        assertTrue(SupportWeekId.WEIGHT in rows(WeightInput(flagOn = true, goalOpen = true, meanKg = 80.4)))
        assertFalse(SupportWeekId.WEIGHT in rows(WeightInput(flagOn = true, goalOpen = true, edCare = true, meanKg = 80.4)))
        assertFalse(SupportWeekId.WEIGHT in rows(WeightInput()))
    }

    // ── W-08 ─────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `W-08 the week runs Monday to Sunday of the local calendar`() {
        listOf(monday, friday, sunday).forEach { d ->
            val cols = WeekScoring.columns(d, emptyList())
            assertEquals(7, cols.size)
            assertEquals(DayOfWeek.MONDAY, cols.first().day.dayOfWeek)
            assertEquals(DayOfWeek.SUNDAY, cols.last().day.dayOfWeek)
            assertEquals(monday, cols.first().day)
        }
        // the next Monday starts a new week; Sunday still belongs to the old one
        assertEquals(monday.plusDays(7), WeekScoring.columns(monday.plusDays(7), emptyList()).first().day)
        assertEquals(45, WeekScoring.number(monday))
        // a week across the new year
        assertEquals(LocalDate.of(2026, 12, 28), CalendarWeek.start(LocalDate.of(2027, 1, 3)))
        assertEquals(listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс"), WeekScoring.columns(friday, emptyList()).map { WeekScoring.short(it.day) })
        // a fact outside the week is ignored
        val v = WeekBuilder.build(input(friday.atTime(9, 0), listOf(DayFact(monday.minusDays(1), DayMode.ROVNO, full, 1))))
        assertEquals(0, v.daysInSupport)
    }

    // ── Titan of the week (D-045) ────────────────────────────────────────────────────────────────

    private fun look(today: LocalDate, facts: List<DayFact>, ritualDone: Boolean = false, pride: Boolean = false, debt: Double? = 0.5, reg: Double? = 80.0) =
        TitanOfWeek.look(
            TitanWeekInput(
                weekStart = monday, today = today, columns = WeekScoring.columns(today, facts), supports = listOf(0.8f, 0.6f, 0.7f, 0.5f, 0.9f),
                sleepDebtHours = debt, regularityPct = reg, goalsAllDone = false, pride = pride, ritualDone = ritualDone,
            ),
        )

    @Test
    fun `the Titan of the week is assembled by rules, comes out by the data and opens on Sunday`() {
        val strong = (0..6).map { fact(it) }
        val a = look(sunday, strong)
        assertEquals(WeekPose.RISE, a.pose)
        assertEquals(6f / 7f, a.reveal)
        assertFalse(a.revealed)
        assertEquals(1f, look(sunday, strong, ritualDone = true).reveal)
        // deterministic
        assertEquals(a, look(sunday, strong))
        // A53 §3.1: carved by the day of the week only — Thursday is 4/7 with any data, or none
        assertEquals(3f / 7f, look(monday.plusDays(2), listOf(fact(0), fact(1))).reveal)
        assertEquals(1f / 7f, look(monday, emptyList()).reveal)
        val thursday = monday.plusDays(3)
        assertEquals(4f / 7f, look(thursday, emptyList()).reveal)
        assertEquals(4f / 7f, look(thursday, (0..3).map { fact(it, fills = null, tasks = 0) }).reveal)
        assertEquals(4f / 7f, look(thursday, (0..3).map { fact(it) }).reveal)
        assertEquals(6f / 7f, look(monday.plusDays(5), emptyList()).reveal)
        // a week of recovery is another figure, lit no darker than a third
        val gentle = look(sunday, (0..6).map { fact(it, fills = half, tasks = 0, mode = DayMode.BEREZHNO) })
        assertEquals(WeekPose.REST, gentle.pose)
        assertTrue(gentle.light >= 0.35f)
        assertEquals(DayMode.BEREZHNO, gentle.mode)
        // sky from sleep, detail from pride first
        assertEquals(WeekSky.CLEAR, a.sky)
        assertEquals(WeekSky.DUSK, look(sunday, strong, debt = 5.0).sky)
        assertEquals(WeekDetail.PRIDE, look(sunday, strong, pride = true).detail)
        // no two weeks are the same
        val other = TitanOfWeek.look(
            TitanWeekInput(monday.plusDays(7), sunday.plusDays(7), WeekScoring.columns(sunday.plusDays(7), strong.map { it.copy(day = it.day.plusDays(7)) }),
                listOf(0.8f, 0.6f, 0.7f, 0.5f, 0.9f), 0.5, 80.0, false),
        )
        assertNotEquals(a.seed, other.seed)
        assertEquals(listOf(a, other), TitanOfWeek.ribbon(listOf(other, a), 2026))
    }

    @Test
    fun `the share card has the image only, no numbers of health`() {
        val card = ShareCard.of(look(sunday, (0..6).map { fact(it) }, ritualDone = true))
        card.texts.forEach { t ->
            assertFalse(t.any { it.isDigit() }, "«$t»")
            assertFalse(t.contains("%"))
            assertTrue(WeekStopWords.hits(t).isEmpty())
        }
    }

    @Test
    fun `one push a week, on Sunday evening, never twice and never with a crisis`() {
        assertTrue(WeekPush.due(sunday.atTime(19, 0), null, ritualDone = false, crisis = false))
        assertFalse(WeekPush.due(sunday.atTime(19, 0), monday, ritualDone = false, crisis = false))
        assertTrue(WeekPush.due(sunday.atTime(19, 0), monday.minusDays(7), ritualDone = false, crisis = false))
        assertFalse(WeekPush.due(sunday.atTime(12, 0), null, false, false))
        assertFalse(WeekPush.due(sunday.atTime(21, 0), null, false, false))
        assertFalse(WeekPush.due(friday.atTime(19, 0), null, false, false))
        assertFalse(WeekPush.due(sunday.atTime(19, 0), null, ritualDone = true, crisis = false))
        assertFalse(WeekPush.due(sunday.atTime(19, 0), null, ritualDone = false, crisis = true))
        val pushes = (0 until 7 * 24).map { monday.atStartOfDay().plusHours(it.toLong()) }
            .fold(null as LocalDate? to 0) { (sent, n), t -> if (WeekPush.due(t, sent, false, false)) CalendarWeek.start(t.toLocalDate()) to n + 1 else sent to n }
        assertEquals(1, pushes.second)
    }

    // ── Fitness age (D-045, behind a flag) ───────────────────────────────────────────────────────

    @Test
    fun `fitness age follows the HUNT model, stays behind its flag and the gates of Agent 3`() {
        assertFalse(WeekFlags.FITNESS_AGE, "D-045: off until Agents 3 and 10")
        // PA index: 2–3 times a week, near exhaustion, 30–60 min → 2.5 × 10 × 0.75
        val act = Activity(perWeek = 3.0, meanMinutes = 45.0, hardShare = 0.3, moderateShare = 0.8)
        assertEquals(18.75, FitnessAge.paIndex(act), 1e-9)
        assertEquals(0.0, FitnessAge.paIndex(null))
        // the published equation for men (Nes 2011)
        assertEquals(100.27 - 0.296 * 40 - 0.369 * 90 - 0.155 * 55 + 0.226 * 18.75, FitnessAge.vo2(Sex.MALE, 40, 90.0, 55.0, 18.75), 1e-9)
        // the norm inverts to its own age
        assertEquals(35.0, FitnessAge.ageFor(Sex.MALE, 49.4), 1e-9)
        assertEquals(55.0, FitnessAge.ageFor(Sex.FEMALE, 33.6), 1e-9)
        val fit = FitnessInput(40, Sex.MALE, 84.0, 52.0, act)
        val unfit = FitnessInput(40, Sex.MALE, 104.0, 72.0, null)
        assertTrue(FitnessAge.estimate(fit)!! < FitnessAge.estimate(unfit)!!)
        assertNull(FitnessAge.estimate(fit.copy(waistCm = null)))

        assertNull(FitnessAge.view(FitnessGate(), fit, null, 3), "the flag is off by default")
        val on = FitnessGate(flagOn = true, optedIn = true, weeksOfData = 4)
        listOf(
            on.copy(sick = true), on.copy(pregnancy = true), on.copy(betaBlockers = true), on.copy(edCare = true), on.copy(stormNet = true),
            on.copy(healthAnxiety = true), on.copy(optedIn = false), on.copy(weeksOfData = 3),
        )
            .forEach { assertNull(FitnessAge.view(it, fit, null, 3), "$it") }
        val now = FitnessAge.estimate(fit)!!
        val v = FitnessAge.view(on, fit, now + 0.6, 3)!!
        assertEquals("за 4 недели −0,6 года", v.pace)
        assertEquals(WeekCopy.FIT_NOTE, v.note)
        assertFalse(v.waistDue)
        assertTrue(FitnessAge.view(on, fit, null, 28)!!.waistDue)
        // above the passport age: a gentle line, no scary words (Agent 10)
        val older = FitnessAge.view(on, unfit, null, 3)!!
        assertEquals(WeekCopy.FIT_ROOM, older.room)
        assertTrue(WeekStopWords.hits(older.room!!).isEmpty())
        assertEquals(WeekCopy.FIT_NEED_INPUT, FitnessAge.view(on, fit.copy(sex = null), null, null)!!.missing)
    }

    // ── Body, supports, view ─────────────────────────────────────────────────────────────────────

    @Test
    fun `body of the week shows arrows to the corridor and a red flag after three high nights`() {
        val c = Corridor(55.0, 2.0, 28)
        val calm = BodySeries(WeekCopy.B_RHR, listOf(55.0, 54.0, 56.0, null, 55.0), c)
        val high = BodySeries(WeekCopy.B_RHR, listOf(55.0, 60.0, 61.0, 59.5, 55.0), c)
        assertEquals(BodyArrow.SAME, BodyRules.arrow(calm))
        assertFalse(BodyRules.redFlag(calm))
        assertTrue(BodyRules.redFlag(high))
        assertFalse(BodyRules.redFlag(BodySeries(WeekCopy.B_RHR, listOf(60.0, 61.0, null, 60.0), c)))
        assertEquals(BodyArrow.UP, BodyRules.arrow(high))
        assertTrue(BodyRules.build(listOf(high)).redFlag)
        assertTrue(BodyRules.build(listOf(BodySeries(WeekCopy.B_HRV, listOf(null), null))).empty)
    }

    @Test
    fun `the ritual card is on Sunday and until the deadline, and the goals count`() {
        val facts = (0..6).map { fact(it) }
        val sun = WeekBuilder.build(input(sunday.atTime(10, 0), facts))
        assertTrue(sun.ritualCard)
        assertFalse(WeekBuilder.build(input(friday.atTime(10, 0), facts)).ritualCard)
        val done = WeekBuilder.build(input(sunday.atTime(10, 0), facts).copy(ritual = RitualState(doneAt = 1L)))
        assertFalse(done.ritualCard)
        assertTrue(done.ritualDone)
        assertEquals(1f, done.titan.reveal)
        val nextMonday = monday.plusDays(7)
        val mon = WeekBuilder.build(WeekInput(nextMonday.atTime(9, 0), emptyList(), WeekGoalRules.defaults(nextMonday, false), PlanStatus.PROPOSED, ritual = RitualState(pastWeekOpen = true)))
        assertTrue(mon.ritualCard)

        val plan = WeekPlan(monday, WeekGoalRules.defaults(monday, false).goals.map { it.copy(progress = if (it.measure == GoalMeasure.MINUTES) 90 else 2) })
        val v = WeekBuilder.build(input(friday.atTime(10, 0), facts, plan))
        assertEquals(6 to 12, v.goalsDone to v.goalsTotal)
        assertEquals("90 / 150", v.goals.first { it.goal.id == WeekGoalRules.ID_ZONES }.text)
        assertEquals(listOf(true, true, false), v.goals.first { it.goal.id == WeekGoalRules.ID_TRAINING }.dots)
        val ritual = RitualRules.view(v, WeekGoalRules.propose(monday.plusDays(7), plan, LastWeek(0.6, 0, true), WeekKind.NORMAL))
        assertEquals(RitualStep.FRAME, ritual.steps.first())
        assertEquals(RitualStep.FORWARD, ritual.steps.last())
    }

    // ── D-050: Sculpture, Experiment, fitness at milestones ──────────────────────────────────────

    @Test
    fun `fitness age moves only at milestones every 4 weeks, by choice, with a range and a lever`() {
        assertNull(FitnessMilestones.number(null, monday))
        assertNull(FitnessMilestones.number(monday, monday.plusWeeks(2)))
        assertEquals(1, FitnessMilestones.number(monday, monday.plusWeeks(3)))
        assertNull(FitnessMilestones.number(monday, monday.plusWeeks(4)))
        assertEquals(2, FitnessMilestones.number(monday, monday.plusWeeks(7)))
        val before = MilestoneInputs(vo2 = 40.0, pa = 5.0, waistCm = 90.0)
        assertEquals(WeekCopy.fitLever(WeekCopy.FIT_LEVER_ACTIVITY), FitnessMilestones.lever(Sex.MALE, before, MilestoneInputs(41.2, 10.0, 90.0)))
        assertEquals(WeekCopy.fitLever(WeekCopy.FIT_LEVER_RHR), FitnessMilestones.lever(Sex.MALE, before, MilestoneInputs(41.0, 5.0, 90.0)))
        assertNull(FitnessMilestones.lever(Sex.MALE, before, MilestoneInputs(39.5, 5.0, 91.0)))
        val gate = FitnessGate(flagOn = true, optedIn = true, weeksOfData = 8)
        val fit = FitnessInput(40, Sex.MALE, 84.0, 52.0, null)
        val v = FitnessAge.view(gate, fit, 38.6, 3, atMilestone = 38.0, milestone = 2, lever = WeekCopy.fitLever(WeekCopy.FIT_LEVER_RHR))!!
        assertEquals(38, v.age)
        assertEquals("36–40", v.range)
        assertEquals("веха 2", v.milestone)
        assertEquals("за 4 недели −0,6 года", v.pace)
        // the offer of the opt-in: flag on, nothing closes it, not chosen yet
        val week = WeekBuilder.build(input(friday.atTime(10, 0), emptyList()).copy(fitnessGate = FitnessGate(flagOn = true)))
        assertTrue(week.fitnessOffer)
        assertFalse(WeekBuilder.build(input(friday.atTime(10, 0), emptyList())).fitnessOffer, "flag off by default")
    }

    private val sleepSeed = ExperimentSeed("exp-screens_sleep", "Экран убран в 22:30 → сон лучше?", "Экран убран в 22:30", ExperimentMetric.SLEEP)

    @Test
    fun `the experiment of the week comes from the patterns or the lever of the Path, one at a time, never in a gentle week`() {
        val pats = listOf(
            Pattern("SCREENS_SLEEP", "В дни без экранов сон длиннее", 8, 0.5, action = "Экран убран в 22:30"),
            Pattern("SWEETS_SLEEP", "x", 9, 0.7, action = "Без сладкого вечером"),
            Pattern("STACK_HEAD", "y", 9, 0.9, action = "Утро из двух практик"),
        )
        val offers = ExperimentRules.offers(pats, null, ExperimentGate())
        assertEquals(listOf("exp-sweets_sleep", "exp-screens_sleep"), offers.map { it.id }, "only measurable seeds, strongest first")
        assertEquals(listOf("exp-screens_sleep"), ExperimentRules.offers(pats, null, ExperimentGate(edCare = true)).map { it.id }, "no food with ed_care")
        val lever = ExperimentSeed("path-lever", "Свет утром → Заряд лучше?", "Свет утром", ExperimentMetric.CHARGE, ExperimentSource.PATH)
        assertEquals("path-lever", ExperimentRules.offers(pats, lever, ExperimentGate()).first().id, "the Path lever wins (A57 §4.3)")
        assertTrue(ExperimentRules.offers(pats, lever, ExperimentGate(gentleWeek = true)).isEmpty())
        assertTrue(ExperimentRules.offers(pats, lever, ExperimentGate(stormNet = true)).isEmpty())

        val did = setOf(monday, monday.plusDays(1), monday.plusDays(3))
        val scale = ExperimentRules.scale(monday, monday.plusDays(4), did)
        assertEquals(3, scale.done)
        assertEquals("Эксперимент · 3 из 5 ›", scale.bridge)
        assertEquals(listOf(true, true, false, true, null, null), scale.days)

        val exp = Experiment(monday, sleepSeed, ExperimentStatus.TAKEN)
        val v = WeekBuilder.build(input(friday.atTime(10, 0), (0..3).map { fact(it) }).copy(experiment = exp, experimentDays = did + friday))
        assertNotNull(v.experiment)
        assertTrue(v.experiment!!.doneToday)
        val gentle = WeekBuilder.build(input(friday.atTime(10, 0), (0..4).map { fact(it, mode = DayMode.BEREZHNO) }).copy(experiment = exp))
        assertNull(gentle.experiment)
        assertEquals(WeekCopy.EXP_NONE, gentle.experimentNote)
    }

    @Test
    fun `every outcome of the experiment is something learned`() {
        fun pts(did: List<Double?>, without: List<Double?>) =
            did.mapIndexed { i, v -> ExperimentPoint(monday.plusDays(i.toLong()), true, v) } +
                without.mapIndexed { i, v -> ExperimentPoint(monday.minusDays(i + 1L), false, v) }
        val yes = ExperimentRules.result(sleepSeed, pts(listOf(440.0, 450.0, 430.0, 445.0, 460.0, 400.0), listOf(420.0, 425.0, 415.0)))
        assertEquals(ExperimentOutcome.YES, yes.outcome)
        assertEquals("Похоже, да: +18 мин, 5 дн. из 6.", yes.text)
        val few = ExperimentRules.result(sleepSeed, pts(listOf(440.0, 450.0, null, null), listOf(420.0, 425.0)))
        assertEquals(ExperimentOutcome.TOO_FEW, few.outcome)
        assertEquals(WeekCopy.EXP_TOO_FEW, few.text)
        val flat = ExperimentRules.result(sleepSeed, pts(listOf(420.0, 421.0, 419.0, 422.0, 420.0), listOf(420.0, 421.0)))
        assertEquals(ExperimentOutcome.NO_LINK, flat.outcome)
        val opposite = ExperimentRules.result(sleepSeed, pts(listOf(380.0, 390.0, 385.0, 380.0, 390.0), listOf(420.0, 421.0)))
        assertEquals(ExperimentOutcome.OPPOSITE, opposite.outcome)
        listOf(yes, few, flat, opposite).forEach {
            assertEquals(WeekCopy.EXP_LEARNED, it.headline)
            assertTrue(WeekStopWords.hits(it.text).isEmpty(), it.text)
            assertFalse(it.text.contains("не получилось"))
        }
        // the ritual shows the answer as its own step after the Sculpture
        val v = WeekBuilder.build(input(sunday.atTime(19, 0), (0..6).map { fact(it) }))
        val r = RitualRules.view(v, WeekGoalRules.propose(monday.plusDays(7), null, null, WeekKind.NORMAL), yes, listOf(sleepSeed))
        assertEquals(listOf(RitualStep.FRAME, RitualStep.EXPERIMENT), r.steps.take(2))
    }

    @Test
    fun `the Sculpture of the week has a texture from sleep and a pose caption on the first two Sundays`() {
        assertEquals(WeekTexture.POLISHED, TitanOfWeek.texture(0.5, 80.0))
        assertEquals(WeekTexture.MATTE, TitanOfWeek.texture(3.0, 80.0))
        assertEquals(WeekTexture.MATTE, TitanOfWeek.texture(null, null))
        val open = look(sunday, (0..6).map { fact(it) }, ritualDone = true)
        assertNotNull(TitanOfWeek.poseCaption(open, 0))
        assertNotNull(TitanOfWeek.poseCaption(open, 1))
        assertNull(TitanOfWeek.poseCaption(open, 2))
        assertNull(TitanOfWeek.poseCaption(look(sunday, (0..6).map { fact(it) }), 0), "not before the reveal")
        assertEquals(WeekCopy.TITAN_READY, WeekCopy.PUSH_TITLE)
    }
}
