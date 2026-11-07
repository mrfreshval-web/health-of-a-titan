package com.healthofatitan.app.domain.training

import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TitanPoseTest {

    private val lib = Content.library
    private val poses = lib.poses

    @Test
    fun `keyframes exist for the most used Classic exercises`() {
        val top = listOf(
            "ex-breath-long-exhale", "ex-cat-cow", "ex-arm-circles", "ex-stretch-hamstring", "ex-ankle-knee-wall",
            "ex-worlds-greatest", "ex-stretch-hip-flexor", "ex-stretch-calf", "ex-squat-bw", "ex-pushup",
        )
        top.forEach { assertTrue(it in poses.exerciseIds, "$it has no keyframes") }
        assertTrue(poses.exerciseIds.size >= 20)
    }

    @Test
    fun `frame keys match the exercise and every frame is anatomically sane`() {
        for (id in poses.exerciseIds) {
            val ex = assertNotNull(lib.exercise(id), id)
            val frames = poses.poses(id)
            assertEquals(ex.animation.poses.map { it.key }.toSet(), frames.keys, id)
            val front = ex.animation.view == "front" || ex.animation.view == "top"
            frames.forEach { (k, p) ->
                assertEquals(TitanPose.JOINTS, p.angles.size)
                assertTrue(p.finite(), "$id.$k")
                assertEquals(emptyList(), Rig.problems(p, front), "$id.$k")
            }
        }
    }

    @Test
    fun `the timeline follows the tempo of the repetition`() {
        val squat = lib.exercise("ex-squat-bw")!!
        val t = PoseTimeline.of(squat, poses, tempo = "3-0-1-0")
        val stand = poses.poses(squat.id).getValue("stand")
        val bottom = poses.poses(squat.id).getValue("bottom")
        assertEquals(stand, t.at(0))
        // 3 s down, 0.15 s minimal hold, 1 s up, 0.15 s hold
        assertEquals(4_300, t.cycleMs)
        assertEquals(bottom, t.at(3_050))
        assertEquals(stand, t.at(4_200))
        val mid = t.at(1_500)
        assertTrue(abs(mid.rootY - (stand.rootY + bottom.rootY) / 2) < 0.5, "halfway down at 1.5 s: ${mid.rootY}")
        assertEquals(t.at(1_000), t.at(1_000 + t.cycleMs), "cyclic")
        assertEquals(bottom, t.still, "reduce motion shows the key pose")
    }

    @Test
    fun `exercises without keyframes stand still as a poster`() {
        val ex = lib.exercise("ex-farmer-carry")!!
        val t = PoseTimeline.of(ex, poses)
        assertEquals(TitanPose.STAND, t.at(12_345))
        assertEquals(TitanPose.STAND, t.still)
        assertEquals(TitanPose.STAND, PoseTimeline.of(null, poses).still)
    }

    @Test
    fun `the other side mirrors near and far limbs`() {
        val lunge = lib.exercise("ex-reverse-lunge")!!
        val a = PoseTimeline.of(lunge, poses).still
        val b = PoseTimeline.of(lunge, poses, otherSide = true).still
        assertEquals(a.legN, b.legF)
        assertEquals(a.armF, b.armN)
    }

    @Test
    fun `interpolation takes the short way round`() {
        assertEquals(20.0, TitanPose.shortest(350.0, 10.0))
        assertEquals(-20.0, TitanPose.shortest(10.0, 350.0))
        val a = TitanPose.STAND.copy(torso = 350.0)
        val b = TitanPose.STAND.copy(torso = 10.0)
        assertEquals(360.0, TitanPose.lerp(a, b, 0.5).torso, 1e-9)
    }

    @Test
    fun `standing pose stands on the floor`() {
        val j = Rig.joints(TitanPose.STAND)
        assertTrue(abs(j.ankleN.y - 91.0) < 0.5)
        assertTrue(j.head.y < 20)
        assertEquals(0.0, Rig.kneeFlexion(TitanPose.STAND.legN), 1e-9)
    }
}
