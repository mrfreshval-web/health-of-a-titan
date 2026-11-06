package com.healthofatitan.app.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Brand seed (brand/README.md, A36 §2.1 «Золото бренда»): the mark, not UI accents. */
val BrandGold = Color(0xFFC9A23A)

/**
 * A36 «Колоннада» palette: gold line on night marble. Names follow A36 §2.1–2.2.
 * Red is NOT here on purpose: it lives only in the M3 `error` roles (SOS and red flags).
 */
object Kolonnada {
    // Dark «Ночь»
    val Night = Color(0xFF0B0E15)
    val Obsidian = Color(0xFF07090E)
    val Graphite = Color(0xFF11151E)
    val Basalt = Color(0xFF151A24)
    val Slate = Color(0xFF1B212D)
    val SlatePlus = Color(0xFF222937)
    val NightSky = Color(0xFF1B2232) // radial light at the top of the screen (A36 §4 «мрамор»)
    val Ivory = Color(0xFFEDE5D3)
    val Travertine = Color(0xFFA9A292)
    val Limestone = Color(0xFF938C7C)
    val Gold = Color(0xFFD9B562)
    val GoldBrand = BrandGold
    val GoldHi = Color(0xFFF3E2B0)
    val GoldButtonTop = Color(0xFFE6C77E) // primary capsule gradient (A36 §6)
    val GoldButtonBottom = Color(0xFFCFA845)
    val Bronze = Color(0xFF3A2E12)
    val OnGold = Color(0xFF1C1505)

    // Light «Мрамор»
    val Marble = Color(0xFFF5F0E6)
    val Ink = Color(0xFF161A22)
    val Sepia = Color(0xFF575144)
    val LimestoneLight = Color(0xFF6E6656)
    val GoldDeep = Color(0xFF7A5A12)
}

/**
 * Facet, day-mode and line tokens (A36 §2.3, §2.5, §6). Mode colours are «materials» —
 * Янтарь, Патина, Сумерки — and always come with a glyph and a word, never colour alone.
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
    /** Gold rule 26 %: «колонны», frames, the hero card cornice. Decorative. */
    val rule: Color,
    /** Gold rule 14 %: active tab pill, soft frames. Decorative. */
    val ruleSoft: Color,
    /** Ivory 7.5 % hairline: card edges and dividers (depth by tone and line, not shadow). */
    val hairline: Color,
    /** Tertiary text: time, captions (A36 §2.1 «Известняк»). */
    val textTertiary: Color,
    /** Top of the screen background gradient (equals the background in the light theme). */
    val skyTop: Color,
    val isDark: Boolean,
)

val DarkHotColors = HotColors(
    facetCharge = Color(0xFFE2AE63),
    facetBalance = Color(0xFF86BFAE),
    facetRhythm = Color(0xFFA3AEDB),
    facetClarity = Color(0xFF8EC6E0),
    facetWeatherLight = Color(0xFFE8CF8A),
    facetWeatherHeavy = Color(0xFF9AA8B4),
    facetConnections = Color(0xFFCFA3CC),
    facetFuel = Color(0xFFA9C79A),
    facetBackpack = Color(0xFFB3BAC4),
    modeRazgon = Color(0xFFE6A867),
    modeRovno = Color(0xFF8FC2B2),
    modeBerezhno = Color(0xFFACA9DE),
    rule = Kolonnada.Gold.copy(alpha = 0.26f),
    ruleSoft = Kolonnada.Gold.copy(alpha = 0.14f),
    hairline = Kolonnada.Ivory.copy(alpha = 0.075f),
    textTertiary = Kolonnada.Limestone,
    skyTop = Kolonnada.NightSky,
    isDark = true,
)

val LightHotColors = HotColors(
    facetCharge = Color(0xFF8A5A12),
    facetBalance = Color(0xFF2E6A5B),
    facetRhythm = Color(0xFF3F4C8F),
    facetClarity = Color(0xFF23668A),
    facetWeatherLight = Color(0xFF7A6315),
    facetWeatherHeavy = Color(0xFF4F5D69),
    facetConnections = Color(0xFF7A3F78),
    facetFuel = Color(0xFF42662F),
    facetBackpack = Color(0xFF4A5361),
    modeRazgon = Color(0xFF8E5617),
    modeRovno = Color(0xFF2F6B5C),
    modeBerezhno = Color(0xFF544D92),
    rule = Kolonnada.GoldDeep.copy(alpha = 0.26f),
    ruleSoft = Kolonnada.GoldDeep.copy(alpha = 0.10f),
    hairline = Kolonnada.Ink.copy(alpha = 0.10f),
    textTertiary = Kolonnada.LimestoneLight,
    skyTop = Kolonnada.Marble,
    isDark = false,
)

val LocalHotColors = staticCompositionLocalOf { DarkHotColors }
