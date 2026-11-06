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
 * A37 «Титаниум» §9.1–9.2. Contrast of text pairs to WCAG AA is listed in A37 (Кость 17.9 : 1 on
 * Глубина). `surfaceTint = Transparent`: no M3 tonal tint, depth = light + glass + hairline.
 * Error roles stay exactly the A11 alert tokens: the crisis card does not change with the visual
 * concept (D-029), and red appears nowhere else (RedOnlyForFlagsTest).
 */
internal val HotDarkScheme: ColorScheme = darkColorScheme(
    primary = Titanium.Dawn,
    onPrimary = Titanium.OnDawn,
    primaryContainer = Titanium.DawnContainer,
    onPrimaryContainer = Titanium.DawnHi,
    inversePrimary = Titanium.DawnText,
    secondary = Titanium.Steel,
    onSecondary = Titanium.Depth,
    secondaryContainer = Titanium.Titan3,
    onSecondaryContainer = Titanium.Bone,
    tertiary = Color(0xFF7DE3D1), // Лёд
    onTertiary = Color(0xFF04201B),
    tertiaryContainer = Color(0xFF0F332D),
    onTertiaryContainer = Color(0xFFC4F4EA),
    background = Titanium.Depth,
    onBackground = Titanium.Bone,
    surface = Titanium.Depth,
    onSurface = Titanium.Bone,
    surfaceVariant = Titanium.Titan3,
    onSurfaceVariant = Titanium.Steel,
    surfaceTint = Color.Transparent,
    inverseSurface = Titanium.Bone,
    inverseOnSurface = Titanium.Depth,
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    outline = Color(0xFF3A3F46), // white 14 % on Глубина, opaque for M3 borders
    outlineVariant = Color(0xFF1A1D21), // white 8 %
    scrim = Color(0xFF000000),
    surfaceBright = Titanium.Titan4,
    surfaceDim = Titanium.Depth,
    surfaceContainerLowest = Titanium.Depth,
    surfaceContainerLow = Titanium.Titan1,
    surfaceContainer = Titanium.Titan2,
    surfaceContainerHigh = Titanium.Titan3,
    surfaceContainerHighest = Titanium.Titan4,
)

internal val HotLightScheme: ColorScheme = lightColorScheme(
    primary = Titanium.DawnText,
    onPrimary = Color.White,
    primaryContainer = Titanium.DawnPillHi,
    onPrimaryContainer = Titanium.OnDawn,
    inversePrimary = Titanium.Dawn,
    secondary = Titanium.InkSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6E5E1),
    onSecondaryContainer = Titanium.Ink,
    tertiary = Color(0xFF0E7A6A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCDEFE8),
    onTertiaryContainer = Color(0xFF02201B),
    background = Titanium.Porcelain,
    onBackground = Titanium.Ink,
    surface = Titanium.Porcelain,
    onSurface = Titanium.Ink,
    surfaceVariant = Color(0xFFE6E5E1),
    onSurfaceVariant = Titanium.InkSecondary,
    surfaceTint = Color.Transparent,
    inverseSurface = Color(0xFF1C2025),
    inverseOnSurface = Titanium.Porcelain,
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    outline = Color(0xFFC9C9C6),
    outlineVariant = Color(0xFFE0DFDB),
    scrim = Color(0xFF000000),
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFE6E5E1),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF9F8F6),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFECEBE8),
    surfaceContainerHighest = Color(0xFFE6E5E1),
)

/** A37 §9.5: capsules (28 dp) for buttons and navigation, cards 26–28 dp — softer than A36. */
val HotShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * App theme «Титаниум» (A37, D-031). Dark «Титан» is the default; light «Фарфор» comes with the
 * «Авто днём» setting. Material You is off by default (A37 §12): the dawn accent and the mode
 * lights are the brand.
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

/** Mode, light and glass tokens of the current theme. */
object HotThemeTokens {
    val colors: HotColors
        @Composable get() = LocalHotColors.current
}
