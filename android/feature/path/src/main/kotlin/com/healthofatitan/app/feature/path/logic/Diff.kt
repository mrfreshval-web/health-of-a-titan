package com.healthofatitan.app.feature.path.logic

import com.healthofatitan.app.domain.path.PathCopy
import com.healthofatitan.app.domain.path.PathLexicon
import com.healthofatitan.app.domain.path.PracticalGoal

/** One row of the card «было → станет» (A58-VP §5.3): no colours of a delta, no strike-through. */
data class DiffRow(val what: String, val was: String, val will: String)

object Diff {
    /** The rows that differ between [before] and [after], in a fixed order. */
    fun rows(before: PracticalGoal, after: PracticalGoal): List<DiffRow> = buildList {
        fun row(what: String, a: String?, b: String?) {
            if (a != b) add(DiffRow(what, a ?: PathCopy.DIFF_NONE, b ?: PathCopy.DIFF_NONE))
        }
        row(PathCopy.DIFF_TITLE, before.title, after.title)
        row(PathCopy.DIFF_WHY, before.why, after.why)
        row(PathCopy.DIFF_BE, before.beName, after.beName)
        row(PathCopy.DIFF_DEADLINE, PathCopy.date(before.deadline), PathCopy.date(after.deadline))
        row(PathCopy.DIFF_START, PathCopy.date(before.start), PathCopy.date(after.start))
        row(PathCopy.DIFF_MODE, PathLexicon.mode(before.mode), PathLexicon.mode(after.mode))
        row(PathCopy.DIFF_STATUS, PathCopy.statusWord(before.status), PathCopy.statusWord(after.status))
        row(
            PathCopy.DIFF_CRITERIA,
            before.criteria.joinToString("\n", transform = PathCopy::criterion).ifEmpty { null },
            after.criteria.joinToString("\n", transform = PathCopy::criterion).ifEmpty { null },
        )
        row(PathCopy.DIFF_LEVER, before.lever?.let { PathLexicon.name(it.key) }, after.lever?.let { PathLexicon.name(it.key) })
        row(
            PathCopy.DIFF_PARTS,
            before.components.joinToString("\n", transform = PathCopy::part).ifEmpty { null },
            after.components.joinToString("\n", transform = PathCopy::part).ifEmpty { null },
        )
    }
}
