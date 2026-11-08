package com.healthofatitan.app.feature.mentor

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.healthofatitan.app.core.model.mentor.MentorPersona

/**
 * «Лицо из знака» (A62 §9, D-056 п. 9): the circle is the head and the sight, the cardiogram is the
 * mouth, two dots are the eyes. Never human: no nose, hair, skin or gender. Сенсей — an open ensō
 * with half-closed eyes and one peak; Винс — a closed circle, live eyes and a grinning pulse;
 * Винсент — a double rim and a calm pulse. While Винс is thinking the pulse breathes; [care] (the
 * crisis card) draws the plain ivory circle without a face (A62 §9 «в бережном режиме»).
 *
 * A first cut in code; the Lottie states of Агенты 4 и 17 replace it.
 */
@Composable
fun VinceFace(
    persona: MentorPersona,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    thinking: Boolean = false,
    care: Boolean = false,
    line: Color = MaterialTheme.colorScheme.onSurface,
    pulse: Color = MaterialTheme.colorScheme.primary,
) {
    val breath by rememberInfiniteTransition(label = "vince").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulse",
    )
    val pulseColor = if (thinking) pulse.copy(alpha = pulse.alpha * breath) else pulse
    Canvas(modifier.size(size)) {
        val k = this.size.minDimension / 24f
        val w = 1.4f * k
        fun p(x: Float, y: Float) = Offset(x * k, y * k)
        val stroke = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun poly(color: Color, vararg pts: Pair<Float, Float>) {
            val path = Path().apply {
                moveTo(pts[0].first * k, pts[0].second * k)
                pts.drop(1).forEach { lineTo(it.first * k, it.second * k) }
            }
            drawPath(path, color, style = stroke)
        }
        if (care) {
            drawCircle(line.copy(alpha = 0.7f), 10.5f * k, p(12f, 12f), style = stroke)
            return@Canvas
        }
        when (persona) {
            MentorPersona.SENSEI -> {
                drawArc(line, -40f, 320f, false, p(1.5f, 1.5f), Size(21f * k, 21f * k), style = stroke)
                // half-closed eyes
                drawLine(line, p(8f, 9.5f), p(10f, 9.5f), strokeWidth = w, cap = StrokeCap.Round)
                drawLine(line, p(14f, 9.5f), p(16f, 9.5f), strokeWidth = w, cap = StrokeCap.Round)
                poly(pulseColor, 5f to 15f, 10.5f to 15f, 12f to 12f, 13.5f to 15f, 19f to 15f)
            }
            MentorPersona.BRO -> {
                drawCircle(line, 10.5f * k, p(12f, 12f), style = stroke)
                drawCircle(line, 1.1f * k, p(9f, 9.5f))
                drawCircle(line, 1.1f * k, p(15f, 9.5f))
                // the grin: the pulse rises at the right end
                poly(pulseColor, 4.5f to 15f, 9f to 15f, 10.5f to 12.5f, 12f to 17f, 13.5f to 14.5f, 16f to 14.5f, 19.5f to 12.5f)
            }
            MentorPersona.BUTLER -> {
                drawCircle(line, 10.5f * k, p(12f, 12f), style = stroke)
                drawCircle(line.copy(alpha = line.alpha * 0.6f), 8.8f * k, p(12f, 12f), style = Stroke(w * 0.6f))
                drawCircle(line, 0.9f * k, p(9.5f, 10f))
                drawCircle(line, 0.9f * k, p(14.5f, 10f))
                poly(pulseColor, 6f to 15f, 10f to 15f, 11f to 13.5f, 12f to 15f, 13f to 13.5f, 14f to 15f, 18f to 15f)
            }
        }
    }
}
