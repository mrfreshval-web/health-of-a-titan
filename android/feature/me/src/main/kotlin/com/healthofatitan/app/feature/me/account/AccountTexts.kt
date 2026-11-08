package com.healthofatitan.app.feature.me.account

import com.healthofatitan.app.core.model.text.G
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Texts of «Настройки › Аккаунт» (A65 §2.3, §3.4, §6; slices А3 and А5). Neutral form only: these
 * screens speak about the account, not about the person. Черновик Агента 5 — ждёт ревью Агентов 4
 * (UX), 7 (удаление, последствия, Google Play) и 10 (тон).
 */
object AccountTexts {
    val TITLE = G("Аккаунт")
    val ROW_CAPTION = G("Входы · устройства · выход")

    val WAYS_IN = G("Входы")
    val GOOGLE = G("Google")
    val WHOOP = G("WHOOP")
    val NOT_LINKED = G("Не привязан")
    val LINK_GOOGLE = G("Привязать Google")
    val LINK_WHOOP = G("Привязать WHOOP")
    val UNLINK = G("Отвязать")
    val LAST_WAY_NOTE = G("Последний способ входа отвязать нельзя: без него в аккаунт не попасть.")
    val STEP_UP_NOTE = G("Привязка, отвязка и удаление просят подтвердить вход: Google или WHOOP спросит ещё раз.")

    val DEVICES = G("Устройства")
    val THIS_PHONE = G("Этот телефон")
    val END_SESSION = G("Завершить")

    val SIGN_OUT = G("Выйти")
    val SIGN_OUT_ALL = G("Выйти везде")
    val SIGN_OUT_TITLE = G("Выйти из аккаунта?")
    val SIGN_OUT_ALL_TITLE = G("Выйти на всех устройствах?")
    val SIGN_OUT_BODY = G(
        "На телефоне живёт один аккаунт HoT. После выхода все данные HoT на этом телефоне сотрутся: дни, журнал, профиль, фото. " +
            "Резервной копии пока нет, она появится в следующих обновлениях. Приложение закроется.",
    )
    val SIGN_OUT_ALL_BODY = G(
        "Сессии закончатся на всех устройствах, и на этом тоже. Данные HoT на этом телефоне сотрутся, приложение закроется. " +
            "Нужна связь с сервером.",
    )

    val DELETE = G("Удалить аккаунт")
    val DELETE_TITLE = G("Удалить аккаунт HoT?")
    val DELETE_WHAT: List<G> = listOf(
        G("Удалятся аккаунт, привязанные входы Google и WHOOP и сессии на всех устройствах."),
        G("Доступ HoT к WHOOP будет отозван, токены сотрутся."),
        G("На этом телефоне сотрётся всё: дни, журнал, профиль, фото, «О себе», журнал безопасности и бережный режим еды."),
        G("Данные о здоровье хранились только на телефоне, на сервере HoT их нет."),
        G("Отменить удаление нельзя."),
    )
    val DELETE_GOOGLE_NOTE = G("Google не выдавал HoT токенов, отзывать там нечего. Убрать HoT из списка: myaccount.google.com/connections.")
    val DELETE_CONFIRM = G("Удалить навсегда")
    val DELETE_DONE = G("Аккаунт удалён. Данные на телефоне стираются, приложение закроется.")
    val CANCEL = G("Отмена")

    val IN_USE_GOOGLE = G("Этот Google уже привязан к другому аккаунту HoT")
    val IN_USE_WHOOP = G("Этот WHOOP уже привязан к другому аккаунту HoT")
    val IN_USE_BODY = G("Объединить два аккаунта нельзя. Можно выйти отсюда и войти в тот аккаунт: данные на этом телефоне сотрутся.")
    val IN_USE_SWITCH = G("Войти в тот аккаунт")

    fun inUse(provider: String): G = if (provider == "whoop") IN_USE_WHOOP else IN_USE_GOOGLE

    private val dateTime = DateTimeFormatter.ofPattern("dd.MM HH:mm")
    private val date = DateTimeFormatter.ofPattern("dd.MM")

    private fun at(ms: Long, format: DateTimeFormatter, zone: ZoneId): String = format.format(Instant.ofEpochMilli(ms).atZone(zone))

    /** «Привязан · 08.11 13:40 · Pixel 9 Pro XL» (A65 §2.3). */
    fun linked(linkedAt: Long, device: String?, zone: ZoneId = ZoneId.systemDefault()): G =
        G(listOfNotNull("Привязан", linkedAt.takeIf { it > 0 }?.let { at(it, dateTime, zone) }, device).joinToString(" · "))

    /** «Вход 08.11 · активность 08.11 13:40». */
    fun device(createdAt: Long, lastUsedAt: Long, zone: ZoneId = ZoneId.systemDefault()): G =
        G("Вход ${at(createdAt, date, zone)} · активность ${at(lastUsedAt, dateTime, zone)}")

    fun note(note: AccountNote): G = when (note) {
        AccountNote.OFFLINE -> G("Нет связи с сервером HoT.")
        AccountNote.FAILED -> G("Не получилось. Попробуй ещё раз.")
        AccountNote.CANCELLED -> G("Отменено.")
        AccountNote.MISMATCH -> G("Подтвердить вход можно только тем Google или WHOOP, который привязан к аккаунту.")
        AccountNote.LINKED -> G("Привязано.")
        AccountNote.UNLINKED -> G("Отвязано.")
        AccountNote.DEVICE_ENDED -> G("Сессия на устройстве завершена.")
        AccountNote.PROVIDER_LINKED -> G("К аккаунту уже привязан другой вход этого типа. Сначала отвяжи его.")
        AccountNote.LAST_WAY -> LAST_WAY_NOTE
        AccountNote.GOOGLE_OFF -> G("Вход через Google в этой сборке не настроен.")
        AccountNote.NO_GOOGLE_ACCOUNT -> G("На телефоне нет аккаунта Google. Добавь его в настройках телефона.")
        AccountNote.STEP_UP_FAILED -> G("Не получилось подтвердить вход. Попробуй ещё раз.")
    }

    val ALL: List<G> = listOf(
        TITLE, ROW_CAPTION, WAYS_IN, GOOGLE, WHOOP, NOT_LINKED, LINK_GOOGLE, LINK_WHOOP, UNLINK, LAST_WAY_NOTE, STEP_UP_NOTE,
        DEVICES, THIS_PHONE, END_SESSION, SIGN_OUT, SIGN_OUT_ALL, SIGN_OUT_TITLE, SIGN_OUT_ALL_TITLE, SIGN_OUT_BODY, SIGN_OUT_ALL_BODY,
        DELETE, DELETE_TITLE, DELETE_GOOGLE_NOTE, DELETE_CONFIRM, DELETE_DONE, CANCEL, IN_USE_GOOGLE, IN_USE_WHOOP, IN_USE_BODY, IN_USE_SWITCH,
        linked(1_762_600_000_000, "Pixel 9 Pro XL"), device(1_762_600_000_000, 1_762_600_000_000),
    ) + DELETE_WHAT + AccountNote.entries.map(::note)
}

/** One-off notes of «Аккаунт». */
enum class AccountNote {
    OFFLINE,
    FAILED,
    CANCELLED,
    MISMATCH,
    LINKED,
    UNLINKED,
    DEVICE_ENDED,
    PROVIDER_LINKED,
    LAST_WAY,
    GOOGLE_OFF,
    NO_GOOGLE_ACCOUNT,
    STEP_UP_FAILED,
}
