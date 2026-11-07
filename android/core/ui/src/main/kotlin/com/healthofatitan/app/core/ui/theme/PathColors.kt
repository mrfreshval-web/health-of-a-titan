package com.healthofatitan.app.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Colours of «Путь» (A58-VP §1.5, D-048 §1): the elements are materials of the world — star silver,
 * sandstone and bronze, deep blue — and never repeat the mode colours (those light only the Titan
 * of the day). No red anywhere on the path (P-19): the only red stays with «Помощь» (A07).
 */
data class PathColors(
    val sky: Color,
    val earth: Color,
    val water: Color,
    /** Warm amber of the lever, hue ≈ 38°, not red. */
    val lever: Color,
    val goalTick: Color,
    val dano: Color,
    val horizonLine: Color,
    val star: Color,
    val pastSelf: Color,
    val skyGlow: Color,
    val deep: Color,
    val danoFill: Color,
    /** Alpha of a part outside the goal («гравировка»). */
    val engrave: Float,
    val track: Float,
    val practice: Float,
    val axis: Float,
    val horizon: Float,
    val horizonGlow: Float,
    val isDark: Boolean,
) {
    /** Every colour of the tokens, for the «no red» check. */
    val all: List<Color> get() = listOf(sky, earth, water, lever, goalTick, dano, horizonLine, star, pastSelf, skyGlow, deep, danoFill)

    companion object {
        val Dark = PathColors(
            sky = Color(0xFFC9D3E6),
            earth = Color(0xFFD8C3A0),
            water = Color(0xFF7FB2F0),
            lever = Color(0xFFE8B86A),
            goalTick = Color(0xFFFFC77A),
            dano = Color(0xFF8A90A6),
            horizonLine = Color(0xFFF3D9A4),
            star = Color(0xFFF3D9A4),
            pastSelf = Color(0xFFA9C8F2).copy(alpha = 0.45f),
            skyGlow = Color(0xFF14181D),
            deep = Color(0xFF0B1A2C),
            danoFill = Color.White.copy(alpha = 0.06f),
            engrave = 0.15f,
            track = 0.28f,
            practice = 0.95f,
            axis = 0.35f,
            horizon = 0.18f,
            horizonGlow = 0.35f,
            isDark = true,
        )

        val Light = PathColors(
            sky = Color(0xFF4F5D78),
            earth = Color(0xFF7A5A2B),
            water = Color(0xFF2C62A6),
            lever = Color(0xFF9A5B12),
            goalTick = Color(0xFF9A5B12),
            dano = Color(0xFF646972),
            horizonLine = Color(0xFF8A5F1A),
            star = Color(0xFF8A5F1A),
            pastSelf = Color(0xFF4F5D78).copy(alpha = 0.45f),
            skyGlow = Color(0xFFE9ECF2),
            deep = Color(0xFFDCE5F1),
            danoFill = Color(0xFF0D0F12).copy(alpha = 0.05f),
            engrave = 0.20f,
            track = 0.28f,
            practice = 1.0f,
            axis = 0.40f,
            horizon = 0.22f,
            horizonGlow = 0.12f,
            isDark = false,
        )

        val current: PathColors
            @Composable
            @ReadOnlyComposable
            get() = if (HotThemeTokens.colors.isDark) Dark else Light
    }
}
