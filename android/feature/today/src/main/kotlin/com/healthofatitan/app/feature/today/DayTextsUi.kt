package com.healthofatitan.app.feature.today

import com.healthofatitan.app.domain.engine.Body
import com.healthofatitan.app.domain.engine.DayFlag
import com.healthofatitan.app.domain.engine.DayTexts
import com.healthofatitan.app.domain.engine.ItemStatus

/**
 * Texts of the day block on «Сегодня» (slice 2: regime, plan, evening deck). Short, neutral, no
 * diagnoses. Review: Agent 3 (body), Agent 10 (tone), Agent 12 (UX copy).
 */
object DayUiTexts {
    const val PENDING_TITLE = "План появится, когда WHOOP посчитает сон"
    const val PENDING_CAPTION = "Обычно это через несколько минут после подъёма."
    const val REGIME = "Режим дня"
    const val WHY = "Почему так"
    const val WHY_HIDE = "Скрыть"
    const val PLAN = "План"
    const val NOTIFY_HINT = "Разреши уведомления — утром придёт режим дня."
    const val NOTIFY_ALLOW = "Разрешить"

    const val EVENING = "Вечер · 2 вопроса"
    const val ALCOHOL_Q = "Алкоголь сегодня?"
    val ALCOHOL_OPTIONS = listOf(0 to "Не пил", 1 to "1", 2 to "2", 3 to "3", 4 to "4+")
    const val BODY_Q = "Как тело?"
    val BODY_OPTIONS = listOf(Body.FINE to "В порядке", Body.SORE to "Забито", Body.SICK to "Простыл")
    const val SKIP = "Пропустить"
    const val DONE = "Принято. Учту утром."

    fun markers(caffeineCutoffMin: Int, windDownMin: Int, bedtimeMin: Int): String =
        "Кофе до ${DayTexts.time(caffeineCutoffMin)} · без экранов с ${DayTexts.time(windDownMin)} · отбой ${DayTexts.time(bedtimeMin)}"

    fun status(s: ItemStatus): String? = when (s) {
        ItemStatus.OPEN -> null
        ItemStatus.DONE_WHOOP -> "✓ WHOOP"
        ItemStatus.DONE_SELF -> "✓ отмечено"
        ItemStatus.MODIFIED -> "⇄ изменено"
    }

    fun flag(f: DayFlag): String = when (f) {
        DayFlag.SICK -> "болеешь"
        DayFlag.TRAVEL -> "перелёт"
        DayFlag.LIGHT_WEEK -> "лёгкая неделя"
        DayFlag.CALIBRATION -> "изучаю норму"
        DayFlag.PRELIMINARY_NORM -> "норма предварительная"
        DayFlag.DATA_LIMITED -> "данных мало"
    }

    fun slot(startMin: Int?, endMin: Int?): String = when {
        startMin == null -> ""
        endMin == null -> DayTexts.time(startMin)
        else -> "${DayTexts.time(startMin)}–${DayTexts.time(endMin)}"
    }
}
