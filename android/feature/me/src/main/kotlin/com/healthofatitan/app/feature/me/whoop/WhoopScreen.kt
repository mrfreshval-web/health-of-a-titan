package com.healthofatitan.app.feature.me.whoop

import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.healthofatitan.app.core.ui.R
import com.healthofatitan.app.core.ui.component.HotCard
import com.healthofatitan.app.core.ui.component.HotOverline
import com.healthofatitan.app.core.ui.component.HotPrimaryButton
import com.healthofatitan.app.core.ui.component.HotSecondaryButton
import com.healthofatitan.app.core.ui.component.HotSourceChip
import com.healthofatitan.app.core.ui.component.HotSpacing
import com.healthofatitan.app.core.ui.component.HotTextButton
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.core.ui.theme.HotType
import com.healthofatitan.app.integration.whoop.SyncStatus
import com.healthofatitan.app.integration.whoop.WhoopAuthFlow

const val WHOOP_SCREEN_TAG = "screen_whoop"
const val WHOOP_CONNECT_TAG = "whoop_connect"
const val WHOOP_DISCONNECT_TAG = "whoop_disconnect"
const val WHOOP_USER_ID_TAG = "whoop_user_id"

/** Callbacks of the WHOOP screen; no-ops in previews and navigation tests. */
data class WhoopActions(
    val connect: () -> Unit = {},
    val sync: () -> Unit = {},
    val disconnect: () -> Unit = {},
    val dismissNote: () -> Unit = {},
)

/** Stateful entry: ViewModel + AppAuth browser step. */
@Composable
fun WhoopRoute(modifier: Modifier = Modifier, viewModel: WhoopViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val auth = remember { WhoopAuthFlow(context) }
    DisposableEffect(auth) { onDispose { auth.dispose() } }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        viewModel.onRedirect(WhoopAuthFlow.parse(result.data))
    }
    LaunchedEffect(viewModel) {
        viewModel.authRequests.collect { launcher.launch(auth.intent(it)) }
    }
    WhoopScreen(
        state = state,
        actions = WhoopActions(viewModel::connect, viewModel::sync, viewModel::disconnect, viewModel::dismissNote),
        modifier = modifier,
    )
}

/**
 * «Я → Подключения → WHOOP» in «Колоннада» (A36): the hero card with the gold cornice carries the
 * status, one gold action per screen (connect), everything else is quiet. Every state is drawn
 * with the same care (A36 §11 rule 10): not connected, connecting, not allowed, connected,
 * learning the norm, offline.
 */
@Composable
fun WhoopScreen(state: WhoopUiState, actions: WhoopActions, modifier: Modifier = Modifier) {
    var confirmDisconnect by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = HotSpacing.edge, end = HotSpacing.edge, top = 4.dp, bottom = HotSpacing.sectionGap)
            .testTag(WHOOP_SCREEN_TAG),
        verticalArrangement = Arrangement.spacedBy(HotSpacing.cardGap),
    ) {
        val status = state.status
        when {
            !status.configured -> NotConfiguredCard()
            status.connected -> ConnectedContent(state, actions, onDisconnect = { confirmDisconnect = true })
            state.notAllowedUserId != null -> NotAllowedContent(state.notAllowedUserId, state, actions)
            else -> DisconnectedContent(state, actions)
        }
    }
    if (confirmDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            title = { Text(WhoopTexts.DISCONNECT_TITLE, style = MaterialTheme.typography.headlineSmall) },
            text = { Text(WhoopTexts.DISCONNECT_TEXT, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                HotTextButton(WhoopTexts.DISCONNECT_CONFIRM, onClick = {
                    confirmDisconnect = false
                    actions.disconnect()
                })
            },
            dismissButton = { HotTextButton(WhoopTexts.CANCEL, onClick = { confirmDisconnect = false }) },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
}

@Composable
private fun StatusHeader(statusText: String, connected: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        HotSourceChip(WhoopTexts.SOURCE, R.drawable.core_ui_ic_band)
        Spacer(Modifier.width(10.dp))
        // Status = dot shape + word, never colour alone (A36 §11 rule 5).
        Box(
            Modifier
                .size(8.dp)
                .background(
                    if (connected) MaterialTheme.colorScheme.primary else HotThemeTokens.colors.textTertiary,
                    CircleShape,
                ),
        )
        Spacer(Modifier.width(8.dp))
        Text(statusText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NoteCard(text: String, onDismiss: () -> Unit) {
    HotCard(modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(4.dp))
        HotTextButton("Понятно", onClick = onDismiss, modifier = Modifier.align(Alignment.End))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(Modifier.height(8.dp))
    Text(text, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.semantics { heading() })
}

/** A line of a list with a gold node: the plan's «узел» in miniature (A36 §2.4). */
@Composable
private fun NodeLine(title: String, text: String? = null) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 6.dp)) {
        Box(
            Modifier
                .padding(top = 7.dp)
                .size(7.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            if (text != null) Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DisconnectedContent(state: WhoopUiState, actions: WhoopActions) {
    HotCard(cornice = true, modifier = Modifier.fillMaxWidth()) {
        StatusHeader(WhoopTexts.NOT_CONNECTED, connected = false)
        Spacer(Modifier.height(14.dp))
        Text(WhoopTexts.LEAD, style = HotType.quote, color = MaterialTheme.colorScheme.onSurface)
    }
    val expired = state.status.syncStatus == SyncStatus.REAUTH
    when {
        state.note != null -> NoteCard(WhoopTexts.note(state.note), actions.dismissNote)
        expired -> NoteCard(WhoopTexts.statusProblem(SyncStatus.REAUTH)!!, actions.dismissNote)
    }
    SectionTitle(WhoopTexts.WHAT_TITLE)
    HotCard(modifier = Modifier.fillMaxWidth()) {
        WhoopTexts.WHAT.forEach { (title, text) -> NodeLine(title, text) }
    }
    SectionTitle(WhoopTexts.PRIVACY_TITLE)
    HotCard(modifier = Modifier.fillMaxWidth()) {
        WhoopTexts.PRIVACY.forEach { NodeLine(it) }
    }
    Spacer(Modifier.height(8.dp))
    HotPrimaryButton(
        text = WhoopTexts.CONNECT,
        onClick = actions.connect,
        loading = state.phase == WhoopPhase.CONNECTING,
        modifier = Modifier.fillMaxWidth().testTag(WHOOP_CONNECT_TAG),
    )
    Text(
        WhoopTexts.CONNECT_HINT,
        style = MaterialTheme.typography.bodySmall,
        color = HotThemeTokens.colors.textTertiary,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
    )
}

@Composable
private fun NotAllowedContent(userId: String, state: WhoopUiState, actions: WhoopActions) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    HotCard(cornice = true, modifier = Modifier.fillMaxWidth()) {
        StatusHeader(WhoopTexts.NOT_ALLOWED_STATUS, connected = false)
        Spacer(Modifier.height(14.dp))
        Text(WhoopTexts.NOT_ALLOWED_LEAD, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(16.dp))
        HotOverline("WHOOP ID")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                userId,
                style = HotType.numeral,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).testTag(WHOOP_USER_ID_TAG),
            )
            HotSecondaryButton(
                text = if (copied) WhoopTexts.COPIED else WhoopTexts.COPY,
                onClick = {
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText("WHOOP ID", userId))
                    copied = true
                },
            )
        }
    }
    HotCard(modifier = Modifier.fillMaxWidth()) {
        WhoopTexts.NOT_ALLOWED_STEPS.forEachIndexed { i, step ->
            Row(modifier = Modifier.padding(vertical = 6.dp)) {
                Text("${i + 1}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(24.dp))
                Text(step, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(WhoopTexts.NOT_ALLOWED_SAFE, style = MaterialTheme.typography.bodySmall, color = HotThemeTokens.colors.textTertiary)
    }
    if (state.note != null) NoteCard(WhoopTexts.note(state.note), actions.dismissNote)
    Spacer(Modifier.height(8.dp))
    HotPrimaryButton(
        text = WhoopTexts.CONNECT,
        onClick = actions.connect,
        loading = state.phase == WhoopPhase.CONNECTING,
        modifier = Modifier.fillMaxWidth().testTag(WHOOP_CONNECT_TAG),
    )
}

@Composable
private fun ConnectedContent(state: WhoopUiState, actions: WhoopActions, onDisconnect: () -> Unit) {
    val status = state.status
    HotCard(cornice = true, modifier = Modifier.fillMaxWidth()) {
        StatusHeader(WhoopTexts.CONNECTED, connected = true)
        Spacer(Modifier.height(14.dp))
        Text(WhoopTexts.syncLine(status.lastSyncAt), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        WhoopTexts.statusProblem(status.syncStatus)?.let { problem ->
            Spacer(Modifier.height(6.dp))
            Text(
                problem,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
    if (state.note != null) NoteCard(WhoopTexts.note(state.note), actions.dismissNote)

    SectionTitle(WhoopTexts.NORM_TITLE)
    HotCard(modifier = Modifier.fillMaxWidth()) {
        val nights = status.baselineNights
        if (nights < 14) {
            Text(WhoopTexts.NORM_LEARNING, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { nights / 14f },
                color = MaterialTheme.colorScheme.primary,
                trackColor = HotThemeTokens.colors.ruleSoft,
                drawStopIndicator = {},
                modifier = Modifier.fillMaxWidth().height(4.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(WhoopTexts.normNights(nights), style = MaterialTheme.typography.bodySmall, color = HotThemeTokens.colors.textTertiary)
        } else {
            Text(WhoopTexts.NORM_READY, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(WhoopTexts.normWindow(nights), style = MaterialTheme.typography.bodySmall, color = HotThemeTokens.colors.textTertiary)
        }
    }

    SectionTitle(WhoopTexts.PRIVACY_TITLE)
    HotCard(modifier = Modifier.fillMaxWidth()) {
        WhoopTexts.PRIVACY.take(2).forEach { NodeLine(it) }
    }

    Spacer(Modifier.height(8.dp))
    HotSecondaryButton(WhoopTexts.SYNC, onClick = actions.sync, modifier = Modifier.fillMaxWidth())
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        HotTextButton(WhoopTexts.RECONNECT, onClick = actions.connect, enabled = state.phase == WhoopPhase.IDLE)
        HotTextButton(
            WhoopTexts.DISCONNECT,
            onClick = onDisconnect,
            enabled = state.phase == WhoopPhase.IDLE,
            modifier = Modifier.testTag(WHOOP_DISCONNECT_TAG),
        )
    }
}

@Composable
private fun NotConfiguredCard() {
    HotCard(cornice = true, modifier = Modifier.fillMaxWidth()) {
        StatusHeader(WhoopTexts.NOT_CONNECTED, connected = false)
        Spacer(Modifier.height(14.dp))
        Text(WhoopTexts.NOT_CONFIGURED, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}
