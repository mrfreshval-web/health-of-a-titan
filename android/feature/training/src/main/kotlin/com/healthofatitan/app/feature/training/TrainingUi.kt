package com.healthofatitan.app.feature.training

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.healthofatitan.app.core.ui.component.HotMode
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.core.ui.theme.Titanium
import com.healthofatitan.app.domain.training.BlockType
import com.healthofatitan.app.domain.training.Exercise
import com.healthofatitan.app.domain.training.PoseLibrary
import com.healthofatitan.app.domain.training.PoseTimeline
import com.healthofatitan.app.domain.training.SessionItem
import com.healthofatitan.app.domain.training.TrainingSession

const val TRAINING_CARD_TAG = "training_day_card"
const val TRAINING_SCREEN_TAG = "screen_training"
const val TRAINING_START_TAG = "training_start"
const val PLAYER_SCREEN_TAG = "screen_training_player"
const val PLAYER_FEEL_BAD_TAG = "training_feel_bad"
const val PLAYER_PAUSED_TAG = "training_paused"
const val PLAYER_FEEL_BAD_HELP_TAG = "training_feel_bad_help"
const val PLAYER_DONE_TAG = "training_done"

/** The line of light of the Titan in the day's mode (A37 §3: Солнце, Лёд, Ирис); ink in «Фарфор». */
@Composable
fun figureLight(mode: HotMode): List<Color> {
    val tokens = HotThemeTokens.colors
    if (!tokens.isDark) return tokens.titanLight
    return when (mode) {
        HotMode.RAZGON -> mode.dawnLight()
        HotMode.ROVNO -> Titanium.LightIce
        HotMode.BEREZHNO -> Titanium.LightIris
    }
}

/** The exercise the card and the list show as the Titan: the first of the main part. */
fun TrainingSession.heroItem(): SessionItem? =
    blocks.firstOrNull { it.type == BlockType.MAIN }?.items?.firstOrNull()
        ?: items.firstOrNull()

fun timeline(exercise: Exercise?, poses: PoseLibrary, tempo: String? = null, otherSide: Boolean = false): PoseTimeline =
    PoseTimeline.of(exercise, poses, tempo, otherSide)

fun Exercise.frontView(): Boolean = animation.view == "front"

/** A hairline capsule choice (place, sound level, RPE), 48 dp high. */
@Composable
fun TrainingChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    tag: String? = null,
) {
    val tokens = HotThemeTokens.colors
    val chosen = selected
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) accent.copy(alpha = 0.16f) else Color.Transparent,
        border = BorderStroke(1.dp, if (selected) accent else tokens.rule),
        modifier = modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
            .semantics {
                this.selected = chosen
                role = Role.RadioButton
            }
            .let { if (tag != null) it.testTag(tag) else it },
    ) {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * «Мне плохо» (A40 §2.5): on every player screen, in the crisis colour of the M3 `error` role
 * (red lives only there, A37 §9.1), one tap stops everything and opens help.
 */
@Composable
fun FeelBadButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
        modifier = modifier.heightIn(min = 48.dp).testTag(PLAYER_FEEL_BAD_TAG),
    ) {
        Box(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            Text(TrainingTexts.FEEL_BAD, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
        }
    }
}
