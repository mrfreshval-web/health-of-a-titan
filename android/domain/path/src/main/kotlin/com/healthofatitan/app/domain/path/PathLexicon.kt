package com.healthofatitan.app.domain.path

/**
 * The one dictionary of the working names of «Путь» (D-047, D-048, A55 §3). The final names come
 * after the card sort on 10 people (A54 §7): change them here and nowhere else. Code never keys on
 * these words — only on [Element], [ComponentKey], [Role] and the mark number.
 */
object PathLexicon {
    const val STATUS = "рабочие имена до сортировки карточек (A54 §7, D-048)"

    const val PATH = "Путь"
    const val WHY = "Зачем"
    const val GIVEN = "Дано"
    const val LEVER = "рычаг"
    const val HORIZON_TITAN = "Титан горизонта"
    const val BY_DATE = "к сроку"
    const val FULL = "целиком"

    fun element(e: Element): String = when (e) {
        Element.SKY -> "Небо"
        Element.EARTH -> "Земля"
        Element.WATER -> "Вода"
    }

    /** The mottos of the elements (A55 резюме). */
    fun motto(e: Element): String = when (e) {
        Element.SKY -> "Меньше шума. Больше неба."
        Element.EARTH -> "Крепкий шаг. Долгий бег."
        Element.WATER -> "Покой — тоже движение."
    }

    /** Short working names (D-048 п. 2, A55 §3). */
    fun name(c: ComponentKey): String = when (c) {
        ComponentKey.CARDIO -> "Выносливость"
        ComponentKey.STRENGTH -> "Сила"
        ComponentKey.MOBILITY -> "Подвижность"
        ComponentKey.BODY -> "Форма"
        ComponentKey.FUEL -> "Топливо"
        ComponentKey.ATTENTION -> "Внимание"
        ComponentKey.MASTERY -> "Мастерство"
        ComponentKey.CLARITY -> "Ясность"
        ComponentKey.OUTLOOK -> "Взгляд"
        ComponentKey.SLEEP -> "Сон"
        ComponentKey.REST -> "Отдых"
        ComponentKey.FEELINGS -> "Чувства"
        ComponentKey.PEOPLE -> "Люди рядом"
        ComponentKey.AFLOAT -> "На плаву"
    }

    /** The names of the scientific model (D-047 п. 1), for «Почему так» and the reviewers. */
    fun modelName(c: ComponentKey): String = when (c) {
        ComponentKey.CARDIO -> "Сердце и дыхание"
        ComponentKey.STRENGTH -> "Сила и мощность"
        ComponentKey.MOBILITY -> "Подвижность и баланс"
        ComponentKey.BODY -> "Состав тела"
        ComponentKey.FUEL -> "Топливо"
        ComponentKey.ATTENTION -> "Внимание"
        ComponentKey.MASTERY -> "Мастерство"
        ComponentKey.CLARITY -> "Ясность и решения"
        ComponentKey.OUTLOOK -> "Гибкость ума"
        ComponentKey.SLEEP -> "Сон"
        ComponentKey.REST -> "Восстановление"
        ComponentKey.FEELINGS -> "Чувства"
        ComponentKey.PEOPLE -> "Связи"
        ComponentKey.AFLOAT -> "На плаву"
    }

    /** Roles «ведёт · подпирает · бережёт» (D-048 п. 2). */
    fun role(r: Role): String = when (r) {
        Role.LEAD -> "ведёт"
        Role.PROP -> "подпирает"
        Role.GUARD -> "бережёт"
    }

    /** The marks 1–5 «начало · база · уверенно · сильно · свободно» (D-048 п. 2). */
    fun mark(n: Int): String = when (n) {
        1 -> "начало"
        2 -> "база"
        3 -> "уверенно"
        4 -> "сильно"
        5 -> "свободно"
        else -> "пока не знаю"
    }

    fun mode(m: GoalMode): String = when (m) {
        GoalMode.BY_DATE -> BY_DATE
        GoalMode.FULL -> FULL
    }

    /** Every name above, for the stop-dictionary test. */
    fun all(): List<String> = listOf(PATH, WHY, GIVEN, LEVER, HORIZON_TITAN, BY_DATE, FULL) +
        Element.entries.flatMap { listOf(element(it), motto(it)) } +
        ComponentKey.entries.flatMap { listOf(name(it), modelName(it)) } +
        Role.entries.map(::role) + (0..5).map(::mark)
}
