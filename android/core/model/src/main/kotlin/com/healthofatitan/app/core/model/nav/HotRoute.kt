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

    /** «Я › Еда и тело» (A41 §10): the profile of specifics, shared with training. Inside a tab. */
    const val NUTRITION_PROFILE = "nutrition_profile"

    /** Nutrition level (A41 §4). Inside a tab. */
    const val NUTRITION_LEVEL = "nutrition_level"

    /** Meal card of one slot of today (A41 §10). Inside the «Сегодня» tab. */
    const val NUTRITION_MEAL_ARG = "slot"
    const val NUTRITION_MEAL = "nutrition_meal/{$NUTRITION_MEAL_ARG}"

    fun nutritionMeal(slot: String): String = "nutrition_meal/$slot"

    const val DEEP_LINK_SCHEME = "hot"
    const val SOS_DEEP_LINK = "$DEEP_LINK_SCHEME://$SOS"
    const val TODAY_DEEP_LINK = "$DEEP_LINK_SCHEME://$TODAY"

    /** Bottom bar order: «Сегодня · Наставник · Неделя · Я». */
    val tabs: List<String> = listOf(TODAY, MENTOR, WEEK, ME)
}
