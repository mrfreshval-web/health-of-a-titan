package com.healthofatitan.app.feature.today

import com.healthofatitan.app.integration.whoop.SyncStatus
import kotlin.math.roundToInt

/**
 * Texts of the «Сегодня» state card (A11, A25 §4). Levels are words relative to the user's own
 * norm, never a verdict and never an overall score; no medical content.
 */
object TodayTexts {
    const val LEAD_NOT_CONNECTED = "Утро за пару минут: состояние, план и одно главное. Начнём с WHOOP — по нему HoT узнает твою норму."
    const val CONNECT = "Подключить WHOOP"
    const val STATE_TITLE = "Состояние"
    const val LOADING = "Загружаю 28 дней данных…"
    const val NORM_NIGHTS = 14
    const val LEARNING = "Учусь твоей норме: нужно ~14 дней"
    const val CHARGE = "Заряд"
    const val BALANCE = "Весы"
    const val NORM_50 = "норма 50"

    fun nights(n: Int) = "$n из $NORM_NIGHTS ночей"

    /** A25 §4: five levels from «заметно ниже нормы» to «заметно выше». */
    fun chargeWord(level: Int?): String = when (level) {
        -2 -> "Заметно ниже нормы"
        -1 -> "Чуть ниже нормы"
        0 -> "Около твоей нормы"
        1 -> "Чуть выше нормы"
        2 -> "Заметно выше нормы"
        else -> "Нет данных"
    }

    /** A25 §4.2: < −1 underload, −1…+1 balance, > +1 load accumulates. */
    fun balanceWord(level: Int?): String = when (level) {
        null -> "Нет данных"
        in Int.MIN_VALUE..-1 -> "Недогрузка"
        0 -> "Баланс"
        else -> "Нагрузка копится"
    }

    fun sleepLine(hours: Double?, debtHours: Double?): String? {
        if (hours == null) return null
        val sleep = "Сон ${duration(hours)}"
        val debt = debtHours?.takeIf { it >= 0.25 }?.let { " · недосып ${duration(it)} за неделю" }.orEmpty()
        return sleep + debt
    }

    fun problem(status: SyncStatus?): String? = when (status) {
        SyncStatus.OFFLINE -> "Обновлю, когда будет связь"
        SyncStatus.UNAVAILABLE, SyncStatus.RATE_LIMITED, SyncStatus.ERROR -> "Данные WHOOP пока не обновились — повторю сам"
        SyncStatus.REAUTH -> "Вход в WHOOP истёк — подключи снова в «Я»"
        SyncStatus.NOT_ALLOWED -> "Доступ к серверу HoT закрыт — подробности в «Я → WHOOP»"
        SyncStatus.OK, null -> null
    }

    internal fun duration(hours: Double): String {
        val minutes = (hours * 60).roundToInt()
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0 -> "$m мин"
            m == 0 -> "$h ч"
            else -> "$h ч $m мин"
        }
    }
}
