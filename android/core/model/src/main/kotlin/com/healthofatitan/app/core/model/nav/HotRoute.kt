package com.healthofatitan.app.core.model.nav

/**
 * Navigation contract (A11 §1.1, A14 §7). Features never depend on each other: they navigate
 * by these routes only.
 */
object HotRoute {
    const val TODAY = "today"
    const val MENTOR = "mentor"
    const val WEEK = "week"

    /** Tab «Путь» (D-033, A42 §3.1): the Titan, the 12-week goal, its stages and «Твой Титан». */
    const val PATH = "path"

    /**
     * «Настройки» under the avatar in the corner of every header (D-033): help, the mentor and the
     * connections. Not a tab any more; `hot://me` still opens it.
     */
    const val ME = "me"

    /** «Аватар › Наставник» (D-030, A38 §9): the mentor's character. */
    const val MENTOR_PERSONA = "mentor_persona"

    /** «Настройки → Подключения → WHOOP» (A11 §11, A18 И1.1). */
    const val WHOOP = "whoop"

    /**
     * Outside the tabs, the crisis screen. D-029 / D-033: no header button; reachable in ≤ 2 taps
     * from every tab («аватар › Помощь», «⋮ › Помощь» in the mentor chat), from the quick settings
     * tile and the shortcut.
     */
    const val SOS = "sos"

    /** «Рассвет» (A37 §4): the morning screen, once a day at the first open; outside the tabs. */
    const val DAWN = "dawn"

    const val DEEP_LINK_SCHEME = "hot"
    const val SOS_DEEP_LINK = "$DEEP_LINK_SCHEME://$SOS"
    const val TODAY_DEEP_LINK = "$DEEP_LINK_SCHEME://$TODAY"

    /** Bottom bar order: «Сегодня · Наставник · Неделя · Путь» (D-033: still four). */
    val tabs: List<String> = listOf(TODAY, MENTOR, WEEK, PATH)
}
