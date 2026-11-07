package com.healthofatitan.app.feature.today

import com.healthofatitan.app.core.ui.component.HotNorm
import com.healthofatitan.app.integration.whoop.SyncFailure
import com.healthofatitan.app.integration.whoop.SyncStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Texts of «Сегодня» (A11, A25 §4). One short line per element: numbers and the ring speak, words
 * only name things. Levels are words relative to the user's own norm, never a verdict and never
 * an overall score; no medical content.
 */
object TodayTexts {
    const val LEAD_NOT_CONNECTED = "Подключи WHOOP — HoT узнает твою норму."
    const val CONNECT = "Подключить WHOOP"
    const val STATE_TITLE = "Состояние"
    const val LOADING = "Загружаю…"
    /** Nights before Заряд is shown: the engine's MIN_BASELINE (14), out of a 28-night window (R5). */
    const val NORM_NIGHTS = HotNorm.MIN_NIGHTS
    const val NORM_FULL_NIGHTS = HotNorm.FULL_NIGHTS

    /** R3: a tap opens an item; a long press marks it. */
    const val OPEN = "Открыть"
    const val UNMARK = "Снять отметку"
    const val NORM = "Норма"
    const val CHARGE = "Заряд"
    const val BALANCE = "Весы"
    const val NORM_50 = "норма 50"
    const val RETRY = "Повторить"

    const val SLEEP = "Сон"
    const val RECOVERY = "Восстановление"
    const val WORKOUT = "Тренировка"
    const val STEPS = "Шаги"
    const val NO_DATA = "нет данных"

    /** R5: the nights of the norm as words, never a fraction («Учусь тебя: 9 из 28 ночей»). */
    fun nights(n: Int) = HotNorm.label(n)

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

    /** «7:12» — hours of sleep as a clock-like number. */
    fun clock(hours: Double): String {
        val minutes = (hours * 60).roundToInt()
        return "${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')}"
    }

    /** Caption under the sleep number: WHOOP's need if known, otherwise the 7 h target and the week's debt. */
    fun sleepCaption(needHours: Double?, debtHours: Double?): String? = when {
        needHours != null -> "нужно ${clock(needHours)}"
        debtHours != null && debtHours >= 0.25 -> "недосып ${duration(debtHours)} за неделю"
        else -> null
    }

    /** «ВСР 62 мс» and «пульс 51» — the recovery inputs of last night (raw WHOOP, memory only). */
    fun hrv(ms: Double) = "${ms.roundToInt()} мс"
    fun restingHr(bpm: Double) = "пульс ${bpm.roundToInt()}"
    const val HRV_LABEL = "ВСР"

    /** Short level word for a tile (the long form is [chargeWord]). */
    fun levelShort(level: Int?): String? = when (level) {
        -2 -> "Заметно ниже"
        -1 -> "Чуть ниже"
        0 -> "Норма"
        1 -> "Чуть выше"
        2 -> "Заметно выше"
        else -> null
    }

    /** «Бег · вчера», «Силовая · 07:10». */
    fun workoutCaption(sport: String?, start: Instant, zone: ZoneId = ZoneId.systemDefault(), today: LocalDate = LocalDate.now(zone)): String {
        val at = start.atZone(zone)
        val day = when (at.toLocalDate()) {
            today -> at.format(DateTimeFormatter.ofPattern("HH:mm"))
            today.minusDays(1) -> "вчера"
            else -> at.format(DateTimeFormatter.ofPattern("d.MM"))
        }
        return "${sport(sport)} · $day"
    }

    /** WHOOP sport names come in English; the common ones in Russian, the rest as «Тренировка». */
    fun sport(name: String?): String {
        val key = name?.lowercase()?.replace('_', ' ')?.replace('-', ' ')?.trim().orEmpty()
        return SPORTS[key] ?: if (key.isEmpty() || key == "activity") WORKOUT else key.replaceFirstChar { it.uppercase() }
    }

    private val SPORTS = mapOf(
        "running" to "Бег",
        "cycling" to "Велосипед",
        "walking" to "Ходьба",
        "hiking" to "Поход",
        "weightlifting" to "Силовая",
        "powerlifting" to "Силовая",
        "strength trainer" to "Силовая",
        "functional fitness" to "Функциональная",
        "hiit" to "ВИИТ",
        "yoga" to "Йога",
        "pilates" to "Пилатес",
        "swimming" to "Плавание",
        "rowing" to "Гребля",
        "boxing" to "Бокс",
        "tennis" to "Теннис",
        "soccer" to "Футбол",
        "basketball" to "Баскетбол",
        "stretching" to "Растяжка",
        "spin" to "Сайкл",
        "elliptical" to "Эллипс",
        "stairmaster" to "Степпер",
        "climber" to "Скалолазание",
        "rock climbing" to "Скалолазание",
        "skiing" to "Лыжи",
        "snowboarding" to "Сноуборд",
        "martial arts" to "Единоборства",
        "jiu jitsu" to "Джиу-джитсу",
        "dance" to "Танцы",
        "golf" to "Гольф",
    )

    /** One line about a sync problem stored in `sync_state` (after a restart, no live progress). */
    fun problem(status: SyncStatus?): String? = when (status) {
        SyncStatus.OFFLINE -> "Нет сети — обновлю сам"
        SyncStatus.UNAVAILABLE, SyncStatus.RATE_LIMITED -> "WHOOP не обновился — повторю сам"
        SyncStatus.ERROR -> "WHOOP не обновился"
        SyncStatus.REAUTH -> "Вход в WHOOP истёк"
        SyncStatus.NOT_ALLOWED, SyncStatus.DENIED -> "Нет доступа к WHOOP"
        SyncStatus.OK, null -> null
    }

    /** The live status line under the state card while/after a sync. */
    fun failure(f: SyncFailure, code: Int?): String {
        val base = when (f) {
            SyncFailure.OFFLINE -> "Нет сети — обновлю сам"
            SyncFailure.REAUTH -> "Вход в WHOOP истёк"
            SyncFailure.NOT_ALLOWED -> "Сервер HoT не пускает"
            SyncFailure.WHOOP_DENIED -> "WHOOP закрыл доступ"
            SyncFailure.RATE_LIMITED -> "WHOOP просит паузу"
            SyncFailure.WHOOP_DOWN -> "WHOOP не отвечает"
            SyncFailure.WHOOP_REJECTED -> "WHOOP отклонил запрос"
            SyncFailure.SERVER -> "Сервер HoT недоступен"
            SyncFailure.PARSE -> "Не понял ответ WHOOP"
            SyncFailure.UNKNOWN -> "Сбой синхронизации"
        }
        return if (code != null && f != SyncFailure.OFFLINE) "$base · $code" else base
    }

    fun done(nights: Int, workouts: Int) = "Готово · $nights ${plural(nights, "ночь", "ночи", "ночей")} · $workouts ${plural(workouts, "тренировка", "тренировки", "тренировок")}"

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

    internal fun plural(n: Int, one: String, few: String, many: String): String {
        val mod100 = n % 100
        val mod10 = n % 10
        return when {
            mod100 in 11..14 -> many
            mod10 == 1 -> one
            mod10 in 2..4 -> few
            else -> many
        }
    }
}
