package com.healthofatitan.app.feature.checkin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.healthofatitan.app.core.ui.theme.Titanium
import com.healthofatitan.app.domain.checkin.BodyFeel
import com.healthofatitan.app.domain.checkin.Drive
import com.healthofatitan.app.domain.checkin.Intimacy
import com.healthofatitan.app.domain.checkin.Pull
import com.healthofatitan.app.domain.checkin.RitualItem
import com.healthofatitan.app.domain.checkin.Sky
import com.healthofatitan.app.domain.checkin.Yesterday

/**
 * Line pictograms of the state cards (A44 §3: a picture instead of a word). One stroke weight,
 * round caps, the ink of the choice; amber (the dawn light) only for «забито», never red.
 * Each glyph is drawn in a 24-unit box and scaled.
 */
private fun DrawScope.u(): Float = size.minDimension / 24f

private fun DrawScope.p(x: Float, y: Float) = Offset(x * u() + (size.width - size.minDimension) / 2f, y * u() + (size.height - size.minDimension) / 2f)

private fun DrawScope.line(color: Color, vararg pts: kotlin.Pair<Float, Float>, w: Float = 1.6f) {
    val path = Path()
    pts.forEachIndexed { i, (x, y) -> val o = p(x, y); if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
    drawPath(path, color, style = Stroke(w * u(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.circle(color: Color, x: Float, y: Float, r: Float, fill: Boolean = false, w: Float = 1.6f) {
    if (fill) drawCircle(color, r * u(), p(x, y)) else drawCircle(color, r * u(), p(x, y), style = Stroke(w * u()))
}

private fun DrawScope.rect(color: Color, x: Float, y: Float, w: Float, h: Float, r: Float = 1.5f, fill: Boolean = false) {
    val o = p(x, y)
    if (fill) drawRoundRect(color, o, Size(w * u(), h * u()), CornerRadius(r * u()))
    else drawRoundRect(color, o, Size(w * u(), h * u()), CornerRadius(r * u()), style = Stroke(1.6f * u()))
}

@Composable
private fun Glyph(size: Dp, description: String?, draw: DrawScope.() -> Unit) {
    val m = Modifier.size(size).let { if (description != null) it.semantics { contentDescription = description } else it }
    Canvas(m) { draw() }
}

/** Заряд: a capsule of five cells filled from the bottom (A44 §1.2 П1). */
@Composable
fun ChargeGlyph(level: Int, color: Color, size: Dp = 28.dp, description: String? = null) = Glyph(size, description) {
    rect(color, 7f, 1.5f, 10f, 21f, r = 3f)
    for (i in 0 until 5) {
        val y = 18.6f - i * 3.9f
        if (i < level) rect(color, 9.2f, y, 5.6f, 2.6f, r = 0.8f, fill = true)
        else rect(color.copy(alpha = 0.18f), 9.2f, y, 5.6f, 2.6f, r = 0.8f, fill = true)
    }
}

private fun DrawScope.cloud(color: Color, dy: Float = 0f) {
    val path = Path()
    val a = p(5f, 15f + dy)
    path.moveTo(a.x, a.y)
    val pts = listOf(4f to 11f + dy, 8f to 8f + dy, 11f to 6f + dy, 16f to 7f + dy, 19f to 10f + dy, 20f to 15f + dy)
    pts.forEach { (x, y) -> val o = p(x, y); path.lineTo(o.x, o.y) }
    path.close()
    drawPath(path, color, style = Stroke(1.6f * u(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Погода: storm · rain · cloud · a break · clear. */
@Composable
fun SkyGlyph(sky: Sky, color: Color, size: Dp = 28.dp, description: String? = null) = Glyph(size, description) {
    when (sky) {
        Sky.STORM -> {
            cloud(color, -2f)
            line(color, 13f to 14f, 10.5f to 18f, 13.5f to 18f, 11f to 22f)
        }
        Sky.RAIN -> {
            cloud(color, -2f)
            line(color, 8f to 16f, 7f to 19f)
            line(color, 12f to 16f, 11f to 19f)
            line(color, 16f to 16f, 15f to 19f)
        }
        Sky.CLOUDY -> cloud(color, 1f)
        Sky.BREAK -> {
            circle(color, 15.5f, 8f, 3.4f)
            line(color, 15.5f to 2f, 15.5f to 3f)
            line(color, 21.5f to 8f, 20.5f to 8f)
            cloud(color, 4f)
        }
        Sky.CLEAR -> {
            circle(color, 12f, 12f, 4.5f)
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45.0)
                val x1 = 12f + 7f * Math.cos(a).toFloat()
                val y1 = 12f + 7f * Math.sin(a).toFloat()
                val x2 = 12f + 9.5f * Math.cos(a).toFloat()
                val y2 = 12f + 9.5f * Math.sin(a).toFloat()
                line(color, x1 to y1, x2 to y2)
            }
        }
    }
}

private fun DrawScope.figure(color: Color) {
    circle(color, 12f, 4.5f, 2.3f)
    line(color, 12f to 7f, 12f to 14.5f)
    line(color, 5f to 9f, 19f to 9f)
    line(color, 12f to 14.5f, 8.5f to 22f)
    line(color, 12f to 14.5f, 15.5f to 22f)
}

/** Тело: light · sore (amber hips) · a cold (a soft halo). */
@Composable
fun BodyGlyph(feel: BodyFeel, color: Color, size: Dp = 28.dp, description: String? = null) = Glyph(size, description) {
    figure(color)
    when (feel) {
        BodyFeel.SORE -> {
            circle(Titanium.Dawn.copy(alpha = 0.35f), 10f, 17.5f, 2.6f, fill = true)
            circle(Titanium.Dawn.copy(alpha = 0.35f), 14f, 17.5f, 2.6f, fill = true)
        }
        BodyFeel.SICK -> circle(color.copy(alpha = 0.45f), 12f, 4.5f, 4.4f, w = 1f)
        BodyFeel.LIGHT -> Unit
    }
}

/** Тяга к делу: the Titan sinks · stands · rises. */
@Composable
fun DriveGlyph(drive: Drive, color: Color, size: Dp = 28.dp, description: String? = null) = Glyph(size, description) {
    line(color, 3f to 21f, 21f to 21f)
    when (drive) {
        Drive.NONE -> line(color, 12f to 6f, 12f to 17f, 8f to 13f, 12f to 17f, 16f to 13f)
        Drive.OK -> line(color, 5f to 12f, 19f to 12f)
        Drive.EAGER -> line(color, 12f to 17f, 12f to 4f, 8f to 8f, 12f to 4f, 16f to 8f)
    }
}

/** «Вчера вечером»: behaviour as icons (A44 П6). */
@Composable
fun YesterdayGlyph(item: Yesterday, color: Color, size: Dp = 28.dp, description: String? = null) = Glyph(size, description) {
    when (item) {
        Yesterday.SCREENS_LATE -> {
            rect(color, 6f, 2.5f, 12f, 19f, r = 2.5f)
            line(color, 13f to 7f, 11f to 9f, 13f to 12f)
        }
        Yesterday.LATE_FOOD -> {
            circle(color, 12f, 14f, 7f)
            circle(color, 12f, 14f, 3.5f)
            line(color, 17f to 3f, 19f to 5f, 21f to 3f)
        }
        Yesterday.ALCOHOL -> {
            line(color, 7f to 3f, 17f to 3f, 16f to 10f, 12f to 13f, 8f to 10f, 7f to 3f)
            line(color, 12f to 13f, 12f to 20f)
            line(color, 8f to 21f, 16f to 21f)
        }
        Yesterday.SWEETS -> {
            circle(color, 12f, 12f, 4.5f)
            line(color, 7.5f to 12f, 4f to 9f, 4f to 15f, 7.5f to 12f)
            line(color, 16.5f to 12f, 20f to 9f, 20f to 15f, 16.5f to 12f)
        }
        Yesterday.COFFEE_LATE -> {
            line(color, 4f to 9f, 16f to 9f, 15f to 19f, 5f to 19f, 4f to 9f)
            line(color, 16f to 11f, 19f to 11f, 19f to 15f, 15.5f to 15f)
            line(color, 8f to 3f, 8f to 6f)
            line(color, 12f to 3f, 12f to 6f)
        }
        Yesterday.NOTHING -> {
            circle(color, 12f, 12f, 8f)
            line(color, 6.5f to 17.5f, 17.5f to 6.5f)
        }
    }
}

/** Р1 «Утро как вчера?»: the stack as icons. */
@Composable
fun RitualGlyph(item: RitualItem, color: Color, size: Dp = 28.dp, description: String? = null) = Glyph(size, description) {
    when (item) {
        RitualItem.COFFEE -> {
            line(color, 4f to 9f, 16f to 9f, 15f to 19f, 5f to 19f, 4f to 9f)
            line(color, 16f to 11f, 19f to 11f, 19f to 15f, 15.5f to 15f)
            line(color, 9f to 3f, 10f to 5f, 9f to 7f)
        }
        RitualItem.COLD_SHOWER -> {
            line(color, 5f to 3f, 5f to 6f, 14f to 6f)
            line(color, 10f to 6f, 14f to 6f, 16f to 9f, 8f to 9f, 10f to 6f)
            line(color, 9f to 12f, 8f to 15f)
            line(color, 12f to 12f, 12f to 16f)
            line(color, 15f to 12f, 16f to 15f)
            line(color, 10f to 18f, 9f to 21f)
            line(color, 14f to 18f, 15f to 21f)
        }
        RitualItem.LIGHT -> {
            line(color, 3f to 18f, 21f to 18f)
            circle(color, 12f, 18f, 5f)
            line(color, 12f to 8f, 12f to 10f)
            line(color, 5f to 11f, 6.5f to 12.5f)
            line(color, 19f to 11f, 17.5f to 12.5f)
        }
        RitualItem.BREATH -> {
            line(color, 3f to 8f, 15f to 8f, 17f to 6f, 15f to 4f)
            line(color, 3f to 12f, 19f to 12f, 21f to 14f, 19f to 16f)
            line(color, 3f to 16f, 12f to 16f)
        }
        RitualItem.WATER -> line(color, 12f to 3f, 17f to 12f, 17f to 15f, 15f to 19f, 12f to 20f, 9f to 19f, 7f to 15f, 7f to 12f, 12f to 3f)
    }
}

/** The person's own stack item (a supplement as they named it): a capsule, no dose. */
@Composable
fun StackGlyph(color: Color, size: Dp = 28.dp, description: String? = null) = Glyph(size, description) {
    rect(color, 4f, 8.5f, 16f, 7f, r = 3.5f)
    line(color, 12f to 8.5f, 12f to 15.5f)
}

/** «Близость»: none · partner · self · not marked. Neutral shapes, no body parts. */
@Composable
fun IntimacyGlyph(value: Intimacy, color: Color, size: Dp = 28.dp, description: String? = null) = Glyph(size, description) {
    when (value) {
        Intimacy.NONE -> circle(color, 12f, 12f, 7f, w = 1.2f)
        Intimacy.PARTNER -> {
            circle(color, 9f, 12f, 5.5f)
            circle(color, 15f, 12f, 5.5f)
        }
        Intimacy.SELF -> {
            circle(color, 12f, 12f, 5.5f)
            circle(color, 12f, 12f, 1.6f, fill = true)
        }
        Intimacy.NO_MARK -> line(color, 6f to 12f, 18f to 12f)
    }
}

/** «Что тянуло?»: a pull as an icon (never a judgement). */
@Composable
fun PullGlyph(value: Pull, color: Color, size: Dp = 28.dp, description: String? = null) = Glyph(size, description) {
    when (value) {
        Pull.FEED -> {
            rect(color, 6f, 2.5f, 12f, 19f, r = 2.5f)
            line(color, 9f to 8f, 15f to 8f)
            line(color, 9f to 12f, 15f to 12f)
            line(color, 9f to 16f, 13f to 16f)
        }
        Pull.VIDEO -> {
            rect(color, 3f, 5f, 18f, 14f, r = 3f)
            line(color, 10f to 9f, 15f to 12f, 10f to 15f, 10f to 9f)
        }
        Pull.GAMES -> {
            rect(color, 3f, 7f, 18f, 11f, r = 5f)
            line(color, 7f to 12.5f, 10f to 12.5f)
            line(color, 8.5f to 11f, 8.5f to 14f)
            circle(color, 15f, 11.5f, 0.9f, fill = true)
            circle(color, 17f, 13.5f, 0.9f, fill = true)
        }
        Pull.SWEETS -> {
            circle(color, 12f, 12f, 4.5f)
            line(color, 7.5f to 12f, 4f to 9f, 4f to 15f, 7.5f to 12f)
            line(color, 16.5f to 12f, 20f to 9f, 20f to 15f, 16.5f to 12f)
        }
        Pull.SHOPPING -> {
            line(color, 5f to 8f, 19f to 8f, 18f to 21f, 6f to 21f, 5f to 8f)
            line(color, 9f to 8f, 9f to 6f, 12f to 3f, 15f to 6f, 15f to 8f)
        }
        Pull.ADULT -> {
            circle(color, 12f, 12f, 9f)
            line(color, 8f to 9.5f, 9.5f to 8.5f, 9.5f to 15.5f)
            circle(color, 13.5f, 10f, 1.6f)
            circle(color, 13.5f, 14f, 1.8f)
            line(color, 17f to 10.5f, 17f to 13.5f)
            line(color, 15.5f to 12f, 18.5f to 12f)
        }
        Pull.NOTHING -> {
            circle(color, 12f, 12f, 8f)
            line(color, 6.5f to 17.5f, 17.5f to 6.5f)
        }
    }
}

/** A lock for the S5 tiles of the collection. */
@Composable
fun LockGlyph(color: Color, size: Dp = 24.dp, description: String? = null) = Glyph(size, description) {
    rect(color, 5f, 10.5f, 14f, 10.5f, r = 2f)
    line(color, 8f to 10.5f, 8f to 7.5f, 9.5f to 4.5f, 12f to 3.5f, 14.5f to 4.5f, 16f to 7.5f, 16f to 10.5f)
}

/** «Быстро»: a spark. */
@Composable
fun QuickGlyph(color: Color, size: Dp = 20.dp) = Glyph(size, null) {
    line(color, 13f to 2f, 6f to 13f, 12f to 13f, 10f to 22f, 18f to 10f, 12f to 10f, 13f to 2f)
}
