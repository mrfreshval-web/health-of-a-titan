package com.healthofatitan.app.feature.me.profile

import com.healthofatitan.app.core.model.profile.AddressForm
import com.healthofatitan.app.core.model.profile.ProfileSettings
import com.healthofatitan.app.core.model.profile.SexForCalc

/**
 * Texts of «Профиль» from A61 §2.2 and §5.1 (D-055). Wording of the address question is Agent 10's
 * («Как к тебе обращаться», not «Твой пол»); the label of the sex for calculations is Agent 3's.
 * The notes under the fields are as Agents 3, 7 and 10 approved them (review of 2026-11-07).
 */
object ProfileTexts {
    const val TITLE = "Профиль"
    const val SECTION_YOU = "Ты"
    const val SECTION_CALC = "Для расчётов"

    const val ADDRESS_TITLE = "Как к тебе обращаться"
    const val ADDRESS_NOTE = "Меняет только окончания слов: «отметил», «отметила» или «отмечено». На расчёты не влияет."

    const val BIRTH_YEAR = "Год рождения"
    const val BIRTH_YEAR_NOTE = "Нужен для фитнес-возраста. Можно не указывать."
    fun birthYearWrong(currentYear: Int) =
        "Нужен год от ${currentYear - ProfileSettings.MAX_AGE} до ${currentYear - ProfileSettings.MIN_AGE}: HoT пока для взрослых."

    const val SEX_TITLE = "Пол для расчётов"
    const val SEX_NOTE = "Нужен только формуле фитнес-возраста: у неё разные версии для женщин и мужчин. Обращение не меняет. " +
        "Если ни один вариант не подходит — «не указывать», тогда фитнес-возраст просто не считается."

    const val LOCAL_ONLY = "Год рождения и пол для расчётов хранятся только на телефоне и в Claude не уходят."

    /** The caption of the card «Ты» in the settings. */
    const val ROW_CAPTION = "Обращение · год рождения · пол для расчётов"

    fun address(form: AddressForm): String = when (form) {
        AddressForm.MASCULINE -> "он"
        AddressForm.FEMININE -> "она"
        AddressForm.NEUTRAL -> "не важно"
    }

    /** The caption of the row «Ты» in the settings: «Обращение: он · она · нейтрально» (Agent 10). */
    fun addressShort(form: AddressForm): String = "Обращение: " + when (form) {
        AddressForm.MASCULINE -> "он"
        AddressForm.FEMININE -> "она"
        AddressForm.NEUTRAL -> "нейтрально"
    }

    fun sex(sex: SexForCalc): String = when (sex) {
        SexForCalc.FEMALE -> "женский"
        SexForCalc.MALE -> "мужской"
        SexForCalc.UNSPECIFIED -> "не указывать"
    }
}
