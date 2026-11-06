package com.healthofatitan.app.core.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.healthofatitan.app.core.ui.R
import com.healthofatitan.app.core.ui.theme.HotThemeTokens

/** TalkBack label for the SOS button (A11 §1.3). */
const val SOS_BUTTON_DESCRIPTION = "SOS, срочная поддержка"
const val SOS_BUTTON_TAG = "sos_button"
const val TOP_BAR_TITLE_TAG = "top_bar_title"

/**
 * Header of every tab screen, «Колоннада» (A36 §10, screens 02/06/07): the short lockup
 * «знак + HoT» on the left and SOS always on the right (A11 §1.3), then the gold overline and
 * the screen title in Cormorant. Transparent: it sits on the night-sky background.
 */
@Composable
fun HotTopBar(
    title: String,
    onSosClick: () -> Unit,
    modifier: Modifier = Modifier,
    overline: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = HotSpacing.edge, end = HotSpacing.edge, top = 8.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HotLockup()
            Spacer(Modifier.weight(1f))
            SosButton(onClick = onSosClick)
        }
        Spacer(Modifier.size(14.dp))
        if (overline != null) {
            HotOverline(overline)
            Spacer(Modifier.size(2.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .semantics { heading() }
                .testTag(TOP_BAR_TITLE_TAG),
        )
    }
}

/**
 * SOS pill: neutral, sober and always visible — no gold, no red, no glow (A36 §11 rule 7).
 * 48 dp touch target; one tap opens the offline SOS screen.
 */
@Composable
fun SosButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, HotThemeTokens.colors.hairline),
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics { contentDescription = SOS_BUTTON_DESCRIPTION }
            .testTag(SOS_BUTTON_TAG),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 18.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.core_ui_ic_sos),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "SOS",
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 0.08.em),
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}
