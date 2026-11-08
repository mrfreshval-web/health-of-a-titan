package com.healthofatitan.app.feature.path.onboarding

import com.healthofatitan.app.domain.path.PathCopy
import com.healthofatitan.app.feature.path.PathData
import com.healthofatitan.app.feature.path.PathFeatureFlags
import com.healthofatitan.app.feature.path.build
import com.healthofatitan.app.feature.path.logic.RitualDraft
import com.healthofatitan.app.feature.path.logic.RitualLogic
import com.healthofatitan.app.feature.path.logic.RitualStep
import org.junit.Test
import java.time.LocalDate

/**
 * A goal is typed letter by letter (08.11: «при попытке внести цель — вылетает»): every prefix of a
 * real goal goes through what the screens compute on each key — the preview of the first launch,
 * the ritual's gate, title and checks — and none of it may throw.
 */
class GoalTypingTest {
    private val today = LocalDate.of(2026, 11, 8)
    private val data = PathData.empty(today)

    private val goals = listOf(
        "Подтянуться 10 раз",
        "Пробежать 10 км",
        "Похудеть на 5 кг",
        "Спать по 8 часов",
        "Быть сильнее",
        "10",
        "  ",
        "a",
        "Сбросить вес к лету!!!",
    )

    @Test
    fun `every prefix of a goal is computed without a crash`() {
        val flags = PathFeatureFlags.current
        val failures = mutableListOf<String>()
        goals.forEach { g ->
            (1..g.length).map { g.take(it) }.forEach { text ->
                runCatching {
                    RitualStep.entries.forEach { step ->
                        val d = RitualDraft(step = step, text = text, why = "чтобы")
                        RitualLogic.title(d, flags, data.given)
                        RitualLogic.gate(d, flags, data.given)
                        val goal = build(d, data)
                        RitualLogic.violations(goal, data.given, flags, data.goals).map { PathCopy.violation(it) }
                    }
                    val goal = build(RitualDraft(text = text.trim(), why = ""), data)
                    val parts = OnboardingRules.withLever(goal.components, OnboardingRules.suggestedLever(BehindAnswers(different = setOf(OnboardingCopy.DIFF_SLEEP)), goal.components))
                    val preview = goal.copy(components = parts, deadline = OnboardingRules.deadline(today, 12))
                    RitualLogic.violations(preview, data.given, flags, data.goals).map { PathCopy.violation(it) }
                    OnboardingRules.STAGES.forEach { OnboardingRules.suggestion(preview, BehindAnswers(), today, it) }
                }.onFailure { failures += "«$text»: ${it::class.simpleName}: ${it.message}\n${it.stackTrace.take(6).joinToString("\n  ")}" }
            }
        }
        if (failures.isNotEmpty()) throw AssertionError("${failures.size} prefixes crash:\n" + failures.take(5).joinToString("\n"))
    }
}
