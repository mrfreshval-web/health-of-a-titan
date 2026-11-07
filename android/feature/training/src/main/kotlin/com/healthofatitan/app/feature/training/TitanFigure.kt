package com.healthofatitan.app.feature.training

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.domain.training.PoseTimeline
import com.healthofatitan.app.domain.training.Pt
import com.healthofatitan.app.domain.training.Rig
import com.healthofatitan.app.domain.training.TitanPose

/**
 * The Titan doing the exercise (A40 §6): the 12-angle skeleton of [TitanPose] drawn as one
 * silhouette in «линия света» (A37 §3) — a light outline over the glass knockout, the far limbs
 * dimmer behind the body, the floor as a hairline. [animate] false (or «Удалить анимацию»)
 * shows the key pose ([PoseTimeline.still]).
 */
@Composable
fun TitanExercise(
    timeline: PoseTimeline,
    front: Boolean,
    light: List<Color>,
    glow: Color?,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    description: String? = null,
) {
    if (!animate || timeline.frames.size < 2) {
        TitanFigure(timeline.still, front, light, glow, modifier, description)
        return
    }
    val ms by produceState(0L, timeline) {
        val start = withFrameMillis { it }
        while (true) {
            value = withFrameMillis { it } - start
        }
    }
    TitanFigure(timeline.at(ms), front, light, glow, modifier, description)
}

@Composable
fun TitanFigure(
    pose: TitanPose,
    front: Boolean,
    light: List<Color>,
    glow: Color?,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    val tokens = HotThemeTokens.colors
    val knockout = tokens.knockout
    val floor = tokens.hairline
    val withGlow = glow?.takeIf { tokens.glow }
    Canvas(modifier.semantics { if (description != null) contentDescription = description }) {
        val unit = size.minDimension / BOX
        val ox = (size.width - BOX * unit) / 2f
        val oy = (size.height - BOX * unit) / 2f
        fun o(p: Pt) = Offset(ox + p.x.toFloat() * unit, oy + p.y.toFloat() * unit)
        val j = Rig.joints(pose, front)

        if (withGlow != null) {
            drawCircle(
                Brush.radialGradient(listOf(withGlow.copy(alpha = 0.22f), Color.Transparent), center = o(Pt(50.0, 50.0)), radius = 46f * unit),
                radius = 46f * unit,
                center = o(Pt(50.0, 50.0)),
            )
        }
        drawLine(floor, o(Pt(8.0, Rig.FLOOR_Y)), o(Pt(92.0, Rig.FLOOR_Y)), strokeWidth = 0.6f * unit)

        val brush = Brush.verticalGradient(light, startY = oy + 8f * unit, endY = oy + Rig.FLOOR_Y.toFloat() * unit)
        val rim = RIM * unit

        // far side: dimmer, behind the body
        val far = listOf(
            Seg(j.shoulder.side(-1, front, Rig.FRONT_SHOULDER), j.elbowF, Rig.W_UPPER_ARM), Seg(j.elbowF, j.handF, Rig.W_FOREARM),
            Seg(j.hip.side(-1, front, Rig.FRONT_HIP), j.kneeF, Rig.W_THIGH), Seg(j.kneeF, j.ankleF, Rig.W_SHIN), Seg(j.ankleF, j.toeF, Rig.W_FOOT),
        )
        silhouette(far, null, null, brush, knockout, unit, rim, alpha = FAR_ALPHA, o = ::o)

        // body and the near side
        val near = listOf(
            Seg(j.shoulder, j.neck, Rig.W_NECK),
            Seg(j.hip.side(1, front, Rig.FRONT_HIP), j.kneeN, Rig.W_THIGH), Seg(j.kneeN, j.ankleN, Rig.W_SHIN), Seg(j.ankleN, j.toeN, Rig.W_FOOT),
            Seg(j.shoulder.side(1, front, Rig.FRONT_SHOULDER), j.elbowN, Rig.W_UPPER_ARM), Seg(j.elbowN, j.handN, Rig.W_FOREARM),
        )
        val c = Rig.spineControl(pose, j)
        val torso = Path().apply {
            moveTo(o(j.hip).x, o(j.hip).y)
            quadraticTo(o(c).x, o(c).y, o(j.shoulder).x, o(j.shoulder).y)
        }
        silhouette(near, torso, j.head, brush, knockout, unit, rim, alpha = 1f, o = ::o)
    }
}

private class Seg(val a: Pt, val b: Pt, val w: Double)

private fun Pt.side(sign: Int, front: Boolean, half: Double): Pt = if (front) Pt(x + sign * half, y) else this

/**
 * One silhouette: every capsule in light, then every capsule again in the knockout a rim
 * thinner, so the outline stays only on the outside of the merged shape.
 */
private fun DrawScope.silhouette(
    segs: List<Seg>,
    torso: Path?,
    head: Pt?,
    light: Brush,
    knockout: Color,
    unit: Float,
    rim: Float,
    alpha: Float,
    o: (Pt) -> Offset,
) {
    fun pass(extra: Float, paint: (Float, Offset, Offset) -> Unit, path: (Float) -> Unit, circle: (Float) -> Unit) {
        segs.forEach { paint(it.w.toFloat() * unit + extra, o(it.a), o(it.b)) }
        if (torso != null) path(Rig.W_TORSO.toFloat() * unit + extra)
        if (head != null) circle(Rig.HEAD_RADIUS.toFloat() * unit + extra / 2f)
    }
    pass(
        rim * 2f,
        { w, a, b -> drawLine(light, a, b, strokeWidth = w, cap = StrokeCap.Round, alpha = alpha) },
        { w -> drawPath(torso!!, light, alpha = alpha, style = Stroke(width = w, cap = StrokeCap.Round)) },
        { r -> drawCircle(light, radius = r, center = o(head!!), alpha = alpha) },
    )
    pass(
        0f,
        { w, a, b -> drawLine(knockout, a, b, strokeWidth = w, cap = StrokeCap.Round) },
        { w -> drawPath(torso!!, knockout, style = Stroke(width = w, cap = StrokeCap.Round)) },
        { r -> drawCircle(knockout, radius = r, center = o(head!!)) },
    )
}

private const val BOX = 100f
private const val RIM = 0.9f
private const val FAR_ALPHA = 0.45f
