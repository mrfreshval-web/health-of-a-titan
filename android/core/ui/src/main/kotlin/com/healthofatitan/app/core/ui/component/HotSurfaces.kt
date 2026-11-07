package com.healthofatitan.app.core.ui.component

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import kotlin.random.Random

/** A37 §9.4: 24 dp edges, 8 dp rhythm, large gaps 21 · 34 · 55 dp; glass cards from 16 dp. */
object HotSpacing {
    val edge = 24.dp
    val cardGap = 16.dp
    val sectionGap = 34.dp
    val cardPadding = 20.dp
    val air = 55.dp
}

/** Card corner radius (A37 §9.5: 26–28 dp). */
val HotCardShape = RoundedCornerShape(26.dp)

/**
 * The material behind every tab (A37 §9.5): «Глубина» with one light from above, an optional
 * aura of the mode light (12–20 %) behind the hero, and 6 % grain so the background reads as a
 * material, not a flat fill. Light «Фарфор» is a plain warm white. The crisis screen does not use
 * it (A36 §11 rule 7 holds).
 *
 * @param aura the mode light behind the hero, or null
 * @param auraCenterY where the aura stands, as a share of the height
 */
@Composable
fun HotBackground(
    modifier: Modifier = Modifier,
    aura: Color? = null,
    auraCenterY: Float = 0.3f,
    content: @Composable BoxScope.() -> Unit,
) {
    val tokens = HotThemeTokens.colors
    val top = tokens.skyTop
    val bottom = MaterialTheme.colorScheme.background
    val grain = rememberGrain()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bottom)
            .drawBehind {
                if (top != bottom) {
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(top, bottom),
                            center = Offset(size.width / 2f, -size.width * 0.2f),
                            radius = size.width * 1.3f,
                        ),
                    )
                }
                if (aura != null && tokens.glow) {
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(aura.copy(alpha = 0.16f), aura.copy(alpha = 0.05f), Color.Transparent),
                            center = Offset(size.width / 2f, size.height * auraCenterY),
                            radius = size.width * 0.75f,
                        ),
                    )
                }
                if (tokens.isDark) drawRect(grain, alpha = 0.06f)
            },
        content = content,
    )
}

/** A 96 px tile of white noise, repeated: the «зерно» of A37 §9.5. Built once. */
@Composable
private fun rememberGrain(): Brush = remember {
    val n = 96
    val rnd = Random(7)
    val px = IntArray(n * n) { (rnd.nextInt(0, 256) shl 24) or 0xFFFFFF }
    val bmp: ImageBitmap = Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888).asImageBitmap()
    ShaderBrush(ImageShader(bmp, TileMode.Repeated, TileMode.Repeated))
}

/**
 * Glass card of «Титаниум» (A37 §9.1, §9.5): a translucent fill (white 7.5 % → 3 %), a hairline
 * edge and a 1 dp light on the top edge; no shadow in the dark theme. Light «Фарфор»: white card
 * with a soft shadow. [cornice] makes the top light brighter: the main card of a screen only.
 */
@Composable
fun HotCard(
    modifier: Modifier = Modifier,
    cornice: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(HotSpacing.cardPadding),
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = HotThemeTokens.colors
    val shape = HotCardShape
    val base = if (tokens.isDark) Modifier else Modifier.shadow(10.dp, shape, ambientColor = Color(0x22000000), spotColor = Color(0x22000000))
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Column(
            modifier = modifier
                .then(base)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(tokens.glassTop, tokens.glassBottom)), shape)
                .border(1.dp, tokens.hairline, shape)
                .drawWithContent {
                    drawContent()
                    if (tokens.isDark) {
                        val inset = 22.dp.toPx()
                        drawLine(
                            brush = Brush.horizontalGradient(
                                0f to Color.Transparent,
                                0.5f to tokens.glassEdge.copy(alpha = if (cornice) 0.5f else tokens.glassEdge.alpha),
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
 * A block that has no data yet: a dashed outline, as «нет данных» (A35) — honest about what is
 * still empty and as carefully drawn as a full card.
 */
@Composable
fun HotPendingCard(
    title: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    radius: Dp = 26.dp,
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
            .padding(horizontal = HotSpacing.cardPadding, vertical = 18.dp),
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

/** Telemetry line «ВТ · 10 НОЯБРЯ», «WHOOP · 06:41»: Geist Mono, tracked capitals, graphite. */
@Composable
fun HotOverline(text: String, modifier: Modifier = Modifier, color: Color = HotThemeTokens.colors.textTertiary) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = color, modifier = modifier, maxLines = 1, overflow = TextOverflow.Ellipsis)
}
