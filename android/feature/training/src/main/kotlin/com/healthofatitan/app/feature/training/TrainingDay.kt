package com.healthofatitan.app.feature.training

import com.healthofatitan.app.core.ui.component.HotMode
import com.healthofatitan.app.domain.engine.DayFlag
import com.healthofatitan.app.domain.engine.ItemKind
import com.healthofatitan.app.domain.engine.ItemStatus
import com.healthofatitan.app.domain.engine.Regime
import com.healthofatitan.app.domain.engine.TrainingType
import com.healthofatitan.app.domain.training.DayMode
import com.healthofatitan.app.domain.training.DayTrainingKind
import com.healthofatitan.app.domain.training.SoundLevel
import com.healthofatitan.app.domain.training.TrainingDecision
import com.healthofatitan.app.domain.training.TrainingEngine
import com.healthofatitan.app.domain.training.TrainingFlag
import com.healthofatitan.app.domain.training.TrainingPlace
import com.healthofatitan.app.domain.training.TrainingRequest
import com.healthofatitan.app.integration.whoop.day.DayItemView
import com.healthofatitan.app.integration.whoop.day.DayView
import java.time.LocalDate

/**
 * The training of the day as the card and the screen show it: what the day engine planned
 * (regime, kind, minutes, flags) turned into a [TrainingRequest] and the configurator's answer.
 *
 * @param fromPlan false: there is no plan for today yet, the session is the «Ровно» default
 * @param done the training item of the plan is already marked (by WHOOP or by the user)
 */
data class TrainingDay(
    val day: LocalDate,
    val request: TrainingRequest,
    val decision: TrainingDecision,
    val fromPlan: Boolean,
    val done: Boolean,
    val sound: SoundLevel,
)

/** Pure mapping from the stored day (A09) to the configurator's request (A40 §4.2). */
object TrainingRequests {

    fun mode(regime: Regime): DayMode = when (regime) {
        Regime.BOOST -> DayMode.BOOST
        Regime.STEADY -> DayMode.STEADY
        Regime.GENTLE -> DayMode.GENTLE
    }

    fun flags(flags: Set<DayFlag>): Set<TrainingFlag> = buildSet {
        if (DayFlag.SICK in flags) add(TrainingFlag.SICK)
        if (DayFlag.TRAVEL in flags) add(TrainingFlag.TRAVEL)
        if (DayFlag.LIGHT_WEEK in flags) add(TrainingFlag.LIGHT_WEEK)
    }

    fun trainingItem(view: DayView): DayItemView? = view.items.firstOrNull { it.kind == ItemKind.TRAINING }

    /** «Силовая · 45 мин» → STRENGTH (the title is written by `DayTexts.trainingTitle`). */
    fun kind(item: DayItemView?): DayTrainingKind? {
        val title = item?.title ?: return null
        val type = TrainingType.entries.firstOrNull { title.startsWith(it.ru) } ?: return null
        return DayTrainingKind.entries.firstOrNull { it.name == type.name }
    }

    fun minutes(item: DayItemView?): Int? {
        item ?: return null
        val slot = if (item.startMin != null && item.endMin != null) item.endMin!! - item.startMin!! else null
        if (slot != null && slot > 0) return slot
        return MINUTES.find(item.title)?.groupValues?.get(1)?.toIntOrNull()
    }

    fun request(view: DayView?, today: LocalDate, place: TrainingPlace): TrainingRequest {
        val plan = view?.takeIf { it.dayKey == today }
            ?: return TrainingRequest(mode = DayMode.STEADY, place = place)
        val item = trainingItem(plan)
        return TrainingRequest(
            mode = mode(plan.regime),
            kind = kind(item),
            minutes = minutes(item),
            place = place,
            flags = flags(plan.flags),
        )
    }

    fun day(engine: TrainingEngine, view: DayView?, today: LocalDate, place: TrainingPlace, sound: SoundLevel): TrainingDay {
        val request = request(view, today, place)
        val plan = view?.takeIf { it.dayKey == today }
        val item = plan?.let(::trainingItem)
        return TrainingDay(
            day = today,
            request = request,
            decision = engine.plan(request),
            fromPlan = plan != null,
            done = item != null && item.status != ItemStatus.OPEN,
            sound = sound,
        )
    }

    private val MINUTES = Regex("""(\d+)\s*мин""")
}

fun DayMode.hotMode(): HotMode = when (this) {
    DayMode.BOOST -> HotMode.RAZGON
    DayMode.STEADY -> HotMode.ROVNO
    DayMode.GENTLE -> HotMode.BEREZHNO
}
