package com.healthofatitan.app.feature.me.consent

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
import androidx.compose.ui.unit.dp
import com.healthofatitan.app.core.database.AgeGate
import com.healthofatitan.app.core.model.text.G
import com.healthofatitan.app.core.ui.component.HotBackground
import com.healthofatitan.app.core.ui.component.HotCard
import com.healthofatitan.app.core.ui.component.HotPrimaryButton
import com.healthofatitan.app.core.ui.component.HotSecondaryButton
import com.healthofatitan.app.core.ui.component.HotTextButton
import com.healthofatitan.app.core.ui.text.text
import kotlinx.coroutines.delay

const val AGE_GATE_TAG = "age_gate"
const val AGE_GATE_CHECK_TAG = "age_gate_check"
const val AGE_GATE_CONTINUE_TAG = "age_gate_continue"
const val AGE_GATE_UNDER_TAG = "age_gate_under"
const val AGE_GATE_ADULTS_ONLY_TAG = "age_gate_adults_only"
const val AGE_GATE_HELP_TAG = "age_gate_help"
const val AGE_GATE_BACK_TAG = "age_gate_back"
const val AGE_GATE_SAVE_FAILED_TAG = "age_gate_save_failed"

/** How long the gate waits for the consent journal before it asks anyway. */
const val LOADING_TIMEOUT_MS = 2_000L

/**
 * The texts of the gate 18+ (R-13, A61 §1.2 «Ч0 · 1а»). Neutral form: the gate stands before the
 * address form is chosen (D-055 п. 4). Ревью Агентов 7 и 10 · 2026-11-08 (П-4, П-5). These are
 * the texts of `age18` v1: a change of the text is a new consent version.
 */
object AgeGateTexts {
    val TITLE = G("HoT — для взрослых")
    val BODY = G("HoT говорит о сне, нагрузке, еде и состоянии. Приложение рассчитано на людей от 18 лет.")
    val CHECK = G("Мне есть 18 лет")
    val CONTINUE = G("Продолжить")
    val UNDER = G("Мне нет 18")
    val NOTE = G("Отметка хранится только на этом телефоне.")
    val SAVE_FAILED = G("Не получилось сохранить, попробуй ещё раз.")

    val ADULTS_ONLY_TITLE = G("HoT пока только для взрослых")
    val ADULTS_ONLY_BODY = G(
        "Спасибо за честный ответ. HoT рассчитан на людей от 18 лет, поэтому дальше не откроется. " +
            "Если сейчас опасно тебе или кому-то рядом, нажми «Помощь»: там номера 112 и 911. " +
            "Если просто тяжело, расскажи взрослому, которому доверяешь.",
    )
    val HELP = G("Помощь")
    val BACK = G("Назад")

    val ALL: List<G> = listOf(TITLE, BODY, CHECK, CONTINUE, UNDER, NOTE, SAVE_FAILED, ADULTS_ONLY_TITLE, ADULTS_ONLY_BODY, HELP, BACK)
}

/**
 * The gate 18+ over the whole app (R-13): nothing of HoT goes on until «Мне есть 18 лет» is
 * confirmed. The crisis protocol is not behind the gate: «Помощь» is one tap away on both
 * screens and opens SOS (112 · 911) over it. [AgeGate.LOADING] keeps «Помощь» on screen and turns
 * into the question after [LOADING_TIMEOUT_MS] if the consent journal does not answer.
 *
 * @param saveFailed the confirmation could not be stored: a short line asks to try again.
 * @param under «Мне нет 18» was tapped: the screen «HoT пока только для взрослых». The caller keeps
 *   it, so the person comes back to it from SOS; it is never stored.
 */
@Composable
fun AgeGateScreen(
    gate: AgeGate,
    under: Boolean,
    onUnderChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onHelp: () -> Unit,
    modifier: Modifier = Modifier,
    saveFailed: Boolean = false,
) {
    var checked by rememberSaveable { mutableStateOf(false) }
    var timedOut by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(gate) {
        if (gate == AgeGate.LOADING) {
            delay(LOADING_TIMEOUT_MS)
            timedOut = true
        }
    }
    val ask = gate == AgeGate.ASK || (gate == AgeGate.LOADING && timedOut)
    // The gate lies over the app: every touch stops here.
    HotBackground(modifier = modifier.fillMaxSize().testTag(AGE_GATE_TAG).pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }) {
        if (ask) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                if (under) {
                    AdultsOnly(onHelp = onHelp, onBack = { onUnderChange(false) })
                } else {
                    Ask(checked, { checked = it }, onConfirm, { onUnderChange(true) }, onHelp, saveFailed)
                }
            }
        } else {
            // Loading: nothing of the app yet, but help is already here.
            Box(Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp), contentAlignment = Alignment.BottomCenter) {
                HotTextButton(AgeGateTexts.HELP.text(), onClick = onHelp, modifier = Modifier.testTag(AGE_GATE_HELP_TAG))
            }
        }
    }
}

@Composable
private fun Ask(checked: Boolean, onChecked: (Boolean) -> Unit, onConfirm: () -> Unit, onUnder: () -> Unit, onHelp: () -> Unit, saveFailed: Boolean) {
    HotCard(cornice = true, modifier = Modifier.fillMaxWidth()) {
        Text(AgeGateTexts.TITLE.text(), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(8.dp))
        Text(AgeGateTexts.BODY.text(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChecked)
                .testTag(AGE_GATE_CHECK_TAG),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = checked, onCheckedChange = null)
            Text(AgeGateTexts.CHECK.text(), style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(12.dp))
        HotPrimaryButton(
            AgeGateTexts.CONTINUE.text(),
            onClick = onConfirm,
            enabled = checked,
            modifier = Modifier.fillMaxWidth().testTag(AGE_GATE_CONTINUE_TAG),
        )
        if (saveFailed) {
            Spacer(Modifier.height(6.dp))
            Text(
                AgeGateTexts.SAVE_FAILED.text(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(AGE_GATE_SAVE_FAILED_TAG).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Spacer(Modifier.height(4.dp))
        HotTextButton(AgeGateTexts.UNDER.text(), onClick = onUnder, modifier = Modifier.testTag(AGE_GATE_UNDER_TAG))
        Text(AgeGateTexts.NOTE.text(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(16.dp))
    HotTextButton(AgeGateTexts.HELP.text(), onClick = onHelp, modifier = Modifier.testTag(AGE_GATE_HELP_TAG))
}

@Composable
private fun AdultsOnly(onHelp: () -> Unit, onBack: () -> Unit) {
    HotCard(cornice = true, modifier = Modifier.fillMaxWidth().testTag(AGE_GATE_ADULTS_ONLY_TAG)) {
        Text(AgeGateTexts.ADULTS_ONLY_TITLE.text(), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(8.dp))
        Text(AgeGateTexts.ADULTS_ONLY_BODY.text(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        HotPrimaryButton(AgeGateTexts.HELP.text(), onClick = onHelp, modifier = Modifier.fillMaxWidth().testTag(AGE_GATE_HELP_TAG))
        Spacer(Modifier.height(4.dp))
        HotSecondaryButton(AgeGateTexts.BACK.text(), onClick = onBack, modifier = Modifier.fillMaxWidth().testTag(AGE_GATE_BACK_TAG))
    }
}
