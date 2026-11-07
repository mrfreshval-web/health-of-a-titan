package com.healthofatitan.app.core.model.text

import com.healthofatitan.app.core.model.profile.AddressForm

/**
 * One line of text in three grammatical genders (A61 §4.1, D-055 п. 4). The neutral form [n] is
 * the base and is required; [m] and [f] are refinements and fall back to [n]. «Не указано» always
 * gives [n], so a text without a chosen address is never masculine by default.
 *
 * Rule for new texts (Agents 16 and 10): write [n] in the present tense, impersonal or nominal
 * («Отмечено», «Есть усталость»); add [m] / [f] only where the neutral form sounds official.
 * The mentor Винс keeps his own masculine voice (D-017): «Понял» is not a [G].
 */
data class G(val n: String, val m: String = n, val f: String = n) {
    fun of(address: AddressForm): String = when (address) {
        AddressForm.NEUTRAL -> n
        AddressForm.MASCULINE -> m
        AddressForm.FEMININE -> f
    }
}
