package com.healthofatitan.app.domain.training

enum class Side(val ru: String) { FIRST("правая сторона"), SECOND("левая сторона") }

/** One RPE question after the last set of an exercise or a round (A40 §7.3, `rules.player.rpe_ask`). */
data class RpeAsk(val key: String, val label: String, val exerciseIds: List<String>)

/** A step of the player (A40 §7.1: `[Подход → Ввод → Отдых]×N`). */
sealed interface WorkoutStep {
    val blockIndex: Int
    val blockType: BlockType

    /**
     * A set (or a side of a set). Timed when [seconds] is set, open (until «Готово») otherwise.
     *
     * @param round 1-based round of a circuit, 1 elsewhere
     */
    data class Work(
        override val blockIndex: Int,
        override val blockType: BlockType,
        val item: SessionItem,
        val set: Int,
        val sets: Int,
        val round: Int,
        val rounds: Int,
        val side: Side?,
        val reps: String?,
        val seconds: Int?,
        val distanceM: Int?,
        /** Asked when this set is done (the last set of the exercise or of the round). */
        val askRpe: RpeAsk? = null,
    ) : WorkoutStep {
        val exercise: Exercise get() = item.exercise
        val timed: Boolean get() = seconds != null
    }

    data class Rest(
        override val blockIndex: Int,
        override val blockType: BlockType,
        val seconds: Int,
        val kind: RestKind,
    ) : WorkoutStep
}

enum class RestKind { REST, ROUND, SIDE_SWITCH }

/**
 * Flattens a session into the player's steps. Formats (SCHEMA.md §3): `flow` and `steady` go
 * through the items once; `straight_sets` set by set; `superset` alternates the items of a pair
 * and rests after the pair; `circuit` goes round by round with a rest between rounds. Per-side
 * items run each side with a short switch (`rules.player.per_side_switch_s`).
 */
object WorkoutSteps {

    fun of(session: TrainingSession, player: PlayerDefaults): List<WorkoutStep> {
        val steps = mutableListOf<WorkoutStep>()
        session.blocks.forEachIndexed { bi, block ->
            val start = steps.size
            when (block.format) {
                "circuit", "amrap", "emom", "for_time" -> circuit(bi, block, player, steps)
                "superset" -> superset(bi, block, player, steps)
                else -> straight(bi, block, player, steps)
            }
            // No rest dangling at the end of a block: the next block starts with its own countdown.
            while (steps.size > start && steps.last() is WorkoutStep.Rest) steps.removeAt(steps.size - 1)
        }
        return steps
    }

    /** RPE is asked for the work of the main part only: never in the warm-up or the cool-down. */
    private fun SessionBlock.asksRpe(item: SessionItem) =
        (type == BlockType.MAIN || type == BlockType.FINISHER) && item.exercise.category in Category.EFFORT

    private fun sides(item: SessionItem): List<Side?> = if (item.perSide) listOf(Side.FIRST, Side.SECOND) else listOf(null)

    private fun addSet(
        bi: Int,
        block: SessionBlock,
        item: SessionItem,
        set: Int,
        sets: Int,
        round: Int,
        rounds: Int,
        player: PlayerDefaults,
        steps: MutableList<WorkoutStep>,
        ask: RpeAsk?,
    ) {
        val sides = sides(item)
        sides.forEachIndexed { i, side ->
            if (i > 0) steps += WorkoutStep.Rest(bi, block.type, player.perSideSwitchS, RestKind.SIDE_SWITCH)
            steps += WorkoutStep.Work(
                blockIndex = bi,
                blockType = block.type,
                item = item,
                set = set,
                sets = sets,
                round = round,
                rounds = rounds,
                side = side,
                reps = item.reps,
                seconds = item.timeS,
                distanceM = item.distanceM,
                askRpe = ask.takeIf { i == sides.lastIndex },
            )
        }
    }

    private fun straight(bi: Int, block: SessionBlock, player: PlayerDefaults, steps: MutableList<WorkoutStep>) {
        block.items.forEachIndexed { ii, item ->
            for (set in 1..item.sets) {
                val ask = if (set == item.sets && block.asksRpe(item)) RpeAsk("b$bi-i$ii", item.exercise.nameRu, listOf(item.exercise.id)) else null
                addSet(bi, block, item, set, item.sets, 1, 1, player, steps, ask)
                if (item.restS > 0) steps += WorkoutStep.Rest(bi, block.type, item.restS, RestKind.REST)
            }
        }
    }

    private fun superset(bi: Int, block: SessionBlock, player: PlayerDefaults, steps: MutableList<WorkoutStep>) {
        val groups = block.items.groupBy { it.pair ?: it.exercise.id }.values
        groups.forEachIndexed { gi, group ->
            val sets = group.maxOf { it.sets }
            val label = group.joinToString(" + ") { it.exercise.nameRu }
            for (set in 1..sets) {
                group.forEachIndexed { ii, item ->
                    if (set > item.sets) return@forEachIndexed
                    val lastOfGroup = ii == group.lastIndex
                    val ask = if (set == sets && lastOfGroup && group.any { block.asksRpe(it) }) RpeAsk("b$bi-g$gi", label, group.map { it.exercise.id }) else null
                    addSet(bi, block, item, set, item.sets, 1, 1, player, steps, ask)
                    if (item.restS > 0) steps += WorkoutStep.Rest(bi, block.type, item.restS, RestKind.REST)
                }
            }
        }
    }

    private fun circuit(bi: Int, block: SessionBlock, player: PlayerDefaults, steps: MutableList<WorkoutStep>) {
        val rounds = block.rounds.coerceAtLeast(1)
        val label = block.items.joinToString(" · ") { it.exercise.nameRu }
        for (round in 1..rounds) {
            block.items.forEachIndexed { ii, item ->
                val last = round == rounds && ii == block.items.lastIndex
                val ask = if (last && block.items.any { block.asksRpe(it) }) RpeAsk("b$bi-circuit", label, block.items.map { it.exercise.id }) else null
                addSet(bi, block, item, round, rounds, round, rounds, player, steps, ask)
                if (item.restS > 0) steps += WorkoutStep.Rest(bi, block.type, item.restS, RestKind.REST)
            }
            if (round < rounds && block.restBetweenRoundsS > 0) steps += WorkoutStep.Rest(bi, block.type, block.restBetweenRoundsS, RestKind.ROUND)
        }
    }

    /** Planned seconds of the steps (timed sets and rests; open sets count [openSetS]). */
    fun plannedSeconds(steps: List<WorkoutStep>, openSetS: Int = 40): Int = steps.sumOf {
        when (it) {
            is WorkoutStep.Rest -> it.seconds
            is WorkoutStep.Work -> it.seconds ?: openSetS
        }
    }
}
