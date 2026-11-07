package com.healthofatitan.app.core.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import kotlinx.coroutines.launch

/**
 * The storyboard of «Титан приходит» (A37 §5, `_sync-titan-storyboard.png`) as keyframes on one
 * timeline in milliseconds. Pure, so the frames are unit-tested.
 *
 * 01 0 ms fog, columns at 50 %, the ring barely visible · 02 180 ms a silhouette far away
 * (scale 0.30, blur 26, 35 %) · 03 420 ms a step forward (0.60, blur 10, 75 %) · 04 680 ms the
 * palms on the capitals (0.84, columns at 69 %, tick) · 05 900 ms the push: the columns overshoot
 * to 104 % and settle, the pulse flashes (tick) · 06 1150 ms the ring draws with the data, the
 * light turns into the mode · 07 1500 ms the Titan is ready.
 */
object TitanArrivalTimeline {
    const val FOG = 0f
    const val FAR = 180f
    const val STEP = 420f
    const val PALMS = 680f
    const val PUSH = 900f
    const val RING = 1150f
    const val END = 1500f

    /** Where a finger pulling to refresh puts the story: from the step to the palms. */
    fun tracking(fraction: Float): Float = STEP + (PALMS - STEP) * fraction.coerceIn(0f, 1f)

    fun titanScale(t: Float) = keys(t, FOG to 0.30f, FAR to 0.30f, STEP to 0.60f, PALMS to 0.84f, PUSH to 1f)
    fun titanAlpha(t: Float) = keys(t, FOG to 0f, FAR to 0.35f, STEP to 0.75f, PALMS to 1f)
    fun titanBlur(t: Float) = keys(t, FOG to 26f, FAR to 26f, STEP to 10f, PALMS to 0f)

    /** Up in the distance, down onto the ground as he comes (art units). */
    fun titanLift(t: Float) = keys(t, FOG to -90f, FAR to -90f, STEP to -30f, PALMS to 0f)

    /** Columns: 0.5 in the fog, 0.69 under the palms, the push overshoots 4 % and settles. */
    fun spread(t: Float) = keys(t, FOG to 0.5f, STEP to 0.5f, PALMS to 0.69f, PUSH to 1.04f, 1050f to 1f)
    fun columnAlpha(t: Float) = keys(t, FOG to 0.35f, STEP to 0.6f, PALMS to 1f)

    /** The pulse flashes through the columns at the push and calms down. */
    fun pulseDraw(t: Float) = keys(t, 840f to 0f, 1000f to 1f)
    fun pulseAlpha(t: Float) = keys(t, 840f to 0f, PUSH to 1f, RING to 0.8f, END to 0f)

    /** How much of the ring may show: it draws «as the data comes» from frame 05 to 06. */
    fun ringReveal(t: Float) = keys(t, PUSH to 0f, RING to 1f)

    /** The fog over the scene. */
    fun fog(t: Float) = keys(t, FOG to 1f, FAR to 0.85f, STEP to 0.45f, PALMS to 0f)

    private fun keys(t: Float, vararg k: Pair<Float, Float>): Float {
        if (t <= k.first().first) return k.first().second
        for (i in 1 until k.size) {
            val (t1, v1) = k[i]
            if (t <= t1) {
                val (t0, v0) = k[i - 1]
                val x = if (t1 - t0 < 0.001f) 1f else (t - t0) / (t1 - t0)
                val e = x * x * (3f - 2f * x) // smoothstep: soft in, soft out
                return v0 + (v1 - v0) * e
            }
        }
        return k.last().second
    }
}

/** A37 §5: the full arrival at most once in 3 hours; otherwise the short one. Process-wide. */
internal object TitanArrivalClock {
    private const val FULL_EVERY_MS = 3 * 60 * 60 * 1000L
    private var lastFull = 0L

    fun takeFull(now: Long): Boolean {
        if (now - lastFull < FULL_EVERY_MS) return false
        lastFull = now
        return true
    }
}

/**
 * «Титан приходит» on the [SyncMarkState] phases:
 * - **RUNNING** (new attempt): the storyboard plays once — full 1.5 s, or the 0.7 s version from
 *   the palms when [SyncMarkState.quick] (pull-to-refresh) or a full one played < 3 h ago; then the
 *   ring follows the real progress and the pulse breathes while data still comes.
 * - **tracking** (finger): the Titan steps forward with the finger, the ring follows it.
 * - **SUCCESS / IDLE:** frame 07 — the ring closed in the mode light.
 * - **ERROR:** the Titan stays; the ring stops where the data stopped and turns into a dashed
 *   muted circle. No red.
 * - **«Удалить анимацию»:** frame 07 at once, no ticks.
 */
@Composable
internal fun TitanArrival(
    state: SyncMarkState,
    modifier: Modifier,
    size: Dp,
    contentDescription: String?,
    style: SyncMarkStyle,
    haptics: Boolean,
) {
    val tl = TitanArrivalTimeline
    val animate = !rememberReduceMotion()
    val running = state.phase == SyncMarkPhase.RUNNING
    val t = remember { Animatable(if (running && animate) tl.FOG else tl.END) }
    val ring = remember { Animatable(if (running) 0f else 1f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var played by remember { mutableLongStateOf(Long.MIN_VALUE) }

    // the story: once per attempt, never cancelled by the phase changing under it
    val runKey = if (running && !state.tracking) state.key else null
    LaunchedEffect(runKey, animate) {
        if (runKey == null || runKey == played) return@LaunchedEffect
        played = runKey
        if (!animate) {
            t.snapTo(tl.END)
            return@LaunchedEffect
        }
        val quick = state.quick || !TitanArrivalClock.takeFull(System.currentTimeMillis())
        scope.launch {
            val total = if (quick) style.quickMillis else style.arriveMillis
            val start = if (quick) maxOf(t.value.takeIf { it < tl.PALMS } ?: tl.PALMS, tl.STEP).coerceAtMost(tl.PALMS) else tl.FOG
            val k = total / (tl.END - start)
            t.snapTo(start)
            if (start < tl.PALMS) t.animateTo(tl.PALMS, tween(((tl.PALMS - start) * k).toInt(), easing = LinearEasing))
            if (haptics) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
            t.animateTo(tl.PUSH, tween(((tl.PUSH - tl.PALMS) * k).toInt(), easing = LinearEasing))
            if (haptics) haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            t.animateTo(tl.END, tween(((tl.END - tl.PUSH) * k).toInt(), easing = LinearEasing))
        }
    }
    // the finger: the Titan steps forward with it
    LaunchedEffect(state.tracking, state.progress) {
        if (running && state.tracking) t.snapTo(tl.tracking(state.progress ?: 0f))
    }
    // idle / success / error seen without a run: the last frame
    LaunchedEffect(state.phase) {
        if (!running && !t.isRunning) t.snapTo(tl.END)
    }
    // the ring: the share of data that came (spring 1.0 / 300, A37 §5)
    LaunchedEffect(state.phase, state.key, state.progress) {
        val ringSpring = spring<Float>(dampingRatio = 1f, stiffness = 300f)
        when (state.phase) {
            SyncMarkPhase.RUNNING -> {
                val target = (state.progress ?: 0f).coerceIn(0.02f, 1f)
                if (ring.value > target + 0.001f && !state.tracking) ring.snapTo(0f)
                if (animate && !state.tracking) ring.animateTo(target, ringSpring) else ring.snapTo(target)
            }
            SyncMarkPhase.SUCCESS, SyncMarkPhase.IDLE -> if (animate) ring.animateTo(1f, ringSpring) else ring.snapTo(1f)
            SyncMarkPhase.ERROR -> Unit
        }
    }
    val breathing = if (running && animate && !state.tracking) {
        rememberInfiniteTransition(label = "titanPulse").animateFloat(
            0.25f, 0.6f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse), label = "breath",
        )
    } else {
        null
    }

    val tokens = HotThemeTokens.colors
    val lit by remember { derivedStateOf { t.value >= tl.RING } }
    val error = state.phase == SyncMarkPhase.ERROR
    val base = TitanLights.default()
    val light = when {
        error -> base.copy(line = List(3) { style.muted }, glow = null)
        lit -> base.copy(line = listOf(Color.White, lerpColor(Color.White, style.accent, 0.35f), style.accent), glow = if (style.glow) style.accent else null)
        else -> base.copy(glow = if (style.glow) base.glow else null)
    }
    val glyph = size < 120.dp
    val a11y = if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier

    TitanScene(
        modifier = modifier.size(size).testTag(SYNC_MARK_TAG).then(a11y),
        light = light,
        layers = rememberTitanLayers(glyph),
        spread = { tl.spread(t.value) },
        titanScale = { tl.titanScale(t.value) },
        titanLift = { tl.titanLift(t.value) },
        titanBlur = { tl.titanBlur(t.value) },
        titanAlpha = { tl.titanAlpha(t.value) },
        columnAlpha = { tl.columnAlpha(t.value) },
        ring = { if (error) ring.value else ring.value * tl.ringReveal(t.value) },
        ringColor = if (error) style.muted else if (lit) style.accent else style.accentHighlight,
        ringTrack = style.track,
        ringDashed = error,
        ringStroke = if (glyph) 70f else 11f,
        ringGlow = { if (style.glow) 1f else 0f },
        pulse = { if (t.value >= tl.END && breathing != null) 1f else tl.pulseDraw(t.value) },
        pulseAlpha = { if (t.value >= tl.END) breathing?.value ?: 0f else tl.pulseAlpha(t.value) },
        pulseColor = if (lit) style.accent else style.accentHighlight,
        ringFit = 0.95f,
    ) {
        // the fog the Titan steps out of (frames 01–03)
        val fogColor = tokens.skyTop
        Spacer(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val f = tl.fog(t.value)
                    if (f > 0.01f) {
                        drawCircle(
                            Brush.radialGradient(
                                listOf(fogColor.copy(alpha = 0.85f * f), fogColor.copy(alpha = 0.55f * f), Color.Transparent),
                                center = Offset(this.size.width / 2f, this.size.height * 0.55f),
                                radius = this.size.minDimension * 0.55f,
                            ),
                            radius = this.size.minDimension * 0.55f,
                            center = Offset(this.size.width / 2f, this.size.height * 0.55f),
                        )
                    }
                },
        )
    }
}
