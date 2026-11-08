package com.healthofatitan.app.feature.today

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthofatitan.app.core.ui.R
import com.healthofatitan.app.core.ui.component.HotMode
import com.healthofatitan.app.core.ui.component.HotOverline
import com.healthofatitan.app.core.ui.component.ModeWord
import com.healthofatitan.app.core.ui.theme.Geist
import com.healthofatitan.app.core.ui.theme.Titanium
import com.healthofatitan.app.domain.today.TodayCopy
import kotlin.math.roundToInt

const val TODAY_SCENE_TAG = "today_scene"

/**
 * The sky of a mode (A66 §1.4 layer 1, A37 «Солнце · Лёд · Ирис»): the vertical gradient of the
 * scene and the colour of the light around the sun. No mode yet (night, no source): a night sky.
 */
internal class SceneSky(val stops: List<Color>, val glow: Color, val sun: Color, val haze: Color?) {
    companion object {
        private val NIGHT = SceneSky(
            listOf(Color(0xFF0B0D10), Color(0xFF0E1013), Color(0xFF0E1013), Color(0xFF0B0C0E), Titanium.Depth),
            glow = Color(0xFF8D97A3),
            sun = Color(0xFFE9EEF3),
            haze = null,
        )

        fun of(mode: HotMode?): SceneSky = when (mode) {
            // stops of mockup 05b (gen.py scene_today): night above, the warm light at the horizon
            HotMode.RAZGON -> SceneSky(
                listOf(Color(0xFF14181D), Color(0xFF171A1F), Color(0xFF1A1712), Color(0xFF0E1013), Titanium.Depth),
                glow = Color(0xFFFFB067),
                sun = Color(0xFFFFBE78),
                haze = null,
            )
            HotMode.ROVNO -> SceneSky(
                listOf(Color(0xFF13181C), Color(0xFF151B1F), Color(0xFF121A1A), Color(0xFF0E1013), Titanium.Depth),
                glow = Color(0xFF9FEADC),
                sun = Color(0xFFFFE1AE),
                haze = null,
            )
            HotMode.BEREZHNO -> SceneSky(
                listOf(Color(0xFF15151F), Color(0xFF181824), Color(0xFF17162A), Color(0xFF0E1013), Titanium.Depth),
                glow = Color(0xFFA8A3FF),
                sun = Color(0xFFC9C5FF),
                haze = Color(0xFFBDB8FF),
            )
            null -> NIGHT
        }
    }
}

/** The light of the columns (A66 §1.1): gold from the base up to the share of the closed day. */
private val ColumnGlow = Color(0xFFFFBE6E)

/**
 * The render of the Titan and his columns, quieter (A66 §3.2а): saturation 0.55, brightness 0.85.
 */
private val QuietColumns: ColorFilter = ColorFilter.colorMatrix(
    ColorMatrix().apply {
        setToSaturation(TodayTopRules.COLUMN_SATURATION)
        val b = TodayTopRules.COLUMN_BRIGHTNESS
        timesAssign(ColorMatrix().apply { setToScale(b, b, b, 1f) })
    },
)

/**
 * «Сегодня» scene, variant B «Тише» (A66 v0.5 §3.2а, §1.4): the sky of the mode under the header,
 * the sun disk on the axis of the Titan behind the figure (its height is Заряд to the norm,
 * linear), the Titan between his quieter columns, their glow rising by the share of the closed
 * day, and the word of the mode on the ground under the feet. One TalkBack node with [spoken]; a
 * tap opens «Почему так». The sun is the one glow of the screen.
 *
 * Layer 2 is the placeholder `core_ui_titan_front` until the render comes (A66 §11.2). The sky is
 * a plain gradient (no AGSL yet). «Удалить анимацию» (A66 §1.3): with animators off the sun and the
 * columns stand at once in their end frame.
 *
 * @param charge Заряд 5–95; null shows no sun (no data, calibration, the night is being counted)
 * @param dayShare the share of the closed day 0…1
 */
@Composable
internal fun TodayScene(
    mode: HotMode?,
    charge: Int?,
    dayShare: Float,
    estimate: Boolean,
    spoken: String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val animate = remember { ValueAnimator.areAnimatorsEnabled() }
    // «Короткое приветствие» (A66 §3.10): the sun rises to its height from 92 %; columns from 0
    val sun = remember { Animatable((charge ?: 0) * if (animate) GREET_FROM else 1f) }
    val glow = remember { Animatable(if (animate) 0f else dayShare) }
    LaunchedEffect(charge, animate) {
        val target = charge?.toFloat() ?: return@LaunchedEffect
        if (animate) sun.animateTo(target, spring(dampingRatio = 0.85f, stiffness = 60f)) else sun.snapTo(target)
    }
    LaunchedEffect(dayShare, animate) {
        if (animate) glow.animateTo(dayShare, tween(COLUMN_MS)) else glow.snapTo(dayShare)
    }
    val sky = SceneSky.of(mode)
    val tap = onOpen
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(TodayTopRules.SCENE_HEIGHT.dp)
            .clipToBounds()
            .testTag(ME_WHY_TAG)
            .clickable(role = Role.Button, onClickLabel = TodayCopy.WHY_TITLE, onClick = onOpen)
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
                this.onClick(label = TodayCopy.WHY_TITLE) { tap(); true }
            },
    ) {
        // 280 : 412 of the width, the head never closer than 24 dp to the header; the axis is the centre
        val frame = TodayTopRules.frameSide(maxWidth.value)
        Spacer(
            Modifier
                .fillMaxSize()
                .testTag(TODAY_SCENE_TAG)
                .drawBehind {
                    drawRect(Brush.verticalGradient(*sky.stops.zip(SKY_STOPS) { c, at -> at to c }.toTypedArray()))
                    sky.haze?.let { h ->
                        drawRect(Brush.verticalGradient(0f to Color.Transparent, 0.55f to h.copy(alpha = 0.07f), 0.9f to Color.Transparent))
                    }
                    if (charge != null) {
                        val c = Offset(size.width / 2f, TodayTopRules.sunCenterY(sun.value, frame).dp.toPx())
                        val halo = (frame * SKY_GLOW_REACH).dp.toPx()
                        drawCircle(Brush.radialGradient(listOf(sky.glow.copy(alpha = TodayTopRules.SKY_GLOW), Color.Transparent), c, halo), halo, c)
                        val r = (TodayTopRules.SUN_DIAMETER / 2f).dp.toPx() / 0.72f
                        drawCircle(
                            Brush.radialGradient(
                                0f to Color(0xFFFFF8EC),
                                0.38f to Color(0xFFFFE2B4),
                                0.62f to sky.sun.copy(alpha = 0.8f),
                                0.72f to Color.Transparent,
                                center = c,
                                radius = r,
                            ),
                            r,
                            c,
                        )
                    }
                },
        )
        // Layer 2: the Titan and his columns; layer 3: the glow, only where the frame is opaque.
        Box(
            Modifier
                .layout { measurable, constraints ->
                    val side = frame.dp.roundToPx()
                    val placeable = measurable.measure(Constraints.fixed(side, side))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        val left = ((constraints.maxWidth - side) / 2f).roundToInt()
                        val top = (TodayTopRules.feetY.dp.toPx() - TodayTopRules.FEET * side).roundToInt()
                        placeable.place(left, top)
                    }
                }
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val share = glow.value.coerceIn(0f, 1f)
                    if (share > 0f) {
                        val s = size.width
                        val base = TodayTopRules.BASE * s
                        val top = TodayTopRules.glowTop(share) * s
                        val a = TodayTopRules.COLUMN_GLOW
                        val brush = Brush.verticalGradient(
                            0f to ColumnGlow.copy(alpha = 0f),
                            0.15f to ColumnGlow.copy(alpha = a / 3f),
                            1f to ColumnGlow.copy(alpha = a),
                            startY = top,
                            endY = base,
                        )
                        TodayTopRules.COLUMNS.forEach { x ->
                            drawRect(
                                brush,
                                topLeft = Offset(x.start * s, top),
                                size = Size((x.endInclusive - x.start) * s, base - top),
                                blendMode = BlendMode.SrcAtop,
                            )
                        }
                    }
                },
        ) {
            Image(
                painter = painterResource(R.drawable.core_ui_titan_front),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                colorFilter = QuietColumns,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // the ground: from 10 dp above the feet down to the depth, the mode word stands on it
        Spacer(
            Modifier
                .fillMaxWidth()
                .height((TodayTopRules.SCENE_HEIGHT - TodayTopRules.feetY + 10f).dp)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Titanium.Titan1.copy(alpha = 0.9f)))),
        )
        if (mode != null) {
            // A66 §3.2а: the word of the mode under the scene, centred, no frame
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = TodayTopRules.MODE_WORD_TOP.dp)
                    .height(TodayTopRules.MODE_WORD_HEIGHT.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ModeWord(
                    mode,
                    style = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
                    glyphSize = 16.dp,
                    gap = 6.dp,
                )
                // S2: without WHOOP the mode is the default day and self-report
                if (estimate) {
                    Spacer(Modifier.width(8.dp))
                    HotOverline(TodayCopy.ESTIMATE, modifier = Modifier.testTag(ME_ESTIMATE_TAG))
                }
            }
        }
    }
}

private const val GREET_FROM = 0.92f

/** The halo of the sky around the sun, as a share of the frame (mockup: 0.55 × S). */
private const val SKY_GLOW_REACH = 0.55f

/** HotMotion `COLUMN_MS` (A66 §1.3). */
private const val COLUMN_MS = 600

/** Where the five sky colours stand (from the top of the scene to the ground). */
private val SKY_STOPS = listOf(0f, 0.24f, 0.64f, 0.835f, 1f)
