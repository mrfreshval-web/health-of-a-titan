package com.healthofatitan.app.core.ui.component

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.healthofatitan.app.core.ui.R
import com.healthofatitan.app.core.ui.theme.HotThemeTokens

const val TOP_BAR_TITLE_TAG = "top_bar_title"
const val TOP_BAR_BACK_TAG = "top_bar_back"
const val TOP_BAR_GLYPH_TAG = "top_bar_glyph"
const val TOP_BAR_CENTER_TAG = "top_bar_center"

/**
 * Header of every tab (D-038, A46 v0.3 §2.1): the mentor Винс as a circle on the left, the date in
 * the middle, the profile and settings as a circle on the right. No status rings any more (A46 §5).
 * While data comes the Titan glyph «Титан приходит» stands next to the date. There is no SOS button
 * here (D-029): help is two taps away — «профиль › Помощь» — plus the quick settings tile and the
 * launcher shortcut; the crisis card appears by itself on detection.
 *
 * @param title the screen title under the row (null on «Сегодня»)
 * @param mentor the mentor's circle on the left (D-038); null shows the Titan glyph there
 * @param center the line in the middle of the row (the date on «Сегодня»)
 * @param avatar the profile circle on the right with its menu (help, mentor, settings)
 */
@Composable
fun HotTopBar(
    title: String?,
    modifier: Modifier = Modifier,
    overline: String? = null,
    onBack: (() -> Unit)? = null,
    sync: SyncMarkState? = null,
    mentor: (@Composable () -> Unit)? = null,
    center: String? = null,
    avatar: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = HotSpacing.edge - 4.dp, end = HotSpacing.edge - 8.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when {
                onBack != null -> BackButton(onBack, overline)
                mentor != null -> mentor()
                else -> TitanGlyph(sync)
            }
            Row(
                Modifier.weight(1f).padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (mentor != null && onBack == null && sync != null && sync.phase != SyncMarkPhase.IDLE) {
                    Box(Modifier.size(28.dp).testTag(TOP_BAR_GLYPH_TAG), contentAlignment = Alignment.Center) {
                        HotSyncMark(state = sync, size = 28.dp, haptics = true)
                    }
                    Spacer(Modifier.width(8.dp))
                }
                if (center != null) {
                    Text(
                        center,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag(TOP_BAR_CENTER_TAG),
                    )
                }
            }
            if (avatar != null) avatar()
        }
        // The telemetry line («Винс · ИИ», the week, «Настройки · Подключения») under the row.
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
