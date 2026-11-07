package com.healthofatitan.app.core.model.safety

/**
 * Emergency and crisis numbers of one country (A07 §4.5, UX review 2026-11-07 Y6).
 *
 * @param country ISO 3166-1 alpha-2 code, null when the country is unknown
 * @param emergency the number to dial when life is in danger right now
 * @param crisisLine the number of a crisis line («поговорить»), null when we have none checked
 * @param crisisShown how the crisis number is written on screen («988», «8-800-2000-122»)
 * @param crisisSms where to text the crisis line, if it takes texts
 * @param crisisChatUrl the crisis line's chat, if it has one
 */
data class HelpLines(
    val country: String?,
    val emergency: String,
    val crisisLine: String?,
    val crisisShown: String?,
    val crisisSms: String? = null,
    val crisisChatUrl: String? = null,
) {
    /** One line for the «Помощь» row: «988 · 911», «8-800-2000-122 · 112», «112». */
    val caption: String
        get() = listOfNotNull(crisisShown, emergency).joinToString(" · ")
}

/**
 * The built-in copy of `content/safety/resources.yaml`: the SOS screen must open instantly and
 * offline, so it never waits for the content assets. `HelpDirectoryTest` checks that this table
 * and the YAML say the same.
 *
 * The country is where the phone is: the mobile network first, then the SIM, then the phone's
 * region setting — never the language (A07 §4.5). A Russian speaker in the US gets 988 and 911;
 * a Russian SIM roaming in Germany gets 112, not a Russian 8-800 line that does not ring from abroad.
 *
 * Crisis content: changes only with review by Agents 10 and 7 (CLAUDE.md).
 */
object HelpDirectory {
    val US = HelpLines(
        country = "US",
        emergency = "911",
        crisisLine = "988",
        crisisShown = "988",
        crisisSms = "988",
        crisisChatUrl = "https://988lifeline.org/chat/",
    )

    val RU = HelpLines(
        country = "RU",
        emergency = "112",
        crisisLine = "88002000122",
        crisisShown = "8-800-2000-122",
    )

    /**
     * Unknown country: 112 is the emergency number of the EU and of GSM phones, most mobile
     * networks route it to the local service. No crisis line: the screen points to a directory.
     */
    val FALLBACK = HelpLines(country = null, emergency = "112", crisisLine = null, crisisShown = null)

    /** International directory of crisis lines by country (A07 §4.5, the source for checks). */
    const val DIRECTORY_URL = "https://findahelpline.com"

    val byCountry: Map<String, HelpLines> = listOf(US, RU).associateBy { it.country!! }

    fun forCountry(iso: String?): HelpLines = normalize(iso)?.let { byCountry[it] } ?: FALLBACK

    /**
     * The first country that is known at all wins, even if it is not in the table: a network in
     * Germany gives [FALLBACK] (112), not the SIM's home lines.
     *
     * @param candidates country codes in order of trust: network, SIM, phone region
     */
    fun resolve(vararg candidates: String?): HelpLines = forCountry(candidates.firstNotNullOfOrNull { normalize(it) })

    private fun normalize(iso: String?): String? = iso?.trim()?.uppercase()?.takeIf { it.length == 2 && it.all(Char::isLetter) }
}
