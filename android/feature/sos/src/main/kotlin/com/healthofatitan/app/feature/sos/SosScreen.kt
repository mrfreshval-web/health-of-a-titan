package com.healthofatitan.app.feature.sos

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.healthofatitan.app.core.model.nav.HotRoute
import com.healthofatitan.app.core.ui.R
import com.healthofatitan.app.core.ui.theme.HotTheme
import com.healthofatitan.app.core.ui.theme.HotType

const val SOS_SCREEN_TAG = "screen_sos"
const val SOS_CALL_988_TAG = "sos_call_988"
const val SOS_CALL_911_TAG = "sos_call_911"

/**
 * Emergency numbers for the founder in the US (A11 §8.1, A07 §4.5). Hardcoded on purpose:
 * this screen must open instantly and offline. Country selection comes from
 * content/safety/resources.yaml in slice 4.
 */
internal object SosNumbers {
    const val CRISIS_LINE = "988"
    const val EMERGENCY = "911"
}

/** SOS outside the tabs (A11 §1.1): from the top bar, the quick settings tile and the shortcut. */
fun NavGraphBuilder.sosScreen(onClose: () -> Unit) {
    composable(route = HotRoute.SOS) {
        SosScreen(onClose = onClose)
    }
}

/**
 * Slice 0 skeleton of the SOS screen (A11 §8.1): the emergency block is always visible, before
 * any triage. Buttons open the dialer; the user confirms the call. Triage and practices come
 * in slices 4–5c. Texts are taken from A11 and still need Agent 10 review.
 * Visual: A36 §10 screen 10 and §11 rule 7 — always dark, sober: Manrope only, no gold, no antiqua,
 * no glow, no marble texture, no animation; red only for the emergency block.
 */
@Composable
fun SosScreen(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    HotTheme(darkTheme = true, dynamicColor = false) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            modifier = modifier.fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag(SOS_SCREEN_TAG),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose, modifier = Modifier.size(56.dp)) {
                        Icon(painterResource(R.drawable.core_ui_ic_close), contentDescription = "Закрыть")
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "SOS",
                        style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 0.16.em),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .semantics { heading() },
                    )
                }
                EmergencyBlock(
                    onCrisisLine = { context.dial(SosNumbers.CRISIS_LINE) },
                    onEmergency = { context.dial(SosNumbers.EMERGENCY) },
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Я рядом. Что сейчас?",
                    style = HotType.sosHeadline,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    "Скоро здесь будут короткие практики: подышать, переждать волну, разобрать перегруз.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    "Я ИИ, не человек. Живые люди — 988.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun EmergencyBlock(onCrisisLine: () -> Unit, onEmergency: () -> Unit) {
    val onBlock = MaterialTheme.colorScheme.onErrorContainer
    val block = MaterialTheme.colorScheme.errorContainer
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = block, contentColor = onBlock),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                "Если опасность прямо сейчас",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onCrisisLine,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = onBlock, contentColor = block),
                    contentPadding = PaddingValues(horizontal = 18.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .testTag(SOS_CALL_988_TAG),
                ) {
                    Icon(painterResource(R.drawable.core_ui_ic_call), contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("988 — поговорить", style = MaterialTheme.typography.titleMedium)
                }
                OutlinedButton(
                    onClick = onEmergency,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, onBlock),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = onBlock),
                    contentPadding = PaddingValues(horizontal = 22.dp),
                    modifier = Modifier
                        .heightIn(min = 56.dp)
                        .testTag(SOS_CALL_911_TAG),
                ) {
                    Icon(painterResource(R.drawable.core_ui_ic_call), contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("911", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/** Opens the dialer with the number pre-filled; the user confirms the call (no CALL_PHONE). */
internal fun Context.dial(number: String) {
    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(intent)
    } catch (@Suppress("UNUSED_PARAMETER") e: ActivityNotFoundException) {
        // No dialer (e.g. tablet without telephony): the number stays visible on screen.
    }
}
