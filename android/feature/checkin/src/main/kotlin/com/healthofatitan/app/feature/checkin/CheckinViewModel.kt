package com.healthofatitan.app.feature.checkin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthofatitan.app.core.datastore.CheckinPrefs
import com.healthofatitan.app.core.datastore.MentorPrefs
import com.healthofatitan.app.core.datastore.MentorSettings
import com.healthofatitan.app.domain.checkin.Answer
import com.healthofatitan.app.domain.checkin.CardId
import com.healthofatitan.app.domain.checkin.DeckEngine
import com.healthofatitan.app.domain.checkin.Discovery
import com.healthofatitan.app.domain.checkin.Phase
import com.healthofatitan.app.domain.checkin.Tile
import com.healthofatitan.app.domain.checkin.Usage
import com.healthofatitan.app.domain.checkin.UsageEvent
import com.healthofatitan.app.domain.engine.CrisisSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** What the «Сегодня» card of the state cards shows, in order of importance. */
data class TodayCheckin(
    val morning: MorningState? = null,
    val discovery: Discovery? = null,
    val removal: CardId? = null,
    val specialist: Boolean = false,
) {
    val privateOffer: Boolean get() = morning?.prefs?.privateAsked == false
    val wakeDue: Boolean get() = morning?.let { !it.wakeDone && DeckEngine.wakeWindow(it.now) } == true
    val revealReady: Boolean get() = morning?.revealReady == true
    val ritualDue: Boolean get() = morning?.let { it.settings.ritual && DeckEngine.ritualDue(it.wakeDoneAt, it.day?.ritualDoneAt != null, it.now) } == true
}

/** The reveal: both levels, the line, the counts. */
data class RevealUi(
    val day: LocalDate,
    val you: Int?,
    val data: Int?,
    val quick: Boolean,
    val gentle: Boolean,
    val whoopConnected: Boolean,
    val regime: com.healthofatitan.app.domain.engine.Regime?,
    val facts: RevealFacts,
    val ritualLater: Boolean,
)

data class SettingsUi(val prefs: CheckinPrefs = CheckinPrefs(), val wakeSec: Long? = null, val ritualSec: Long? = null, val skips: List<Pair<CardId, Double>> = emptyList())

/**
 * The state cards around the deck: the «Сегодня» card, the reveal, the discoveries and the
 * collection, «Что спрашивать», the evening additions and the storm net.
 */
@HiltViewModel
class CheckinViewModel @Inject constructor(
    private val repo: CheckinRepository,
    mentor: MentorSettings,
) : ViewModel() {

    val mentorPrefs: StateFlow<MentorPrefs> = mentor.prefs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MentorPrefs())

    val morning: StateFlow<MorningState?> = repo.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _today = MutableStateFlow(TodayCheckin())
    val today: StateFlow<TodayCheckin> = _today

    private val _reveal = MutableStateFlow<RevealUi?>(null)
    val reveal: StateFlow<RevealUi?> = _reveal

    private val _discovery = MutableStateFlow<Discovery?>(null)
    val discovery: StateFlow<Discovery?> = _discovery

    private val _collection = MutableStateFlow<List<Tile>>(emptyList())
    val collection: StateFlow<List<Tile>> = _collection

    private val _settings = MutableStateFlow(SettingsUi())
    val settings: StateFlow<SettingsUi> = _settings

    private val _care = MutableSharedFlow<Unit>(extraBufferCapacity = 2)
    /** The storm net fired in the evening weather: open «Как ты на самом деле?». */
    val care: SharedFlow<Unit> = _care

    init {
        viewModelScope.launch {
            repo.state.collect { m ->
                _today.value = _today.value.copy(morning = m)
            }
        }
    }

    /** «Сегодня» opened: the rare cards (discovery, removal offer, specialist) are checked once. */
    fun refreshToday() {
        viewModelScope.launch {
            val m = repo.state.first()
            _today.value = _today.value.copy(
                morning = m,
                discovery = runCatching { repo.nextDiscovery(m.gentle) }.getOrNull(),
                removal = runCatching { repo.removalOffers().firstOrNull() }.getOrNull(),
                specialist = runCatching { repo.specialistDue() }.getOrDefault(false),
            )
        }
    }

    fun privateOffer(yes: Boolean) {
        viewModelScope.launch { repo.updatePrefs { it.copy(privateAsked = true, intimacy = yes || it.intimacy, pulls = yes || it.pulls) } }
    }

    fun removal(card: CardId, remove: Boolean) {
        viewModelScope.launch {
            repo.updatePrefs { p -> if (remove) p.copy(off = p.off + card.ref) else p.copy(removalDismissed = p.removalDismissed + card.ref) }
        }
        _today.value = _today.value.copy(removal = null)
    }

    fun specialistSeen() {
        viewModelScope.launch { repo.specialistShown() }
        _today.value = _today.value.copy(specialist = false)
    }

    fun loadReveal() {
        viewModelScope.launch {
            val m = repo.state.first()
            val answers = repo.answers(m.today)
            val you = answers[CardId.CHARGE]?.takeIf { !it.skipped }?.level
            val data = m.dataLevel
            if (data != null) repo.revealSeen(m.today, data)
            _reveal.value = RevealUi(
                day = m.today,
                you = you,
                data = data,
                quick = m.day?.quick == true,
                gentle = m.gentle,
                whoopConnected = m.whoopConnected,
                regime = m.regime,
                facts = repo.revealFacts(m.today),
                ritualLater = m.settings.ritual,
            )
        }
    }

    fun loadDiscovery() {
        viewModelScope.launch {
            val m = repo.state.first()
            val d = _today.value.discovery ?: repo.nextDiscovery(m.gentle)
            if (d != null) repo.markShown(d)
            _discovery.value = d
            _collection.value = repo.collection()
        }
    }

    fun loadCollection() {
        viewModelScope.launch { _collection.value = repo.collection() }
    }

    fun loadSettings() {
        viewModelScope.launch {
            val events = repo.usage()
            _settings.value = SettingsUi(
                prefs = repo.prefs.first(),
                wakeSec = Usage.medianSeconds(events, Phase.WAKE),
                ritualSec = Usage.medianSeconds(events, Phase.RITUAL),
                skips = CardId.entries.mapNotNull { c -> Usage.skipShare(events, c)?.let { c to it } }.filter { it.second > 0.0 },
            )
        }
        viewModelScope.launch { repo.prefs.collect { p -> _settings.value = _settings.value.copy(prefs = p) } }
    }

    fun updatePrefs(change: (CheckinPrefs) -> CheckinPrefs) {
        viewModelScope.launch { repo.updatePrefs(change) }
    }

    fun deleteAllPrivate() {
        viewModelScope.launch { repo.deleteAllPrivate() }
    }

    // ── Evening additions to «Закрыть день» ─────────────────────────────────────────────────

    /** В-3 Погода вечера, «Где?» zones, the Sunday week: saved at once; the weather runs the net. */
    fun saveEvening(day: LocalDate, answer: Answer) {
        viewModelScope.launch {
            repo.save(day, listOf(answer.copy(atMs = System.currentTimeMillis())))
            repo.log(listOf(UsageEvent(day, answer.card.phase, answer.card, if (answer.skipped) com.healthofatitan.app.domain.checkin.UsageKind.SKIPPED else com.healthofatitan.app.domain.checkin.UsageKind.ANSWERED)))
            if (answer.card == CardId.EVENING_WEATHER && !answer.skipped && repo.stormCheck(CrisisSource.EVENING) != null) _care.emit(Unit)
        }
    }

    fun saveCloseness(day: LocalDate, level: Int) {
        viewModelScope.launch { repo.saveCloseness(day, level) }
    }

    private val _evening = MutableStateFlow<Map<CardId, Answer>>(emptyMap())
    val evening: StateFlow<Map<CardId, Answer>> = _evening

    fun loadEvening(day: LocalDate) {
        viewModelScope.launch { _evening.value = repo.answers(day) }
    }
}
