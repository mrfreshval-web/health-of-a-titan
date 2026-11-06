package com.healthofatitan.app.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/*
 * Fallback schemes from the brand seed #C9A23A (A11 §12.1), tonal values in the spirit of
 * Material Theme Builder ("Tonal spot"). TODO(slice 2): regenerate with Material Theme Builder
 * and verify contrast ≥ 4.5:1 (A11 §12.5).
 * Error roles use exactly the A11 alert tokens; red appears nowhere else.
 */
internal val HotDarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFE6C364),
    onPrimary = Color(0xFF3D2E00),
    primaryContainer = Color(0xFF584400),
    onPrimaryContainer = Color(0xFFFFE08F),
    inversePrimary = Color(0xFF735C0C),
    secondary = Color(0xFFD6C5A0),
    onSecondary = Color(0xFF3A3016),
    secondaryContainer = Color(0xFF52462A),
    onSecondaryContainer = Color(0xFFF3E1BB),
    tertiary = Color(0xFFAECFA7),
    onTertiary = Color(0xFF1A3719),
    tertiaryContainer = Color(0xFF314E2E),
    onTertiaryContainer = Color(0xFFC9EBC1),
    background = Color(0xFF16130B),
    onBackground = Color(0xFFEAE1D4),
    surface = Color(0xFF16130B),
    onSurface = Color(0xFFEAE1D4),
    surfaceVariant = Color(0xFF4C4639),
    onSurfaceVariant = Color(0xFFCFC5B4),
    surfaceTint = Color(0xFFE6C364),
    inverseSurface = Color(0xFFEAE1D4),
    inverseOnSurface = Color(0xFF353027),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    outline = Color(0xFF989080),
    outlineVariant = Color(0xFF4C4639),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF3D392F),
    surfaceDim = Color(0xFF16130B),
    surfaceContainerLowest = Color(0xFF110E07),
    surfaceContainerLow = Color(0xFF1F1B13),
    surfaceContainer = Color(0xFF231F17),
    surfaceContainerHigh = Color(0xFF2E2A21),
    surfaceContainerHighest = Color(0xFF39342B),
)

internal val HotLightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF735C0C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFE08F),
    onPrimaryContainer = Color(0xFF241A00),
    inversePrimary = Color(0xFFE6C364),
    secondary = Color(0xFF695D3F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF3E1BB),
    onSecondaryContainer = Color(0xFF231B04),
    tertiary = Color(0xFF486643),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC9EBC1),
    onTertiaryContainer = Color(0xFF052106),
    background = Color(0xFFFFF8F0),
    onBackground = Color(0xFF1F1B13),
    surface = Color(0xFFFFF8F0),
    onSurface = Color(0xFF1F1B13),
    surfaceVariant = Color(0xFFECE1CF),
    onSurfaceVariant = Color(0xFF4C4639),
    surfaceTint = Color(0xFF735C0C),
    inverseSurface = Color(0xFF353027),
    inverseOnSurface = Color(0xFFF8EFE2),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    outline = Color(0xFF7E7667),
    outlineVariant = Color(0xFFCFC5B4),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFF8F0),
    surfaceDim = Color(0xFFE2D9CC),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFCF2E5),
    surfaceContainer = Color(0xFFF6ECDF),
    surfaceContainerHigh = Color(0xFFF0E7D9),
    surfaceContainerHighest = Color(0xFFEAE1D4),
)

/**
 * App theme. Dark by default (A11 §12.1, brand: gold on dark). Material You is supported but
 * off until the «Я → Настройки → Тема» setting exists (slice 2+).
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
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/** Facet/mode tokens of the current theme. */
object HotThemeTokens {
    val colors: HotColors
        @Composable get() = LocalHotColors.current
}
