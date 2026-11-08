package com.healthofatitan.app.feature.mentor

import com.healthofatitan.app.core.model.mentor.MentorPersona

/**
 * The app's own lines in the chat with Винс (no model). ЧЕРНОВИК — ждёт ревью Агентов 10
 * (тон, кризис, бережный режим), 3 (медицинские строки) и 16 (характеры); правки ревью 08.11 внесены, строки — тоже черновик. The crisis line follows
 * A62 block 7 §3 word for word; the numbers are only 112 and 911 (решение основателя 07.11).
 */
object MentorTexts {
    /** The name in the chat header (D-056 п. 8, block 6 §4): only the name, no «· ИИ». */
    fun name(persona: MentorPersona): String = when (persona) {
        MentorPersona.SENSEI -> "Сенсей"
        MentorPersona.BRO -> "Винс"
        MentorPersona.BUTLER -> "Винсент"
    }

    /** Винсент speaks on «вы» (block 6 §4, ревью 16): [ty] for Сенсей and Винс, [vy] for Винсент. */
    private fun form(persona: MentorPersona, ty: String, vy: String): String = if (persona == MentorPersona.BUTLER) vy else ty

    /** The first line of a new conversation, said by the app in the character's manner (block 6). */
    fun greeting(persona: MentorPersona): String = when (persona) {
        MentorPersona.SENSEI -> "Я здесь. Что сегодня разберём?"
        MentorPersona.BRO -> "Здорово! Что сегодня разбираем?"
        MentorPersona.BUTLER -> "Здравствуйте. Чем могу быть полезен?"
    }

    /** One line under the header on the first visit (A62 block 6 §6: disclosure in the introduction). */
    const val DISCLOSURE = "Винс — ИИ-наставник HoT: всегда честно скажет, что знает, а чего нет. Не врач и не психотерапевт."

    /** [DISCLOSURE] with the name of the chosen character (ревью 16). */
    fun disclosure(persona: MentorPersona): String =
        "${name(persona)} — ИИ-наставник HoT: всегда честно скажет, что знает, а чего нет. Не врач и не психотерапевт."

    /** М6 ревью 08.11: always under the name, on the chat screen only. */
    const val SUBTITLE = "ИИ-наставник"

    fun inputHint(persona: MentorPersona): String = when (persona) {
        MentorPersona.SENSEI -> "Напиши Сенсею"
        MentorPersona.BRO -> "Напиши Винсу"
        MentorPersona.BUTLER -> "Напишите Винсенту"
    }

    const val SEND = "Отправить"
    const val THINKING = "Думает…"

    /** A62 block 7 §3: what Винс says in SOS. */
    const val CRISIS = "Я ИИ, не человек. Живые люди ответят по 112 и 911."
    const val CRISIS_BUTTON = "Открыть «Помощь»"
    const val HELP_CHIP = "Помощь · 112 · 911"

    /**
     * М1: the model's `[[SOS_MED]]` (chest pain, fainting, stroke signs…). In the manner of the
     * check-in's painChest line. Not the crisis journal, not the 72 h window. Ждёт ревью Агента 3.
     */
    fun emergency(persona: MentorPersona): String = form(
        persona,
        "Похоже, это может быть срочно. Если это происходит сейчас — звони 112 или 911. Не садись за руль и попроси кого-нибудь побыть рядом. Тренировки отложи, пока врач не разрешит.",
        "Похоже, это может быть срочно. Если это происходит сейчас — звоните 112 или 911. Не садитесь за руль и попросите кого-нибудь побыть рядом. Тренировки отложите, пока врач не разрешит.",
    )

    /** М2: Text 1 of A07 §4.3, once per conversation after CrisisGuard level 1. Ждёт ревью Агента 10. */
    fun safetyQuestion(persona: MentorPersona): String = form(
        persona,
        "Слышу, как тебе тяжело. Спрошу прямо, потому что это важно: бывают мысли навредить себе или что не хочется жить?",
        "Слышу, как вам тяжело. Спрошу прямо, потому что это важно: бывают ли у вас мысли навредить себе или что не хочется жить?",
    )

    /** М10: inside the crisis window, after the card was shown once. Ждёт ревью Агента 10. */
    fun window(persona: MentorPersona): String = form(
        persona,
        "Я рядом. Сейчас без советов и планов — важнее, как ты. Если станет тяжелее — «Помощь»: 112 и 911.",
        "Я рядом. Сейчас без советов и планов — важнее, как вы. Если станет тяжелее — «Помощь»: 112 и 911.",
    )

    fun edCareReplaced(persona: MentorPersona): String = form(
        persona,
        "Об этом сейчас не говорю — у нас бережный режим еды. Давай о сне, движении или о том, как прошёл день.",
        "Об этом сейчас не говорю — у нас бережный режим еды. Давайте о сне, движении или о том, как прошёл день.",
    )

    /** М5: a food or weight hiding of the bus without ED_CARE — no mention of the care mode. */
    fun foodReplaced(persona: MentorPersona): String = form(
        persona,
        "Об этом сейчас не говорю. Давай о сне, движении или о том, как прошёл день.",
        "Об этом сейчас не говорю. Давайте о сне, движении или о том, как прошёл день.",
    )

    const val DOSE_REPLACED = "Дозы и схемы приёма не называю — это решает врач. Остальной план могу расписать по шагам."
    const val DIAGNOSIS_REPLACED = "Диагнозы я не ставлю. Могу помочь собрать, что рассказать врачу, и подсказать, насколько это срочно."

    fun refused(persona: MentorPersona): String = form(
        persona,
        "На это я не отвечу. Если тебе сейчас тяжело — «Помощь» рядом: 112 и 911. О чём поговорим?",
        "На это я не отвечу. Если вам сейчас тяжело — «Помощь» рядом: 112 и 911. О чём поговорим?",
    )

    fun notConfigured(persona: MentorPersona): String =
        "${name(persona)} ещё не подключён к серверу ИИ. Как только ключ будет на месте, я заговорю."

    fun needsLogin(persona: MentorPersona): String = form(
        persona,
        "Чтобы говорить с Винсом, войди в HoT: подключи WHOOP в профиле.",
        "Чтобы говорить с Винсентом, войдите в HoT: подключите WHOOP в профиле.",
    )

    fun offline(persona: MentorPersona): String = form(
        persona,
        "Нет связи с сервером. Напиши ещё раз, когда сеть появится.",
        "Нет связи с сервером. Напишите ещё раз, когда сеть появится.",
    )

    fun busy(persona: MentorPersona): String = form(
        persona,
        "Слишком много сообщений подряд. Дай мне минуту.",
        "Слишком много сообщений подряд. Дайте мне минуту.",
    )

    fun failed(persona: MentorPersona): String = form(
        persona,
        "Не получилось ответить. Попробуй ещё раз.",
        "Не получилось ответить. Попробуйте ещё раз.",
    )

    const val SOFT_LIMIT = "Сегодня отвечаю короче — длинный разговор продолжим завтра."
    const val DAY_LIMIT = "На сегодня мой лимит разговоров исчерпан. Продолжим завтра. Если срочно — «Помощь»: 112 и 911."
    const val MONTH_LIMIT = "Лимит разговоров на этот месяц исчерпан. Ритуалы и план дня работают как обычно. Если срочно — «Помощь»: 112 и 911."

    const val MENU_NEW = "Новый разговор"
    const val MENU_MEMORY = "Что Винс помнит"
    const val MENU_HELP = "Помощь"
    const val MEMORY_TITLE = "Что Винс помнит"
    const val MEMORY_EMPTY = "Пока ничего. После разговора Винс сохраняет короткую выжимку — только на этом телефоне."
    const val MEMORY_NOTE = "Выжимки хранятся только на телефоне, зашифрованы. Винс видит три последние."
    const val MEMORY_FORGET = "Забыть все разговоры"
    const val MEMORY_CLOSE = "Закрыть"
}
