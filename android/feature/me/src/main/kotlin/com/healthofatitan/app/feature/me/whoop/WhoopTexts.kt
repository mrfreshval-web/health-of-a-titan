package com.healthofatitan.app.feature.me.whoop

import com.healthofatitan.app.integration.whoop.SyncStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Texts of «Я → Подключения → WHOOP» (A11 §11, A18 И1.1–И1.2). Tone: «ты», calm, no pressure
 * (A11 tone guide). Service texts only — no medical or psychological content.
 */
object WhoopTexts {
    const val TITLE = "WHOOP"
    const val BACK_LABEL = "Я · Подключения"
    const val SOURCE = "WHOOP"

    const val NOT_CONNECTED = "Не подключён"
    const val CONNECTED = "Подключён"
    const val LEAD = "Подключи WHOOP — и HoT узнает твою норму. Сравниваю тебя только с тобой: по твоим ночам, а не по чужим средним."

    const val WHAT_TITLE = "Что HoT возьмёт"
    val WHAT = listOf(
        "Сон" to "длительность и середина ночи",
        "Восстановление" to "вариабельность пульса и пульс покоя",
        "Нагрузка" to "минуты тренировок в пульсовых зонах",
        "Циклы" to "чтобы день шёл от пробуждения до пробуждения",
    )

    const val PRIVACY_TITLE = "Как HoT бережёт данные"
    val PRIVACY = listOf(
        "Сырые данные WHOOP не сохраняются: окно в 28 дней считается в памяти, на телефоне остаются только твои показатели HoT.",
        "Секрет приложения WHOOP живёт на сервере HoT, в телефоне его нет. Ключ входа там хранится зашифрованным.",
        "Отключить можно в любой момент: доступ удалится и на телефоне, и на сервере.",
    )

    const val CONNECT = "Подключить WHOOP"
    const val CONNECT_HINT = "Откроется страница входа WHOOP. Пароль вводишь там — HoT его не видит."

    const val SYNC = "Синхронизировать"
    const val RECONNECT = "Переподключить"
    const val DISCONNECT = "Отключить и удалить токены"
    const val DISCONNECT_TITLE = "Отключить WHOOP?"
    const val DISCONNECT_TEXT = "Ключи доступа удалятся с телефона и с сервера HoT, доступ к WHOOP будет отозван. " +
        "Твои показатели HoT останутся. Данные в самом WHOOP не изменятся."
    const val DISCONNECT_CONFIRM = "Отключить"
    const val CANCEL = "Отмена"

    const val NORM_TITLE = "Твоя норма"
    const val NORM_LEARNING = "Учусь твоей норме: нужно ~14 дней"
    const val NORM_READY = "Норма собрана — сравниваю тебя только с тобой"
    fun normNights(n: Int) = "$n из 14 ночей"
    fun normWindow(n: Int) = "по $n ${nightsWord(n)} за 28 дней"

    const val OFFLINE = "Обновлю, когда будет связь"

    const val NOT_ALLOWED_STATUS = "Вход выполнен, доступа пока нет"
    const val NOT_ALLOWED_LEAD = "HoT пока открыт только для владельца. Добавь этот WHOOP ID в список доступа сервера:"
    const val COPY = "Скопировать"
    const val COPIED = "Скопировано"
    val NOT_ALLOWED_STEPS = listOf(
        "GitHub → Settings → Secrets and variables → Actions → секрет ALLOWED_WHOOP_USER_IDS: впиши этот номер (несколько — через запятую).",
        "Дождись деплоя сервера: Actions → Backend (hot-edge), пара минут.",
        "Нажми «Подключить WHOOP» ещё раз.",
    )
    const val NOT_ALLOWED_SAFE = "Ничего не сохранено: доступ к WHOOP отозван сразу после входа."

    const val NOT_CONFIGURED = "Эта сборка не знает адрес сервера HoT. Основателю: задать переменную EDGE_BASE_URL " +
        "(GitHub → Settings → Secrets and variables → Actions → Variables) и взять новую сборку."

    fun note(note: WhoopNote): String = when (note) {
        WhoopNote.CANCELLED -> "Вход отменён. Можно попробовать ещё раз, когда будет удобно."
        WhoopNote.REJECTED -> "WHOOP не подтвердил вход — так бывает, если страница была открыта дольше 10 минут. Попробуй ещё раз."
        WhoopNote.OFFLINE -> "Нет связи с сервером HoT. Подключу, когда появится интернет — нажми ещё раз."
        WhoopNote.SERVER_NOT_READY -> "Сервер HoT ещё не настроен для WHOOP. Основателю: проверить секреты WHOOP_CLIENT_ID, WHOOP_CLIENT_SECRET и TOKEN_ENC_KEY."
        WhoopNote.NEEDS_NETWORK_TO_DISCONNECT -> "Чтобы удалить доступ и на сервере, нужна связь. Ничего не удалено — попробуй, когда будет интернет."
        WhoopNote.DISCONNECTED -> "WHOOP отключён. Токены удалены с телефона и с сервера."
    }

    fun syncLine(lastSyncAt: Long?, zone: ZoneId = ZoneId.systemDefault(), today: LocalDate = LocalDate.now(zone)): String {
        if (lastSyncAt == null) return "Загружаю 28 дней данных…"
        val at = Instant.ofEpochMilli(lastSyncAt).atZone(zone)
        val time = at.format(DateTimeFormatter.ofPattern("HH:mm"))
        return when (at.toLocalDate()) {
            today -> "Синхронизация $time"
            today.minusDays(1) -> "Синхронизация вчера в $time"
            else -> "Синхронизация " + at.format(DateTimeFormatter.ofPattern("d MMMM, HH:mm", Locale.forLanguageTag("ru")))
        }
    }

    /** A short status for the row in «Я» and the status card. */
    fun statusProblem(status: SyncStatus?): String? = when (status) {
        SyncStatus.OFFLINE -> OFFLINE
        SyncStatus.UNAVAILABLE -> "WHOOP или сервер HoT не отвечают — повторю сам"
        SyncStatus.RATE_LIMITED -> "WHOOP просит подождать — повторю позже"
        SyncStatus.NOT_ALLOWED -> "Доступ закрыт на сервере — проверь список ALLOWED_WHOOP_USER_IDS"
        SyncStatus.ERROR -> "Не смог разобрать ответ WHOOP — попробую при следующей синхронизации"
        SyncStatus.REAUTH -> "Вход в WHOOP истёк — подключи снова"
        SyncStatus.OK, null -> null
    }

    private fun nightsWord(n: Int): String {
        val mod100 = n % 100
        val mod10 = n % 10
        return when {
            mod100 in 11..14 -> "ночам"
            mod10 == 1 -> "ночи"
            else -> "ночам"
        }
    }
}
