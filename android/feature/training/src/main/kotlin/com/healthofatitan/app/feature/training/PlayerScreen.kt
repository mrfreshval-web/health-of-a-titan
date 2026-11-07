package com.healthofatitan.app.feature.training

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.healthofatitan.app.core.ui.component.HotBackground
import com.healthofatitan.app.core.ui.component.HotMode
import com.healthofatitan.app.core.ui.component.HotOverline
import com.healthofatitan.app.core.ui.component.HotPrimaryButton
import com.healthofatitan.app.core.ui.component.HotRatioBar
import com.healthofatitan.app.core.ui.component.HotSecondaryButton
import com.healthofatitan.app.core.ui.component.HotSpacing
import com.healthofatitan.app.core.ui.component.HotStatTile
import com.healthofatitan.app.core.ui.component.HotTextButton
import com.healthofatitan.app.core.ui.component.TitanSeal
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.core.ui.theme.HotType
import com.healthofatitan.app.domain.training.PlayerPhase
import com.healthofatitan.app.domain.training.Side
import com.healthofatitan.app.domain.training.WorkoutStep
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos

/** What the player's buttons do (wired to [WorkoutController] by the route). */
data class PlayerActions(
    val onDone: () -> Unit = {},
    val onSkip: () -> Unit = {},
    val onExtend: () -> Unit = {},
    val onPause: () -> Unit = {},
    val onResume: () -> Unit = {},
    val onFinish: () -> Unit = {},
    val onFeelBad: () -> Unit = {},
    val onRpe: (Int?) -> Unit = {},
    val onClose: () -> Unit = {},
)

/**
 * The workout player (A40 §7.1): countdown → set → rest ×N → summary. The Titan shows the
 * exercise in its tempo, the timer is the hero of a timed set and of a rest (the breathing ring),
 * RPE is one tap after the last set of an exercise, and «Мне плохо» is on every screen.
 *
 * @param now `elapsedRealtime` of this frame: the screen is a pure function of the run and now
 * @param animate false with «Удалить анимацию»: the Titan stands in the key pose, the ring is still
 */
@Composable
fun PlayerScreen(run: WorkoutRun, now: Long, actions: PlayerActions, animate: Boolean = true) {
    val mode = run.session.mode.hotMode()
    val state = run.state
    HotBackground(aura = mode.color()) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = HotSpacing.edge, vertical = 12.dp)
                .testTag(PLAYER_SCREEN_TAG),
        ) {
            if (state.phase == PlayerPhase.DONE) {
                Summary(run, now, actions)
                return@Column
            }
            val step = run.player.step(state)
            TopRow(run, step, actions)
            Spacer(Modifier.height(10.dp))
            val works = run.player.steps.count { it is WorkoutStep.Work }
            val done = state.log.size
            HotRatioBar(if (works == 0) 0f else done.toFloat() / works)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    step is WorkoutStep.Rest -> Rest(run, step, now, mode, animate)
                    step is WorkoutStep.Work -> Work(run, step, now, mode, animate)
                }
                if (state.paused) Paused(actions)
            }
            state.pendingRpe?.takeIf { !state.paused }?.let { ask ->
                Rpe(ask.label, actions.onRpe)
                Spacer(Modifier.height(10.dp))
            }
            if (!state.paused) Controls(state.phase, actions)
        }
    }
}

@Composable
private fun TopRow(run: WorkoutRun, step: WorkoutStep?, actions: PlayerActions) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val label = step?.let { TrainingTexts.block(it.blockType) } ?: run.session.template.nameRu
        Column(Modifier.weight(1f)) {
            HotOverline(label)
            if (step is WorkoutStep.Work) {
                val line = if (step.rounds > 1) TrainingTexts.roundOf(step.round, step.rounds) else TrainingTexts.setOf(step.set, step.sets)
                Text(line, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        FeelBadButton(onClick = actions.onFeelBad)
    }
}

@Composable
private fun BoxScope.Work(run: WorkoutRun, step: WorkoutStep.Work, now: Long, mode: HotMode, animate: Boolean) {
    val state = run.state
    val exercise = step.exercise
    val countdown = state.phase == PlayerPhase.COUNTDOWN
    val left = state.remainingMs(now)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))
        TitanExercise(
            timeline = timeline(exercise, run.poses, step.item.tempo, otherSide = step.side == Side.SECOND),
            front = exercise.frontView(),
            light = figureLight(mode),
            glow = mode.color(),
            modifier = Modifier.fillMaxWidth().height(300.dp),
            animate = animate && !countdown && !state.paused,
            description = exercise.nameRu,
        )
        Text(
            exercise.nameRu,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        step.side?.let {
            Text(TrainingTexts.side(it), style = MaterialTheme.typography.labelLarge, color = mode.color())
        }
        Spacer(Modifier.height(8.dp))
        when {
            countdown -> {
                HotOverline(TrainingTexts.GET_READY)
                BigNumber(seconds(left).toString())
            }
            step.timed -> BigNumber(TrainingTexts.clock(seconds(left)))
            else -> {
                val reps = step.reps
                val n = reps?.trim()?.toIntOrNull()
                when {
                    n != null -> {
                        BigNumber(n.toString())
                        Text(TrainingTexts.plural(n, "повтор", "повтора", "повторов"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    reps != null -> Text(reps, style = HotType.numeral, textAlign = TextAlign.Center)
                    step.distanceM != null -> BigNumber("${step.distanceM} м")
                }
            }
        }
        step.item.tempo?.let {
            Spacer(Modifier.height(6.dp))
            Text(TrainingTexts.tempo(it), style = HotType.mono, color = HotThemeTokens.colors.textTertiary)
        }
        Spacer(Modifier.height(10.dp))
        Technique(run, step)
    }
}

/** Cues of the exercise only from reviewed content; until then a neutral caption (TODO review). */
@Composable
private fun Technique(run: WorkoutRun, step: WorkoutStep.Work) {
    val cue = step.exercise.cuesRu.firstOrNull()
    val text = if (run.reviewed && cue != null) cue else TrainingTexts.TECHNIQUE_PENDING
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = HotThemeTokens.colors.textTertiary,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 12.dp),
    )
}

@Composable
private fun BigNumber(text: String) {
    Text(
        text,
        style = HotType.timer,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
}

/**
 * A rest (A40 §7.2): the breathing ring — the arc is the time left, the light inside breathes
 * in 4 s and out 6 s (a calm rhythm, no instruction) — the timer, and what comes next.
 */
@Composable
private fun BoxScope.Rest(run: WorkoutRun, step: WorkoutStep.Rest, now: Long, mode: HotMode, animate: Boolean) {
    val state = run.state
    val left = state.remainingMs(now) ?: 0L
    val total = maxOf(step.seconds * 1000L, left, 1L)
    val elapsed = state.elapsedMs(now)
    val cycle = elapsed % BREATH_MS
    val inhale = cycle < INHALE_MS
    val breath = when {
        !animate -> 1f
        inhale -> ease(cycle.toFloat() / INHALE_MS)
        else -> 1f - ease((cycle - INHALE_MS).toFloat() / (BREATH_MS - INHALE_MS))
    }
    val color = mode.color()
    val tokens = HotThemeTokens.colors
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(12.dp))
        Box(Modifier.size(300.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
                val r = size.minDimension / 2f - 8.dp.toPx()
                val stroke = 3.dp.toPx()
                drawCircle(tokens.ruleSoft, radius = r, style = Stroke(stroke))
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * (left.toFloat() / total),
                    useCenter = false,
                    topLeft = Offset(center.x - r, center.y - r),
                    size = Size(r * 2, r * 2),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
                val inner = r * (0.55f + 0.3f * breath)
                drawCircle(
                    Brush.radialGradient(listOf(color.copy(alpha = if (tokens.glow) 0.30f else 0.14f), Color.Transparent), center = center, radius = inner),
                    radius = inner,
                )
                drawCircle(color.copy(alpha = 0.5f), radius = inner, style = Stroke(1.dp.toPx()))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                HotOverline(TrainingTexts.REST)
                BigNumber(TrainingTexts.clock(seconds(left)))
                if (animate) {
                    Text(
                        if (inhale) TrainingTexts.BREATH_IN else TrainingTexts.BREATH_OUT,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        run.player.nextWork(state)?.let { next ->
            Text(TrainingTexts.next(next.exercise.nameRu), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(TrainingTexts.dose(next), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** RPE in one tap after the last set (A40 §7.3), the clock keeps going. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.Rpe(label: String, onRpe: (Int?) -> Unit) {
    Text(TrainingTexts.RPE_QUESTION, style = MaterialTheme.typography.titleSmall)
    Text(label, style = MaterialTheme.typography.bodySmall, color = HotThemeTokens.colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        RPE_VALUES.forEach { v -> TrainingChip(v.toString(), selected = false, onClick = { onRpe(v) }) }
        TrainingChip(TrainingTexts.RPE_LATER, selected = false, onClick = { onRpe(null) })
    }
}

@Composable
private fun Controls(phase: PlayerPhase, actions: PlayerActions) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        HotTextButton(TrainingTexts.PAUSE, onClick = actions.onPause)
        if (phase == PlayerPhase.REST) {
            HotSecondaryButton(TrainingTexts.EXTEND, onClick = actions.onExtend)
            HotPrimaryButton(TrainingTexts.SKIP, onClick = actions.onSkip, modifier = Modifier.weight(1f))
        } else {
            HotSecondaryButton(TrainingTexts.SKIP, onClick = actions.onSkip)
            HotPrimaryButton(TrainingTexts.READY, onClick = actions.onDone, modifier = Modifier.weight(1f).testTag(PLAYER_DONE_TAG))
        }
    }
}

@Composable
private fun BoxScope.Paused(actions: PlayerActions) {
    Column(
        Modifier
            .matchParentSize()
            .background(HotThemeTokens.colors.knockout)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        Text(TrainingTexts.PAUSED, style = HotType.headline)
        HotPrimaryButton(TrainingTexts.RESUME, onClick = actions.onResume, modifier = Modifier.fillMaxWidth())
        HotSecondaryButton(TrainingTexts.FINISH, onClick = actions.onFinish, modifier = Modifier.fillMaxWidth())
    }
}

/** «Итог» (A40 §7.1) with «Печать»: the ring closes around the Titan once, then the numbers. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.Summary(run: WorkoutRun, now: Long, actions: PlayerActions) {
    val s = run.summary(now)
    Column(
        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        TitanSeal(key = run.id, spoken = TrainingTexts.SEAL_SPOKEN, modifier = Modifier.size(240.dp))
        Text(TrainingTexts.SUMMARY_TITLE, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(run.session.template.nameRu, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HotStatTile(TrainingTexts.SUMMARY_TIME, TrainingTexts.duration(s.activeSeconds), modifier = Modifier.weight(1f))
            HotStatTile(TrainingTexts.SUMMARY_SETS, TrainingTexts.sets(s.setsDone, s.setsPlanned), modifier = Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HotStatTile(TrainingTexts.SUMMARY_RPE, s.rpeMedian?.toString() ?: "—", modifier = Modifier.weight(1f))
            HotStatTile(
                TrainingTexts.SUMMARY_STRETCH,
                if (s.stretchDone) TrainingTexts.STRETCH_DONE else TrainingTexts.STRETCH_SKIPPED,
                modifier = Modifier.weight(1f),
            )
        }
        if (s.feltBad) {
            Text(TrainingTexts.SUMMARY_FELT_BAD, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
    }
    Spacer(Modifier.height(12.dp))
    HotPrimaryButton(TrainingTexts.CLOSE, onClick = actions.onClose, modifier = Modifier.fillMaxWidth())
}

private fun seconds(ms: Long?): Int = if (ms == null) 0 else ceil(ms / 1000.0).toInt()

private fun ease(x: Float): Float = ((1 - cos(PI * x.coerceIn(0f, 1f))) / 2).toFloat()

private const val INHALE_MS = 4_000L
private const val BREATH_MS = 10_000L
private val RPE_VALUES = 5..10
