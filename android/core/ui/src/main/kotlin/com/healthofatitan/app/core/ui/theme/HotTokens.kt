package com.healthofatitan.app.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Brand seed (A11 §12.1, brand/README.md): gold on dark. */
val BrandGold = Color(0xFFC9A23A)

/**
 * Facet and day-mode tokens (A11 §12.1). Red is NOT here on purpose: red is reserved for
 * red flags and emergency help and lives only in the M3 `error` roles.
 */
@Immutable
data class HotColors(
    val facetCharge: Color,
    val facetBalance: Color,
    val facetRhythm: Color,
    val facetClarity: Color,
    val facetWeatherLight: Color,
    val facetWeatherHeavy: Color,
    val facetConnections: Color,
    val facetFuel: Color,
    val facetBackpack: Color,
    val modeRazgon: Color,
    val modeRovno: Color,
    val modeBerezhno: Color,
)

val DarkHotColors = HotColors(
    facetCharge = Color(0xFFFFB74D),
    facetBalance = Color(0xFF4DB6AC),
    facetRhythm = Color(0xFF9FA8DA),
    facetClarity = Color(0xFF81D4FA),
    facetWeatherLight = Color(0xFFFFE082),
    facetWeatherHeavy = Color(0xFF90A4AE),
    facetConnections = Color(0xFFCE93D8),
    facetFuel = Color(0xFFA5D6A7),
    facetBackpack = Color(0xFFB0BEC5),
    modeRazgon = Color(0xFFFFCA6B),
    modeRovno = Color(0xFF80CBC4),
    modeBerezhno = Color(0xFFB39DDB),
)

val LightHotColors = HotColors(
    facetCharge = Color(0xFFB26A00),
    facetBalance = Color(0xFF00796B),
    facetRhythm = Color(0xFF3949AB),
    facetClarity = Color(0xFF0277BD),
    facetWeatherLight = Color(0xFF8D6E00),
    facetWeatherHeavy = Color(0xFF546E7A),
    facetConnections = Color(0xFF7B1FA2),
    facetFuel = Color(0xFF2E7D32),
    facetBackpack = Color(0xFF455A64),
    modeRazgon = Color(0xFF9A6B00),
    modeRovno = Color(0xFF00695C),
    modeBerezhno = Color(0xFF5E35B1),
)

val LocalHotColors = staticCompositionLocalOf { DarkHotColors }
