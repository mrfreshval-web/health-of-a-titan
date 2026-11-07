package com.healthofatitan.app.feature.path

import com.healthofatitan.app.core.model.mentor.MentorPersona
import com.healthofatitan.app.domain.path.GoalPlan
import com.healthofatitan.app.domain.path.Sphere
import com.healthofatitan.app.domain.path.StepVersion

/**
 * Texts of the tab «Путь» and of the step of the day (A42 §3). Process only: no weight, no looks,
 * no «пропустил», no counters of missed days (A42 §3.6).
 *
 * TODO(review: Agent 10 — tone, A42 §3.6; Agent 3 — step sizes of the body goal): draft texts.
 */
object PathTexts {
    const val TAB = "Путь"
    const val TITAN = "Твой Титан"
    const val GOAL_OVERLINE = "Цель на 12 недель"
    const val TODAY_OVERLINE = "Сегодня к цели"
    const val TAKE = "Беру"
    const val OTHER = "Другая"
    const val KEEP = "Оставить как есть"
    const val CHANGE = "Поменять цель"
    const val NEW_GOAL = "Новая цель"
    const val PAST = "Прошлые цели"
    const val NO_DATA_NOW = "сейчас — учусь твоей норме"
    const val CYCLE_DONE = "Двенадцать недель позади."
    const val NO_GOAL_TODAY = "Цель на 12 недель"
    const val NO_GOAL_TODAY_CAPTION = "Одна цель из твоих данных — один тап"
    const val MINIMAL_COUNTS = "минимум — тоже шаг"

    fun sphere(s: Sphere): String = when (s) {
        Sphere.MIND -> "Разум"
        Sphere.WORK -> "Работа"
        Sphere.BONDS -> "Связи"
        Sphere.FOOD -> "Еда"
        Sphere.BODY -> "Тело"
        Sphere.SLEEP -> "Сон"
    }

    /** «Сон 7 ч · 5 из 7». */
    fun goal(plan: GoalPlan, days: Int = plan.target): String = when (plan.sphere) {
        Sphere.SLEEP -> "Сон 7 ч · $days из 7"
        Sphere.BODY -> "Тренировка · $days из 7"
        else -> "${sphere(plan.sphere)} · $days из 7"
    }

    /** «этап 2 из 3 · неделя 6 из 12». */
    fun where(stage: Int, week: Int): String = "этап $stage из 3 · неделя $week из 12"

    fun now(plan: GoalPlan): String = plan.baseline?.let { "сейчас $it из 7" } ?: NO_DATA_NOW

    /** «4 → 5 → 5 из 7». */
    fun stages(plan: GoalPlan): String = plan.stages.joinToString(" → ") + " из 7"

    fun week(done: Int, target: Int): String = "$done/$target"

    fun weekA11y(done: Int, target: Int): String = "Цель недели: $done из $target"

    fun inLight(lit: Int): String = "$lit из 6 в свете"

    fun litWeeks(n: Int): String = "недель в свете: $n"

    /** The step of the day by the mode (A42 §3.3): Разгон full, Ровно normal, Бережно minimal. */
    fun stepTitle(sphere: Sphere, version: StepVersion): String = when (sphere) {
        Sphere.SLEEP -> if (version == StepVersion.MINIMAL) "Лечь вовремя" else "Сон 7 часов"
        Sphere.BODY -> when (version) {
            StepVersion.FULL -> "Тренировка 45 минут"
            StepVersion.NORMAL -> "Тренировка 30 минут"
            StepVersion.MINIMAL -> "Прогулка 20 минут"
        }
        else -> sphere(sphere)
    }

    fun stepDetail(sphere: Sphere, version: StepVersion, bedtimeMin: Int?): String? {
        val bed = bedtimeMin?.let { "отбой в ${clock(it)}" }
        return when {
            version == StepVersion.MINIMAL -> listOfNotNull(bed.takeIf { sphere == Sphere.SLEEP }, MINIMAL_COUNTS).joinToString(" · ")
            sphere == Sphere.SLEEP -> bed
            sphere == Sphere.BODY -> if (version == StepVersion.FULL) "можно с интервалами" else "в своём темпе"
            else -> null
        }
    }

    fun clock(min: Int): String {
        val m = ((min % 1440) + 1440) % 1440
        return "%02d:%02d".format(m / 60, m % 60)
    }
}

/**
 * The mentor's characters for the choice by a preview line (A38 §9). The lines are taken
 * verbatim from A38 (§1, §7.1 «утро после плохого сна»): there is no content/mentor yet.
 *
 * TODO(review: Agent 10): A38 is a draft under review; move these lines to content/mentor when
 * it appears (A38 §10.1) and replace from there.
 */
object PersonaTexts {
    const val TITLE = "Наставник"
    const val SITUATION = "Утро после короткой ночи"
    const val NOTE = "Характер меняет манеру речи — не план и не безопасность."
    const val STRONG_WORDS = "Крепкие слова"
    const val STRONG_WORDS_OFF = "выключено · ждёт решения и согласия 18+"

    fun essence(p: MentorPersona): String = when (p) {
        MentorPersona.SENSEI -> "строгий учитель с чистой и красивой речью"
        MentorPersona.BRO -> "свой парень, прямой, с крепким словом"
        MentorPersona.BUTLER -> "безупречная вежливость и сухой юмор"
    }

    fun preview(p: MentorPersona): String = when (p) {
        MentorPersona.SENSEI ->
            "«Четыре часа сорок. Тело просит тишины — дай ему её. Интервалы снимаем: сорок минут ходьбы, вода, отбой в 22:30. Исполни.»"
        MentorPersona.BRO ->
            "«Так, братан, 4:40 — это не сон, это перекур. Интервалы сегодня в топку, идём гулять сорок минут. И кофе после двух даже не нюхай.»"
        MentorPersona.BUTLER ->
            "«Доброе утро, Валентин. Ночь выдалась, скажем так, лаконичной: четыре часа сорок минут. Я взял на себя смелость заменить интервалы прогулкой на сорок минут.»"
    }
}
