package com.healthofatitan.app.feature.me.profile

import com.healthofatitan.app.core.model.text.G

/**
 * Texts of the account part of «Профиль» (A65 §2.4, slice А4): name, photo, body data from WHOOP
 * and «О себе». Neutral form only. Черновик Агента 5 — ждёт ревью Агентов 4 (UX), 7 (данные, S4,
 * согласие) и 10 (тон, «О себе»).
 */
object AccountProfileTexts {
    val SECTION = G("Имя и фото")
    val NAME = G("Имя")
    fun nameAsk(name: String) = G("$name, верно?")
    val NAME_YES = G("Да, верно")
    val NAME_EDIT = G("Изменить")
    val NAME_SAVE = G("Сохранить")
    val NAME_EMPTY = G("Имя не указано")
    fun googleLine(mask: String) = G("Google: $mask")

    val PHOTO_GALLERY = G("Выбрать фото")
    val PHOTO_CAMERA = G("Снять фото")
    val PHOTO_DELETE = G("Удалить фото")
    val PHOTO_FAILED = G("Не получилось открыть фото.")
    val PHOTO_NOTE = G("Фото хранится только на телефоне, зашифровано, и никуда не отправляется: ни на сервер, ни в Claude.")
    val AVATAR_DESCRIPTION = G("Аватар")

    val BODY = G("Из WHOOP")
    fun height(cm: Int) = G("Рост: $cm см")
    fun maxPulse(bpm: Int) = G("Максимальный пульс: $bpm уд/мин")
    val BODY_NONE = G("Рост и максимальный пульс подтянутся из WHOOP после подключения.")

    val ABOUT = G("О себе")
    val ABOUT_HINT = G("Что важно знать: работа, ритм дня, что помогает. До 500 знаков.")
    fun aboutCount(length: Int, max: Int) = G("$length / $max")
    val ABOUT_SAVE = G("Сохранить")
    val ABOUT_CLEAR = G("Очистить")
    val ABOUT_SAVED = G("Сохранено.")
    val ABOUT_NOTE = G("Текст хранится только на телефоне, зашифрован отдельно от остального.")
    val ABOUT_MENTOR = G("Давать «О себе» Винсу")
    val ABOUT_MENTOR_NOTE = G("Пока выключено, «О себе» не уходит ни Винсу, ни в Claude. Можно выключить в любой момент.")

    val ALL: List<G> = listOf(
        SECTION, NAME, nameAsk("Валентин"), NAME_YES, NAME_EDIT, NAME_SAVE, NAME_EMPTY, googleLine("v•••@gmail.com"),
        PHOTO_GALLERY, PHOTO_CAMERA, PHOTO_DELETE, PHOTO_FAILED, PHOTO_NOTE, AVATAR_DESCRIPTION,
        BODY, height(182), maxPulse(190), BODY_NONE,
        ABOUT, ABOUT_HINT, aboutCount(0, 500), ABOUT_SAVE, ABOUT_CLEAR, ABOUT_SAVED, ABOUT_NOTE, ABOUT_MENTOR, ABOUT_MENTOR_NOTE,
    )
}
