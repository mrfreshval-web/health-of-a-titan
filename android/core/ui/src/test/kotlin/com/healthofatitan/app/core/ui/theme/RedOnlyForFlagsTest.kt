package com.healthofatitan.app.core.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.test.assertTrue

/**
 * A11 §12.1: red is reserved for red flags and emergency help. Facet and mode tokens must not
 * be red (hue within ±15° of 0° with noticeable saturation).
 */
class RedOnlyForFlagsTest {

    private fun HotColors.all(): Map<String, Color> = mapOf(
        "facetCharge" to facetCharge, "facetBalance" to facetBalance, "facetRhythm" to facetRhythm,
        "facetClarity" to facetClarity, "facetWeatherLight" to facetWeatherLight,
        "facetWeatherHeavy" to facetWeatherHeavy, "facetConnections" to facetConnections,
        "facetFuel" to facetFuel, "facetBackpack" to facetBackpack, "modeRazgon" to modeRazgon,
        "modeRovno" to modeRovno, "modeBerezhno" to modeBerezhno,
    )

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
    fun `facet and mode tokens are not red in both themes`() {
        for ((theme, colors) in listOf("dark" to DarkHotColors, "light" to LightHotColors)) {
            colors.all().forEach { (name, color) ->
                assertTrue(!isRed(color), "$theme.$name looks red; red is only for flags (A11 §12.1)")
            }
        }
    }

    @Test
    fun `error roles are the A11 alert tokens`() {
        assertTrue(HotDarkScheme.error == Color(0xFFF2B8B5) && HotDarkScheme.errorContainer == Color(0xFF8C1D18))
        assertTrue(HotLightScheme.error == Color(0xFFB3261E) && HotLightScheme.errorContainer == Color(0xFFF9DEDC))
    }
}
