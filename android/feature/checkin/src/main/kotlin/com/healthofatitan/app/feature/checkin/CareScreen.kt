package com.healthofatitan.app.feature.checkin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.healthofatitan.app.core.ui.component.HotPrimaryButton
import com.healthofatitan.app.core.ui.component.HotSecondaryButton
import com.healthofatitan.app.core.ui.component.HotTextButton
import com.healthofatitan.app.core.ui.component.dialHelp
import com.healthofatitan.app.core.ui.component.openHelpDirectory
import com.healthofatitan.app.core.ui.component.rememberHelpLines
import com.healthofatitan.app.core.ui.theme.HotType

const val CARE_TAG = "checkin_care"
const val CARE_HELP_TAG = "checkin_care_help"
const val CARE_OK_TAG = "checkin_care_ok"

/**
 * «Как ты на самом деле?» (sc6, И2.8′, A07 §4.3 level 1): a sober screen — no Titan, no game.
 * «Нужна помощь сейчас» opens the SOS screen (its texts unchanged), «Поговорить» opens the mentor
 * in its careful mode, «Всё ок, просто день» closes. The crisis line and the emergency number of
 * the phone's country (988 and 911 in the US, 8-800-2000-122 and 112 in Russia; Y6) are on this
 * screen itself; without a line of ours the left button opens the directory.
 */
@Composable
fun CareScreen(onHelp: () -> Unit, onTalk: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val lines = rememberHelpLines()
    var okSaid by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
            .testTag(CARE_TAG),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text(CheckinTexts.CARE_TITLE, style = HotType.sosHeadline, modifier = Modifier.semantics { heading() })
        Text(CheckinTexts.CARE_LINE, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        HotPrimaryButton(CheckinTexts.CARE_HELP, onClick = onHelp, modifier = Modifier.fillMaxWidth().testTag(CARE_HELP_TAG))
        HotSecondaryButton(CheckinTexts.CARE_TALK, onClick = onTalk, modifier = Modifier.fillMaxWidth())
        HotSecondaryButton(
            CheckinTexts.CARE_OK,
            onClick = { if (okSaid) onClose() else okSaid = true },
            modifier = Modifier.fillMaxWidth().testTag(CARE_OK_TAG),
        )
        if (okSaid) {
            Text(CheckinTexts.CARE_OK_LINE, style = MaterialTheme.typography.bodyMedium)
            HotTextButton(CheckinTexts.CLOSE, onClick = onClose)
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { lines.crisisLine?.let { context.dialHelp(it) } ?: context.openHelpDirectory() },
                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
            ) { Text(lines.crisisShown ?: CheckinTexts.FIND_LINE, textAlign = TextAlign.Center) }
            OutlinedButton(onClick = { context.dialHelp(lines.emergency) }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                Text(lines.emergency)
            }
        }
    }
}
