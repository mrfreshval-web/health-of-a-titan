package com.healthofatitan.app.feature.checkin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.healthofatitan.app.core.datastore.CheckinPrefs
import com.healthofatitan.app.core.ui.component.HotCard
import com.healthofatitan.app.core.ui.component.HotOverline
import com.healthofatitan.app.core.ui.component.HotSpacing
import com.healthofatitan.app.core.ui.component.HotTextButton
import com.healthofatitan.app.core.ui.text.text
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.domain.checkin.CardId

const val SETTINGS_TAG = "checkin_settings"
const val SETTINGS_INTIMACY_TAG = "checkin_settings_intimacy"

@Composable
fun CheckinSettingsRoute(onOpenCollection: () -> Unit, viewModel: CheckinViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { viewModel.loadSettings() }
    val ui by viewModel.settings.collectAsStateWithLifecycle()
    CheckinSettingsScreen(ui, onChange = viewModel::updatePrefs, onDeletePrivate = viewModel::deleteAllPrivate, onOpenCollection = onOpenCollection)
}

/**
 * «Что спрашивать» v0.2 (sc7, И2.17′): groups wake · after the ritual · evening · «Только ты» ·
 * game. Each row has its «зачем» under the title. A switched-off card disappears from the deck.
 * «Только ты» switches need the fingerprint to turn on.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CheckinSettingsScreen(
    ui: SettingsUi,
    onChange: ((CheckinPrefs) -> CheckinPrefs) -> Unit,
    onDeletePrivate: () -> Unit,
    onOpenCollection: () -> Unit,
) {
    val p = ui.prefs
    val context = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    fun card(c: CardId) = c.ref !in p.off
    fun toggleCard(c: CardId) = onChange { it.copy(off = if (c.ref in it.off) it.off - c.ref else it.off + c.ref) }
    fun privately(change: (CheckinPrefs) -> CheckinPrefs, turningOn: Boolean) {
        if (turningOn) context.unlockOnlyYou { ok -> if (ok) onChange(change) } else onChange(change)
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = HotSpacing.edge, vertical = 8.dp)
            .testTag(SETTINGS_TAG),
        verticalArrangement = Arrangement.spacedBy(HotSpacing.cardGap),
    ) {
        Group(CheckinTexts.S_WAKE) {
            HotOverline(CheckinTexts.S_WAKE_CORE)
            listOf(CardId.BODY, CardId.HEAD, CardId.DRIVE, CardId.YESTERDAY, CardId.SLEPT).forEach { c ->
                Toggle(CheckinTexts.title(c), CheckinTexts.why(c), card(c)) { toggleCard(c) }
            }
            Toggle(CheckinTexts.S_QUICK, CheckinTexts.S_QUICK_WHY, p.quick) { onChange { it.copy(quick = !it.quick) } }
        }
        Group(CheckinTexts.S_RITUAL) {
            Toggle(CheckinTexts.S_RITUAL, CheckinTexts.S_RITUAL_WHY, p.ritual) { onChange { it.copy(ritual = !it.ritual) } }
            Toggle(CheckinTexts.S_RITUAL_REMIND, CheckinTexts.S_RITUAL_REMIND_WHY, p.ritualReminder) { onChange { it.copy(ritualReminder = !it.ritualReminder) } }
            HotOverline(CheckinTexts.S_STACK)
            if (p.stack.isEmpty()) {
                Text(CheckinTexts.S_STACK_EMPTY, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    p.stack.forEach { name ->
                        CheckinChip("$name  ✕", selected = true, onClick = { onChange { it.copy(stack = it.stack - name) } }, leading = { c -> StackGlyph(c, size = 18.dp) })
                    }
                }
            }
            Text(CheckinTexts.STACK_HINT.text(), style = MaterialTheme.typography.bodySmall, color = HotThemeTokens.colors.textTertiary)
        }
        Group(CheckinTexts.S_EVENING) {
            Toggle(CheckinTexts.S_EVENING, CheckinTexts.S_EVENING_WHY, p.evening) { onChange { it.copy(evening = !it.evening) } }
            Toggle(CheckinTexts.S_DRINKS, CheckinTexts.S_DRINKS_WHY, !p.drinks) { onChange { it.copy(drinks = !it.drinks) } }
        }
        Group(CheckinTexts.ONLY_YOU) {
            HotOverline(CheckinTexts.ONLY_YOU_LINE)
            Toggle(CheckinTexts.CLOSENESS, CheckinTexts.why(CardId.INTIMACY), p.intimacy, Modifier.testTag(SETTINGS_INTIMACY_TAG)) {
                privately({ it.copy(intimacy = !it.intimacy, privateAsked = true) }, turningOn = !p.intimacy)
            }
            Toggle(CheckinTexts.PULLS, CheckinTexts.why(CardId.PULLS), p.pulls) {
                privately({ it.copy(pulls = !it.pulls, privateAsked = true) }, turningOn = !p.pulls)
            }
            Toggle(CheckinTexts.PRIVATE_LINKS, CheckinTexts.PRIVATE_LINKS_WHY, p.links) {
                privately({ it.copy(links = !it.links) }, turningOn = !p.links)
            }
            HotTextButton(CheckinTexts.PRIVATE_OFF_ALL, onClick = { confirm = true })
        }
        Group(CheckinTexts.S_GAME) {
            Toggle(CheckinTexts.S_DISCOVERIES, CheckinTexts.S_DISCOVERIES_WHY, p.discoveries) { onChange { it.copy(discoveries = !it.discoveries) } }
            Toggle(CheckinTexts.S_HAPTICS, null, p.haptics) { onChange { it.copy(haptics = !it.haptics) } }
            HotTextButton(CheckinTexts.COLLECTION, onClick = onOpenCollection)
        }
        Group(CheckinTexts.S_USAGE) {
            Text(CheckinTexts.usage(ui.wakeSec, ui.ritualSec), style = MaterialTheme.typography.bodyMedium)
            ui.skips.forEach { (c, share) -> HotOverline(CheckinTexts.skipShare(c, share)) }
        }
        Spacer(Modifier.height(24.dp))
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            text = { Text(CheckinTexts.PRIVATE_OFF_CONFIRM) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    onDeletePrivate()
                }) { Text(CheckinTexts.PRIVATE_OFF_ALL) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(CheckinTexts.KEEP) } },
        )
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    HotCard(modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }
    }
}

@Composable
private fun Toggle(title: String, why: String?, on: Boolean, modifier: Modifier = Modifier, onToggle: () -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = on, role = Role.Switch, onValueChange = { onToggle() }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (why != null) Text("ⓘ $why", style = MaterialTheme.typography.bodySmall, color = HotThemeTokens.colors.textTertiary)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = on, onCheckedChange = null)
    }
}
