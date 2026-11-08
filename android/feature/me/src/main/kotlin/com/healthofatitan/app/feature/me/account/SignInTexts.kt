package com.healthofatitan.app.feature.me.account

/**
 * Texts of «Вход» (A65 §2, D-062). Gender-neutral: the sign-in stands before the address form is
 * chosen. Draft of Agent 5; Agent 4 polishes the wording (A65 «Открытые вопросы»).
 */
object SignInTexts {
    const val TITLE = "Вход в HoT"
    const val BODY = "Один аккаунт для телефона и данных. Без паролей: войди через Google или через WHOOP."
    const val GOOGLE = "Войти через Google"
    const val WHOOP = "Войти через WHOOP"
    const val GOOGLE_OFF = "Вход через Google в этой сборке не настроен."
    const val PRIVACY = "Почту и имя сервер HoT не хранит. Данные о здоровье хранятся только на телефоне."
    const val HELP = "Помощь"

    const val INVITE_TITLE = "У тебя уже есть HoT?"
    const val INVITE_BODY = "Если HoT уже работал с твоим WHOOP, войди через WHOOP. Новый аккаунт — по коду приглашения."
    const val INVITE_LABEL = "Код приглашения"
    const val CREATE = "Создать аккаунт"
    const val BACK = "Назад"

    /** A65 §2.3: the phone keeps another account's data; signing in asks to wipe it first. */
    const val OWNER_TITLE = "На телефоне данные другого аккаунта HoT"
    const val OWNER_BODY = "На телефоне живёт один аккаунт. Чтобы войти, данные прошлого аккаунта нужно стереть: " +
        "дни, журнал, профиль, фото, «О себе». Восстановить их не получится. " +
        "После стирания приложение закроется — останется войти ещё раз."
    const val OWNER_WIPE = "Стереть и войти"
    const val OWNER_CANCEL = "Отмена"

    fun whoopId(id: String) = "WHOOP ID для приглашения: $id"

    fun note(note: SignInNote): String = when (note) {
        SignInNote.CANCELLED -> "Вход отменён."
        SignInNote.REJECTED -> "Не получилось войти. Попробуй ещё раз."
        SignInNote.OFFLINE -> "Нет связи с сервером HoT."
        SignInNote.NOT_CONFIGURED -> "Сервер HoT не настроен для входа."
        SignInNote.GOOGLE_NOT_CONFIGURED -> GOOGLE_OFF
        SignInNote.NO_GOOGLE_ACCOUNT -> "На телефоне нет аккаунта Google. Добавь его в настройках или войди через WHOOP."
        SignInNote.INVITE_INVALID -> "Код не подошёл: проверь его или попроси новый."
        SignInNote.OTHER_ACCOUNT -> "Этот WHOOP привязан к другому аккаунту HoT."
    }

    val ALL: List<String> = listOf(TITLE, BODY, GOOGLE, WHOOP, GOOGLE_OFF, PRIVACY, HELP, INVITE_TITLE, INVITE_BODY, INVITE_LABEL, CREATE, BACK) +
        listOf(OWNER_TITLE, OWNER_BODY, OWNER_WIPE, OWNER_CANCEL) +
        SignInNote.entries.map(::note)
}
