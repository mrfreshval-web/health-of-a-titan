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

    /**
     * Outside the tabs, the crisis screen. D-029: no header button any more; reachable in ≤ 2 taps
     * («Я › Помощь», «⋮ › Помощь» in the mentor chat), from the quick settings tile and the shortcut.
     */
    const val SOS = "sos"

    /** «Рассвет» (A37 §4): the morning screen, once a day at the first open; outside the tabs. */
    const val DAWN = "dawn"

    /** Stream B «Тренировка» (A40 §7): the session of the day, under «Сегодня». */
    const val TRAINING = "training"

    /** The workout player, full screen outside the tabs; its notification opens it. */
    const val TRAINING_PLAYER = "training_player"

    const val DEEP_LINK_SCHEME = "hot"
    const val SOS_DEEP_LINK = "$DEEP_LINK_SCHEME://$SOS"
    const val TODAY_DEEP_LINK = "$DEEP_LINK_SCHEME://$TODAY"

    /** Bottom bar order: «Сегодня · Наставник · Неделя · Я». */
    val tabs: List<String> = listOf(TODAY, MENTOR, WEEK, ME)
}
