package com.healthofatitan.app.feature.today

import com.healthofatitan.app.core.ui.component.HotMode
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Words of the hero facts and the Заряд scale (UX review of build 119: Y2, Y3). They describe the
 * data and the metric (A25), they give no advice about the body and no diagnosis.
 * Review: Agent 12 (copy), Agent 3 (⚑ the lines about sleep stages, resting pulse and Заряд —
 * wording to be confirmed), Agent 10 (tone).
 */
object HeroTexts {
    private val ru: Locale = Locale.forLanguageTag("ru")

    const val STRAIN_MAX = 21.0

    const val SLEEP_TITLE = "Сон этой ночью"
    const val STAGE_DEEP = "Глубокий"
    const val STAGE_REM = "Быстрый (REM)"
    const val STAGE_LIGHT = "Лёгкий"
    const val STAGE_AWAKE = "Без сна"
    const val STAGES_NONE = "Состав сна: нет данных. Фазы приходят из WHOOP при синхронизации — сейчас их нет в памяти."
    const val STAGES_SOURCE = "Фазы — оценка WHOOP по пульсу и движению, не лабораторный замер. Только показываю, в Заряд они не входят."

    const val PULSE_TITLE = "Пульс покоя"
    const val PULSE_WHAT = "Это не пульс сейчас и не цель: WHOOP измеряет его ночью, во сне. Он входит в Заряд."

    const val LOAD_TITLE = "Потолок нагрузки"

    const val CHARGE = "Заряд"
    const val NORM_TICK = "твоя норма — 50"
    const val CHARGE_WHAT = "Заряд — как тело восстановилось за ночь по сравнению с твоей нормой: 50 — обычная для тебя ночь, " +
        "ниже — восстановление слабее обычного, выше — сильнее. Считаю по ВСР (вариабельности пульса), пульсу покоя и дыханию " +
        "этой ночи против твоих последних 28 ночей; это не Recovery % WHOOP и не оценка самочувствия. " +
        "Что повлияло на день — в «Почему такой день» ниже."

    /** «6:48» on the chip. */
    fun sleepChip(hours: Double): String = TitanTexts.hm(hours)

    /** «≤ 12» on the chip: a ceiling, so it reads as «not above». */
    fun loadChip(ceiling: Double): String = "≤ ${TitanTexts.ceiling(ceiling)}"

    fun sleepSpoken(hours: Double) = "Сон ${hm((hours * 60).roundToInt())}"
    fun pulseSpoken(bpm: Int) = "Пульс покоя $bpm ударов в минуту"
    fun loadSpoken(ceiling: Double) = "Потолок нагрузки ${TitanTexts.ceiling(ceiling)} из 21"

    /** «6 ч 48 мин из 8 ч 06 мин по оценке WHOOP» or «6 ч 48 мин». */
    fun sleepLine(hours: Double, needHours: Double?): String {
        val slept = hm((hours * 60).roundToInt())
        return if (needHours != null && needHours > 0) "$slept из ${hm((needHours * 60).roundToInt())} по оценке WHOOP" else slept
    }

    fun pulseLine(bpm: Int) = "$bpm ударов в минуту — ночью, в покое."

    /** «12 из 21 по шкале strain WHOOP. Сейчас — 0,3.» */
    fun loadLine(ceiling: Double, strain: Double?): String =
        "${TitanTexts.ceiling(ceiling)} по шкале strain WHOOP (0–21)." + (strain?.let { " Сейчас — ${String.format(ru, "%.1f", it)}." } ?: "")

    fun loadWhat(mode: HotMode): String =
        "Strain — нагрузка дня по пульсу: тренировки, ходьба, всё вместе. Режим «${mode.word}» строит день ниже этой отметки."

    fun zone(zone: ChargeZone): String = when (zone) {
        ChargeZone.LOW -> "ниже твоей нормы"
        ChargeZone.NORM -> "около твоей нормы"
        ChargeZone.HIGH -> "выше твоей нормы"
    }

    fun chargeSpoken(charge: Int) = "Заряд $charge, ${zone(ChargeZone.of(charge))}, твоя норма — 50"

    /** «6 ч 48 мин», «41 мин», «2 ч». */
    fun hm(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0 -> "$m мин"
            m == 0 -> "$h ч"
            else -> "$h ч ${m.toString().padStart(2, '0')} мин"
        }
    }
}
