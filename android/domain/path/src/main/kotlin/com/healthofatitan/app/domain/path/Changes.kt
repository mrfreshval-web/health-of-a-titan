package com.healthofatitan.app.domain.path

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** The kind of a change (A56 §3): [structural] ones count for the soft limit of P-08. */
enum class ChangeType(val structural: Boolean) {
    CREATE(false),
    COSMETIC(false),
    DEADLINE(true),
    MODE(true),
    COMPOSITION(true),
    TARGET(true),
    LEVER(true),
    RESTART(true),
    PAUSE(false),
    RESUME(false),
    RELEASE(false),
    CLOSE(false),
    SAFETY(false),
    HOLD(false),
    CROSSROADS(true),
    STAGE(false),
    UNDO(false),
    MIGRATED(false),
}

/** One step of the crossroads (A56 §3 «Перекрёсток»): nothing is chosen in advance. */
enum class CrossroadsOption { SHIFT_DEADLINE, LESS_BY_DATE, REBUILD_FROM_TODAY, EASE_LEAD, SWITCH_LEVER, AS_IS }

/** Why HoT itself takes sleep off the lever role (A56 §3.1): «без цифр сна» or > 2 stages. */
enum class LeverReason { PERSON, HOT_PROPOSAL, SLEEP_STEP_DOWN }

/** Every change of a goal (A56 §3). */
sealed interface GoalChange {
    val type: ChangeType

    data class Rename(val title: String) : GoalChange { override val type = ChangeType.COSMETIC }
    data class SetWhy(val why: String?) : GoalChange { override val type = ChangeType.COSMETIC }
    data class SetBeName(val name: String?) : GoalChange { override val type = ChangeType.COSMETIC }
    data class MoveDeadline(val deadline: LocalDate) : GoalChange { override val type = ChangeType.DEADLINE }
    data class SetMode(val mode: GoalMode) : GoalChange { override val type = ChangeType.MODE }
    data class AddComponent(val key: ComponentKey, val role: Role) : GoalChange { override val type = ChangeType.COMPOSITION }
    data class RemoveComponent(val key: ComponentKey) : GoalChange { override val type = ChangeType.COMPOSITION }
    data class ReplaceComponent(val old: ComponentKey, val new: ComponentKey) : GoalChange { override val type = ChangeType.COMPOSITION }
    data class SetRole(val key: ComponentKey, val role: Role) : GoalChange { override val type = ChangeType.COMPOSITION }
    data class SetTargetMark(val key: ComponentKey, val mark: Int?) : GoalChange { override val type = ChangeType.TARGET }
    data class SetCriteria(val criteria: List<Criterion>) : GoalChange { override val type = ChangeType.TARGET }
    data class SetLever(val key: ComponentKey, val reason: LeverReason = LeverReason.PERSON) : GoalChange {
        override val type = ChangeType.LEVER
    }
    data object RestartFromToday : GoalChange { override val type = ChangeType.RESTART }
    data class Pause(val reason: PauseReason, val until: LocalDate?) : GoalChange { override val type = ChangeType.PAUSE }
    data object Resume : GoalChange { override val type = ChangeType.RESUME }
    data class Release(val toQueue: Boolean = true) : GoalChange { override val type = ChangeType.RELEASE }
    data object Close : GoalChange { override val type = ChangeType.CLOSE }

    /** A new line of «Дано» (trauma, a stop signal): dangerous parts get the safe version at once. */
    data class ApplyGiven(val given: GivenLayer) : GoalChange { override val type = ChangeType.SAFETY }
    data object Hold : GoalChange { override val type = ChangeType.HOLD }
    data object Unhold : GoalChange { override val type = ChangeType.HOLD }
    data object NextStage : GoalChange { override val type = ChangeType.STAGE }
    data class Crossroads(val option: CrossroadsOption, val inner: GoalChange?) : GoalChange {
        override val type = ChangeType.CROSSROADS
    }
}

/**
 * The table of powers (A56 §3.1). Safety HoT changes itself and at once; rebuilds it only proposes;
 * the goal, its date, parts and «Зачем» belong to the person. The mentor goes through the diff only.
 */
object Authority {
    /** HoT itself, with an explanation, no card (P-17 exemption). */
    fun hotSelf(change: GoalChange): Boolean = when (change) {
        is GoalChange.ApplyGiven, GoalChange.Hold -> true
        is GoalChange.SetLever -> change.reason == LeverReason.SLEEP_STEP_DOWN
        else -> false
    }

    /** HoT proposes, a tap is needed: a pause by a body signal, rebuilds, the lever, the next stage. */
    fun hotProposes(change: GoalChange): Boolean = when (change) {
        is GoalChange.Pause, is GoalChange.AddComponent, is GoalChange.RemoveComponent, is GoalChange.ReplaceComponent,
        is GoalChange.SetRole, is GoalChange.SetLever, is GoalChange.SetCriteria, GoalChange.NextStage, GoalChange.RestartFromToday,
        -> true
        else -> false
    }

    fun mayInitiate(change: GoalChange, by: Initiator): Boolean = when (by) {
        Initiator.PERSON -> !hotSelf(change) || change is GoalChange.ApplyGiven
        Initiator.HOT -> hotSelf(change) || hotProposes(change)
        Initiator.MENTOR -> !hotSelf(change)
    }
}

/** What a change does to «Неделя», «Сегодня» and the experiment (A56 §3 step 1, P-13, P-15). */
data class Effects(
    /** Only safety changes today's tasks; everything else starts tomorrow (P-13). */
    val todayChanges: Boolean,
    /** The day the week goals change: the next Monday, or today if the person chose «сейчас». */
    val weekFrom: LocalDate?,
    val today: String,
    val week: String,
    val experiment: String,
)

/** A card «было → станет» (A56 §1 `ChangeProposal`). */
data class ChangeProposal(
    val goalId: Long,
    val fromVersion: Int,
    val change: GoalChange,
    val before: PracticalGoal,
    val after: PracticalGoal,
    val effects: Effects,
    val initiator: Initiator,
    val createdAt: Long,
    val expiresAt: Long,
    /** P-08: a second rebuild this week — «Посмотрим в воскресенье?» with «Всё равно поменять». */
    val soft: Boolean = false,
    /** P-10: the third move of the deadline — the mentor offers a talk. Nothing is blocked. */
    val suggestTalk: Boolean = false,
)

/** A version of a goal (A56 §1 `GoalVersion`): every change but the tasks of the day makes one (P-11). */
data class GoalVersion(
    val goalId: Long,
    val v: Int,
    val snapshot: PracticalGoal?,
    val changeType: ChangeType,
    val initiator: Initiator,
    val reason: String? = null,
    val at: Long,
)

enum class Refusal {
    NOT_ALLOWED,
    INVALID,
    STALE,
    EXPIRED,
    SOFT_LIMIT,
    LEVER_TOO_SOON,
    LAST_WATER,
    NOTHING_TO_UNDO,
    UNDO_WINDOW_OVER,
    TOO_LONG,
}

sealed interface ChangeResult {
    data class Proposed(val proposal: ChangeProposal) : ChangeResult
    data class Applied(val goal: PracticalGoal, val version: GoalVersion) : ChangeResult
    data class Refused(val reason: Refusal, val violations: List<Violation> = emptyList()) : ChangeResult
}

/** The context of a change: time, history and the filters. */
data class ChangeContext(
    val nowMs: Long,
    val today: LocalDate,
    val versions: List<GoalVersion> = emptyList(),
    val given: GivenLayer = GivenLayer.NONE,
    val flags: PathFlags = PathFlags.DEFAULT,
    val others: List<PracticalGoal> = emptyList(),
)

/**
 * One path for every change (A56 §3): proposal → «Применить · Не менять» → a new version →
 * «Вернуть как было». Deterministic; the rules P-05, P-08…P-17 live here.
 */
object ChangeEngine {
    const val TITLE_MAX = 60
    const val WHY_MAX = 120
    const val CALIBRATION_DAYS = 7L
    const val LEVER_MIN_DAYS = 28L
    const val PROPOSAL_TTL_MS = 24L * 3600_000L
    const val UNDO_MS = 24L * 3600_000L
    const val UNDO_CROSSROADS_MS = 7L * 24 * 3600_000L

    fun propose(goal: PracticalGoal, change: GoalChange, by: Initiator, ctx: ChangeContext, weekNow: Boolean = false): ChangeResult {
        if (!Authority.mayInitiate(change, by)) return ChangeResult.Refused(Refusal.NOT_ALLOWED)
        if (by == Initiator.HOT && change is GoalChange.SetLever && leverChangedRecently(ctx)) {
            return ChangeResult.Refused(Refusal.LEVER_TOO_SOON)
        }
        val after = when (val t = transform(goal, change, ctx)) {
            is Transformed.Ok -> t.goal
            is Transformed.No -> return ChangeResult.Refused(t.reason, t.violations)
        }
        val violations = PathValidator.goal(after, ctx.given, ctx.flags, ctx.others)
        if (violations.isNotEmpty()) return ChangeResult.Refused(Refusal.INVALID, violations)
        return ChangeResult.Proposed(
            ChangeProposal(
                goalId = goal.id,
                fromVersion = goal.version,
                change = change,
                before = goal,
                after = after,
                effects = effects(change, ctx.today, weekNow),
                initiator = by,
                createdAt = ctx.nowMs,
                expiresAt = ctx.nowMs + PROPOSAL_TTL_MS,
                soft = softLimited(goal, change, ctx),
                suggestTalk = change.deadlineMove() && ctx.versions.count { it.goalId == goal.id && it.isDeadlineMove() } >= 2,
            ),
        )
    }

    /**
     * «Применить»: only the person confirms a card (P-17; the mentor's card too, by the person's
     * «да»). [override] is «Всё равно поменять» of the soft limit (P-08).
     */
    fun apply(goal: PracticalGoal, proposal: ChangeProposal, confirmedBy: Initiator, nowMs: Long, override: Boolean = false, reason: String? = null): ChangeResult {
        if (confirmedBy != Initiator.PERSON) return ChangeResult.Refused(Refusal.NOT_ALLOWED)
        if (proposal.goalId != goal.id || proposal.fromVersion != goal.version) return ChangeResult.Refused(Refusal.STALE)
        if (nowMs > proposal.expiresAt) return ChangeResult.Refused(Refusal.EXPIRED)
        if (proposal.soft && !override) return ChangeResult.Refused(Refusal.SOFT_LIMIT)
        val why = reason ?: (proposal.change as? GoalChange.Crossroads)?.option?.name
        return applied(goal, proposal.after, proposal.change.type, proposal.initiator, nowMs, why)
    }

    /** What HoT does itself (A56 §3.1 «HoT сам»): safety, hold, sleep off the lever. No card. */
    fun applySelf(goal: PracticalGoal, change: GoalChange, ctx: ChangeContext): ChangeResult {
        if (!Authority.hotSelf(change)) return ChangeResult.Refused(Refusal.NOT_ALLOWED)
        val after = when (val t = transform(goal, change, ctx)) {
            is Transformed.Ok -> t.goal
            is Transformed.No -> return ChangeResult.Refused(t.reason, t.violations)
        }
        return applied(goal, after, change.type, Initiator.HOT, ctx.nowMs, null)
    }

    /**
     * A cosmetic change by the person (name, «Зачем», the phrase «быть»): at once, without a card
     * (A56 §3 «сразу новая формулировка»), but with a version (P-11).
     */
    fun applyCosmetic(goal: PracticalGoal, change: GoalChange, ctx: ChangeContext): ChangeResult {
        if (change.type != ChangeType.COSMETIC) return ChangeResult.Refused(Refusal.NOT_ALLOWED)
        val after = when (val t = transform(goal, change, ctx)) {
            is Transformed.Ok -> t.goal
            is Transformed.No -> return ChangeResult.Refused(t.reason, t.violations)
        }
        return applied(goal, after, change.type, Initiator.PERSON, ctx.nowMs, null)
    }

    /**
     * «Вернуть как было» (P-12): 24 h after a change and after «Отпустить», 7 days after a choice on
     * the crossroads. Safety is never undone (P-05). What was done — marks, fires, «Взято» — is not
     * in the snapshot and is never rolled back (P-14).
     */
    fun undo(goal: PracticalGoal, versions: List<GoalVersion>, nowMs: Long): ChangeResult {
        val sorted = versions.filter { it.goalId == goal.id }.sortedBy { it.v }
        val last = sorted.lastOrNull { it.v == goal.version } ?: return ChangeResult.Refused(Refusal.NOTHING_TO_UNDO)
        if (last.changeType in setOf(ChangeType.CREATE, ChangeType.MIGRATED, ChangeType.SAFETY, ChangeType.HOLD, ChangeType.UNDO)) {
            return ChangeResult.Refused(Refusal.NOTHING_TO_UNDO)
        }
        val window = if (last.changeType == ChangeType.CROSSROADS) UNDO_CROSSROADS_MS else UNDO_MS
        if (nowMs - last.at > window) return ChangeResult.Refused(Refusal.UNDO_WINDOW_OVER)
        val previous = sorted.lastOrNull { it.v < last.v }?.snapshot ?: return ChangeResult.Refused(Refusal.NOTHING_TO_UNDO)
        return applied(goal, previous.copy(id = goal.id), ChangeType.UNDO, Initiator.PERSON, nowMs, null)
    }

    private fun applied(goal: PracticalGoal, after: PracticalGoal, type: ChangeType, by: Initiator, nowMs: Long, reason: String?): ChangeResult.Applied {
        val next = after.copy(id = goal.id, version = goal.version + 1)
        return ChangeResult.Applied(next, GoalVersion(goal.id, next.version, next, type, by, reason, nowMs))
    }

    /** P-08: 7 days of calibration, then ≤ 1 structural rebuild per goal per week; safety, pause and release are free. */
    fun softLimited(goal: PracticalGoal, change: GoalChange, ctx: ChangeContext): Boolean {
        if (!change.type.structural) return false
        if (ctx.nowMs - goal.takenAt < CALIBRATION_DAYS * DAY_MS) return false
        val monday = StageRules.weekStart(ctx.today)
        return ctx.versions.any { v ->
            v.goalId == goal.id && v.changeType.structural && !day(v.at).isBefore(monday)
        }
    }

    /** P-09: HoT changes the lever not more often than every 4 weeks (safety aside). */
    private fun leverChangedRecently(ctx: ChangeContext): Boolean = ctx.versions.any { v ->
        v.changeType == ChangeType.LEVER && v.initiator == Initiator.HOT && ctx.nowMs - v.at < LEVER_MIN_DAYS * DAY_MS
    }

    /** P-13 and P-15: today only by safety, the week from Monday or now, the experiment holds. */
    fun effects(change: GoalChange, today: LocalDate, weekNow: Boolean): Effects {
        val safety = change.type == ChangeType.SAFETY
        val cosmetic = change.type == ChangeType.COSMETIC
        val weekFrom = when {
            cosmetic -> null
            weekNow || safety -> today
            else -> today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
        }
        return Effects(
            todayChanges = safety,
            weekFrom = weekFrom,
            today = when {
                safety -> PathCopy.EFFECT_TODAY_SAFETY
                change is GoalChange.Pause || change is GoalChange.Release -> PathCopy.EFFECT_TODAY_PAUSE
                else -> PathCopy.EFFECT_TODAY_SAME
            },
            week = when {
                cosmetic -> PathCopy.EFFECT_WEEK_NONE
                weekNow || safety -> PathCopy.EFFECT_WEEK_NOW
                else -> PathCopy.EFFECT_WEEK_NEXT
            },
            experiment = PathCopy.EFFECT_EXPERIMENT_SAME,
        )
    }

    private sealed interface Transformed {
        data class Ok(val goal: PracticalGoal) : Transformed
        data class No(val reason: Refusal, val violations: List<Violation> = emptyList()) : Transformed
    }

    private fun ok(goal: PracticalGoal) = Transformed.Ok(goal)

    private fun transform(goal: PracticalGoal, change: GoalChange, ctx: ChangeContext): Transformed = when (change) {
        is GoalChange.Rename -> change.title.trim().let { t ->
            if (t.isEmpty() || t.length > TITLE_MAX) Transformed.No(Refusal.TOO_LONG) else ok(goal.copy(title = t))
        }
        is GoalChange.SetWhy -> change.why?.trim()?.takeIf { it.isNotEmpty() }.let { w ->
            if (w != null && w.length > WHY_MAX) Transformed.No(Refusal.TOO_LONG) else ok(goal.copy(why = w))
        }
        is GoalChange.SetBeName -> change.name?.trim()?.takeIf { it.isNotEmpty() }.let { n ->
            if (n != null && n.length > TITLE_MAX) Transformed.No(Refusal.TOO_LONG) else ok(goal.copy(beName = n))
        }
        is GoalChange.MoveDeadline -> PathValidator.deadline(ctx.today, change.deadline)
            ?.let { Transformed.No(Refusal.INVALID, listOf(it)) }
            ?: ok(goal.copy(deadline = change.deadline))
        is GoalChange.SetMode -> ok(goal.copy(mode = change.mode))
        is GoalChange.AddComponent -> if (goal.has(change.key)) {
            Transformed.No(Refusal.INVALID)
        } else {
            val safe = ctx.given.constraintOf(change.key)?.safeVersion
            ok(goal.copy(components = goal.components + GoalComponent(change.key, change.role, safeVersion = safe)))
        }
        is GoalChange.RemoveComponent -> when {
            !goal.has(change.key) -> Transformed.No(Refusal.INVALID)
            !PathValidator.removable(goal, change.key) -> Transformed.No(Refusal.LAST_WATER, listOf(Violation.NO_WATER))
            else -> ok(relever(goal.copy(components = goal.components.filter { it.key != change.key })))
        }
        is GoalChange.ReplaceComponent -> {
            val old = goal.component(change.old)
            if (old == null || goal.has(change.new)) {
                Transformed.No(Refusal.INVALID)
            } else {
                val safe = ctx.given.constraintOf(change.new)?.safeVersion
                ok(goal.copy(components = goal.components.map { if (it.key == change.old) GoalComponent(change.new, old.role, safeVersion = safe, isLever = old.isLever) else it }))
            }
        }
        is GoalChange.SetRole -> if (!goal.has(change.key)) {
            Transformed.No(Refusal.INVALID)
        } else {
            ok(goal.copy(components = goal.components.map { if (it.key == change.key) it.copy(role = change.role) else it }))
        }
        is GoalChange.SetTargetMark -> {
            val c = goal.component(change.key)
            val mark = change.mark
            when {
                c == null -> Transformed.No(Refusal.INVALID)
                // R7 / P-06: a target above the reach of one stage is «целиком», not a mark of now
                mark != null && c.startMark != null && mark > MarkRules.nextTarget(c.startMark) ->
                    Transformed.No(Refusal.INVALID, listOf(Violation.MARK_OUT_OF_RANGE))
                else -> ok(goal.copy(components = goal.components.map { if (it.key == change.key) it.copy(targetMark = mark) else it }))
            }
        }
        is GoalChange.SetCriteria -> ok(goal.copy(criteria = change.criteria))
        is GoalChange.SetLever -> if (!goal.has(change.key)) {
            Transformed.No(Refusal.INVALID)
        } else {
            ok(goal.copy(components = goal.components.map { it.copy(isLever = it.key == change.key) }))
        }
        GoalChange.RestartFromToday -> ok(goal.copy(start = ctx.today, pausedDays = 0, pauseFrom = null))
        is GoalChange.Pause -> GoalLifecycle.next(goal.status, GoalEvent.PAUSE, Initiator.PERSON)?.let { s ->
            ok(goal.copy(status = s, pauseFrom = ctx.today, pauseReason = change.reason, pauseUntil = change.until.takeIf { change.reason.asksTerm }))
        } ?: Transformed.No(Refusal.INVALID)
        GoalChange.Resume -> GoalLifecycle.next(goal.status, GoalEvent.RESUME, Initiator.PERSON)?.let { s ->
            ok(closePause(goal, ctx.today).copy(status = s))
        } ?: Transformed.No(Refusal.INVALID)
        is GoalChange.Release -> GoalLifecycle.next(goal.status, GoalEvent.RELEASE, Initiator.PERSON)?.let { s ->
            ok(goal.copy(status = s, releaseToQueue = change.toQueue))
        } ?: Transformed.No(Refusal.INVALID)
        GoalChange.Close -> GoalLifecycle.next(goal.status, GoalEvent.CLOSE, Initiator.PERSON)?.let { ok(goal.copy(status = it)) }
            ?: Transformed.No(Refusal.INVALID)
        is GoalChange.ApplyGiven -> ok(safe(goal, change.given))
        GoalChange.Hold -> GoalLifecycle.next(goal.status, GoalEvent.HOLD, Initiator.HOT)?.let { s ->
            ok(goal.copy(status = s, pauseFrom = goal.pauseFrom ?: ctx.today))
        } ?: Transformed.No(Refusal.INVALID)
        GoalChange.Unhold -> GoalLifecycle.next(goal.status, GoalEvent.UNHOLD, Initiator.PERSON)?.let {
            if (goal.pauseReason != null) ok(goal.copy(status = GoalStatus.PAUSED)) else ok(closePause(goal, ctx.today).copy(status = GoalStatus.ACTIVE))
        } ?: Transformed.No(Refusal.INVALID)
        GoalChange.NextStage -> GoalLifecycle.next(goal.status, GoalEvent.NEXT_STAGE, Initiator.PERSON)?.let { s ->
            ok(goal.copy(status = s, stage = goal.stage + 1, start = ctx.today, pausedDays = 0, pauseFrom = null))
        } ?: Transformed.No(Refusal.INVALID)
        is GoalChange.Crossroads -> change.inner?.let { transform(goal, it, ctx) } ?: ok(goal)
    }

    private fun closePause(goal: PracticalGoal, today: LocalDate): PracticalGoal {
        val days = goal.pauseFrom?.let { ChronoUnit.DAYS.between(it, today).coerceAtLeast(0).toInt() } ?: 0
        return goal.copy(pausedDays = goal.pausedDays + days, pauseFrom = null, pauseReason = null, pauseUntil = null)
    }

    /** «Дано» at once: the safe version or out; the last Water is replaced, never dropped (P-03, P-05). */
    fun safe(goal: PracticalGoal, given: GivenLayer): PracticalGoal {
        var parts = goal.components.mapNotNull { c ->
            val g = given.constraintOf(c.key) ?: return@mapNotNull c
            if (g.safeVersion != null) c.copy(safeVersion = g.safeVersion) else null
        }
        if (parts.none { it.key.element == Element.WATER }) {
            val water = ComponentKey.of(Element.WATER).firstOrNull { k -> given.constraintOf(k) == null && parts.none { it.key == k } }
            if (water != null) parts = parts + GoalComponent(water, Role.GUARD)
        }
        if (parts.none { it.role == Role.LEAD } && parts.isNotEmpty()) {
            parts = listOf(parts.first().copy(role = Role.LEAD)) + parts.drop(1)
        }
        return relever(goal.copy(components = parts))
    }

    /** Keeps exactly one lever: when it left, the first lead takes it. */
    private fun relever(goal: PracticalGoal): PracticalGoal {
        if (goal.components.count { it.isLever } == 1) return goal
        val lead = goal.components.firstOrNull { it.role == Role.LEAD } ?: goal.components.firstOrNull() ?: return goal
        return goal.copy(components = goal.components.map { it.copy(isLever = it.key == lead.key) })
    }

    private fun GoalChange.deadlineMove(): Boolean =
        this is GoalChange.MoveDeadline || (this is GoalChange.Crossroads && inner is GoalChange.MoveDeadline)

    /** A move of the deadline, by the person or by «Сдвинуть срок» on the crossroads (P-10). */
    private fun GoalVersion.isDeadlineMove(): Boolean =
        changeType == ChangeType.DEADLINE || (changeType == ChangeType.CROSSROADS && reason == CrossroadsOption.SHIFT_DEADLINE.name)

    private fun day(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()

    private const val DAY_MS = 24L * 3600_000L
}

/** «Перекрёсток» (A56 §3): after a pause, a hard week or a lag. Nothing is pre-selected. */
object CrossroadsRules {
    /** After a pause or a hard week: three ways (A56 резюме). */
    val AFTER_PAUSE = listOf(CrossroadsOption.SHIFT_DEADLINE, CrossroadsOption.LESS_BY_DATE, CrossroadsOption.REBUILD_FROM_TODAY)

    /** A lag at milestone 4 or 8: «Темп ниже расчёта — это данные, не оценка». */
    val LAG = listOf(
        CrossroadsOption.SHIFT_DEADLINE, CrossroadsOption.LESS_BY_DATE, CrossroadsOption.EASE_LEAD,
        CrossroadsOption.SWITCH_LEVER, CrossroadsOption.AS_IS,
    )

    /** The change behind an option; null when the option changes nothing for this goal. */
    fun change(goal: PracticalGoal, option: CrossroadsOption): GoalChange? = when (option) {
        CrossroadsOption.SHIFT_DEADLINE -> GoalChange.MoveDeadline(goal.deadline.plusWeeks(SHIFT_WEEKS))
        CrossroadsOption.LESS_BY_DATE -> GoalChange.SetCriteria(goal.criteria.map(::lighter))
        CrossroadsOption.REBUILD_FROM_TODAY -> GoalChange.RestartFromToday
        CrossroadsOption.EASE_LEAD -> goal.components
            .firstOrNull { it.role == Role.LEAD && it.targetMark != null && it.targetMark > (it.startMark ?: MarkRules.MIN) }
            ?.let { GoalChange.SetTargetMark(it.key, it.targetMark!! - 1) }
        CrossroadsOption.SWITCH_LEVER -> goal.components
            .filter { !it.isLever && it.role != Role.LEAD }
            .firstOrNull()
            ?.let { GoalChange.SetLever(it.key, LeverReason.PERSON) }
        CrossroadsOption.AS_IS -> null
    }?.let { GoalChange.Crossroads(option, it) }

    /** «К сроку — меньше»: the range a quarter lower, still a range. */
    fun lighter(c: Criterion): Criterion {
        val low = kotlin.math.round(c.low * LIGHTER * 10) / 10
        val high = maxOf(kotlin.math.round(c.high * LIGHTER * 10) / 10, low + STEP)
        return c.copy(low = low, high = high)
    }

    private const val SHIFT_WEEKS = 4L
    private const val LIGHTER = 0.75
    private const val STEP = 0.5
}
