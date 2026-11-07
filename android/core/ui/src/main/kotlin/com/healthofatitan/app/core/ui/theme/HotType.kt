package com.healthofatitan.app.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.healthofatitan.app.core.ui.R

/*
 * A37 §9.3: Geist for text and numbers, Geist Mono for telemetry (date, source, time, captions).
 * Both SIL OFL 1.1 (Vercel, Basement Studio), static instances subset to Latin + Cyrillic by
 * brand/mark-v2/android/build_geist_fonts.py, licence in assets/licenses/OFL-Geist.txt.
 * Manrope stays bundled as the fallback (A37 §9.3) and for the crisis screen, which does not
 * change with the visual concept (D-029). Cormorant left the interface: only the store and the
 * splash wordmark keep it.
 */
val Geist = FontFamily(
    Font(R.font.geist_extralight, FontWeight.ExtraLight),
    Font(R.font.geist_light, FontWeight.Light),
    Font(R.font.geist_regular, FontWeight.Normal),
    Font(R.font.geist_medium, FontWeight.Medium),
)

val GeistMono = FontFamily(
    Font(R.font.geist_mono_medium, FontWeight.Medium),
)

val Manrope = FontFamily(
    Font(R.font.manrope_light, FontWeight.Light),
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
)

/** Tabular, lining figures: numbers do not jump when they update (A37 §9.3 rule 2). */
private const val TNUM = "tnum, lnum"

private fun geist(size: Int, line: Int, weight: FontWeight, track: Double = 0.0) = TextStyle(
    fontFamily = Geist,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = track.em,
    fontFeatureSettings = TNUM,
)

/** Geist Mono 500, +0.14 em: set in capitals by the composable (HotOverline), ≤ 5 words. */
private fun mono(size: Int, line: Int, track: Double = 0.14) = TextStyle(
    fontFamily = GeistMono,
    fontWeight = FontWeight.Medium,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = track.em,
)

/**
 * The Fibonacci scale of A37 §9.3 (89 · 55 · 34 · 21 · 13, plus 16 and 11) on the M3 roles:
 * display = 89 / 55 / 34, headline 34 / 28 / 21, title 21 / 16 / 13, body 16 / 14 / 13,
 * label 15 / 13 and the mono telemetry line as labelSmall.
 */
val HotTypography = Typography(
    displayLarge = geist(89, 84, FontWeight.ExtraLight, -0.055),
    displayMedium = geist(55, 56, FontWeight.ExtraLight, -0.045),
    displaySmall = geist(34, 38, FontWeight.Light, -0.03),
    headlineLarge = geist(34, 38, FontWeight.Light, -0.03),
    headlineMedium = geist(28, 32, FontWeight.Light, -0.025),
    headlineSmall = geist(21, 26, FontWeight.Normal, -0.01),
    titleLarge = geist(21, 26, FontWeight.Normal, -0.01),
    titleMedium = geist(16, 22, FontWeight.Medium),
    titleSmall = geist(13, 18, FontWeight.Medium),
    bodyLarge = geist(16, 22, FontWeight.Normal),
    bodyMedium = geist(14, 20, FontWeight.Normal),
    bodySmall = geist(13, 18, FontWeight.Normal),
    labelLarge = geist(15, 20, FontWeight.Medium),
    labelMedium = geist(13, 18, FontWeight.Medium),
    labelSmall = mono(11, 14),
)

/** HoT styles outside the M3 scale (A37 §9.3). */
object HotType {
    /** The mode word in the morning (89 sp, 96 line) and the workout number. */
    val display = geist(89, 96, FontWeight.ExtraLight, -0.055)

    /** «Ровно» under the ring, «Закрыть день» (55 / 56). */
    val headline = geist(55, 56, FontWeight.ExtraLight, -0.045)

    /** Haiku lines and the lead phrase of a screen: light, airy, never italic antiqua. */
    val quote = geist(21, 30, FontWeight.Light, -0.01)

    /** Key numbers: Заряд, sleep, metrics (34 / 38, light). */
    val numeral = geist(34, 38, FontWeight.Light, -0.03)

    /** Workout and practice timers. */
    val timer = geist(89, 84, FontWeight.ExtraLight, -0.055)

    /** Telemetry: date, source, time, captions (Geist Mono 500, +0.14 em, upper case). */
    val mono = mono(11, 14)

    /**
     * Clock times in lists and on «Линия дня» (UX review 119, R1): Geist Mono, every digit the
     * same width, tight tracking so «23:05» never wraps in [com.healthofatitan.app.core.ui.component.HotNumbers.TimeColumn].
     */
    val time = mono(13, 16, 0.02)

    /** Ring values in the header (Geist 400, tabular). */
    val ringValue = geist(15, 18, FontWeight.Normal, -0.01)

    /** In the app the wordmark is Geist Mono with wide tracking (A37 §3 «Логотип»). */
    val wordmark = mono(13, 16, 0.32)

    /** Crisis headline: sober Manrope, unchanged by the visual concept (A36 §11 rule 7, D-029). */
    val sosHeadline = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        fontFeatureSettings = TNUM,
    )
}
