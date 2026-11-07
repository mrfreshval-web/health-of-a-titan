package com.healthofatitan.app.domain.engine

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Texts of the day plan: «Почему так» one line per rule, item titles, the push. Short on purpose
 * (the founder asks for little text). Neutral, «ты», no diagnoses, no «вред / нельзя / срыв»
 * (A09 §9, Agent 10). Medical wording ⚑3, psychological ⚑10, training ⚑12 — see the PR.
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

    fun signalsWhy(signals: Set<String>, night: NightFacts, regime: Regime): String {
        val parts = buildList {
            if (Signals.ORS_DAY in signals) add("восстановление ниже твоей нормы")
            if (Signals.ORS_TREND in signals) add("неделя ниже нормы")
            if (Signals.SLEEP in signals) add(sleepSignal(night))
            if (Signals.LOAD in signals) add("нагрузка копится")
            if (Signals.SELF in signals) add("самочувствие ниже обычного")
        }
        if (parts.isEmpty()) {
            return when (regime) {
                Regime.BOOST -> "Восстановление и сон не ниже твоей нормы — Разгон."
                else -> "Сигналов усталости нет, но запаса для Разгона мало — ${regime.ru}."
            }
        }
        val text = parts.joinToString(", ").replaceFirstChar { it.uppercase() }
        return "$text — ${regime.ru}."
    }

    private fun sleepSignal(n: NightFacts): String {
        val h = n.sleepHours
        val prev = n.prevSleepHours
        return when {
            h != null && h < 6.0 -> "сон ${duration(h)}"
            h != null && prev != null && h < 7.0 && prev < 7.0 -> "вторая ночь меньше 7 ч"
            else -> "недосып за неделю ${n.sleepDebt7h?.let(::duration) ?: ""}".trim()
        }
    }

    fun hardCapSleep(hours: Double) = "Сон ${duration(hours)} — меньше 5 часов, поэтому Бережно, даже если бодро."
    const val HARD_CAP_SICK = "Пока болеешь — потолок Бережно, самочувствие его не поднимает."
    const val SELF_VS_DATA = "Данные говорят об усталости, а ты чувствуешь себя хорошо: Ровно, без интервалов."
    fun stepUp(yesterday: Regime) = "Вчера было ${yesterday.ru}: поднимаюсь на одну ступень за день."
    const val HYSTERESIS = "Восстановление ещё не вернулось к норме — сигнал держу до выхода выше −0,5."
    const val CALIBRATION = "Изучаю твою норму (нужно ~14 ночей): пока опираюсь на сон."
    fun preliminary(days: Int) = "Норма предварительная ($days дн. из 28): Разгон пока не даю."
    const val LOW_COVERAGE = "Ночью браслет записал не всё — восстановление не оцениваю."
    const val LIGHT_WEEK_GENTLE = "Третий бережный день подряд — лёгкая неделя: легче на ~40 %, без интервалов."
    const val LIGHT_WEEK_LOAD = "Нагрузка копится неделю — лёгкая неделя: легче на ~40 %, без интервалов."
    fun lightWeekDay(day: Int) = "Лёгкая неделя, день $day из 7: легче на ~40 %, без интервалов."

    // --- Safety and context (SAF, CTX) -------------------------------------------------------

    const val SICK = "Ты отметил, что простыл: сегодня без нагрузки, по желанию — короткая прогулка."

    /** CTX-01. Only inside the app, never in the push (S4, A09 §9). */
    fun alcohol(servings: Int, noIntervals: Boolean, lighter: Boolean): String {
        val n = if (servings >= 4) "4+" else servings.toString()
        val tail = when {
            lighter -> " Тренировка облегчённая."
            noIntervals -> " Без интервалов."
            else -> ""
        }
        return "Вчера $n порции — это часть объяснения цифр. Вода и завтрак с белком.$tail"
    }

    const val HEAVY_YESTERDAY = "Вчера была тяжёлая сессия, восстановление ниже нормы — это адаптация. Без интервалов."

    fun travel(shiftHours: Double): String {
        val h = abs(shiftHours).roundToInt()
        val dir = if (shiftHours > 0) "восток" else "запад"
        return "Ты на $h ч к $dir от дома: день по местному времени, без интервалов."
    }

    const val SORE = "Ты отметил, что тело забито: тренировка облегчённая."

    // --- Day rules (SLP, NUT, ACT, WRK) ------------------------------------------------------

    fun bedtime(bedtime: Int, wakeTomorrow: Int, source: AnchorSource, needMin: Int?, shifted: Boolean): String {
        val base = if (needMin != null) {
            "к подъёму в ${time(wakeTomorrow)} (${source.ru}) выйдет ${minutesText(needMin)} сна"
        } else {
            "за 8 ч до подъёма в ${time(wakeTomorrow)} (${source.ru})"
        }
        val prefix = if (shifted) "На 30 мин раньше после короткой ночи или в бережный день. " else ""
        return "${prefix}Отбой ${time(bedtime)}: $base."
    }

    fun caffeine(cutoff: Int) = "Кофе до ${time(cutoff)} — за 9 часов до отбоя."
    const val LIGHT = "10–20 минут дневного света в первый час — вечером легче заснуть."
    fun nap(hours: Double) = "Ночь ${duration(hours)}: короткий сон 10–20 мин до 15:00, если получится."
    fun dinner(end: Int) = "Ужин до ${time(end)} — за 3 часа до сна."
    const val WALK = "Бережный день: 20–30 минут спокойной ходьбы помогают и настроению, и сну."
    fun trainingSlot(start: Int, fromHistory: Boolean) =
        if (fromHistory) "Тренировка в ${time(start)} — твоё обычное время по WHOOP." else "Обычного времени тренировки пока нет — ставлю на ${time(start)}."
    const val LATE_INTENSE = "Окно поздно для интенсива: он должен закончиться за 2 ч до сна, поэтому спокойнее."
    const val FOCUS = "Главная задача — в первые часы после подъёма: голова свежее."
    const val ONE_TASK = "Сегодня одна главная задача, важные решения — до обеда."
    const val SHORT_NIGHT_TRAINING = "Сон меньше 6 ч: интервалы → Zone 2, силовая облегчённая."

    // --- A19 weekly queue ----------------------------------------------------------------------

    fun queueStrength(strengthThisWeek: Int) = "Силовых на этой неделе $strengthThisWeek из 2 — сегодня силовая."
    const val QUEUE_STRENGTH_GAP = "Силовой не было 3 дня — сегодня силовая."
    const val QUEUE_INTERVALS = "Разгон, интервалов за неделю меньше двух — сегодня интервалы."
    fun queueZone2(weekMin: Int) = "Кардио на неделе $weekMin мин из 150 — сегодня Zone 2."
    const val QUEUE_DEFAULT = "Неделя по плану — силовая через день, между ними Zone 2."
    const val QUEUE_REST = "6 дней подряд с тренировкой — сегодня восстановление."
    fun replaced(from: TrainingType, to: TrainingType) = "По очереди недели — ${from.ru}, сегодня вместо этого ${to.ru}."

    // --- Items ---------------------------------------------------------------------------------

    const val ITEM_WATER = "Стакан воды"
    const val ITEM_LIGHT = "Свет 10–20 мин"
    const val ITEM_LIGHT_LOCAL = "Свет по местному времени"
    const val ITEM_BREAKFAST = "Завтрак с белком"
    const val ITEM_NAP = "Короткий сон 10–20 мин"
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

    fun headline(regime: Regime): String = when (regime) {
        Regime.BOOST -> "Разгон · ты выше своей нормы"
        Regime.STEADY -> "Ровно · день по плану"
        Regime.GENTLE -> "Бережно · сегодня без героизма"
    }

    fun summary(night: NightFacts, training: TrainingChoice, bedtime: Int): String {
        val sleep = night.sleepHours?.let { "Сон ${duration(it)}. " }.orEmpty()
        val tr = when (training.type) {
            TrainingType.REST -> "Без нагрузки"
            else -> {
                val at = training.startMin?.let { " в ${time(it)}" }.orEmpty()
                "${training.type.ru} ${training.minutes} мин$at"
            }
        }
        return "$sleep$tr, отбой ${time(bedtime)}."
    }
}
