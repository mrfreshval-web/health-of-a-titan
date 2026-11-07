package com.healthofatitan.app.domain.today.week

/**
 * Every new word of «Неделя» (A52 v0.1, D-044, D-045) in one place.
 *
 * **Ждёт ревью 3, 10, 16** (медицинский смысл, психика, редактура): the wording is a working draft
 * of the developers. Nothing medical is invented here: a number says where it comes from, a skew
 * is «факт + что можно сделать», never a verdict. The A39 stop dictionary and the W-02 words
 * («пропуск», «провал», «серия») are run over all of it in a test.
 */
object WeekCopy {
    const val REVIEW = "ждёт ревью 3, 10, 16"

    // Blocks (A52 §1).
    const val BLOCK_GLANCE = "Неделя одним взглядом"
    const val BLOCK_GOALS = "Цели недели"
    const val BLOCK_SUPPORTS = "Опоры за неделю"
    const val BLOCK_BODY = "Тело за неделю"
    const val BLOCK_PATTERNS = "Закономерности"
    const val BLOCK_RITUAL = "Итог и сборка"

    fun weekNumber(n: Int): String = "неделя $n"

    // The main number (A52 §2.1).
    const val DAYS_IN_SUPPORT = "Дни в опоре"
    const val GOALS_TOTAL = "Цели недели"
    const val DAY_IN_SUPPORT = "день в опоре"
    const val DAY_REST = "день отдыха"
    const val DAY_FUTURE = "впереди"
    const val DAY_NO_DATA = "данных нет"
    const val DAY_TODAY = "сегодня"
    const val SUPPORTS_FILLED = "опор наполнено"
    const val TASKS_DONE = "дел сделано"
    const val READ_ONLY = "только для чтения"
    const val WEEKDAYS = "пн вт ср чт пт сб вс"

    fun ofTotal(n: Int, total: Int): String = "$n из $total"

    fun markHint(mark: Int): String = "метка $mark"

    const val SPOKEN_COLUMNS = "Дни недели с понедельника по воскресенье"

    // Goals (A52 §2.2).
    const val GOAL_TRAINING = "Тренировки"
    const val GOAL_FOCUS = "Фокус-блоки"
    const val GOAL_ZONES = "Минуты в зонах"
    const val GOAL_SLEEP = "Шаг пути: сон по потребности"
    const val GOAL_REMINDER = "Напоминание"
    const val UNIT_TIMES = "раз"
    const val UNIT_MINUTES = "мин"
    const val UNIT_NIGHTS = "ночей"
    const val UNIT_DAYS = "дней"
    const val GOAL_CHANGE = "Поменять"
    const val GOAL_LIGHTER = "Полегче"
    const val GOAL_MORE = "Чуть больше"
    const val GOAL_REMOVE = "Снять с недели"
    const val GOAL_KEEP = "Оставить"
    const val GOAL_NO_PENALTY = "Поменять можно в любой момент — ничего не сгорает."
    const val GOAL_SUGGEST_UP = "Неделя далась легко — можно на шаг больше. Решаешь ты."
    const val GOAL_SUGGEST_DOWN = "Следующая неделя на шаг легче: так держится ритм."
    const val GOAL_FIRST_WEEK = "Первая неделя: цели небольшие, чтобы присмотреться."
    const val GOAL_PAUSED = "Цели на паузе, пока ты восстанавливаешься."
    const val GOAL_AUTO = "Собрал сам — поправь, если нужно"
    const val GOAL_PROPOSED = "Предложение на неделю — одобри в «Итоге и сборке»"
    const val GOAL_EMPTY = "Цели появятся после первой сборки недели."
    const val GOAL_RETURNED = "вернулось в неделю"
    const val REMINDER_ADD = "+ напоминание как дело"
    const val REMINDER_HINT = "Напоминание станет делом дня в нужные дни."
    const val REMINDER_TITLE = "Что напомнить"
    const val REMINDER_DAYS = "Сколько дней в неделю"
    const val SAVE = "Сохранить"
    const val CANCEL = "Отмена"

    fun minutes(done: Int, target: Int): String = "$done / $target"

    // Supports of the week (A52 §2.3).
    const val S_BALANCE = "усердие ↔ восстановление"
    const val S_MOVEMENT = "движение"
    const val S_NUTRITION = "питание"
    const val S_DRINK = "питьё"
    const val S_SLEEP = "сон"
    const val S_WEIGHT = "вес"
    const val S_NO_DATA = "данных пока мало"

    fun balanceDays(n: Int, total: Int): String = "в балансе $n дн. из $total"

    fun stepsDays(n: Int, total: Int): String = "норма шагов $n/$total"

    fun zones(min: Int): String = "минуты в зонах $min из 150–300"

    fun mealsDays(n: Int, total: Int): String = "дни по плану $n/$total"

    fun drinkPct(pct: Int): String = "$pct % нормы, в среднем"

    fun sleepDebt(text: String): String = "долг $text"

    fun regularity(pct: Int): String = "регулярность $pct %"

    fun nightsAtNeed(n: Int, total: Int): String = "ночей по потребности $n/$total"

    fun weightAvg(text: String): String = "в среднем $text кг, тренд недели"

    const val WEIGHT_NO_DATA = "появится с весами в Health Connect"

    // Skews (A52 §2.3): a fact and what can be done, never a verdict.
    const val SKEW_PILING = "Нагрузка копится три дня и больше — можно вставить лёгкий день."
    const val SKEW_UNDERLOAD = "Нагрузки меньше, чем позволяет Заряд, — можно добавить движения."
    const val SKEW_STEPS = "Норма шагов пока в паре дней — короткая прогулка после еды добавит."
    const val SKEW_PROTEIN = "Белка меньше привычного — можно добавить его к завтраку."
    const val SKEW_DRINK = "Питья меньше половины нормы — стакан к каждому приёму пищи поможет."
    const val SKEW_DEBT = "Долг сна накопился — отбой на полчаса раньше пару вечеров вернёт его."
    const val SKEW_REGULARITY = "Отбой гуляет — одно и то же время подъёма выравнивает сон."
    const val SKEW_HIDDEN = "Неделя на восстановление — это тоже работа."

    // Body of the week (A52 §2.4). Names as «Почему так» has them; texts — Agent 3.
    const val B_RHR = "пульс покоя"
    const val B_HRV = "вариабельность пульса"
    const val B_RESP = "частота дыхания"
    const val B_SPO2 = "кислород крови"
    const val B_SKIN = "температура кожи"
    const val B_CORRIDOR = "к твоему коридору 30 дней"
    const val B_UP = "выше коридора"
    const val B_DOWN = "ниже коридора"
    const val B_SAME = "в коридоре"
    const val B_FLAG = "Пульс покоя третью ночь заметно выше твоего обычного. Если самочувствие другое, чем всегда, — «Помощь» рядом."
    const val B_HELP = "Помощь"
    const val B_NO_DATA = "Подключи WHOOP — здесь появятся пять сигналов тела за неделю."

    // Patterns (A52 §2.5).
    const val PATTERN_LINK = "связь, не причина"
    const val PATTERN_TRY = "Попробовать на неделе"
    const val PATTERN_TAKEN = "В целях следующей недели"
    const val PATTERN_EMPTY = "Соберу к следующему воскресенью."

    fun evidence(n: Int): String = "наблюдений: $n"

    // The ritual (A52 §2.6).
    const val RITUAL_CARD = "Итог и сборка · 4 мин"
    const val RITUAL_OPEN_UNTIL = "Можно до вторника."
    const val RITUAL_DONE = "Неделя собрана"
    const val R_FRAME = "Неделя одним кадром"
    const val R_WORKED = "Что сработало"
    const val R_WORK = "Работа"
    const val R_PRIDE_Q = "Чем гордишься на этой неделе?"
    const val R_PRIDE_HINT = "уйдёт в «За плечами · Взято»"
    const val R_CARRY_Q = "Что тянется?"
    const val R_CARRY_HINT = "можно оставить в «Несу» или взять в цель недели"
    const val R_CARRY_TO_GOAL = "В цель недели"
    const val R_CARRY_KEEP = "Оставить в «Несу»"
    const val R_SKEW = "Одно предложение"
    const val R_TAKE = "Беру"
    const val R_AS_IS = "Как есть"
    const val R_EASIER = "Полегче"
    const val R_NEXT = "Цели следующей недели"
    const val R_FORWARD = "Вперёд"
    const val R_APPROVE = "Собрать неделю"
    const val R_NEXT_STEP = "Дальше"
    const val R_WORKED_NONE = "Неделя шла своим ходом — победа уже в том, что ты здесь."

    fun beforeAfter(before: Int, after: Int): String = "дни в опоре: было $before → стало $after"

    // «Скульптура недели» (A53 §3.1, D-050) — the hero; MVP: the Week Seal, then a static frame, then the reveal.
    const val TITAN = "Скульптура недели"
    const val TITAN_FORMING = "Высекается день за днём"
    const val TITAN_READY = "Твоя неделя высечена"
    const val TITAN_REVEAL = "Раскрыть"
    const val TITAN_YEAR = "Фриз года"
    const val TITAN_SHARE = "Поделиться"
    const val SEAL = "Печать недели"
    const val SEAL_REVERSE = "опоры недели"
    const val SHARE_TITLE = "Печать недели"
    const val SHARE_SLOGAN = "Держи своё небо"
    const val PUSH_TITLE = "Твоя неделя высечена"
    const val PUSH_TEXT = "Итог и сборка — пять минут, когда удобно."
    const val PUSH_CHANNEL = "Неделя"
    const val PUSH_CHANNEL_DESCRIPTION = "Одно сообщение в воскресенье — Скульптура недели"
    const val PUSH_PUBLIC = "HoT"
    const val NEW_PAGE = "Новая страница — свежая глыба на Неделе"
    const val TEXTURE_POLISHED = "полированный камень"
    const val TEXTURE_MATTE = "матовый камень"

    /** «Что значит эта поза» — under the figure on the first two Sundays (A53 §3.1). */
    fun poseMeaning(pose: String): String = "Поза недели: $pose. Её выбирают дни в опоре; сколько высечено, зависит только от дня недели."

    fun carvedSpoken(days: Int): String = "высечено $days из 7"

    // Poses (placeholder words until Agents 4 and 17).
    const val POSE_REST = "отдыхает у колонны"
    const val POSE_STAND = "стоит ровно"
    const val POSE_HOLD = "держит небо"
    const val POSE_RISE = "поднимает свод"
    const val SKY_CLEAR = "ясное небо"
    const val SKY_HAZE = "лёгкая дымка"
    const val SKY_DUSK = "мягкие сумерки"
    const val DETAIL_PRIDE = "твоя гордость недели"
    const val DETAIL_GOALS = "все цели недели"
    const val DETAIL_SLEEP = "неделя без долга сна"
    const val DETAIL_FOCUS = "рекорд фокуса"
    const val DETAIL_MOVE = "неделя в движении"

    // «Фитнес-возраст» (A53 §3.3, D-050): milestones every 4 weeks, by choice, behind a flag until Agents 3 and 10.
    const val FIT_TITLE = "Фитнес-возраст"
    // A52 §2.8 asks for «оценка, не диагноз»; the A39 dictionary bans «диагноз» — Agent 3 decides.
    const val FIT_NOTE = "оценка кардиофитнеса по модели HUNT, не медицинский вывод"
    const val FIT_DRIVERS = "что двигает число: пульс покоя, тренировки, талия"
    const val FIT_ROOM = "Есть куда расти — двигают пульс покоя, тренировки и талия."
    const val FIT_NEED_INPUT = "Для оценки нужны возраст, пол и обхват талии."
    const val FIT_WAIST = "Обхват талии, см"
    const val FIT_AGE = "Возраст, лет"
    const val FIT_SEX = "Пол"
    const val FIT_MALE = "мужской"
    const val FIT_FEMALE = "женский"
    const val FIT_WAIST_DUE = "Пора обновить обхват талии — раз в 4 недели."
    const val FIT_OPT_IN = "Показывать фитнес-возраст на вехах"
    const val FIT_OPT_OUT = "Скрыть"
    const val FIT_WHAT = "Что это и чего не значит"
    const val FIT_WHAT_TEXT = "Оценка выносливости сердца и лёгких по модели HUNT: пульс покоя, тренировки, талия, возраст и пол. " +
        "Это ориентир для темпа, не оценка здоровья и не повод для тревоги. Обновляется раз в 4 недели, на вехе."
    const val FIT_FIRST_AFTER = "Первая веха — после 4 недель данных."
    const val FIT_LEVER_RHR = "пульс покоя"
    const val FIT_LEVER_ACTIVITY = "тренировки"
    const val FIT_LEVER_WAIST = "талия"

    fun fitPace(text: String): String = "за 4 недели $text"

    fun fitRange(age: Int): String = "${age - 2}–${age + 2}"

    fun fitLever(lever: String): String = "помогли: $lever"

    fun fitMilestone(n: Int): String = "веха $n"

    // «Эксперимент недели» (A53 §3.2, D-050).
    const val EXP_TITLE = "Эксперимент недели"
    const val EXP_LEARNED = "Узнали"
    const val EXP_TOO_FEW = "Данных мало — повторим?"
    const val EXP_NO_LINK = "Похоже, заметной связи нет — и это тоже знание о себе."
    const val EXP_LINK = "связь, не причина"
    const val EXP_TAKE = "Беру"
    const val EXP_CHANGE = "Другой"
    const val EXP_SKIP = "Не сейчас"
    const val EXP_REPEAT = "Повторить"
    const val EXP_DID_TODAY = "Сегодня сделал"
    const val EXP_NONE = "Эксперимента на этой неделе нет — неделя для восстановления."
    const val EXP_OFFER = "Проверим на себе одну вещь?"
    const val EXP_RESULT = "Итог эксперимента"
    const val EXP_M_SLEEP = "сон"
    const val EXP_M_CHARGE = "Заряд"
    const val EXP_M_PULSE = "пульс покоя"
    const val EXP_U_MIN = "мин"
    const val EXP_U_POINTS = "п."
    const val EXP_U_BPM = "уд/мин"

    fun expHypothesis(action: String, metric: ExperimentMetric): String =
        "$action → ${metric.words} ${if (metric.higherIsBetter) "лучше" else "спокойнее"}?"

    fun expBridge(done: Int, goal: Int): String = "Эксперимент · $done из $goal ›"

    fun expYes(shift: String, days: Int, of: Int): String = "Похоже, да: $shift, $days дн. из $of."

    fun expOpposite(metric: ExperimentMetric): String = "Похоже, у тебя это работает иначе: ${metric.words} не лучше. Полезно знать."

    /** Every user-facing string above, for the stop-dictionary test. */
    fun all(): List<String> = listOf(
        BLOCK_GLANCE, BLOCK_GOALS, BLOCK_SUPPORTS, BLOCK_BODY, BLOCK_PATTERNS, BLOCK_RITUAL, weekNumber(45),
        DAYS_IN_SUPPORT, GOALS_TOTAL, DAY_IN_SUPPORT, DAY_REST, DAY_FUTURE, DAY_NO_DATA, DAY_TODAY, SUPPORTS_FILLED, TASKS_DONE,
        READ_ONLY, WEEKDAYS, ofTotal(3, 7), markHint(4), SPOKEN_COLUMNS,
        GOAL_TRAINING, GOAL_FOCUS, GOAL_ZONES, GOAL_SLEEP, GOAL_REMINDER, UNIT_TIMES, UNIT_MINUTES, UNIT_NIGHTS, UNIT_DAYS,
        GOAL_CHANGE, GOAL_LIGHTER, GOAL_MORE, GOAL_REMOVE, GOAL_KEEP, GOAL_NO_PENALTY, GOAL_SUGGEST_UP, GOAL_SUGGEST_DOWN,
        GOAL_FIRST_WEEK, GOAL_PAUSED, GOAL_AUTO, GOAL_PROPOSED, GOAL_EMPTY, GOAL_RETURNED, REMINDER_ADD, REMINDER_HINT,
        REMINDER_TITLE, REMINDER_DAYS, SAVE, CANCEL, minutes(90, 150),
        S_BALANCE, S_MOVEMENT, S_NUTRITION, S_DRINK, S_SLEEP, S_WEIGHT, S_NO_DATA, balanceDays(4, 5), stepsDays(3, 5), zones(90),
        mealsDays(4, 5), drinkPct(74), sleepDebt("3:40"), regularity(71), nightsAtNeed(3, 5), weightAvg("80,4"), WEIGHT_NO_DATA,
        SKEW_PILING, SKEW_UNDERLOAD, SKEW_STEPS, SKEW_PROTEIN, SKEW_DRINK, SKEW_DEBT, SKEW_REGULARITY, SKEW_HIDDEN,
        B_RHR, B_HRV, B_RESP, B_SPO2, B_SKIN, B_CORRIDOR, B_UP, B_DOWN, B_SAME, B_FLAG, B_HELP, B_NO_DATA,
        PATTERN_LINK, PATTERN_TRY, PATTERN_TAKEN, PATTERN_EMPTY, evidence(6),
        RITUAL_CARD, RITUAL_OPEN_UNTIL, RITUAL_DONE, R_FRAME, R_WORKED, R_WORK, R_PRIDE_Q, R_PRIDE_HINT, R_CARRY_Q, R_CARRY_HINT,
        R_CARRY_TO_GOAL, R_CARRY_KEEP, R_SKEW, R_TAKE, R_AS_IS, R_EASIER, R_NEXT, R_FORWARD, R_APPROVE, R_NEXT_STEP, R_WORKED_NONE,
        beforeAfter(3, 4),
        TITAN, TITAN_FORMING, TITAN_READY, TITAN_REVEAL, TITAN_YEAR, TITAN_SHARE, SHARE_TITLE, SHARE_SLOGAN, PUSH_TITLE, PUSH_TEXT,
        PUSH_CHANNEL, PUSH_CHANNEL_DESCRIPTION, PUSH_PUBLIC, POSE_REST, POSE_STAND, POSE_HOLD, POSE_RISE, SKY_CLEAR, SKY_HAZE,
        SKY_DUSK, DETAIL_PRIDE, DETAIL_GOALS, DETAIL_SLEEP, DETAIL_FOCUS, DETAIL_MOVE,
        FIT_TITLE, FIT_NOTE, FIT_DRIVERS, FIT_ROOM, FIT_NEED_INPUT, FIT_WAIST, FIT_AGE, FIT_SEX, FIT_MALE, FIT_FEMALE,
        FIT_WAIST_DUE, fitPace("−0,6 года"), SEAL, SEAL_REVERSE, NEW_PAGE, TEXTURE_POLISHED, TEXTURE_MATTE, poseMeaning(POSE_HOLD),
        carvedSpoken(4), FIT_OPT_IN, FIT_OPT_OUT, FIT_WHAT, FIT_WHAT_TEXT, FIT_FIRST_AFTER, FIT_LEVER_RHR, FIT_LEVER_ACTIVITY,
        FIT_LEVER_WAIST, fitRange(41), fitLever(FIT_LEVER_RHR), fitMilestone(2),
        EXP_TITLE, EXP_LEARNED, EXP_TOO_FEW, EXP_NO_LINK, EXP_LINK, EXP_TAKE, EXP_CHANGE, EXP_SKIP, EXP_REPEAT, EXP_DID_TODAY, EXP_NONE,
        EXP_OFFER, EXP_RESULT, expBridge(3, 5), expYes("+18 мин", 5, 6),
    ) + ExperimentMetric.entries.flatMap { listOf(expHypothesis("Экран убран в 22:30", it), expOpposite(it)) } + PatternTexts.all() + RitualRules.SKEW_TITLES.values
}

/**
 * W-02: words that never appear on «Неделя» (A52 §5, A39 §5). On top of the A39 dictionary of
 * «Сегодня» ([com.healthofatitan.app.domain.today.StopWords]).
 */
object WeekStopWords {
    val WORDS: List<String> = listOf("пропуск", "пропущ", "провал", "серия", "серии", "подряд дней", "штраф", "сгорел")

    fun hits(text: String): List<String> =
        com.healthofatitan.app.domain.today.StopWords.hits(text) + WORDS.filter { text.lowercase().contains(it) }
}
