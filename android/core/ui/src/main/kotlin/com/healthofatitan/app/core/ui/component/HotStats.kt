package com.healthofatitan.app.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.core.ui.theme.HotType

/**
 * «Твоя норма» while it is being learned: a thin ring that fills night by night with «9/14» in the
 * middle — A36 «круг = мера». Replaces a paragraph of explanation.
 */
@Composable
fun HotNormRing(
    value: Int,
    total: Int,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    description: String = "Норма: $value из $total",
) {
    val gold = MaterialTheme.colorScheme.primary
    val track = HotThemeTokens.colors.ruleSoft
    val fraction = (value.toFloat() / total).coerceIn(0f, 1f)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .semantics { contentDescription = description }
            .drawBehind {
                val w = 3.dp.toPx()
                val inset = w / 2
                val arcSize = Size(this.size.width - w, this.size.height - w)
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(w))
                drawArc(gold, -90f, 360f * fraction, false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Round))
            },
    ) {
        Text(
            "$value/$total",
            style = if (size >= 64.dp) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

/**
 * One fact as a tile: a small label, a big number in Manrope and one quiet line under it. No
 * sentences — the number speaks (A36 §8, §11 rule 3). When [value] is null the tile is drawn as
 * «нет данных» (dashed, [HotPendingCard]) with the same footprint.
 */
@Composable
fun HotStatTile(
    label: String,
    value: String?,
    modifier: Modifier = Modifier,
    caption: String? = null,
    pendingCaption: String? = null,
    footer: (@Composable () -> Unit)? = null,
) {
    if (value == null) {
        HotPendingCard(title = label, caption = pendingCaption, modifier = modifier.heightIn(min = 112.dp))
        return
    }
    HotCard(modifier = modifier.heightIn(min = 112.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
            HotOverline(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text(value, style = HotType.numeral, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (caption != null) {
                Text(caption, style = MaterialTheme.typography.bodySmall, color = HotThemeTokens.colors.textTertiary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (footer != null) {
                Spacer(Modifier.height(6.dp))
                footer()
            }
        }
    }
}

/** A thin bar «сколько из нужного» (sleep vs need): gold fill on a soft track, no numbers on it. */
@Composable
fun HotRatioBar(fraction: Float, modifier: Modifier = Modifier) {
    val gold = MaterialTheme.colorScheme.primary
    val track = HotThemeTokens.colors.ruleSoft
    val f = fraction.coerceIn(0f, 1f)
    Spacer(
        modifier
            .fillMaxWidth()
            .height(4.dp)
            .drawBehind {
                val h = size.height
                drawLine(track, Offset(h / 2, h / 2), Offset(size.width - h / 2, h / 2), h, StrokeCap.Round)
                if (f > 0f) drawLine(gold, Offset(h / 2, h / 2), Offset(h / 2 + (size.width - h) * f, h / 2), h, StrokeCap.Round)
            },
    )
}
