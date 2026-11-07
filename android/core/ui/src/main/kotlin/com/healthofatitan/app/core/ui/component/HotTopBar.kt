package com.healthofatitan.app.core.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.healthofatitan.app.core.ui.R
import com.healthofatitan.app.core.ui.theme.HotThemeTokens

const val TOP_BAR_TITLE_TAG = "top_bar_title"
const val TOP_BAR_BACK_TAG = "top_bar_back"
const val TOP_BAR_GLYPH_TAG = "top_bar_glyph"
const val TOP_BAR_WHY_TAG = "top_bar_why"

/**
 * Header of every tab, «Титаниум» (A37 §1, mockup s2; D-029): the Titan glyph in a glass circle
 * (it becomes the sync indicator «Титан приходит» while data comes), the telemetry line (date,
 * «Винс · ИИ», week), the three status rings and the mentor's avatar in the corner. There is no
 * SOS button here (D-029): help is two taps away — «аватар › Помощь» (D-033) and «⋮ › Помощь» in
 * the mentor chat — plus the quick settings tile and the launcher shortcut; the crisis card
 * appears by itself on detection. A tap on the rings opens what each ring is (UX review 119, Y1).
 *
 * @param title the screen title under the line (null on «Сегодня»: the hero speaks there)
 * @param avatar the avatar in the corner with its menu (D-033: settings, mentor, help)
 */
@Composable
fun HotTopBar(
    title: String?,
    modifier: Modifier = Modifier,
    overline: String? = null,
    onBack: (() -> Unit)? = null,
    sync: SyncMarkState? = null,
    rings: StatusRings = StatusRings.Empty,
    avatar: (@Composable () -> Unit)? = null,
) {
    // Not saveable: the explanation closes when the screen changes (it stayed open on the device, R2).
    var why by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = HotSpacing.edge - 4.dp, end = HotSpacing.edge - 8.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) BackButton(onBack, overline) else TitanGlyph(sync)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            HotStatusRings(rings, onClick = { why = !why }, expanded = why)
            if (avatar != null) {
                Spacer(Modifier.width(6.dp))
                avatar()
            }
        }
        // UX review 119 (R2, Y1): no run-on status line any more; a tap opens three named rows.
        AnimatedVisibility(why) {
            HotRingsExplainer(
                rings,
                modifier = Modifier
                    .padding(start = 4.dp, top = 10.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag(TOP_BAR_WHY_TAG),
            )
        }
        // The telemetry line («Винс · ИИ», the week, «Настройки · Подключения») under the row: the
        // rings with their pictograms (Y1) need the width, and a breadcrumb must not wrap per letter.
        if (overline != null) {
            Spacer(Modifier.size(12.dp))
            HotOverline(overline, modifier = Modifier.padding(start = 4.dp).clearAndSetSemantics { })
        }
        if (title != null) {
            Spacer(Modifier.size(if (overline != null) 6.dp else 18.dp))
            Text(
                title,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .semantics { heading() }
                    .testTag(TOP_BAR_TITLE_TAG),
            )
        }
    }
}

/** The Titan glyph in a glass circle, 44 dp; the live sync indicator while data comes. */
@Composable
private fun TitanGlyph(sync: SyncMarkState?) {
    val tokens = HotThemeTokens.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .border(1.dp, tokens.rule, CircleShape)
            .semantics(mergeDescendants = true) { contentDescription = HOT_BRAND_NAME }
            .testTag(TOP_BAR_GLYPH_TAG),
    ) {
        if (sync != null && sync.phase != SyncMarkPhase.IDLE) {
            HotSyncMark(state = sync, size = 40.dp, haptics = true)
        } else {
            HotMark(size = 26.dp, tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun BackButton(onBack: () -> Unit, label: String?) {
    Surface(
        onClick = onBack,
        shape = CircleShape,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .semantics { contentDescription = "Назад" + (label?.let { ": $it" } ?: "") }
            .testTag(TOP_BAR_BACK_TAG),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, end = 12.dp)) {
            Icon(painterResource(R.drawable.core_ui_ic_back), contentDescription = null, modifier = Modifier.size(24.dp))
            // the label is said by TalkBack and drawn as the overline under the row
        }
    }
}
