package com.healthofatitan.app.core.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.test.assertTrue

/**
 * A11 §12.1: red is reserved for red flags and emergency help. Mode, light and glass tokens
 * must not be red (hue within ±15° of 0° with noticeable saturation).
 */
class RedOnlyForFlagsTest {

    private fun HotColors.all(): Map<String, Color> = mapOf(
        "modeRazgon" to modeRazgon, "modeRovno" to modeRovno, "modeBerezhno" to modeBerezhno,
        "rule" to rule, "ruleSoft" to ruleSoft, "hairline" to hairline, "textTertiary" to textTertiary,
        "skyTop" to skyTop, "glassTop" to glassTop, "glassBottom" to glassBottom, "glassEdge" to glassEdge,
        "knockout" to knockout,
    ) + dawnFill.mapIndexed { i, c -> "dawnFill$i" to c } +
        titanLight.mapIndexed { i, c -> "titanLight$i" to c } +
        columnLight.mapIndexed { i, c -> "columnLight$i" to c }

    private fun isRed(c: Color): Boolean {
        val mx = max(c.red, max(c.green, c.blue))
        val mn = min(c.red, min(c.green, c.blue))
        val delta = mx - mn
        if (mx == 0f || delta / mx < 0.25f) return false // grey-ish
        if (mx != c.red) return false
        val hue = (60f * ((c.green - c.blue) / delta) + 360f) % 360f
        return hue < 15f || hue > 345f
    }

    @Test
    fun `mode light and glass tokens are not red in both themes`() {
        for ((theme, colors) in listOf("dark" to DarkHotColors, "light" to LightHotColors)) {
            colors.all().forEach { (name, color) ->
                assertTrue(!isRed(color), "$theme.$name looks red; red is only for flags (A11 §12.1)")
            }
        }
    }

    @Test
    fun `no colour scheme role outside error is red`() {
        for ((theme, scheme) in listOf("dark" to HotDarkScheme, "light" to HotLightScheme)) {
            mapOf(
                "primary" to scheme.primary,
                "secondary" to scheme.secondary, "tertiary" to scheme.tertiary,
                "background" to scheme.background, "surface" to scheme.surface,
                "surfaceContainer" to scheme.surfaceContainer, "onSurface" to scheme.onSurface,
                "outline" to scheme.outline, "onPrimary" to scheme.onPrimary,
                "primaryContainer" to scheme.primaryContainer, "tertiaryContainer" to scheme.tertiaryContainer,
            ).forEach { (name, color) ->
                assertTrue(!isRed(color), "$theme.$name looks red; red is only for flags (A37 §9.1)")
            }
        }
    }

    @Test
    fun `error roles are the A11 alert tokens`() {
        assertTrue(HotDarkScheme.error == Color(0xFFF2B8B5) && HotDarkScheme.errorContainer == Color(0xFF8C1D18))
        assertTrue(HotLightScheme.error == Color(0xFFB3261E) && HotLightScheme.errorContainer == Color(0xFFF9DEDC))
    }
}
