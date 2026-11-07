package com.healthofatitan.app.feature.checkin

import android.content.Context
import android.content.ContextWrapper
import android.app.Activity
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.healthofatitan.app.core.ui.component.HotCard
import com.healthofatitan.app.core.ui.component.HotOverline
import com.healthofatitan.app.core.ui.component.HotPrimaryButton
import com.healthofatitan.app.core.ui.component.HotSecondaryButton
import com.healthofatitan.app.core.ui.component.HotTextButton
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.domain.checkin.Intimacy
import com.healthofatitan.app.domain.checkin.PrivateAnswer
import com.healthofatitan.app.domain.checkin.PrivateLayer
import com.healthofatitan.app.domain.checkin.Pull

const val ONLY_YOU_TAG = "checkin_only_you"
const val ONLY_YOU_UNLOCK_TAG = "checkin_only_you_unlock"
const val PRIVATE_OFFER_TAG = "checkin_private_offer"
const val PRIVATE_OFFER_YES_TAG = "checkin_private_offer_yes"

/**
 * S5 gate (A44 §6.4, A11 §11): the system prompt with the fingerprint, falling back to the screen
 * lock. Without any lock set up on the phone there is nothing to ask, so the layer opens (the
 * screen stays FLAG_SECURE either way).
 */
fun Context.unlockOnlyYou(onResult: (Boolean) -> Unit) {
    val auth = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    val manager = getSystemService(BiometricManager::class.java)
    if (manager == null || manager.canAuthenticate(auth) != BiometricManager.BIOMETRIC_SUCCESS || findActivity() == null) {
        onResult(true)
        return
    }
    val prompt = BiometricPrompt.Builder(this)
        .setTitle(CheckinTexts.UNLOCK_TITLE)
        .setSubtitle(CheckinTexts.UNLOCK_SUBTITLE)
        .setAllowedAuthenticators(auth)
        .build()
    prompt.authenticate(
        CancellationSignal(),
        mainExecutor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) = onResult(true)
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) = onResult(false)
        },
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** The lock in front of the card: nothing of S5 is drawn before the unlock. */
@Composable
fun OnlyYouLock(onUnlocked: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        LockGlyph(MaterialTheme.colorScheme.onSurfaceVariant, size = 36.dp)
        Spacer(Modifier.height(10.dp))
        Text(CheckinTexts.ONLY_YOU_LINE, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(14.dp))
        HotSecondaryButton(
            CheckinTexts.UNLOCK,
            onClick = { context.unlockOnlyYou { ok -> if (ok) onUnlocked() } },
            modifier = Modifier.fillMaxWidth().testTag(ONLY_YOU_UNLOCK_TAG),
        )
    }
}

/**
 * П7 «Только ты» (§6.4, w5): closeness and «Что тянуло?» with the optional «хотелось меньше».
 * The answer gets the neutral «Принято», no line of the character.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnlyYouContent(layer: PrivateLayer, haptics: Boolean, onDone: (PrivateAnswer) -> Unit, modifier: Modifier = Modifier) {
    var intimacy by remember { mutableStateOf<Intimacy?>(null) }
    var pulls by remember { mutableStateOf(emptySet<Pull>()) }
    var less by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().testTag(ONLY_YOU_TAG), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HotOverline(CheckinTexts.ONLY_YOU_LINE)
        if (layer.intimacy) {
            Text(CheckinTexts.CLOSENESS, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Intimacy.entries.forEach { v ->
                    AnswerTile(
                        CheckinTexts.intimacy(v),
                        intimacy == v,
                        onClick = { intimacy = if (intimacy == v) null else v },
                        spoken = CheckinTexts.intimacySpoken(v),
                        haptics = haptics,
                        minHeight = 76,
                        modifier = Modifier.weight(1f),
                    ) { c -> IntimacyGlyph(v, c, size = 24.dp) }
                }
            }
        }
        if (layer.pulls) {
            Text(CheckinTexts.PULLS, style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Pull.entries.forEach { v ->
                    CheckinChip(
                        CheckinTexts.pull(v),
                        v in pulls,
                        onClick = {
                            pulls = when {
                                v == Pull.NOTHING -> if (v in pulls) emptySet() else setOf(Pull.NOTHING)
                                v in pulls -> pulls - v
                                else -> pulls - Pull.NOTHING + v
                            }
                        },
                        leading = { c -> PullGlyph(v, c, size = 18.dp) },
                    )
                }
            }
            if (pulls.any { it != Pull.NOTHING }) {
                CheckinChip(CheckinTexts.WANT_LESS, less, onClick = { less = !less })
                Text(CheckinTexts.WANT_LESS_HINT, style = MaterialTheme.typography.bodySmall, color = HotThemeTokens.colors.textTertiary)
            }
        }
        HotPrimaryButton(CheckinTexts.DONE, onClick = { onDone(PrivateAnswer(intimacy, pulls, less && pulls.any { it != Pull.NOTHING })) }, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * D-036 п. 4: the founder turns «Только ты» on at the first run with one explicit tap; the
 * layer is never on by itself. «Не сейчас» is remembered and the card does not come back.
 */
@Composable
fun PrivateOfferCard(onYes: () -> Unit, onNo: () -> Unit, modifier: Modifier = Modifier) {
    HotCard(modifier = modifier.fillMaxWidth().testTag(PRIVATE_OFFER_TAG)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LockGlyph(MaterialTheme.colorScheme.primary, size = 24.dp)
            Spacer(Modifier.width(10.dp))
            Text(CheckinTexts.OFFER_TITLE, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(8.dp))
        Text(CheckinTexts.OFFER_LINE, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            HotPrimaryButton(CheckinTexts.OFFER_YES, onClick = onYes, modifier = Modifier.weight(1f).testTag(PRIVATE_OFFER_YES_TAG))
            HotTextButton(CheckinTexts.OFFER_NO, onClick = onNo)
        }
    }
}
