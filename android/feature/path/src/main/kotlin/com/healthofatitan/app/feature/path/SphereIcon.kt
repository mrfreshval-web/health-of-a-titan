package com.healthofatitan.app.feature.path

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.healthofatitan.app.domain.path.Sphere

/**
 * Sphere pictograms (A37 §6 grammar: 24 dp grid, 1.6 dp line, round caps): разум — head with a
 * spark, работа — screen, связи — two rings, еда — plate, тело — dumbbell, сон — crescent.
 * Every pictogram has a contentDescription; meaning never rides on colour alone (A37 §6).
 */
@Composable
fun SphereIcon(
    sphere: Sphere,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    color: Color = LocalContentColor.current,
    describe: Boolean = true,
) {
    val a11y = if (describe) Modifier.semantics { contentDescription = PathTexts.sphere(sphere) } else Modifier
    Canvas(modifier.size(size).then(a11y)) {
        val k = this.size.minDimension / 24f
        val s = Stroke(1.6f * k, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun o(x: Float, y: Float) = Offset(x * k, y * k)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color, o(x1, y1), o(x2, y2), s.width, StrokeCap.Round)
        when (sphere) {
            Sphere.MIND -> {
                drawCircle(color, 6.5f * k, o(12f, 14f), style = s)
                line(12f, 2.5f, 12f, 4.5f)
                line(5.2f, 5.2f, 6.6f, 6.6f)
                line(18.8f, 5.2f, 17.4f, 6.6f)
                drawCircle(color, 1.2f * k, o(12f, 14f))
            }
            Sphere.WORK -> {
                drawRoundRect(color, o(3.5f, 4.5f), Size(17f * k, 11f * k), androidx.compose.ui.geometry.CornerRadius(2f * k), style = s)
                line(12f, 15.5f, 12f, 19f)
                line(8f, 19.5f, 16f, 19.5f)
            }
            Sphere.BONDS -> {
                drawCircle(color, 5.2f * k, o(9f, 12f), style = s)
                drawCircle(color, 5.2f * k, o(15f, 12f), style = s)
            }
            Sphere.FOOD -> {
                drawCircle(color, 8.25f * k, o(12f, 12f), style = s)
                drawCircle(color, 4.5f * k, o(12f, 12f), style = s)
            }
            Sphere.BODY -> {
                line(7f, 12f, 17f, 12f)
                line(7f, 8f, 7f, 16f)
                line(17f, 8f, 17f, 16f)
                line(4f, 10f, 4f, 14f)
                line(20f, 10f, 20f, 14f)
            }
            Sphere.SLEEP -> {
                // crescent: outer circle (12,12) r 8 and inner circle (16,8) r 7, meeting at 13.7° / 256.3°
                val moon = Path().apply {
                    arcTo(Rect(4f * k, 4f * k, 20f * k, 20f * k), 13.7f, 242.6f, true)
                    arcTo(Rect(9f * k, 1f * k, 23f * k, 15f * k), 212.6f, -155.2f, false)
                    close()
                }
                drawPath(moon, color, style = s)
            }
        }
    }
}
