package com.healthofatitan.app.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthofatitan.app.domain.engine.Body
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
import javax.inject.Inject

/** What the day block shows: the latest stored plan, the time, and the evening deck state. */
data class DayUiState(
    val plan: DayView? = null,
    val now: LocalDateTime = LocalDateTime.now(),
    /** The day whose evening deck is answered (or skipped) — the latest one stored. */
    val eveningAnswered: LocalDate? = null,
    /** Just answered in this session: show «Принято» instead of the deck. */
    val eveningJustSaved: Boolean = false,
    val loaded: Boolean = false,
) {
    val today: LocalDate get() = now.toLocalDate()

    /** Today's plan exists (the night is scored). */
    val planToday: Boolean get() = plan?.dayKey == today

    /**
     * The evening deck (D-027, ≤ 3 taps) for the plan's day: after 18:00 that day, or before
     * 05:00 the next morning; once per day.
     */
    val eveningFor: LocalDate?
        get() {
            val p = plan ?: return null
            val due = (p.dayKey == today && now.hour >= EVENING_FROM_HOUR) ||
                (p.dayKey == today.minusDays(1) && now.hour < EVENING_UNTIL_HOUR)
            return if (due && eveningAnswered != p.dayKey) p.dayKey else null
        }

    companion object {
        const val EVENING_FROM_HOUR = 18
        const val EVENING_UNTIL_HOUR = 5
    }
}

@HiltViewModel
class DayViewModel @Inject constructor(
    private val plans: DayPlanRepository,
    private val morning: MorningCoordinator,
) : ViewModel() {

    private val justSaved = MutableStateFlow(false)

    private val clock = flow {
        while (true) {
            emit(LocalDateTime.now())
            delay(TICK_MS)
        }
    }

    val state: StateFlow<DayUiState> =
        combine(plans.latest, plans.latestEvening, clock, justSaved) { plan, evening, now, saved ->
            DayUiState(
                plan = plan,
                now = now,
                eveningAnswered = evening?.dayKey?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                eveningJustSaved = saved,
                loaded = true,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DayUiState())

    /** «Сегодня» is on screen: rebuild today's plan and count it as seen (no morning push then). */
    fun opened() {
        viewModelScope.launch { runCatching { morning.onTodayOpened() } }
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

    private companion object {
        const val TICK_MS = 60_000L
    }
}
