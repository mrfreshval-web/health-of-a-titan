package com.healthofatitan.app.integration.whoop.day

import com.healthofatitan.app.domain.today.DayTask
import com.healthofatitan.app.domain.today.TaskKind
import org.junit.Test
import kotlin.test.assertEquals

/** S1b: a hidden training takes only the step «тренировка» off «Сегодня», nothing else. */
class TrainingGateTest {

    private val tasks = listOf(
        DayTask("t", TaskKind.TRAINING, "Силовая · 45 мин"),
        DayTask("f", TaskKind.FOCUS, "Фокус · 50 мин"),
        DayTask("r", TaskKind.REMINDER, "Позвонить маме"),
    )

    @Test
    fun `an open training stays, a hidden one leaves alone`() {
        assertEquals(tasks, tasks.withoutTraining(false))
        assertEquals(listOf("f", "r"), tasks.withoutTraining(true).map { it.id })
    }
}
