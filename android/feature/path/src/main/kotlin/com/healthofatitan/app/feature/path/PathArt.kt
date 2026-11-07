package com.healthofatitan.app.feature.path

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.healthofatitan.app.core.ui.component.HotMode
import com.healthofatitan.app.core.ui.component.LottieEntry
import com.healthofatitan.app.core.ui.component.LottieSection
import com.healthofatitan.app.core.ui.component.TitanLights
import com.healthofatitan.app.core.ui.component.TitanLottieFigure
import com.healthofatitan.app.core.ui.component.rememberReduceMotion
import com.healthofatitan.app.core.ui.component.rememberTitanLottie
import com.healthofatitan.app.domain.path.Element

/**
 * The day Titan of «Путь» (A58-VP §3.2): Lottie from tools/lottie `paths.py` (Agent 17), five files
 * by the manifest keys of the package. This is the one point of substitution: a later file under
 * the same key replaces the figure without touching the screens. A missing key draws nothing — the
 * slice still reads, the Titan is decoration over the meaning.
 */
object PathArt {
    const val HOLD = "path-hold"
    const val PALMS = "path-palms"
    const val SKY = "path-sky"
    const val EARTH = "path-earth"
    const val WATER = "path-water"

    fun header(e: Element): String = when (e) {
        Element.SKY -> SKY
        Element.EARTH -> EARTH
        Element.WATER -> WATER
    }
}

/** What the day Titan of the slice does (§3.1): holds, looks to the lever's tier, or holds with one hand. */
enum class DayPose { HOLD, LOOK_SKY, LOOK_EARTH, LOOK_WATER, ONE_HAND }

/**
 * The marker sections of `path-hold` for a pose: the breathing loops and the single 400 ms looks
 * (held on their last frame; the way back is the same section in reverse).
 */
fun daySection(entry: LottieEntry, pose: DayPose): LottieSection? = when (pose) {
    DayPose.HOLD -> LottieSection.of(entry, "hold_loop_start", "hold_loop_end", loop = true)
    DayPose.LOOK_SKY -> LottieSection.of(entry, "look_sky")
    DayPose.LOOK_EARTH -> LottieSection.of(entry, "look_earth")
    DayPose.LOOK_WATER -> LottieSection.of(entry, "look_water")
    DayPose.ONE_HAND -> LottieSection.of(entry, "one_hand_loop_start", "one_hand_loop_end", loop = true)
}

/** The day Titan in the Lottie view of the slice; lit by the mode of the day (the only place modes show). */
@Composable
fun PathDayTitan(pose: DayPose, mode: HotMode?, modifier: Modifier = Modifier, still: Boolean = false) {
    val entry = rememberTitanLottie()[PathArt.HOLD] ?: return Box(modifier)
    val reduce = rememberReduceMotion()
    val light = if (mode != null) TitanLights.of(mode) else TitanLights.default()
    val section = remember(entry, pose) { daySection(entry, pose) }
    TitanLottieFigure(
        entry = entry,
        cycleMs = 0,
        light = light.line,
        glow = light.glow,
        accent = light.line.last(),
        modifier = modifier,
        animate = !reduce && !still,
        section = section,
    )
}

/**
 * A Titan of [key] (palms in the ritual, the heads of the element cards): the whole file loops,
 * the poster is the still of «Уменьшить анимацию».
 */
@Composable
fun PathTitan(key: String, modifier: Modifier = Modifier, once: Boolean = false, still: Boolean = false) {
    val entry = rememberTitanLottie()[key] ?: return Box(modifier)
    val reduce = rememberReduceMotion()
    val light = TitanLights.default()
    val section = remember(entry, once) {
        if (once) LottieSection.of(entry, entry.markers.minByOrNull { it.value }?.key ?: "poster", "poster") else null
    }
    TitanLottieFigure(
        entry = entry,
        cycleMs = 0,
        light = light.line,
        glow = light.glow,
        accent = light.line.last(),
        modifier = modifier,
        animate = !reduce && !still,
        section = section,
    )
}
