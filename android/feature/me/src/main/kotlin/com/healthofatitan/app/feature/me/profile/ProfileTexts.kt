package com.healthofatitan.app.feature.me.profile

import com.healthofatitan.app.core.model.profile.AddressForm
import com.healthofatitan.app.core.model.profile.ProfileSettings
import com.healthofatitan.app.core.model.profile.SexForCalc

/**
 * Texts of «Профиль» from A61 §2.2 and §5.1 (D-055). Wording of the address question is Agent 10's
 * («Как к тебе обращаться», not «Твой пол»); the label of the sex for calculations is Agent 3's.
 * The notes under the fields are drafts for Agents 16 and 10.
 */
object ProfileTexts {
    const val TITLE = "Профиль"
    const val SECTION_YOU = "Ты"
    const val SECTION_CALC = "Для расчётов"

    const val ADDRESS_TITLE = "Как к тебе обращаться"
    const val ADDRESS_NOTE = "Меняет только окончания слов в текстах. Можно не выбирать."

    const val BIRTH_YEAR = "Год рождения"
    const val BIRTH_YEAR_NOTE = "Нужен для фитнес-возраста. Можно не указывать."
    val BIRTH_YEAR_WRONG = "Год между ${ProfileSettings.MAX_AGE} и ${ProfileSettings.MIN_AGE} годами назад"

    const val SEX_TITLE = "Пол для расчётов (формулы пульса и фитнес-возраста)"
    const val SEX_NOTE = "Нужен формулам пульса и фитнес-возраста, тексты не меняет."

    const val LOCAL_ONLY = "Хранится только на телефоне"

    /** The caption of the card «Ты» in the settings. */
    const val ROW_CAPTION = "Обращение · год рождения · пол для расчётов"

    fun address(form: AddressForm): String = when (form) {
        AddressForm.MASCULINE -> "он"
        AddressForm.FEMININE -> "она"
        AddressForm.NEUTRAL -> "не важно"
    }

    fun sex(sex: SexForCalc): String = when (sex) {
        SexForCalc.FEMALE -> "женский"
        SexForCalc.MALE -> "мужской"
        SexForCalc.UNSPECIFIED -> "не указывать"
    }
}
