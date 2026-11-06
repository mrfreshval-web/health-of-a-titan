package com.healthofatitan.app.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.healthofatitan.app.core.ui.R

/*
 * A36 §3: Cormorant Garamond for feeling (display, headlines, the quote of the day; only from
 * 20 sp), Manrope for text and every number (tabular figures). Both are SIL OFL 1.1, bundled in
 * res/font as static instances subset to Latin + Cyrillic (licences in assets/licenses/).
 */
val Cormorant = FontFamily(
    Font(R.font.cormorant_garamond_medium, FontWeight.Medium),
    Font(R.font.cormorant_garamond_semibold, FontWeight.SemiBold),
    Font(R.font.cormorant_garamond_medium_italic, FontWeight.Medium, FontStyle.Italic),
)

val Manrope = FontFamily(
    Font(R.font.manrope_light, FontWeight.Light),
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
)

/** Tabular, lining figures: numbers do not jump when they update (A36 §3 rule 2). */
private const val TNUM = "tnum, lnum"

private fun serif(size: Int, line: Int, track: Double = 0.0) = TextStyle(
    fontFamily = Cormorant,
    fontWeight = FontWeight.Medium,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = track.sp,
)

private fun sans(size: Int, line: Int, weight: FontWeight, track: Double = 0.0) = TextStyle(
    fontFamily = Manrope,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = track.sp,
    fontFeatureSettings = TNUM,
)

val HotTypography = Typography(
    displayLarge = serif(56, 60, -0.5),
    displayMedium = serif(44, 48, -0.3),
    displaySmall = serif(36, 42),
    headlineLarge = serif(32, 38),
    headlineMedium = serif(28, 34),
    headlineSmall = serif(24, 30),
    titleLarge = sans(20, 26, FontWeight.SemiBold),
    titleMedium = sans(16, 22, FontWeight.SemiBold, 0.1),
    titleSmall = sans(14, 20, FontWeight.SemiBold),
    bodyLarge = sans(16, 24, FontWeight.Normal),
    bodyMedium = sans(14, 20, FontWeight.Normal),
    bodySmall = sans(12, 16, FontWeight.Normal),
    labelLarge = sans(14, 20, FontWeight.SemiBold, 0.1),
    labelMedium = sans(12, 16, FontWeight.SemiBold, 0.4),
    // Overline: upper case is applied by the composable (HotOverline), never to the word «HoT».
    labelSmall = sans(11, 16, FontWeight.SemiBold).copy(letterSpacing = 0.16.em),
)

/** HoT styles outside the M3 scale (A36 §3). */
object HotType {
    /** One phrase of the day, the week summary. */
    val quote = TextStyle(
        fontFamily = Cormorant,
        fontStyle = FontStyle.Italic,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    )

    /** Key numbers: Заряд, sleep, metrics (≥ 22 sp, A36 §11 rule 3). */
    val numeral = sans(24, 30, FontWeight.SemiBold)

    /** Workout and practice timers. */
    val timer = sans(72, 80, FontWeight.Light, -1.5)

    /** The «HoT» wordmark next to the mark (brand/mark-v2 §5: Cormorant SemiBold, tracking 0.12 em). */
    val wordmark = TextStyle(
        fontFamily = Cormorant,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.12.em,
    )

    /** SOS headline: sober Manrope, no antiqua on the crisis screen (A36 §11 rule 7). */
    val sosHeadline = sans(26, 32, FontWeight.SemiBold)
}
