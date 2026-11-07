package com.healthofatitan.app.feature.week

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthofatitan.app.domain.today.week.RitualAnswers
import com.healthofatitan.app.domain.today.week.Sex
import com.healthofatitan.app.domain.today.WeekGoal
import com.healthofatitan.app.integration.whoop.week.WeekRepository
import com.healthofatitan.app.integration.whoop.week.WeekScreenState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What «Неделя» draws: the week (null while it loads), what the person touched on the screen. */
data class WeekUiState(
    val week: WeekScreenState? = null,
    /** Patterns taken as «Попробовать на неделе»: they join the next week in the ritual. */
    val tried: List<WeekGoal> = emptyList(),
)

@HiltViewModel
class WeekViewModel @Inject constructor(private val repo: WeekRepository) : ViewModel() {

    private val tried = MutableStateFlow<List<WeekGoal>>(emptyList())

    val state: StateFlow<WeekUiState> = combine(
        repo.observe().map<WeekScreenState, WeekScreenState?> { it }.catch { emit(null) },
        tried,
    ) { week, t -> WeekUiState(week, t) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeekUiState())

    fun lighter(goalId: String) = launch { repo.changeTarget(goalId, -1) }

    fun heavier(goalId: String) = launch { repo.changeTarget(goalId, +1) }

    fun remove(goalId: String) = launch { repo.removeGoal(goalId) }

    fun addReminder(title: String, days: Int) = launch { repo.addReminder(title, days) }

    fun tryPattern(goal: WeekGoal) {
        if (tried.value.none { it.id == goal.id }) tried.value = tried.value + goal
    }

    fun saveInputs(age: Int?, sex: Sex?, waist: Double?) = launch { repo.saveBodyInputs(age, sex, waist) }

    fun finishRitual(answers: RitualAnswers) {
        val ritual = state.value.week?.ritual ?: return
        launch {
            repo.finishRitual(ritual, ritual.skew, answers.copy(tried = answers.tried + tried.value))
            tried.value = emptyList()
        }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { runCatching { block() } }
    }
}
