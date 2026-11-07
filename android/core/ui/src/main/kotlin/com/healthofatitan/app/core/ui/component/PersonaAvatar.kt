package com.healthofatitan.app.core.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.healthofatitan.app.core.model.mentor.MentorPersona

/**
 * The mentor's avatar in the language of the mark (A38 §8.3): no face and no body, only the
 * circle, the column and the pulse. Сенсей — an open ensō with one column and a flat pulse with
 * one peak; Бро — a closed circle, two columns like shoulders and a pulse with two sharp peaks out
 * of the circle; Дворецкий — a porcelain double rim, one column with a volute capital and a bow.
 * [persona] null is the neutral avatar of the care mode: a plain circle (A38 §8.3).
 *
 * Drawn in a 24-unit grid and scaled, so the same art serves 24, 48 and 96 dp.
 */
@Composable
fun PersonaAvatar(
    persona: MentorPersona?,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    line: Color = MaterialTheme.colorScheme.onSurface,
    pulse: Color = MaterialTheme.colorScheme.primary,
) {
    Canvas(modifier.size(size)) {
        val k = this.size.minDimension / 24f
        val w = 1.5f * k
        fun p(x: Float, y: Float) = Offset(x * k, y * k)
        fun stroke(width: Float = w) = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun polyline(color: Color, vararg pts: Pair<Float, Float>) {
            val path = Path().apply {
                moveTo(pts[0].first * k, pts[0].second * k)
                pts.drop(1).forEach { lineTo(it.first * k, it.second * k) }
            }
            drawPath(path, color, style = stroke())
        }
        when (persona) {
            null -> drawCircle(line, 10f * k, p(12f, 12f), style = stroke())
            MentorPersona.SENSEI -> {
                // ensō: open at the top right, thicker where the brush starts
                drawArc(line, -40f, 320f, false, p(2f, 2f), Size(20f * k, 20f * k), style = stroke())
                drawArc(line, -40f, 40f, false, p(2f, 2f), Size(20f * k, 20f * k), style = stroke(w * 1.9f))
                column(k, w, line, 12f)
                polyline(pulse, 4f to 15f, 10f to 15f, 11.5f to 11.5f, 13f to 15f, 20f to 15f)
            }
            MentorPersona.BRO -> {
                drawCircle(line, 10f * k, p(12f, 12f), style = stroke())
                column(k, w, line, 9f)
                column(k, w, line, 15f)
                polyline(pulse, 0.5f to 14.5f, 6f to 14.5f, 7.5f to 10f, 9f to 17f, 12f to 14.5f, 14f to 9.5f, 15.5f to 17f, 17f to 14.5f, 23.5f to 14.5f)
            }
            MentorPersona.BUTLER -> {
                drawCircle(line, 10f * k, p(12f, 12f), style = stroke())
                drawCircle(line.copy(alpha = line.alpha * 0.6f), 8.4f * k, p(12f, 12f), style = stroke(w * 0.6f))
                column(k, w, line, 12f, top = 9f)
                // volute capital
                drawCircle(line, 0.9f * k, p(10.2f, 7.6f), style = stroke(w * 0.8f))
                drawCircle(line, 0.9f * k, p(13.8f, 7.6f), style = stroke(w * 0.8f))
                // the bow under the capital
                polyline(pulse, 12f to 10f, 9.6f to 8.9f, 9.6f to 11.1f, 12f to 10f, 14.4f to 8.9f, 14.4f to 11.1f, 12f to 10f)
            }
        }
    }
}

/** A column of the mark: shaft with a capital and a base, centred on [x]. */
private fun DrawScope.column(k: Float, w: Float, color: Color, x: Float, top: Float = 7f, bottom: Float = 17f) {
    val s = Stroke(w, cap = StrokeCap.Round)
    drawLine(color, Offset(x * k, top * k), Offset(x * k, bottom * k), strokeWidth = s.width, cap = StrokeCap.Round)
    drawLine(color, Offset((x - 1.3f) * k, top * k), Offset((x + 1.3f) * k, top * k), strokeWidth = s.width, cap = StrokeCap.Round)
    drawLine(color, Offset((x - 1.3f) * k, bottom * k), Offset((x + 1.3f) * k, bottom * k), strokeWidth = s.width, cap = StrokeCap.Round)
}
