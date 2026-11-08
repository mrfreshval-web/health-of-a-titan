package com.healthofatitan.app.core.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthofatitan.app.core.ui.theme.HotType
import com.healthofatitan.app.core.ui.theme.Titanium
import kotlin.math.cos
import kotlin.math.sin

/** The arc of a metric (A66 §1.1 «Дуга-шкала 270°»): from 135° clockwise through 270°. */
object MetricArcGeometry {
    const val START_DEG = 135f
    const val SWEEP_DEG = 270f

    /** The angle in degrees of a share 0…1 of the arc (clamped). */
    fun angle(share: Float): Float = START_DEG + SWEEP_DEG * share.coerceIn(0f, 1f)
}

/**
 * The 270° arc of a metric (A66 §3.3): a track, the corridor of the personal norm as a lighter band
 * and the value as a 9 dp dot. Without data ([value] null) the arc is dashed and has no dot: the
 * dash means only «нет данных» (A66 §1.1).
 */
@Composable
fun MetricArc(
    value: Float?,
    band: ClosedFloatingPointRange<Float>?,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    content: @Composable () -> Unit = {},
) {
    val track = Color.White.copy(alpha = 0.09f)
    val bandColor = Titanium.Bone.copy(alpha = 0.30f)
    val dashColor = Titanium.Steel.copy(alpha = 0.7f)
    val dot = MaterialTheme.colorScheme.onSurface
    val ring = Titanium.Titan1
    Box(
        modifier
            .size(size)
            .drawBehind {
                val line = 5.dp.toPx()
                val r = this.size.minDimension / 2f - line
                val topLeft = Offset(center.x - r, center.y - r)
                val box = Size(r * 2f, r * 2f)
                if (value == null) {
                    val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx()))
                    drawArc(dashColor, MetricArcGeometry.START_DEG, MetricArcGeometry.SWEEP_DEG, false, topLeft, box, style = Stroke(2.dp.toPx(), pathEffect = dash))
                } else {
                    drawArc(track, MetricArcGeometry.START_DEG, MetricArcGeometry.SWEEP_DEG, false, topLeft, box, style = Stroke(line, cap = StrokeCap.Round))
                    if (band != null) {
                        val from = MetricArcGeometry.angle(band.start)
                        val to = MetricArcGeometry.angle(band.endInclusive)
                        drawArc(bandColor, from, to - from, false, topLeft, box, style = Stroke(line))
                    }
                    val a = Math.toRadians(MetricArcGeometry.angle(value).toDouble())
                    val p = Offset(center.x + r * cos(a).toFloat(), center.y + r * sin(a).toFloat())
                    drawCircle(ring, 4.5.dp.toPx() + 2.dp.toPx(), p)
                    drawCircle(dot, 4.5.dp.toPx(), p)
                }
            },
        contentAlignment = Alignment.Center,
    ) { content() }
}

private val TileShape = RoundedCornerShape(16.dp)

/**
 * A metric tile of «Сегодня» (A66 §3.3, variant B «Тише» §3.2а): the arc with the number in its
 * centre, the name and the word to the norm, straight on the background — no card; the row divides
 * the tiles with hairlines. The whole tile is one button (the ripple keeps the 16 dp rounding);
 * TalkBack reads [spoken] (the full sentence, never «дуга, 57»).
 *
 * @param value the number in the arc; «—» without data
 * @param arc the position of the value on the arc, 0…1; null draws the dashed «нет данных»
 * @param arcSize the side of the arc (50 dp in «Тише»)
 * @param height the least height of the tile; a two-line word makes it taller
 */
@Composable
fun MetricTile(
    label: String,
    value: String,
    word: String?,
    arc: Float?,
    band: ClosedFloatingPointRange<Float>?,
    spoken: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onClickLabel: String? = null,
    arcSize: Dp = 50.dp,
    height: Dp = 88.dp,
) {
    val tap = onClick
    Column(
        modifier
            .heightIn(min = height)
            .clip(TileShape)
            .clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
                this.onClick(label = onClickLabel) { tap(); true }
            }
            .padding(top = 2.dp, start = 4.dp, end = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MetricArc(arc, band, size = arcSize) {
            Text(
                value,
                style = HotType.numeral.copy(fontSize = if (value.length > 3) 16.sp else 19.sp, lineHeight = 22.sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 16.sp),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
        )
        if (word != null) {
            Text(
                word,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 14.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}
