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
    fun endDeviceTitle(name: String) = G("Завершить сессию на «$name»?")
    val END_SESSION_BODY = G("Там нужно будет войти снова. Данные HoT на том устройстве не сотрутся.")

    val UNLINK_GOOGLE_TITLE = G("Отвязать Google?")
    val UNLINK_GOOGLE_BODY = G("Входить через Google больше не получится. Данные на телефоне не меняются. Перед отвязкой HoT попросит подтвердить вход.")
    val UNLINK_WHOOP_TITLE = G("Отвязать WHOOP?")
    val UNLINK_WHOOP_BODY = G(
        "Синхронизация WHOOP остановится, токены на сервере сотрутся, HoT попросит WHOOP отозвать доступ. " +
            "Уже сохранённые дни останутся на телефоне. Перед отвязкой HoT попросит подтвердить вход.",
    )

    val SIGN_OUT = G("Выйти")
    val SIGN_OUT_ALL = G("Выйти везде")
    val SIGN_OUT_TITLE = G("Выйти из аккаунта?")
    val SIGN_OUT_ALL_TITLE = G("Выйти на всех устройствах?")
    private val SIGN_OUT_KEEPS_BODY = G(
        "Сессия на этом телефоне закончится. Дни, журнал и профиль останутся на телефоне зашифрованными: " +
            "после входа в этот же аккаунт всё будет на месте.",
    )
    private val SIGN_OUT_ALL_KEEPS_BODY = G("Сессии закончатся на всех устройствах, и на этом тоже. Данные на устройствах не стираются. Нужна связь с сервером.")
    private val SIGN_OUT_ALL_WIPES_BODY = G(
        "Сессии закончатся на всех устройствах, и на этом тоже. Данные HoT на этом телефоне сотрутся, восстановить их не получится. " +
            "Приложение закроется. Нужна связь с сервером.",
    )

    /** «Выйти и стереть телефон» (review 2026-11-08 А-1): its own button and dialog. */
    val WIPE = G("Выйти и стереть телефон")
    val WIPE_TITLE = G("Стереть данные HoT на этом телефоне?")
    val WIPE_BODY = G(
        "Сотрётся всё: дни, журнал, профиль, фото, «О себе». Резервной копии пока нет: восстановить стёртое не получится. " +
            "Аккаунт останется, приложение закроется.",
    )
    val WIPE_CONFIRM = G("Стереть и выйти")

    /** The button «Выйти»: in variant 2 it is the wipe itself. */
    val SIGN_OUT_BUTTON: G get() = if (SignOutPolicy.WIPES) WIPE else SIGN_OUT
    val SIGN_OUT_DIALOG_TITLE: G get() = if (SignOutPolicy.WIPES) WIPE_TITLE else SIGN_OUT_TITLE
    val SIGN_OUT_BODY: G get() = if (SignOutPolicy.WIPES) WIPE_BODY else SIGN_OUT_KEEPS_BODY
    val SIGN_OUT_CONFIRM: G get() = if (SignOutPolicy.WIPES) WIPE_CONFIRM else SIGN_OUT
    val SIGN_OUT_ALL_BODY: G get() = if (SignOutPolicy.WIPES) SIGN_OUT_ALL_WIPES_BODY else SIGN_OUT_ALL_KEEPS_BODY

    val DELETE = G("Удалить аккаунт")
    val DELETE_TITLE = G("Удалить аккаунт HoT?")
    val DELETE_WHAT: List<G> = listOf(
        G("Удалятся аккаунт, привязанные входы Google и WHOOP и сессии на всех устройствах."),
        G("Токены WHOOP на сервере HoT сотрутся, HoT попросит WHOOP отозвать доступ."),
        G("На этом телефоне сотрётся всё: дни, журнал, профиль, фото, «О себе», журнал безопасности и бережный режим еды."),
        G("На других устройствах с этим аккаунтом данные HoT сотрутся при их следующем запуске с интернетом."),
        G("Данные о здоровье хранились только на телефоне, на сервере HoT их нет."),
        G("Отменить удаление нельзя."),
    )
    val DELETE_GOOGLE_NOTE = G("Google не выдавал HoT токенов, отзывать там нечего. Убрать HoT из списка: myaccount.google.com/connections.")
    val DELETE_CONFIRM = G("Удалить навсегда")
    val DELETE_DONE = G("Аккаунт удалён. Данные на телефоне стираются, приложение закроется.")
    val CANCEL = G("Отмена")

    val IN_USE_GOOGLE = G("Этот Google уже привязан к другому аккаунту HoT")
    val IN_USE_WHOOP = G("Этот WHOOP уже привязан к другому аккаунту HoT")
    val IN_USE_BODY = G(
        "Объединить два аккаунта нельзя. Можно выйти отсюда и войти в тот аккаунт. " +
            "Перед входом HoT спросит, стирать ли данные этого аккаунта на телефоне.",
    )
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
        DEVICES, THIS_PHONE, END_SESSION, SIGN_OUT, SIGN_OUT_ALL, SIGN_OUT_TITLE, SIGN_OUT_ALL_TITLE,
        SIGN_OUT_KEEPS_BODY, SIGN_OUT_ALL_KEEPS_BODY, SIGN_OUT_ALL_WIPES_BODY, WIPE, WIPE_TITLE, WIPE_BODY, WIPE_CONFIRM,
        endDeviceTitle("Pixel 8"), END_SESSION_BODY, UNLINK_GOOGLE_TITLE, UNLINK_GOOGLE_BODY, UNLINK_WHOOP_TITLE, UNLINK_WHOOP_BODY,
        DELETE, DELETE_TITLE, DELETE_GOOGLE_NOTE, DELETE_CONFIRM, DELETE_DONE, CANCEL, IN_USE_GOOGLE, IN_USE_WHOOP, IN_USE_BODY, IN_USE_SWITCH,
        linked(1_762_600_000_000, "Pixel 9 Pro XL"), device(1_762_600_000_000, 1_762_600_000_000),
    ) + DELETE_WHAT + AccountNote.entries.map(::note)
}

/**
 * The founder's answer to «Что делает „Выйти“, пока нет резервных копий?» (review 2026-11-08 §4) —
 * the one switch. false (variant 1, recommended): «Выйти» and «Выйти везде» keep the data, a
 * separate «Выйти и стереть телефон» wipes. true (variant 2): «Выйти» is «Выйти и стереть
 * телефон», «Выйти везде» wipes too, and «Войти в тот аккаунт» asks the wipe question first.
 */
object SignOutPolicy {
    const val WIPES = false
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
