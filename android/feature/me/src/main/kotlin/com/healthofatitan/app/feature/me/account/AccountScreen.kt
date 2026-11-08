package com.healthofatitan.app.feature.me.account

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.healthofatitan.app.core.account.AccountCredentials
import com.healthofatitan.app.core.account.AccountView
import com.healthofatitan.app.core.account.DeviceDto
import com.healthofatitan.app.core.account.IdentityDto
import com.healthofatitan.app.core.model.nav.HotRoute
import com.healthofatitan.app.core.ui.component.HotCard
import com.healthofatitan.app.core.ui.component.HotOverline
import com.healthofatitan.app.core.ui.component.HotSecondaryButton
import com.healthofatitan.app.core.ui.component.HotSpacing
import com.healthofatitan.app.core.ui.component.HotTextButton
import com.healthofatitan.app.core.ui.text.text
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.integration.whoop.WhoopAuthFlow

const val ACCOUNT_SCREEN_TAG = "screen_account"
const val ACCOUNT_SIGN_OUT_TAG = "account_sign_out"
const val ACCOUNT_SIGN_OUT_ALL_TAG = "account_sign_out_all"
const val ACCOUNT_DELETE_TAG = "account_delete"
const val ACCOUNT_DELETE_CONFIRM_TAG = "account_delete_confirm"
const val ACCOUNT_NOTE_TAG = "account_note"

fun accountLinkTag(provider: String) = "account_link_$provider"

fun accountUnlinkTag(provider: String) = "account_unlink_$provider"

fun accountDeviceTag(id: String) = "account_device_$id"

/** Callbacks of «Аккаунт»; no-ops in previews and tests. */
data class AccountActions(
    val link: (String) -> Unit = {},
    val unlink: (String) -> Unit = {},
    val endDevice: (DeviceDto) -> Unit = {},
    val signOut: () -> Unit = {},
    val signOutAll: () -> Unit = {},
    val openDelete: (Boolean) -> Unit = {},
    val delete: () -> Unit = {},
    val confirm: (AccountConfirm) -> Unit = {},
    val dismissConfirm: () -> Unit = {},
    val switchAccount: () -> Unit = {},
    val closeInUse: () -> Unit = {},
)

/**
 * «Настройки › Аккаунт» (A65 §2.3, §3.4, §6).
 *
 * @param stateful false renders an empty screen without a ViewModel (navigation tests run without Hilt).
 */
fun NavGraphBuilder.accountScreen(stateful: Boolean = true) {
    composable(route = HotRoute.ACCOUNT) {
        if (stateful) AccountRoute() else AccountScreen(AccountUiState(loading = false), AccountActions())
    }
}

@Composable
private fun AccountRoute(viewModel: AccountViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val auth = remember { WhoopAuthFlow(context) }
    DisposableEffect(auth) { onDispose { auth.dispose() } }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        viewModel.onWhoopRedirect(WhoopAuthFlow.parse(result.data), context)
    }
    LaunchedEffect(viewModel) {
        viewModel.authRequests.collect { launcher.launch(auth.intent(it)) }
    }
    AccountScreen(
        state = state,
        actions = AccountActions(
            link = { provider ->
                viewModel.run(if (provider == AccountCredentials.PROVIDER_WHOOP) FreshAction.LINK_WHOOP else FreshAction.LINK_GOOGLE, context)
            },
            unlink = { provider ->
                viewModel.run(if (provider == AccountCredentials.PROVIDER_WHOOP) FreshAction.UNLINK_WHOOP else FreshAction.UNLINK_GOOGLE, context)
            },
            endDevice = { viewModel.endDevice(it.id) },
            signOut = viewModel::signOut,
            signOutAll = viewModel::signOutAll,
            openDelete = viewModel::openDelete,
            delete = { viewModel.run(FreshAction.DELETE, context) },
            confirm = { viewModel.ask(it) },
            dismissConfirm = { viewModel.ask(null) },
            switchAccount = viewModel::signOut,
            closeInUse = viewModel::closeInUse,
        ),
    )
}

@Composable
fun AccountScreen(state: AccountUiState, actions: AccountActions, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = HotSpacing.edge, end = HotSpacing.edge, top = 4.dp, bottom = HotSpacing.sectionGap)
            .testTag(ACCOUNT_SCREEN_TAG),
        verticalArrangement = Arrangement.spacedBy(HotSpacing.cardGap),
    ) {
        state.note?.let { note ->
            Text(
                AccountTexts.note(note).text(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag(ACCOUNT_NOTE_TAG).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (state.deleting) {
            DeleteStep(state, actions)
        } else {
            Overview(state, actions)
        }
    }
    Dialogs(state, actions)
}

@Composable
private fun Overview(state: AccountUiState, actions: AccountActions) {
    val view = state.view
    Section(AccountTexts.WAYS_IN.text())
    HotCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(AccountCredentials.PROVIDER_GOOGLE to AccountTexts.GOOGLE, AccountCredentials.PROVIDER_WHOOP to AccountTexts.WHOOP).forEach { (provider, title) ->
                WayIn(provider, title.text(), view, state, actions)
            }
            HotOverline(AccountTexts.STEP_UP_NOTE.text())
        }
    }
    val devices = view?.devices.orEmpty()
    if (devices.isNotEmpty()) {
        Section(AccountTexts.DEVICES.text())
        HotCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                devices.forEach { device -> Device(device, state.busy, actions) }
            }
        }
    }
    HotCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HotSecondaryButton(
                AccountTexts.SIGN_OUT.text(),
                onClick = { actions.confirm(AccountConfirm.SignOut) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().testTag(ACCOUNT_SIGN_OUT_TAG),
            )
            HotSecondaryButton(
                AccountTexts.SIGN_OUT_ALL.text(),
                onClick = { actions.confirm(AccountConfirm.SignOutAll) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().testTag(ACCOUNT_SIGN_OUT_ALL_TAG),
            )
            HotTextButton(
                AccountTexts.DELETE.text(),
                onClick = { actions.openDelete(true) },
                enabled = !state.busy && view != null,
                modifier = Modifier.testTag(ACCOUNT_DELETE_TAG),
            )
        }
    }
}

@Composable
private fun WayIn(provider: String, title: String, view: AccountView?, state: AccountUiState, actions: AccountActions) {
    val identity: IdentityDto? = view?.identities?.firstOrNull { it.provider == provider }
    val last = view != null && view.identities.size < 2
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                identity?.let { AccountTexts.linked(it.linkedAt, it.linkedDevice).text() } ?: AccountTexts.NOT_LINKED.text(),
                style = MaterialTheme.typography.bodySmall,
                color = HotThemeTokens.colors.textTertiary,
            )
        }
        when {
            view == null -> Unit
            identity == null -> HotTextButton(
                if (provider == AccountCredentials.PROVIDER_WHOOP) AccountTexts.LINK_WHOOP.text() else AccountTexts.LINK_GOOGLE.text(),
                onClick = { actions.link(provider) },
                enabled = !state.busy && (provider == AccountCredentials.PROVIDER_WHOOP || state.googleAvailable),
                modifier = Modifier.testTag(accountLinkTag(provider)),
            )
            else -> HotTextButton(
                AccountTexts.UNLINK.text(),
                onClick = { actions.confirm(AccountConfirm.Unlink(provider)) },
                enabled = !state.busy && !last,
                modifier = Modifier.testTag(accountUnlinkTag(provider)),
            )
        }
    }
}

@Composable
private fun Device(device: DeviceDto, busy: Boolean, actions: AccountActions) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().testTag(accountDeviceTag(device.id))) {
        Column(Modifier.weight(1f)) {
            Text(device.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                if (device.current) AccountTexts.THIS_PHONE.text() else AccountTexts.device(device.createdAt, device.lastUsedAt).text(),
                style = MaterialTheme.typography.bodySmall,
                color = HotThemeTokens.colors.textTertiary,
            )
        }
        if (!device.current) {
            HotTextButton(AccountTexts.END_SESSION.text(), onClick = { actions.confirm(AccountConfirm.EndDevice(device.id, device.name)) }, enabled = !busy)
        }
    }
}

/** «Удалить аккаунт»: what goes, then «Удалить навсегда» (A65 §6). */
@Composable
private fun DeleteStep(state: AccountUiState, actions: AccountActions) {
    HotCard(cornice = true, modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(AccountTexts.DELETE_TITLE.text(), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            AccountTexts.DELETE_WHAT.forEach { line ->
                Text("• " + line.text(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            Text(AccountTexts.DELETE_GOOGLE_NOTE.text(), style = MaterialTheme.typography.bodySmall, color = HotThemeTokens.colors.textTertiary)
            Text(AccountTexts.STEP_UP_NOTE.text(), style = MaterialTheme.typography.bodySmall, color = HotThemeTokens.colors.textTertiary)
            HotSecondaryButton(
                AccountTexts.DELETE_CONFIRM.text(),
                onClick = actions.delete,
                enabled = !state.busy,
                loading = state.busy,
                modifier = Modifier.fillMaxWidth().testTag(ACCOUNT_DELETE_CONFIRM_TAG),
            )
            HotTextButton(AccountTexts.CANCEL.text(), onClick = { actions.openDelete(false) }, enabled = !state.busy)
        }
    }
}

@Composable
private fun Dialogs(state: AccountUiState, actions: AccountActions) {
    state.inUse?.let { provider ->
        AlertDialog(
            onDismissRequest = actions.closeInUse,
            title = { Text(AccountTexts.inUse(provider).text()) },
            text = { Text(AccountTexts.IN_USE_BODY.text()) },
            confirmButton = { TextButton(onClick = actions.switchAccount) { Text(AccountTexts.IN_USE_SWITCH.text()) } },
            dismissButton = { TextButton(onClick = actions.closeInUse) { Text(AccountTexts.CANCEL.text()) } },
        )
    }
    val confirm = state.confirm ?: return
    val (title, body, yes) = when (confirm) {
        AccountConfirm.SignOut -> Triple(AccountTexts.SIGN_OUT_TITLE.text(), AccountTexts.SIGN_OUT_BODY.text(), AccountTexts.SIGN_OUT.text() to actions.signOut)
        AccountConfirm.SignOutAll -> Triple(AccountTexts.SIGN_OUT_ALL_TITLE.text(), AccountTexts.SIGN_OUT_ALL_BODY.text(), AccountTexts.SIGN_OUT_ALL.text() to actions.signOutAll)
        is AccountConfirm.Unlink -> Triple(
            (if (confirm.provider == AccountCredentials.PROVIDER_WHOOP) AccountTexts.WHOOP else AccountTexts.GOOGLE).text(),
            AccountTexts.STEP_UP_NOTE.text(),
            AccountTexts.UNLINK.text() to { actions.unlink(confirm.provider) },
        )
        is AccountConfirm.EndDevice -> Triple(confirm.name, AccountTexts.END_SESSION.text(), AccountTexts.END_SESSION.text() to { actions.endDevice(DeviceDto(confirm.id, confirm.name)) })
    }
    AlertDialog(
        onDismissRequest = actions.dismissConfirm,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = yes.second) { Text(yes.first) } },
        dismissButton = { TextButton(onClick = actions.dismissConfirm) { Text(AccountTexts.CANCEL.text()) } },
    )
}

@Composable
private fun Section(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.semantics { heading() },
    )
}
