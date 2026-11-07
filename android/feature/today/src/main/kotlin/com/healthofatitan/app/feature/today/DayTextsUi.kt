package com.healthofatitan.app.feature.today

import com.healthofatitan.app.domain.engine.Body
import com.healthofatitan.app.domain.engine.DayFlag
import com.healthofatitan.app.domain.engine.DayTexts
import com.healthofatitan.app.domain.engine.ItemStatus

/**
 * Texts of the day block on «Сегодня» (slice 2: regime, plan, evening deck). Short, «ты»,
 * gender-neutral, no diagnoses, no judgement of the body or of drinking (A39: Agents 3, 10, 12,
 * 16). Where A37 puts a pictogram, the word stays as its `contentDescription`.
 */
object DayUiTexts {
    const val PENDING_TITLE = "План придёт, когда WHOOP посчитает ночь"
    const val PENDING_CAPTION = "Обычно — через несколько минут после подъёма."
    const val REGIME = "Режим дня"
    const val WHY = "Почему так"
    const val WHY_HIDE = "Скрыть"
    const val PLAN = "План"
    const val NOTIFY_HINT = "Утром пришлю режим дня — разреши уведомления."
    const val NOTIFY_ALLOW = "Разрешить"

    const val EVENING = "Вечер · 2 тапа"
    const val ALCOHOL_Q = "Алкоголь сегодня"
    val ALCOHOL_OPTIONS = listOf(0 to "Нет", 1 to "1", 2 to "2", 3 to "3", 4 to "4+")
    const val BODY_Q = "Как тело?"
    val BODY_OPTIONS = listOf(Body.FINE to "Легко", Body.SORE to "Забито", Body.SICK to "Простуда")
    const val SKIP = "Пропустить"
    const val DONE = "Принято. Утро это учтёт."

    /**
     * Markers line (A09 §3 budget: markers are a line, not items). The bedtime is a plan item of
     * its own, so it is not repeated here; [bedtimeMin] stays for the call site.
     */
    @Suppress("UNUSED_PARAMETER")
    fun markers(caffeineCutoffMin: Int, windDownMin: Int, bedtimeMin: Int): String =
        "Кофе до ${DayTexts.time(caffeineCutoffMin)} · экраны в сторону с ${DayTexts.time(windDownMin)}"

    fun status(s: ItemStatus): String? = when (s) {
        ItemStatus.OPEN -> null
        ItemStatus.DONE_WHOOP -> "✓ WHOOP"
        ItemStatus.DONE_SELF -> "✓ отмечено"
        ItemStatus.MODIFIED -> "⇄ изменено"
    }

    fun flag(f: DayFlag): String = when (f) {
        DayFlag.SICK -> "простуда"
        DayFlag.TRAVEL -> "в дороге"
        DayFlag.LIGHT_WEEK -> "лёгкая неделя"
        DayFlag.CALIBRATION -> "изучаю норму"
        DayFlag.PRELIMINARY_NORM -> "норма складывается"
        DayFlag.DATA_LIMITED -> "данных мало"
        DayFlag.MANUAL -> "без данных ночи"
    }

    fun slot(startMin: Int?, endMin: Int?): String = when {
        startMin == null -> ""
        endMin == null -> DayTexts.time(startMin)
        else -> "${DayTexts.time(startMin)}–${DayTexts.time(endMin)}"
    }
}
