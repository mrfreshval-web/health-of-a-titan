package com.healthofatitan.app.feature.week

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.healthofatitan.app.core.ui.component.HotMode
import com.healthofatitan.app.core.ui.component.TitanLight
import com.healthofatitan.app.core.ui.component.TitanLights
import com.healthofatitan.app.core.ui.component.TitanLottie
import com.healthofatitan.app.core.ui.component.TitanLottieFigure
import com.healthofatitan.app.core.ui.component.TitanScene
import com.healthofatitan.app.core.ui.component.rememberTitanLottie
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.domain.today.DayMode
import com.healthofatitan.app.domain.today.week.TitanWeekLook
import com.healthofatitan.app.domain.today.week.WeekPose
import com.healthofatitan.app.domain.today.week.WeekSky
import kotlin.random.Random

fun DayMode.hotMode(): HotMode = when (this) {
    DayMode.RAZGON -> HotMode.RAZGON
    DayMode.ROVNO -> HotMode.ROVNO
    DayMode.BEREZHNO -> HotMode.BEREZHNO
}

/**
 * Placeholder graphics of the Titan of the week (A52 §2.7) until the pack of Agents 4 and 17: the
 * pose is a key frame (`poster`) of a TitanLottie file, the light is the mode light of the week.
 * The mapping lives here only, so the final pack replaces one table.
 */
object WeekTitanArt {
    fun lottieId(pose: WeekPose): String = when (pose) {
        WeekPose.REST -> "ex-breath-long-exhale"
        WeekPose.STAND -> TitanLottie.HERO_TEST_PASSED
        WeekPose.HOLD -> TitanLottie.HERO_SEAL
        WeekPose.RISE -> TitanLottie.HERO_LEVEL_UP
    }

    /** The sky of the week, top to horizon. Never red, never black-and-stormy (W-02, A53 §3.1). */
    fun sky(sky: WeekSky, dark: Boolean): List<Color> = when (sky) {
        WeekSky.CLEAR -> if (dark) listOf(Color(0xFF0F1A2A), Color(0xFF1E3550)) else listOf(Color(0xFFDCE8F5), Color(0xFFF4F7FB))
        WeekSky.HAZE -> if (dark) listOf(Color(0xFF14181F), Color(0xFF283240)) else listOf(Color(0xFFE6E8EC), Color(0xFFF5F5F6))
        WeekSky.DUSK -> if (dark) listOf(Color(0xFF17142A), Color(0xFF2E2848)) else listOf(Color(0xFFE6E2F2), Color(0xFFF6F4FA))
    }
}

/**
 * The Titan of the week: the sky (sleep), five columns (the supports of the week), the figure
 * (pose and light by «Дни в опоре»), coming out of its outline by [TitanWeekLook.reveal].
 * [revealOverride] lets the ritual animate the last step on Sunday.
 */
@Composable
fun WeekTitanFigure(look: TitanWeekLook, modifier: Modifier = Modifier, revealOverride: Float? = null) {
    val tokens = HotThemeTokens.colors
    val mode = look.mode.hotMode()
    val reveal = (revealOverride ?: look.reveal).coerceIn(0f, 1f)
    val modeColor = mode.color()
    val sky = WeekTitanArt.sky(look.sky, tokens.isDark)
    val outline = if (tokens.isDark) Color.White.copy(alpha = 0.28f) else Color.Black.copy(alpha = 0.22f)
    Box(modifier.testTag(WEEK_TITAN_TAG).semantics { contentDescription = look.spoken }) {
        Canvas(Modifier.fillMaxSize()) {
            val r = CornerRadius(26.dp.toPx())
            drawRoundRect(Brush.verticalGradient(sky), cornerRadius = r)
            // stars: the seed makes each week's sky its own; more light, more stars
            val rnd = Random(look.seed)
            repeat(10 + (look.light * 18).toInt()) {
                val p = Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height * 0.55f)
                drawCircle(Color.White.copy(alpha = (0.15f + 0.5f * rnd.nextFloat()) * (0.3f + 0.7f * reveal)), radius = 1.dp.toPx() + rnd.nextFloat() * 1.2f.dp.toPx(), center = p)
            }
            // the five columns of the supports, left and right of the figure, lit by their fill
            val base = size.height * 0.92f
            val maxH = size.height * 0.62f
            val w = size.width * 0.07f
            val xs = listOf(0.08f, 0.2f, 0.8f, 0.92f, 0.5f)
            look.columns.forEachIndexed { i, fill ->
                if (i == 4) return@forEachIndexed // the fifth support is the plinth under the Titan
                val h = maxH * (0.25f + 0.75f * fill)
                val x = size.width * xs[i] - w / 2
                drawRoundRect(outline, Offset(x, base - maxH), Size(w, maxH), CornerRadius(w / 2), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))))
                drawRoundRect(
                    Brush.verticalGradient(listOf(modeColor.copy(alpha = 0.25f + 0.55f * fill * reveal), modeColor.copy(alpha = 0.08f)), startY = base - h, endY = base),
                    Offset(x, base - h), Size(w, h), CornerRadius(w / 2),
                )
            }
            val plinth = look.columns.getOrElse(4) { 0f }
            drawRoundRect(
                modeColor.copy(alpha = 0.18f + 0.5f * plinth * reveal),
                Offset(size.width * 0.32f, base), Size(size.width * 0.36f, 4.dp.toPx()), CornerRadius(2.dp.toPx()),
            )
        }
        val manifest = rememberTitanLottie()
        val entry = manifest[WeekTitanArt.lottieId(look.pose)]
        val figure = Modifier.fillMaxWidth().fillMaxSize().padding(horizontal = 56.dp, vertical = 10.dp)
        val light = weekLight(mode, look.light)
        // the outline is always there; the light comes with the data (contour → light)
        TitanScene(
            modifier = figure.alpha(0.35f * (1f - reveal) + 0.05f),
            light = TitanLights.default(),
            ring = { 0f },
        )
        if (entry != null) {
            TitanLottieFigure(
                entry = entry,
                cycleMs = 0L,
                light = light.line,
                glow = light.glow?.takeIf { reveal > 0.5f },
                accent = modeColor,
                modifier = figure.alpha(0.15f + 0.85f * reveal),
                animate = false,
            )
        } else {
            TitanScene(
                modifier = figure.alpha(0.15f + 0.85f * reveal),
                light = light,
                ring = { look.daysInSupport / 7f * reveal },
                ringDashed = look.mode == DayMode.BEREZHNO,
            )
        }
    }
}

/** The light of the week: the mode light, softer when fewer days were in support (never darker than a third). */
@Composable
private fun weekLight(mode: HotMode, level: Float): TitanLight {
    val lit = TitanLights.of(mode)
    return lit.copy(glowStrength = level.coerceIn(0.35f, 1f))
}

const val WEEK_TITAN_TAG = "week_titan"
