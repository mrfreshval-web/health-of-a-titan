package com.healthofatitan.app.feature.nutrition

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.DialogProperties
import com.healthofatitan.app.core.model.text.G
import com.healthofatitan.app.core.ui.component.HotTextButton
import com.healthofatitan.app.core.ui.text.text

const val WITHDRAW_BUTTON_TAG = "s4_withdraw"
const val WITHDRAW_CONFIRM_TAG = "s4_withdraw_confirm"
const val WITHDRAW_CANCEL_TAG = "s4_withdraw_cancel"
const val KEEP_ED_CARE_YES_TAG = "s4_keep_ed_care_yes"
const val KEEP_ED_CARE_NO_TAG = "s4_keep_ed_care_no"
const val ED_CARE_OFF_TAG = "ed_care_keep_off"

/**
 * The texts of the withdrawal of S4 (D-058). Neutral form, no words about a diagnosis.
 * Ревью Агентов 7 и 10 · 2026-11-08 (Ю-1, Ю-2, Ю-3, П-3, П-4). The text of [KEEP_BODY] is the
 * text of the consent `ed_care_keep` v1: a change of it is a new consent version.
 */
object ConsentWithdrawTexts {
    val REVOKE = G("Отозвать согласие на данные о здоровье")
    val CONFIRM_TITLE = G("Отозвать согласие?")
    val CONFIRM_BODY = G(
        "Аллергии, тип питания, состояния и вечерние отметки о еде удалятся с этого телефона. " +
            "Тарелка дня станет «Основой» и перестанет учитывать аллергии и ограничения. " +
            "Дать согласие снова можно в любой момент.",
    )

    /** Added to [CONFIRM_BODY] when the mode is off but its history keeps things hidden (Ю-2). */
    val CONFIRM_BODY_HISTORY = G("Отметка о бережном режиме тоже удалится: скрытые калории, вес и фото тела снова станут видны.")
    val CONFIRM_YES = G("Отозвать")
    val CONFIRM_NO = G("Не сейчас")

    val KEEP_TITLE = G("Оставить бережный режим еды?")
    val KEEP_BODY = G(
        "Сейчас включён бережный режим еды: калории, вес и фото тела скрыты. " +
            "Можно оставить только его — тогда на этом телефоне останется одна отметка «режим включён», а остальное удалится. " +
            "Выключить режим и удалить отметку можно в любой момент в «Я › Еда и тело». " +
            "Если не оставлять, удалится всё, режим выключится, а калории, вес и фото тела снова станут видны.",
    )
    val KEEP_YES = G("Да, оставить режим")
    val KEEP_NO = G("Нет, выключить режим")

    /** «Я › Еда и тело» without S4: the withdrawal of `ed_care_keep` is as easy as its grant (Ю-3). */
    val ED_CARE_OFF = G("Выключить бережный режим еды")

    val ALL: List<G> = listOf(
        REVOKE, CONFIRM_TITLE, CONFIRM_BODY, CONFIRM_BODY_HISTORY, CONFIRM_YES, CONFIRM_NO,
        KEEP_TITLE, KEEP_BODY, KEEP_YES, KEEP_NO, ED_CARE_OFF,
    )
}

/** Where the withdrawal stands: the confirmation, then (only while `ED_CARE` is on) the one question. */
enum class WithdrawStep { NONE, CONFIRM, ASK_KEEP }

/**
 * «Отозвать согласие на данные о здоровье» with a confirmation (D-058). While `ED_CARE` is on the
 * person is asked once «Оставить бережный режим еды?»: «Да» keeps one mark, «Нет» or a closed
 * question deletes everything. Without `ED_CARE` it is the usual withdrawal, no question.
 *
 * @param edCareHistory `ED_CARE` was on and its history still keeps calories, weight and body
 *   photo hidden: the confirmation says that this goes too (Ю-2)
 * @param onWithdraw called once with `keepEdCare`
 */
@Composable
fun ConsentWithdraw(
    edCareActive: Boolean,
    onWithdraw: (keepEdCare: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    edCareHistory: Boolean = false,
) {
    var step by rememberSaveable { mutableStateOf(WithdrawStep.NONE) }
    HotTextButton(ConsentWithdrawTexts.REVOKE.text(), onClick = { step = WithdrawStep.CONFIRM }, modifier = modifier.testTag(WITHDRAW_BUTTON_TAG))
    val finish = { keep: Boolean ->
        step = WithdrawStep.NONE
        onWithdraw(keep)
    }
    when (step) {
        WithdrawStep.NONE -> Unit
        WithdrawStep.CONFIRM -> AlertDialog(
            onDismissRequest = { step = WithdrawStep.NONE },
            title = { Text(ConsentWithdrawTexts.CONFIRM_TITLE.text()) },
            text = {
                val body = ConsentWithdrawTexts.CONFIRM_BODY.text()
                Text(if (!edCareActive && edCareHistory) body + " " + ConsentWithdrawTexts.CONFIRM_BODY_HISTORY.text() else body)
            },
            confirmButton = {
                TextButton(
                    onClick = { if (edCareActive) step = WithdrawStep.ASK_KEEP else finish(false) },
                    modifier = Modifier.testTag(WITHDRAW_CONFIRM_TAG),
                ) { Text(ConsentWithdrawTexts.CONFIRM_YES.text()) }
            },
            dismissButton = {
                TextButton(onClick = { step = WithdrawStep.NONE }, modifier = Modifier.testTag(WITHDRAW_CANCEL_TAG)) {
                    Text(ConsentWithdrawTexts.CONFIRM_NO.text())
                }
            },
        )
        // A closed question is «Нет» (D-058); a tap outside does not close it by accident.
        WithdrawStep.ASK_KEEP -> AlertDialog(
            onDismissRequest = { finish(false) },
            properties = DialogProperties(dismissOnClickOutside = false),
            title = { Text(ConsentWithdrawTexts.KEEP_TITLE.text()) },
            text = { Text(ConsentWithdrawTexts.KEEP_BODY.text()) },
            confirmButton = {
                TextButton(onClick = { finish(true) }, modifier = Modifier.testTag(KEEP_ED_CARE_YES_TAG)) {
                    Text(ConsentWithdrawTexts.KEEP_YES.text())
                }
            },
            dismissButton = {
                TextButton(onClick = { finish(false) }, modifier = Modifier.testTag(KEEP_ED_CARE_NO_TAG)) {
                    Text(ConsentWithdrawTexts.KEEP_NO.text())
                }
            },
        )
    }
}

/**
 * «Выключить бережный режим еды» on «Я › Еда и тело» without S4 while the kept mark holds
 * `ED_CARE` on (Ю-3): the path «Нет» — the mark is deleted and `ed_care_keep` is withdrawn.
 */
@Composable
fun EdCareOff(onOff: () -> Unit, modifier: Modifier = Modifier) {
    HotTextButton(ConsentWithdrawTexts.ED_CARE_OFF.text(), onClick = onOff, modifier = modifier.testTag(ED_CARE_OFF_TAG))
}
