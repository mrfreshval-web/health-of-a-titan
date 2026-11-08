package com.healthofatitan.app.feature.mentor

import com.healthofatitan.app.core.model.mentor.MentorPersona

/**
 * The app's own lines in the chat with Винс (no model). ЧЕРНОВИК — ждёт ревью Агентов 10
 * (тон, кризис, бережный режим), 3 (медицинские строки) и 16 (характеры). The crisis line follows
 * A62 block 7 §3 word for word; the numbers are only 112 and 911 (решение основателя 07.11).
 */
object MentorTexts {
    /** The name in the chat header (D-056 п. 8, block 6 §4): only the name, no «· ИИ». */
    fun name(persona: MentorPersona): String = when (persona) {
        MentorPersona.SENSEI -> "Сенсей"
        MentorPersona.BRO -> "Винс"
        MentorPersona.BUTLER -> "Винсент"
    }

    /** The first line of a new conversation, said by the app in the character's manner (block 6). */
    fun greeting(persona: MentorPersona): String = when (persona) {
        MentorPersona.SENSEI -> "Я здесь. Что сегодня разберём?"
        MentorPersona.BRO -> "Здорово! Что сегодня разбираем?"
        MentorPersona.BUTLER -> "Добрый день. Чем могу быть полезен?"
    }

    /** One line under the header on the first visit (A62 block 6 §6: disclosure in the introduction). */
    const val DISCLOSURE = "Винс — ИИ-наставник HoT: всегда честно скажет, что знает, а чего нет. Не врач."

    const val INPUT_HINT = "Напиши Винсу"
    const val SEND = "Отправить"
    const val THINKING = "Думает…"

    /** A62 block 7 §3: what Винс says in SOS. */
    const val CRISIS = "Я ИИ, не человек. Живые люди ответят по 112 и 911."
    const val CRISIS_BUTTON = "Открыть «Помощь»"

    const val ED_CARE_REPLACED = "Об этом сейчас не говорю — у нас бережный режим еды. Давай о сне, движении или о том, как прошёл день."
    const val DOSE_REPLACED = "Дозы и схемы приёма не называю — это решает врач. Остальной план могу расписать по шагам."
    const val DIAGNOSIS_REPLACED = "Диагнозы я не ставлю. Могу помочь собрать, что рассказать врачу, и подсказать, насколько это срочно."
    const val REFUSED = "На это я не отвечу. Давай о другом — или, если тебе сейчас плохо, открой «Помощь»: 112 и 911."

    const val NOT_CONFIGURED = "Винс ещё не подключён к серверу ИИ. Как только ключ будет на месте, я заговорю."
    const val NEEDS_LOGIN = "Чтобы говорить с Винсом, войди в HoT: подключи WHOOP в профиле."
    const val OFFLINE = "Нет связи с сервером. Напиши ещё раз, когда сеть появится."
    const val BUSY = "Слишком много сообщений подряд. Дай мне минуту."
    const val FAILED = "Не получилось ответить. Попробуй ещё раз."
    const val SOFT_LIMIT = "Сегодня отвечаю короче — длинный разговор продолжим завтра."
    const val DAY_LIMIT = "На сегодня мой лимит разговоров исчерпан. Продолжим завтра. Если срочно — «Помощь»: 112 и 911."
    const val MONTH_LIMIT = "Лимит разговоров на этот месяц исчерпан. Ритуалы и план дня работают как обычно."

    const val MENU_NEW = "Новый разговор"
    const val MENU_MEMORY = "Что Винс помнит"
    const val MENU_HELP = "Помощь"
    const val MEMORY_TITLE = "Что Винс помнит"
    const val MEMORY_EMPTY = "Пока ничего. После разговора Винс сохраняет короткую выжимку — только на этом телефоне."
    const val MEMORY_NOTE = "Выжимки хранятся только на телефоне, зашифрованы. Винс видит три последние."
    const val MEMORY_FORGET = "Забыть все разговоры"
    const val MEMORY_CLOSE = "Закрыть"
}
