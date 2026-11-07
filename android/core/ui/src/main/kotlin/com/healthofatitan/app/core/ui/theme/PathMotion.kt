package com.healthofatitan.app.core.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import kotlin.math.PI
import kotlin.math.sin

/**
 * The motion dictionary of «Путь» (A58-VP §4.0): the only source of curves and durations of the
 * path. «Уменьшить анимацию» stops every loop and replaces flights by a 200 ms cross-fade
 * ([REDUCED_MS]); the end frame is the same.
 */
object PathMotion {
    /** Change of pose, unfolding, appearance. */
    val EaseStandard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Sheets and text coming in. */
    val EaseDecel: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Leaving, dissolving. */
    val EaseAccel: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** The flight of a fire (prototype `hotFly`). */
    val EaseFlight: Easing = CubicBezierEasing(0.2f, 0.7f, 0.2f, 1f)

    /** x inside a step. */
    val EaseLinear: Easing = LinearEasing

    /** The breath of the Titan and the wave of Water: a sine of [BREATH_MS], 0…1…0. */
    fun breath(ms: Long): Float = ((1.0 - kotlin.math.cos(2.0 * PI * (ms % BREATH_MS) / BREATH_MS)) / 2.0).toFloat()

    /** The lever halo (§1.4): α 0.12 → 0.55 → 0.12 by a sine over 4 s, 3 cycles, then 0.30. */
    fun leverHalo(ms: Long?): Float {
        if (ms == null || ms >= LEVER_CYCLE_MS * LEVER_CYCLES) return LEVER_REST
        val t = (ms % LEVER_CYCLE_MS).toDouble() / LEVER_CYCLE_MS
        return (0.12 + 0.43 * sin(PI * t)).toFloat()
    }

    /** A fire lands: r × 1.4 → × 1. */
    fun <T> springLand(): SpringSpec<T> = spring(dampingRatio = 0.6f, stiffness = 400f)

    /** The Titan steps on the stone. */
    fun <T> springStep(): SpringSpec<T> = spring(dampingRatio = 0.75f, stiffness = 300f)

    /** The sheet «было → станет», «Перекрёсток». */
    fun <T> springSheet(): SpringSpec<T> = spring(dampingRatio = 0.9f, stiffness = 380f)

    const val BREATH_MS = 6_000L
    const val LEVER_CYCLE_MS = 4_000L
    const val LEVER_CYCLES = 3
    const val LEVER_REST = 0.30f
    const val POSE_MS = 400
    const val SHEET_MS = 1_200
    const val FLIGHT_MS = 1_200
    const val SUNDAY_STEP_MS = 2_400
    const val UNFOLD_MS = 2_400
    const val ASSEMBLE_MS = 3_500
    const val PAUSE_MS = 800
    const val REDUCED_MS = 200

    /** A moment longer than this is skipped by a tap (the end frame at once). */
    const val SKIPPABLE_MS = 1_000
}
