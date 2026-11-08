package com.healthofatitan.app.feature.nutrition

import com.healthofatitan.app.domain.nutrition.NutritionDayPlan
import com.healthofatitan.app.domain.nutrition.ProfileMode
import com.healthofatitan.app.domain.safety.Restriction
import com.healthofatitan.app.domain.safety.SafetyConsumer
import com.healthofatitan.app.domain.safety.SafetyState
import com.healthofatitan.app.domain.today.FoodMeal
import com.healthofatitan.app.domain.today.TodayFood
import com.healthofatitan.app.integration.whoop.day.TodayFoodSource
import com.healthofatitan.app.integration.whoop.safety.SafetyBus
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.LocalDateTime

/**
 * The food plan as «Сегодня» shows it (audit 2026-11-08 S3, Р-3, Р-16). The strictest wins: the
 * restrictions of the bus for «Сегодня» (`ED_CARE`, its history after the exit, pregnancy and
 * lactation) and the profile's own `ED_CARE` mode of the plan are united, never subtracted.
 *
 * - `HIDE_FOOD_TALK` → [TodayFood.quiet]: no plate line and no list of meals on «Сегодня»;
 * - `HIDE_FOOD_WINDOWS` (or quiet, or the plan hides numbers) → no clock time of a meal;
 * - `HIDE_CALORIES` (or quiet, or the plan is in `ED_CARE`) → no «N из M» and no kcal.
 */
fun todayFood(plan: NutritionDayPlan?, restrictions: Set<Restriction>): TodayFood {
    val edPlan = plan?.mode == ProfileMode.ED_CARE
    val quiet = Restriction.HIDE_FOOD_TALK in restrictions
    val hideTimes = quiet || Restriction.HIDE_FOOD_WINDOWS in restrictions || plan?.hideNumbers == true
    val hideCalories = Restriction.HIDE_CALORIES in restrictions || edPlan
    val hideCounts = quiet || hideCalories
    return TodayFood(
        plate = plan?.template?.ru,
        meals = plan?.slots.orEmpty().map { s ->
            FoodMeal(slot = s.slot, title = s.ru, timeMin = s.timeMin?.takeIf { !hideTimes }, optional = s.optional)
        },
        hideTimes = hideTimes,
        hideCounts = hideCounts,
        hideCalories = hideCalories,
        quiet = quiet,
    )
}

@Module
@InstallIn(SingletonComponent::class)
object TodayFoodModule {

    /** One minute is enough: the plan changes with the day, the profile and the bus, not by the second. */
    private const val TICK_MS = 60_000L

    @Provides
    fun provideTodayFood(repo: NutritionRepository, bus: SafetyBus): TodayFoodSource = TodayFoodSource { day ->
        val clock = flow {
            while (true) {
                emit(LocalDateTime.now())
                delay(TICK_MS)
            }
        }
        // An unreadable bus is not «no ED_CARE»: the strictest column hides the numbers (rule 3 of the bus).
        combine(repo.state(clock), bus.state.catch { emit(SafetyState(unknown = true)) }) { s, b ->
            todayFood(s.plan?.takeIf { it.dayKey == day }, b.restrictions(SafetyConsumer.TODAY))
        }.distinctUntilChanged()
    }
}
