package com.healthofatitan.app.feature.today

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.healthofatitan.app.core.model.nav.HotRoute
import com.healthofatitan.app.core.ui.component.HotMode
import com.healthofatitan.app.core.ui.component.HotOverline
import com.healthofatitan.app.core.ui.component.HotPrimaryButton
import com.healthofatitan.app.core.ui.component.HotSpacing
import com.healthofatitan.app.core.ui.component.ModeWord
import com.healthofatitan.app.core.ui.component.TitanLight
import com.healthofatitan.app.core.ui.component.TitanScene
import com.healthofatitan.app.core.ui.component.rememberReduceMotion
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.core.ui.theme.HotType
import com.healthofatitan.app.core.ui.theme.Titanium
import com.healthofatitan.app.integration.whoop.day.DawnRepository
import com.healthofatitan.app.integration.whoop.day.DawnView
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

const val DAWN_SCREEN_TAG = "screen_dawn"
const val DAWN_START_TAG = "dawn_start"

/** «Рассвет» (A37 §4, wow 2): full screen, no tabs; «Начать день» goes on to «Сегодня». */
fun NavGraphBuilder.dawnScreen(onStart: () -> Unit) {
    composable(route = HotRoute.DAWN) { DawnRoute(onStart) }
}

@HiltViewModel
class DawnViewModel @Inject constructor(private val dawn: DawnRepository) : ViewModel() {
    private val view = MutableStateFlow<DawnView?>(null)
    val state: StateFlow<DawnView?> = view.asStateFlow()

    init {
        viewModelScope.launch { view.value = runCatching { dawn.today() }.getOrNull() }
    }
}

@Composable
private fun DawnRoute(onStart: () -> Unit, viewModel: DawnViewModel = hiltViewModel()) {
    val view by viewModel.state.collectAsStateWithLifecycle()
    DawnScreen(view, onStart)
}

/**
 * The dawn: a sky that turns into the mode's light at the horizon, the sun-ring standing as high
 * as the mode and the night's Заряд, the dark Titan with a line of light on its contour, then the
 * haiku (its third line brighter), the mode word, three numbers and «Начать день». 1.6 s; with
 * «remove animations» the final frame at once.
 */
@Composable
fun DawnScreen(view: DawnView?, onStart: () -> Unit, modifier: Modifier = Modifier) {
    val reduce = rememberReduceMotion()
    val rise = remember { Animatable(if (reduce) 1f else 0f) }
    val text = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(view != null) {
        if (view == null || reduce) return@LaunchedEffect
        launch { rise.animateTo(1f, tween(1600, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))) }
        text.animateTo(1f, tween(700, delayMillis = 900))
    }
    val mode = view?.regime?.mode() ?: HotMode.ROVNO
    val sun = mode.sun()
    val horizon = mode.horizon()
    val tokens = HotThemeTokens.colors
    val sky = if (tokens.isDark) Titanium.Depth else Titanium.Porcelain

    Box(
        modifier
            .fillMaxSize()
            .background(sky)
            .testTag(DAWN_SCREEN_TAG),
    ) {
        // the sky: depth above, the mode's warmth at the horizon (≈ 46 % from the top)
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
            val hy = size.height * 0.46f
            drawRect(
                Brush.verticalGradient(
                    0f to sky,
                    0.30f to sky,
                    0.46f to horizon.copy(alpha = if (tokens.isDark) 0.55f else 0.35f),
                    0.62f to sky,
                ),
            )
            drawCircle(
                Brush.radialGradient(listOf(sun.copy(alpha = 0.30f * rise.value), Color.Transparent), center = Offset(size.width / 2f, hy), radius = size.width * 0.8f),
                radius = size.width * 0.8f,
                center = Offset(size.width / 2f, hy),
            )
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = HotSpacing.edge, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            view?.let { HotOverline(TitanTexts.date(it.day)) }
            Spacer(Modifier.height(8.dp))
            DawnSky(mode, view?.sunHeight ?: 0.5f, rise::value)
            Column(
                Modifier.graphicsLayer {
                    alpha = text.value
                    translationY = (1f - text.value) * 16.dp.toPx()
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                view?.haiku?.let { haiku ->
                    Spacer(Modifier.height(8.dp))
                    Column(
                        Modifier.semantics(mergeDescendants = true) { contentDescription = haiku.lines.joinToString(". ") },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        haiku.lines.forEachIndexed { i, line ->
                            Text(
                                line,
                                style = HotType.quote,
                                textAlign = TextAlign.Center,
                                color = if (i == haiku.lines.lastIndex) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
                if (view != null) {
                    val big = LocalDensity.current.fontScale >= 2f
                    ModeWord(
                        mode,
                        style = if (big) HotType.display.copy(fontSize = 55.sp, lineHeight = 60.sp) else HotType.display,
                        color = sun.takeIf { tokens.isDark } ?: mode.color(),
                        glyphSize = 48.dp,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.height(24.dp))
                    DawnNumbers(view)
                }
                Spacer(Modifier.height(32.dp))
                HotPrimaryButton(
                    TitanTexts.START_DAY,
                    onClick = onStart,
                    trailingArrow = true,
                    modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp).testTag(DAWN_START_TAG),
                )
                Spacer(Modifier.height(16.dp))
                HotOverline(TitanTexts.synced(view?.syncedAt))
            }
        }
    }
}

/** The sun-ring rising behind the dark Titan, the line of light on his contour in the mode's colour. */
@Composable
private fun DawnSky(mode: HotMode, sunHeight: Float, rise: () -> Float) {
    val tokens = HotThemeTokens.colors
    val sun = mode.sun()
    val horizonColor = mode.horizon()
    Box(Modifier.fillMaxWidth().widthIn(max = 380.dp).aspectRatio(1f)) {
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
            val w = size.width
            val hy = w * 0.80f
            val r = w * 0.36f
            // final centre: from the horizon (0) up to a ring standing fully above it (1)
            val target = hy - r * (0.15f + 1.05f * sunHeight)
            val cy = hy - (hy - target) * rise()
            val stroke = 3.dp.toPx()
            val dash = if (mode.dashed) PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 7.dp.toPx())) else null
            clipRect(bottom = hy) {
                if (tokens.glow) {
                    drawCircle(
                        Brush.radialGradient(listOf(sun.copy(alpha = 0.35f), Color.Transparent), center = Offset(w / 2f, cy), radius = r * 1.5f),
                        radius = r * 1.5f,
                        center = Offset(w / 2f, cy),
                    )
                }
                drawCircle(sun, r, Offset(w / 2f, cy), style = Stroke(stroke, cap = StrokeCap.Round, pathEffect = dash))
            }
            drawLine(
                Brush.horizontalGradient(listOf(Color.Transparent, horizonColor, Color.Transparent)),
                Offset(0f, hy),
                Offset(w, hy),
                1.5.dp.toPx(),
            )
        }
        TitanScene(
            modifier = Modifier.fillMaxSize().padding(top = 36.dp),
            light = TitanLight(
                line = mode.dawnLight(),
                column = tokens.columnLight,
                knockout = if (tokens.isDark) Titanium.Depth else Titanium.Porcelain,
                glow = sun.takeIf { tokens.glow },
                glowStrength = 0.6f,
            ),
            titanAlpha = { (rise() * 1.6f).coerceIn(0f, 1f) },
            titanLift = { (1f - rise()) * 0.04f },
            ring = { 0f },
        )
    }
}

@Composable
private fun DawnNumbers(view: DawnView) {
    Row(
        Modifier.semantics(mergeDescendants = true) {
            contentDescription = TitanTexts.dawnSpoken(view.sleepHours, view.charge, view.strainCeiling)
        },
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        view.sleepHours?.let { DawnNumber(TitanTexts.hm(it), TitanTexts.SLEEP) }
        view.charge?.let { DawnNumber(it.toString(), TitanTexts.CHARGE) }
        DawnNumber(TitanTexts.ceiling(view.strainCeiling), TitanTexts.CEILING)
    }
}

@Composable
private fun DawnNumber(value: String, label: String) {
    Column(Modifier.clearAndSetSemantics { }, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = HotType.numeral, color = MaterialTheme.colorScheme.onSurface)
        HotOverline(label)
    }
}
