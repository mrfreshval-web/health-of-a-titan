package com.healthofatitan.app.core.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.healthofatitan.app.core.ui.R
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * How the Titan is lit (A37 §3 «линия света»): a vertical gradient on the figure, a colder and
 * dimmer one on the columns, the glass knockout behind the lines and the glow of the mode.
 *
 * @param glow colour of the soft light around the figure; null = no glow (light theme, crisis)
 * @param solidTitan fill the silhouette with [knockout] at full opacity (a figure against the sun)
 */
@Immutable
data class TitanLight(
    val line: List<Color>,
    val column: List<Color>,
    val knockout: Color,
    val glow: Color?,
    val glowStrength: Float = 1f,
    val solidTitan: Boolean = false,
)

object TitanLights {
    /** The default light of the theme: warm white line, cold columns, no mode. */
    @Composable
    fun default(): TitanLight {
        val t = HotThemeTokens.colors
        return TitanLight(t.titanLight, t.columnLight, t.knockout, if (t.glow) Color(0xFFFFC77A) else null)
    }

    /** Lit by the mode: the line goes from white to the mode colour, the glow is the mode light. */
    @Composable
    fun of(mode: HotMode): TitanLight {
        val t = HotThemeTokens.colors
        if (!t.glow) return default()
        val c = mode.color()
        return TitanLight(
            line = listOf(Color.White, lerpColor(Color.White, c, 0.35f), c),
            column = t.columnLight,
            knockout = t.knockout,
            glow = c,
        )
    }
}

/** Mark v2 geometry the scenes share (brand/mark-v2 README §1, HotMarkGeometry). */
object TitanGeometry {
    const val ART = 2048f
    const val RING_CX = 1024.2f
    const val RING_CY = 1023.0f
    const val RING_R = 940.0f

    /** Left edge of the left column: how far a column travels from its place to the axis. */
    const val COLUMN_LEFT = 338f

    /** Pivot of the Titan's approach (titan.js: translate(1024 1250)). */
    const val PIVOT_X = 1024f
    const val PIVOT_Y = 1250f

    /** Top and bottom of the line-of-light gradient (titan.js). */
    const val LIGHT_TOP = 450f
    const val LIGHT_BOTTOM = 1820f
}

/** The scene's layers as painters: line art for heroes, silhouettes for glyph sizes (< 120 dp). */
class TitanLayers(
    val titan: Painter,
    val titanKnockout: Painter?,
    val left: Painter,
    val right: Painter,
    val leftKnockout: Painter?,
    val rightKnockout: Painter?,
    val glyph: Boolean,
)

@Composable
fun rememberTitanLayers(glyph: Boolean = false): TitanLayers = if (glyph) {
    TitanLayers(
        titan = painterResource(R.drawable.titan_glyph_titan),
        titanKnockout = null,
        left = painterResource(R.drawable.titan_glyph_column_left),
        right = painterResource(R.drawable.titan_glyph_column_right),
        leftKnockout = null,
        rightKnockout = null,
        glyph = true,
    )
} else {
    TitanLayers(
        titan = painterResource(R.drawable.titan_line),
        titanKnockout = painterResource(R.drawable.titan_knockout),
        left = painterResource(R.drawable.titan_column_left_line),
        right = painterResource(R.drawable.titan_column_right_line),
        leftKnockout = painterResource(R.drawable.titan_column_left_knockout),
        rightKnockout = painterResource(R.drawable.titan_column_right_knockout),
        glyph = false,
    )
}

/**
 * The scene of «Титаниум» (A37 §3, §13 `TitanScene(spread, scale, blur, ring, pulse, light)`):
 * mark v2 taken apart — two columns, the Titan, the ring and the pulse — re-lit as a line of light.
 *
 * All motion parameters are lambdas read in the layout/draw phase, so an animation never
 * recomposes the scene: 60 fps with the ~100 KB figure (VectorPainter caches it as a bitmap,
 * graphicsLayer moves and blurs the bitmap).
 *
 * The art square is fitted so that the ring takes [ringFit] of the box (A37 §9.4: the scene sits
 * in the ring at 90 % of its radius, as in the mark).
 *
 * @param spread columns: 1 = the mark, 0.5 = halfway to the axis, 1.04 = the push overshoot
 * @param titanScale / titanLift / titanBlur / titanAlpha: the Titan's approach (lift and blur in art units)
 * @param ring 0…1 progress of the ring from 12 o'clock; [ringTrack] draws the rest
 * @param pulse 0…1 how much of the pulse has drawn (left to right)
 * @param overlay drawn on top in the same box (pills, check marks)
 */
@Composable
fun TitanScene(
    modifier: Modifier = Modifier,
    light: TitanLight = TitanLights.default(),
    layers: TitanLayers = rememberTitanLayers(),
    spread: () -> Float = { 1f },
    titanScale: () -> Float = { 1f },
    titanLift: () -> Float = { 0f },
    titanBlur: () -> Float = { 0f },
    titanAlpha: () -> Float = { 1f },
    columnAlpha: () -> Float = { 1f },
    ring: () -> Float = { 0f },
    ringColor: Color = light.glow ?: light.line.last(),
    ringTrack: Color? = null,
    ringDashed: Boolean = false,
    ringStroke: Float = 11f,
    ringGlow: () -> Float = { 1f },
    pulse: () -> Float = { 0f },
    pulseAlpha: () -> Float = { 1f },
    pulseColor: Color = ringColor,
    ringFit: Float = 0.93f,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val pulsePaths = remember { HotMarkGeometry.PULSE.map(::pulsePath) }
    Box(modifier) {
        // 1 · ring and pulse (behind everything: the pulse runs behind the columns and the Titan)
        androidx.compose.foundation.layout.Spacer(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val fit = ArtFit(size, ringFit)
                    val measures = pulsePaths.map { PathMeasure().apply { setPath(it, false) } }
                    val part = Path()
                    val dash = PathEffect.dashPathEffect(floatArrayOf(ringStroke * 0.5f, ringStroke * 3.2f))
                    onDrawBehind {
                        fit.inArt(this) {
                            val r = TitanGeometry.RING_R
                            val c = Offset(TitanGeometry.RING_CX, TitanGeometry.RING_CY)
                            val box = Size(2 * r, 2 * r)
                            val tl = Offset(c.x - r, c.y - r)
                            if (ringTrack != null) {
                                drawCircle(ringTrack, r, c, style = Stroke(ringStroke * 0.8f))
                            }
                            val arc = ring().coerceIn(0f, 1f)
                            if (arc > 0f) {
                                val g = ringGlow()
                                if (light.glow != null && g > 0f) {
                                    // the ring and the figure are one hero: one soft halo (A37 §9.5)
                                    drawArc(ringColor.copy(alpha = 0.10f * g), -90f, 360f * arc, false, tl, box, style = Stroke(ringStroke * 7f, cap = StrokeCap.Round))
                                    drawArc(ringColor.copy(alpha = 0.22f * g), -90f, 360f * arc, false, tl, box, style = Stroke(ringStroke * 2.8f, cap = StrokeCap.Round))
                                }
                                drawArc(
                                    ringColor, -90f, 360f * arc, false, tl, box,
                                    style = Stroke(
                                        ringStroke,
                                        cap = if (ringDashed) StrokeCap.Round else if (arc < 1f) StrokeCap.Round else StrokeCap.Butt,
                                        pathEffect = if (ringDashed) dash else null,
                                    ),
                                )
                            }
                            val p = pulse().coerceIn(0f, 1f)
                            val pa = pulseAlpha().coerceIn(0f, 1f)
                            if (p > 0f && pa > 0f) {
                                measures.forEach { m ->
                                    part.reset()
                                    m.getSegment(0f, m.length * p, part, true)
                                    if (light.glow != null) {
                                        drawPath(part, pulseColor.copy(alpha = 0.25f * pa), style = Stroke(22f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                                    }
                                    drawPath(part, pulseColor.copy(alpha = pa), style = Stroke(7f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                                }
                            }
                        }
                    }
                },
        )
        // 2 · columns: colder and dimmer than the figure; they move apart from the axis
        androidx.compose.foundation.layout.Spacer(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = columnAlpha().coerceIn(0f, 1f) }
                .drawBehind {
                    val fit = ArtFit(size, ringFit)
                    val d = (1f - spread()) * (TitanGeometry.PIVOT_X - TitanGeometry.COLUMN_LEFT)
                    fit.layer(this, dxArt = d) {
                        layers.leftKnockout?.let { drawTinted(it, it.art(fit), light.knockout) }
                        drawLit(layers.left, fit, light.column)
                    }
                    fit.layer(this, dxArt = -d) {
                        layers.rightKnockout?.let { drawTinted(it, it.art(fit), light.knockout) }
                        drawLit(layers.right, fit, light.column)
                    }
                },
        )
        // 3 · the glow of the figure: a blurred copy in the mode light (dark theme only)
        if (light.glow != null) {
            androidx.compose.foundation.layout.Spacer(
                Modifier
                    .fillMaxSize()
                    .titanTransform(ringFit, titanScale, titanLift, titanAlpha) { 0.6f * light.glowStrength }
                    .graphicsLayer {
                        val px = ArtFit(size, ringFit).scale
                        val r = (if (layers.glyph) 26f else 18f) * px + titanBlur() * px
                        renderEffect = BlurEffect(r.coerceAtLeast(0.5f), r.coerceAtLeast(0.5f), TileMode.Decal)
                    }
                    .drawBehind {
                        val fit = ArtFit(size, ringFit)
                        fit.layer(this) { drawTinted(layers.titan, layers.titan.art(fit), light.glow) }
                    },
            )
        }
        // 4 · the Titan: glass knockout, then the line of light
        androidx.compose.foundation.layout.Spacer(
            Modifier
                .fillMaxSize()
                .titanTransform(ringFit, titanScale, titanLift, titanAlpha) { 1f }
                .graphicsLayer {
                    val b = titanBlur() * ArtFit(size, ringFit).scale
                    renderEffect = if (b > 0.5f) BlurEffect(b, b, TileMode.Decal) else null
                }
                .drawBehind {
                    val fit = ArtFit(size, ringFit)
                    fit.layer(this) {
                        layers.titanKnockout?.let {
                            drawTinted(it, it.art(fit), if (light.solidTitan) light.knockout.copy(alpha = 1f) else light.knockout)
                        }
                        drawLit(layers.titan, fit, light.line)
                    }
                },
        )
        overlay()
    }
}

private fun Modifier.titanTransform(
    ringFit: Float,
    scale: () -> Float,
    lift: () -> Float,
    alpha: () -> Float,
    alphaK: () -> Float,
): Modifier = graphicsLayer {
    val fit = ArtFit(size, ringFit)
    val s = scale()
    scaleX = s
    scaleY = s
    translationY = lift() * fit.scale
    this.alpha = (alpha() * alphaK()).coerceIn(0f, 1f)
    val pivot = fit.toPx(Offset(TitanGeometry.PIVOT_X, TitanGeometry.PIVOT_Y))
    transformOrigin = TransformOrigin(pivot.x / size.width, pivot.y / size.height)
}

/** Maps the 2048 art space into a box so that the ring takes [ringFit] of the smaller side. */
internal class ArtFit(box: Size, ringFit: Float) {
    val scale: Float = min(box.width, box.height) * ringFit / (2f * TitanGeometry.RING_R)
    private val dx = box.width / 2f - TitanGeometry.RING_CX * scale
    private val dy = box.height / 2f - TitanGeometry.RING_CY * scale

    fun toPx(art: Offset) = Offset(dx + art.x * scale, dy + art.y * scale)

    /** Paths in art units (ring, pulse): the canvas is scaled. */
    fun inArt(scope: DrawScope, block: DrawScope.() -> Unit) = with(scope) {
        withTransform({
            translate(dx, dy)
            scale(scale, scale, Offset.Zero)
        }, block)
    }

    /**
     * Painters in px: only translated, never scaled, so a VectorPainter caches its bitmap at the
     * real on-screen size (a 2048 px bitmap per layer would cost 16 MB each).
     */
    fun layer(scope: DrawScope, dxArt: Float = 0f, block: DrawScope.() -> Unit) = with(scope) {
        translate(dx + dxArt * scale, dy, block)
    }

    val artSize: Size get() = Size(TitanGeometry.ART * scale, TitanGeometry.ART * scale)

    /** Point on the ring at [fraction] of a turn from 12 o'clock, in px. */
    fun ringPoint(fraction: Float): Offset {
        val a = Math.toRadians((-90.0 + 360.0 * fraction)).toFloat()
        return toPx(Offset(TitanGeometry.RING_CX + TitanGeometry.RING_R * cos(a), TitanGeometry.RING_CY + TitanGeometry.RING_R * sin(a)))
    }
}

/** Where the ring of a [TitanScene] of [box] size ends at [fraction] (for the Заряд pill). */
fun titanRingPoint(box: Size, fraction: Float, ringFit: Float = 0.93f): Offset = ArtFit(box, ringFit).ringPoint(fraction)

/** Ring radius of a [TitanScene] of [box] size, px. */
fun titanRingRadius(box: Size, ringFit: Float = 0.93f): Float = ArtFit(box, ringFit).scale * TitanGeometry.RING_R

@Suppress("UnusedReceiverParameter")
private fun Painter.art(fit: ArtFit): Size = fit.artSize

private fun DrawScope.drawTinted(p: Painter, size: Size, color: Color) {
    with(p) { draw(size, colorFilter = ColorFilter.tint(color)) }
}

/** The line of light: the white layer through a vertical gradient (SrcIn in an offscreen layer). */
private fun DrawScope.drawLit(p: Painter, fit: ArtFit, colors: List<Color>) {
    val size = fit.artSize
    if (colors.distinct().size == 1) {
        drawTinted(p, size, colors.first())
        return
    }
    val k = fit.scale
    drawIntoCanvas { canvas ->
        canvas.saveLayer(Rect(Offset.Zero, size), Paint())
        drawTinted(p, size, Color.White)
        drawRect(
            Brush.verticalGradient(colors, startY = TitanGeometry.LIGHT_TOP * k, endY = TitanGeometry.LIGHT_BOTTOM * k),
            size = size,
            blendMode = BlendMode.SrcIn,
        )
        canvas.restore()
    }
}

private fun pulsePath(pts: FloatArray): Path = Path().apply {
    moveTo(pts[0], pts[1])
    var i = 2
    while (i + 1 < pts.size) {
        lineTo(pts[i], pts[i + 1])
        i += 2
    }
}

internal fun lerpColor(a: Color, b: Color, t: Float): Color = androidx.compose.ui.graphics.lerp(a, b, t)
