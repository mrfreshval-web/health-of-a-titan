package com.healthofatitan.app.core.model.safety

/**
 * The help numbers of HoT: 112 and 911, the same everywhere and always in this order.
 *
 * Founder's decision of 2026-11-07 («телефон везде 112 и 911, все»), which replaces the
 * per-country lines of the content review Y6: no 988, no national trust lines, no detection of
 * the country. 112 reaches the emergency service from most mobile phones worldwide, 911 in North
 * America (and on many GSM networks it is redirected to the local service).
 *
 * The built-in copy of `content/safety/resources.yaml`, so SOS opens instantly and offline;
 * `HelpNumbersTest` fails if the two differ. Crisis content: Agents 10 and 7.
 */
object HelpNumbers {
    const val FIRST = "112"
    const val SECOND = "911"

    /** In the order every screen shows them. */
    val ALL: List<String> = listOf(FIRST, SECOND)

    /** «112 · 911» — the caption of the «Помощь» row. */
    val CAPTION: String = ALL.joinToString(" · ")

    /** «112 или 911» — inside a sentence. */
    val IN_TEXT: String = ALL.joinToString(" или ")
}
