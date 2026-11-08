package com.healthofatitan.app.feature.me.consent

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.healthofatitan.app.core.database.ConsentGate
import com.healthofatitan.app.core.ui.component.HotBackground
import com.healthofatitan.app.core.ui.component.HotCard
import com.healthofatitan.app.core.ui.component.HotPrimaryButton
import com.healthofatitan.app.core.ui.component.HotSecondaryButton
import com.healthofatitan.app.core.ui.component.HotTextButton
import com.healthofatitan.app.core.ui.text.text
import kotlinx.coroutines.delay

const val CONSENT_TAG = "unified_consent"
const val CONSENT_CHECK_TAG = "unified_consent_check"
const val CONSENT_ACCEPT_TAG = "unified_consent_accept"
const val CONSENT_DECLINE_TAG = "unified_consent_decline"
const val CONSENT_DECLINED_TAG = "unified_consent_declined"
const val CONSENT_UNDER_TAG = "unified_consent_under"
const val CONSENT_HELP_TAG = "unified_consent_help"
const val CONSENT_SAVE_FAILED_TAG = "unified_consent_save_failed"

/** What the screen shows besides the text: the text, «Не принимаю» or «Мне нет 18». */
enum class ConsentView { TEXT, DECLINED, UNDER }

/**
 * The unified consent (A68 v1) over the whole app, after «Вход» and before the first launch. The
 * text fits one screen with a scroll; «Продолжить» works only with the tick «Принимаю». «Помощь»
 * is above the screen: the crisis protocol is never behind a consent.
 *
 * @param view kept by the caller, so the person comes back to it from SOS.
 */
@Composable
fun UnifiedConsentScreen(
    gate: ConsentGate,
    view: ConsentView,
    onViewChange: (ConsentView) -> Unit,
    onAccept: () -> Unit,
    onHelp: () -> Unit,
    modifier: Modifier = Modifier,
    saveFailed: Boolean = false,
) {
    var checked by rememberSaveable { mutableStateOf(false) }
    var timedOut by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(gate) {
        if (gate == ConsentGate.LOADING) {
            delay(LOADING_TIMEOUT_MS)
            timedOut = true
        }
    }
    val ask = gate == ConsentGate.ASK || (gate == ConsentGate.LOADING && timedOut)
    HotBackground(modifier = modifier.fillMaxSize().testTag(CONSENT_TAG).pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            if (ask) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(top = 56.dp, bottom = 32.dp),
                ) {
                    when (view) {
                        ConsentView.TEXT -> ConsentText(checked, { checked = it }, onAccept, onViewChange, saveFailed)
                        ConsentView.DECLINED -> Notice(UnifiedConsentTexts.DECLINED_TITLE.text(), UnifiedConsentTexts.DECLINED_BODY.text(), CONSENT_DECLINED_TAG, onHelp) {
                            onViewChange(ConsentView.TEXT)
                        }
                        ConsentView.UNDER -> Notice(AgeGateTexts.ADULTS_ONLY_TITLE.text(), AgeGateTexts.ADULTS_ONLY_BODY.text(), AGE_GATE_ADULTS_ONLY_TAG, onHelp) {
                            onViewChange(ConsentView.TEXT)
                        }
                    }
                }
            }
            // «Помощь» stays on top of the screen, even while the journal is read.
            HotTextButton(
                UnifiedConsentTexts.HELP.text(),
                onClick = onHelp,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).testTag(CONSENT_HELP_TAG),
            )
        }
    }
}

@Composable
private fun ConsentText(checked: Boolean, onChecked: (Boolean) -> Unit, onAccept: () -> Unit, onView: (ConsentView) -> Unit, saveFailed: Boolean) {
    Text(UnifiedConsentTexts.TITLE.text(), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(8.dp))
    Text(UnifiedConsentTexts.INTRO.text(), style = MaterialTheme.typography.bodyMedium)
    UnifiedConsentTexts.SECTIONS.forEach { section ->
        Spacer(Modifier.height(16.dp))
        Text(section.title.text(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
        section.lines.forEach { line ->
            Spacer(Modifier.height(6.dp))
            Text(line.text(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Spacer(Modifier.height(16.dp))
    Text(UnifiedConsentTexts.CONFIRMS.text(), style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(12.dp))
    HotCard(cornice = true, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChecked)
                .testTag(CONSENT_CHECK_TAG),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = checked, onCheckedChange = null)
            Text(UnifiedConsentTexts.CHECK.text(), style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(8.dp))
        HotPrimaryButton(UnifiedConsentTexts.CONTINUE.text(), onClick = onAccept, enabled = checked, modifier = Modifier.fillMaxWidth().testTag(CONSENT_ACCEPT_TAG))
        if (saveFailed) {
            Spacer(Modifier.height(6.dp))
            Text(
                UnifiedConsentTexts.SAVE_FAILED.text(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(CONSENT_SAVE_FAILED_TAG).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        HotTextButton(UnifiedConsentTexts.DECLINE.text(), onClick = { onView(ConsentView.DECLINED) }, modifier = Modifier.testTag(CONSENT_DECLINE_TAG))
        HotTextButton(UnifiedConsentTexts.UNDER.text(), onClick = { onView(ConsentView.UNDER) }, modifier = Modifier.testTag(CONSENT_UNDER_TAG))
    }
}

@Composable
private fun Notice(title: String, body: String, tag: String, onHelp: () -> Unit, onBack: () -> Unit) {
    HotCard(cornice = true, modifier = Modifier.fillMaxWidth().testTag(tag)) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        HotPrimaryButton(UnifiedConsentTexts.HELP.text(), onClick = onHelp, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(4.dp))
        HotSecondaryButton(UnifiedConsentTexts.BACK.text(), onClick = onBack, modifier = Modifier.fillMaxWidth())
    }
}
