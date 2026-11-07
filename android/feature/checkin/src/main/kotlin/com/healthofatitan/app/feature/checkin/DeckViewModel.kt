package com.healthofatitan.app.feature.checkin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthofatitan.app.domain.checkin.Answer
import com.healthofatitan.app.domain.checkin.CardId
import com.healthofatitan.app.domain.checkin.DeckContext
import com.healthofatitan.app.domain.checkin.DeckEngine
import com.healthofatitan.app.domain.checkin.DeckSettings
import com.healthofatitan.app.domain.checkin.DeckState
import com.healthofatitan.app.domain.checkin.Phase
import com.healthofatitan.app.domain.checkin.PrivateAnswer
import com.healthofatitan.app.domain.checkin.RitualDelta
import com.healthofatitan.app.domain.checkin.TitanLook
import com.healthofatitan.app.domain.checkin.UsageEvent
import com.healthofatitan.app.domain.checkin.UsageKind
import com.healthofatitan.app.domain.checkin.Yesterday
import com.healthofatitan.app.domain.engine.CrisisSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

/** What the deck screen draws. */
data class DeckUi(
    val phase: Phase,
    val day: LocalDate,
    val deck: DeckState,
    val settings: DeckSettings,
    val context: DeckContext,
    /** Р1: yesterday's stack put in («как вчера»). */
    val prefill: Set<String> = emptySet(),
    /** The wake Заряд for the ritual delta. */
    val wakeCharge: Int? = null,
    val wakeAt: LocalDateTime? = null,
    /** S5 unlocked in this pass of the deck only. */
    val unlocked: Boolean = false,
    val finished: Boolean = false,
) {
    val look: TitanLook get() = TitanLook.of(deck.answers.values)
    val yesterdayOptions: List<Yesterday> get() = DeckEngine.yesterdayOptions(settings, context)
    val delta: Int? get() = RitualDelta.of(wakeCharge, deck.answers[CardId.CHARGE_AFTER]?.takeIf { !it.skipped }?.level)
}

/** One-off events for navigation. */
sealed interface DeckEvent {
    /** The wake deck is done: open the reveal. */
    data object Reveal : DeckEvent

    /** The storm net fired: «Как ты на самом деле?». */
    data object Care : DeckEvent
}

/**
 * The deck (И2.1′, И2.13′, И2.19): every answer is saved at once, a skip is a skip, «Быстро»
 * leaves two cards, «назад» goes one step. S5 answers go to the encrypted table only. Usage events
 * carry the time on a card, never the answer (И2.18).
 */
@HiltViewModel
class DeckViewModel @Inject constructor(private val repo: CheckinRepository) : ViewModel() {

    private val _ui = MutableStateFlow<DeckUi?>(null)
    val ui: StateFlow<DeckUi?> = _ui

    private val _events = MutableSharedFlow<DeckEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<DeckEvent> = _events

    private var cardShownAt = 0L
    private var deckStartedAt = 0L
    private var started: Phase? = null

    fun start(phase: Phase) {
        if (started == phase) return
        started = phase
        viewModelScope.launch {
            val morning = repo.state.first()
            val day = morning.today
            val settings = morning.settings
            val ctx = repo.context(day, morning.planToday)
            val saved = repo.answers(day)
            val cards = when (phase) {
                Phase.RITUAL -> DeckEngine.ritual(settings.copy(ritual = true))
                else -> DeckEngine.wake(settings, ctx)
            }
            // resume a deck closed half-way: what is answered stays, the deck opens at the first open card
            val kept = saved.filterKeys { it in cards }
            val first = cards.indexOfFirst { it !in kept }.let { if (it < 0) cards.size else it }
            val deck = DeckState(cards, if (phase == Phase.RITUAL) 0 else first, kept)
            _ui.value = DeckUi(
                phase = phase,
                day = day,
                deck = if (deck.done && phase == Phase.WAKE) DeckState(cards, 0, kept) else deck,
                settings = settings,
                context = ctx,
                prefill = if (phase == Phase.RITUAL) saved[CardId.RITUAL]?.takeIf { !it.skipped }?.choices ?: DeckEngine.prefillRitual(repo.yesterdayRitual(day)) else emptySet(),
                wakeCharge = saved[CardId.CHARGE]?.takeIf { !it.skipped }?.level,
                wakeAt = morning.wakeDoneAt,
            )
            deckStartedAt = System.currentTimeMillis()
            cardShownAt = deckStartedAt
        }
    }

    private fun spent(): Long {
        val now = System.currentTimeMillis()
        return (now - cardShownAt).also { cardShownAt = now }
    }

    fun answer(answer: Answer) {
        val ui = _ui.value ?: return
        val a = answer.copy(atMs = System.currentTimeMillis())
        val next = ui.copy(deck = ui.deck.answer(a))
        _ui.value = next
        val took = spent()
        viewModelScope.launch {
            repo.save(ui.day, listOf(a))
            repo.log(listOf(UsageEvent(ui.day, ui.phase, a.card, UsageKind.ANSWERED, took)))
            if (a.card == CardId.WEATHER && repo.stormCheck(CrisisSource.JOURNAL) != null) _events.emit(DeckEvent.Care)
        }
        if (next.deck.done) finish()
    }

    fun answerPrivate(answer: PrivateAnswer) {
        val ui = _ui.value ?: return
        val placeholder = Answer(CardId.ONLY_YOU, atMs = System.currentTimeMillis())
        val next = ui.copy(deck = ui.deck.answer(placeholder), unlocked = false)
        _ui.value = next
        val took = spent()
        viewModelScope.launch {
            repo.savePrivate(ui.day, answer)
            repo.log(listOf(UsageEvent(ui.day, ui.phase, CardId.ONLY_YOU, UsageKind.ANSWERED, took)))
        }
        if (next.deck.done) finish()
    }

    fun unlocked() {
        _ui.value = _ui.value?.copy(unlocked = true)
    }

    fun skip() {
        val ui = _ui.value ?: return
        val card = ui.deck.current ?: return
        val next = ui.copy(deck = ui.deck.skip(System.currentTimeMillis()), unlocked = false)
        _ui.value = next
        val took = spent()
        viewModelScope.launch {
            repo.save(ui.day, listOf(Answer.skip(card, System.currentTimeMillis())))
            repo.log(listOf(UsageEvent(ui.day, ui.phase, card, UsageKind.SKIPPED, took)))
        }
        if (next.deck.done) finish()
    }

    fun back() {
        val ui = _ui.value ?: return
        _ui.value = ui.copy(deck = ui.deck.back(), unlocked = false)
    }

    fun quick() {
        val ui = _ui.value ?: return
        val next = ui.copy(deck = ui.deck.quick(), unlocked = false)
        _ui.value = next
        viewModelScope.launch { repo.log(listOf(UsageEvent(ui.day, ui.phase, null, UsageKind.DECK_QUICK))) }
        if (next.deck.done) finish()
    }

    /** Closed with ✕: the answers so far are saved already (partial save). */
    fun leave() {
        val ui = _ui.value ?: return
        if (ui.finished) return
        viewModelScope.launch { repo.log(listOf(UsageEvent(ui.day, ui.phase, null, UsageKind.DECK_LEFT, System.currentTimeMillis() - deckStartedAt))) }
    }

    private fun finish() {
        val ui = _ui.value ?: return
        if (ui.finished) return
        _ui.value = ui.copy(finished = true)
        val took = System.currentTimeMillis() - deckStartedAt
        viewModelScope.launch {
            repo.log(listOf(UsageEvent(ui.day, ui.phase, null, UsageKind.DECK_DONE, took)))
            when (ui.phase) {
                Phase.RITUAL -> repo.ritualDone(ui.day)
                else -> {
                    val quick = ui.deck.cards == DeckEngine.QUICK
                    repo.wakeDone(ui.day, quick, ui.deck.answers[CardId.CHARGE]?.takeIf { !it.skipped }?.level)
                    _events.emit(DeckEvent.Reveal)
                }
            }
        }
    }

    fun addToStack(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch { repo.updatePrefs { p -> if (clean in p.stack) p else p.copy(stack = p.stack + clean) } }
        _ui.value = _ui.value?.let { it.copy(settings = it.settings.copy(stack = it.settings.stack + clean)) }
    }
}
