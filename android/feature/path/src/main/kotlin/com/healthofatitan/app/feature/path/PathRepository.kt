package com.healthofatitan.app.feature.path

import com.healthofatitan.app.core.database.HotDatabase
import com.healthofatitan.app.core.database.ProfileStore
import com.healthofatitan.app.core.database.dao.PathDao
import com.healthofatitan.app.core.database.entity.PathGoalComponentEntity
import com.healthofatitan.app.core.database.entity.PathGoalEntity
import com.healthofatitan.app.core.database.entity.PathGoalVersionEntity
import com.healthofatitan.app.core.database.entity.PathHorizonEntity
import com.healthofatitan.app.core.database.entity.PathLetterEntity
import com.healthofatitan.app.core.database.entity.PathMarkEntity
import com.healthofatitan.app.core.model.profile.ProfileCategory
import com.healthofatitan.app.core.model.profile.UserConstraints
import com.healthofatitan.app.core.ui.component.HotMode
import com.healthofatitan.app.domain.engine.Regime
import com.healthofatitan.app.domain.metrics.Metrics
import com.healthofatitan.app.domain.path.ChangeContext
import com.healthofatitan.app.domain.path.ChangeEngine
import com.healthofatitan.app.domain.path.ChangeProposal
import com.healthofatitan.app.domain.path.ChangeResult
import com.healthofatitan.app.domain.path.ChangeType
import com.healthofatitan.app.domain.path.ComponentKey
import com.healthofatitan.app.domain.path.GivenLayer
import com.healthofatitan.app.domain.path.GoalChange
import com.healthofatitan.app.domain.path.GoalCodec
import com.healthofatitan.app.domain.path.GoalComponent
import com.healthofatitan.app.domain.path.GoalKind
import com.healthofatitan.app.domain.path.GoalLayer
import com.healthofatitan.app.domain.path.GoalLifecycle
import com.healthofatitan.app.domain.path.GoalMode
import com.healthofatitan.app.domain.path.GoalStatus
import com.healthofatitan.app.domain.path.GoalVersion
import com.healthofatitan.app.domain.path.HorizonRules
import com.healthofatitan.app.domain.path.Initiator
import com.healthofatitan.app.domain.path.LetterOpen
import com.healthofatitan.app.domain.path.Obstacle
import com.healthofatitan.app.domain.path.PathFlags
import com.healthofatitan.app.domain.path.PathOverview
import com.healthofatitan.app.domain.path.PathOverviewBuilder
import com.healthofatitan.app.domain.path.PauseReason
import com.healthofatitan.app.domain.path.PracticalGoal
import com.healthofatitan.app.domain.path.Refusal
import com.healthofatitan.app.domain.path.Role
import com.healthofatitan.app.domain.path.Sufficiency
import com.healthofatitan.app.feature.path.logic.PathGiven
import com.healthofatitan.app.integration.whoop.day.DayPlanRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * The feature flags of «Путь». Weight and strength goals stay off until the tables of safe pace
 * exist (R7 blocker, D-047); switching the flag is a code change with the review of Agents 3 and 12.
 */
object PathFeatureFlags {
    val current: PathFlags = PathFlags(weightAndStrength = false, allTemplates = false)

    /**
     * D-051 (tech §6.4): the slice of the MVP is static and the Lottie of the elements become their
     * posters. The breathing, the looks and the halo of the lever are built and wait behind this flag.
     */
    const val LIVING_SLICE: Boolean = false

    /** D-051: the letter of Э7 comes later; the screen and the table are ready. */
    const val LETTER: Boolean = false
}

/** Everything the screen «Путь» and the ritual read. */
data class PathData(
    val today: LocalDate,
    val overview: PathOverview,
    val goals: List<PracticalGoal> = emptyList(),
    val versions: List<GoalVersion> = emptyList(),
    val given: GivenLayer = GivenLayer.NONE,
    val mode: HotMode? = null,
    /** The average of the last 7 nights, hours: the hint of Э1 and the sufficiency of sleep (R5). */
    val sleepHours: Double? = null,
    val marks: Map<ComponentKey, Int> = emptyMap(),
) {
    val goal: PracticalGoal? get() = overview.goal

    val sufficiency: List<Sufficiency>
        get() = listOfNotNull(sleepHours?.let { Sufficiency(ComponentKey.SLEEP, it, SLEEP_THRESHOLD) })

    companion object {
        const val SLEEP_THRESHOLD = 7.0

        fun empty(today: LocalDate) = PathData(today, PathOverview.empty(today))
    }
}

/**
 * «Путь» from stored HoT data (A56 §1): goals, parts, versions, the horizon and marks from
 * `path_*`, sleep (SLP) for the lever's sufficiency, today's mode from the day plan and «Дано»
 * from the profile of specifics. Pure rules live in `:domain:path`; nothing here talks to the
 * network, and no text of a goal leaves the phone (P-21).
 */
class PathRepository @Inject constructor(
    private val dao: PathDao,
    private val plans: DayPlanRepository,
    private val profiles: ProfileStore,
    private val clock: Clock,
) {
    private val user = HotDatabase.LOCAL_USER

    @OptIn(ExperimentalCoroutinesApi::class)
    val data: Flow<PathData> = flowOf(Unit).flatMapLatest {
        val today = LocalDate.now(clock)
        val from = today.minusDays(SLEEP_DAYS).toString()
        val stored = combine(dao.observeGoals(user), dao.observeComponents(user), dao.observeVersions(user)) { goals, parts, versions ->
            val byGoal = parts.groupBy { it.goalId }
            val list = goals.mapNotNull { toGoal(it, byGoal[it.id].orEmpty()) }
            list to versions.mapNotNull { v -> toVersion(v, list.firstOrNull { it.id == v.goalId }) }
        }
        val context = combine(plans.latest, profiles.profile) { plan, profile ->
            val regime = plan?.takeIf { it.dayKey == today }?.regime
            regime to PathGiven.from(codes(profile.constraints))
        }
        combine(
            stored,
            dao.observeHorizons(user),
            dao.observeMarks(user),
            dao.observeMetric(user, Metrics.SLP.id, Metrics.SLP.version, from),
            context,
        ) { (goals, versions), horizons, marks, sleep, (regime, given) ->
            val goal = goals.firstOrNull { GoalLifecycle.layer(it.status) == GoalLayer.PATH }
            val taken = marks.filter { it.goalId == goal?.id }
                .mapNotNull { m -> ComponentKey.fromId(m.component)?.let { it to m.mark } }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, v) -> v.max() }
            val transferred = horizons.firstOrNull { it.goalId == goal?.id }?.transferred.orEmpty()
                .split(',').mapNotNull { ComponentKey.fromId(it.trim()) }.toSet()
            val hours = sleep.mapNotNull { it.value }.takeIf { it.isNotEmpty() }?.average()
            val data = PathData(today, PathOverview.empty(today), goals, versions, given, regime?.toMode(), hours, taken)
            data.copy(
                overview = PathOverviewBuilder.build(
                    today = today,
                    goal = goal,
                    marks = taken,
                    transferred = transferred,
                    given = given,
                    sufficiency = data.sufficiency,
                    heavy = regime == Regime.GENTLE,
                ),
            )
        }
    }

    /** «Беру путь» (Э7): the goal, its parts, version 1, the horizon with the first fire, the letter. */
    suspend fun start(goal: PracticalGoal, firstFire: ComponentKey?, letter: String?): Long {
        val now = clock.millis()
        val id = dao.saveGoal(
            toEntity(goal, now),
            toComponents(goal),
            PathGoalVersionEntity(userId = user, goalId = 0, v = 1, snapshot = GoalCodec.encode(goal), changeType = ChangeType.CREATE.name, initiator = Initiator.PERSON.name, reason = null, at = now),
        )
        dao.upsertHorizon(PathHorizonEntity(id, user, firstFire?.id.orEmpty(), 0, now))
        letter?.trim()?.takeIf { it.isNotEmpty() }?.let {
            dao.upsertLetter(PathLetterEntity(id, user, it.take(300), LetterOpen.MILESTONE_8.name, LocalDate.now(clock).toString(), null))
        }
        return id
    }

    /** A mark taken by a measurement (self-report): the fire moves when it reaches the target (P-14). */
    suspend fun markTaken(key: ComponentKey, mark: Int): Boolean {
        val d = data.first()
        val goal = d.goal ?: return false
        val now = clock.millis()
        dao.insertMark(PathMarkEntity(user, goal.id, key.id, mark, d.today.toString(), SOURCE_SELF, now))
        val already = dao.horizon(goal.id)?.transferred.orEmpty().split(',').mapNotNull { ComponentKey.fromId(it.trim()) }.toSet()
        val marks = d.marks + (key to maxOf(mark, d.marks[key] ?: 0))
        val moved = HorizonRules.transfers(goal, marks, already)
        dao.upsertHorizon(PathHorizonEntity(goal.id, user, moved.joinToString(",") { it.id }, HorizonRules.step(goal.start, d.today, 0), now))
        return key in moved && key !in already
    }

    /** A card «было → станет» (A56 §3): nothing changes until «Применить». */
    suspend fun propose(change: GoalChange, weekNow: Boolean = false): ChangeResult {
        val d = data.first()
        val goal = d.goal ?: return ChangeResult.Refused(Refusal.NOTHING_TO_UNDO)
        if (change.type == ChangeType.COSMETIC) return save(ChangeEngine.applyCosmetic(goal, change, ctx(d)))
        return ChangeEngine.propose(goal, change, Initiator.PERSON, ctx(d), weekNow)
    }

    suspend fun apply(proposal: ChangeProposal, override: Boolean = false): ChangeResult {
        val d = data.first()
        val goal = d.goal ?: d.goals.firstOrNull { it.id == proposal.goalId } ?: return ChangeResult.Refused(Refusal.STALE)
        return save(ChangeEngine.apply(goal, proposal, Initiator.PERSON, clock.millis(), override))
    }

    /** «Вернуть как было» (P-12). */
    suspend fun undo(): ChangeResult {
        val d = data.first()
        val goal = d.goal ?: d.goals.maxByOrNull { it.takenAt } ?: return ChangeResult.Refused(Refusal.NOTHING_TO_UNDO)
        return save(ChangeEngine.undo(goal, d.versions, clock.millis()))
    }

    private suspend fun save(result: ChangeResult): ChangeResult {
        if (result is ChangeResult.Applied) {
            val g = result.goal
            val existing = dao.goal(g.id)
            dao.saveGoal(
                toEntity(g, existing?.createdAt ?: clock.millis(), existing?.legacySphere),
                toComponents(g),
                PathGoalVersionEntity(
                    userId = user, goalId = g.id, v = result.version.v, snapshot = GoalCodec.encode(g),
                    changeType = result.version.changeType.name, initiator = result.version.initiator.name,
                    reason = result.version.reason, at = result.version.at,
                ),
            )
        }
        return result
    }

    private fun ctx(d: PathData) = ChangeContext(
        nowMs = clock.millis(),
        today = d.today,
        versions = d.versions,
        given = d.given,
        flags = PathFeatureFlags.current,
        others = d.goals.filter { it.id != d.goal?.id && GoalLifecycle.layer(it.status) == GoalLayer.PATH },
    )

    private fun toEntity(g: PracticalGoal, createdAt: Long, legacy: String? = null) = PathGoalEntity(
        id = g.id,
        userId = user,
        title = g.title,
        why = g.why,
        beName = g.beName,
        kind = g.kind.name,
        template = g.templateId,
        startDay = g.start.toString(),
        deadline = g.deadline.toString(),
        stage = g.stage,
        mode = g.mode.name,
        status = g.status.name,
        criteria = GoalCodec.encodeCriteria(g.criteria),
        obstacle = g.obstacle?.text,
        ifThen = g.obstacle?.ifThen,
        version = g.version,
        pausedDays = g.pausedDays,
        pauseFrom = g.pauseFrom?.toString(),
        pauseReason = g.pauseReason?.id,
        pauseUntil = g.pauseUntil?.toString(),
        takenAt = g.takenAt,
        releaseToQueue = g.releaseToQueue,
        createdAt = createdAt,
        endedDay = if (GoalLifecycle.layer(g.status) == GoalLayer.PATH) null else LocalDate.now(clock).toString(),
        legacySphere = legacy,
    )

    private fun toComponents(g: PracticalGoal) = g.components.mapIndexed { i, c ->
        PathGoalComponentEntity(user, g.id, c.key.id, c.role.name, c.targetMark, c.startMark, c.safeVersion, c.isLever, i)
    }

    private companion object {
        const val SLEEP_DAYS = 7L
        const val SOURCE_SELF = "SELF"
    }
}

/** A row of `path_goal` with its parts → the domain goal; an unreadable row is skipped. */
internal fun toGoal(e: PathGoalEntity, parts: List<PathGoalComponentEntity>): PracticalGoal? = runCatching {
    PracticalGoal(
        id = e.id,
        title = e.title,
        why = e.why,
        beName = e.beName,
        kind = runCatching { GoalKind.valueOf(e.kind) }.getOrDefault(GoalKind.DO),
        templateId = e.template,
        start = LocalDate.parse(e.startDay),
        deadline = LocalDate.parse(e.deadline),
        stage = e.stage,
        mode = runCatching { GoalMode.valueOf(e.mode) }.getOrDefault(GoalMode.BY_DATE),
        status = GoalStatus.parse(e.status) ?: return@runCatching null,
        criteria = GoalCodec.decodeCriteria(e.criteria),
        obstacle = e.obstacle?.let { Obstacle(it, e.ifThen.orEmpty()) },
        components = parts.sortedBy { it.sortOrder }.mapNotNull { c ->
            val key = ComponentKey.fromId(c.component) ?: return@mapNotNull null
            val role = runCatching { Role.valueOf(c.role) }.getOrNull() ?: return@mapNotNull null
            GoalComponent(key, role, c.targetMark, c.startMark, c.safeVersion, c.isLever)
        },
        version = e.version,
        pausedDays = e.pausedDays,
        pauseFrom = e.pauseFrom?.let(LocalDate::parse),
        pauseReason = PauseReason.fromId(e.pauseReason),
        pauseUntil = e.pauseUntil?.let(LocalDate::parse),
        takenAt = e.takenAt,
        releaseToQueue = e.releaseToQueue,
    )
}.getOrNull()

internal fun toVersion(v: PathGoalVersionEntity, goal: PracticalGoal?): GoalVersion? {
    val type = runCatching { ChangeType.valueOf(v.changeType) }.getOrNull() ?: return null
    val by = runCatching { Initiator.valueOf(v.initiator) }.getOrDefault(Initiator.PERSON)
    val snapshot = GoalCodec.decode(v.snapshot, v.goalId, v.v) ?: goal?.takeIf { it.version == v.v }
    return GoalVersion(v.goalId, v.v, snapshot, type, by, v.reason, v.at)
}

/** Injuries, life stage and medical flags of the profile of specifics: the codes «Дано» reads. */
internal fun codes(c: UserConstraints): Set<String> =
    (c.codes(ProfileCategory.INJURIES) + c.codes(ProfileCategory.LIFE_STAGE) + c.codes(ProfileCategory.MEDICAL)).toSet()

fun Regime.toMode(): HotMode = when (this) {
    Regime.BOOST -> HotMode.RAZGON
    Regime.STEADY -> HotMode.ROVNO
    Regime.GENTLE -> HotMode.BEREZHNO
}
