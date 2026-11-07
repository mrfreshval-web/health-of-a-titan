package com.healthofatitan.app.domain.today.week

import com.healthofatitan.app.domain.today.GoalMeasure
import com.healthofatitan.app.domain.today.GoalSource
import com.healthofatitan.app.domain.today.Sphere
import com.healthofatitan.app.domain.today.TaskKind
import com.healthofatitan.app.domain.today.WeekGoal
import kotlin.math.abs

/**
 * A pattern of the week (A52 §2.5, §4 `Pattern { text, evidenceN, effect, actionGoal? }`): found by
 * the pre-registered rules of Agent 9 (the discoveries of A44 §5), never an AI conclusion in the
 * numbers. [onlyYou] patterns («Только ты», S5) never reach the week.
 */
data class Pattern(
    val id: String,
    val text: String,
    val evidenceN: Int,
    val effect: Double,
    val action: String? = null,
    val onlyYou: Boolean = false,
    /** The effect holds (its interval does not cross zero). */
    val steady: Boolean = true,
)

data class PatternView(val pattern: Pattern, val label: String, val evidence: String, val actionGoal: WeekGoal?)

object PatternRules {
    const val MAX = 3
    const val MIN_EVIDENCE = 6

    /** W-03: ≤ 3 a week, each with ≥ 6 points, a steady effect, the «связь, не причина» label, no S5. */
    fun select(all: List<Pattern>): List<PatternView> = all.asSequence()
        .filter { !it.onlyYou && it.steady && it.evidenceN >= MIN_EVIDENCE }
        .sortedWith(compareByDescending<Pattern> { abs(it.effect) }.thenByDescending { it.evidenceN }.thenBy { it.id })
        .take(MAX)
        .map { PatternView(it, WeekCopy.PATTERN_LINK, WeekCopy.evidence(it.evidenceN), actionGoal(it)) }
        .toList()

    /** «Попробовать на неделе» → a small optional goal of the next week (A52 §2.5). */
    fun actionGoal(p: Pattern): WeekGoal? = p.action?.let {
        WeekGoal(
            id = WeekGoalRules.PATTERN_PREFIX + p.id,
            sphere = Sphere.MIND,
            kind = TaskKind.REMINDER,
            title = it,
            target = 3,
            unit = WeekCopy.UNIT_DAYS,
            progress = 0,
            source = GoalSource.PATTERN,
            measure = GoalMeasure.DAYS,
        )
    }
}

/**
 * Texts of the pre-registered pairs of A44 §5 as the week says them: a link with its count and an
 * action to try. Without morals, without «bad» habits. Ждёт ревью 3, 10, 16 (and Agent 9 for the
 * method).
 */
object PatternTexts {
    data class Text(val positive: String, val gentle: String, val action: String?)

    val BY_PAIR: Map<String, Text> = mapOf(
        "SHOWER_CHARGE" to Text("В утра с холодным душем Заряд чаще выше", "Утра с холодным душем и без — Заряд почти одинаковый", "Холодный душ утром"),
        "LIGHT_CHARGE" to Text("В утра со светом в первые полчаса бодрость выше", "Утренний свет пока мало меняет бодрость", "Свет в первые 30 минут"),
        "BREATH_CHARGE" to Text("В утра с дыхательной практикой голова яснее", "Дыхательная практика пока мало меняет утро", "Дыхание утром, 3 минуты"),
        "STACK_HEAD" to Text("Когда утро собрано из нескольких практик, голова яснее", "Утренний набор пока мало меняет голову", null),
        "SCREENS_SLEEP" to Text("В дни без экранов перед сном сон длиннее", "Экраны перед сном пока мало меняют сон", "Экран убран в 22:30"),
        "SCREENS_CHARGE" to Text("Вечер без экранов — утром Заряд выше", "Экраны вечером пока мало меняют Заряд", "Экран убран в 22:30"),
        "ALCOHOL_PULSE" to Text("После вечеров без алкоголя пульс покоя ниже", "Связь алкоголя и пульса пока не видна", "Вечер без алкоголя"),
        "ALCOHOL_HEAD" to Text("После вечеров без алкоголя утром голова яснее", "Связь алкоголя и утра пока не видна", "Вечер без алкоголя"),
        "SWEETS_WEATHER" to Text("Дни со сладким и без — настроение отличается", "Сладкое пока мало меняет настроение", null),
        "TRAINING_HEAD" to Text("На следующий день после тренировки голова яснее", "Тренировка пока мало меняет следующий день", null),
        "COFFEE_SLEEP" to Text("В дни с кофе после 14:00 сон короче", "Кофе после обеда пока мало меняет сон", "Кофе до 14:00"),
    )

    fun all(): List<String> = BY_PAIR.values.flatMap { listOfNotNull(it.positive, it.gentle, it.action) }
}
