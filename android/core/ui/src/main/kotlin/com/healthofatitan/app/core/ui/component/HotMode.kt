package com.healthofatitan.app.core.ui.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.core.ui.theme.Titanium

/**
 * The three day modes as the UI sees them (A09 §2 regimes, A37 §9.1 «режимы — это свет»).
 * Colour always comes with [ModeGlyph] and the word: never colour alone.
 *
 * @param sunHeight how high the sun-ring stands on «Рассвет»: share of its radius above the
 *   horizon (A37 §4: Разгон above, Ровно half, Бережно at the horizon)
 */
enum class HotMode(val word: String, val sunHeight: Float, val dashed: Boolean) {
    RAZGON("Разгон", 1.35f, false),
    ROVNO("Ровно", 0.55f, false),
    BEREZHNO("Бережно", 0.05f, true),
    ;

    @Composable
    fun color(): Color {
        val t = HotThemeTokens.colors
        return when (this) {
            RAZGON -> t.modeRazgon
            ROVNO -> t.modeRovno
            BEREZHNO -> t.modeBerezhno
        }
    }

    /** The line of light on the Titan at dawn (A37 §4: amber, warm, lilac silver). */
    fun dawnLight(): List<Color> = when (this) {
        RAZGON -> listOf(Color.White, Color(0xFFFFE1AE), Color(0xFFFFB067))
        ROVNO -> Titanium.LightWarm
        BEREZHNO -> Titanium.LightIris
    }

    /** The sun-ring and the sky at the horizon (A37 §4 table). */
    fun sun(): Color = when (this) {
        RAZGON -> Color(0xFFFFD08A)
        ROVNO -> Color(0xFFFFE1AE)
        BEREZHNO -> Color(0xFFC9C5FF)
    }

    fun horizon(): Color = when (this) {
        RAZGON -> Color(0xFFF2A65E)
        ROVNO -> Color(0xFFD9935E)
        BEREZHNO -> Color(0xFF8F86D9)
    }
}

/**
 * Mode pictogram of the A37 grammar (§6 «режимы: солнце-кольцо на разной высоте»): a horizon line
 * and the sun-ring standing above it as high as the mode; dashed in «Бережно».
 */
@Composable
fun ModeGlyph(mode: HotMode, modifier: Modifier = Modifier, size: Dp = 24.dp, color: Color = mode.color()) {
    Spacer(
        modifier
            .size(size)
            .clearAndSetSemantics { }
            .drawBehind {
                val w = this.size.width
                val stroke = (w / 15f).coerceAtLeast(1.4f)
                val horizonY = w * 0.74f
                val r = w * 0.27f
                val cy = horizonY - r * mode.sunHeight.coerceAtMost(1.1f)
                val c = Offset(w / 2f, cy)
                val dash = if (mode.dashed) PathEffect.dashPathEffect(floatArrayOf(stroke * 0.9f, stroke * 1.4f)) else null
                clipRect(bottom = horizonY) {
                    drawCircle(color, r, c, style = Stroke(stroke, cap = StrokeCap.Round, pathEffect = dash))
                    // the Titan between two columns, reduced to three strokes
                    val top = c.y - r * 0.45f
                    drawLine(color, Offset(w / 2f, top), Offset(w / 2f, horizonY), stroke * 0.9f, StrokeCap.Round)
                    drawLine(color, Offset(w / 2f - r * 0.55f, top + r * 0.35f), Offset(w / 2f + r * 0.55f, top + r * 0.35f), stroke * 0.9f, StrokeCap.Round)
                }
                if (mode == HotMode.RAZGON) {
                    for (i in -1..1) {
                        val a = Math.toRadians(-90.0 + i * 40.0)
                        val from = Offset(c.x + (r + stroke * 1.6f) * kotlin.math.cos(a).toFloat(), c.y + (r + stroke * 1.6f) * kotlin.math.sin(a).toFloat())
                        val to = Offset(c.x + (r + stroke * 3.2f) * kotlin.math.cos(a).toFloat(), c.y + (r + stroke * 3.2f) * kotlin.math.sin(a).toFloat())
                        drawLine(color, from, to, stroke * 0.9f, StrokeCap.Round)
                    }
                }
                drawLine(color, Offset(w * 0.1f, horizonY), Offset(w * 0.9f, horizonY), stroke, StrokeCap.Round)
            },
    )
}

/** Glyph + word of the mode, one TalkBack node «Режим: Ровно». */
@Composable
fun ModeWord(mode: HotMode, style: TextStyle, modifier: Modifier = Modifier, glyphSize: Dp = 40.dp, color: Color = mode.color(), gap: Dp = 14.dp) {
    Row(
        modifier.semantics(mergeDescendants = true) { contentDescription = "Режим: ${mode.word}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModeGlyph(mode, size = glyphSize, color = color)
        Spacer(Modifier.width(gap))
        Text(mode.word, style = style, color = color, modifier = Modifier.clearAndSetSemantics { })
    }
}
