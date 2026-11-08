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
const val ED_CARE_OFF_CONFIRM_TAG = "ed_care_keep_off_confirm"
const val ED_CARE_OFF_CANCEL_TAG = "ed_care_keep_off_cancel"

/**
 * The texts of the withdrawal of S4 (D-058). Neutral form, no words about a diagnosis.
 * Ревью Агентов 7 и 10 · 2026-11-08 (Ю-1, Ю-2, Ю-3, П-3, П-4; дорецензия Д-1…Д-4). The texts of
 * [KEEP_BODY] and [KEEP_BODY_HISTORY] are the text of the consent `ed_care_keep` v1: a change of
 * them is a new consent version. The `*_NO_WEIGHT` lines stand when the person already took the
 * weight back on step 3 (Д-1): «калории и фото тела» instead of «калории, вес и фото тела».
 */
object ConsentWithdrawTexts {
    val REVOKE = G("Отозвать согласие на данные о здоровье")
    val CONFIRM_TITLE = G("Отозвать согласие?")
    val CONFIRM_BODY = G(
        "Аллергии, тип питания, состояния и вечерние отметки о еде удалятся с этого телефона. " +
            "Тарелка дня станет «Основой» и перестанет учитывать аллергии и ограничения. " +
            "Дать согласие снова можно в любой момент.",
    )

    /**
     * Added to [CONFIRM_BODY] when the mode is off but its history keeps things hidden (Ю-2). Not
     * shown while П-2 stands (the question is asked then); it comes back if П-2 is rolled back.
     */
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

    // П-2 (поправка основателя 2026-11-08, принята): the mode is off, its history keeps
    // calories, weight and body photo hidden.
    val KEEP_TITLE_HISTORY = G("Оставить скрытыми калории, вес и фото тела?")
    val KEEP_TITLE_HISTORY_NO_WEIGHT = G("Оставить скрытыми калории и фото тела?")
    val KEEP_BODY_HISTORY = G(
        "Бережный режим еды выключен, а калории, вес и фото тела скрыты. " +
            "Можно оставить их скрытыми — тогда на этом телефоне останется одна отметка о том, что они скрыты, " +
            "а остальное удалится; режим при этом не включится. " +
            "Удалить отметку можно в любой момент в «Я › Еда и тело». " +
            "Если не оставлять, удалится всё, а калории, вес и фото тела снова станут видны.",
    )
    val KEEP_BODY_HISTORY_NO_WEIGHT = G(
        "Бережный режим еды выключен, а калории и фото тела скрыты. " +
            "Можно оставить их скрытыми — тогда на этом телефоне останется одна отметка о том, что они скрыты, " +
            "а остальное удалится; режим при этом не включится. " +
            "Удалить отметку можно в любой момент в «Я › Еда и тело». " +
            "Если не оставлять, удалится всё, а калории и фото тела снова станут видны.",
    )
    val KEEP_YES_HISTORY = G("Да, оставить скрытыми")
    val KEEP_NO_HISTORY = G("Нет, показать снова")
    val HIDDEN_OFF = G("Показать снова калории, вес и фото тела")
    val HIDDEN_OFF_NO_WEIGHT = G("Показать снова калории и фото тела")

    // Д-3: the confirmation before the kept mark is deleted.
    val OFF_TITLE = G("Выключить бережный режим еды?")
    val OFF_BODY = G("Отметка удалится с этого телефона, а калории, вес и фото тела снова станут видны.")
    val OFF_YES = G("Выключить")
    val HIDDEN_OFF_TITLE = G("Показать снова калории, вес и фото тела?")
    val HIDDEN_OFF_TITLE_NO_WEIGHT = G("Показать снова калории и фото тела?")
    val HIDDEN_OFF_BODY = G("Отметка удалится с этого телефона, и они снова станут видны.")
    val HIDDEN_OFF_YES = G("Показать")

    val ALL: List<G> = listOf(
        REVOKE, CONFIRM_TITLE, CONFIRM_BODY, CONFIRM_BODY_HISTORY, CONFIRM_YES, CONFIRM_NO,
        KEEP_TITLE, KEEP_BODY, KEEP_YES, KEEP_NO, ED_CARE_OFF,
        KEEP_TITLE_HISTORY, KEEP_TITLE_HISTORY_NO_WEIGHT, KEEP_BODY_HISTORY, KEEP_BODY_HISTORY_NO_WEIGHT,
        KEEP_YES_HISTORY, KEEP_NO_HISTORY, HIDDEN_OFF, HIDDEN_OFF_NO_WEIGHT,
        OFF_TITLE, OFF_BODY, OFF_YES, HIDDEN_OFF_TITLE, HIDDEN_OFF_TITLE_NO_WEIGHT, HIDDEN_OFF_BODY, HIDDEN_OFF_YES,
    )
}

/** Where the withdrawal stands: the confirmation, then (only with `ED_CARE` or its history) the one question. */
enum class WithdrawStep { NONE, CONFIRM, ASK_KEEP }

/** What the person did on the dialogs of the withdrawal. */
enum class WithdrawEvent { OPEN, CONFIRM, CANCEL, BACK, KEEP_YES, KEEP_NO }

/**
 * A move of the withdrawal: the next step and, when it ends, the answer.
 *
 * @property withdraw null — nothing is withdrawn yet; true / false — withdraw with / without the kept mark
 */
data class WithdrawMove(val step: WithdrawStep, val withdraw: Boolean? = null)

/**
 * The flow of the withdrawal (D-058 with П-1 and П-2). «Назад» on the question goes back to the
 * confirmation and deletes nothing (П-1): the mode ends only by an explicit «Нет» (D-057 п. 2).
 *
 * @param ask `ED_CARE` is on or its history still hides things (П-2)
 */
fun withdrawNext(step: WithdrawStep, event: WithdrawEvent, ask: Boolean): WithdrawMove = when (event) {
    WithdrawEvent.OPEN -> WithdrawMove(WithdrawStep.CONFIRM)
    WithdrawEvent.CANCEL -> WithdrawMove(WithdrawStep.NONE)
    WithdrawEvent.CONFIRM -> if (ask) WithdrawMove(WithdrawStep.ASK_KEEP) else WithdrawMove(WithdrawStep.NONE, withdraw = false)
    WithdrawEvent.BACK -> if (step == WithdrawStep.ASK_KEEP) WithdrawMove(WithdrawStep.CONFIRM) else WithdrawMove(WithdrawStep.NONE)
    WithdrawEvent.KEEP_YES -> WithdrawMove(WithdrawStep.NONE, withdraw = true)
    WithdrawEvent.KEEP_NO -> WithdrawMove(WithdrawStep.NONE, withdraw = false)
}

/**
 * «Отозвать согласие на данные о здоровье» with a confirmation (D-058). While `ED_CARE` is on the
 * person is asked once «Оставить бережный режим еды?»; when it is off but its history still hides
 * calories, weight and body photo — «Оставить скрытыми калории, вес и фото тела?» (П-2). «Да» keeps
 * one mark, «Нет» deletes everything, «Назад» returns to the confirmation (П-1). Without `ED_CARE`
 * and its history it is the usual withdrawal, no question.
 *
 * @param edCareHistory `ED_CARE` was on and its history is in the journal
 * @param weightBack the person already took the weight back on step 3: the texts name only
 *   calories and body photo (Д-1)
 * @param onWithdraw called once with `keepEdCare`
 */
@Composable
fun ConsentWithdraw(
    edCareActive: Boolean,
    onWithdraw: (keepEdCare: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    edCareHistory: Boolean = false,
    weightBack: Boolean = false,
) {
    var step by rememberSaveable { mutableStateOf(WithdrawStep.NONE) }
    val ask = edCareActive || edCareHistory
    val on = { event: WithdrawEvent ->
        val move = withdrawNext(step, event, ask)
        step = move.step
        move.withdraw?.let(onWithdraw)
    }
    HotTextButton(ConsentWithdrawTexts.REVOKE.text(), onClick = { on(WithdrawEvent.OPEN) }, modifier = modifier.testTag(WITHDRAW_BUTTON_TAG))
    val t = ConsentWithdrawTexts
    when (step) {
        WithdrawStep.NONE -> Unit
        WithdrawStep.CONFIRM -> AlertDialog(
            onDismissRequest = { on(WithdrawEvent.BACK) },
            title = { Text(t.CONFIRM_TITLE.text()) },
            text = { Text(t.CONFIRM_BODY.text()) },
            confirmButton = {
                TextButton(onClick = { on(WithdrawEvent.CONFIRM) }, modifier = Modifier.testTag(WITHDRAW_CONFIRM_TAG)) {
                    Text(t.CONFIRM_YES.text())
                }
            },
            dismissButton = {
                TextButton(onClick = { on(WithdrawEvent.CANCEL) }, modifier = Modifier.testTag(WITHDRAW_CANCEL_TAG)) {
                    Text(t.CONFIRM_NO.text())
                }
            },
        )
        // «Назад» goes back to the confirmation (П-1); a tap outside does not close it by accident.
        WithdrawStep.ASK_KEEP -> AlertDialog(
            onDismissRequest = { on(WithdrawEvent.BACK) },
            properties = DialogProperties(dismissOnClickOutside = false),
            title = {
                val title = when {
                    edCareActive -> t.KEEP_TITLE
                    weightBack -> t.KEEP_TITLE_HISTORY_NO_WEIGHT
                    else -> t.KEEP_TITLE_HISTORY
                }
                Text(title.text())
            },
            text = {
                val body = when {
                    edCareActive -> t.KEEP_BODY
                    weightBack -> t.KEEP_BODY_HISTORY_NO_WEIGHT
                    else -> t.KEEP_BODY_HISTORY
                }
                Text(body.text())
            },
            confirmButton = {
                TextButton(onClick = { on(WithdrawEvent.KEEP_YES) }, modifier = Modifier.testTag(KEEP_ED_CARE_YES_TAG)) {
                    Text((if (edCareActive) t.KEEP_YES else t.KEEP_YES_HISTORY).text())
                }
            },
            dismissButton = {
                TextButton(onClick = { on(WithdrawEvent.KEEP_NO) }, modifier = Modifier.testTag(KEEP_ED_CARE_NO_TAG)) {
                    Text((if (edCareActive) t.KEEP_NO else t.KEEP_NO_HISTORY).text())
                }
            },
        )
    }
}

/**
 * «Выключить бережный режим еды» on «Я › Еда и тело» without S4 while the kept mark holds
 * `ED_CARE` on (Ю-3), or «Показать снова калории, вес и фото тела» when the kept mark only hides
 * them (П-2): the path «Нет» — the mark is deleted and `ed_care_keep` is withdrawn. A confirmation
 * comes first (Д-3): «Назад», a tap outside and «Не сейчас» delete nothing.
 *
 * @param weightBack the weight is already back: the texts name only calories and body photo (Д-1)
 */
@Composable
fun EdCareOff(onOff: () -> Unit, modifier: Modifier = Modifier, active: Boolean = true, weightBack: Boolean = false) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    val t = ConsentWithdrawTexts
    val label = when {
        active -> t.ED_CARE_OFF
        weightBack -> t.HIDDEN_OFF_NO_WEIGHT
        else -> t.HIDDEN_OFF
    }
    HotTextButton(label.text(), onClick = { confirm = true }, modifier = modifier.testTag(ED_CARE_OFF_TAG))
    if (!confirm) return
    val title = when {
        active -> t.OFF_TITLE
        weightBack -> t.HIDDEN_OFF_TITLE_NO_WEIGHT
        else -> t.HIDDEN_OFF_TITLE
    }
    AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text(title.text()) },
        text = { Text((if (active) t.OFF_BODY else t.HIDDEN_OFF_BODY).text()) },
        confirmButton = {
            TextButton(
                onClick = {
                    confirm = false
                    onOff()
                },
                modifier = Modifier.testTag(ED_CARE_OFF_CONFIRM_TAG),
            ) { Text((if (active) t.OFF_YES else t.HIDDEN_OFF_YES).text()) }
        },
        dismissButton = {
            TextButton(onClick = { confirm = false }, modifier = Modifier.testTag(ED_CARE_OFF_CANCEL_TAG)) {
                Text(t.CONFIRM_NO.text())
            }
        },
    )
}
