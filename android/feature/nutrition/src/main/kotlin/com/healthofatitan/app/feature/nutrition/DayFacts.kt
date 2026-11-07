package com.healthofatitan.app.feature.nutrition

import com.healthofatitan.app.domain.engine.DayFlag
import com.healthofatitan.app.domain.engine.ItemKind
import com.healthofatitan.app.domain.engine.Regime
import com.healthofatitan.app.domain.engine.TrainingType
import com.healthofatitan.app.domain.nutrition.DayCondition
import com.healthofatitan.app.domain.nutrition.DayMode
import com.healthofatitan.app.domain.nutrition.FoldedProfile
import com.healthofatitan.app.domain.nutrition.Level
import com.healthofatitan.app.domain.nutrition.LoadClass
import com.healthofatitan.app.domain.nutrition.NutritionDayInput
import com.healthofatitan.app.integration.whoop.day.DayView
import java.time.LocalDate

/**
 * The day engine (A09) → the food engine (A41 §8.1): regime, flags, bedtime, the training of the
 * day and its load class (A41 §8.2). Food only reads the day; it never writes back.
 */
object DayFacts {

    /** The «Сегодня» plan id of the training item (DayEngine.Ids.TRAINING). */
    private const val TRAINING = "training"
    private const val LIGHT = "light"

    fun input(
        day: DayView?,
        today: LocalDate,
        chosen: Level,
        profile: FoldedProfile,
        chips: Set<DayCondition>,
        replaced: Map<String, Int>,
    ): NutritionDayInput {
        val view = day?.takeIf { it.dayKey == today }
        val training = view?.items?.firstOrNull { it.id == TRAINING || it.kind == ItemKind.TRAINING }
        val startMin = training?.startMin
        val endMin = training?.endMin
        val minutes = if (startMin != null && endMin != null) endMin - startMin else null
        val type = training?.title?.let { title -> TrainingType.entries.firstOrNull { title.startsWith(it.ru) } }
        val load = loadOf(type, minutes)
        val flags = view?.flags.orEmpty()
        val conditions = buildSet {
            addAll(chips)
            if (DayFlag.SICK in flags) add(DayCondition.SICK)
            if (DayFlag.TRAVEL in flags) add(DayCondition.TRAVEL)
            if (DayFlag.LIGHT_WEEK in flags) add(DayCondition.LIGHT_WEEK)
        }
        return NutritionDayInput(
            dayKey = today,
            chosenLevel = chosen,
            profile = profile,
            mode = when (view?.regime) {
                Regime.BOOST -> DayMode.RAZGON
                Regime.GENTLE -> DayMode.BEREZHNO
                else -> DayMode.RAVNO
            },
            conditions = conditions,
            loadToday = load,
            loadTomorrow = null, // the weekly training plan (stream B) will tell; until then no look-ahead
            wakeMin = view?.items?.firstOrNull { it.id == LIGHT }?.startMin ?: DEFAULT_WAKE,
            bedtimeMin = view?.bedtimeMin ?: DEFAULT_BEDTIME,
            trainingStartMin = startMin.takeIf { load != LoadClass.REST },
            trainingMinutes = minutes,
        )
    }

    /**
     * A41 §8.2 classes from the A19 training of the day: rest; light — mobility, a walk, Zone 2
     * under 45 min; moderate — strength or Zone 2 45–75 min; hard — intervals; long — ≥ 90 min.
     */
    fun loadOf(type: TrainingType?, minutes: Int?): LoadClass {
        val m = minutes ?: 0
        return when (type) {
            null, TrainingType.REST -> LoadClass.REST
            TrainingType.MOBILITY, TrainingType.WALK -> LoadClass.LIGHT
            else -> when {
                m >= 90 -> LoadClass.LONG
                type == TrainingType.INTERVALS -> LoadClass.HARD
                type == TrainingType.ZONE2 && m < 45 -> LoadClass.LIGHT
                else -> LoadClass.MODERATE
            }
        }
    }

    const val DEFAULT_WAKE = 7 * 60
    const val DEFAULT_BEDTIME = 23 * 60
}
