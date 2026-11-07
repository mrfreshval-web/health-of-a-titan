package com.healthofatitan.app.core.model.profile

/**
 * How the app speaks to the person (A61 §4.1, D-055 п. 2): the grammatical gender of the words
 * about them («отмечено» · «отметил» · «отметила»). Not a question about sex: «Как к тебе
 * обращаться». [NEUTRAL] is the default and the value of «не важно» / not chosen.
 *
 * The three values are the ones of Android `GrammaticalInflectionManager` (API 34: NEUTRAL /
 * MASCULINE / FEMININE), so the texts can later move to `strings.xml` with `-neuter`,
 * `-masculine` and `-feminine` qualifiers.
 *
 * @property code stable value stored on the device and later sent to the mentor's prompt (`address_form`)
 */
enum class AddressForm(val code: String) {
    NEUTRAL("n"),
    MASCULINE("m"),
    FEMININE("f"),
    ;

    companion object {
        val DEFAULT = NEUTRAL

        /** Unknown or missing codes read as [NEUTRAL]: «не указано» never breaks a text. */
        fun fromCode(code: String?): AddressForm = entries.firstOrNull { it.code == code } ?: DEFAULT
    }
}

/**
 * «Пол для расчётов» (A61 §2.2, D-055 п. 3): a variable of the formulas (HUNT VO₂max, the
 * fitness age, reference ranges), not an identity and never the address of the texts. Health
 * data (S3): stays on the phone in the encrypted database, never goes to Claude.
 *
 * @property code stable value stored on the device (`male | female | unspecified`)
 */
enum class SexForCalc(val code: String) {
    UNSPECIFIED("unspecified"),
    FEMALE("female"),
    MALE("male"),
    ;

    companion object {
        fun fromCode(code: String?): SexForCalc = entries.firstOrNull { it.code == code } ?: UNSPECIFIED
    }
}

/**
 * The three facts of the profile «Я» the person types by hand (A61 §2.1); everything else is
 * derived from WHOOP and the phone (D-027). No account, no e-mail, no login (D-055 п. 1).
 * None of them is required for «Сегодня».
 *
 * @property birthYear null until the person gives it (asked before the first fitness age)
 */
data class ProfileSettings(
    val addressForm: AddressForm = AddressForm.DEFAULT,
    val birthYear: Int? = null,
    val sexForCalc: SexForCalc = SexForCalc.UNSPECIFIED,
) {
    /** Whole years reached in [year] (± 1 year: only the year of birth is known; HUNT gives ± 2 anyway). */
    fun ageIn(year: Int): Int? = birthYear?.let { year - it }?.takeIf { it in MIN_AGE..MAX_AGE }

    companion object {
        /** HoT is for adults only (A61 §1.3 `age18`). */
        const val MIN_AGE = 18
        const val MAX_AGE = 100

        /** A year of birth the profile accepts in [currentYear]: an adult up to [MAX_AGE] years. */
        fun validBirthYear(year: Int?, currentYear: Int): Boolean =
            year != null && year in (currentYear - MAX_AGE)..(currentYear - MIN_AGE)
    }
}
