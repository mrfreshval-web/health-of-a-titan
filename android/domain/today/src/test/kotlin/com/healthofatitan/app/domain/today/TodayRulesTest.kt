package com.healthofatitan.app.domain.today

import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A46 v0.3 §6: the rules T-01…T-13 that hold without a screen. */
class TodayRulesTest {

    private val day = LocalDate.of(2026, 11, 7) // a Saturday
    private fun at(h: Int, m: Int = 0) = day.atTime(h, m)

    private fun facts(s: Scenario, now: LocalDateTime = at(12, 40)): DayFacts = when (s) {
        Scenario.NO_SOURCE -> DayFacts(now, whoopConnected = false, nightReady = false, mode = DayMode.ROVNO)
        Scenario.SELF -> DayFacts(now, whoopConnected = false, nightReady = false, mode = DayMode.ROVNO, healthConnect = true)
        Scenario.NIGHT_PENDING -> DayFacts(now, whoopConnected = true, nightReady = false, mode = null)
        Scenario.AFTER_DAWN -> DayFacts(at(8, 30), whoopConnected = true, nightReady = true, mode = DayMode.ROVNO, dawnDone = true)
        Scenario.DAY -> DayFacts(now, whoopConnected = true, nightReady = true, mode = DayMode.ROVNO, dawnDone = true)
        Scenario.AFTER_WORKOUT -> DayFacts(now, whoopConnected = true, nightReady = true, mode = DayMode.ROVNO, workoutConfirmed = true)
        Scenario.EVENING -> DayFacts(at(20, 30), whoopConnected = true, nightReady = true, mode = DayMode.ROVNO)
        Scenario.GENTLE -> DayFacts(now, whoopConnected = true, nightReady = true, mode = DayMode.BEREZHNO)
        Scenario.SAFETY -> DayFacts(now, whoopConnected = true, nightReady = true, mode = DayMode.ROVNO, safety = Safety.SAF_01)
        Scenario.RETURN -> DayFacts(now, whoopConnected = true, nightReady = true, mode = DayMode.ROVNO, returning = true)
    }

    @Test
    fun `every scenario of section 4 is reachable`() {
        Scenario.entries.forEach { assertEquals(it, Scenarios.of(facts(it)), it.id) }
        assertEquals(Scenario.SAFETY, Scenarios.of(facts(Scenario.GENTLE).copy(safety = Safety.CRISIS)))
        assertEquals(Scenario.SAFETY, Scenarios.of(facts(Scenario.NO_SOURCE).copy(safety = Safety.CARE)))
    }

    // ---------------------------------------------------------------- S2: a day without WHOOP

    private val week = listOf(
        DayTask("training", TaskKind.TRAINING, "Силовая · 45 мин", weekGoalId = "body-training", hasMini = true, route = "training"),
        DayTask("focus", TaskKind.FOCUS, "Фокус-блоки", weekGoalId = "work-focus", hasMini = true),
        DayTask("wg:sleep", TaskKind.REMINDER, "Отбой вовремя", weekGoalId = "sleep"),
    )

    @Test
    fun `S2 without WHOOP the scenario follows Health Connect and self-report`() {
        val bare = DayFacts(at(10), whoopConnected = false, nightReady = false, mode = DayMode.ROVNO)
        assertEquals(Scenario.NO_SOURCE, Scenarios.of(bare))
        assertEquals(Scenario.SELF, Scenarios.of(bare.copy(healthConnect = true)), "Health Connect only")
        assertEquals(Scenario.SELF, Scenarios.of(bare.copy(selfReported = true)), "the wake deck only")
        assertEquals(Scenario.SELF, Scenarios.of(bare.copy(healthConnect = true, now = at(21))), "the evening stays 4.1a")
        assertEquals(Scenario.GENTLE, Scenarios.of(bare.copy(mode = DayMode.BEREZHNO, healthConnect = true)))
        assertEquals(Scenario.RETURN, Scenarios.of(bare.copy(returning = true)))
        assertEquals(DayLight.DARK, Scenarios.light(Scenario.NO_SOURCE, at(10)))
        assertEquals(DayLight.MORNING, Scenarios.light(Scenario.SELF, at(10)))
        // help still wins over a day without WHOOP
        listOf(Safety.SAF_01, Safety.CRISIS, Safety.CARE).forEach { safety ->
            assertEquals(Scenario.SAFETY, Scenarios.of(bare.copy(safety = safety)))
            assertEquals(Scenario.SAFETY, Scenarios.of(bare.copy(safety = safety, healthConnect = true, selfReported = true)))
        }
        assertEquals(listOf(TaskKind.HELP, TaskKind.DOCTOR), TaskRules.pick(Scenario.SAFETY, DayMode.ROVNO, week, emptySet()).map { it.kind })
    }

    @Test
    fun `S2 without WHOOP the week's tasks come, a workout among them, and connecting is one of them`() {
        listOf(Scenario.NO_SOURCE, Scenario.SELF).forEach { s ->
            val tasks = TaskRules.pick(s, DayMode.ROVNO, week, emptySet())
            assertTrue(tasks.size in 2..TaskRules.MAX, "$s: $tasks")
            assertTrue(tasks.any { it.weekGoalId != null }, "$s: at least one task of the week")
            assertEquals(TaskKind.TRAINING, tasks.first().kind, "$s: the workout is there and plays")
            assertTrue(TaskRules.playable(tasks, 0))
            assertEquals(TaskKind.CONNECT_WHOOP, tasks.last().kind, "$s: connecting is the last task")
            assertEquals(1, tasks.count { it.kind == TaskKind.CONNECT_WHOOP })
        }
        assertEquals(TodayCopy.TASK_CONNECT_SOURCE, TaskRules.pick(Scenario.NO_SOURCE, DayMode.ROVNO, week, emptySet()).last().title)
        assertEquals(TodayCopy.TASK_CONNECT_WHOOP, TaskRules.pick(Scenario.SELF, DayMode.ROVNO, week, emptySet(), healthConnect = true).last().title)
        // nothing from the week yet: «Собрать неделю» first, connecting after it
        assertEquals(
            listOf(TaskKind.ASSEMBLE_WEEK, TaskKind.CONNECT_WHOOP),
            TaskRules.pick(Scenario.NO_SOURCE, null, emptyList(), emptySet()).map { it.kind },
        )
        // with WHOOP there is no connecting task
        assertTrue(TaskRules.pick(Scenario.DAY, DayMode.ROVNO, week, emptySet()).none { it.kind == TaskKind.CONNECT_WHOOP })
    }

    @Test
    fun `S2 without WHOOP the day can be closed in the evening and the windows are an estimate`() {
        listOf(Scenario.NO_SOURCE, Scenario.SELF).forEach { s ->
            val morning = RhythmRules.build(RhythmInput(at(9), s, sleep = SleepPrep(bedtimeMin = 23 * 60), estimate = true))
            assertEquals(listOf(Ritual.DAWN, Ritual.CLOSE_DAY), morning.dots.map { it.ritual })
            assertTrue(morning.dots.first().active, "$s: the wake deck is the self-report")
            assertEquals(4, morning.windows.size)
            assertTrue(morning.estimate)
            val evening = RhythmRules.build(RhythmInput(at(20, 30), s, sleep = SleepPrep(bedtimeMin = 23 * 60), estimate = true))
            assertTrue(evening.dots.first { it.ritual == Ritual.CLOSE_DAY }.active, "$s: «Закрыть день» in the evening")
        }
        assertFalse(RhythmRules.build(RhythmInput(at(9), Scenario.SELF, estimate = true)).estimate, "no windows, no mark")
    }

    @Test
    fun `S2 without WHOOP the mode is never above Rovno and is marked as an estimate`() {
        DayMode.entries.forEach { m ->
            val me = MeRules.build(MeInput(Scenario.SELF, m, null, Safety.NONE, 70, 420, 460, 4, 0, whoopConnected = false))
            assertNotNull(me.mode)
            assertTrue(me.mode != DayMode.RAZGON, "$m → ${me.mode}")
            assertTrue(me.estimate)
            assertNull(me.charge, "Заряд is WHOOP's")
            assertNull(me.night)
            assertNull(me.calibration, "no «Учусь тебя» without WHOOP")
            assertEquals(4, me.self)
        }
        assertEquals(DayMode.BEREZHNO, MeRules.build(MeInput(Scenario.GENTLE, DayMode.BEREZHNO, null, Safety.NONE, null, null, null, 2, 0, whoopConnected = false)).mode)
        val unknown = MeRules.build(MeInput(Scenario.NO_SOURCE, DayMode.ROVNO, null, Safety.NONE, null, null, null, null, 0, whoopConnected = false))
        assertNull(unknown.mode)
        assertFalse(unknown.estimate)
        assertEquals(TodayCopy.LEAD_NO_SOURCE, unknown.lead)
        val sober = MeRules.build(MeInput(Scenario.SAFETY, DayMode.ROVNO, null, Safety.CRISIS, null, null, null, 3, 0, whoopConnected = false))
        assertNull(sober.mode)
        assertFalse(sober.showTitan)
        assertFalse(MeRules.build(MeInput(Scenario.DAY, DayMode.RAZGON, null, Safety.NONE, 70, 408, 460, 4, 28)).estimate)
    }

    @Test
    fun `T-01 the order of blocks is the same in every scenario and at any hour`() {
        Scenario.entries.forEach { s ->
            (0..23).forEach { h ->
                assertEquals(listOf("block_me", "block_supports", "block_tasks", "block_rhythm"), TodayLayout.blocks(s, at(h)).map { it.tag })
            }
        }
    }

    private fun task(id: String, done: Boolean = false, mini: Boolean = true) =
        DayTask(id, TaskKind.FOCUS, "Дело $id", weekGoalId = "g-$id", hasMini = mini, done = done)

    @Test
    fun `T-02 one to three tasks, two in Berezhno, only the first plays`() {
        val many = (1..6).map { task("$it") }
        Scenario.entries.forEach { s ->
            listOf(emptyList(), many.take(1), many).forEach { c ->
                val mode = facts(s).mode
                val tasks = TaskRules.pick(s, mode, c, emptySet())
                assertTrue(tasks.size in 1..3, "$s: ${tasks.size}")
                if (s == Scenario.GENTLE) assertTrue(tasks.size <= 2)
                tasks.indices.forEach { i -> if (i > 0) assertFalse(TaskRules.playable(tasks, i)) }
            }
        }
        val gentle = TaskRules.pick(Scenario.GENTLE, DayMode.BEREZHNO, many, emptySet())
        assertTrue(gentle.all { it.mini }, "Бережно: mini by default")
        assertEquals(TaskKind.START_SMALL, TaskRules.pick(Scenario.RETURN, DayMode.ROVNO, many, emptySet()).single().kind)
        assertEquals(TaskKind.CONNECT_WHOOP, TaskRules.pick(Scenario.NO_SOURCE, null, many, emptySet()).last().kind)
        assertEquals(TaskKind.START_QUESTIONS, TaskRules.pick(Scenario.NIGHT_PENDING, null, many, emptySet()).first().kind)
        assertEquals(listOf(TaskKind.HELP, TaskKind.DOCTOR), TaskRules.pick(Scenario.SAFETY, DayMode.ROVNO, many, emptySet()).map { it.kind })
        assertTrue(TaskRules.playable(TaskRules.pick(Scenario.DAY, DayMode.ROVNO, many, emptySet()), 0))
    }

    @Test
    fun `open tasks come before done ones`() {
        val tasks = TaskRules.pick(Scenario.DAY, DayMode.ROVNO, listOf(task("a", done = true), task("b")), emptySet())
        assertEquals(listOf("b", "a"), tasks.map { it.id })
        val allDone = TaskRules.pick(Scenario.DAY, DayMode.ROVNO, listOf(task("a", done = true)), emptySet())
        assertFalse(TaskRules.playable(allDone, 0))
    }

    @Test
    fun `T-03 a task title is on the list once`() {
        val tasks = TaskRules.pick(Scenario.DAY, DayMode.ROVNO, listOf(task("a"), task("a").copy(id = "a2"), task("b")), emptySet())
        assertEquals(tasks.map { it.title }.distinct(), tasks.map { it.title })
    }

    @Test
    fun `T-08 not today returns the task to its week goal, never to tomorrow`() {
        val t = task("a")
        val move = TaskRules.notToday(t, day)
        assertEquals("g-a", move.weekGoalId)
        assertFalse(move.toTomorrow)
        val today = TaskRules.pick(Scenario.DAY, DayMode.ROVNO, listOf(t, task("b")), setOf(move.taskId))
        assertEquals(listOf("b"), today.map { it.id })
        // tomorrow the week offers what it has; the returned task is not put first by itself
        val tomorrow = TaskRules.pick(Scenario.DAY, DayMode.ROVNO, listOf(task("b"), t), emptySet())
        assertEquals("b", tomorrow.first().id)
        assertFalse(TaskRules.movable(DayTask("help", TaskKind.HELP, "Помощь")))
    }

    @Test
    fun `T-04 supports have no alarm tone and T-11 every support has a source`() {
        assertEquals(setOf("CALM", "LIT", "NOTE"), SupportTone.entries.map { it.name }.toSet())
        Scenario.entries.forEach { s ->
            val views = Supports.build(SupportsInput(mode = DayMode.ROVNO, scenario = s, nowMin = 21 * 60 + 30, strain = 19.0, kilojoule = 9000.0, whoopSteps = 2000, hourlySteps = List(24) { 300 }, mealsMarked = 1, water = 2))
            assertEquals(SupportId.entries, views.map { it.id })
            views.forEach { v ->
                assertTrue(v.source.isNotBlank(), "${v.id} has a source")
                assertTrue(v.fill in 0f..1f)
            }
        }
    }

    @Test
    fun `effort over its corridor is a quiet note, effort is hidden in safety`() {
        val over = Supports.build(SupportsInput(DayMode.BEREZHNO, Scenario.GENTLE, 600, strain = 12.0)).first()
        assertEquals(SupportTone.NOTE, over.tone)
        assertEquals("6–10", over.caption)
        val hidden = Supports.build(SupportsInput(DayMode.ROVNO, Scenario.SAFETY, 600, strain = 12.0)).first()
        assertTrue(hidden.pending)
        assertEquals("—", hidden.value)
        assertEquals(TodayCopy.EFFORT_HIDDEN, hidden.source)
    }

    @Test
    fun `movement counts hours with at least 250 steps`() {
        val hourly = List(24) { if (it in 8..14) 400 else 100 }
        assertEquals(7, Supports.movingHours(hourly))
        val m = Supports.build(SupportsInput(DayMode.ROVNO, Scenario.DAY, 900, whoopSteps = 5200, hourlySteps = hourly))[1]
        assertEquals("5 200", m.value)
        assertEquals("7/12 ч", m.caption)
        assertEquals(9_000, SupportNorms.stepsGoal(40))
        assertEquals(7_000, SupportNorms.stepsGoal(65))
    }

    @Test
    fun `water norm is an estimate between 6 and 14 glasses`() {
        assertEquals(8, SupportNorms.waterGlasses(null, null))
        assertEquals(10, SupportNorms.waterGlasses(null, 15.0))
        assertEquals(10, SupportNorms.waterGlasses(80.0, null))
        assertEquals(14, SupportNorms.waterGlasses(140.0, 20.0))
        assertTrue(Supports.build(SupportsInput(DayMode.ROVNO, Scenario.DAY, 600, water = 3))[3].estimate)
    }

    @Test
    fun `sleep preparation counts the windows that passed`() {
        val prep = SleepPrep(bedtimeMin = 23 * 60 + 5)
        assertEquals(0, prep.held(10 * 60, null, null))
        assertEquals(1, prep.held(15 * 60, null, null))
        assertEquals(4, prep.held(22 * 60 + 10, 18 * 60, 20 * 60))
        assertEquals(2, prep.held(22 * 60 + 10, 20 * 60 + 30, 21 * 60 + 30))
    }

    @Test
    fun `T-07 the haiku stays all day and goes with SAF-01, crisis and CARE`() {
        val haiku = listOf("Тихий пульс утра —", "сила не в спешке,", "а в ровном шаге.")
        (5..23).forEach { h ->
            val s = Scenarios.of(facts(Scenario.DAY, at(h)))
            assertNotNull(MeRules.build(MeInput(s, DayMode.ROVNO, haiku, Safety.NONE, 44, 408, 460, 3, 28)).haiku, "hour $h")
        }
        listOf(Safety.SAF_01, Safety.CRISIS, Safety.CARE).forEach { safety ->
            val me = MeRules.build(MeInput(Scenario.SAFETY, DayMode.ROVNO, haiku, safety, 44, 408, 460, 3, 28))
            assertNull(me.haiku)
            assertFalse(me.showTitan)
            assertFalse(MeRules.haikuVisible(safety))
        }
    }

    @Test
    fun `calibration until 14 nights caps the mode at Rovno`() {
        val me = MeRules.build(MeInput(Scenario.DAY, DayMode.RAZGON, null, Safety.NONE, 70, 400, 460, 4, 9))
        assertEquals("Учусь тебя: 9 из 28", me.calibration)
        assertEquals(DayMode.ROVNO, me.mode)
        assertNull(me.charge)
        val ready = MeRules.build(MeInput(Scenario.DAY, DayMode.RAZGON, null, Safety.NONE, 70, 408, 460, 4, 28))
        assertNull(ready.calibration)
        assertEquals(DayMode.RAZGON, ready.mode)
        assertEquals("6:48", ready.night)
        assertEquals("7:40", ready.need)
    }

    @Test
    fun `T-05 only Rassvet once a day and CrisisGuard 2 open by themselves`() {
        assertNull(AutoOpen.reason(crisisLevel = 1, rassvet = null))
        assertEquals(AutoOpenReason.CRISIS, AutoOpen.reason(2, RassvetStep.DECK))
        assertEquals(AutoOpenReason.RASSVET, AutoOpen.reason(0, RassvetStep.DAWN))
        val now = at(7, 10)
        assertEquals(RassvetStep.DECK, Rassvet.trigger(now, nightReady = true, offeredToday = false, deckDone = false, revealPending = false, dawnShown = false))
        assertEquals(RassvetStep.REVEAL, Rassvet.trigger(now, true, false, deckDone = true, revealPending = true, dawnShown = false))
        assertEquals(RassvetStep.DAWN, Rassvet.trigger(now, true, false, deckDone = true, revealPending = false, dawnShown = false))
        assertNull(Rassvet.trigger(now, true, offeredToday = true, deckDone = false, revealPending = false, dawnShown = false), "once a day")
        assertNull(Rassvet.trigger(now, nightReady = false, offeredToday = false, deckDone = false, revealPending = false, dawnShown = false), "after the night's data")
        assertNull(Rassvet.trigger(at(4, 50), true, false, false, false, false))
        assertNull(Rassvet.trigger(now, true, false, deckDone = true, revealPending = false, dawnShown = true))
    }

    @Test
    fun `rhythm keeps the default rituals of D-043 and lights the breather in Berezhno`() {
        val r = RhythmRules.build(RhythmInput(at(12), Scenario.DAY, dawnDone = true, sleep = SleepPrep(bedtimeMin = 23 * 60 + 5)))
        assertEquals(listOf(Ritual.DAWN, Ritual.CLOSE_DAY), r.dots.map { it.ritual })
        assertTrue(r.dots.first().done)
        assertEquals("кофе до 14:00 · ужин до 21:00 · экраны с 22:00 · отбой 23:05", r.windows.joinToString(" · ") { it.text })
        assertFalse(r.breatherLit)
        assertTrue(RhythmRules.build(RhythmInput(at(12), Scenario.GENTLE)).breatherLit)
        val evening = RhythmRules.build(RhythmInput(at(20), Scenario.EVENING, settings = RitualSettings(transition = true, landing = true)))
        assertEquals(listOf(Ritual.DAWN, Ritual.TRANSITION, Ritual.CLOSE_DAY, Ritual.LANDING), evening.dots.map { it.ritual })
        assertTrue(evening.dots.first { it.ritual == Ritual.CLOSE_DAY }.active)
        assertEquals(Ritual.CLOSE_DAY, RitualSettings().workTodayIn)
        assertEquals(Ritual.TRANSITION, RitualSettings(transition = true).workTodayIn)
        assertEquals(listOf(Ritual.DAWN, Ritual.CLOSE_DAY), RhythmRules.build(RhythmInput(at(9), Scenario.NO_SOURCE)).dots.map { it.ritual })
        val sunday = LocalDate.of(2026, 11, 8)
        assertEquals(DayOfWeek.SUNDAY, sunday.dayOfWeek)
        assertTrue(Ritual.WEEK_ASSEMBLY in RitualSettings().enabled(sunday))
    }

    @Test
    fun `T-11 every why section and metric has a source, missing values stay missing`() {
        val empty = WhyBuilder.build(WhyInput())
        assertEquals(WhySectionId.entries, empty.map { it.id })
        empty.flatMap { it.metrics }.forEach { assertTrue(it.source.isNotBlank()) }
        assertTrue(empty.first { it.id == WhySectionId.SLEEP }.metrics.all { it.value == null })
    }

    @Test
    fun `three values outside the corridor bring the gentle sentence, without a diagnosis`() {
        val c = Corridor(55.0, 3.0, 30)
        val why = WhyBuilder.build(
            WhyInput(
                restingHr = 62.0, restingHrCorridor = c,
                hrv = 30.0, hrvCorridor = Corridor(50.0, 8.0, 30),
                respiratoryRate = 17.5, respiratoryCorridor = Corridor(15.0, 0.6, 30),
                sleepMin = 408, needMin = 460, self = 2, dataLevel = 4,
            ),
        )
        val dev = why.first { it.id == WhySectionId.DEVIATIONS }
        assertEquals(3, dev.metrics.size)
        assertTrue(dev.text!!.startsWith(TodayCopy.WHY_MANY_DEVIATIONS))
        assertEquals(TodayCopy.WHY_YOU_SCISSORS, why.last().text)
        assertEquals(3, why.first().metrics.size)
        assertEquals(Arrow.UP, dev.metrics.first { it.name == TodayCopy.M_RHR }.arrow)
    }

    @Test
    fun `T-13 every text passes the A39 stop dictionary`() {
        val generated = Scenario.entries.flatMap { s ->
            Supports.build(SupportsInput(DayMode.ROVNO, s, 900, strain = 8.0, whoopSteps = 4000)).flatMap { listOf(it.spoken, it.source, it.label) } +
                TaskRules.pick(s, DayMode.ROVNO, emptyList(), emptySet()).flatMap { listOfNotNull(it.title, it.why) }
        } + WhyBuilder.build(WhyInput(self = 2, dataLevel = 4)).mapNotNull { it.text }
        (TodayCopy.all() + generated).forEach { text -> assertEquals(emptyList(), StopWords.hits(text), "«$text»") }
        assertEquals(listOf("провал"), StopWords.hits("Провал недели"))
        assertEquals(listOf("пропустил"), StopWords.hits("ты пропустил"))
    }

    @Test
    fun `the week is a calendar week and a day in support needs a task and three supports`() {
        assertEquals(LocalDate.of(2026, 11, 2), CalendarWeek.start(day))
        assertEquals(LocalDate.of(2026, 11, 8), CalendarWeek.end(day))
        assertEquals(LocalDate.of(2026, 11, 2), CalendarWeek.start(LocalDate.of(2026, 11, 2)))
        WeekPlan.empty(day)
        assertTrue(DayScores.of(listOf(0.8f, 0.7f, 0.9f, 0.1f, 0f), tasksDone = 1, gentle = false).dayInSupport)
        assertFalse(DayScores.of(listOf(0.8f, 0.7f, 0.9f, 0.1f, 0f), tasksDone = 0, gentle = false).dayInSupport)
        assertFalse(DayScores.of(listOf(0.6f, 0.6f, 0.6f, 0f, 0f), 1, gentle = false).dayInSupport)
        assertTrue(DayScores.of(listOf(0.6f, 0.6f, 0.6f, 0f, 0f), 1, gentle = true).dayInSupport)
        assertTrue(DayScores.of(emptyList(), 0, gentle = false, sick = true).dayInSupport)
    }
}
