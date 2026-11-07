package com.healthofatitan.app.feature.path.slice

import com.healthofatitan.app.domain.path.ComponentKey
import com.healthofatitan.app.domain.path.Element
import com.healthofatitan.app.domain.training.Pt
import com.healthofatitan.app.domain.training.Rig
import com.healthofatitan.app.domain.training.RigJoints
import com.healthofatitan.app.domain.training.TitanPose
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin

/** A rectangle of the artboard: left, top, right, bottom in artboard units. */
data class ArtRect(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    val width: Double get() = right - left
    val height: Double get() = bottom - top
}

/**
 * Geometry of «Срез мира» (A58-VP §1.2, §2.3): an artboard of 388 × 424 units, scaled once on
 * entry to the `DrawScope` by `s = width / 388`. Pure numbers — the drawing, the 16 TalkBack nodes
 * and the touch zones read the same values, and [SliceGeometryTest] checks the hands of the day
 * Titan really hold the inner arc.
 */
object SliceGeometry {
    const val W = 388.0
    const val H = 424.0
    const val CX = 194.0
    const val HORIZON_Y = 262.0

    /** Side margin of the slice: the only element of «Путь» with 12 dp instead of the edge. */
    const val MARGIN_DP = 12

    // «Зачем»: the star and its axis.
    val STAR = Pt(194.0, 28.0)
    const val STAR_OUTER = 14.0
    const val STAR_INNER = 3.5
    const val AXIS_TOP = 44.0
    const val AXIS_BOTTOM = 404.0

    // Sky: four half circles centred on the horizon, inner to outer.
    val ARC_CENTER = Pt(CX, HORIZON_Y)
    val ARCS: List<ComponentKey> = listOf(ComponentKey.ATTENTION, ComponentKey.MASTERY, ComponentKey.CLARITY, ComponentKey.OUTLOOK)
    val ARC_RADII = listOf(123.0, 137.0, 151.0, 165.0)
    const val ARC_STROKE = 5.0

    // Earth: five slabs, top to bottom.
    val SLABS: List<ComponentKey> = listOf(ComponentKey.CARDIO, ComponentKey.STRENGTH, ComponentKey.MOBILITY, ComponentKey.BODY, ComponentKey.FUEL)
    const val SLAB_STROKE = 8.0

    fun slabY(i: Int): Double = 280.0 + 11.0 * i

    fun slabWidth(i: Int): Double = 300.0 + 16.0 * i

    // Water: five currents, top to bottom.
    val CURRENTS: List<ComponentKey> = listOf(ComponentKey.SLEEP, ComponentKey.REST, ComponentKey.FEELINGS, ComponentKey.PEOPLE, ComponentKey.AFLOAT)
    const val CURRENT_STROKE = 3.0
    const val CURRENT_X0 = 14.0
    const val CURRENT_X1 = 374.0
    const val CURRENT_HALF_PERIOD = 18.0
    const val CURRENT_AMPLITUDE = 2.5

    fun currentY(i: Int): Double = 346.0 + 12.0 * i

    /** y of current [i] at [x], with the phase of the wave (0…1 of a period). */
    fun currentAt(i: Int, x: Double, phase: Double = 0.0): Double =
        currentY(i) + CURRENT_AMPLITUDE * sin(PI * (x - CURRENT_X0) / CURRENT_HALF_PERIOD + 2.0 * PI * phase)

    const val WATER_DEEP_TOP = 320.0
    const val WATER_DEEP_BOTTOM = 402.0

    // «Дано».
    val DANO = ArtRect(24.0, 404.0, 364.0, 422.0)
    const val DANO_RADIUS = 4.0

    // Labels of the tiers: baselines.
    val LABEL_SKY = Pt(16.0, 150.0)
    val LABEL_EARTH = Pt(16.0, 273.0)
    val LABEL_WATER = Pt(16.0, 340.0)
    val LABEL_WHY = Pt(214.0, 32.0)

    // The day Titan: the Lottie square, 1 rig unit = 1.3 artboard units, rig floor y 92 → horizon.
    const val DAY_LEFT = 129.0
    const val DAY_TOP = 142.4
    const val DAY_SIZE = 130.0
    const val DAY_SCALE = 1.3

    fun dayPoint(p: Pt): Pt = Pt(DAY_LEFT + p.x * DAY_SCALE, DAY_TOP + p.y * DAY_SCALE)

    /** Height of the rig figure, units of the rig (canon of the mark v2). */
    const val RIG_HEIGHT = 83.6

    /** The Titan of the horizon by the week of the stage (§2.3): no metric enters the formula. */
    fun horizonX(week: Int): Double = if (week >= MEET_WEEK) CX else 292.0 - 5.5 * week

    fun horizonHeight(week: Int): Double = if (week >= MEET_WEEK) DAY_SCALE * RIG_HEIGHT else 46.0 + 2.0 * week

    const val MEET_WEEK = 12

    /** A point of the rig drawn as the Titan of the horizon at [week]: feet on the horizon. */
    fun horizonPoint(p: Pt, week: Int): Pt {
        val k = horizonHeight(week) / RIG_HEIGHT
        return Pt(horizonX(week) + (p.x - 50.0) * k, HORIZON_Y + (p.y - Rig.FLOOR_Y) * k)
    }

    /** Name under his feet: baseline y 274, centred on the figure within x 200…376. */
    const val NAME_BASELINE = 274.0
    const val NAME_MIN_X = 200.0
    const val NAME_MAX_X = 376.0

    /** Touch zones of the tiers (§1.6): Sky to the horizon, Earth 262…330, Water 330…402. */
    fun tierAt(y: Double): Element? = when {
        y < 0.0 || y > WATER_DEEP_BOTTOM -> null
        y < HORIZON_Y -> Element.SKY
        y < 330.0 -> Element.EARTH
        else -> Element.WATER
    }

    fun tierBounds(e: Element): ArtRect = when (e) {
        Element.SKY -> ArtRect(0.0, 0.0, W, HORIZON_Y)
        Element.EARTH -> ArtRect(0.0, HORIZON_Y, W, 330.0)
        Element.WATER -> ArtRect(0.0, 330.0, W, WATER_DEEP_BOTTOM)
    }

    /** Bounds of a part for its TalkBack node: an arc is the band of 14 units at its top. */
    fun bounds(key: ComponentKey): ArtRect {
        ARCS.indexOf(key).takeIf { it >= 0 }?.let { i ->
            val r = ARC_RADII[i]
            return ArtRect(CX - r, HORIZON_Y - r - 7.0, CX + r, HORIZON_Y - r + 7.0)
        }
        SLABS.indexOf(key).takeIf { it >= 0 }?.let { i ->
            val half = slabWidth(i) / 2
            return ArtRect(CX - half, slabY(i) - 5.5, CX + half, slabY(i) + 5.5)
        }
        val i = CURRENTS.indexOf(key)
        return ArtRect(CURRENT_X0, currentY(i) - 6.0, CURRENT_X1, currentY(i) + 6.0)
    }

    val STAR_BOUNDS = ArtRect(STAR.x - STAR_OUTER, STAR.y - STAR_OUTER, STAR.x + STAR_OUTER, STAR.y + STAR_OUTER)

    fun horizonBounds(week: Int): ArtRect {
        val h = horizonHeight(week)
        val x = horizonX(week)
        return ArtRect(x - h * 0.3, HORIZON_Y - h, x + h * 0.3, HORIZON_Y)
    }

    /** Length of a part, for the five equal segments of the marks (§1.2 «Длины деталей»). */
    fun length(key: ComponentKey): Double {
        ARCS.indexOf(key).takeIf { it >= 0 }?.let { return PI * ARC_RADII[it] }
        SLABS.indexOf(key).takeIf { it >= 0 }?.let { return slabWidth(it) }
        return CURRENT_X1 - CURRENT_X0
    }

    /** The five mark segments of a part as fractions [from, to] of its length, gaps of 2 units. */
    fun segments(key: ComponentKey): List<Pair<Double, Double>> {
        val len = length(key)
        val gap = SEGMENT_GAP / len
        val seg = (1.0 - gap * 4) / 5
        return (0 until 5).map { i -> (i * (seg + gap)) to (i * (seg + gap) + seg) }
    }

    const val SEGMENT_GAP = 2.0

    /** The fraction of a part where mark [m] (1…5) ends: the goal tick stands there. */
    fun markEnd(key: ComponentKey, m: Int): Double = segments(key)[(m - 1).coerceIn(0, 4)].second

    // Poses of «Путь» (§3.1). The day Titan is Lottie; these are the same key frames for anchors.
    val HOLD = TitanPose(
        rootX = 50.0, rootY = 48.8, torso = 0.0, head = 0.0, nod = 4.0,
        armN = 40.0 to 10.0, armF = 320.0 to 350.0,
        legN = Triple(174.0, 180.0, 135.0), legF = Triple(186.0, 180.0, 225.0),
    )
    val ONE_HAND = TitanPose(
        rootX = 49.0, rootY = 48.8, torso = 3.0, head = -4.0, nod = -3.0,
        armN = 40.0 to 10.0, armF = 200.0 to 80.0,
        legN = Triple(174.0, 180.0, 135.0), legF = Triple(186.0, 180.0, 225.0),
    )
    val HZ_STAND = TitanPose(
        rootX = 50.0, rootY = 48.8, torso = 0.0, head = 0.0,
        armN = 165.0 to 170.0, armF = 195.0 to 190.0,
        legN = Triple(176.0, 180.0, 135.0), legF = Triple(184.0, 180.0, 225.0),
    )

    /** Side view, mirrored (faces left, to the Titan): forearms on the knees, the head a little up. */
    val HZ_SIT = TitanPose(
        rootX = 50.0, rootY = 71.0, torso = 5.0, head = 8.0,
        armN = 160.0 to 100.0, armF = 158.0 to 98.0,
        legN = Triple(90.0, 180.0, 90.0), legF = Triple(92.0, 180.0, 92.0),
    )
    val MEET_OPEN = HZ_STAND.copy(armN = 130.0 to 120.0, armF = 230.0 to 240.0)

    /** The stone of the waiting pose, rig units: x 36…58, y 74…92, radius 4. */
    val STONE = ArtRect(36.0, 74.0, 58.0, 92.0)

    /** Components with no fire: a soft ring on both figures, never moved, never counted (§2.2). */
    fun haloOnly(key: ComponentKey): Boolean = !key.hasMarks

    /** The anchor of a component on a rig figure (§2.2): never the waist or the belly. */
    fun anchor(key: ComponentKey, j: RigJoints): Pt = when (key) {
        ComponentKey.ATTENTION -> Pt(j.head.x, j.head.y - 9.0)
        ComponentKey.MASTERY -> j.handN
        ComponentKey.CLARITY -> j.handF
        ComponentKey.OUTLOOK -> j.head
        ComponentKey.CARDIO -> lerp(j.shoulder, j.hip, 0.15)
        ComponentKey.STRENGTH -> j.elbowN
        ComponentKey.MOBILITY -> j.kneeN
        ComponentKey.BODY -> j.kneeF
        ComponentKey.FUEL -> j.elbowF
        ComponentKey.SLEEP -> j.neck
        ComponentKey.REST -> j.ankleF
        ComponentKey.FEELINGS -> lerp(j.shoulder, j.hip, 0.35)
        ComponentKey.PEOPLE -> j.hip
        ComponentKey.AFLOAT -> j.ankleN
    }

    /**
     * The pillar of light (§2.5) holds the fires on its axis at equal intervals, Sky on top, Earth
     * in the middle, Water below: no anatomical anchor at all. Returns fractions of the height
     * from the feet (0) to the top (1).
     */
    fun pillarStops(keys: List<ComponentKey>): Map<ComponentKey, Double> {
        val ordered = keys.sortedWith(compareBy({ it.element.ordinal }, { it.ordinal }))
        val n = ordered.size
        return ordered.mapIndexed { i, k -> k to (0.85 - 0.7 * (i + 1) / (n + 1)) }.toMap()
    }

    fun distance(a: Pt, b: Pt): Double = hypot(a.x - b.x, a.y - b.y)

    fun lerp(a: Pt, b: Pt, t: Double): Pt = Pt(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
}
