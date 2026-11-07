package com.healthofatitan.app.feature.checkin

import com.healthofatitan.app.core.database.HotDatabase
import com.healthofatitan.app.core.database.dao.CheckinDao
import com.healthofatitan.app.core.database.dao.DayDao
import com.healthofatitan.app.core.database.dao.MetricDao
import com.healthofatitan.app.core.database.entity.CheckinAnswerEntity
import com.healthofatitan.app.core.database.entity.CheckinDayEntity
import com.healthofatitan.app.core.database.entity.CheckinDiscoveryEntity
import com.healthofatitan.app.core.database.entity.CheckinEventEntity
import com.healthofatitan.app.core.database.entity.CheckinPrivateEntity
import com.healthofatitan.app.core.datastore.CheckinPrefs
import com.healthofatitan.app.core.datastore.CheckinSettings
import com.healthofatitan.app.core.security.FieldCipher
import com.healthofatitan.app.domain.checkin.Answer
import com.healthofatitan.app.domain.checkin.CardId
import com.healthofatitan.app.domain.checkin.Codec
import com.healthofatitan.app.domain.checkin.DayRecord
import com.healthofatitan.app.domain.checkin.DeckContext
import com.healthofatitan.app.domain.checkin.DeckEngine
import com.healthofatitan.app.domain.checkin.DeckSettings
import com.healthofatitan.app.domain.checkin.Discoveries
import com.healthofatitan.app.domain.checkin.Discovery
import com.healthofatitan.app.domain.checkin.Link
import com.healthofatitan.app.domain.checkin.Mood
import com.healthofatitan.app.domain.checkin.MorningMark
import com.healthofatitan.app.domain.checkin.Mornings
import com.healthofatitan.app.domain.checkin.Phase
import com.healthofatitan.app.domain.checkin.PrivateAnswer
import com.healthofatitan.app.domain.checkin.PrivateLayer
import com.healthofatitan.app.domain.checkin.Reveal
import com.healthofatitan.app.domain.checkin.Sky
import com.healthofatitan.app.domain.checkin.SpecialistRoute
import com.healthofatitan.app.domain.checkin.StormNet
import com.healthofatitan.app.domain.checkin.StormReason
import com.healthofatitan.app.domain.checkin.Tile
import com.healthofatitan.app.domain.checkin.Usage
import com.healthofatitan.app.domain.checkin.UsageEvent
import com.healthofatitan.app.domain.checkin.UsageKind
import com.healthofatitan.app.domain.checkin.WeatherMark
import com.healthofatitan.app.domain.engine.CrisisAssessment
import com.healthofatitan.app.domain.engine.CrisisLevel
import com.healthofatitan.app.domain.engine.CrisisReason
import com.healthofatitan.app.domain.engine.CrisisSource
import com.healthofatitan.app.domain.engine.Regime
import com.healthofatitan.app.domain.metrics.Metrics
import com.healthofatitan.app.integration.whoop.WhoopConnection
import com.healthofatitan.app.integration.whoop.day.DailyLoopRepository
import com.healthofatitan.app.integration.whoop.day.DayPlanRepository
import com.healthofatitan.app.integration.whoop.day.MorningCoordinator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** The morning as «Сегодня», the deck and the reveal see it. */
data class MorningState(
    val now: LocalDateTime,
    val settings: DeckSettings,
    val prefs: CheckinPrefs,
    val day: CheckinDayEntity?,
    /** WHOOP has scored the night (today's plan exists, built from data). */
    val planToday: Boolean,
    val regime: Regime?,
    val whoopConnected: Boolean,
    /** HoT Заряд level 1…5 of the night (ECAP@1), null while WHOOP has not scored it. */
    val dataLevel: Int?,
) {
    val today: LocalDate get() = now.toLocalDate()
    val wakeDone: Boolean get() = day?.wakeDoneAt != null
    val wakeDoneAt: LocalDateTime?
        get() = day?.wakeDoneAt?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()) }
    val gentle: Boolean get() = regime == Regime.GENTLE

    /** The reveal is waiting for WHOOP, or is ready and not yet seen. */
    val revealReady: Boolean get() = wakeDone && day?.revealSeenAt == null && dataLevel != null
}

/** The morning screen the app opens by itself, if any. */
enum class MorningDue { NONE, WAKE, REVEAL }

/** What the reveal shows besides the two Titans. */
data class RevealFacts(val matched: Int, val total: Int, val mornings: Int)

/**
 * The state cards' data (A44 §7): answers in `checkin_answer`, S5 encrypted in `checkin_private`,
 * the morning in `checkin_day`, discoveries and usage events. The S5 layer never leaves this
 * class in plain text except to the screen that unlocked it; nothing here talks to a network.
 */
@Singleton
class CheckinRepository @Inject constructor(
    private val dao: CheckinDao,
    private val metricDao: MetricDao,
    private val dayDao: DayDao,
    private val settingsStore: CheckinSettings,
    private val cipher: FieldCipher,
    private val plans: DayPlanRepository,
    private val morning: MorningCoordinator,
    private val loop: DailyLoopRepository,
    private val whoop: WhoopConnection,
    private val clock: Clock,
) {
    private val user = HotDatabase.LOCAL_USER

    private fun today(): LocalDate = LocalDate.now(clock)

    val prefs: Flow<CheckinPrefs> = settingsStore.prefs

    val settings: Flow<DeckSettings> = prefs.map(::toSettings)

    private val minutes: Flow<LocalDateTime> = flow {
        while (true) {
            emit(LocalDateTime.now(clock))
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: Flow<MorningState> = minutes.map { it.toLocalDate() }.flatMapLatestDistinct { day ->
        combine(
            minutes,
            prefs,
            dao.observeDay(user, day.toString()),
            plans.latest,
            whoop.status,
        ) { now, p, row, plan, status ->
            val planToday = plan?.dayKey == day
            MorningState(
                now = now,
                settings = toSettings(p),
                prefs = p,
                day = row,
                planToday = planToday,
                regime = plan?.regime?.takeIf { planToday },
                whoopConnected = status.connected,
                dataLevel = if (planToday) dataLevel(day) else null,
            )
        }
    }

    /**
     * What the app opens by itself (D-036): the blind deck in the wake window until it is done,
     * then the reveal once WHOOP has scored the night. «Рассвет» waits while this is not NONE.
     */
    val due: Flow<MorningDue> = state.map { s ->
        when {
            !s.wakeDone && DeckEngine.wakeWindow(s.now) -> MorningDue.WAKE
            s.revealReady -> MorningDue.REVEAL
            else -> MorningDue.NONE
        }
    }.distinctUntilChanged()

    /** HoT Заряд (ECAP@1) level of the night → 1…5; never a raw WHOOP value. */
    suspend fun dataLevel(day: LocalDate): Int? =
        metricDao.metricsForDays(user, listOf(day.toString()))
            .firstOrNull { it.metricId == Metrics.ECAP.id && it.metricVersion == Metrics.ECAP.version }
            ?.level.let(Reveal::dataLevel)

    suspend fun context(day: LocalDate, planToday: Boolean): DeckContext {
        val evening = dayDao.evening(user, day.minusDays(1).toString())
        return DeckContext(day = day, whoopSawNight = planToday, eveningAlcoholMarked = evening?.alcohol != null)
    }

    /** Yesterday's Р1 for «как вчера». */
    suspend fun yesterdayRitual(day: LocalDate): Answer? =
        dao.answers(user, day.minusDays(1).toString()).firstOrNull { it.card == CardId.RITUAL.ref }?.toAnswer()

    suspend fun answers(day: LocalDate): Map<CardId, Answer> =
        dao.answers(user, day.toString()).mapNotNull { it.toAnswer() }.associateBy { it.card }

    /** Each answer is saved at once (partial save, И2.1′). S5 cards never come here. */
    suspend fun save(day: LocalDate, answers: Collection<Answer>) {
        val rows = answers.filter { !it.card.private }.map { a ->
            CheckinAnswerEntity(
                userId = user,
                dayKey = day.toString(),
                card = a.card.ref,
                phase = a.card.phase.name,
                level = a.level.takeIf { !a.skipped },
                choices = if (a.skipped) "" else Codec.choices(a.choices),
                count = a.count.takeIf { !a.skipped },
                skipped = a.skipped,
                answeredAt = a.atMs.takeIf { it > 0 } ?: clock.millis(),
            )
        }
        if (rows.isNotEmpty()) dao.upsertAnswers(rows)
    }

    // ── S5 «Только ты» ──────────────────────────────────────────────────────────────────────

    private fun ad(day: String, card: CardId) = "checkin_private:$day:${card.ref}".toByteArray()

    /**
     * Encrypted with K_field and bound to its row; plain text exists only in memory. Stored as
     * `intimacy@1` and `pulls@1` (A44 §7), one encrypted row each.
     */
    suspend fun savePrivate(day: LocalDate, answer: PrivateAnswer) {
        if (answer.empty) return
        val key = day.toString()
        val at = clock.millis()
        fun row(card: CardId, part: PrivateAnswer) =
            CheckinPrivateEntity(user, key, card.ref, cipher.encrypt(Codec.encodePrivate(part).toByteArray(), ad(key, card)), at)
        dao.upsertPrivate(
            listOfNotNull(
                answer.intimacy?.let { row(CardId.INTIMACY, PrivateAnswer(intimacy = it)) },
                if (answer.pulls.isNotEmpty() || answer.wantLess) row(CardId.PULLS, PrivateAnswer(pulls = answer.pulls, wantLess = answer.wantLess)) else null,
            ),
        )
    }

    suspend fun saveCloseness(day: LocalDate, level: Int) {
        val key = day.toString()
        dao.upsertPrivate(listOf(CheckinPrivateEntity(user, key, CardId.WEEK_CLOSENESS.ref, cipher.encrypt(level.toString().toByteArray(), ad(key, CardId.WEEK_CLOSENESS)), clock.millis())))
    }

    /** Decrypted only for the discoveries «под замком» and the specialist count. */
    private suspend fun privateFrom(from: LocalDate): Map<LocalDate, PrivateAnswer> =
        dao.privateFrom(user, from.toString())
            .filter { it.card == CardId.INTIMACY.ref || it.card == CardId.PULLS.ref }
            .mapNotNull { row ->
                val card = CardId.fromRef(row.card) ?: return@mapNotNull null
                runCatching { LocalDate.parse(row.dayKey) to Codec.decodePrivate(String(cipher.decrypt(row.payload, ad(row.dayKey, card)))) }.getOrNull()
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, parts) ->
                PrivateAnswer(
                    intimacy = parts.firstNotNullOfOrNull { it.intimacy },
                    pulls = parts.flatMap { it.pulls }.toSet(),
                    wantLess = parts.any { it.wantLess },
                )
            }

    /** «Выключить и удалить всё» (A44 §6.4). */
    suspend fun deleteAllPrivate() {
        dao.deletePrivate(user)
        dao.deletePrivateDiscoveries(user)
        settingsStore.update { it.copy(intimacy = false, pulls = false, links = false) }
    }

    /** A44 §6.4 маршрут: «хотелось меньше» ≥ 3 in 14 days → one soft offer, again after 30 days. */
    suspend fun specialistDue(): Boolean {
        val p = prefs.first()
        if (!p.pulls) return false
        val today = today()
        val days = privateFrom(today.minusDays(SpecialistRoute.WINDOW_DAYS)).filterValues { it.wantLess }.keys
        return SpecialistRoute.due(days, today, p.specialistDay?.let { runCatching { LocalDate.parse(it) }.getOrNull() })
    }

    suspend fun specialistShown() = settingsStore.update { it.copy(specialistDay = today().toString()) }

    // ── The morning ─────────────────────────────────────────────────────────────────────────

    suspend fun wakeDone(day: LocalDate, quick: Boolean, youLevel: Int?) {
        val old = dao.day(user, day.toString())
        dao.upsertDay(
            (old ?: CheckinDayEntity(user, day.toString())).copy(
                wakeDoneAt = old?.wakeDoneAt ?: clock.millis(),
                quick = quick,
                youLevel = youLevel ?: old?.youLevel,
            ),
        )
        // the plan reads the deck now (A44 §5: SG-SELF, SAF-03, EVE-01); no push from here
        runCatching { plans.refresh() }
    }

    suspend fun revealSeen(day: LocalDate, dataLevel: Int?) {
        val old = dao.day(user, day.toString()) ?: CheckinDayEntity(user, day.toString())
        dao.upsertDay(old.copy(revealSeenAt = old.revealSeenAt ?: clock.millis(), dataLevel = dataLevel ?: old.dataLevel))
    }

    suspend fun ritualDone(day: LocalDate) {
        val old = dao.day(user, day.toString()) ?: CheckinDayEntity(user, day.toString())
        dao.upsertDay(old.copy(ritualDoneAt = clock.millis()))
    }

    suspend fun revealFacts(day: LocalDate): RevealFacts {
        val rows = dao.observeDaysFrom(user, day.minusDays(89).toString()).first()
        val (n, m) = Reveal.matched(rows.mapNotNull { r -> r.youLevel?.let { y -> r.dataLevel?.let { y to it } } })
        val mornings = Mornings.withYou(rows.filter { it.wakeDoneAt != null }.map { LocalDate.parse(it.dayKey) }, day)
        return RevealFacts(n, m, mornings)
    }

    // ── Storm net (И2.8′) ───────────────────────────────────────────────────────────────────

    /**
     * After a «Погода» answer: the net on the weather of morning and evening. Fires at most every
     * 72 h; the event is logged without any words (A07 §4.7) and the caller opens «Как ты на
     * самом деле?». S5 is never read.
     */
    suspend fun stormCheck(source: CrisisSource): StormReason? {
        val now = clock.instant()
        val today = today()
        val rows = dao.answersFrom(user, today.minusDays(6).toString())
        val marks = rows.filter { !it.skipped && (it.card == CardId.WEATHER.ref || it.card == CardId.EVENING_WEATHER.ref) }
            .mapNotNull { r -> Sky.of(r.level)?.let { WeatherMark(Instant.ofEpochMilli(r.answeredAt), it, Mood.EMPTY.name in Codec.choices(r.choices)) } }
        val byDay = rows.groupBy { it.dayKey }
        val mornings = byDay.map { (d, rs) ->
            MorningMark(
                LocalDate.parse(d),
                rs.firstOrNull { it.card == CardId.CHARGE.ref && !it.skipped }?.level,
                Sky.of(rs.firstOrNull { it.card == CardId.WEATHER.ref && !it.skipped }?.level),
            )
        }
        val last = prefs.first().stormShownAt?.let(Instant::ofEpochMilli)
        val reason = StormNet.check(marks, mornings, now, last) ?: return null
        settingsStore.update { it.copy(stormShownAt = now.toEpochMilli()) }
        runCatching { loop.logSafety(CrisisAssessment(CrisisLevel.CLARIFY, CrisisReason.PATTERN), source) }
        return reason
    }

    suspend fun quiet(): Boolean = StormNet.quiet(prefs.first().stormShownAt?.let(Instant::ofEpochMilli), clock.instant())

    // ── Discoveries (И2.21) ─────────────────────────────────────────────────────────────────

    /** 90 days of records: answers, HoT WHOOP z-values, workouts, the evening alcohol; S5 only with links. */
    suspend fun records(links: Boolean): List<DayRecord> {
        val today = today()
        val from = today.minusDays(Discoveries.WINDOW_DAYS)
        val answers = dao.answersFrom(user, from.toString()).mapNotNull { r -> r.toAnswer()?.let { LocalDate.parse(r.dayKey) to it } }
            .groupBy({ it.first }, { it.second })
        val sleep = metricDao.series(user, Metrics.SLP.id, Metrics.SLP.version, from.toString()).associate { it.dayKey to it.z }
        val resting = metricDao.series(user, Metrics.RHR_Z.id, Metrics.RHR_Z.version, from.toString()).associate { it.dayKey to it.value }
        val trained = dayDao.sessions(user, from.minusDays(1).toString()).map { it.dayKey }.toSet()
        val only = if (links) privateFrom(from) else emptyMap()
        val haveWhoop = sleep.isNotEmpty()
        return answers.keys.sorted().map { d ->
            DayRecord(
                day = d,
                answers = answers[d].orEmpty().associateBy { it.card },
                only = only[d],
                sleepZ = sleep[d.toString()],
                restingZ = resting[d.toString()],
                trainedBefore = if (haveWhoop) d.minusDays(1).toString() in trained else null,
                eveningAlcohol = dayDao.evening(user, d.minusDays(1).toString())?.alcohol,
            )
        }
    }

    val shownDiscoveries: Flow<List<CheckinDiscoveryEntity>> = dao.observeDiscoveries(user)

    /** The next discovery to reveal now (≤ 1 a week, none in the quiet days), or null. */
    suspend fun nextDiscovery(gentle: Boolean): Discovery? {
        val p = prefs.first()
        if (!p.discoveries) return null
        val shown = dao.discoveries(user).mapNotNull { r -> Link.entries.firstOrNull { it.name == r.pairId }?.let { it to Instant.ofEpochMilli(r.shownAt) } }.toMap()
        return Discoveries.next(records(p.links), today(), clock.instant(), shown, p.links, gentle, quiet())
    }

    suspend fun markShown(d: Discovery) {
        dao.insertDiscovery(
            CheckinDiscoveryEntity(
                userId = user,
                pairId = d.pair.name,
                n1 = d.n1,
                n0 = d.n0,
                effect = d.effect,
                ciLow = d.ciLow,
                ciHigh = d.ciHigh,
                method = Discoveries.METHOD,
                shownAt = clock.millis(),
                sensitivity = if (d.pair.s5) "S5" else "S3",
            ),
        )
    }

    suspend fun collection(): List<Tile> {
        val p = prefs.first()
        val shown = dao.discoveries(user).mapNotNull { r -> Link.entries.firstOrNull { it.name == r.pairId }?.let { it to Instant.ofEpochMilli(r.shownAt) } }.toMap()
        return Discoveries.collection(records(p.links), today(), shown, p.links)
    }

    // ── Usage metrics (И2.18) ───────────────────────────────────────────────────────────────

    suspend fun log(events: List<UsageEvent>) {
        if (events.isEmpty()) return
        val at = clock.millis()
        dao.insertEvents(
            events.map { e ->
                CheckinEventEntity(userId = user, dayKey = e.day.toString(), phase = e.phase.name, card = e.card?.ref, kind = e.kind.name, durationMs = e.durationMs, at = at)
            },
        )
    }

    suspend fun usage(): List<UsageEvent> =
        dao.eventsFrom(user, today().minusDays(60).toString()).mapNotNull { e ->
            val phase = Phase.entries.firstOrNull { it.name == e.phase } ?: return@mapNotNull null
            val kind = UsageKind.entries.firstOrNull { it.name == e.kind } ?: return@mapNotNull null
            UsageEvent(LocalDate.parse(e.dayKey), phase, e.card?.let(CardId::fromRef), kind, e.durationMs)
        }

    suspend fun removalOffers(): List<CardId> {
        val p = prefs.first()
        val first = dao.firstDay(user)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val off = p.off.mapNotNull(CardId::fromRef).toSet()
        val dismissed = p.removalDismissed.mapNotNull(CardId::fromRef).toSet()
        return Usage.removalOffers(usage(), first, today(), off, dismissed)
    }

    suspend fun updatePrefs(change: (CheckinPrefs) -> CheckinPrefs) = settingsStore.update(change)

    /** «Сегодня» was opened: the plan for today (no push from here: A34 owns the morning push). */
    suspend fun touchMorning() = runCatching { morning.onTodayOpened() }

    companion object {
        fun toSettings(p: CheckinPrefs) = DeckSettings(
            off = p.off.mapNotNull(CardId::fromRef).toSet(),
            quick = p.quick,
            privateLayer = PrivateLayer(p.intimacy, p.pulls, p.links),
            ritual = p.ritual,
            ritualReminder = p.ritualReminder,
            evening = p.evening,
            discoveries = p.discoveries,
            haptics = p.haptics,
            drinks = p.drinks,
            stack = p.stack,
        )
    }
}

internal fun CheckinAnswerEntity.toAnswer(): Answer? {
    val id = CardId.fromRef(card) ?: return null
    return Answer(id, level, Codec.choices(choices), count, skipped, answeredAt)
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun <T, R> Flow<T>.flatMapLatestDistinct(transform: (T) -> Flow<R>): Flow<R> =
    distinctUntilChanged().flatMapLatest(transform)
