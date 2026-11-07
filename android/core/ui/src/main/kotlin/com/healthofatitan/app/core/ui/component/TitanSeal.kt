package com.healthofatitan.app.core.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.core.ui.theme.Titanium
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

const val TITAN_SEAL_TAG = "titan_seal"

/**
 * «Печать» WHOOP (A37 §11 wow 3, s4): the workout was confirmed by WHOOP — the ring closes
 * around the Titan in the ice light, light leaves it in rays and the phone gives a double tick.
 * 600 ms, once per [key]; «Удалить анимацию» shows the closed ring at once, without ticks.
 * Recognition without confetti.
 *
 * @param key one confirmed workout (its id): the seal plays once per key
 */
@Composable
fun TitanSeal(key: String, modifier: Modifier = Modifier, spoken: String = "Тренировку подтвердил WHOOP") {
    val animate = !rememberReduceMotion()
    val ring = remember(key) { Animatable(if (animate) 0f else 1f) }
    val rays = remember(key) { Animatable(if (animate) 0f else 1f) }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(key, animate) {
        if (!animate) return@LaunchedEffect
        ring.animateTo(1f, tween(SEAL_MS * 2 / 3, easing = FastOutSlowInEasing))
        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        delay(SECOND_TICK_MS)
        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        rays.animateTo(1f, tween(SEAL_MS, easing = LinearEasing))
    }
    val tokens = HotThemeTokens.colors
    val ice = tokens.modeRovno
    val light = TitanLight(Titanium.LightIce, tokens.columnLight, tokens.knockout, if (tokens.glow) ice else null)
    TitanScene(
        modifier = modifier.semantics { contentDescription = spoken }.testTag(TITAN_SEAL_TAG),
        light = light,
        ring = { ring.value },
        ringColor = ice,
        ringTrack = tokens.ruleSoft,
        ringStroke = 16f,
        ringFit = 0.66f,
    ) {
        // rays: 48 hairlines leaving the ring as it closes, the strongest at the moment of closing
        Spacer(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val k = ring.value
                    val r0 = titanRingRadius(size, 0.66f)
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val fade = if (rays.value > 0f) 1f - 0.6f * rays.value else k
                    if (fade <= 0.01f) return@drawBehind
                    for (i in 0 until RAYS) {
                        val a = (i * 360f / RAYS + 3.75f) * DEG
                        val len = r0 * (0.22f + 0.18f * ((i * 7) % 5) / 4f) * (0.4f + 0.6f * k)
                        val start = r0 * (1.12f + 0.08f * rays.value)
                        val from = Offset(c.x + start * cos(a), c.y + start * sin(a))
                        val to = Offset(c.x + (start + len) * cos(a), c.y + (start + len) * sin(a))
                        drawLine(ice.copy(alpha = 0.22f * fade), from, to, 1.2f * density, StrokeCap.Round)
                    }
                },
        )
    }
}

private const val SEAL_MS = 600
private const val SECOND_TICK_MS = 90L
private const val RAYS = 48
private const val DEG = (Math.PI / 180.0).toFloat()
