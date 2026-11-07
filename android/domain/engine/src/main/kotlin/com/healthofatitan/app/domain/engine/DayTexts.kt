package com.healthofatitan.app.domain.engine

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Texts of the day plan: «Почему так» one line per rule, item titles, the push. Short on purpose
 * (the founder asks for little text), «ты», gender-neutral, no diagnoses and no medical claims, no
 * «вред / нельзя / срыв / плохо / должен», no words about the body's looks (A07, A09 §9, A37 §4).
 * Reviewed in A39 by Agents 3, 10, 12 and 16; wording about doctors ⚑7.
 */
object DayTexts {

    fun time(min: Int): String {
        val m = Math.floorMod(min, 24 * 60)
        return "%02d:%02d".format(m / 60, m % 60)
    }

    fun duration(hours: Double): String {
        val minutes = (hours * 60).roundToInt()
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0 -> "$m мин"
            m == 0 -> "$h ч"
            else -> "$h ч $m мин"
        }
    }

    private fun minutesText(min: Int) = duration(min / 60.0)

    // --- Regime (REG) ---------------------------------------------------------------------

    /** REG-02: what the points were made of. The regime itself is on the card, not repeated. */
    fun signalsWhy(signals: Set<String>, night: NightFacts, regime: Regime): String {
        val parts = buildList {
            if (Signals.SLEEP in signals) add(sleepSignal(night))
            if (Signals.ORS_DAY in signals) add("восстановление ниже твоего обычного")
            if (Signals.ORS_TREND in signals) add("неделя ниже обычного")
            if (Signals.LOAD in signals) add("нагрузка копится")
            if (Signals.SELF in signals) add("самочувствие ниже обычного")
        }
        if (parts.isEmpty()) {
            return if (regime == Regime.BOOST) "Сон и восстановление выше обычного — есть запас." else "Сигналов усталости нет."
        }
        return parts.joinToString(" · ").replaceFirstChar { it.uppercase() } + "."
    }

    private fun sleepSignal(n: NightFacts): String {
        val h = n.sleepHours
        val prev = n.prevSleepHours
        return when {
            h != null && h < 6.0 -> "сон ${duration(h)}"
            h != null && prev != null && h < 7.0 && prev < 7.0 -> "вторая ночь короче 7 ч"
            else -> "недосып за неделю ${n.sleepDebt7h?.let(::duration) ?: ""}".trim()
        }
    }

    fun hardCapSleep(hours: Double) = "Сон ${duration(hours)} — меньше 5 ч. Сегодня Бережно, даже если бодро."
    const val HARD_CAP_SICK = "Пока простуда — только Бережно."
    const val SELF_VS_DATA = "Данные просят отдыха, а самочувствие хорошее: Ровно, без интервалов."
    fun stepUp(yesterday: Regime) = "Вчера было ${yesterday.ru}: поднимаемся на одну ступень."
    const val HYSTERESIS = "Восстановление ещё не вернулось к обычному — держу осторожность."
    const val CALIBRATION = "Изучаю твою норму: нужно около 14 ночей. Пока без Разгона."
    fun preliminary(days: Int) = "Норма ещё складывается ($days дн. из 28): Разгон позже."
    const val LOW_COVERAGE = "Ночью браслет записал не всё — восстановление не оцениваю."
    const val MANUAL = "WHOOP ещё не посчитал ночь — день собран по умолчанию, уточню позже."
    const val LIGHT_WEEK_GENTLE = "Третий бережный день — лёгкая неделя: легче на 40 %, без интервалов."
    const val LIGHT_WEEK_LOAD = "Нагрузка копится неделю — лёгкая неделя: легче на 40 %, без интервалов."
    fun lightWeekDay(day: Int) = "Лёгкая неделя, день $day из 7: легче на 40 %."

    // --- Safety and context (SAF, CTX, EVE) --------------------------------------------------

    const val SICK = "Отмечена простуда: без нагрузки, по желанию — короткая прогулка."

    /** SAF-03 see_doctor (⚑7: wording about doctors). */
    const val SICK_SEE_DOCTOR = "Третий день простуды. Если не легчает или стало хуже — покажись врачу."

    /** SAF-04: a question, not a verdict; no illness names (⚑7). */
    const val PHYSIO_SHIFT = "Ночью пульс и дыхание выше твоего обычного. Сегодня без интервалов — прислушайся к себе."

    /** CTX-01. Only inside the app, never in the push (S4, A09 §9). Information, not a verdict. */
    fun alcohol(servings: Int, lowRecovery: Boolean, lighter: Boolean): String {
        val n = if (servings >= 4) "4+" else servings.toString()
        val head = if (lowRecovery) {
            "Вчера $n порции — у многих после этого ночь восстанавливает слабее. Это не оценка."
        } else {
            "Вчера $n порции."
        }
        val tail = when {
            lighter -> " Вода, завтрак с белком, тренировка легче."
            lowRecovery -> " Вода, завтрак с белком, без интервалов."
            else -> " Вода и завтрак с белком."
        }
        return head + tail
    }

    const val HEAVY_YESTERDAY = "Вчера была тяжёлая тренировка — тело перестраивается. Сегодня без интервалов."

    fun travel(shiftHours: Double, firstDay: Boolean): String {
        val h = abs(shiftHours).roundToInt()
        val sign = if (shiftHours > 0) "+" else "−"
        val tail = if (firstDay) " Первый день — без интервалов." else ""
        return "Сдвиг $sign$h ч от дома: день по местному времени.$tail"
    }

    const val SORE = "Тело забито — тренировка легче, без интервалов."

    // --- Day rules (SLP, NUT, ACT, WRK) ------------------------------------------------------

    fun bedtime(bedtime: Int, wakeTomorrow: Int, source: AnchorSource, needMin: Int?, shifted: Boolean): String {
        val base = if (needMin != null) {
            "к подъёму в ${time(wakeTomorrow)} (${source.ru}) выйдет ${minutesText(needMin)} сна"
        } else {
            "за 8 ч до подъёма в ${time(wakeTomorrow)} (${source.ru})"
        }
        val prefix = if (shifted) "На 30 мин раньше обычного. " else ""
        return "${prefix}Отбой ${time(bedtime)}: $base."
    }

    fun caffeine(cutoff: Int) = "Кофе до ${time(cutoff)} — за 9 ч до отбоя."
    const val LIGHT = "Дневной свет в первый час после подъёма — вечером легче уснуть."
    fun nap(hours: Double) = "Ночь ${duration(hours)}: если выйдет, короткий сон 10–20 мин до 15:00. Второй кофе ночь не вернёт."
    fun dinner(end: Int) = "Ужин до ${time(end)} — за 3 ч до сна."
    const val WALK = "Бережный день: 20–30 мин спокойной ходьбы — для настроения и сна."
    fun trainingSlot(start: Int, fromHistory: Boolean) =
        if (fromHistory) "Тренировка в ${time(start)} — твоё обычное время." else "Обычного времени тренировки пока нет — ставлю ${time(start)}."
    const val LATE_INTENSE = "Окно позднее: интенсив заканчиваем за 2 ч до сна, поэтому спокойнее."
    const val FOCUS = "Главная задача — в первые часы после подъёма."
    const val ONE_TASK = "Одна главная задача, решения — до обеда."
    const val SHORT_NIGHT_TRAINING = "Сон меньше 6 ч: без интервалов, силовая легче."

    // --- A19 weekly queue ----------------------------------------------------------------------

    fun queueStrength(strengthThisWeek: Int) = "Силовых на неделе $strengthThisWeek из 2 — сегодня силовая."
    const val QUEUE_STRENGTH_GAP = "Силовой не было 3 дня — сегодня силовая."
    const val QUEUE_INTERVALS = "Разгон, интервалов на неделе меньше двух — сегодня интервалы."
    fun queueZone2(weekMin: Int) = "Кардио на неделе $weekMin из 150 мин — сегодня Zone 2."
    const val QUEUE_DEFAULT = "Неделя идёт по плану: силовая через день, между ними Zone 2."
    const val QUEUE_REST = "Шесть дней подряд с тренировкой — сегодня восстановление."
    fun replaced(from: TrainingType, to: TrainingType) = "По плану недели — ${from.ru}, сегодня вместо этого ${to.ru}."

    // --- Items ---------------------------------------------------------------------------------

    const val ITEM_WATER = "Стакан воды"
    const val ITEM_LIGHT = "Дневной свет 10–20 мин"
    const val ITEM_LIGHT_LOCAL = "Свет по местному времени"
    const val ITEM_BREAKFAST = "Завтрак с белком"
    const val ITEM_NAP = "Короткий сон"
    const val ITEM_FOCUS = "Главная задача"
    const val ITEM_DINNER = "Ужин"
    const val ITEM_BEDTIME = "Отбой"

    fun focusDetail(blocks: Int, blockMin: Int) = if (blocks > 1) "$blocks блока по $blockMin мин" else "$blockMin мин, одна задача"

    fun trainingTitle(c: TrainingChoice): String = when (c.type) {
        TrainingType.REST -> TrainingType.REST.ru
        else -> "${c.type.ru} · ${c.minutes} мин"
    }

    fun trainingDetail(c: TrainingChoice): String {
        val template = c.templateId?.let { Templates.name(it) }
        val intensity = when (c.type) {
            TrainingType.STRENGTH -> if (c.level == TrainingLevel.L0) "RPE 7–8" else "RPE ≤ 7, без отказа"
            TrainingType.INTERVALS -> "RPE 8"
            TrainingType.ZONE2 -> "разговорный темп"
            TrainingType.MOBILITY -> "спокойно"
            TrainingType.WALK -> "спокойный темп"
            TrainingType.REST -> "по желанию — короткая прогулка"
        }
        return listOfNotNull(template, intensity).joinToString(" · ")
    }

    // --- Push (A11 §2): title ≤ 40, text ≤ 110, without S4/S5 ------------------------------------

    /** The regime word first, then a short image of the day (Titan voice, A37 §4; ⚑16). */
    fun headline(regime: Regime, flags: Set<DayFlag> = emptySet()): String = when {
        DayFlag.SICK in flags -> "Бережно · день тишины"
        regime == Regime.BOOST -> "Разгон · бери высоту"
        regime == Regime.STEADY -> "Ровно · свой темп"
        else -> "Бережно · копим силы"
    }

    fun summary(night: NightFacts, training: TrainingChoice, bedtime: Int): String {
        val sleep = night.sleepHours?.let { "Сон ${duration(it)} · " }.orEmpty()
        val tr = when (training.type) {
            TrainingType.REST -> "без нагрузки"
            else -> {
                val at = training.startMin?.let { " в ${time(it)}" }.orEmpty()
                "${training.type.ru} ${training.minutes} мин$at"
            }
        }
        return "$sleep$tr · отбой ${time(bedtime)}".replaceFirstChar { it.uppercase() }
    }
}
