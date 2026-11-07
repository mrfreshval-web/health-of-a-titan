package com.healthofatitan.app.domain.training

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkoutPlayerTest {

    private val lib = Content.library
    private val engine = TrainingEngine(lib)
    private val defaults = lib.rules.player

    private fun session(r: TrainingRequest) = assertIs<TrainingDecision.Planned>(engine.plan(r)).session

    private val functional = session(TrainingRequest(DayMode.BOOST, DayTrainingKind.STRENGTH))
    private val steps = WorkoutSteps.of(functional, defaults)

    @Test
    fun `a circuit goes round by round with rest between rounds and one RPE question`() {
        val main = functional.blocks.indexOfFirst { it.type == BlockType.MAIN }
        val work = steps.filterIsInstance<WorkoutStep.Work>().filter { it.blockIndex == main }
        // 5 exercises × 3 rounds, the lunge on both sides
        assertEquals(3 * 6, work.size)
        val rests = steps.filterIsInstance<WorkoutStep.Rest>().filter { it.blockIndex == main && it.kind == RestKind.ROUND }
        assertEquals(2, rests.size)
        assertEquals(60, rests.first().seconds)
        val asks = steps.filterIsInstance<WorkoutStep.Work>().mapNotNull { it.askRpe }
        assertEquals(1, asks.size, "one question for the circuit, none for mobility")
        assertEquals(3, work.last().round)
        assertEquals(3, work.last().rounds)
    }

    @Test
    fun `per-side items switch sides with a short pause`() {
        val lunge = steps.indexOfFirst { it is WorkoutStep.Work && it.exercise.id == "ex-reverse-lunge" }
        val first = assertIs<WorkoutStep.Work>(steps[lunge])
        assertEquals(Side.FIRST, first.side)
        val switch = assertIs<WorkoutStep.Rest>(steps[lunge + 1])
        assertEquals(RestKind.SIDE_SWITCH, switch.kind)
        assertEquals(defaults.perSideSwitchS, switch.seconds)
        assertEquals(Side.SECOND, assertIs<WorkoutStep.Work>(steps[lunge + 2]).side)
    }

    @Test
    fun `no block ends with a rest and the session ends with the cool-down`() {
        functional.blocks.indices.forEach { bi -> assertTrue(steps.last { it.blockIndex == bi } is WorkoutStep.Work) }
        val last = assertIs<WorkoutStep.Work>(steps.last())
        assertEquals(BlockType.COOLDOWN, last.blockType)
    }

    @Test
    fun `supersets alternate the pair and rest after it`() {
        val s = session(TrainingRequest(DayMode.BOOST, DayTrainingKind.STRENGTH, equipment = TrainingPlace.HOME.equipment + "dumbbells"))
        val st = WorkoutSteps.of(s, defaults)
        val main = s.blocks.indexOfFirst { it.type == BlockType.MAIN }
        val ids = st.filter { it.blockIndex == main }.map { if (it is WorkoutStep.Work) it.exercise.id else "rest${(it as WorkoutStep.Rest).seconds}" }
        assertEquals(listOf("ex-goblet-squat", "ex-db-row", "rest5", "ex-db-row", "rest75", "ex-goblet-squat"), ids.take(6).map { if (it.startsWith("rest")) it else it })
        assertEquals(2, st.filterIsInstance<WorkoutStep.Work>().filter { it.blockIndex == main }.mapNotNull { it.askRpe }.size)
    }

    private fun run(player: WorkoutPlayer, from: PlayerState, at: Long, until: Long, stepMs: Long = 100): Pair<PlayerState, List<PlayerCue>> {
        var s = from
        val cues = mutableListOf<PlayerCue>()
        var t = at
        while (t <= until) {
            val tr = player.tick(t, s)
            s = tr.state
            cues += tr.cues
            t += stepMs
        }
        return s to cues
    }

    @Test
    fun `countdown, timed set, last seconds and the end of a rest`() {
        val stretch = lib.exercise("ex-stretch-calf")!!
        val item = SessionItem(stretch, 1, null, 10, null, false, 0, null, null, null, null, null)
        val work = WorkoutStep.Work(0, BlockType.COOLDOWN, item, 1, 1, 1, 1, null, null, 10, null)
        val player = WorkoutPlayer(listOf(work, WorkoutStep.Rest(0, BlockType.COOLDOWN, 5, RestKind.REST), work), countdownS = 3)
        val start = player.start(1_000)
        assertEquals(PlayerPhase.COUNTDOWN, start.state.phase)
        val (afterCount, c1) = run(player, start.state, 1_000, 4_000)
        assertEquals(3, c1.count { it == PlayerCue.COUNTDOWN_TICK })
        assertEquals(1, c1.count { it == PlayerCue.SET_START })
        assertEquals(PlayerPhase.WORK, afterCount.phase)
        val (afterWork, c2) = run(player, afterCount, 4_100, 14_000)
        assertEquals(3, c2.count { it == PlayerCue.LAST_SECONDS_TICK })
        assertEquals(PlayerPhase.REST, afterWork.phase)
        assertEquals(10, afterWork.log.single().seconds)
        val (afterRest, c3) = run(player, afterWork, 14_100, 19_100)
        assertEquals(1, c3.count { it == PlayerCue.REST_END })
        assertEquals(0, c3.count { it == PlayerCue.SET_START }, "the end of the rest is the start signal")
        assertEquals(PlayerPhase.WORK, afterRest.phase, "after a rest the set starts without a countdown")
    }

    @Test
    fun `open sets wait for done, RPE is asked once and answered with one tap`() {
        val player = WorkoutPlayer(steps)
        var s = player.start(0).state
        var now = 0L
        var guard = 0
        while (!s.done && guard++ < 1_000) {
            now += 1_000
            s = when (s.phase) {
                PlayerPhase.WORK -> player.done(now, s).state
                PlayerPhase.COUNTDOWN, PlayerPhase.REST -> player.done(now, s).state
                else -> s
            }
            if (s.pendingRpe != null) s = player.answerRpe(s, 7).state
        }
        assertTrue(s.done)
        assertEquals(steps.count { it is WorkoutStep.Work }, s.log.size)
        assertEquals(mapOf("b1-circuit" to 7), s.rpe)
        val summary = WorkoutSummary.of(player, s, now)
        assertEquals(s.log.size, summary.setsDone)
        assertEquals(7, summary.rpeMedian)
        assertTrue(summary.stretchDone)
    }

    @Test
    fun `rest can be extended, skipped and paused`() {
        val player = WorkoutPlayer(steps)
        val rest = steps.indexOfFirst { it is WorkoutStep.Rest && it.kind == RestKind.ROUND }
        var s = PlayerState(index = rest, phase = PlayerPhase.REST, phaseStartedAt = 0, endsAt = 60_000, startedAt = 0)
        s = player.extendRest(s).state
        assertEquals(75_000, s.endsAt)
        s = player.pause(10_000, s).state
        assertEquals(65_000, s.remainingMs(40_000), "time stands still in a pause")
        s = player.resume(40_000, s).state
        assertEquals(105_000, s.endsAt)
        assertEquals(30_000, s.pausedMs)
        assertEquals(PlayerPhase.REST, player.tick(41_000, s).state.phase)
        val ready = player.done(41_000, s).state
        assertEquals(rest + 1, ready.index)
    }

    @Test
    fun `feel bad stops everything at once`() {
        val player = WorkoutPlayer(steps)
        val s = player.start(0).state
        val t = player.feelBad(500, s)
        assertTrue(t.state.paused)
        assertTrue(t.state.feltBad)
        assertEquals(listOf(PlayerCue.FEEL_BAD), t.cues)
        assertEquals(t.state, player.tick(60_000, t.state).state, "nothing moves while paused")
        assertEquals(t.state, player.done(60_000, t.state).state)
        val end = player.finish(70_000, t.state)
        assertTrue(end.state.done)
        assertTrue(WorkoutSummary.of(player, end.state, 70_000).feltBad)
    }

    @Test
    fun `skipping is allowed and logged without reproach`() {
        val player = WorkoutPlayer(steps)
        var s = player.start(0).state
        s = player.skip(100, s).state
        val entry = s.log.single()
        assertTrue(entry.skipped)
        assertNull(entry.reps)
        assertEquals(1, s.index)
    }

    @Test
    fun `a newer RPE question replaces an unanswered one`() {
        val player = WorkoutPlayer(steps)
        val ask1 = RpeAsk("a", "A", listOf("x"))
        val s = PlayerState(pendingRpe = ask1)
        assertEquals(s.copy(pendingRpe = null), player.answerRpe(s, null).state)
        assertEquals(mapOf("a" to 10), player.answerRpe(s, 12).state.rpe)
    }

    @Test
    fun `music is sacred - only feel bad may pause it, silence has no tones`() {
        for (cue in PlayerCue.entries) {
            assertEquals(cue == PlayerCue.FEEL_BAD, SoundPolicy.pausesMusic(cue))
            assertNull(SoundPolicy.of(cue, SoundLevel.SILENT).tone, "$cue")
            assertNotNull(SoundPolicy.of(cue, SoundLevel.SILENT).vibe, "$cue: every event is felt")
        }
        assertEquals(Tone.READY, SoundPolicy.of(PlayerCue.REST_END, SoundLevel.IMPORTANT).tone)
        assertEquals(Vibe.DOUBLE_CLICK, SoundPolicy.of(PlayerCue.REST_END, SoundLevel.IMPORTANT).vibe)
        assertEquals(SoundLevel.IMPORTANT, SoundLevel.DEFAULT)
        assertEquals(SoundLevel.IMPORTANT, SoundLevel.of("nonsense"))
    }

    @Test
    fun `no heart rate source in this slice`() {
        assertEquals(false, NoHeartRate.available)
        assertNull(NoHeartRate.bpm.value)
    }
}
