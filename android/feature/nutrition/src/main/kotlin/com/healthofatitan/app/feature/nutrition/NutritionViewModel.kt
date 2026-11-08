package com.healthofatitan.app.feature.nutrition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthofatitan.app.core.model.profile.AllergySeverity
import com.healthofatitan.app.core.model.profile.ProfileCategory
import com.healthofatitan.app.core.model.profile.UserConstraints
import com.healthofatitan.app.domain.nutrition.DayCondition
import com.healthofatitan.app.domain.nutrition.EveningMark
import com.healthofatitan.app.domain.nutrition.Level
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDateTime
import javax.inject.Inject

/** All food screens share one view model: the card on «Сегодня», the meal card, profile and level. */
@HiltViewModel
class NutritionViewModel @Inject constructor(private val repo: NutritionRepository) : ViewModel() {

    private val clock = flow {
        while (true) {
            emit(LocalDateTime.now())
            delay(TICK_MS)
        }
    }

    val state: StateFlow<NutritionState> = repo.state(clock)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NutritionState())

    /** Taps faster than the database round trip build on the last edit, not on a stale state. */
    private var draft: UserConstraints? = null

    /** A fair lock keeps the writes in tap order. */
    private val writes = Mutex()

    private val profile: UserConstraints get() = draft ?: state.value.profile.constraints

    fun toggleChip(c: DayCondition) = repo.toggleChip(c)

    fun replace(slot: String) = repo.replace(slot)

    fun setEvening(mark: EveningMark) = launch { repo.setEvening(mark) }

    fun consent(granted: Boolean) {
        draft = null
        launch { repo.setConsent(granted) }
    }

    /** Withdraws S4 (D-058); [keepEdCare] is the answer to «Оставить бережный режим еды?». */
    fun withdrawConsent(keepEdCare: Boolean) {
        draft = null
        launch { repo.withdrawConsent(keepEdCare) }
    }

    fun setLevel(level: Level) = launch { repo.setLevel(level) }

    /** One tap: none → «избегаю» → «аллергия» → «тяжёлая реакция» → none. */
    fun cycleAllergen(id: String) = edit { p ->
        val next = p.allergies[id]?.next() ?: if (id in p.allergies) null else AllergySeverity.AVOID
        p.copy(allergies = if (next == null) p.allergies - id else p.allergies + (id to next))
    }

    /** «Ем всё» clears the other diets; any other diet clears «Ем всё». */
    fun toggleDiet(id: String) = edit { p ->
        val on = id !in p.diets
        val diets = when {
            !on -> p.diets - id
            id == OMNIVORE -> setOf(OMNIVORE)
            else -> p.diets - OMNIVORE + id
        }
        p.copy(diets = diets)
    }

    fun toggleIntolerance(id: String) = edit { p -> p.copy(intolerances = p.intolerances.toggle(id)) }

    fun toggleLifeStage(id: String) = edit { p -> p.copy(lifeStage = p.lifeStage.toggle(id)) }

    fun toggleMedical(id: String) = edit { p -> p.copy(medical = p.medical.toggle(id)) }

    fun toggleInjury(id: String) = edit { p -> p.copy(injuries = p.injuries.toggle(id)) }

    fun delete(category: ProfileCategory) {
        draft = draft?.without(category)
        launch { repo.delete(category) }
    }

    fun deleteAll() {
        draft = null
        launch { repo.deleteAll() }
    }

    private fun edit(change: (UserConstraints) -> UserConstraints) {
        if (!state.value.profile.consented) return
        val next = change(profile)
        draft = next
        launch { writes.withLock { repo.save(next) } }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private fun Set<String>.toggle(id: String) = if (id in this) this - id else this + id

    private companion object {
        const val TICK_MS = 60_000L
        const val OMNIVORE = "omnivore"
    }
}
