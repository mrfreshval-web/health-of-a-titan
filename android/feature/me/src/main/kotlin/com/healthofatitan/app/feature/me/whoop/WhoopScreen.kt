package com.healthofatitan.app.feature.me.whoop

import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.healthofatitan.app.core.ui.R
import com.healthofatitan.app.core.ui.component.HotCard
import com.healthofatitan.app.core.ui.component.HotNormRing
import com.healthofatitan.app.core.ui.component.HotOverline
import com.healthofatitan.app.core.ui.component.HotPrimaryButton
import com.healthofatitan.app.core.ui.component.HotSecondaryButton
import com.healthofatitan.app.core.ui.component.HotSourceChip
import com.healthofatitan.app.core.ui.component.HotSpacing
import com.healthofatitan.app.core.ui.component.HotSyncMark
import com.healthofatitan.app.core.ui.component.HotTextButton
import com.healthofatitan.app.core.ui.component.SyncMarkPhase
import com.healthofatitan.app.core.ui.component.SyncMarkState
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.core.ui.theme.HotType
import com.healthofatitan.app.integration.whoop.SyncIndicator
import com.healthofatitan.app.integration.whoop.SyncIndicators
import com.healthofatitan.app.integration.whoop.SyncLight
import com.healthofatitan.app.integration.whoop.SyncLog
import com.healthofatitan.app.integration.whoop.SyncLogEntry
import com.healthofatitan.app.integration.whoop.SyncProgress
import com.healthofatitan.app.integration.whoop.SyncStatus
import com.healthofatitan.app.integration.whoop.WhoopAuthFlow
import kotlinx.coroutines.delay
import java.time.Instant

const val WHOOP_SCREEN_TAG = "screen_whoop"
const val WHOOP_CONNECT_TAG = "whoop_connect"
const val WHOOP_DISCONNECT_TAG = "whoop_disconnect"
const val WHOOP_USER_ID_TAG = "whoop_user_id"
const val WHOOP_SYNC_STATUS_TAG = "whoop_sync_status"
const val WHOOP_LOG_TAG = "whoop_log"

/** Callbacks of the WHOOP screen; no-ops in previews and navigation tests. */
data class WhoopActions(
    val connect: () -> Unit = {},
    val sync: () -> Unit = {},
    val disconnect: () -> Unit = {},
    val dismissNote: () -> Unit = {},
    val clearLog: () -> Unit = {},
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
        actions = WhoopActions(viewModel::connect, viewModel::sync, viewModel::disconnect, viewModel::dismissNote, viewModel::clearLog),
        modifier = modifier,
    )
}

/**
 * «Я → Подключения → WHOOP». The hero is the HoT mark itself as the sync indicator
 * ([HotSyncMark]): tap = refresh, long press = «Журнал синхронизации». One line under it says
 * what is happening; everything else is a number, a ring or one word. Every state is drawn with
 * the same care (A36 §11 rule 10): not connected, connecting, not allowed, connected, syncing,
 * done, failed.
 */
@Composable
fun WhoopScreen(state: WhoopUiState, actions: WhoopActions, modifier: Modifier = Modifier) {
    var confirmDisconnect by rememberSaveable { mutableStateOf(false) }
    var showLog by rememberSaveable { mutableStateOf(false) }
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
            status.connected -> ConnectedContent(state, actions, onDisconnect = { confirmDisconnect = true }, onOpenLog = { showLog = true })
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
    if (showLog) SyncLogSheet(state.log, onClear = actions.clearLog, onDismiss = { showLog = false })
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            HotTextButton("OK", onClick = onDismiss)
        }
    }
}

/** «Сырые данные не храню · Подробнее»: one line, the details only on request. */
@Composable
private fun PrivacyLine() {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().animateContentSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                WhoopTexts.PRIVACY_SHORT,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            HotTextButton(if (open) WhoopTexts.LESS else WhoopTexts.MORE, onClick = { open = !open })
        }
        if (open) {
            WhoopTexts.PRIVACY.forEach { NodeLine(it) }
        }
    }
}

/** A line with a gold node: the plan's «узел» in miniature (A36 §2.4). */
@Composable
private fun NodeLine(text: String) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)) {
        Box(
            Modifier
                .padding(top = 7.dp)
                .size(6.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        )
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DisconnectedContent(state: WhoopUiState, actions: WhoopActions) {
    HotCard(cornice = true, modifier = Modifier.fillMaxWidth()) {
        StatusHeader(WhoopTexts.NOT_CONNECTED, connected = false)
        Spacer(Modifier.height(18.dp))
        HotSyncMark(SyncMarkState.Idle, size = 112.dp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(18.dp))
        Text(WhoopTexts.LEAD, style = HotType.quote, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            WhoopTexts.WHAT.forEachIndexed { i, word ->
                if (i > 0) Spacer(Modifier.width(14.dp))
                Box(Modifier.size(5.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(word, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    val expired = state.status.syncStatus == SyncStatus.REAUTH
    when {
        state.note != null -> NoteCard(WhoopTexts.note(state.note), actions.dismissNote)
        expired -> NoteCard(WhoopTexts.statusProblem(SyncStatus.REAUTH)!!, actions.dismissNote)
    }
    HotPrimaryButton(
        text = WhoopTexts.CONNECT,
        onClick = actions.connect,
        loading = state.phase == WhoopPhase.CONNECTING,
        modifier = Modifier.fillMaxWidth().testTag(WHOOP_CONNECT_TAG),
    )
    PrivacyLine()
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

/** Live indicator + a clock that ticks while a fresh success or «только что» may change. */
@Composable
private fun rememberNowAndIndicator(progress: SyncProgress, lastSyncAt: Long?): Pair<Long, SyncIndicator> {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(progress, lastSyncAt) {
        while (true) {
            now = System.currentTimeMillis()
            val next = SyncIndicators.nextChangeAt(progress, now)
            delay(next?.let { (it - now + 50).coerceAtMost(TICK_MS) } ?: TICK_MS)
        }
    }
    return now to SyncIndicators.of(progress, now)
}

private const val TICK_MS = 30_000L

internal fun SyncIndicator.toMark(): SyncMarkState =
    SyncMarkState(SyncMarkPhase.valueOf(light.name), progress, key, celebrate)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConnectedContent(state: WhoopUiState, actions: WhoopActions, onDisconnect: () -> Unit, onOpenLog: () -> Unit) {
    val status = state.status
    val (now, indicator) = rememberNowAndIndicator(state.progress, status.lastSyncAt)
    val running = indicator.light == SyncLight.RUNNING
    val failed = state.progress as? SyncProgress.Failed
    val line = when (indicator.light) {
        SyncLight.RUNNING -> (state.progress as? SyncProgress.Running)?.let { WhoopTexts.step(it.step) }
        SyncLight.SUCCESS -> (state.progress as? SyncProgress.Succeeded)?.counts?.let { WhoopTexts.done(it.nights, it.workouts) }
        SyncLight.ERROR -> failed?.let { WhoopTexts.failure(it.failure, it.httpCode) }
        SyncLight.IDLE -> null
    } ?: WhoopTexts.syncLine(status.lastSyncAt, now = Instant.ofEpochMilli(now))
    val stored = WhoopTexts.statusProblem(status.syncStatus).takeIf { state.progress is SyncProgress.Idle }

    HotCard(cornice = true, modifier = Modifier.fillMaxWidth()) {
        StatusHeader(WhoopTexts.CONNECTED, connected = true)
        Spacer(Modifier.height(12.dp))
        HotSyncMark(
            state = indicator.toMark(),
            size = 148.dp,
            contentDescription = line,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .combinedClickable(
                    onClickLabel = WhoopTexts.SYNC,
                    onLongClickLabel = WhoopTexts.LOG_TITLE,
                    onClick = { if (!running) actions.sync() },
                    onLongClick = onOpenLog,
                ),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            line,
            style = MaterialTheme.typography.titleMedium,
            color = if (indicator.light == SyncLight.SUCCESS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite }
                .testTag(WHOOP_SYNC_STATUS_TAG),
        )
        if (stored != null && indicator.light == SyncLight.IDLE) {
            Text(stored, style = MaterialTheme.typography.bodySmall, color = HotThemeTokens.colors.textTertiary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        if (indicator.light == SyncLight.ERROR && failed?.willRetry == false) {
            HotTextButton(WhoopTexts.RETRY, onClick = actions.sync, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(thickness = 1.dp, color = HotThemeTokens.colors.hairline)
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val nights = status.baselineNights
            HotNormRing(nights.coerceAtMost(14), 14, size = 48.dp)
            Spacer(Modifier.width(14.dp))
            Text(
                if (nights >= 14) WhoopTexts.NORM_READY else WhoopTexts.NORM,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
    if (state.note != null) NoteCard(WhoopTexts.note(state.note), actions.dismissNote)

    HotSecondaryButton(WhoopTexts.SYNC, onClick = actions.sync, enabled = !running, modifier = Modifier.fillMaxWidth())
    PrivacyLine()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        HotTextButton(WhoopTexts.LOG, onClick = onOpenLog, modifier = Modifier.testTag(WHOOP_LOG_TAG))
        HotTextButton(WhoopTexts.RECONNECT, onClick = actions.connect, enabled = state.phase == WhoopPhase.IDLE)
        HotTextButton(
            WhoopTexts.DISCONNECT,
            onClick = onDisconnect,
            enabled = state.phase == WhoopPhase.IDLE,
            modifier = Modifier.testTag(WHOOP_DISCONNECT_TAG),
        )
    }
}

/**
 * «Журнал синхронизации»: the last 20 attempts — time, who started it, result, HTTP code, step,
 * counts. No health values, no tokens. «Скопировать» puts it as text into the clipboard to send
 * to the developers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SyncLogSheet(entries: List<SyncLogEntry>, onClear: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = HotSpacing.edge)
                .padding(bottom = HotSpacing.sectionGap)
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                WhoopTexts.LOG_TITLE,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Text(WhoopTexts.LOG_HINT, style = MaterialTheme.typography.bodySmall, color = HotThemeTokens.colors.textTertiary)
            Spacer(Modifier.height(8.dp))
            Row {
                HotTextButton(if (copied) WhoopTexts.COPIED else WhoopTexts.COPY, enabled = entries.isNotEmpty(), onClick = {
                    val text = SyncLog.asText(entries) { WhoopTexts.logTime(it) }
                    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("HoT sync log", text))
                    copied = true
                })
                HotTextButton(WhoopTexts.LOG_CLEAR, enabled = entries.isNotEmpty(), onClick = onClear)
            }
            if (entries.isEmpty()) {
                Text(WhoopTexts.LOG_EMPTY, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            entries.forEach { e ->
                HorizontalDivider(thickness = 1.dp, color = HotThemeTokens.colors.hairline)
                Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
                    // ✓ / ! as a node shape, not colour alone (A36 §11 rule 5)
                    Box(
                        Modifier
                            .padding(top = 5.dp)
                            .size(8.dp)
                            .background(if (e.ok) MaterialTheme.colorScheme.primary else HotThemeTokens.colors.textTertiary, CircleShape),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "${if (e.ok) "✓" else "!"} ${WhoopTexts.logTime(e.at)} · ${WhoopTexts.trigger(e.trigger)}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(WhoopTexts.logLine(e), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
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
