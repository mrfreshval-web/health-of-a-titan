package com.healthofatitan.app.core.ui.component

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.core.ui.theme.Kolonnada
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

const val SYNC_MARK_TAG = "sync_mark"

/** What the sync mark shows. Texts live next to it; the mark itself never carries words. */
enum class SyncMarkPhase { IDLE, RUNNING, SUCCESS, ERROR }

/**
 * @param progress 0…1 while [SyncMarkPhase.RUNNING] (the ring), null = not known yet
 * @param key identifies one sync attempt, so the finale plays once per attempt
 * @param celebrate the finale (ring closes, wave, check) plays; false shows the end state at once
 */
@Immutable
data class SyncMarkState(
    val phase: SyncMarkPhase = SyncMarkPhase.IDLE,
    val progress: Float? = null,
    val key: Long = 0L,
    val celebrate: Boolean = false,
    /** The ring follows [progress] without easing (a finger pulling to refresh). */
    val tracking: Boolean = false,
) {
    companion object {
        val Idle = SyncMarkState()
    }
}

/**
 * Everything visual about the sync mark that a theme may change: colours, line weights and
 * timings. Nothing here is hard-wired in the drawing code, so a new visual concept only needs a
 * new [HotSyncMarkDefaults.style] (and, if the figure changes, a new [SyncMarkArt]).
 */
@Immutable
data class SyncMarkStyle(
    /** Ring arc, figures, wave, success badge. */
    val accent: Color,
    /** Comet head and the bright tip of the ring. */
    val accentHighlight: Color,
    /** Error state: dashed ring, dimmed figures, «!» badge outline. */
    val muted: Color,
    /** Ring track under the progress arc. */
    val track: Color,
    val onAccent: Color,
    val errorBadge: Color,
    val onErrorBadge: Color,
    /** Soft light behind lit figures, the comet and the ring tip (A36 §4: dark theme only). */
    val glow: Boolean,
    /** Ring stroke = [ringStroke] + [strokeScale] × size; pulse stroke likewise. */
    val ringStroke: Dp = 1.5.dp,
    val pulseStroke: Dp = 1.1.dp,
    val strokeScale: Float = 0.006f,
    /** Opacity of the figures while running or failed (the lit one goes to full). */
    val dimAlpha: Float = 0.5f,
    val sweepMillis: Int = 1500,
    val finaleMillis: Int = 1100,
    val progressMillis: Int = 520,
    val fadeMillis: Int = 420,
    /** Width of the light around a figure as the pulse passes it, in art units. */
    val litWidth: Float = 150f,
    /** Comet tail length, in art units. */
    val trail: Float = 430f,
)

object HotSyncMarkDefaults {
    /** The style from the current theme tokens. */
    @Composable
    fun style(): SyncMarkStyle {
        val tokens = HotThemeTokens.colors
        val scheme = MaterialTheme.colorScheme
        return SyncMarkStyle(
            accent = scheme.primary,
            accentHighlight = if (tokens.isDark) Kolonnada.GoldHi else scheme.primary,
            muted = tokens.textTertiary,
            track = tokens.ruleSoft,
            onAccent = scheme.onPrimary,
            errorBadge = scheme.surfaceContainerHigh,
            onErrorBadge = scheme.onSurfaceVariant,
            glow = tokens.isDark,
        )
    }
}

/**
 * The figure the sync animation is drawn on, in its own coordinate space: a ring, a pulse
 * polyline (pieces with increasing x) and filled parts that light up as the pulse passes them.
 * [HotMarkArt] is the HoT mark (columns + titan); another concept can pass its own art — even
 * one without parts (ring + pulse only).
 */
interface SyncMarkArt {
    val ringCenter: Offset
    val ringRadius: Float

    /** Pulse pieces as x,y pairs; between pieces the pulse runs hidden behind a part. */
    val pulse: List<FloatArray>
    val parts: List<SyncMarkPart>

    /** Range of x the comet head travels per loop. */
    val sweep: ClosedFloatingPointRange<Float>
}

/** A filled part of the art. [litX] is where the pulse lights it; [glowCenter]/[glowRadius] its light. */
class SyncMarkPart(val path: Path, val litX: Float, val glowCenter: Offset, val glowRadius: Float)

/** The HoT mark from brand/mark-v2 (generated geometry, [HotMarkGeometry]). */
object HotMarkArt : SyncMarkArt {
    override val ringCenter = Offset(HotMarkGeometry.RING_CX, HotMarkGeometry.RING_CY)
    override val ringRadius = HotMarkGeometry.RING_R
    override val pulse: List<FloatArray> = HotMarkGeometry.PULSE
    override val sweep = 0f..2050f
    override val parts: List<SyncMarkPart> by lazy {
        fun parse(d: String) = PathParser().parsePathString(d).toPath()
        listOf(
            SyncMarkPart(parse(HotMarkGeometry.LEFT_COLUMN), HotMarkGeometry.LEFT_COLUMN_X, Offset(HotMarkGeometry.LEFT_COLUMN_X, HotMarkGeometry.COLUMNS_MID_Y), 420f),
            SyncMarkPart(parse(HotMarkGeometry.TITAN), HotMarkGeometry.TITAN_X, Offset(HotMarkGeometry.TITAN_X, HotMarkGeometry.TITAN_MID_Y), 520f),
            SyncMarkPart(parse(HotMarkGeometry.RIGHT_COLUMN), HotMarkGeometry.RIGHT_COLUMN_X, Offset(HotMarkGeometry.RIGHT_COLUMN_X, HotMarkGeometry.COLUMNS_MID_Y), 420f),
        )
    }
}

/**
 * «Пульс сквозь колоннаду» — the HoT mark as the sync indicator (brand/mark-v2, A36 §4, §7).
 *
 * - **Running:** a pulse comet sweeps left to right along the mark's own ECG line; every column
 *   and the titan light up as it passes through them, and the ring draws clockwise from the top
 *   as the real progress of the sync (token → cycles → recovery → sleep → workouts → metrics).
 * - **Success:** the ring closes, the figures fill to full colour, one wave leaves the ring and a
 *   check is drawn in the badge (≈ 1.1 s, once per sync).
 * - **Error:** sober: no glow, the ring turns into a dashed «no data» circle (A36 §2.4) in the
 *   muted colour and the badge shows «!». Never red: red is for flags and SOS only.
 * - **Reduce motion** (`ANIMATOR_DURATION_SCALE == 0`): no sweep, no wave; the ring still shows
 *   the progress and the end states appear at once (A36 §7).
 *
 * Everything animates in the draw phase only (no recomposition per frame) — 60 fps on a Pixel.
 * Visual parameters come from [style] (theme tokens) and the figure from [art], so the look can
 * be replaced without touching the state logic or the screens.
 *
 * @param badge the check / «!» badge; off by default below 56 dp (top bar, small indicators)
 */
@Composable
fun HotSyncMark(
    state: SyncMarkState,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    badge: Boolean = size >= 56.dp,
    contentDescription: String? = null,
    style: SyncMarkStyle = HotSyncMarkDefaults.style(),
    art: SyncMarkArt = HotMarkArt,
) {
    val reduceMotion = rememberReduceMotion()
    val phase = state.phase
    val animate = !reduceMotion

    // Pulse sweep: 0…1 in a loop while running; −1 = no comet.
    val sweep: State<Float> = if (phase == SyncMarkPhase.RUNNING && animate) {
        rememberInfiniteTransition(label = "syncSweep").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(style.sweepMillis, easing = LinearEasing), RepeatMode.Restart),
            label = "sweep",
        )
    } else {
        remember { mutableFloatStateOf(-1f) }
    }

    // The ring: real progress while running, closed otherwise.
    val ring = remember { Animatable(if (phase == SyncMarkPhase.RUNNING) 0f else 1f) }
    LaunchedEffect(phase, state.key, state.progress, animate) {
        when (phase) {
            SyncMarkPhase.RUNNING -> {
                val target = (state.progress ?: 0f).coerceIn(MIN_ARC, 1f)
                // a new run starts from the top (while tracking a finger the ring may also go back)
                if (ring.value > target + 0.001f && !state.tracking) ring.snapTo(0f)
                if (animate && !state.tracking) {
                    ring.animateTo(target, tween(style.progressMillis, easing = FastOutSlowInEasing))
                } else {
                    ring.snapTo(target)
                }
            }
            SyncMarkPhase.SUCCESS ->
                if (animate && state.celebrate) ring.animateTo(1f, tween(style.finaleMillis * 3 / 10, easing = FastOutSlowInEasing)) else ring.snapTo(1f)
            SyncMarkPhase.IDLE -> ring.snapTo(1f)
            SyncMarkPhase.ERROR -> Unit
        }
    }

    // Finale of a success: 0 → 1 once; 1 = finished (also the static end state).
    val finale = remember { Animatable(1f) }
    LaunchedEffect(phase, state.key, animate) {
        if (phase == SyncMarkPhase.SUCCESS && state.celebrate && animate) {
            finale.snapTo(0f)
            finale.animateTo(1f, tween(style.finaleMillis, easing = LinearEasing))
        } else {
            finale.snapTo(1f)
        }
    }

    val dim = animateFloatAsState(
        targetValue = if (phase == SyncMarkPhase.RUNNING || phase == SyncMarkPhase.ERROR) style.dimAlpha else 1f,
        animationSpec = if (animate) tween(style.fadeMillis, easing = FastOutSlowInEasing) else snap(),
        label = "dim",
    )
    val errorMix = animateFloatAsState(
        targetValue = if (phase == SyncMarkPhase.ERROR) 1f else 0f,
        animationSpec = if (animate) tween(style.fadeMillis * 2 / 3) else snap(),
        label = "error",
    )

    val pulsePaths = remember(art) { art.pulse.map(::polyline) }
    val a11y = if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier

    Spacer(
        modifier = modifier
            .size(size)
            .testTag(SYNC_MARK_TAG)
            .then(a11y)
            .drawWithCache {
                val r = art.ringRadius
                val c = art.ringCenter
                val view = 2f * (r * 1.075f)
                val s = this.size.minDimension / view
                val unit = 1f / s // px → art units
                val ringW = (style.ringStroke.toPx() + this.size.minDimension * style.strokeScale) * unit
                val pulseW = (style.pulseStroke.toPx() + this.size.minDimension * style.strokeScale) * unit
                val badgeR = r * 0.2f
                val badgeC = Offset(c.x + r * COS45, c.y + r * COS45)
                val check = Path().apply {
                    moveTo(badgeC.x - badgeR * 0.42f, badgeC.y + badgeR * 0.02f)
                    lineTo(badgeC.x - badgeR * 0.12f, badgeC.y + badgeR * 0.32f)
                    lineTo(badgeC.x + badgeR * 0.44f, badgeC.y - badgeR * 0.28f)
                }
                val checkMeasure = PathMeasure().apply { setPath(check, false) }
                val checkPart = Path()
                val dashed = PathEffect.dashPathEffect(floatArrayOf(ringW * 2.2f, ringW * 2.6f))
                onDrawBehind {
                    val t = sweep.value
                    val head = if (t >= 0f) art.sweep.start + (art.sweep.endInclusive - art.sweep.start) * t else Float.NaN
                    val err = errorMix.value
                    val f = finale.value
                    val base = lerp(style.accent, style.muted, err)
                    val canvasW = this.size.width
                    val canvasH = this.size.height

                    withTransform({
                        translate(canvasW / 2f - c.x * s, canvasH / 2f - c.y * s)
                        scale(s, s, pivot = Offset.Zero)
                    }) {
                        // 1 · light behind the parts the pulse passes through
                        if (style.glow && !head.isNaN()) {
                            art.parts.forEach { p ->
                                glow(style.accent, p.glowCenter, p.glowRadius, 0.3f * bump(head, p.litX, style.litWidth))
                            }
                        }
                        // success: one wave from the ring and a short warm light at the centre
                        if (f < 1f) {
                            val e = ((f - 0.22f) / 0.78f).coerceIn(0f, 1f)
                            if (e > 0f) {
                                val fade = (1f - e) * (1f - e)
                                drawCircle(
                                    color = style.accent.copy(alpha = 0.6f * fade),
                                    radius = r * (1f + 0.2f * easeOut(e)),
                                    center = c,
                                    style = Stroke(width = ringW * (2.6f - 1.6f * e)),
                                )
                            }
                            if (style.glow) {
                                val warm = sin((f.coerceIn(0f, 0.7f) / 0.7f) * PI.toFloat())
                                glow(style.accent, Offset(c.x, c.y + r * 0.12f), r * 0.87f, 0.34f * warm)
                            }
                        }

                        // 2 · ring: track + arc of the progress; dashed and muted on error
                        val arc = ring.value
                        if (err > 0f) {
                            drawCircle(style.muted.copy(alpha = 0.7f * err), r, c, style = Stroke(ringW, pathEffect = dashed))
                        }
                        if (err < 1f) {
                            if (arc < 1f) drawCircle(style.track.copy(alpha = (1f - err) * style.track.alpha), r, c, style = Stroke(ringW))
                            drawArc(
                                color = style.accent.copy(alpha = 1f - err),
                                startAngle = -90f,
                                sweepAngle = 360f * arc,
                                useCenter = false,
                                topLeft = Offset(c.x - r, c.y - r),
                                size = Size(r * 2, r * 2),
                                style = Stroke(ringW, cap = if (arc < 1f) StrokeCap.Round else StrokeCap.Butt),
                            )
                            if (phase == SyncMarkPhase.RUNNING && arc < 1f) {
                                val a = (-90f + 360f * arc) * DEG
                                val tip = Offset(c.x + r * cos(a), c.y + r * sin(a))
                                if (style.glow) glow(style.accentHighlight, tip, ringW * 7f, 0.55f)
                                drawCircle(style.accentHighlight, ringW * 0.9f, tip)
                            }
                        }

                        // 3 · the pulse: quiet line + the comet with a bright head
                        val pulseAlpha = if (phase == SyncMarkPhase.RUNNING) 0.32f else 0.85f - 0.45f * err
                        pulsePaths.forEach {
                            drawPath(it, base.copy(alpha = pulseAlpha), style = Stroke(pulseW, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                        if (!head.isNaN()) comet(art, pulsePaths, head, pulseW, style)

                        // 4 · the parts; each one lights up as the pulse passes
                        val d = dim.value
                        art.parts.forEach { p ->
                            val alpha = if (head.isNaN()) d else min(1f, d + (1f - d) * bump(head, p.litX, style.litWidth))
                            drawPath(p.path, base.copy(alpha = alpha))
                        }

                        // 5 · badge: check after success, «!» after an error
                        if (badge && phase == SyncMarkPhase.SUCCESS) {
                            val k = ((f - 0.40f) / 0.25f).coerceIn(0f, 1f)
                            val ck = ((f - 0.52f) / 0.38f).coerceIn(0f, 1f)
                            if (k > 0f) {
                                drawCircle(style.accent, badgeR * easeOut(k), badgeC)
                                if (ck > 0f) {
                                    checkPart.reset()
                                    checkMeasure.getSegment(0f, checkMeasure.length * easeOut(ck), checkPart, true)
                                    drawPath(checkPart, style.onAccent, style = Stroke(badgeR * 0.17f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                                }
                            }
                        }
                        if (badge && err > 0f) {
                            drawCircle(style.errorBadge.copy(alpha = err), badgeR, badgeC)
                            drawCircle(style.muted.copy(alpha = err), badgeR, badgeC, style = Stroke(ringW))
                            val bar = badgeR * 0.14f
                            drawLine(
                                style.onErrorBadge.copy(alpha = err),
                                Offset(badgeC.x, badgeC.y - badgeR * 0.45f),
                                Offset(badgeC.x, badgeC.y + badgeR * 0.12f),
                                bar,
                                StrokeCap.Round,
                            )
                            drawCircle(style.onErrorBadge.copy(alpha = err), bar * 0.62f, Offset(badgeC.x, badgeC.y + badgeR * 0.42f))
                        }
                    }
                }
            },
    )
}

/** True when the user turned animations off (A36 §7 «Удалить анимацию»). */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}

private fun polyline(pts: FloatArray): Path = Path().apply {
    moveTo(pts[0], pts[1])
    var i = 2
    while (i + 1 < pts.size) {
        lineTo(pts[i], pts[i + 1])
        i += 2
    }
}

/** y of the pulse at [x], or null where it runs hidden behind a part. */
internal fun pulseYAt(pulse: List<FloatArray>, x: Float): Float? {
    for (pts in pulse) {
        if (x < pts[0] || x > pts[pts.size - 2]) continue
        var i = 0
        while (i + 3 < pts.size) {
            val x0 = pts[i]
            val x1 = pts[i + 2]
            if (x in x0..x1) {
                val k = if (x1 - x0 < 0.001f) 0f else (x - x0) / (x1 - x0)
                return pts[i + 1] + (pts[i + 3] - pts[i + 1]) * k
            }
            i += 2
        }
    }
    return null
}

private fun DrawScope.comet(art: SyncMarkArt, paths: List<Path>, head: Float, pulseW: Float, style: SyncMarkStyle) {
    val color = style.accentHighlight
    val tail = head - style.trail
    val brush = Brush.horizontalGradient(
        0f to color.copy(alpha = 0f),
        0.7f to color.copy(alpha = 0.55f),
        1f to color,
        startX = tail,
        endX = head,
    )
    paths.forEachIndexed { i, path ->
        val pts = art.pulse[i]
        val left = max(pts[0] - pulseW, tail)
        val right = min(pts[pts.size - 2] + pulseW, head)
        if (right > left) {
            clipRect(left = left, top = -1e5f, right = right, bottom = 1e5f) {
                drawPath(path, brush, style = Stroke(pulseW * 1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
    val y = pulseYAt(art.pulse, head) ?: return
    if (style.glow) glow(color, Offset(head, y), pulseW * 9f, 0.6f)
    drawCircle(color, pulseW * 1.5f, Offset(head, y))
}

private fun DrawScope.glow(color: Color, center: Offset, radius: Float, alpha: Float) {
    if (alpha <= 0.004f || radius <= 0f) return
    drawCircle(
        brush = Brush.radialGradient(
            0f to color.copy(alpha = alpha),
            0.45f to color.copy(alpha = alpha * 0.35f),
            1f to color.copy(alpha = 0f),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

/** How strongly a part at [x] is lit by the pulse head at [head]: a soft bell, 1 at the centre. */
internal fun bump(head: Float, x: Float, width: Float): Float {
    val d = (head - x) / width
    return exp(-d * d)
}

private fun easeOut(t: Float): Float = 1f - (1f - t) * (1f - t) * (1f - t)

private const val MIN_ARC = 0.02f
private val DEG = (PI / 180.0).toFloat()
private val COS45 = cos(PI / 4).toFloat()
