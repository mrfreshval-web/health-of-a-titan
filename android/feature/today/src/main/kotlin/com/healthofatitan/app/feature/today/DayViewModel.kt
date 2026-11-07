package com.healthofatitan.app.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthofatitan.app.core.database.entity.EveningCheckEntity
import com.healthofatitan.app.domain.engine.Body
import com.healthofatitan.app.domain.engine.CrisisAssessment
import com.healthofatitan.app.domain.engine.CrisisSource
import com.healthofatitan.app.domain.engine.DayClose
import com.healthofatitan.app.domain.engine.DayEngine
import com.healthofatitan.app.domain.engine.FoodClose
import com.healthofatitan.app.domain.engine.Steps
import com.healthofatitan.app.domain.engine.TrainingClose
import com.healthofatitan.app.domain.engine.WorkoutMatcher
import com.healthofatitan.app.integration.healthconnect.PhoneSteps
import com.healthofatitan.app.integration.healthconnect.StepsRepository
import com.healthofatitan.app.integration.healthconnect.WalkWindow
import com.healthofatitan.app.integration.whoop.day.DailyLoopRepository
import com.healthofatitan.app.integration.whoop.day.DayPlanRepository
import com.healthofatitan.app.integration.whoop.day.DayView
import com.healthofatitan.app.integration.whoop.day.MorningCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

/** What the day block shows: the latest stored plan, the time, the evening and the phone's steps. */
data class DayUiState(
    val plan: DayView? = null,
    val now: LocalDateTime = LocalDateTime.now(),
    /** The day whose evening deck is answered (or skipped) — the latest one stored. */
    val eveningAnswered: LocalDate? = null,
    /** Just answered in this session: show «Принято» instead of the deck. */
    val eveningJustSaved: Boolean = false,
    val loaded: Boolean = false,
    /** The answers of the latest closed day («не сегодня» changes the closing line). */
    val closed: DayClose? = null,
    /** Phone steps from Health Connect (D-024), memory only. */
    val steps: PhoneSteps = PhoneSteps.Unavailable,
) {
    val today: LocalDate get() = now.toLocalDate()

    /** Today's plan exists (the night is scored). */
    val planToday: Boolean get() = plan?.dayKey == today

    /** The evening window of the plan's day: after 18:00 that day, or before 05:00 the next morning. */
    private val inEvening: Boolean
        get() {
            val p = plan ?: return false
            return (p.dayKey == today && now.hour >= EVENING_FROM_HOUR) ||
                (p.dayKey == today.minusDays(1) && now.hour < EVENING_UNTIL_HOUR)
        }

    /** «Закрыть день» (A18 И4.1, D-027: one tap each) for the plan's day, once per day. */
    val eveningFor: LocalDate?
        get() = plan?.dayKey?.takeIf { inEvening && eveningAnswered != it }

    /** The day is closed and it is still its evening: the light card instead of the questions. */
    val closedFor: LocalDate?
        get() = plan?.dayKey?.takeIf { inEvening && eveningAnswered == it }

    companion object {
        const val EVENING_FROM_HOUR = 18
        const val EVENING_UNTIL_HOUR = 5
    }
}

/** Stored answers → the engine's [DayClose]; unknown names read as «не отмечено». */
internal fun EveningCheckEntity.toClose(): DayClose = DayClose(
    training = training?.let { t -> TrainingClose.entries.firstOrNull { it.name == t } },
    food = food?.let { f -> FoodClose.entries.firstOrNull { it.name == f } },
    body = body?.let { b -> Body.entries.firstOrNull { it.name == b } },
    alcohol = alcohol,
    planHelped = planHelped,
)

/**
 * The planned walk's window for phone steps (A35 §4): the slot ±3 h like WHOOP's match
 * ([WorkoutMatcher.SLOT_WINDOW_MIN]); null when today's training is not a walk.
 */
internal fun walkWindow(plan: DayView?, zone: ZoneId): WalkWindow? {
    val item = plan?.items?.firstOrNull { it.id == DayEngine.Ids.TRAINING } ?: return null
    if (!Steps.isWalk(item.rule)) return null
    val start = item.startMin ?: return null
    val end = item.endMin ?: return null
    val midnight = plan.dayKey.atStartOfDay(zone)
    val slack = WorkoutMatcher.SLOT_WINDOW_MIN.toLong()
    return WalkWindow(
        from = midnight.plusMinutes(start - slack).toInstant(),
        to = midnight.plusMinutes(end + slack).toInstant(),
        minutes = end - start,
    )
}

@HiltViewModel
class DayViewModel @Inject constructor(
    private val plans: DayPlanRepository,
    private val morning: MorningCoordinator,
    private val loop: DailyLoopRepository,
    private val steps: StepsRepository,
) : ViewModel() {

    private val justSaved = MutableStateFlow(false)

    private val clock = flow {
        while (true) {
            emit(LocalDateTime.now())
            delay(TICK_MS)
        }
    }

    val state: StateFlow<DayUiState> =
        combine(plans.latest, plans.latestEvening, clock, justSaved, steps.state) { plan, evening, now, saved, phone ->
            DayUiState(
                plan = plan,
                now = now,
                eveningAnswered = evening?.dayKey?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                eveningJustSaved = saved,
                loaded = true,
                closed = evening?.toClose(),
                steps = phone,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DayUiState())

    /** «С возвращением» is due (A18 И4.4: silence ≥ 7 days, once). */
    val welcome: StateFlow<Boolean> =
        loop.welcomeDue().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        // Phone steps: re-read every few minutes while «Сегодня» lives (memory only, R-11).
        viewModelScope.launch {
            while (true) {
                refreshSteps()
                delay(STEPS_MS)
            }
        }
    }

    /** «Сегодня» is on screen: rebuild today's plan and count it as seen (no morning push then). */
    fun opened() {
        viewModelScope.launch { runCatching { morning.onTodayOpened() } }
        refreshSteps()
    }

    fun refreshSteps() {
        viewModelScope.launch { runCatching { steps.refresh(walkWindow(state.value.plan, ZoneId.systemDefault())) } }
    }

    fun toggle(day: LocalDate, itemId: String) {
        viewModelScope.launch { plans.toggle(day, itemId) }
    }

    /** A skipped card is null (A25 §2.1); skipping the deck stores both as null. */
    fun saveEvening(day: LocalDate, alcohol: Int?, body: Body?) {
        viewModelScope.launch {
            plans.saveEvening(day, alcohol, body)
            justSaved.value = true
        }
    }

    /** «Закрыть день» (A18 И4.1): the answers, and the training item lit by the answer. */
    fun closeDay(day: LocalDate, close: DayClose) {
        viewModelScope.launch {
            plans.closeDay(day, close)
            justSaved.value = true
        }
    }

    /** A07 §4.7: the level of the evening note is logged without the note. */
    fun crisis(assessment: CrisisAssessment) {
        viewModelScope.launch { runCatching { loop.logSafety(assessment, CrisisSource.EVENING) } }
    }

    fun welcomeSeen() {
        viewModelScope.launch { runCatching { loop.welcomeShown() } }
    }

    private companion object {
        const val TICK_MS = 60_000L
        const val STEPS_MS = 5 * 60_000L
    }
}
