package com.healthofatitan.app.integration.whoop.safety

import com.healthofatitan.app.core.database.HotDatabase
import com.healthofatitan.app.core.database.dao.EventLogDao
import com.healthofatitan.app.core.database.entity.EventLogEntity
import com.healthofatitan.app.core.model.safety.SafetyEventKinds
import com.healthofatitan.app.domain.safety.EdCareReason
import com.healthofatitan.app.domain.safety.EdCareReturn
import com.healthofatitan.app.domain.safety.JournalEvent
import com.healthofatitan.app.domain.safety.SafetyEvents
import com.healthofatitan.app.domain.safety.SafetyProjector
import com.healthofatitan.app.domain.safety.SafetyState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.Instant

/**
 * The safety bus `SafetyState` (A58 §2, A60 §3, D-051): the one place «Сегодня», «Неделя» and
 * «Путь» read safety from. It holds no state of its own — every read projects the event journal
 * (`event_log`, A58 §8) at the current moment, so a crisis window closes by the clock and
 * `ED_CARE` ends only by an event the person wrote.
 *
 * Writers: the crisis log (CrisisGuard ≥ 1, [crisis]), the profile of specifics (`ED_CARE` entry,
 * `ProfileStore`), and the person's own choices of the exit and the return steps.
 *
 * A withdrawal of S4 (D-058) deletes every `safety.ed_care.*` event. With «Да» to «Оставить
 * бережный режим еды?» one entry `self_choice` from the source
 * [SafetyEventKinds.SOURCE_CONSENT_KEEP] stays (consent `ed_care_keep`): the bus reads it like any
 * entry, so `ED_CARE` stays on without the old reason, and the exit is the person's choice.
 */
class SafetyBus(
    private val dao: EventLogDao,
    private val clock: Clock,
    /** How often the state is projected again without a new event (a crisis window may close). */
    private val tickMs: Long = TICK_MS,
) {
    private val user = HotDatabase.LOCAL_USER

    private val ticks: Flow<Long> = flow {
        while (true) {
            emit(clock.millis())
            delay(tickMs)
        }
    }

    val state: Flow<SafetyState> =
        combine(dao.observe(user, SafetyEvents.PREFIX), ticks) { rows, now -> project(rows, now) }.distinctUntilChanged()

    suspend fun current(): SafetyState = project(dao.list(user, SafetyEvents.PREFIX), clock.millis())

    /** A07 §4.7: the level, the source and the time — never the words. */
    suspend fun crisis(level: Int, source: String, at: Long = clock.millis()) {
        if (level <= 0) return
        append(SafetyEvents.CRISIS, level.toString(), source, at)
    }

    /** `ED_CARE` began for [reason]; a second entry while it is on keeps the longer cooldown. */
    suspend fun enterEdCare(reason: EdCareReason, source: String = SOURCE_PERSON) =
        append(SafetyEvents.ED_CARE_ENTER, reason.code, source)

    /**
     * The person chose to leave `ED_CARE` (the three questions of the end of the cooldown). Before
     * the cooldown ends the choice changes nothing and is not written.
     *
     * @return true if the mode is over now
     */
    suspend fun chooseEdCareExit(): Boolean {
        if (!current().edCare.exitOpen(clock.millis())) return false
        append(SafetyEvents.ED_CARE_EXIT, null, SOURCE_PERSON)
        return !current().edCare.active
    }

    /** The person chose a step of the return (1–3). */
    suspend fun chooseEdCareStep(step: Int) = append(SafetyEvents.ED_CARE_STEP, step.toString(), SOURCE_PERSON)

    /** The person took one thing back on step 3; calories are never among them. */
    suspend fun chooseEdCareReturn(item: EdCareReturn) = append(SafetyEvents.ED_CARE_RETURN, item.code, SOURCE_PERSON)

    private suspend fun append(kind: String, payload: String?, source: String, at: Long = clock.millis()) {
        dao.append(EventLogEntity(userId = user, kind = kind, payload = payload, source = source, at = at, dayKey = Instant.ofEpochMilli(at).atZone(clock.zone).toLocalDate().toString()))
    }

    private fun project(rows: List<EventLogEntity>, now: Long): SafetyState =
        SafetyProjector.project(rows.map { JournalEvent(it.id, it.kind, it.payload, it.at, it.source) }, now)

    companion object {
        const val TICK_MS = 60_000L
        const val SOURCE_PERSON = "person"
    }
}
