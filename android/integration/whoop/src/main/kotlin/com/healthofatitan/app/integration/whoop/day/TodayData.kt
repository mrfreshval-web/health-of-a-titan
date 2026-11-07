package com.healthofatitan.app.integration.whoop.day

import com.healthofatitan.app.core.database.HotDatabase
import com.healthofatitan.app.core.database.dao.TodayDao
import com.healthofatitan.app.core.database.entity.TodayMarkEntity
import com.healthofatitan.app.domain.today.DayTask
import com.healthofatitan.app.domain.today.Safety
import com.healthofatitan.app.domain.today.TaskMove
import com.healthofatitan.app.domain.today.WeekPlan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate

/** What the person marked on a day (A46 §2.3, §2.4): water, meals, tasks done or sent back to the week. */
data class TodayMarks(
    val water: Int = 0,
    val meals: Set<String> = emptySet(),
    /** Time of the latest meal mark, for «сон сегодня» (ужин до 21:00). */
    val lastMealAt: Long? = null,
    val done: Set<String> = emptySet(),
    val toWeek: Set<String> = emptySet(),
) {
    companion object {
        val EMPTY = TodayMarks()
    }
}

/** Water, meals and tasks of a day, stored in `today_mark` (Room 10). Only on the device. */
class TodayMarksRepository(private val dao: TodayDao, private val clock: Clock) {
    private val user = HotDatabase.LOCAL_USER

    fun observe(day: LocalDate): Flow<TodayMarks> = dao.observeDay(user, day.toString()).map { rows ->
        TodayMarks(
            water = rows.firstOrNull { it.kind == TodayMarkEntity.KIND_WATER }?.count ?: 0,
            meals = rows.filter { it.kind == TodayMarkEntity.KIND_MEAL }.map { it.ref }.toSet(),
            lastMealAt = rows.filter { it.kind == TodayMarkEntity.KIND_MEAL }.maxOfOrNull { it.at },
            done = rows.filter { it.kind == TodayMarkEntity.KIND_TASK_DONE }.map { it.ref }.toSet(),
            toWeek = rows.filter { it.kind == TodayMarkEntity.KIND_TASK_TO_WEEK }.map { it.ref }.toSet(),
        )
    }

    /** One glass more or less (never below zero). */
    suspend fun water(day: LocalDate, delta: Int) {
        val key = day.toString()
        val now = dao.day(user, key).firstOrNull { it.kind == TodayMarkEntity.KIND_WATER }?.count ?: 0
        val next = (now + delta).coerceIn(0, MAX_GLASSES)
        dao.upsert(TodayMarkEntity(user, key, TodayMarkEntity.KIND_WATER, "", next, at = clock.millis()))
    }

    suspend fun meal(day: LocalDate, slot: String, eaten: Boolean) {
        if (eaten) {
            dao.upsert(TodayMarkEntity(user, day.toString(), TodayMarkEntity.KIND_MEAL, slot, 1, at = clock.millis()))
        } else {
            dao.delete(user, day.toString(), TodayMarkEntity.KIND_MEAL, slot)
        }
    }

    suspend fun taskDone(day: LocalDate, taskId: String, done: Boolean) {
        if (done) {
            dao.upsert(TodayMarkEntity(user, day.toString(), TodayMarkEntity.KIND_TASK_DONE, taskId, 1, at = clock.millis()))
        } else {
            dao.delete(user, day.toString(), TodayMarkEntity.KIND_TASK_DONE, taskId)
        }
    }

    /** T-08: «Не сегодня» — the task goes back to its week goal for this day only, not into tomorrow. */
    suspend fun toWeek(move: TaskMove) {
        dao.upsert(TodayMarkEntity(user, move.day.toString(), TodayMarkEntity.KIND_TASK_TO_WEEK, move.taskId, 1, goal = move.weekGoalId, at = clock.millis()))
    }

    suspend fun undoToWeek(day: LocalDate, taskId: String) {
        dao.delete(user, day.toString(), TodayMarkEntity.KIND_TASK_TO_WEEK, taskId)
    }

    private companion object {
        const val MAX_GLASSES = 30
    }
}

/**
 * Where the tasks of the day come from (A51 §3.1, A52 §2.2): the week and its goals
 * ([com.healthofatitan.app.integration.whoop.week.WeekRepository]).
 */
interface WeekPlanSource {
    /** The calendar week of [day] with its goals and their progress. */
    fun week(day: LocalDate): Flow<WeekPlan>

    /** Candidates for the tasks of [day], each tied to a week goal ([DayTask.weekGoalId]). */
    fun candidates(day: LocalDate): Flow<List<DayTask>>
}

/** What overrides «Сегодня» today (A46 §4.8). :app binds it from the crisis log and the state cards. */
fun interface TodaySafetySource {
    fun safety(day: LocalDate): Flow<Safety>

    companion object {
        val None = TodaySafetySource { flowOf(Safety.NONE) }
    }
}

/** «ты» of «Рассвет» on the 1–5 scale and the haiku already shown today (read only, never logs a dawn). */
class TodayMorning(
    private val morningSelf: MorningSelfSource,
    private val dawn: DawnRepository,
) {
    /** Energy of the wake deck (2/4/6/8/10) → 1–5. */
    suspend fun self(day: LocalDate): Int? = runCatching { morningSelf.of(day)?.energy }.getOrNull()?.let { (it / 2).coerceIn(1, 5) }

    fun haiku(day: LocalDate): Flow<List<String>?> = dawn.shownHaiku(day)

    fun dawnShown(day: LocalDate): Flow<Boolean> = dawn.shown(day)
}
