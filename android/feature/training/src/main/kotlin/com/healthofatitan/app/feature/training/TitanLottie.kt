package com.healthofatitan.app.feature.training

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.healthofatitan.app.core.ui.component.TitanLottieFigure
import com.healthofatitan.app.core.ui.component.rememberTitanLottie
import com.healthofatitan.app.domain.training.Exercise
import com.healthofatitan.app.domain.training.PoseTimeline

/**
 * The exercise in the player: the Lottie file when the exercise has one (the Titan of the mark,
 * recoloured to the mode and the theme, mirrored for the other side), otherwise the Compose
 * skeleton as before. [animate] false («Удалить анимацию», pause, countdown) shows the static key
 * pose (`poster`). The files and their runtime live in core:ui (TitanLottie.kt there).
 */
@Composable
fun TitanExerciseView(
    exercise: Exercise,
    timeline: PoseTimeline,
    front: Boolean,
    light: List<Color>,
    glow: Color?,
    accent: Color,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    otherSide: Boolean = false,
    tempo: String? = null,
    description: String? = null,
) {
    val entry = rememberTitanLottie()[exercise.id]
    if (entry == null) {
        TitanExercise(timeline, front, light, glow, modifier, animate, description)
        return
    }
    // with a tempo the cycle follows the user's tempo (the rig's timeline); without one the file's own
    val cycle = if (PoseTimeline.tempo(tempo ?: exercise.default.tempo) != null) timeline.cycleMs else 0L
    TitanLottieFigure(entry, cycle, light, glow, accent, modifier, animate, otherSide, description)
}
