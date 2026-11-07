package com.healthofatitan.app.feature.today

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.healthofatitan.app.domain.engine.Body
import com.healthofatitan.app.domain.engine.DayFlag
import com.healthofatitan.app.domain.engine.FoodClose
import com.healthofatitan.app.domain.engine.ItemStatus

/**
 * Pictograms of the day (A37 §6 grammar, A39 §7): the Titan between two columns reduced to a few
 * strokes, so a flag, a feeling or a status reads as a pose, never as colour alone. Every glyph
 * carries its word as `contentDescription` (A36). Interim drawings until the illustrator's set.
 */
private enum class Pose { UPRIGHT, DASHED, TRAVEL, LEANING, LIGHT, HEAVY }

private fun DrawScope.titan(pose: Pose, color: Color) {
    val w = size.minDimension
    val s = (w / 16f).coerceAtLeast(1.3f)
    val dash = if (pose == Pose.DASHED) PathEffect.dashPathEffect(floatArrayOf(s * 1.1f, s * 1.3f)) else null
    fun line(a: Offset, b: Offset) = drawLine(color, a, b, s, StrokeCap.Round, pathEffect = dash)
    fun p(x: Float, y: Float) = Offset(x * w, y * w)

    // columns
    val tilt = if (pose == Pose.TRAVEL) 12f else 0f
    rotate(-tilt, p(0.2f, 0.86f)) { line(p(0.2f, 0.16f), p(0.2f, 0.86f)) }
    rotate(tilt, p(0.8f, 0.86f)) { line(p(0.8f, 0.16f), p(0.8f, 0.86f)) }

    // the figure, leaning onto the right column when it rests
    val lean = if (pose == Pose.LEANING) 16f else 0f
    rotate(lean, p(0.5f, 0.86f)) {
        val headY = if (pose == Pose.HEAVY) 0.32f else 0.26f
        drawCircle(color, w * 0.065f, p(0.5f, headY), style = Stroke(s, pathEffect = dash))
        val shoulder = p(0.5f, headY + 0.13f)
        line(p(0.5f, headY + 0.08f), p(0.5f, 0.64f))
        line(p(0.5f, 0.64f), p(0.42f, 0.86f))
        line(p(0.5f, 0.64f), p(0.58f, 0.86f))
        when (pose) {
            Pose.LIGHT -> {
                line(shoulder, p(0.24f, 0.2f))
                line(shoulder, p(0.76f, 0.2f))
            }
            Pose.HEAVY -> {
                line(shoulder, p(0.38f, 0.62f))
                line(shoulder, p(0.62f, 0.62f))
                // the weight on the shoulders
                line(p(0.3f, headY - 0.1f), p(0.7f, headY - 0.1f))
            }
            Pose.LEANING -> {
                line(shoulder, p(0.38f, 0.58f))
                line(shoulder, p(0.7f, shoulder.y / w + 0.06f))
            }
            else -> {
                line(shoulder, p(0.24f, shoulder.y / w))
                line(shoulder, p(0.76f, shoulder.y / w))
            }
        }
    }
}

@Composable
private fun Glyph(description: String, size: Dp, modifier: Modifier, draw: DrawScope.() -> Unit) {
    Spacer(
        modifier
            .size(size)
            .semantics { contentDescription = description }
            .drawBehind(draw),
    )
}

/** A day flag as a pose: dashed Titan (learning the norm), tilted columns (travel), leaning (a cold), ceiling, no data. */
@Composable
fun FlagGlyph(flag: DayFlag, color: Color, modifier: Modifier = Modifier, size: Dp = 28.dp) {
    Glyph(DayUiTexts.flag(flag), size, modifier) {
        when (flag) {
            DayFlag.CALIBRATION, DayFlag.PRELIMINARY_NORM -> titan(Pose.DASHED, color)
            DayFlag.TRAVEL -> titan(Pose.TRAVEL, color)
            DayFlag.SICK -> titan(Pose.LEANING, color)
            DayFlag.LIGHT_WEEK -> ceiling(color)
            DayFlag.MANUAL, DayFlag.DATA_LIMITED -> noData(color)
            // A37 §6: «возвращение» — the Titan in the light again
            DayFlag.RETURN -> titan(Pose.LIGHT, color)
        }
    }
}

/** «Как тело?»: light (arms up), sore (under weight), a cold (leaning on the column). */
@Composable
fun BodyGlyph(body: Body, label: String, color: Color, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Glyph(label, size, modifier) {
        titan(
            when (body) {
                Body.FINE -> Pose.LIGHT
                Body.SORE -> Pose.HEAVY
                Body.SICK -> Pose.LEANING
            },
            color,
        )
    }
}

/** A39 §7 «потолок»: a bar with a rising arrow stopping under it. */
private fun DrawScope.ceiling(color: Color) {
    val w = size.minDimension
    val s = (w / 14f).coerceAtLeast(1.3f)
    drawLine(color, Offset(w * 0.18f, w * 0.22f), Offset(w * 0.82f, w * 0.22f), s, StrokeCap.Round)
    drawLine(color, Offset(w * 0.5f, w * 0.84f), Offset(w * 0.5f, w * 0.36f), s, StrokeCap.Round)
    drawLine(color, Offset(w * 0.36f, w * 0.5f), Offset(w * 0.5f, w * 0.36f), s, StrokeCap.Round)
    drawLine(color, Offset(w * 0.64f, w * 0.5f), Offset(w * 0.5f, w * 0.36f), s, StrokeCap.Round)
}

/** «нет данных»: a dashed ring with an empty centre. */
private fun DrawScope.noData(color: Color) {
    val w = size.minDimension
    val s = (w / 14f).coerceAtLeast(1.3f)
    drawCircle(color, w * 0.36f, center, style = Stroke(s, pathEffect = PathEffect.dashPathEffect(floatArrayOf(s * 1.2f, s * 1.6f))))
    drawLine(color, Offset(w * 0.38f, w * 0.5f), Offset(w * 0.62f, w * 0.5f), s, StrokeCap.Round)
}

/** A35 status as a pictogram: ✓ in a filled ring (WHOOP), ✓ in a thin ring (self), ⇄ (changed). */
@Composable
fun StatusGlyph(status: ItemStatus, color: Color, onColor: Color, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    val word = DayUiTexts.status(status) ?: return
    Glyph(word, size, modifier) {
        val w = this.size.minDimension
        val r = w / 2f
        val s = 1.8.dp.toPx()
        when (status) {
            ItemStatus.DONE_WHOOP -> {
                drawCircle(color, r)
                drawCheck(onColor, s)
            }
            ItemStatus.DONE_SELF -> {
                drawCircle(color, r - s / 2f, style = Stroke(s))
                drawCheck(color, s)
            }
            ItemStatus.MODIFIED -> {
                drawCircle(color, r - s / 2f, style = Stroke(s * 0.7f))
                drawLine(color, Offset(w * 0.28f, w * 0.4f), Offset(w * 0.72f, w * 0.4f), s, StrokeCap.Round)
                drawLine(color, Offset(w * 0.6f, w * 0.29f), Offset(w * 0.72f, w * 0.4f), s, StrokeCap.Round)
                drawLine(color, Offset(w * 0.72f, w * 0.6f), Offset(w * 0.28f, w * 0.6f), s, StrokeCap.Round)
                drawLine(color, Offset(w * 0.4f, w * 0.71f), Offset(w * 0.28f, w * 0.6f), s, StrokeCap.Round)
            }
            ItemStatus.OPEN -> Unit
        }
    }
}

private fun DrawScope.drawCheck(color: Color, s: Float) {
    val w = size.minDimension
    drawLine(color, Offset(w * 0.3f, w * 0.52f), Offset(w * 0.44f, w * 0.66f), s, StrokeCap.Round)
    drawLine(color, Offset(w * 0.44f, w * 0.66f), Offset(w * 0.7f, w * 0.38f), s, StrokeCap.Round)
}

/** Markers line pictograms (A39 §7): a cup (coffee until) and a screen (screens away from). */
@Composable
fun CupGlyph(description: String, color: Color, modifier: Modifier = Modifier, size: Dp = 18.dp) {
    Glyph(description, size, modifier) {
        val w = this.size.minDimension
        val s = (w / 12f).coerceAtLeast(1.2f)
        val st = Stroke(s, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.18f, w * 0.34f), Offset(w * 0.68f, w * 0.34f), s, StrokeCap.Round)
        drawLine(color, Offset(w * 0.18f, w * 0.34f), Offset(w * 0.24f, w * 0.8f), s, StrokeCap.Round)
        drawLine(color, Offset(w * 0.68f, w * 0.34f), Offset(w * 0.62f, w * 0.8f), s, StrokeCap.Round)
        drawLine(color, Offset(w * 0.24f, w * 0.8f), Offset(w * 0.62f, w * 0.8f), s, StrokeCap.Round)
        drawArc(color, -90f, 180f, false, Offset(w * 0.6f, w * 0.42f), androidx.compose.ui.geometry.Size(w * 0.2f, w * 0.22f), style = st)
        drawLine(color, Offset(w * 0.36f, w * 0.12f), Offset(w * 0.36f, w * 0.22f), s, StrokeCap.Round)
        drawLine(color, Offset(w * 0.5f, w * 0.12f), Offset(w * 0.5f, w * 0.22f), s, StrokeCap.Round)
    }
}

@Composable
fun ScreenGlyph(description: String, color: Color, modifier: Modifier = Modifier, size: Dp = 18.dp) {
    Glyph(description, size, modifier) {
        val w = this.size.minDimension
        val s = (w / 12f).coerceAtLeast(1.2f)
        drawRoundRect(
            color,
            Offset(w * 0.28f, w * 0.1f),
            androidx.compose.ui.geometry.Size(w * 0.44f, w * 0.8f),
            androidx.compose.ui.geometry.CornerRadius(w * 0.08f),
            style = Stroke(s),
        )
        drawLine(color, Offset(w * 0.44f, w * 0.78f), Offset(w * 0.56f, w * 0.78f), s, StrokeCap.Round)
    }
}

/** «Закрыть день» (A37 s5): the Titan over a half-ring of the day, dashed below — the day closing. */
@Composable
fun CloseDayGlyph(description: String, color: Color, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    Glyph(description, size, modifier) {
        val w = this.size.minDimension
        val s = (w / 16f).coerceAtLeast(1.3f)
        drawCircle(color, w * 0.08f, Offset(w * 0.5f, w * 0.18f), style = Stroke(s))
        drawLine(color, Offset(w * 0.5f, w * 0.26f), Offset(w * 0.5f, w * 0.5f), s, StrokeCap.Round)
        drawLine(color, Offset(w * 0.3f, w * 0.34f), Offset(w * 0.7f, w * 0.34f), s, StrokeCap.Round)
        drawLine(color, Offset(w * 0.5f, w * 0.5f), Offset(w * 0.4f, w * 0.62f), s, StrokeCap.Round)
        drawLine(color, Offset(w * 0.5f, w * 0.5f), Offset(w * 0.6f, w * 0.62f), s, StrokeCap.Round)
        drawLine(color, Offset(w * 0.08f, w * 0.62f), Offset(w * 0.92f, w * 0.62f), s, StrokeCap.Round)
        drawArc(
            color, 0f, 180f, false, Offset(w * 0.2f, w * 0.44f), androidx.compose.ui.geometry.Size(w * 0.6f, w * 0.4f),
            style = Stroke(s, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(s * 1.4f, s * 1.6f))),
        )
    }
}

/** «Еда» variants as plates (A37 §6 «тарелки в вариантах еды»): full plate, half plate, just a dot. */
@Composable
fun PlateGlyph(food: FoodClose, description: String, color: Color, modifier: Modifier = Modifier, size: Dp = 20.dp) {
    Glyph(description, size, modifier) {
        val w = this.size.minDimension
        val s = (w / 12f).coerceAtLeast(1.2f)
        when (food) {
            FoodClose.PLATE -> {
                drawCircle(color, w * 0.42f, center, style = Stroke(s))
                drawLine(color, Offset(w * 0.5f, w * 0.08f), Offset(w * 0.5f, w * 0.92f), s)
                drawLine(color, Offset(w * 0.5f, w * 0.5f), Offset(w * 0.86f, w * 0.7f), s)
                drawLine(color, Offset(w * 0.5f, w * 0.5f), Offset(w * 0.14f, w * 0.7f), s)
            }
            FoodClose.PARTLY -> {
                drawCircle(color, w * 0.42f, center, style = Stroke(s))
                drawArc(color, 90f, 180f, true, Offset(w * 0.08f, w * 0.08f), androidx.compose.ui.geometry.Size(w * 0.84f, w * 0.84f))
            }
            FoodClose.JUST_ATE -> {
                drawCircle(color, w * 0.42f, center, style = Stroke(s))
                drawCircle(color, w * 0.14f, center)
            }
        }
    }
}

/** Phone steps (A37 §6 «данные: шаги»): two footprints. */
@Composable
fun StepsGlyph(description: String, color: Color, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Glyph(description, size, modifier) {
        val w = this.size.minDimension
        val s = (w / 14f).coerceAtLeast(1.2f)
        drawOval(color, Offset(w * 0.16f, w * 0.18f), androidx.compose.ui.geometry.Size(w * 0.24f, w * 0.38f), style = Stroke(s))
        drawOval(color, Offset(w * 0.2f, w * 0.62f), androidx.compose.ui.geometry.Size(w * 0.16f, w * 0.14f), style = Stroke(s))
        drawOval(color, Offset(w * 0.58f, w * 0.34f), androidx.compose.ui.geometry.Size(w * 0.24f, w * 0.38f), style = Stroke(s))
        drawOval(color, Offset(w * 0.62f, w * 0.76f), androidx.compose.ui.geometry.Size(w * 0.16f, w * 0.14f), style = Stroke(s))
    }
}

/** A pen: the optional evening note (A07 §4: the crisis filter reads it, it is not stored). */
@Composable
fun PenGlyph(description: String, color: Color, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    Glyph(description, size, modifier) {
        val w = this.size.minDimension
        val s = (w / 12f).coerceAtLeast(1.2f)
        drawLine(color, Offset(w * 0.22f, w * 0.78f), Offset(w * 0.72f, w * 0.28f), s * 2.2f, StrokeCap.Round)
        drawLine(color, Offset(w * 0.16f, w * 0.86f), Offset(w * 0.24f, w * 0.76f), s, StrokeCap.Round)
        drawLine(color, Offset(w * 0.14f, w * 0.9f), Offset(w * 0.86f, w * 0.9f), s * 0.8f, StrokeCap.Round)
    }
}

/** «План помог?»: a check in a ring (yes) or a level line (not really) — never a cross. */
@Composable
fun HelpedGlyph(helped: Boolean, description: String, color: Color, modifier: Modifier = Modifier, size: Dp = 20.dp) {
    Glyph(description, size, modifier) {
        val w = this.size.minDimension
        val s = (w / 11f).coerceAtLeast(1.2f)
        if (helped) {
            drawCheck(color, s)
        } else {
            drawLine(color, Offset(w * 0.26f, w * 0.5f), Offset(w * 0.74f, w * 0.5f), s, StrokeCap.Round)
        }
    }
}
