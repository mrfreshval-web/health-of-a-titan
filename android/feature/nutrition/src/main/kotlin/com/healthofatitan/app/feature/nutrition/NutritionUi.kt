package com.healthofatitan.app.feature.nutrition

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.domain.nutrition.Plate

/** Plate roles → light (one meaning everywhere, A41 mockups): veg Лёд, protein Рассвет, starch Ирис. */
@Composable
fun roleColor(role: String): Color {
    val t = HotThemeTokens.colors
    return when (role) {
        "veg", "fruit" -> t.modeRovno
        "protein" -> MaterialTheme.colorScheme.primary
        "starch" -> t.modeBerezhno
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

/** The order the plate is drawn in, clockwise from twelve. */
internal val PLATE_ROLES = listOf("veg", "protein", "starch", "fat")

/**
 * The plate as a ring of shares (A41 §5.1): no numbers, the sectors are the message. The word of
 * every share is in the content description.
 */
@Composable
fun PlateDonut(plate: Plate, size: Dp, modifier: Modifier = Modifier, stroke: Dp = size * 0.2f) {
    val parts = PLATE_ROLES.mapNotNull { r -> plate.share[r]?.takeIf { it > 0 }?.let { r to it } }
    val colors = parts.map { roleColor(it.first) }
    val track = HotThemeTokens.colors.ruleSoft
    val gap = MaterialTheme.colorScheme.background
    val words = parts.joinToString(", ") { NutritionTexts.roleWord(it.first) }
    Canvas(
        modifier
            .size(size)
            .semantics { contentDescription = "${plate.ru}: $words" },
    ) {
        val w = stroke.toPx()
        val d = this.size.minDimension - w
        val topLeft = Offset(w / 2, w / 2)
        drawArc(track, 0f, 360f, false, topLeft, Size(d, d), style = Stroke(w + 6.dp.toPx()))
        var start = -90f
        val total = parts.sumOf { it.second }.toFloat().coerceAtLeast(0.01f)
        parts.forEachIndexed { i, (_, share) ->
            val sweep = 360f * share.toFloat() / total
            drawArc(colors[i], start, sweep, false, topLeft, Size(d, d), style = Stroke(w))
            drawArc(gap, start + sweep - 0.8f, 1.6f, false, topLeft, Size(d, d), style = Stroke(w + 1f))
            start += sweep
        }
    }
}

/** A capsule: hairline, selected = light rim and the accent. Minimum 40 dp touch height. */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    accent: Color = HotThemeTokens.colors.modeRovno,
    glyph: FoodGlyph? = null,
    onClick: (() -> Unit)? = null,
) {
    val rim = if (selected) accent else HotThemeTokens.colors.rule
    val fg = if (selected) accent else MaterialTheme.colorScheme.onSurface
    Row(
        modifier
            .clip(CircleShape)
            .border(1.dp, rim, CircleShape)
            .then(
                if (onClick != null) {
                    Modifier
                        .clickable(role = Role.Checkbox, onClick = onClick)
                        .semantics { this.selected = selected }
                } else {
                    Modifier
                },
            )
            .heightIn(min = 40.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (glyph != null) FoodIcon(glyph, fg, size = 16.dp)
        Text(text, style = MaterialTheme.typography.labelMedium, color = fg)
    }
}

/** Hand portion: pictogram + count (count omitted when numbers are hidden). */
@Composable
fun HandPortion(role: String, count: Double?, modifier: Modifier = Modifier) {
    val color = roleColor(role)
    Row(
        modifier.semantics(mergeDescendants = true) {
            contentDescription = listOfNotNull(NutritionTexts.handWord(role), count?.let(NutritionTexts::hands)).joinToString(" ")
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FoodIcon(FoodGlyphs.hand(role), color, size = 18.dp, strokeWidth = 1.4f)
        if (count != null) {
            Text(NutritionTexts.hands(count), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A round node of the line of the day. */
@Composable
fun Node(color: Color, glyph: FoodGlyph, modifier: Modifier = Modifier, size: Dp = 36.dp, filled: Boolean = false, onClick: (() -> Unit)? = null) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .border(1.dp, if (filled) color else HotThemeTokens.colors.rule, CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        FoodIcon(glyph, color, size = size * 0.55f)
    }
}
