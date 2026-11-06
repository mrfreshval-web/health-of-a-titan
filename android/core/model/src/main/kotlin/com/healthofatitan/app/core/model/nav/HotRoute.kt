package com.healthofatitan.app.core.model.nav

/**
 * Navigation contract (A11 §1.1, A14 §7). Features never depend on each other: they navigate
 * by these routes only.
 */
object HotRoute {
    const val TODAY = "today"
    const val MENTOR = "mentor"
    const val WEEK = "week"
    const val ME = "me"

    /** «Я → Подключения → WHOOP» (A11 §11, A18 И1.1); lives under the «Я» tab. */
    const val WHOOP = "whoop"

    /** Outside the tabs: reachable in 1 tap from every screen, tile and shortcut. */
    const val SOS = "sos"

    const val DEEP_LINK_SCHEME = "hot"
    const val SOS_DEEP_LINK = "$DEEP_LINK_SCHEME://$SOS"
    const val TODAY_DEEP_LINK = "$DEEP_LINK_SCHEME://$TODAY"

    /** Bottom bar order: «Сегодня · Наставник · Неделя · Я». */
    val tabs: List<String> = listOf(TODAY, MENTOR, WEEK, ME)
}
