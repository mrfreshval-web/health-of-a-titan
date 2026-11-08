package com.healthofatitan.app.feature.today

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.healthofatitan.app.core.ui.component.HotCard
import com.healthofatitan.app.core.ui.component.HotNumbers
import com.healthofatitan.app.core.ui.component.HotTextButton
import com.healthofatitan.app.domain.engine.ItemStatus
import com.healthofatitan.app.integration.whoop.day.DayItemView
import kotlinx.coroutines.delay
import java.time.LocalDate

const val DAY_UNDO_TAG = "today_day_undo"

/** The waking day starts at 05:00: a bedtime at 02:50 is the end of today, not its start. */
private const val DAY_START_MIN = 5 * 60

/** Minutes on the waking day's axis: 02:50 → 26:50, so it sorts after the dinner at 23:05. */
internal fun dayMin(min: Int): Int = if (min < DAY_START_MIN) min + 1440 else min

/** The item of the moment: in its slot now, else the next open one, else the first open one. */
fun nowItem(items: List<DayItemView>, nowMin: Int): DayItemView? {
    val now = dayMin(nowMin)
    val open = items.filter { it.status == ItemStatus.OPEN }
    return open.firstOrNull { s ->
        val start = s.startMin?.let(::dayMin) ?: return@firstOrNull false
        val end = s.endMin?.let { e -> start + HotNumbers.span(s.startMin!!, e) } ?: return@firstOrNull false
        now in start..end
    }
        ?: open.filter { it.startMin != null && dayMin(it.startMin!!) >= now }.minByOrNull { dayMin(it.startMin!!) }
        ?: open.firstOrNull()
}

/** WHOOP's own marks are not the user's to undo (A35). */
internal fun canMark(item: DayItemView): Boolean = item.status == ItemStatus.OPEN || item.status == ItemStatus.DONE_SELF

/** A mark just made, kept for [DayMarks.UNDO_MS] so it can be taken back. */
data class PendingMark(val day: LocalDate, val itemId: String, val title: String, val marked: Boolean, val key: Long, val text: String? = null)

/**
 * Marks with an undo (R3, A46 §2.4): a swipe marks at once, then «Отменить» stays for 5 s. A plan
 * item's undo is one more toggle (DayPlanRepository.toggle: OPEN ⇄ DONE_SELF); any other action
 * (a task of the day, «Не сегодня») brings its own undo through [show].
 */
@Stable
class DayMarks {
    var toggle: (LocalDate, String) -> Unit = { _, _ -> }
    var pending by mutableStateOf<PendingMark?>(null)
        private set
    private var undoAction: (() -> Unit)? = null
    private var counter = 0L

    fun mark(day: LocalDate, item: DayItemView) {
        if (!canMark(item)) return
        toggle(day, item.id)
        show(day, item.id, item.title, marked = item.status == ItemStatus.OPEN) { toggle(day, item.id) }
    }

    /** An action just done: «[title] · Отменить» for 5 s; [undo] takes it back. */
    fun show(day: LocalDate, id: String, title: String, marked: Boolean, text: String? = null, undo: () -> Unit) {
        undoAction = undo
        pending = PendingMark(day, id, title, marked = marked, key = ++counter, text = text)
    }

    fun undo() {
        if (pending == null) return
        undoAction?.invoke()
        undoAction = null
        pending = null
    }

    fun expire(key: Long) {
        if (pending?.key == key) {
            pending = null
            undoAction = null
        }
    }

    companion object {
        const val UNDO_MS = 5_000L
    }
}

@Composable
fun rememberDayMarks(onToggle: (LocalDate, String) -> Unit): DayMarks {
    val marks = remember { DayMarks() }
    marks.toggle = onToggle
    return marks
}

/**
 * «Отмечено: Ужин · Отменить» for 5 s after a mark (R3). A floating glass line at the bottom of
 * «Сегодня»; it goes by itself.
 */
@Composable
fun UndoBar(marks: DayMarks, modifier: Modifier = Modifier) {
    val pending = marks.pending
    LaunchedEffect(pending?.key) {
        val key = pending?.key ?: return@LaunchedEffect
        delay(DayMarks.UNDO_MS)
        marks.expire(key)
    }
    AnimatedVisibility(pending != null, modifier = modifier) {
        val p = pending ?: return@AnimatedVisibility
        HotCard(
            modifier = Modifier.fillMaxWidth().testTag(DAY_UNDO_TAG),
            contentPadding = PaddingValues(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics { role = Role.Button }) {
                Text(
                    p.text ?: DayUiTexts.undoLine(p.title, p.marked),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                HotTextButton(DayUiTexts.UNDO, onClick = marks::undo)
            }
        }
    }
}
