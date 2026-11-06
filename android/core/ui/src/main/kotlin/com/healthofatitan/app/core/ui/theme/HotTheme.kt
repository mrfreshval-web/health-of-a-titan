package com.healthofatitan.app.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/*
 * A36 «Колоннада» §2 and §9. Text pairs are checked to WCAG AA (body text 13.9 : 1 on Basalt).
 * `surfaceTint = Transparent`: no M3 tonal tint on raised surfaces, depth = tone + hairline.
 * Error roles are exactly the A11 alert tokens; red appears nowhere else (RedOnlyForFlagsTest).
 */
internal val HotDarkScheme: ColorScheme = darkColorScheme(
    primary = Kolonnada.Gold,
    onPrimary = Kolonnada.OnGold,
    primaryContainer = Kolonnada.Bronze,
    onPrimaryContainer = Kolonnada.GoldHi,
    inversePrimary = Kolonnada.GoldDeep,
    secondary = Color(0xFFCFC6B3),
    onSecondary = Color(0xFF23201A),
    secondaryContainer = Color(0xFF2A2F3A),
    onSecondaryContainer = Color(0xFFE6DECB),
    tertiary = Color(0xFF8FC2B2), // Патина
    onTertiary = Color(0xFF0E2A23),
    tertiaryContainer = Color(0xFF1C3A33),
    onTertiaryContainer = Color(0xFFBFE3D7),
    background = Kolonnada.Night,
    onBackground = Kolonnada.Ivory,
    surface = Kolonnada.Night,
    onSurface = Kolonnada.Ivory,
    surfaceVariant = Kolonnada.SlatePlus,
    onSurfaceVariant = Kolonnada.Travertine,
    surfaceTint = Color.Transparent,
    inverseSurface = Kolonnada.Ivory,
    inverseOnSurface = Kolonnada.Night,
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    outline = Color(0xFF7A7363),
    outlineVariant = Color(0xFF262B35),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF2A3140),
    surfaceDim = Kolonnada.Night,
    surfaceContainerLowest = Kolonnada.Obsidian,
    surfaceContainerLow = Kolonnada.Graphite,
    surfaceContainer = Kolonnada.Basalt,
    surfaceContainerHigh = Kolonnada.Slate,
    surfaceContainerHighest = Kolonnada.SlatePlus,
)

internal val HotLightScheme: ColorScheme = lightColorScheme(
    primary = Kolonnada.GoldDeep,
    onPrimary = Color.White,
    primaryContainer = Kolonnada.GoldHi,
    onPrimaryContainer = Color(0xFF2B210A),
    inversePrimary = Kolonnada.Gold,
    secondary = Color(0xFF5F5747),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE5D3),
    onSecondaryContainer = Color(0xFF231F17),
    tertiary = Color(0xFF2F6B5C),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCFE9E0),
    onTertiaryContainer = Color(0xFF07211A),
    background = Kolonnada.Marble,
    onBackground = Kolonnada.Ink,
    surface = Kolonnada.Marble,
    onSurface = Kolonnada.Ink,
    surfaceVariant = Color(0xFFE9E2D4),
    onSurfaceVariant = Kolonnada.Sepia,
    surfaceTint = Color.Transparent,
    inverseSurface = Color(0xFF232733),
    inverseOnSurface = Kolonnada.Marble,
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    outline = Color(0xFF857C6B),
    outlineVariant = Color(0xFFD8CFBE),
    scrim = Color(0xFF000000),
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFE2DACB),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAF6EE),
    surfaceContainer = Color(0xFFEFE9DD),
    surfaceContainerHigh = Color(0xFFE9E2D4),
    surfaceContainerHighest = Color(0xFFE2DACB),
)

/** Architectural shapes, softer than the system ones (A36 §6). Buttons are capsules (CircleShape). */
val HotShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * App theme «Колоннада». Dark «Ночь» is the default (A36 §11 rule 4); light «Мрамор» comes with
 * the «Авто днём» setting. Material You is off by default: gold is part of the brand (A36 §2.5);
 * when switched on, the brand is still carried by the fonts, lines and glyphs.
 */
@Composable
fun HotTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val scheme = when {
        dynamicColor -> { // minSdk 34 ≥ S: Material You is always available
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> HotDarkScheme
        else -> HotLightScheme
    }
    CompositionLocalProvider(LocalHotColors provides if (darkTheme) DarkHotColors else LightHotColors) {
        MaterialTheme(colorScheme = scheme, typography = HotTypography, shapes = HotShapes, content = content)
    }
}

/** Facet, mode and line tokens of the current theme. */
object HotThemeTokens {
    val colors: HotColors
        @Composable get() = LocalHotColors.current
}
