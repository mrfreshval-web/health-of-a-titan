package com.healthofatitan.app.domain.engine

/** «Тренировка» when the day closes (A18 И4.1 ①): one tap, no RPE form, no reproach. */
enum class TrainingClose { DONE, CHANGED, NOT_TODAY }

/** «Еда» when the day closes (A18 И4.1 ①, A37 s5): by the plate, partly, just ate — no calories. */
enum class FoodClose { PLATE, PARTLY, JUST_ATE }

/**
 * «Закрыть день» (A18 И4.1, A37 s5, D-027): every answer is one tap and may stay empty (null is
 * «не отмечено», never a failure). The last tap — «План помог?» — closes the day.
 */
data class DayClose(
    val training: TrainingClose? = null,
    val food: FoodClose? = null,
    val body: Body? = null,
    val alcohol: Int? = null,
    val planHelped: Boolean? = null,
)

/**
 * The evening's arithmetic, without judgement (A37 §3 «стремление без стыда», A11 §4.2): what the
 * user marks only lights things up; nothing is ever subtracted, crossed out or coloured red.
 */
object Evening {

    /**
     * The training item's status after the evening: «сделал» → «✓ отмечено тобой», «изменил» →
     * «⇄ изменено»; «не сегодня» leaves it open. What WHOOP confirmed is never undone (A35 §4).
     */
    fun trainingStatus(current: ItemStatus, answer: TrainingClose?): ItemStatus = when {
        current == ItemStatus.DONE_WHOOP || current == ItemStatus.MODIFIED -> current
        answer == TrainingClose.DONE -> ItemStatus.DONE_SELF
        answer == TrainingClose.CHANGED -> ItemStatus.MODIFIED
        answer == TrainingClose.NOT_TODAY -> ItemStatus.OPEN
        else -> current
    }

    /** The training row needs a tap only while WHOOP has not seen it (A37 §1: then 4 taps, else 3). */
    fun trainingAsked(current: ItemStatus?): Boolean = current == ItemStatus.OPEN || current == ItemStatus.DONE_SELF

    /** Floor of the light: a closed day is never dark, even with nothing marked. */
    const val LIGHT_FLOOR = 0.35f

    /**
     * «Свет без стыда»: how bright the Titan of the closed day shines — [LIGHT_FLOOR] for showing up,
     * the rest by the share of plan items that were done in any of the three ways (A35 §4).
     */
    fun light(statuses: List<ItemStatus>): Float {
        if (statuses.isEmpty()) return LIGHT_FLOOR
        val done = statuses.count { it != ItemStatus.OPEN }
        return LIGHT_FLOOR + (1f - LIGHT_FLOOR) * done / statuses.size
    }

    /** How many of the rows are answered — the «2 / 3» of the mockup s5. */
    fun answered(close: DayClose, trainingAsked: Boolean): Int =
        listOfNotNull(
            close.training.takeIf { trainingAsked },
            close.food,
            close.body,
            close.alcohol,
        ).size
}

/**
 * Phone steps against the plan (A35 §4, D-024). Steps are read in memory from Health Connect
 * (R-11: never stored); only the result of the check is shown.
 */
object Steps {

    /** [Оценка] until 3 days of own steps exist; Agents 3 and 12 confirm (A35 §4 «цель — личная медиана»). */
    const val DEFAULT_GOAL = 8_000

    /** A day with fewer phone steps is a day the phone stayed on the table, not a day of the person. */
    const val MIN_DAY = 1_000

    const val MIN_DAYS = 3

    /** A35 §4: walking is about ~80 steps a minute. */
    const val WALK_STEPS_PER_MIN = 80

    /** The share of the planned walk that confirms it (like WHOOP's 70 % for walks, A35 §4). */
    const val WALK_SHARE = 0.7

    /** The personal goal: the median of earlier days with real phone steps, else [DEFAULT_GOAL]. */
    fun goal(history: List<Int>): Int {
        val days = history.filter { it >= MIN_DAY }.sorted()
        if (days.size < MIN_DAYS) return DEFAULT_GOAL
        val mid = days.size / 2
        val median = if (days.size % 2 == 1) days[mid] else (days[mid - 1] + days[mid]) / 2
        return (median + 50) / 100 * 100
    }

    /**
     * A35 §4 «Ходьба»: the planned walk is confirmed by the phone when the steps inside its slot
     * reach ~80 a minute for 70 % of the planned time ([isWalk]). Other trainings are WHOOP's to confirm.
     */
    fun walkConfirmed(plannedMinutes: Int, stepsInSlot: Int?): Boolean {
        if (stepsInSlot == null || plannedMinutes <= 0) return false
        return stepsInSlot >= WALK_STEPS_PER_MIN * plannedMinutes * WALK_SHARE
    }

    /** Plan items that are a walk: ACT-01 (the GENTLE walk) and RET-01 (the minimal version). */
    fun isWalk(rule: RuleRef?): Boolean = rule?.id == RuleSet.ACT_01.id || rule?.id == RuleSet.RET_01.id

    /** The header ring (D-029): the share of the goal, capped at a full ring. */
    fun fraction(steps: Int, goal: Int): Float = if (goal <= 0) 0f else (steps.toFloat() / goal).coerceIn(0f, 1f)
}
