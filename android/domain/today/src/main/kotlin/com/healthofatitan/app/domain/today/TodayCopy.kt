package com.healthofatitan.app.domain.today

/**
 * Every new word of «Сегодня» v3 (A46 v0.3) in one place. **Ждёт ревью Агентов 3, 10 и 16**
 * (медицинский смысл, психика, редактура): the wording is a working draft, no medical meaning is
 * invented here — what a number means is said by its source, not by us. The A39 stop dictionary
 * ([StopWords]) is run over all of it in a test (T-13).
 */
object TodayCopy {
    const val REVIEW = "ждёт ревью 3, 10, 16"

    // Blocks (A46 §1).
    const val BLOCK_ME = "Я сегодня"
    const val BLOCK_SUPPORTS = "Опоры"
    const val BLOCK_SUPPORTS_HINT = "держу за тебя"
    const val BLOCK_TASKS = "Дела дня"
    const val BLOCK_RHYTHM = "Ритм дня"
    const val WHY_LINK = "почему так"

    // «Я сегодня» (A46 §2.2, §4).
    const val CHARGE = "Заряд"
    const val CHARGE_NORM = "норма 50"
    const val NIGHT = "ночь"
    const val YOU = "ты"
    const val YOU_CAPTION = "бодрость утром"
    const val NO_DATA = "нет данных"
    const val ESTIMATE = "оценка"
    const val LEAD_NO_WHOOP = "Я ещё не знаю тебя"
    const val LEAD_NIGHT_PENDING = "Ночь досчитывается"
    const val LEAD_RETURN = "С возвращением"
    const val LEAD_SAFETY = "Сегодня главное — ты. Помощь рядом."

    fun calibration(nights: Int): String = "Учусь тебя: $nights из ${MeRules.NORM_NIGHTS}"

    fun need(text: String): String = "нужно $text"

    // Supports (A46 §2.3).
    const val EFFORT = "усердие"
    const val MOVEMENT = "движение"
    const val NUTRITION = "питание"
    const val WATER = "вода"
    const val SLEEP_TONIGHT = "сон сегодня"
    const val WATER_ADD = "+ стакан"
    const val WATER_REMOVE = "− стакан"
    const val SOURCE_WHOOP_CYCLE = "WHOOP: нагрузка дня и активные калории"
    const val SOURCE_STEPS = "WHOOP: шаги дня; по часам — Health Connect телефона"
    const val SOURCE_STEPS_PHONE = "Health Connect телефона"
    const val SOURCE_MEALS = "твои отметки приёмов пищи"
    const val SOURCE_WATER = "твои тапы; формула нормы — оценка"
    const val SOURCE_SLEEP_PREP = "время отметок и окна сна — оценка"
    const val EFFORT_HIDDEN = "WHOOP · сегодня скрыто"
    const val MEAL_MARK = "Отметить приём"
    const val MEAL_UNMARK = "Снять отметку"

    // Meal slots for the nutrition support (A41 §10, the plan's words).
    val MEALS: List<Pair<String, String>> = listOf(
        "breakfast" to "Завтрак",
        "lunch" to "Обед",
        "snack" to "Перекус",
        "dinner" to "Ужин",
    )

    const val COND_CAFFEINE = "кофе до"
    const val COND_TRAINING = "тренировка до"
    const val COND_DINNER = "ужин до"
    const val COND_SCREENS = "экраны с"

    fun ofTotal(done: Int, total: Int): String = "$done из $total"

    // Tasks (A46 §2.4).
    const val NOW = "сейчас"
    const val FULL = "полная"
    const val MINI = "мини"
    const val NOT_TODAY = "Не сегодня"
    const val DONE = "Отмечено"
    const val UNDO = "Отменить"
    const val START = "Начать"
    const val OPEN = "открыть"
    const val TASK_CONNECT_WHOOP = "Подключить WHOOP · 1 мин"
    const val TASK_START_QUESTIONS = "Начать с вопросов"
    const val TASK_START_SMALL = "Начать с малого"
    const val TASK_START_SMALL_WHY = "одно небольшое дело — и день уже твой"
    const val TASK_HELP = "Помощь"
    // A46 says «Был у врача»; A39 has no male gender, so a neutral draft (Agent 16).
    const val TASK_DOCTOR = "Врач уже в курсе"
    const val TASK_ASSEMBLE_WEEK = "Собрать неделю"
    const val TASK_ASSEMBLE_WEEK_WHY = "дела дня берутся из целей недели"
    const val TASK_REMINDER = "напоминание"
    const val BACK_TO_WEEK = "Вернулось в неделю"

    fun week(done: Int, target: Int): String = "неделя $done из $target"

    // Rhythm (A46 §2.5, A49, D-043).
    const val DAWN = "Рассвет"
    const val TRANSITION = "Переход"
    const val CLOSE_DAY = "Закрыть день"
    const val LANDING = "Приземление"
    const val WEEK_ASSEMBLY = "Итог и сборка"
    const val BREATHER = "Передышка"
    const val ARSENAL = "Арсенал"
    const val WIN_COFFEE = "кофе до"
    const val WIN_DINNER = "ужин до"
    const val WIN_SCREENS = "экраны с"
    const val WIN_BEDTIME = "отбой"

    // «Почему так» (A46 §3).
    const val WHY_TITLE = "Почему так"
    const val WHY_SUMMARY = "Итог"
    const val WHY_SLEEP = "Сон"
    const val WHY_HEART = "Сердце и сосуды"
    const val WHY_BREATH = "Дыхание"
    const val WHY_DEVIATIONS = "Отклонения"
    const val WHY_YOU = "Ты ↔ WHOOP"
    // A46 says «Не согласен»; neutral draft without gender (Agent 16).
    const val WHY_DISAGREE = "Не похоже на меня"
    const val WHY_ASK = "Спросить Винса"
    const val WHY_NO_DEVIATIONS = "Всё в твоём обычном коридоре."

    /** A46 §3 п. 5: ≥ 3 values outside the corridor at once. Wording — Agent 3 (ждёт ревью). */
    const val WHY_MANY_DEVIATIONS = "Тело, похоже, тратит силы на что-то ещё — прислушайся к самочувствию."
    const val WHY_HELP_HINT = "Если тревожно — «Помощь» в профиле справа сверху."
    const val WHY_YOU_MATCH = "Ощущения и данные совпали."
    const val WHY_YOU_SCISSORS = "Ощущения и данные разошлись — так тоже бывает, это повод присмотреться."
    const val WHY_YOU_NONE = "Ответь на вопросы Рассвета — и здесь появится сравнение."

    fun deviationsCount(n: Int): String = "Вне коридора: $n"

    const val M_SLEEP = "сон"
    const val M_NEED = "потребность"
    const val M_DEEP = "глубокий"
    const val M_REM = "быстрый"
    const val M_LIGHT = "лёгкий"
    const val M_AWAKE = "пробуждения"
    const val M_EFFICIENCY = "эффективность"
    const val M_CONSISTENCY = "регулярность"
    const val M_DEBT = "долг сна"
    const val M_RHR = "пульс покоя"
    const val M_HRV = "вариабельность пульса"
    const val M_RHR_TREND = "пульс покоя, 7 дней"
    const val M_RESP = "частота дыхания"
    const val M_SPO2 = "кислород крови"
    const val M_SKIN = "температура кожи"
    const val M_SELF = "бодрость утром"
    const val SRC_SLEEP = "WHOOP sleep"
    const val SRC_RECOVERY = "WHOOP recovery"
    const val SRC_CHECKIN = "Рассвет"

    // Placeholders of this slice (D-038, D-043).
    const val WEEK_SOON = "Неделя собирается в следующем срезе."
    const val BEHIND = "За плечами"
    const val BEHIND_SOON = "Взято · Несу · Дано — скоро здесь."
    const val ARSENAL_SOON = "Здесь будет то, что ты несёшь с собой: практики, приёмы, свои слова."
    const val BREATHER_LINE = "Пауза. Ты здесь."
    const val BREATHER_REST = "Просто передохнуть"
    const val BREATHER_OFF = "Что-то сбило"
    const val BREATHER_HIDE = "Хочу спрятаться"
    const val BREATHER_EXHALE = "Вдох — и длинный выдох. Отвечать ничего не нужно."
    const val SUPPORT_SOURCE = "Источник"
    const val SUPPORT_NORM = "Норма"
    const val BREATHER_BACK = "Вернуться к дню"
    const val STEPS_GOAL = "цель"

    /** Every user-facing string above, for the stop-dictionary test (T-13). */
    fun all(): List<String> = listOf(
        BLOCK_ME, BLOCK_SUPPORTS, BLOCK_SUPPORTS_HINT, BLOCK_TASKS, BLOCK_RHYTHM, WHY_LINK, CHARGE, CHARGE_NORM, NIGHT, YOU,
        YOU_CAPTION, NO_DATA, ESTIMATE, LEAD_NO_WHOOP, LEAD_NIGHT_PENDING, LEAD_RETURN, LEAD_SAFETY, calibration(3), need("7:40"),
        EFFORT, MOVEMENT, NUTRITION, WATER, SLEEP_TONIGHT, WATER_ADD, WATER_REMOVE, SOURCE_WHOOP_CYCLE, SOURCE_STEPS,
        SOURCE_STEPS_PHONE, SOURCE_MEALS, SOURCE_WATER, SOURCE_SLEEP_PREP, EFFORT_HIDDEN, MEAL_MARK, MEAL_UNMARK,
        COND_CAFFEINE, COND_TRAINING, COND_DINNER, COND_SCREENS, NOW, FULL, MINI, NOT_TODAY, DONE, UNDO, START, OPEN,
        TASK_CONNECT_WHOOP, TASK_START_QUESTIONS, TASK_START_SMALL, TASK_START_SMALL_WHY, TASK_HELP, TASK_DOCTOR,
        TASK_ASSEMBLE_WEEK, TASK_ASSEMBLE_WEEK_WHY, TASK_REMINDER, BACK_TO_WEEK, week(2, 3), DAWN, TRANSITION, CLOSE_DAY,
        LANDING, WEEK_ASSEMBLY, BREATHER, ARSENAL, WIN_COFFEE, WIN_DINNER, WIN_SCREENS, WIN_BEDTIME, WHY_TITLE, WHY_SUMMARY,
        WHY_SLEEP, WHY_HEART, WHY_BREATH, WHY_DEVIATIONS, WHY_YOU, WHY_DISAGREE, WHY_ASK, WHY_NO_DEVIATIONS,
        WHY_MANY_DEVIATIONS, WHY_HELP_HINT, WHY_YOU_MATCH, WHY_YOU_SCISSORS, WHY_YOU_NONE, deviationsCount(3), M_SLEEP,
        M_NEED, M_DEEP, M_REM, M_LIGHT, M_AWAKE, M_EFFICIENCY, M_CONSISTENCY, M_DEBT, M_RHR, M_HRV, M_RHR_TREND, M_RESP,
        M_SPO2, M_SKIN, M_SELF, SRC_SLEEP, SRC_RECOVERY, SRC_CHECKIN, WEEK_SOON, BEHIND, BEHIND_SOON, ARSENAL_SOON,
        BREATHER_LINE, BREATHER_REST, BREATHER_OFF, BREATHER_HIDE, BREATHER_EXHALE, SUPPORT_SOURCE, SUPPORT_NORM,
        BREATHER_BACK, STEPS_GOAL,
    ) + MEALS.map { it.second }
}

/**
 * The A39 stop dictionary (A39 §5 «тест-словарь») plus A46 T-13: words of shame, judgement and
 * diagnosis that no text of «Сегодня» may contain.
 */
object StopWords {
    val WORDS: List<String> = listOf(
        "вред", "нельзя", "срыв", "плохо", "зависим", "должен", "провал", "лень", "лишн", "диагноз", "болезн", "лечени",
        "пропустил", "пропущен", "не выполнил", "невыполн", "героизм",
    )

    fun hits(text: String): List<String> = WORDS.filter { text.lowercase().contains(it) }
}
