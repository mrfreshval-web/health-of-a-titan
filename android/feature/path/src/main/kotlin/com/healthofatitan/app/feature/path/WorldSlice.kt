package com.healthofatitan.app.feature.path

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.healthofatitan.app.core.ui.component.HotMode
import com.healthofatitan.app.core.ui.component.rememberReduceMotion
import com.healthofatitan.app.core.ui.theme.Geist
import com.healthofatitan.app.core.ui.theme.HotType
import com.healthofatitan.app.core.ui.theme.PathColors
import com.healthofatitan.app.core.ui.theme.PathMotion
import com.healthofatitan.app.domain.path.BalanceKind
import com.healthofatitan.app.domain.path.ComponentKey
import com.healthofatitan.app.domain.path.Element
import com.healthofatitan.app.domain.path.HorizonLight
import com.healthofatitan.app.domain.path.HorizonTitan
import com.healthofatitan.app.domain.path.PartState
import com.healthofatitan.app.domain.path.PathCopy
import com.healthofatitan.app.domain.path.PathLexicon
import com.healthofatitan.app.domain.path.PathOverview
import com.healthofatitan.app.domain.path.Role
import com.healthofatitan.app.domain.path.SlicePart
import com.healthofatitan.app.domain.training.Pt
import com.healthofatitan.app.domain.training.Rig
import com.healthofatitan.app.domain.training.RigJoints
import com.healthofatitan.app.feature.path.slice.ArtRect
import com.healthofatitan.app.feature.path.slice.SliceGeometry
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import androidx.compose.ui.semantics.Role as A11yRole

const val PATH_SLICE_TAG = "path_slice"
const val PATH_HORIZON_TAG = "path_horizon_titan"

fun partTag(key: ComponentKey) = "path_part_${key.id}"

private val G = SliceGeometry

/**
 * «Срез мира» (A55 §1, A58-VP §1–§2): the vault of Sky held by the day Titan, the slabs of Earth,
 * the currents of Water on «Дано», the star «Зачем» and the Titan of the horizon. Drawn in the
 * artboard of 388 × 424 units scaled once; the form tells the state of every part, the colour only
 * repeats it. Sixteen TalkBack nodes; a finger taps the three tiers.
 *
 * @param overview what the slice shows; the empty path engraves everything
 * @param mode the mode of the day: it lights the day Titan only
 * @param why the line «Зачем»: the star is full with it, α 0.35 without it
 * @param still no motion at all (screenshots, Э4 preview)
 */
@Composable
fun WorldSlice(
    overview: PathOverview,
    modifier: Modifier = Modifier,
    mode: HotMode? = null,
    why: String? = overview.goal?.why,
    onTier: (Element) -> Unit = {},
    onPart: (ComponentKey) -> Unit = {},
    onStar: () -> Unit = {},
    still: Boolean = false,
    showDayTitan: Boolean = true,
) {
    val colors = PathColors.current
    val reduce = rememberReduceMotion()
    val animate = PathFeatureFlags.LIVING_SLICE && !still && !reduce && !overview.paused
    val ms by produceState<Long?>(null, animate) {
        value = null
        if (!animate) return@produceState
        val start = withFrameMillis { it }
        while (true) value = withFrameMillis { it } - start
    }
    val measurer = rememberTextMeasurer()
    val heavy = overview.balance.kind == BalanceKind.HEAVY
    val pose = when {
        overview.paused || heavy -> DayPose.ONE_HAND
        overview.balance.kind == BalanceKind.LEVER -> when (overview.balance.lever?.element) {
            Element.SKY -> DayPose.LOOK_SKY
            Element.EARTH -> DayPose.LOOK_EARTH
            Element.WATER -> DayPose.LOOK_WATER
            null -> DayPose.HOLD
        }
        else -> DayPose.HOLD
    }
    val rigPose = if (pose == DayPose.ONE_HAND) G.ONE_HAND else G.HOLD
    val leverDetail = overview.balance.takeIf { it.kind == BalanceKind.LEVER }?.sub
    val description = PathCopy.a11ySlice(overview.goal?.title, overview.balance)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .aspectRatio((G.W / G.H).toFloat())
            .testTag(PATH_SLICE_TAG)
            .semantics {
                isTraversalGroup = true
                contentDescription = description
            },
    ) {
        val unit: Dp = maxWidth / G.W.toFloat()
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(overview.goal?.id) {
                    detectTapGestures { o ->
                        val s = size.width / G.W.toFloat()
                        val x = o.x / s
                        val y = o.y / s
                        if (G.distance(Pt(x.toDouble(), y.toDouble()), G.STAR) < 24.0) onStar()
                        else G.tierAt(y.toDouble())?.let(onTier)
                    }
                },
        ) {
            val s = size.width / G.W.toFloat()
            withTransform({ scale(s, s, pivot = Offset.Zero) }) {
                drawWorld(overview, colors, why, ms)
            }
            drawLabels(overview, colors, measurer, s)
        }
        if (showDayTitan) {
            PathDayTitan(
                pose = pose,
                mode = mode,
                still = still,
                modifier = Modifier
                    .offset(unit * G.LOTTIE_LEFT.toFloat(), unit * G.LOTTIE_TOP.toFloat())
                    .size(unit * G.LOTTIE_SIZE.toFloat()),
            )
        }
        // Fires on the day Titan: above the Lottie (§1.3 layer 11).
        Canvas(Modifier.fillMaxSize()) {
            val s = size.width / G.W.toFloat()
            withTransform({ scale(s, s, pivot = Offset.Zero) }) {
                drawDayFires(overview, colors, Rig.joints(rigPose, front = true))
            }
        }
        A11yNodes(overview, unit, leverDetail, why, onPart, onStar)
    }
}

/** One TalkBack node of the slice. */
private data class A11yNode(val rect: ArtRect, val text: String, val tag: String, val action: (() -> Unit)?)

/** The 17 nodes of §1.6 in reading order: star, 4 arcs, 5 slabs, 5 currents, «Дано», the horizon. */
@Composable
private fun A11yNodes(
    overview: PathOverview,
    unit: Dp,
    leverDetail: String?,
    why: String?,
    onPart: (ComponentKey) -> Unit,
    onStar: () -> Unit,
) {
    val byKey = overview.parts.associateBy { it.key }
    val nodes = buildList {
        add(A11yNode(G.STAR_BOUNDS, PathCopy.a11yWhy(why), "path_star", onStar))
        (G.ARCS + G.SLABS + G.CURRENTS).forEach { key ->
            val part = byKey[key] ?: SlicePart(key, PartState.OFF)
            add(A11yNode(G.bounds(key), PathCopy.a11yPart(part, leverDetail, overview.paused), partTag(key)) { onPart(key) })
        }
        add(A11yNode(G.DANO, PathCopy.a11yGiven(overview.given), "path_given", null))
        overview.horizon?.let { h -> add(A11yNode(G.horizonBounds(h.step), PathCopy.a11yHorizon(h, overview.lightOnly), PATH_HORIZON_TAG, null)) }
    }
    nodes.forEachIndexed { i, n ->
        Box(
            Modifier
                .offset(unit * n.rect.left.toFloat(), unit * n.rect.top.toFloat())
                .size(unit * n.rect.width.toFloat(), unit * n.rect.height.toFloat())
                .clearAndSetSemantics {
                    contentDescription = n.text
                    testTag = n.tag
                    traversalIndex = i.toFloat()
                    val action = n.action
                    if (action != null) {
                        role = A11yRole.Button
                        onClick(label = PathCopy.A11Y_OPEN) {
                            action()
                            true
                        }
                    }
                },
        )
    }
}

private fun PathColors.material(e: Element): Color = when (e) {
    Element.SKY -> sky
    Element.EARTH -> earth
    Element.WATER -> water
}

private fun f(d: Double) = d.toFloat()

private fun o(p: Pt) = Offset(p.x.toFloat(), p.y.toFloat())

/** The whole world in artboard units, layers of §1.3. */
internal fun DrawScope.drawWorld(overview: PathOverview, colors: PathColors, why: String?, ms: Long?) {
    // 1. Sky glow and the depth of Water.
    drawRect(
        Brush.radialGradient(listOf(colors.skyGlow, colors.skyGlow.copy(alpha = 0f)), center = Offset(f(G.CX), 0f), radius = 300f),
        topLeft = Offset.Zero,
        size = Size(f(G.W), f(G.HORIZON_Y)),
    )
    drawRect(
        Brush.verticalGradient(listOf(colors.deep.copy(alpha = 0f), colors.deep), startY = f(G.WATER_DEEP_TOP), endY = f(G.WATER_DEEP_BOTTOM)),
        topLeft = Offset(0f, f(G.WATER_DEEP_TOP)),
        size = Size(f(G.W), f(G.WATER_DEEP_BOTTOM - G.WATER_DEEP_TOP)),
    )
    // 2. The axis «Зачем».
    drawLine(
        colors.star.copy(alpha = colors.axis),
        Offset(f(G.CX), f(G.AXIS_TOP)),
        Offset(f(G.CX), f(G.AXIS_BOTTOM)),
        strokeWidth = 1f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 4f)),
    )
    val parts = overview.parts.associateBy { it.key }
    val animate = ms != null
    // 5 (under the part). The lever halo: breathes three cycles, then stands at 0.30; off on pause.
    if (!overview.paused) {
        parts.values.firstOrNull { it.lever }?.let { lever ->
            val a = PathMotion.leverHalo(ms)
            val w = when (lever.key.element) {
                Element.SKY -> 12.0
                Element.EARTH -> 15.0
                Element.WATER -> 10.0
            }
            strokePart(lever.key, colors.lever.copy(alpha = a), w, StrokeCap.Round)
        }
    }
    // 3–4. Tracks, fills, ticks, practice.
    G.ARCS.plus(G.SLABS).plus(G.CURRENTS).forEach { key ->
        val part = parts[key] ?: SlicePart(key, PartState.OFF)
        val c = colors.material(key.element)
        val width = width(key)
        when (part.state) {
            PartState.OFF, PartState.EXCLUDED -> strokePart(key, c.copy(alpha = colors.engrave), width, cap(key))
            PartState.MARKS -> {
                strokePart(key, c.copy(alpha = colors.track), width, cap(key))
                G.segments(key).take(part.mark.coerceIn(0, 5)).forEach { (a, b) -> strokePart(key, c, width, StrokeCap.Butt, a, b) }
                part.target?.let { t -> tick(key, G.markEnd(key, t), colors.goalTick) }
            }
            PartState.PRACTICE -> {
                strokePart(key, c.copy(alpha = colors.track), width, cap(key))
                val phase = when {
                    !animate -> 0f
                    key.element == Element.SKY -> -(ms!! / 1000f) * 4f
                    key.element == Element.WATER -> -(ms!! / 1000f) * 6f
                    else -> 0f
                }
                val weight = if (animate && key.element == Element.EARTH && (ms!! % 6_000L) < 600L) {
                    0.05f * sin(PI * (ms % 6_000L) / 600.0).toFloat()
                } else {
                    0f
                }
                strokePart(
                    key, c.copy(alpha = (colors.practice + weight).coerceAtMost(1f)), 1.5, StrokeCap.Round,
                    effect = PathEffect.dashPathEffect(floatArrayOf(0.01f, 4.5f), phase),
                )
            }
        }
    }
    // 6. The horizon.
    drawLine(colors.horizonLine.copy(alpha = colors.horizon), Offset(0f, f(G.HORIZON_Y)), Offset(f(G.W), f(G.HORIZON_Y)), strokeWidth = 1f)
    // 7. «Дано» and the brackets of safe versions.
    drawRoundRect(colors.danoFill, Offset(f(G.DANO.left), f(G.DANO.top)), Size(f(G.DANO.width), f(G.DANO.height)), CornerRadius(f(G.DANO_RADIUS)))
    drawRoundRect(
        colors.dano.copy(alpha = 0.5f), Offset(f(G.DANO.left), f(G.DANO.top)), Size(f(G.DANO.width), f(G.DANO.height)),
        CornerRadius(f(G.DANO_RADIUS)), style = Stroke(0.8f),
    )
    parts.values.filter { it.safeVersion != null || it.state == PartState.EXCLUDED }.forEach { p ->
        val end = rightEnd(p.key)
        drawArc(
            colors.dano, startAngle = -90f, sweepAngle = 180f, useCenter = false,
            topLeft = Offset(f(end.x) - 7f, f(end.y) - 7f), size = Size(14f, 14f), style = Stroke(1.2f),
        )
        if (p.state != PartState.EXCLUDED) {
            val x = min(end.x + 7.0, G.DANO.right - 4.0)
            drawLine(
                colors.dano.copy(alpha = 0.5f), Offset(f(x), f(end.y) + 7f), Offset(f(x), f(G.DANO.top)),
                strokeWidth = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 3f)),
            )
        }
    }
    // 8. The Titan of the horizon.
    overview.horizon?.takeIf { !it.meeting }?.let { h ->
        val elements = overview.goal?.components?.map { it.key.element }?.distinct()?.sortedBy { it.ordinal }.orEmpty()
        drawHorizonTitan(h, h.step, overview.lightOnly, colors, elements)
    }
    // 9. The star «Зачем».
    val starAlpha = if (overview.goal != null && !why.isNullOrBlank()) 1f else 0.35f
    drawPath(starPath(G.STAR, G.STAR_OUTER, G.STAR_INNER), colors.star.copy(alpha = starAlpha))
}

private fun width(key: ComponentKey): Double = when (key.element) {
    Element.SKY -> G.ARC_STROKE
    Element.EARTH -> G.SLAB_STROKE
    Element.WATER -> G.CURRENT_STROKE
}

private fun cap(key: ComponentKey): StrokeCap = if (key.element == Element.EARTH) StrokeCap.Butt else StrokeCap.Round

/** The right end of a part: the bracket of a safe version stands there. */
private fun rightEnd(key: ComponentKey): Pt {
    G.ARCS.indexOf(key).takeIf { it >= 0 }?.let { return Pt(G.CX + G.ARC_RADII[it], G.HORIZON_Y - 8.0) }
    G.SLABS.indexOf(key).takeIf { it >= 0 }?.let { return Pt(G.CX + G.slabWidth(it) / 2 + 9.0, G.slabY(it)) }
    val i = G.CURRENTS.indexOf(key)
    return Pt(G.CURRENT_X1 + 6.0, G.currentY(i))
}

/** A part, or its stretch [from]…[to] (fractions of its length from the left end). */
private fun DrawScope.strokePart(
    key: ComponentKey,
    color: Color,
    width: Double,
    cap: StrokeCap,
    from: Double = 0.0,
    to: Double = 1.0,
    effect: PathEffect? = null,
) {
    val stroke = Stroke(f(width), cap = cap, pathEffect = effect)
    G.ARCS.indexOf(key).takeIf { it >= 0 }?.let { i ->
        val r = f(G.ARC_RADII[i])
        drawArc(
            color, startAngle = f(180.0 + 180.0 * from), sweepAngle = f(180.0 * (to - from)), useCenter = false,
            topLeft = Offset(f(G.CX) - r, f(G.HORIZON_Y) - r), size = Size(2 * r, 2 * r), style = stroke,
        )
        return
    }
    G.SLABS.indexOf(key).takeIf { it >= 0 }?.let { i ->
        val w = G.slabWidth(i)
        val x0 = G.CX - w / 2
        drawLine(
            color, Offset(f(x0 + w * from), f(G.slabY(i))), Offset(f(x0 + w * to), f(G.slabY(i))),
            strokeWidth = f(width), cap = cap, pathEffect = effect,
        )
        return
    }
    val i = G.CURRENTS.indexOf(key)
    val span = G.CURRENT_X1 - G.CURRENT_X0
    val path = Path()
    var x = G.CURRENT_X0 + span * from
    val end = G.CURRENT_X0 + span * to
    path.moveTo(f(x), f(G.currentAt(i, x)))
    while (x < end) {
        x = min(x + 2.0, end)
        path.lineTo(f(x), f(G.currentAt(i, x)))
    }
    drawPath(path, color, style = stroke)
}

/** The goal tick: a stroke across the part, 2 units × (thickness + 6), at fraction [at]. */
private fun DrawScope.tick(key: ComponentKey, at: Double, color: Color) {
    val half = (width(key) + 6.0) / 2
    G.ARCS.indexOf(key).takeIf { it >= 0 }?.let { i ->
        val a = (180.0 + 180.0 * at) * PI / 180.0
        val r = G.ARC_RADII[i]
        val p0 = Pt(G.CX + cos(a) * (r - half), G.HORIZON_Y + sin(a) * (r - half))
        val p1 = Pt(G.CX + cos(a) * (r + half), G.HORIZON_Y + sin(a) * (r + half))
        drawLine(color, o(p0), o(p1), strokeWidth = 2f)
        return
    }
    G.SLABS.indexOf(key).takeIf { it >= 0 }?.let { i ->
        val w = G.slabWidth(i)
        val x = G.CX - w / 2 + w * at
        drawLine(color, Offset(f(x), f(G.slabY(i) - half)), Offset(f(x), f(G.slabY(i) + half)), strokeWidth = 2f)
        return
    }
    val i = G.CURRENTS.indexOf(key)
    val x = G.CURRENT_X0 + (G.CURRENT_X1 - G.CURRENT_X0) * at
    val y = G.currentAt(i, x)
    drawLine(color, Offset(f(x), f(y - half)), Offset(f(x), f(y + half)), strokeWidth = 2f)
}

internal fun starPath(c: Pt, outer: Double, inner: Double): Path = Path().apply {
    for (k in 0 until 8) {
        val r = if (k % 2 == 0) outer else inner
        val a = -PI / 2 + k * PI / 4
        val x = f(c.x + cos(a) * r)
        val y = f(c.y + sin(a) * r)
        if (k == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** The segments of the line of light of a rig figure (§2.1): no face, no hair, no muscle. */
internal fun rigSegments(j: RigJoints, front: Boolean): List<Pair<Pt, Pt>> {
    val sw = if (front) Rig.FRONT_SHOULDER else 0.0
    val hw = if (front) Rig.FRONT_HIP else 0.0
    val shN = Pt(j.shoulder.x + sw, j.shoulder.y)
    val shF = Pt(j.shoulder.x - sw, j.shoulder.y)
    val hpN = Pt(j.hip.x + hw, j.hip.y)
    val hpF = Pt(j.hip.x - hw, j.hip.y)
    return listOf(
        j.hip to j.shoulder, j.shoulder to j.neck, shF to shN, hpF to hpN,
        shN to j.elbowN, j.elbowN to j.handN, shF to j.elbowF, j.elbowF to j.handF,
        hpN to j.kneeN, j.kneeN to j.ankleN, j.ankleN to j.toeN,
        hpF to j.kneeF, j.kneeF to j.ankleF, j.ankleF to j.toeF,
    )
}

/**
 * The Titan of the horizon (§2): the same rig as the day Titan, a line of light by its joints,
 * scaled by the week of the calendar only. Seated on a stone on pause (§2.4); a pillar of light
 * when `ed_care` or «Форма» is in the goal (§2.5) — then no body at all.
 */
internal fun DrawScope.drawHorizonTitan(
    h: HorizonTitan,
    week: Int,
    lightOnly: Boolean,
    colors: PathColors,
    elements: List<Element>,
    tint: Color? = null,
) {
    val line = tint ?: colors.horizonLine
    val height = G.horizonHeight(week)
    val k = height / G.RIG_HEIGHT
    val seated = h.seated
    if (seated) {
        val stone = G.STONE
        val a = G.horizonPoint(Pt(100.0 - stone.right, stone.top), week)
        val b = G.horizonPoint(Pt(100.0 - stone.left, stone.bottom), week)
        drawRoundRect(
            colors.dano.copy(alpha = if (colors.isDark) 0.6f else 0.35f), o(a), Size(f(b.x - a.x), f(b.y - a.y)), CornerRadius(f(4.0 * k)),
        )
    }
    val fireKeys = h.fires.map { it.key }
    if (lightOnly) {
        val pillar = if (seated) 0.6 * height else 0.9 * height
        val w = 0.22 * height
        val cx = G.horizonX(week)
        val bottom = if (seated) G.horizonPoint(Pt(50.0, G.STONE.top), week).y else G.HORIZON_Y
        val top = bottom - pillar
        drawRoundRect(
            Brush.radialGradient(
                listOf(line.copy(alpha = 0.35f), line.copy(alpha = 0f)),
                center = Offset(f(cx), f((top + bottom) / 2)),
                radius = f(pillar / 2),
            ),
            topLeft = Offset(f(cx - w / 2), f(top)), size = Size(f(w), f(pillar)), cornerRadius = CornerRadius(f(w / 2)),
        )
        drawLine(line, Offset(f(cx), f(top)), Offset(f(cx), f(bottom)), strokeWidth = 1.2f)
        G.pillarStops(fireKeys).forEach { (key, frac) ->
            val fire = h.fires.first { it.key == key }
            drawFire(Pt(cx, bottom - pillar * frac), key, fire.light, colors)
        }
        return
    }
    val pose = if (seated) G.HZ_SIT else G.HZ_STAND
    val front = !seated
    val raw = Rig.joints(pose, front = front)
    val j = if (seated) mirror(raw) else raw
    fun at(p: Pt) = o(G.horizonPoint(p, week))
    val segments = rigSegments(j, front)
    val head = G.horizonPoint(j.head, week)
    val (lift, squash) = G.nodHead(pose.nod)
    val headR = Rig.HEAD_RADIUS * k
    val headCenter = Offset(f(head.x), f(head.y - lift * k))
    fun figure(color: Color, width: Float, alpha: Float = 1f) {
        segments.forEach { (a, b) -> drawLine(color, at(a), at(b), strokeWidth = width, cap = StrokeCap.Round, alpha = alpha) }
        drawOval(
            color, topLeft = Offset(headCenter.x - f(headR), headCenter.y - f(headR * squash)),
            size = Size(f(2 * headR), f(2 * headR * squash)), style = Stroke(width), alpha = alpha,
        )
    }
    // 1. Halo: two wide transparent strokes in the dark (no blur), one in the light theme.
    if (colors.isDark) {
        figure(line, 6f, 0.12f)
        figure(line, 4f, colors.horizonGlow)
    } else {
        figure(line, 5f, colors.horizonGlow)
    }
    // 2. The line: in the dark a vertical gradient of the goal's elements, each half mixed with the line.
    val brush: Brush? = if (colors.isDark && tint == null && elements.size > 1) {
        Brush.verticalGradient(
            elements.map { lerp(colors.material(it), line, 0.5f) },
            startY = f(G.HORIZON_Y - height),
            endY = f(G.HORIZON_Y),
        )
    } else {
        null
    }
    val lineWidth = 1.6f
    if (brush != null) {
        segments.forEach { (a, b) -> drawLine(brush, at(a), at(b), strokeWidth = lineWidth, cap = StrokeCap.Round) }
        drawOval(
            brush, topLeft = Offset(headCenter.x - f(headR), headCenter.y - f(headR * squash)),
            size = Size(f(2 * headR), f(2 * headR * squash)), style = Stroke(lineWidth),
        )
    } else {
        val solid = if (colors.isDark && elements.size == 1 && tint == null) lerp(colors.material(elements[0]), line, 0.5f) else line
        figure(solid, lineWidth)
    }
    // 3–4. Fires and halos at their anchors.
    if (tint == null) {
        h.fires.forEach { fire -> drawFire(G.horizonPoint(G.anchor(fire.key, j), week), fire.key, fire.light, colors) }
    }
}

private fun mirror(j: RigJoints): RigJoints {
    fun m(p: Pt) = Pt(100.0 - p.x, p.y)
    return RigJoints(
        hip = m(j.hip), shoulder = m(j.shoulder), neck = m(j.neck), head = m(j.head),
        elbowN = m(j.elbowN), handN = m(j.handN), elbowF = m(j.elbowF), handF = m(j.handF),
        kneeN = m(j.kneeN), ankleN = m(j.ankleN), toeN = m(j.toeN),
        kneeF = m(j.kneeF), ankleF = m(j.ankleF), toeF = m(j.toeF),
    )
}

/** A fire (core r 2.4 + glow r 6) or, for a part with no marks, a soft ring r 5 (§2.2 layers 3–4). */
private fun DrawScope.drawFire(at: Pt, key: ComponentKey, light: HorizonLight, colors: PathColors) {
    val c = colors.material(key.element)
    val p = o(at)
    when {
        light == HorizonLight.WITH_PERSON -> Unit
        light == HorizonLight.SHARED || G.haloOnly(key) ->
            drawCircle(c.copy(alpha = if (colors.isDark) 0.45f else 0.6f), radius = 5f, center = p, style = Stroke(1f))
        else -> {
            if (colors.isDark) {
                drawCircle(c.copy(alpha = 0.35f), radius = 6f, center = p)
                drawCircle(lerp(c, Color.White, 0.4f), radius = 2.4f, center = p)
            } else {
                drawCircle(c.copy(alpha = 0.6f), radius = 6f, center = p, style = Stroke(1f))
                drawCircle(c, radius = 2.4f, center = p)
            }
        }
    }
}

/**
 * Fires on the day Titan (§2.2, A57 §4.3): not more than three taken fires — the last taken of
 * each element — and the soft rings of the parts with no marks.
 */
internal fun DrawScope.drawDayFires(overview: PathOverview, colors: PathColors, j: RigJoints) {
    val h = overview.horizon ?: return
    val taken = h.fires.filter { it.light == HorizonLight.WITH_PERSON }
        .groupBy { it.key.element }
        .mapNotNull { (_, v) -> v.lastOrNull() }
        .take(3)
    val shared = h.fires.filter { it.light == HorizonLight.SHARED }
    (taken + shared).forEach { fire ->
        val light = if (fire.light == HorizonLight.WITH_PERSON) HorizonLight.ON_HORIZON else fire.light
        drawFire(G.dayPoint(G.anchor(fire.key, j)), fire.key, light, colors)
    }
}

/** Labels: not scaled with the slice, in sp, limited to 1.3 × at large font sizes (§1.1). */
private fun DrawScope.drawLabels(overview: PathOverview, colors: PathColors, measurer: TextMeasurer, s: Float) {
    val clamp = min(fontScale, 1.3f) / fontScale
    fun overline(size: Float, color: Color) = TextStyle(
        fontFamily = Geist, fontWeight = FontWeight.SemiBold, fontSize = (size * clamp).sp, letterSpacing = (0.14 * size * clamp).sp, color = color,
    )
    fun baseline(text: String, style: TextStyle, x: Float, y: Float, maxWidth: Float = size.width) {
        val r = measurer.measure(
            text, style, overflow = TextOverflow.Ellipsis, maxLines = 1,
            constraints = Constraints(maxWidth = maxWidth.toInt().coerceAtLeast(1)),
        )
        drawText(r, topLeft = Offset(x, y - r.firstBaseline))
    }
    baseline(PathLexicon.WHY.uppercase(), overline(11f, colors.star), f(G.LABEL_WHY.x) * s, f(G.LABEL_WHY.y) * s)
    baseline(PathLexicon.element(Element.SKY).uppercase(), overline(11f, colors.sky), f(G.LABEL_SKY.x) * s, f(G.LABEL_SKY.y) * s)
    baseline(PathLexicon.element(Element.EARTH).uppercase(), overline(11f, colors.earth), f(G.LABEL_EARTH.x) * s, f(G.LABEL_EARTH.y) * s)
    baseline(PathLexicon.element(Element.WATER).uppercase(), overline(11f, colors.water), f(G.LABEL_WATER.x) * s, f(G.LABEL_WATER.y) * s)
    // «Дано»: the overline and one line, cut by an ellipsis.
    val danoX = f(G.DANO.left + 8) * s
    val danoY = f(G.DANO.top + 13) * s
    val label = PathLexicon.GIVEN.uppercase()
    val head = measurer.measure(label, overline(11f, colors.dano))
    drawText(head, topLeft = Offset(danoX, danoY - head.firstBaseline))
    val given = overview.given.joinToString(" · ").ifEmpty { "—" }
    baseline(
        given,
        TextStyle(fontFamily = Geist, fontSize = (12f * clamp).sp, color = colors.dano),
        danoX + head.size.width + 8f * s, danoY,
        maxWidth = f(G.DANO.right - 8) * s - (danoX + head.size.width + 8f * s),
    )
    // The name of the Titan of the horizon, under his feet.
    val h: HorizonTitan = overview.horizon?.takeIf { !it.meeting } ?: return
    val style = HotType.horizonName.copy(fontSize = (13f * clamp).sp, color = colors.horizonLine)
    val r = measurer.measure(
        h.name, style, overflow = TextOverflow.Ellipsis, maxLines = 1,
        constraints = Constraints(maxWidth = (f(G.NAME_MAX_X - G.NAME_MIN_X) * s).toInt()),
    )
    val cx = f(G.horizonX(h.step)) * s
    val x = (cx - r.size.width / 2f).coerceIn(f(G.NAME_MIN_X) * s, f(G.NAME_MAX_X) * s - r.size.width)
    drawText(r, topLeft = Offset(x, f(G.NAME_BASELINE) * s - r.firstBaseline))
}

/** The roles of a goal's parts, for the element rows (seniority by [Role.ordinal]). */
internal fun SlicePart.roleWord(): String? = role?.let(PathLexicon::role)

internal fun roleOrder(r: Role?): Int = r?.ordinal ?: Int.MAX_VALUE
