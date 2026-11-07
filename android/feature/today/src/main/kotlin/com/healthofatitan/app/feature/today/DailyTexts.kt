package com.healthofatitan.app.feature.today

import com.healthofatitan.app.core.model.profile.AddressForm
import com.healthofatitan.app.core.model.text.G
import com.healthofatitan.app.domain.engine.Body
import com.healthofatitan.app.domain.engine.FoodClose
import com.healthofatitan.app.domain.engine.ReturnPolicy
import com.healthofatitan.app.domain.engine.TrainingClose
import java.time.LocalDate
import java.util.Locale

/**
 * Words of stream A «Каждый день»: «Закрыть день» (A18 И4.1, A37 s5), the return after a pause
 * (И4.4, A11 §2/§3.3) and phone steps (A35). Pictograms carry the meaning; a word stays as the
 * `contentDescription` and as a short label. No «пропустил», «серия», «прервал», no red, no
 * judgement of the body, food or drinking. Review: Agent 10 (tone), Agent 7 (steps, privacy).
 * The crisis card itself is not here: its texts are A07 §4.4 only (SOS screen, unchanged).
 */
object DailyTexts {
    // «Закрыть день»
    const val CLOSE_TITLE = "Закрыть день"
    const val TRAINING = "Тренировка"
    const val FOOD = "Еда"
    const val BODY = "Тело"
    const val ALCOHOL = "Алкоголь"
    const val PLAN_HELPED = "План помог?"
    const val YES = "Да"
    const val NOT_REALLY = "Не очень"
    const val LAST_TAP = "Последний тап закроет день"
    const val CLOSE_WITHOUT = "Закрыть без отметок"
    const val NOTE = "Мысль дня"
    const val NOTE_HINT = "Пара слов о дне. Не сохраняется."
    const val HELP = "Помощь"
    const val DAY_LIGHT = "Свет дня"

    /**
     * A61 §4.2 example of [G] (D-055): «Сделано» · «По-другому» without a chosen address. The
     * neutral wording is the A61 draft and waits for Agents 16 and 10.
     */
    private val TRAINING_LABELS = listOf(
        TrainingClose.DONE to G(n = "Сделано", m = "Сделал", f = "Сделала"),
        TrainingClose.CHANGED to G(n = "По-другому", m = "Изменил", f = "Изменила"),
        TrainingClose.NOT_TODAY to G("Не сегодня"),
    )

    fun trainingOptions(address: AddressForm): List<Pair<TrainingClose, String>> =
        TRAINING_LABELS.map { (value, label) -> value to label.of(address) }
    val FOOD_OPTIONS = listOf(
        FoodClose.PLATE to "По тарелке",
        FoodClose.PARTLY to "Частично",
        FoodClose.JUST_ATE to "Просто",
    )
    val BODY_OPTIONS = listOf(Body.FINE to "Легко", Body.SORE to "Забито", Body.SICK to "Простуда")
    val ALCOHOL_OPTIONS = listOf(0 to "Нет", 1 to "1", 2 to "2", 3 to "3", 4 to "4+")

    /** «2 / 4» of the mockup s5. */
    fun progress(answered: Int, total: Int): String = "$answered / $total"

    // After the day is closed: light for showing up, never a score
    const val CLOSED = "День закрыт"
    const val CLOSED_LINE = "Принято. Утро это учтёт."

    /** A18 И4.1 ②: «не сегодня» → no reproach, the plan adapts. */
    const val NOT_TODAY_LINE = "Понял. Завтра подстрою план."

    // Return after a pause (A11 §3.3 «Возвращение (С6)»)
    const val WELCOME_TITLE = "С возвращением"
    const val WELCOME_LINE = "Начнём с малого: 10 минут и один шаг."
    const val WELCOME_START = "Начать с малого"

    private val ru: Locale = Locale.forLanguageTag("ru")

    /** «Новый старт — с понедельника, если хочешь.» (A18 И4.4 ③): Monday or the 1st, whichever is nearer. */
    fun newStart(today: LocalDate): String {
        val start = ReturnPolicy.newStart(today)
        val whenText = when {
            start == today -> "сегодня"
            start.dayOfMonth == 1 -> "с 1-го числа"
            else -> "с понедельника"
        }
        return "Новый старт — $whenText, если хочешь."
    }

    // Phone steps (A35, D-024)
    const val STEPS = "Шаги телефона"
    const val STEPS_ASK = "Шаги телефона — чтобы сверить прогулку с планом и заполнить кольцо шагов."
    const val STEPS_ALLOW = "Разрешить"
    const val WALK_BY_STEPS = "прогулка подтверждена шагами"

    /** «6 800 / 9 000» with thin spaces. */
    fun steps(today: Int, goal: Int): String = "${group(today)} / ${group(goal)}"

    /** TalkBack: «Шаги телефона: 6 800 из 9 000». */
    fun stepsSpoken(today: Int, goal: Int): String = "$STEPS: ${group(today)} из ${group(goal)}"

    private fun group(v: Int): String =
        String.format(ru, "%,d", v).replace('\u00a0', '\u2009').replace('\u202f', '\u2009').replace(' ', '\u2009')
}
