package com.healthofatitan.app.feature.checkin

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.healthofatitan.app.core.ui.theme.HotThemeTokens

/**
 * One answer as a picture (И2.14′): ≥ 56 dp, the word under the glyph and as the description, a
 * soft dawn rim when chosen. One tap is the answer; the same tick for every card (alcohol and
 * «Только ты» included, A44 §6.5).
 */
@Composable
fun AnswerTile(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    spoken: String = label,
    haptics: Boolean = true,
    minHeight: Int = 84,
    glyph: @Composable (Color) -> Unit,
) {
    val tokens = HotThemeTokens.colors
    val haptic = LocalHapticFeedback.current
    val ink = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = {
            if (haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.75f) else tokens.rule),
        modifier = modifier
            .heightIn(min = minHeight.dp)
            .widthIn(min = 56.dp)
            .semantics(mergeDescendants = true) {
                this.selected = selected
                role = Role.Button
                contentDescription = spoken
            },
    ) {
        Column(
            Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.clearAndSetSemantics { }) { glyph(ink) }
            Spacer(Modifier.height(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = ink,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}

/** A small pill (moods, servings, «хотелось меньше», settings values). */
@Composable
fun CheckinChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, leading: (@Composable (Color) -> Unit)? = null) {
    val tokens = HotThemeTokens.colors
    val ink = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else tokens.rule),
        modifier = modifier.heightIn(min = 48.dp).semantics { this.selected = selected; role = Role.Checkbox },
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                leading(ink)
                Spacer(Modifier.width(8.dp))
            }
            Text(label, style = MaterialTheme.typography.labelLarge, color = ink)
        }
    }
}

/** A token of the chain under the Titan (w1): one per answered card. */
@Composable
fun AnswerToken(spoken: String, content: @Composable (Color) -> Unit) {
    val tokens = HotThemeTokens.colors
    Surface(
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, tokens.rule),
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = spoken },
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            content(MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Text straight on the night sky (no card around it) takes the ivory of the background. */
@Composable
internal fun NightContent(content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground, content = content)
