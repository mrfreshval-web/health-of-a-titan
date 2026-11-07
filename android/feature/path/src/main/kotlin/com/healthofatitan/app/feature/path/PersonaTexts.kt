package com.healthofatitan.app.feature.path

import com.healthofatitan.app.core.model.mentor.MentorPersona


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
            "«Доброе утро. Ночь выдалась, скажем так, лаконичной: четыре часа сорок минут. Я взял на себя смелость заменить интервалы прогулкой на сорок минут.»"
    }
}
