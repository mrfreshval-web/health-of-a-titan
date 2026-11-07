package com.healthofatitan.app.domain.training

/**
 * «Почему так» lines of the training configurator (A40 §4.2 step 8). Neutral technical wording
 * about what the rule did; no advice about health. The wording of gates TG-01…TG-07 is a
 * placeholder until Agents 3 and 10 review it (rules.yaml `reviewedBy: []`).
 *
 * TODO(review: Agents 3, 10, 12): wording of every line below.
 */
object TrainingWhy {
    const val EMERGENCY = "Сегодня без тренировки."
    const val STOP_SIGNAL = "Тренировки на паузе до отметки «врач разрешил»."
    const val DOCTOR = "Тренировки на паузе до консультации."
    const val NO_PROGRAM = "Для этого профиля программы тренировок в HoT нет."
    const val SICK = "Отмечено недомогание: только восстановление до 20 минут."
    const val SEE_SPECIALIST = "До консультации — только мобильность."
    const val SCREENING = "Щадящий режим Классики: RPE до 6, без тяжёлого веса и натуживания."
    const val ED_CARE = "Без чипперов и лишних сессий."
    const val LIGHT_WEEK = "Лёгкая неделя: объём меньше на 40 %."
    const val TRAVEL = "В дороге: ровная версия, без интервалов."
    const val CLASSIC_NO_INTERVALS = "В Классике нет интервалов — вместо них ровное кардио."
    const val MOBILITY_KEPT = "Разминка и растяжка остаются в любой версии."

    fun full(mode: DayMode) = "${mode.ru}: полная версия шаблона."

    fun lighter(mode: DayMode) = "${mode.ru}: облегчённая версия — основная часть короче, разминка и растяжка те же."

    fun swapped(mode: DayMode, from: String, to: String) = "${mode.ru}: вместо «$from» сегодня «$to»."

    fun replaced(n: Int) = "Под твой профиль заменено упражнений: $n."
}
