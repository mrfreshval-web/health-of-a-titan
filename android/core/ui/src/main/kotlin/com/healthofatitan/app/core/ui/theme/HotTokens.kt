package com.healthofatitan.app.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Gold of the mark (A37 §9.1 «Золото знака»): the mark and the splash only, never UI accents. */
val BrandGold = Color(0xFFD6A964)

/**
 * A37 «Титаниум» palette (§9.1–9.2): titanium, glass and one warm light. Names follow A37.
 * Red is NOT here on purpose: it lives only in the M3 `error` roles (crisis card and red flags).
 */
object Titanium {
    // Dark «Титан» (main)
    val Depth = Color(0xFF060708) // «Глубина»: background, surface
    val Titan1 = Color(0xFF0E1013)
    val Titan2 = Color(0xFF15181C)
    val Titan3 = Color(0xFF1C2025)
    val Titan4 = Color(0xFF23282E)
    val Bone = Color(0xFFF3F1EC) // «Кость»: onSurface 17.9 : 1
    val Steel = Color(0xFFA3A7AE) // «Сталь»: onSurfaceVariant 8.3 : 1
    val Graphite = Color(0xFF80858D) // «Графит»: time, captions 5.4 : 1
    val Dawn = Color(0xFFFFC77A) // «Рассвет»: primary
    val DawnHi = Color(0xFFFFE1AE)
    val DawnLow = Color(0xFFFFB05E)
    val OnDawn = Color(0xFF1A1206)
    val DawnContainer = Color(0xFF3A2A12)

    // Light «Фарфор» (day)
    val Porcelain = Color(0xFFF3F2EF)
    val Ink = Color(0xFF0D0F12) // 17.1 : 1
    val InkSecondary = Color(0xFF565B63) // 6.1 : 1
    val InkTertiary = Color(0xFF646972) // 4.9 : 1
    val DawnText = Color(0xFF9A5B12) // accent text on light, 4.8 : 1
    val DawnPillHi = Color(0xFFFFD597)
    val DawnPillLow = Color(0xFFFFB866)

    /** «Линия света» of the Titan (A37 §3): white → warm, top to bottom. */
    val LightWarm = listOf(Color(0xFFFFFFFF), Color(0xFFFFF1D6), Color(0xFFFFD08A))

    /** Ice light: workout seal and «Ровно» (A37 §3). */
    val LightIce = listOf(Color(0xFFFFFFFF), Color(0xFFDDF8F2), Color(0xFF9FEADC))

    /** Iris light: «Бережно», calm silver-lilac, never a «bad» dim (A37 §3). */
    val LightIris = listOf(Color(0xFFFFFFFF), Color(0xFFE6E4FF), Color(0xFFBDB8FF))

    /** Columns are colder and dimmer than the figure: the Titan is always brighter than architecture. */
    val ColumnLight = listOf(Color(0xFFE9EEF3), Color(0xFFB9C2CC), Color(0xFF8D97A3))

    /** Glass knockout behind the lines (A37 §3: rgba(7,8,10,.94)). */
    val Knockout = Color(0xF007080A)
}

/**
 * Mode, light and glass tokens of the current theme (A37 §9). Mode colours are light — Солнце,
 * Лёд, Ирис — and always come with a glyph and a word, never colour alone.
 */
@Immutable
data class HotColors(
    /** Разгон — Солнце. */
    val modeRazgon: Color,
    /** Ровно — Лёд. */
    val modeRovno: Color,
    /** Бережно — Ирис. */
    val modeBerezhno: Color,
    /** Hairline 14 %: decorative outlines, active pill rims (A37 `outline`). */
    val rule: Color,
    /** Soft fill 7 %: ring tracks, inactive pills. Decorative. */
    val ruleSoft: Color,
    /** Hairline 8 %: card edges and dividers (depth by light and line, not shadow). */
    val hairline: Color,
    /** Tertiary text: time, captions, sources («Графит»). */
    val textTertiary: Color,
    /** Top of the screen background: the one light from above (equals the background in light). */
    val skyTop: Color,
    /** Glass card fill, top and bottom of its vertical gradient (A37 §9.1 «Стекло»). */
    val glassTop: Color,
    val glassBottom: Color,
    /** 1 dp light rim on the top edge of glass. */
    val glassEdge: Color,
    /** Main button fill, top → bottom (A37 §9.1 «Рассвет»). */
    val dawnFill: List<Color>,
    /** Line of light on the Titan when no mode tints it. */
    val titanLight: List<Color>,
    val columnLight: List<Color>,
    /** Fill behind the line art (glass knockout of the silhouette). */
    val knockout: Color,
    /** Glow is for the one hero on a screen; none in the light theme (A37 §9.5). */
    val glow: Boolean,
    val isDark: Boolean,
)

val DarkHotColors = HotColors(
    modeRazgon = Color(0xFFFFB067),
    modeRovno = Color(0xFF7DE3D1),
    modeBerezhno = Color(0xFFA8A3FF),
    rule = Color.White.copy(alpha = 0.14f),
    ruleSoft = Color.White.copy(alpha = 0.07f),
    hairline = Color.White.copy(alpha = 0.08f),
    textTertiary = Titanium.Graphite,
    skyTop = Color(0xFF14181D),
    glassTop = Color.White.copy(alpha = 0.075f),
    glassBottom = Color.White.copy(alpha = 0.03f),
    glassEdge = Color.White.copy(alpha = 0.12f),
    dawnFill = listOf(Titanium.DawnHi, Titanium.Dawn, Titanium.DawnLow),
    titanLight = Titanium.LightWarm,
    columnLight = Titanium.ColumnLight,
    knockout = Titanium.Knockout,
    glow = true,
    isDark = true,
)

val LightHotColors = HotColors(
    modeRazgon = Color(0xFFA25A12),
    modeRovno = Color(0xFF0E7A6A),
    modeBerezhno = Color(0xFF5149C9),
    rule = Titanium.Ink.copy(alpha = 0.14f),
    ruleSoft = Titanium.Ink.copy(alpha = 0.06f),
    hairline = Titanium.Ink.copy(alpha = 0.08f),
    textTertiary = Titanium.InkTertiary,
    skyTop = Titanium.Porcelain,
    glassTop = Color.White,
    glassBottom = Color.White,
    glassEdge = Color.White,
    dawnFill = listOf(Titanium.DawnPillHi, Titanium.DawnPillHi, Titanium.DawnPillLow),
    titanLight = listOf(Color(0xFF2A2E34), Color(0xFF2A2E34), Color(0xFF3A3F46)),
    columnLight = listOf(Color(0xFF6E747C), Color(0xFF80858D), Color(0xFF8D939B)),
    knockout = Titanium.Porcelain,
    glow = false,
    isDark = false,
)

val LocalHotColors = staticCompositionLocalOf { DarkHotColors }
