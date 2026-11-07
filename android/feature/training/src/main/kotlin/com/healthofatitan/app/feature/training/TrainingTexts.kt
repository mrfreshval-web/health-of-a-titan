package com.healthofatitan.app.feature.training

import com.healthofatitan.app.domain.training.BlockType
import com.healthofatitan.app.domain.training.SessionItem
import com.healthofatitan.app.domain.training.Side
import com.healthofatitan.app.domain.training.WorkoutStep

/**
 * Interface words of the training screens. Technical labels only: nothing here describes
 * health, symptoms or what a body should feel. The helper lines of the player and the summary
 * are placeholders for review.
 *
 * TODO(review: Agents 3, 10, 12): every line marked «review» below; techniques and cues come
 * only from content/training once its `reviewedBy` is filled.
 */
object TrainingTexts {
    const val CARD_OVERLINE = "Тренировка дня"
    const val SCREEN_TITLE = "Тренировка"
    const val BACK_LABEL = "Сегодня"
    const val START = "Начать"
    const val CONTINUE = "Вернуться к тренировке"
    const val RUNNING = "Идёт тренировка"
    const val DONE_TODAY = "Сегодня уже отмечено"
    const val NO_PLAN = "Плана дня ещё нет — версия «Ровно»."
    const val WHY = "Почему так"
    const val PLACE = "Где"
    const val SOUND = "Звук тренировки"

    // review: the sound levels in one line each (A43 §1.2)
    const val SOUND_HINT = "Музыка не прерывается: только вибрация и короткие тоны поверх неё."
    const val COACH_LATER = "Голос «Тренера» — в следующей версии; пока как «Только важное»."

    const val FEEL_BAD = "Мне плохо"
    const val READY = "Готово"
    const val SKIP = "Пропустить"
    const val PAUSE = "Пауза"
    const val PAUSED = "Пауза"
    const val RESUME = "Продолжить"
    const val FINISH = "Завершить"
    const val REST = "Отдых"
    const val EXTEND = "+15 с"
    const val GET_READY = "Приготовься"
    const val BREATH_IN = "вдох"
    const val BREATH_OUT = "выдох"

    // review: RPE question (A40 §7.3, scale 1–10 of the content)
    const val RPE_QUESTION = "Насколько тяжело было? RPE"
    const val RPE_LATER = "Не сейчас"

    // review: neutral caption until technique texts pass review (content reviewedBy is empty)
    const val TECHNIQUE_PENDING = "Подсказки по технике появятся после проверки тренером."

    const val SUMMARY_TITLE = "Тренировка завершена"
    const val SUMMARY_TIME = "Время"
    const val SUMMARY_SETS = "Подходы"
    const val SUMMARY_RPE = "RPE"
    const val SUMMARY_STRETCH = "Растяжка"
    const val STRETCH_DONE = "сделана"
    const val STRETCH_SKIPPED = "пропущена"

    // Review 2026-11-07 (Agents 3, 10): after «Мне плохо» the summary names the next step too.
    const val SUMMARY_FELT_BAD = "Отмечено «Мне плохо». Тренировка остановлена. " +
        "Если это повторится или не проходит — покажись врачу до следующей тренировки."

    // ── «Мне плохо» (A40 §2.5 → A19 §6.2 stop signals F1–F7). Review 2026-11-07, Agents 3 and 10:
    // the button used to show only «Пауза»; the stop signals and the emergency number of the
    // phone's country (Y6) are now on that screen. No diagnosis, no «дотерпи».
    const val FEEL_BAD_TITLE = "Стоп. Сядь и отдышись"

    /** F1, F2, F5: call now. */
    fun feelBadUrgent(number: String) =
        "Боль или давление в груди, обморок, онемение, спутанность, сбой речи или зрения — звони $number."

    /** F3: breathlessness that does not pass. */
    fun feelBadBreath(number: String) = "Одышка не проходит за 2–3 минуты отдыха — тоже $number."

    /** F4, F6, F7: stop and rest, no call. */
    const val FEEL_BAD_REST = "Резкая боль в суставе или мышце — пропусти это упражнение. " +
        "Холодный пот, тошнота, «что-то не так» — заверши тренировку и отдохни."

    fun callEmergency(number: String) = "Позвонить $number"
    const val CLOSE = "Закрыть"
    const val SEAL_SPOKEN = "Тренировка завершена"

    const val CHANNEL_NAME = "Тренировка"
    const val CHANNEL_DESCRIPTION = "Таймер тренировки при выключенном экране"
    const val NOTIFICATION_TITLE = "Тренировка"

    const val HIDDEN_TITLE = "Сегодня без тренировки"

    fun next(name: String) = "Дальше: $name"

    fun minutes(min: Int) = "$min мин"

    fun exercises(n: Int): String = "$n ${plural(n, "упражнение", "упражнения", "упражнений")}"

    fun setOf(set: Int, sets: Int) = "Подход $set из $sets"

    fun roundOf(round: Int, rounds: Int) = "Круг $round из $rounds"

    fun side(side: Side) = side.ru.replaceFirstChar { it.uppercase() }

    fun tempo(tempo: String) = "Темп $tempo"

    fun block(type: BlockType) = type.ru

    fun dose(w: WorkoutStep.Work): String = listOfNotNull(
        w.reps?.let { reps(it) },
        w.seconds?.let { clock(it) },
        w.distanceM?.let { "$it м" },
        w.side?.let { side(it) },
    ).joinToString(" · ")

    /** The dose of an item in the session list: «3 × 10», «2 × 0:40 на сторону». */
    fun dose(item: SessionItem): String {
        val one = item.reps ?: item.timeS?.let { clock(it) } ?: item.distanceM?.let { "$it м" } ?: ""
        val sets = if (item.sets > 1) "${item.sets} × " else ""
        val side = if (item.perSide) " на сторону" else ""
        return "$sets$one$side".trim()
    }

    fun reps(text: String): String {
        val n = text.trim().toIntOrNull()
        return if (n != null) "$n ${plural(n, "повтор", "повтора", "повторов")}" else text
    }

    /** 0:40, 1:05, 12:00. */
    fun clock(seconds: Int): String {
        val s = seconds.coerceAtLeast(0)
        return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
    }

    fun duration(seconds: Long): String {
        val m = (seconds + 30) / 60
        return if (m >= 60) "${m / 60} ч ${m % 60} мин" else "$m мин"
    }

    fun sets(done: Int, planned: Int) = "$done из $planned"

    fun plural(n: Int, one: String, few: String, many: String): String {
        val m10 = n % 10
        val m100 = n % 100
        return when {
            m10 == 1 && m100 != 11 -> one
            m10 in 2..4 && m100 !in 12..14 -> few
            else -> many
        }
    }
}
