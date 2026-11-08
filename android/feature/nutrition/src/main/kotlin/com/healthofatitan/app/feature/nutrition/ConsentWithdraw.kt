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

/**
 * The texts of the withdrawal of S4 (D-058). Neutral form, no words about a diagnosis.
 * **Черновик, ждёт ревью Агентов 7 и 10.** The text of [KEEP_BODY] is the text of the consent
 * `ed_care_keep` v1: a change of it is a new consent version.
 */
object ConsentWithdrawTexts {
    val REVOKE = G("Отозвать согласие на данные о здоровье")
    val CONFIRM_TITLE = G("Отозвать согласие?")
    val CONFIRM_BODY = G(
        "Особенности из профиля и вечерние отметки о еде удалятся с этого телефона. " +
            "Тарелка дня станет «Основой» без фильтров. Дать согласие снова можно в любой момент.",
    )
    val CONFIRM_YES = G("Отозвать")
    val CONFIRM_NO = G("Не сейчас")

    val KEEP_TITLE = G("Оставить бережный режим еды?")
    val KEEP_BODY = G(
        "Сейчас включён бережный режим еды: без калорий, веса и фото тела. " +
            "Можно оставить только его: на телефоне останется одна отметка «режим включён», остальное удалится. " +
            "Если не оставлять, удалится всё и режим выключится.",
    )
    val KEEP_YES = G("Да, оставить")
    val KEEP_NO = G("Нет, удалить всё")

    val ALL: List<G> = listOf(REVOKE, CONFIRM_TITLE, CONFIRM_BODY, CONFIRM_YES, CONFIRM_NO, KEEP_TITLE, KEEP_BODY, KEEP_YES, KEEP_NO)
}

/** Where the withdrawal stands: the confirmation, then (only while `ED_CARE` is on) the one question. */
enum class WithdrawStep { NONE, CONFIRM, ASK_KEEP }

/**
 * «Отозвать согласие на данные о здоровье» with a confirmation (D-058). While `ED_CARE` is on the
 * person is asked once «Оставить бережный режим еды?»: «Да» keeps one mark, «Нет» or a closed
 * question deletes everything. Without `ED_CARE` it is the usual withdrawal, no question.
 *
 * @param onWithdraw called once with `keepEdCare`
 */
@Composable
fun ConsentWithdraw(edCareActive: Boolean, onWithdraw: (keepEdCare: Boolean) -> Unit, modifier: Modifier = Modifier) {
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
            text = { Text(ConsentWithdrawTexts.CONFIRM_BODY.text()) },
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
