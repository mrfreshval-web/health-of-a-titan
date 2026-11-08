package com.healthofatitan.app.feature.me.whoop

import com.healthofatitan.app.integration.whoop.SyncFailure
import com.healthofatitan.app.integration.whoop.SyncLogEntry
import com.healthofatitan.app.integration.whoop.SyncStatus
import com.healthofatitan.app.integration.whoop.SyncStep
import com.healthofatitan.app.integration.whoop.SyncTrigger
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Texts of «Я → Подключения → WHOOP» (A11 §11, A18 И1.1–И1.2). Tone: «ты», calm, no pressure
 * (A11 tone guide). One short line per element; details sit behind «Подробнее». Service texts
 * only — no medical or psychological content.
 */
object WhoopTexts {
    const val TITLE = "WHOOP"
    const val BACK_LABEL = "Настройки · Подключения"
    const val SOURCE = "WHOOP"

    const val NOT_CONNECTED = "Не подключён"
    const val CONNECTED = "Подключён"
    const val LEAD = "Твоя норма — по твоим ночам."

    /** What HoT takes: one word each, the icons do the rest. */
    val WHAT = listOf("Сон", "Восстановление", "Нагрузка")

    const val PRIVACY_SHORT = "Сырые данные не храню"
    const val MORE = "Подробнее"
    const val LESS = "Свернуть"
    val PRIVACY = listOf(
        "Окно 28 дней считается в памяти, на телефоне — только показатели HoT.",
        "Пароль WHOOP вводишь у WHOOP, HoT его не видит.",
        "Отключение удаляет доступ на телефоне и на сервере.",
    )

    const val CONNECT = "Подключить WHOOP"

    const val SYNC = "Обновить"
    const val RETRY = "Повторить"
    const val RECONNECT = "Переподключить"
    const val DISCONNECT = "Отключить"
    const val DISCONNECT_TITLE = "Отключить WHOOP?"
    const val DISCONNECT_TEXT = "Токены удалятся с телефона и сервера. Показатели HoT останутся."
    const val DISCONNECT_CONFIRM = "Отключить"
    const val CANCEL = "Отмена"

    const val NORM = "Норма"
    const val NORM_READY = "Норма готова"

    const val OFFLINE = "Нет сети — обновлю сам"

    const val NOT_ALLOWED_STATUS = "Доступа пока нет"
    const val NOT_ALLOWED_LEAD = "Добавь этот WHOOP ID на сервер HoT:"
    const val COPY = "Скопировать"
    const val COPIED = "Скопировано"
    val NOT_ALLOWED_STEPS = listOf(
        "GitHub → секрет ALLOWED_WHOOP_USER_IDS → вписать номер.",
        "Дождаться деплоя Backend (hot-edge).",
        "Нажать «Подключить WHOOP».",
    )
    const val NOT_ALLOWED_SAFE = "Ничего не сохранено."

    const val NOT_CONFIGURED = "Сборка без адреса сервера. Основателю: переменная EDGE_BASE_URL."

    const val LOG = "Журнал"
    const val LOG_TITLE = "Журнал синхронизации"
    const val LOG_EMPTY = "Пока пусто"
    const val LOG_CLEAR = "Очистить"
    const val LOG_HINT = "Без данных о здоровье и без токенов."

    fun note(note: WhoopNote): String = when (note) {
        WhoopNote.CANCELLED -> "Вход отменён."
        WhoopNote.REJECTED -> "WHOOP не подтвердил вход. Попробуй ещё раз."
        WhoopNote.OFFLINE -> "Нет связи с сервером HoT."
        WhoopNote.SERVER_NOT_READY -> "Сервер HoT не настроен. Основателю: секреты WHOOP_CLIENT_ID, WHOOP_CLIENT_SECRET, TOKEN_ENC_KEY."
        WhoopNote.NEEDS_NETWORK_TO_DISCONNECT -> "Нужна связь. Ничего не удалено."
        WhoopNote.DISCONNECTED -> "WHOOP отключён, токены удалены."
        WhoopNote.OTHER_ACCOUNT -> "Этот WHOOP привязан к другому аккаунту HoT. Здесь ничего не изменилось."
        WhoopNote.STEP_UP -> "Чтобы привязать WHOOP, нужно подтвердить вход. Открой «Настройки › Аккаунт» и нажми «Привязать WHOOP»."
        WhoopNote.PROVIDER_LINKED -> "К аккаунту уже привязан другой WHOOP. Сначала отвяжи его в «Настройки › Аккаунт»."
        WhoopNote.SIGNED_OUT -> "Сессия HoT закончилась. Войди снова."
    }

    /** Y7: «Синхронизировано в 14:32», «… вчера в 14:32»; before the first sync — «Загружаю…». */
    fun syncLine(lastSyncAt: Long?, zone: ZoneId = ZoneId.systemDefault(), now: Instant = Instant.now()): String {
        if (lastSyncAt == null) return "Загружаю…"
        val at = Instant.ofEpochMilli(lastSyncAt).atZone(zone)
        val today = now.atZone(zone).toLocalDate()
        val minutes = (now.toEpochMilli() - lastSyncAt) / 60_000
        val time = at.format(DateTimeFormatter.ofPattern("HH:mm"))
        return when {
            minutes < 1 -> "Синхронизировано только что"
            at.toLocalDate() == today -> "Синхронизировано в $time"
            at.toLocalDate() == today.minusDays(1) -> "Синхронизировано вчера в $time"
            else -> "Синхронизировано " + at.format(DateTimeFormatter.ofPattern("d MMMM, HH:mm", Locale.forLanguageTag("ru")))
        }
    }

    /** The step a running sync is on, as one word. */
    fun step(step: SyncStep): String = when (step) {
        SyncStep.START -> "Подключаюсь…"
        SyncStep.CYCLES -> "Дни…"
        SyncStep.RECOVERY -> "Восстановление…"
        SyncStep.SLEEP -> "Сон…"
        SyncStep.WORKOUTS -> "Тренировки…"
        SyncStep.COMPUTE -> "Считаю норму…"
    }

    fun done(nights: Int, workouts: Int) =
        "Готово · $nights ${plural(nights, "ночь", "ночи", "ночей")} · $workouts ${plural(workouts, "тренировка", "тренировки", "тренировок")}"

    /** Why a sync failed, briefly, with the HTTP code when there is one. */
    fun failure(f: SyncFailure, code: Int?): String {
        val base = when (f) {
            SyncFailure.OFFLINE -> OFFLINE
            SyncFailure.REAUTH -> "Вход истёк — переподключи"
            SyncFailure.NOT_ALLOWED -> "Сервер HoT не пускает"
            SyncFailure.WHOOP_DENIED -> "WHOOP закрыл доступ"
            SyncFailure.RATE_LIMITED -> "WHOOP просит паузу — повторю сам"
            SyncFailure.WHOOP_DOWN -> "WHOOP не отвечает — повторю сам"
            SyncFailure.WHOOP_REJECTED -> "WHOOP отклонил запрос"
            SyncFailure.SERVER -> "Сервер HoT недоступен — повторю сам"
            SyncFailure.PARSE -> "Не понял ответ WHOOP"
            SyncFailure.UNKNOWN -> "Сбой синхронизации"
        }
        return if (code != null && f != SyncFailure.OFFLINE) "$base · $code" else base
    }

    /** A short status for the row in «Я» (from the stored status, survives restarts). */
    fun statusProblem(status: SyncStatus?): String? = when (status) {
        SyncStatus.OFFLINE -> OFFLINE
        SyncStatus.UNAVAILABLE -> "Нет ответа — повторю сам"
        SyncStatus.RATE_LIMITED -> "Пауза — повторю позже"
        SyncStatus.NOT_ALLOWED -> "Сервер HoT не пускает"
        SyncStatus.DENIED -> "WHOOP закрыл доступ"
        SyncStatus.ERROR -> "Ошибка синхронизации"
        SyncStatus.REAUTH -> "Вход истёк"
        SyncStatus.OK, null -> null
    }

    fun trigger(name: String): String = when (runCatching { SyncTrigger.valueOf(name) }.getOrNull()) {
        SyncTrigger.MANUAL -> "вручную"
        SyncTrigger.LOGIN -> "вход"
        SyncTrigger.APP_OPEN -> "запуск"
        SyncTrigger.BACKGROUND -> "фон"
        null -> name.lowercase()
    }

    /** One line of the log: «✓ 30 ночей · 5 трен. · 2,1 с» or «! WHOOP_DOWN · 502 · SLEEP». */
    fun logLine(e: SyncLogEntry): String = if (e.ok) {
        listOfNotNull(
            e.nights?.let { "$it ${plural(it, "ночь", "ночи", "ночей")}" },
            e.workouts?.let { "$it трен." },
            e.days?.let { "$it дн." },
            seconds(e.durationMs),
        ).joinToString(" · ")
    } else {
        listOfNotNull(e.result, e.httpCode?.toString(), e.step?.lowercase(), e.detail, seconds(e.durationMs).takeIf { e.durationMs > 0 })
            .joinToString(" · ")
    }

    fun logTime(at: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(at).atZone(zone).format(DateTimeFormatter.ofPattern("dd.MM HH:mm:ss"))

    private fun seconds(ms: Long): String = String.format(Locale.forLanguageTag("ru"), "%.1f с", ms / 1000.0)

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
