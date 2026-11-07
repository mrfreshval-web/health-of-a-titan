package com.healthofatitan.app.domain.training

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * One key frame of the Titan rig (A40 §6.2, prototype `docs/design/training/src/pose.js`): twelve
 * segment angles plus the hip position. Angles are absolute, in degrees, clockwise from «up»
 * (0 = up, 90 = forward, 180 = down); the figure faces right in the side view. Coordinates live
 * in a 100 × 100 box with the floor at [Rig.FLOOR_Y]. N = the near side (bright), F = the far
 * side (dim). The same data drives the Compose view and the mockups.
 */
data class TitanPose(
    val rootX: Double,
    val rootY: Double,
    val torso: Double,
    val head: Double,
    /** Near arm: upper arm, forearm. */
    val armN: Pair<Double, Double>,
    val armF: Pair<Double, Double>,
    /** Near leg: thigh, shin, foot. */
    val legN: Triple<Double, Double, Double>,
    val legF: Triple<Double, Double, Double>,
    /**
     * Bend of the spine, share of the torso length: + arches the back (cat), − sags it (cow,
     * a breath into the belly). Drawn as a curve through the torso; not a joint.
     */
    val spine: Double = 0.0,
) {
    /** The other side of a one-sided exercise: near and far limbs change places. */
    fun mirrorSides(): TitanPose = copy(armN = armF, armF = armN, legN = legF, legF = legN)

    /** The twelve joint angles in a fixed order (torso, head, arms, legs). */
    val angles: List<Double>
        get() = listOf(torso, head, armN.first, armN.second, armF.first, armF.second) +
            listOf(legN.first, legN.second, legN.third, legF.first, legF.second, legF.third)

    companion object {
        const val JOINTS = 12

        /** The calm standing pose: the poster for exercises that have no keyframes yet (A40 §12). */
        val STAND = TitanPose(
            rootX = 50.0, rootY = 49.0, torso = 0.0, head = 0.0,
            armN = 178.0 to 182.0, armF = 182.0 to 178.0,
            legN = Triple(180.0, 180.0, 90.0), legF = Triple(183.0, 178.0, 92.0),
        )

        /** Linear blend on the shortest arc for every angle. [t] 0…1. */
        fun lerp(a: TitanPose, b: TitanPose, t: Double): TitanPose {
            fun l(x: Double, y: Double) = x + (y - x) * t
            fun ang(x: Double, y: Double): Double = x + shortest(x, y) * t
            fun p(x: Pair<Double, Double>, y: Pair<Double, Double>) = ang(x.first, y.first) to ang(x.second, y.second)
            fun tr(x: Triple<Double, Double, Double>, y: Triple<Double, Double, Double>) =
                Triple(ang(x.first, y.first), ang(x.second, y.second), ang(x.third, y.third))
            return TitanPose(
                rootX = l(a.rootX, b.rootX),
                rootY = l(a.rootY, b.rootY),
                torso = ang(a.torso, b.torso),
                head = ang(a.head, b.head),
                armN = p(a.armN, b.armN),
                armF = p(a.armF, b.armF),
                legN = tr(a.legN, b.legN),
                legF = tr(a.legF, b.legF),
                spine = l(a.spine, b.spine),
            )
        }

        /** Signed shortest turn from [from] to [to], degrees in (−180, 180]. */
        fun shortest(from: Double, to: Double): Double {
            var d = (to - from) % 360.0
            if (d > 180.0) d -= 360.0
            if (d <= -180.0) d += 360.0
            return d
        }
    }
}

/** Points of the rig in the 100-unit box (y grows down). */
data class Pt(val x: Double, val y: Double)

data class RigJoints(
    val hip: Pt,
    val shoulder: Pt,
    val neck: Pt,
    val head: Pt,
    val elbowN: Pt, val handN: Pt,
    val elbowF: Pt, val handF: Pt,
    val kneeN: Pt, val ankleN: Pt, val toeN: Pt,
    val kneeF: Pt, val ankleF: Pt, val toeF: Pt,
) {
    val all: List<Pt> get() = listOf(hip, shoulder, neck, head, elbowN, handN, elbowF, handF, kneeN, ankleN, toeN, kneeF, ankleF, toeF)
}

/** Forward kinematics of the rig; segment lengths follow the Titan of mark v2 (pose.js `L`). */
object Rig {
    const val TORSO = 26.0
    const val NECK = 3.6
    const val HEAD = 5.4
    const val UPPER_ARM = 14.0
    const val FOREARM = 13.0
    const val THIGH = 21.0
    const val SHIN = 21.0
    const val FOOT = 6.0
    const val HEAD_RADIUS = 5.6
    const val FLOOR_Y = 92.0

    /** Capsule widths (pose.js `W`), in box units. */
    const val W_TORSO = 9.5
    const val W_UPPER_ARM = 5.2
    const val W_FOREARM = 4.4
    const val W_THIGH = 6.6
    const val W_SHIN = 5.2
    const val W_FOOT = 3.4
    const val W_NECK = 3.6

    private fun step(o: Pt, deg: Double, len: Double): Pt {
        val a = deg * PI / 180.0
        return Pt(o.x + sin(a) * len, o.y - cos(a) * len)
    }

    /** Half widths of the shoulders and the pelvis in the front view (side view: 0). */
    const val FRONT_SHOULDER = 6.5
    const val FRONT_HIP = 4.0

    /**
     * Joint positions. In the [front] view (A40 `animation.view: front | top`) the near limbs hang
     * from the right shoulder and hip, the far ones from the left; in the side view from one point.
     */
    fun joints(p: TitanPose, front: Boolean = false): RigJoints {
        val hip = Pt(p.rootX, p.rootY)
        val sh = step(hip, p.torso, TORSO)
        val neck = step(sh, p.head, NECK)
        val head = step(neck, p.head, HEAD)
        val sw = if (front) FRONT_SHOULDER else 0.0
        val hw = if (front) FRONT_HIP else 0.0
        val eN = step(Pt(sh.x + sw, sh.y), p.armN.first, UPPER_ARM)
        val eF = step(Pt(sh.x - sw, sh.y), p.armF.first, UPPER_ARM)
        val kN = step(Pt(hip.x + hw, hip.y), p.legN.first, THIGH)
        val kF = step(Pt(hip.x - hw, hip.y), p.legF.first, THIGH)
        val aN = step(kN, p.legN.second, SHIN)
        val aF = step(kF, p.legF.second, SHIN)
        return RigJoints(
            hip = hip, shoulder = sh, neck = neck, head = head,
            elbowN = eN, handN = step(eN, p.armN.second, FOREARM),
            elbowF = eF, handF = step(eF, p.armF.second, FOREARM),
            kneeN = kN, ankleN = aN, toeN = step(aN, p.legN.third, FOOT),
            kneeF = kF, ankleF = aF, toeF = step(aF, p.legF.third, FOOT),
        )
    }

    /** Control point of the spine curve (quadratic through hip and shoulder), see [TitanPose.spine]. */
    fun spineControl(p: TitanPose, j: RigJoints): Pt {
        val mid = Pt((j.hip.x + j.shoulder.x) / 2, (j.hip.y + j.shoulder.y) / 2)
        return step(mid, p.torso - 90.0, p.spine * TORSO)
    }

    /** Knee flexion of a leg, degrees (0 = straight, + = shin behind the thigh). */
    fun kneeFlexion(leg: Triple<Double, Double, Double>): Double = TitanPose.shortest(leg.first, leg.second)

    /** Elbow flexion of an arm, degrees (0 = straight, + = forearm in front of the upper arm). */
    fun elbowFlexion(arm: Pair<Double, Double>): Double = -TitanPose.shortest(arm.first, arm.second)

    /** Anatomical limits the validator and the tests enforce (A40 §6.3). [Оценка] Agent 4/12. */
    const val KNEE_MIN = -10.0
    const val KNEE_MAX = 165.0
    const val ELBOW_MIN = -10.0
    const val ELBOW_MAX = 160.0

    /** Problems of a pose: joints out of range or a body part through the floor. */
    fun problems(p: TitanPose, front: Boolean = false): List<String> {
        val out = mutableListOf<String>()
        listOf("leg_n" to p.legN, "leg_f" to p.legF).forEach { (n, leg) ->
            val k = kneeFlexion(leg)
            if (k < KNEE_MIN || k > KNEE_MAX) out += "$n knee ${k.toInt()}°"
        }
        listOf("arm_n" to p.armN, "arm_f" to p.armF).forEach { (n, arm) ->
            val e = elbowFlexion(arm)
            if (e < ELBOW_MIN || e > ELBOW_MAX) out += "$n elbow ${e.toInt()}°"
        }
        val j = joints(p, front)
        val lowest = j.all.maxOf { it.y }
        if (lowest > FLOOR_Y + 2.5) out += "below the floor by ${"%.1f".format(lowest - FLOOR_Y)}"
        if (j.head.y + HEAD_RADIUS > FLOOR_Y + 2.5) out += "head through the floor"
        return out
    }
}

/** Keyframes of every exercise (content/training/poses.yaml → assets/content/training/poses.json). */
class PoseLibrary(private val byExercise: Map<String, Map<String, TitanPose>>) {
    fun poses(exerciseId: String): Map<String, TitanPose> = byExercise[exerciseId].orEmpty()

    val exerciseIds: Set<String> get() = byExercise.keys

    companion object {
        val EMPTY = PoseLibrary(emptyMap())

        fun fromTree(m: Map<String, Any?>): PoseLibrary {
            val all = m.obj("poses").orEmpty().mapNotNull { (id, v) ->
                @Suppress("UNCHECKED_CAST")
                val frames = v as? Map<String, Any?> ?: return@mapNotNull null
                id to frames.mapNotNull { (key, f) ->
                    @Suppress("UNCHECKED_CAST")
                    (f as? Map<String, Any?>)?.let(::pose)?.let { key to it }
                }.toMap()
            }.toMap()
            return PoseLibrary(all)
        }

        fun pose(m: Map<String, Any?>): TitanPose? {
            val root = m.doubles("root").takeIf { it.size == 2 } ?: return null
            val torso = (m["torso"] as? Number)?.toDouble() ?: return null
            val armN = m.doubles("arm_n").takeIf { it.size == 2 } ?: return null
            val legN = m.doubles("leg_n").takeIf { it.size == 3 } ?: return null
            val armF = m.doubles("arm_f").takeIf { it.size == 2 } ?: armN
            val legF = m.doubles("leg_f").takeIf { it.size == 3 } ?: legN
            return TitanPose(
                rootX = root[0], rootY = root[1], torso = torso,
                head = (m["head"] as? Number)?.toDouble() ?: torso,
                armN = armN[0] to armN[1], armF = armF[0] to armF[1],
                legN = Triple(legN[0], legN[1], legN[2]), legF = Triple(legF[0], legF[1], legF[2]),
                spine = (m["spine"] as? Number)?.toDouble() ?: 0.0,
            )
        }
    }
}

/**
 * Which frame to draw at a given moment (A40 §6.2: frames follow the phases of a repetition in
 * the exercise tempo, «3-0-1-0» = 3 s down, 1 s up). Pure, so the timing is tested without UI.
 */
class PoseTimeline(
    val frames: List<TitanPose>,
    /** Transition into frame i+1 (ms), then a hold (ms), cyclic. */
    private val moves: List<Long>,
    private val holds: List<Long>,
) {
    val cycleMs: Long = moves.sum() + holds.sum()

    /** The pose shown when animation is off (A40 §6.2: the final frame is the key pose). */
    val still: TitanPose get() = frames.getOrElse(1) { frames.first() }

    fun at(ms: Long): TitanPose {
        if (frames.size < 2 || cycleMs <= 0L) return frames.first()
        var t = ((ms % cycleMs) + cycleMs) % cycleMs
        for (i in frames.indices) {
            val from = frames[i]
            val to = frames[(i + 1) % frames.size]
            val move = moves[i]
            if (t < move) return TitanPose.lerp(from, to, ease(t.toDouble() / move))
            t -= move
            val hold = holds[i]
            if (t < hold) return to
            t -= hold
        }
        return frames.first()
    }

    companion object {
        private const val MIN_MOVE_MS = 600L
        private const val MIN_HOLD_MS = 150L

        /** Cubic in-out: soft start and stop, like a controlled repetition. */
        fun ease(x: Double): Double {
            val t = x.coerceIn(0.0, 1.0)
            return if (t < 0.5) 4 * t * t * t else 1 - (-2 * t + 2).let { it * it * it } / 2
        }

        /** "3-0-1-0" → [3, 0, 1, 0] seconds; null for anything else. */
        fun tempo(text: String?): List<Double>? {
            val parts = text?.split('-')?.map { it.trim().toDoubleOrNull() } ?: return null
            if (parts.size != 4 || parts.any { it == null }) return null
            return parts.map { it!! }
        }

        /**
         * Frames in the order of `animation.poses`; with [tempo] the moves alternate eccentric /
         * concentric and the holds the two pauses. Holds (time measure) get a long hold in the key pose.
         */
        fun of(exercise: Exercise?, library: PoseLibrary, tempo: String? = null, otherSide: Boolean = false): PoseTimeline {
            val keyed = exercise?.let { library.poses(it.id) }.orEmpty()
            val ordered = exercise?.animation?.poses?.mapNotNull { keyed[it.key] }.orEmpty()
            val frames = (ordered.ifEmpty { listOf(TitanPose.STAND) }).map { if (otherSide) it.mirrorSides() else it }
            if (frames.size < 2) return PoseTimeline(frames, listOf(0L), listOf(0L))
            val t = tempo(tempo ?: exercise?.default?.tempo)
            val timed = exercise?.measure?.timed == true
            val moves = frames.indices.map { i ->
                val s = when {
                    t != null -> if (i % 2 == 0) t[0] else t[2]
                    timed -> 1.8
                    else -> 1.2
                }
                max(MIN_MOVE_MS, (s * 1000).toLong())
            }
            val holds = frames.indices.map { i ->
                val s = when {
                    t != null -> if (i % 2 == 0) t[1] else t[3]
                    timed -> if (i % 2 == 0) 4.0 else 0.6
                    else -> 0.4
                }
                max(MIN_HOLD_MS, (s * 1000).toLong())
            }
            return PoseTimeline(frames, moves, holds)
        }
    }
}

/** Is any pose angle wildly off (used by tests: no NaN, every angle finite). */
internal fun TitanPose.finite(): Boolean = angles.all { !it.isNaN() && abs(it) < 1e4 } && !rootX.isNaN() && !rootY.isNaN()
