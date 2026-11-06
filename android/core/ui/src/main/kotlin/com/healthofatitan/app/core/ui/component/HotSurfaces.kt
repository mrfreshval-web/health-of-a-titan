package com.healthofatitan.app.core.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.healthofatitan.app.core.ui.theme.HotThemeTokens

/** Screen edge padding and gaps (A36 §6: 4 dp grid, 16 dp edges, 12 dp between cards). */
object HotSpacing {
    val edge = 16.dp
    val cardGap = 12.dp
    val sectionGap = 24.dp
    val cardPadding = 18.dp
}

/**
 * «Ночное небо» behind the tab screens (A36 §4): a radial light from the top, #1B2232 → Ночь.
 * In the light theme it is plain «Мрамор». The SOS screen does not use it (A36 §11 rule 7).
 */
@Composable
fun HotBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val top = HotThemeTokens.colors.skyTop
    val bottom = MaterialTheme.colorScheme.background
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bottom)
            .drawBehind {
                if (top != bottom) {
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(top, bottom),
                            center = Offset(size.width / 2f, -size.width * 0.15f),
                            radius = size.width * 1.25f,
                        ),
                    )
                }
            },
        content = content,
    )
}

/**
 * Card of «Колоннада»: tone instead of shadow (Базальт), a 1 dp ivory hairline, 18 dp corners.
 * [cornice] adds the gold «карниз» on top: the main card of a screen only (A36 §6).
 */
@Composable
fun HotCard(
    modifier: Modifier = Modifier,
    cornice: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(HotSpacing.cardPadding),
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = HotThemeTokens.colors
    val gold = MaterialTheme.colorScheme.primary
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, tokens.hairline),
    ) {
        Column(
            modifier = Modifier
                .drawWithContent {
                    drawContent()
                    if (cornice) {
                        val inset = 18.dp.toPx()
                        drawLine(
                            brush = Brush.horizontalGradient(
                                0f to Color.Transparent,
                                0.5f to gold.copy(alpha = 0.9f),
                                1f to Color.Transparent,
                                startX = inset,
                                endX = size.width - inset,
                            ),
                            start = Offset(inset, 0.5.dp.toPx()),
                            end = Offset(size.width - inset, 0.5.dp.toPx()),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }
                }
                .padding(contentPadding),
            content = content,
        )
    }
}

/**
 * A block that has no data yet: dashed outline, as «нет данных» in A36 §2.4 — honest about
 * what is still empty, and as carefully drawn as a full card (A36 §11 rule 10).
 */
@Composable
fun HotPendingCard(
    title: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    radius: Dp = 18.dp,
) {
    val tokens = HotThemeTokens.colors
    val dash = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
    Column(
        modifier = modifier
            .drawBehind {
                val w = 1.dp.toPx()
                drawRoundRect(
                    color = dash,
                    topLeft = Offset(w / 2, w / 2),
                    size = size.copy(width = size.width - w, height = size.height - w),
                    cornerRadius = CornerRadius(radius.toPx()),
                    style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))),
                )
            }
            .padding(horizontal = HotSpacing.cardPadding, vertical = 16.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (caption != null) {
            Text(caption, style = MaterialTheme.typography.bodySmall, color = tokens.textTertiary)
        }
    }
}

/** Overline «ВТОРНИК · 10 НОЯБРЯ»: gold, tracked capitals (labelSmall). Never for the word «HoT». */
@Composable
fun HotOverline(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = color, modifier = modifier)
}
