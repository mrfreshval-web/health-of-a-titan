package com.healthofatitan.app.integration.whoop.day

import com.healthofatitan.app.domain.today.TodayFood
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate

/**
 * The food of [day] for «Сегодня» (A41 §10, audit 2026-11-08 S3): the meals of the food plan and
 * what the safety bus hides. The nutrition module binds it; «Сегодня» never reads the food engine.
 */
fun interface TodayFoodSource {
    fun food(day: LocalDate): Flow<TodayFood>

    companion object {
        val None = TodayFoodSource { flowOf(TodayFood()) }
    }
}
