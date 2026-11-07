package com.healthofatitan.app.domain.training

import kotlin.math.ceil

enum class PlayerPhase { READY, COUNTDOWN, WORK, REST, DONE }

/** Events the player raises for haptics and tones (A40 §7.5, A43 §1.3). */
enum class PlayerCue { COUNTDOWN_TICK, SET_START, LAST_SECONDS_TICK, REST_END, SIDE_SWITCH, FINISHED, FEEL_BAD }

/** One set as it was done. [reps] is what the user confirmed (the plan if untouched). */
data class SetLog(
    val stepIndex: Int,
    val blockType: BlockType,
    val exerciseId: String,
    val set: Int,
    val side: Side?,
    val reps: String?,
    val seconds: Int,
    val skipped: Boolean,
)

/**
 * The player's state (A40 §7.1). Time is a monotonic clock in ms given from outside, so the
 * machine is pure and the foreground service, the screen and the tests drive the same thing.
 */
data class PlayerState(
    val index: Int = 0,
    val phase: PlayerPhase = PlayerPhase.READY,
    val phaseStartedAt: Long = 0L,
    /** End of a timed phase (countdown, timed set, rest); null for an open set. */
    val endsAt: Long? = null,
    val pausedAt: Long? = null,
    val pausedMs: Long = 0L,
    val pendingRpe: RpeAsk? = null,
    val rpe: Map<String, Int> = emptyMap(),
    val log: List<SetLog> = emptyList(),
    /** The last whole second that already ticked in this phase (dedupes ticks between frames). */
    val tickedSecond: Int = Int.MAX_VALUE,
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
    val feltBad: Boolean = false,
) {
    val paused: Boolean get() = pausedAt != null
    val done: Boolean get() = phase == PlayerPhase.DONE

    /** Remaining ms of a timed phase at [now] (frozen while paused). */
    fun remainingMs(now: Long): Long? = endsAt?.let { (it - (pausedAt ?: now)).coerceAtLeast(0L) }

    /** Elapsed ms of the current phase at [now] (frozen while paused). */
    fun elapsedMs(now: Long): Long = ((pausedAt ?: now) - phaseStartedAt).coerceAtLeast(0L)
}

data class Transition(val state: PlayerState, val cues: List<PlayerCue> = emptyList())

/**
 * State machine of the workout player: `Обзор → [Отсчёт → Подход → Отдых]×N → Итог`, plus
 * pause and «Мне плохо» at any moment. Timed sets end by themselves, open sets end on «Готово».
 * The 3-2-1 countdown runs before a set that does not follow a rest (the end of a rest is
 * already the signal). RPE is asked once after the last set of an exercise, a pair or a circuit,
 * without stopping the clock: the question stays until answered or replaced by the next one.
 */
class WorkoutPlayer(
    val steps: List<WorkoutStep>,
    private val countdownS: Int = 3,
    private val extendStepS: Int = 15,
) {
    fun step(s: PlayerState): WorkoutStep? = steps.getOrNull(s.index)

    /** The next set after the current step (what the rest screen shows as «Дальше»). */
    fun nextWork(s: PlayerState): WorkoutStep.Work? =
        steps.drop(s.index + 1).firstOrNull { it is WorkoutStep.Work } as? WorkoutStep.Work

    fun start(now: Long, s: PlayerState = PlayerState()): Transition {
        if (s.phase != PlayerPhase.READY) return Transition(s)
        return enter(0, now, s.copy(startedAt = now))
    }

    fun tick(now: Long, s: PlayerState): Transition {
        if (s.paused || s.phase == PlayerPhase.READY || s.phase == PlayerPhase.DONE) return Transition(s)
        val ends = s.endsAt ?: return Transition(s)
        val left = ends - now
        val second = ceil(left / 1000.0).toInt()
        return when (s.phase) {
            PlayerPhase.COUNTDOWN -> {
                if (left <= 0) return beginWork(now, s)
                if (second < s.tickedSecond) Transition(s.copy(tickedSecond = second), listOf(PlayerCue.COUNTDOWN_TICK)) else Transition(s)
            }
            PlayerPhase.WORK -> {
                if (left <= 0) return complete(now, s, reps = null, skipped = false)
                if (second <= LAST_SECONDS && second < s.tickedSecond) Transition(s.copy(tickedSecond = second), listOf(PlayerCue.LAST_SECONDS_TICK)) else Transition(s)
            }
            PlayerPhase.REST -> {
                if (left <= 0) {
                    val t = enter(s.index + 1, now, s)
                    return t.copy(cues = listOf(PlayerCue.REST_END) + t.cues.filter { it != PlayerCue.SET_START })
                }
                if (second <= LAST_SECONDS && second < s.tickedSecond) Transition(s.copy(tickedSecond = second), listOf(PlayerCue.LAST_SECONDS_TICK)) else Transition(s)
            }
            else -> Transition(s)
        }
    }

    /** «Готово»: the set is done as planned or with the [reps] the user set; in a countdown it starts the set now. */
    fun done(now: Long, s: PlayerState, reps: String? = null): Transition = when {
        s.paused -> Transition(s)
        s.phase == PlayerPhase.COUNTDOWN -> beginWork(now, s)
        s.phase == PlayerPhase.WORK -> complete(now, s, reps, skipped = false)
        s.phase == PlayerPhase.REST -> enter(s.index + 1, now, s)
        else -> Transition(s)
    }

    /** «Пропустить» without reproach (A40 §7.3). */
    fun skip(now: Long, s: PlayerState): Transition = when {
        s.paused -> Transition(s)
        s.phase == PlayerPhase.COUNTDOWN || s.phase == PlayerPhase.WORK -> complete(now, s, null, skipped = true)
        s.phase == PlayerPhase.REST -> enter(s.index + 1, now, s)
        else -> Transition(s)
    }

    /** «+15 с» on a rest. */
    fun extendRest(s: PlayerState): Transition {
        if (s.phase != PlayerPhase.REST || s.endsAt == null) return Transition(s)
        return Transition(s.copy(endsAt = s.endsAt + extendStepS * 1000L, tickedSecond = Int.MAX_VALUE))
    }

    fun pause(now: Long, s: PlayerState): Transition =
        if (s.paused || s.phase == PlayerPhase.DONE || s.phase == PlayerPhase.READY) Transition(s) else Transition(s.copy(pausedAt = now))

    fun resume(now: Long, s: PlayerState): Transition {
        val at = s.pausedAt ?: return Transition(s)
        val d = (now - at).coerceAtLeast(0L)
        return Transition(
            s.copy(
                pausedAt = null,
                pausedMs = s.pausedMs + d,
                endsAt = s.endsAt?.plus(d),
                phaseStartedAt = s.phaseStartedAt + d,
            ),
        )
    }

    /** «Мне плохо» (A40 §2.5): everything stops at once; the screen opens help. */
    fun feelBad(now: Long, s: PlayerState): Transition {
        val paused = pause(now, s).state
        return Transition(paused.copy(feltBad = true), listOf(PlayerCue.FEEL_BAD))
    }

    fun answerRpe(s: PlayerState, value: Int?): Transition {
        val ask = s.pendingRpe ?: return Transition(s)
        val rpe = if (value != null) s.rpe + (ask.key to value.coerceIn(RPE_MIN, RPE_MAX)) else s.rpe
        return Transition(s.copy(pendingRpe = null, rpe = rpe))
    }

    /** End now: the summary shows what was done. */
    fun finish(now: Long, s: PlayerState): Transition {
        if (s.phase == PlayerPhase.DONE) return Transition(s)
        val resumed = resume(now, s).state
        return Transition(resumed.copy(phase = PlayerPhase.DONE, endsAt = null, finishedAt = now, phaseStartedAt = now), listOf(PlayerCue.FINISHED))
    }

    private fun beginWork(now: Long, s: PlayerState): Transition {
        val w = step(s) as? WorkoutStep.Work ?: return enter(s.index + 1, now, s)
        return Transition(
            s.copy(
                phase = PlayerPhase.WORK,
                phaseStartedAt = now,
                endsAt = w.seconds?.let { now + it * 1000L },
                tickedSecond = Int.MAX_VALUE,
            ),
            listOf(PlayerCue.SET_START),
        )
    }

    private fun complete(now: Long, s: PlayerState, reps: String?, skipped: Boolean): Transition {
        val w = step(s) as? WorkoutStep.Work ?: return enter(s.index + 1, now, s)
        val seconds = if (s.phase == PlayerPhase.WORK) ((now - s.phaseStartedAt) / 1000L).toInt().coerceAtLeast(0) else 0
        val entry = SetLog(
            stepIndex = s.index,
            blockType = w.blockType,
            exerciseId = w.exercise.id,
            set = w.set,
            side = w.side,
            reps = if (skipped) null else reps ?: w.reps,
            seconds = if (w.timed && !skipped && s.phase == PlayerPhase.WORK) minOf(seconds, w.seconds ?: seconds) else seconds,
            skipped = skipped,
        )
        val pending = if (!skipped && w.askRpe != null) w.askRpe else s.pendingRpe
        return enter(s.index + 1, now, s.copy(log = s.log + entry, pendingRpe = pending))
    }

    private fun enter(index: Int, now: Long, s: PlayerState): Transition {
        val base = s.copy(index = index, phaseStartedAt = now, tickedSecond = Int.MAX_VALUE)
        val st = steps.getOrNull(index)
            ?: return Transition(base.copy(index = steps.size, phase = PlayerPhase.DONE, endsAt = null, finishedAt = now), listOf(PlayerCue.FINISHED))
        return when (st) {
            is WorkoutStep.Rest -> Transition(
                base.copy(phase = PlayerPhase.REST, endsAt = now + st.seconds * 1000L),
                if (st.kind == RestKind.SIDE_SWITCH) listOf(PlayerCue.SIDE_SWITCH) else emptyList(),
            )
            is WorkoutStep.Work -> {
                val afterRest = steps.getOrNull(index - 1) is WorkoutStep.Rest
                if (!afterRest && countdownS > 0) {
                    Transition(base.copy(phase = PlayerPhase.COUNTDOWN, endsAt = now + countdownS * 1000L))
                } else {
                    beginWork(now, base)
                }
            }
        }
    }

    companion object {
        const val LAST_SECONDS = 3
        const val RPE_MIN = 1
        const val RPE_MAX = 10
    }
}

/** What the summary («Итог», A40 §7.1) shows and the log stores. */
data class WorkoutSummary(
    val setsDone: Int,
    val setsSkipped: Int,
    val setsPlanned: Int,
    val activeSeconds: Long,
    val rpeMedian: Int?,
    val blocksTouched: Set<BlockType>,
    val stretchDone: Boolean,
    val feltBad: Boolean,
) {
    companion object {
        fun of(player: WorkoutPlayer, s: PlayerState, now: Long): WorkoutSummary {
            val planned = player.steps.count { it is WorkoutStep.Work }
            val done = s.log.filter { !it.skipped }
            val start = s.startedAt ?: now
            val end = s.finishedAt ?: now
            val values = s.rpe.values.sorted()
            val median = if (values.isEmpty()) null else values[(values.size - 1) / 2]
            val stretchIds = player.steps.filterIsInstance<WorkoutStep.Work>().filter { it.exercise.isStretch }.map { it.exercise.id }.toSet()
            return WorkoutSummary(
                setsDone = done.size,
                setsSkipped = s.log.count { it.skipped },
                setsPlanned = planned,
                activeSeconds = ((end - start - s.pausedMs) / 1000L).coerceAtLeast(0L),
                rpeMedian = median,
                blocksTouched = done.map { it.blockType }.toSet(),
                stretchDone = done.any { it.exerciseId in stretchIds },
                feltBad = s.feltBad,
            )
        }
    }
}
