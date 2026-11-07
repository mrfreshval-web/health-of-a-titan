package com.healthofatitan.app.feature.training

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.healthofatitan.app.core.database.HotDatabase
import com.healthofatitan.app.core.database.dao.TrainingLogDao
import com.healthofatitan.app.core.database.entity.TrainingSetEntity
import com.healthofatitan.app.core.database.entity.TrainingWorkoutEntity
import com.healthofatitan.app.domain.engine.ItemStatus
import com.healthofatitan.app.domain.training.PlayerPhase
import com.healthofatitan.app.domain.training.PlayerState
import com.healthofatitan.app.domain.training.PoseLibrary
import com.healthofatitan.app.domain.training.SoundLevel
import com.healthofatitan.app.domain.training.Transition
import com.healthofatitan.app.domain.training.TrainingLibrary
import com.healthofatitan.app.domain.training.TrainingSession
import com.healthofatitan.app.domain.training.WorkoutPlayer
import com.healthofatitan.app.domain.training.WorkoutStep
import com.healthofatitan.app.domain.training.WorkoutSteps
import com.healthofatitan.app.domain.training.WorkoutSummary
import com.healthofatitan.app.integration.whoop.day.DayPlanRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One workout in progress: the session, its player and the player's state. Times in [state] are
 * `elapsedRealtime` (monotonic, survives a clock change); [startedWallMs] is for the log.
 */
data class WorkoutRun(
    val id: String,
    val day: LocalDate,
    val session: TrainingSession,
    val player: WorkoutPlayer,
    val poses: PoseLibrary,
    val sound: SoundLevel,
    val reviewed: Boolean,
    val state: PlayerState,
    val startedWallMs: Long,
    val saved: Boolean = false,
) {
    fun summary(now: Long): WorkoutSummary = WorkoutSummary.of(player, state, now)
}

/**
 * The workout clock (A40 §7): the state machine of [WorkoutPlayer] driven by a 200 ms tick,
 * cues played by [CuePlayer], the foreground [WorkoutService] kept while a workout runs, and
 * the finished workout saved to `training_workout` / `training_set` with the plan item of the
 * day marked done («отмечено тобой»). One workout at a time, process-wide.
 */
@Singleton
class WorkoutController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val cues: CuePlayer,
    private val log: TrainingLogDao,
    private val days: DayPlanRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _run = MutableStateFlow<WorkoutRun?>(null)
    val run: StateFlow<WorkoutRun?> = _run.asStateFlow()
    private var ticker: Job? = null

    fun now(): Long = SystemClock.elapsedRealtime()

    /** Starts [session]; a workout already running is kept (one at a time). */
    fun start(session: TrainingSession, day: LocalDate, library: TrainingLibrary, sound: SoundLevel) {
        if (_run.value?.state?.done == false) return
        val defaults = library.rules.player
        val player = WorkoutPlayer(WorkoutSteps.of(session, defaults), countdownS = defaults.countdownS, extendStepS = defaults.extendRestStepS)
        val t = player.start(now())
        _run.value = WorkoutRun(
            id = UUID.randomUUID().toString(),
            day = day,
            session = session,
            player = player,
            poses = library.poses,
            sound = sound,
            reviewed = library.reviewed,
            state = t.state,
            startedWallMs = System.currentTimeMillis(),
        )
        play(t)
        startService()
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                delay(TICK_MS)
                val r = _run.value ?: break
                if (r.state.done) break
                update { p, s -> p.tick(now(), s) }
            }
        }
    }

    fun done(reps: String? = null) = update { p, s -> p.done(now(), s, reps) }

    fun skip() = update { p, s -> p.skip(now(), s) }

    fun extendRest() = update { p, s -> p.extendRest(s) }

    fun pause() = update { p, s -> p.pause(now(), s) }

    fun resume() {
        cues.releaseMusic()
        update { p, s -> p.resume(now(), s) }
    }

    /** «Мне плохо» (A40 §2.5): stop now; the screen opens help. */
    fun feelBad() = update { p, s -> p.feelBad(now(), s) }

    fun answerRpe(value: Int?) = update { p, s -> p.answerRpe(s, value) }

    fun finish() = update { p, s -> p.finish(now(), s) }

    /** The summary was closed: forget the run, stop the service, give the music back. */
    fun close() {
        val r = _run.value
        if (r != null && !r.state.done) finish()
        ticker?.cancel()
        cues.releaseMusic()
        _run.value = null
        stopService()
    }

    private fun update(step: (WorkoutPlayer, PlayerState) -> Transition) {
        val r = _run.value ?: return
        val t = step(r.player, r.state)
        if (t.state == r.state && t.cues.isEmpty()) return
        val next = r.copy(state = t.state)
        _run.value = next
        play(t, r.sound)
        if (t.state.done && !r.state.done) onFinished(next)
    }

    private fun play(t: Transition, level: SoundLevel = _run.value?.sound ?: SoundLevel.DEFAULT) {
        t.cues.forEach { cues.play(it, level) }
    }

    private fun onFinished(r: WorkoutRun) {
        ticker?.cancel()
        stopService()
        scope.launch {
            runCatching { save(r) }
            _run.value?.takeIf { it.id == r.id }?.let { _run.value = it.copy(saved = true) }
        }
    }

    private suspend fun save(r: WorkoutRun) {
        val user = HotDatabase.LOCAL_USER
        val s = r.summary(now())
        val finishedWall = r.startedWallMs + ((r.state.finishedAt ?: now()) - (r.state.startedAt ?: now())).coerceAtLeast(0L)
        val rpeByExercise = r.player.steps.asSequence()
            .filterIsInstance<WorkoutStep.Work>()
            .mapNotNull { w -> w.askRpe?.let { ask -> r.state.rpe[ask.key]?.let { (w.exercise.id to w.set) to it } } }
            .toMap()
        log.save(
            TrainingWorkoutEntity(
                userId = user,
                workoutId = r.id,
                dayKey = r.day.toString(),
                templateRef = r.session.ref,
                mode = r.session.mode.name,
                lighter = r.session.lighter,
                startedAt = r.startedWallMs,
                finishedAt = finishedWall,
                activeS = s.activeSeconds,
                setsDone = s.setsDone,
                setsSkipped = s.setsSkipped,
                setsPlanned = s.setsPlanned,
                rpeMedian = s.rpeMedian,
                feltBad = s.feltBad,
                stretchDone = s.stretchDone,
            ),
            r.state.log.map { e ->
                TrainingSetEntity(
                    userId = user,
                    workoutId = r.id,
                    stepIndex = e.stepIndex,
                    exerciseId = e.exerciseId,
                    blockType = e.blockType.id,
                    setNo = e.set,
                    side = e.side?.name?.lowercase(),
                    reps = e.reps,
                    seconds = e.seconds,
                    skipped = e.skipped,
                    rpe = rpeByExercise[e.exerciseId to e.set],
                )
            },
        )
        // The plan item of the day: a workout with at least one set done marks it «отмечено тобой».
        if (s.setsDone > 0) {
            val view = days.latest.first()
            val item = view?.takeIf { it.dayKey == r.day }?.let(TrainingRequests::trainingItem)
            if (item != null && item.status == ItemStatus.OPEN) days.toggle(r.day, item.id)
        }
    }

    private fun startService() {
        runCatching { ContextCompat.startForegroundService(context, Intent(context, WorkoutService::class.java)) }
    }

    private fun stopService() {
        runCatching { context.stopService(Intent(context, WorkoutService::class.java)) }
    }

    /** True while a workout is between start and summary (the service keeps the process alive). */
    val active: Boolean get() = _run.value?.state?.let { it.phase != PlayerPhase.DONE } == true

    private companion object {
        const val TICK_MS = 200L
    }
}
