package com.healthofatitan.app.feature.me.account

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.healthofatitan.app.core.ui.component.HotBackground
import com.healthofatitan.app.core.ui.component.HotCard
import com.healthofatitan.app.core.ui.component.HotPrimaryButton
import com.healthofatitan.app.core.ui.component.HotSecondaryButton
import com.healthofatitan.app.core.ui.component.HotTextButton
import com.healthofatitan.app.integration.whoop.WhoopAuthFlow

const val SIGN_IN_TAG = "sign_in"
const val SIGN_IN_GOOGLE_TAG = "sign_in_google"
const val SIGN_IN_WHOOP_TAG = "sign_in_whoop"
const val SIGN_IN_INVITE_TAG = "sign_in_invite"
const val SIGN_IN_CREATE_TAG = "sign_in_create"
const val SIGN_IN_HELP_TAG = "sign_in_help"
const val SIGN_IN_NOTE_TAG = "sign_in_note"

/** Callbacks of «Вход»; no-ops in previews and tests. */
data class SignInActions(
    val google: () -> Unit = {},
    val whoop: () -> Unit = {},
    val submitInvite: (String) -> Unit = {},
    val back: () -> Unit = {},
    val help: () -> Unit = {},
)

/** Stateful entry: ViewModel, Credential Manager and the AppAuth browser step for WHOOP. */
@Composable
fun SignInRoute(onHelp: () -> Unit, modifier: Modifier = Modifier, viewModel: SignInViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val auth = remember { WhoopAuthFlow(context) }
    DisposableEffect(auth) { onDispose { auth.dispose() } }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        viewModel.onWhoopRedirect(WhoopAuthFlow.parse(result.data))
    }
    LaunchedEffect(viewModel) {
        viewModel.authRequests.collect { launcher.launch(auth.intent(it)) }
    }
    SignInScreen(
        state = state,
        actions = SignInActions(
            google = { viewModel.signInWithGoogle(context) },
            whoop = { viewModel.signInWithWhoop() },
            submitInvite = viewModel::submitInvite,
            back = viewModel::backToChoice,
            help = onHelp,
        ),
        modifier = modifier,
    )
}

/**
 * «Вход» over the app after the gate 18+ (A65 §2, D-062). Two equal ways in: Google and WHOOP.
 * No passwords, no e-mail codes. «Помощь» is one tap away here too: the crisis protocol is never
 * behind the sign-in (it opens SOS over this screen).
 */
@Composable
fun SignInScreen(state: SignInUiState, actions: SignInActions, modifier: Modifier = Modifier) {
    val busy = state.phase != SignInPhase.IDLE
    // The screen lies over the app: every touch stops here.
    HotBackground(modifier = modifier.fillMaxSize().testTag(SIGN_IN_TAG).pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            HotCard(cornice = true, modifier = Modifier.fillMaxWidth()) {
                if (state.needsInvite) {
                    InviteStep(state, actions, busy)
                } else {
                    ChooseStep(state, actions, busy)
                }
                state.note?.let { note ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        SignInTexts.note(note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag(SIGN_IN_NOTE_TAG).semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            HotTextButton(SignInTexts.HELP, onClick = actions.help, modifier = Modifier.testTag(SIGN_IN_HELP_TAG))
        }
    }
}

@Composable
private fun ChooseStep(state: SignInUiState, actions: SignInActions, busy: Boolean) {
    Text(SignInTexts.TITLE, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(8.dp))
    Text(SignInTexts.BODY, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(16.dp))
    HotPrimaryButton(
        SignInTexts.GOOGLE,
        onClick = actions.google,
        enabled = !busy && state.googleAvailable,
        loading = state.phase == SignInPhase.GOOGLE,
        modifier = Modifier.fillMaxWidth().testTag(SIGN_IN_GOOGLE_TAG),
    )
    Spacer(Modifier.height(8.dp))
    HotSecondaryButton(
        SignInTexts.WHOOP,
        onClick = actions.whoop,
        enabled = !busy,
        loading = state.phase == SignInPhase.WHOOP,
        modifier = Modifier.fillMaxWidth().testTag(SIGN_IN_WHOOP_TAG),
    )
    if (!state.googleAvailable) {
        Spacer(Modifier.height(6.dp))
        Text(SignInTexts.GOOGLE_OFF, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(10.dp))
    Text(SignInTexts.PRIVACY, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun InviteStep(state: SignInUiState, actions: SignInActions, busy: Boolean) {
    var code by rememberSaveable { mutableStateOf("") }
    Text(SignInTexts.INVITE_TITLE, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(8.dp))
    Text(SignInTexts.INVITE_BODY, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (state.signupTicket != null) {
        // Came with Google: maybe this person already has HoT through WHOOP (A65 §2.1 step 5).
        Spacer(Modifier.height(12.dp))
        HotSecondaryButton(
            SignInTexts.WHOOP,
            onClick = actions.whoop,
            enabled = !busy,
            loading = state.phase == SignInPhase.WHOOP,
            modifier = Modifier.fillMaxWidth().testTag(SIGN_IN_WHOOP_TAG),
        )
    }
    state.whoopUserId?.let { id ->
        Spacer(Modifier.height(8.dp))
        Text(SignInTexts.whoopId(id), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = code,
        onValueChange = { code = it.take(40) },
        label = { Text(SignInTexts.INVITE_LABEL) },
        singleLine = true,
        enabled = !busy,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth().testTag(SIGN_IN_INVITE_TAG),
    )
    Spacer(Modifier.height(8.dp))
    HotPrimaryButton(
        SignInTexts.CREATE,
        onClick = { actions.submitInvite(code) },
        enabled = !busy && code.isNotBlank(),
        loading = state.phase == SignInPhase.SIGNUP,
        modifier = Modifier.fillMaxWidth().testTag(SIGN_IN_CREATE_TAG),
    )
    Spacer(Modifier.height(4.dp))
    HotTextButton(SignInTexts.BACK, onClick = actions.back, enabled = !busy)
}
