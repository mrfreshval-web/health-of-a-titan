package com.healthofatitan.app.feature.today

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.healthofatitan.app.core.model.nav.HotRoute
import com.healthofatitan.app.core.ui.component.HotCard
import com.healthofatitan.app.core.ui.component.HotMode
import com.healthofatitan.app.core.ui.component.HotNumber
import com.healthofatitan.app.core.ui.component.HotNumbers
import com.healthofatitan.app.core.ui.component.HotOverline
import com.healthofatitan.app.core.ui.component.HotTextButton
import com.healthofatitan.app.core.ui.component.HotTimeCell
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.core.ui.theme.HotType
import com.healthofatitan.app.core.ui.theme.Titanium
import com.healthofatitan.app.domain.engine.DayEngine
import com.healthofatitan.app.domain.engine.ItemKind
import com.healthofatitan.app.domain.engine.ItemStatus
import com.healthofatitan.app.integration.whoop.day.DayItemView
import com.healthofatitan.app.integration.whoop.day.DayView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

const val DAY_UNDO_TAG = "today_day_undo"
const val DAY_PLAN_TOGGLE_TAG = "today_day_plan_toggle"
const val DAY_ROW_TAG = "today_day_row_"

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

/**
 * Where a tap on an item leads (UX review 119, R3): the training opens «Тренировка дня» and its
 * player (stream B), a meal opens its card of «Тарелка дня» (stream C). Other items unfold in
 * place. A tap never marks an item done.
 */
internal fun itemRoute(item: DayItemView): String? = when {
    item.id == DayEngine.Ids.TRAINING -> HotRoute.TRAINING
    item.kind == ItemKind.NUTRITION && item.id in MEAL_SLOTS -> HotRoute.nutritionMeal(item.id)
    else -> null
}

/** Meal slots the nutrition card knows (A41 §10): the plan's item ids are the same words. */
private val MEAL_SLOTS = setOf(DayEngine.Ids.BREAKFAST, "lunch", DayEngine.Ids.DINNER)

/** WHOOP's own marks are not the user's to undo (A35). */
internal fun canMark(item: DayItemView): Boolean = item.status == ItemStatus.OPEN || item.status == ItemStatus.DONE_SELF

/** A mark just made, kept for [UNDO_MS] so it can be taken back. */
data class PendingMark(val day: LocalDate, val itemId: String, val title: String, val marked: Boolean, val key: Long)

/**
 * Marks with an undo (R3): a long press or a swipe marks at once, then «Отменить» stays for 5 s.
 * The undo is one more toggle (DayPlanRepository.toggle: OPEN ⇄ DONE_SELF).
 */
@Stable
class DayMarks {
    var toggle: (LocalDate, String) -> Unit = { _, _ -> }
    var pending by mutableStateOf<PendingMark?>(null)
        private set
    private var counter = 0L

    fun mark(day: LocalDate, item: DayItemView) {
        if (!canMark(item)) return
        toggle(day, item.id)
        pending = PendingMark(day, item.id, item.title, marked = item.status == ItemStatus.OPEN, key = ++counter)
    }

    fun undo() {
        val p = pending ?: return
        toggle(p.day, p.itemId)
        pending = null
    }

    fun expire(key: Long) {
        if (pending?.key == key) pending = null
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
 * Tap opens, long press or a swipe to the right marks (R3). The swipe follows the finger and
 * springs back; past 96 dp it marks with a tick of haptics.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun rememberMarkGestures(enabled: Boolean, marked: Boolean, onOpen: () -> Unit, onMark: () -> Unit): Modifier {
    val haptics = LocalHapticFeedback.current
    val shift = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val limit = with(LocalDensity.current) { 96.dp.toPx() }
    val drag = rememberDraggableState { d -> scope.launch { shift.snapTo((shift.value + d).coerceIn(0f, limit * 1.3f)) } }
    return Modifier
        .offset { IntOffset(shift.value.roundToInt(), 0) }
        .draggable(
            state = drag,
            orientation = Orientation.Horizontal,
            enabled = enabled,
            onDragStopped = {
                if (shift.value >= limit) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onMark()
                }
                shift.animateTo(0f)
            },
        )
        .combinedClickable(
            onClick = onOpen,
            onClickLabel = TodayTexts.OPEN,
            onLongClick = if (enabled) {
                {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onMark()
                }
            } else {
                null
            },
            onLongClickLabel = if (marked) TodayTexts.UNMARK else TitanTexts.MARK_DONE,
        )
}

/**
 * The item in focus (s2 «Сейчас», R3, Y5): the item of the moment, or the node picked on «Линия
 * дня». A tap opens it (training, meal) or unfolds it; a long press or a swipe marks it, with
 * an undo. No round check button: a mark is never one stray tap.
 */
@Composable
fun FocusCard(plan: DayView, item: DayItemView, isNow: Boolean, mode: HotMode, marks: DayMarks, onOpen: (String) -> Unit) {
    var open by rememberSaveable(plan.dayKey, item.id) { mutableStateOf(false) }
    val route = itemRoute(item)
    val done = item.status != ItemStatus.OPEN
    val gestures = rememberMarkGestures(
        enabled = canMark(item),
        marked = done,
        onOpen = { if (route != null) onOpen(route) else open = !open },
        onMark = { marks.mark(plan.dayKey, item) },
    )
    HotCard(cornice = true, modifier = Modifier.fillMaxWidth().testTag(DAY_NOW_TAG), contentPadding = PaddingValues(0.dp)) {
        Column(
            gestures
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    stateDescription = when {
                        done -> TitanTexts.MARKED
                        isNow -> TitanTexts.NOW
                        else -> ""
                    }
                }
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KindBadge(item.kind, mode.color(), sizeDp = 64, filled = done)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HotOverline(if (isNow) TitanTexts.NOW else TitanTexts.NEXT, color = MaterialTheme.colorScheme.primary)
                        item.startMin?.let {
                            Spacer(Modifier.width(8.dp))
                            HotNumber(HotNumbers.clock(it), style = HotType.time, color = MaterialTheme.colorScheme.primary)
                        }
                        if (item.startMin != null && item.endMin != null) {
                            Spacer(Modifier.width(6.dp))
                            HotNumber(
                                "· " + HotNumbers.duration(HotNumbers.span(item.startMin!!, item.endMin!!)),
                                style = MaterialTheme.typography.bodySmall,
                                color = HotThemeTokens.colors.textTertiary,
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(item.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    item.detail?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = HotThemeTokens.colors.textTertiary,
                            maxLines = if (open) Int.MAX_VALUE else 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                ItemEnd(item, route != null, size = 28)
            }
            AnimatedVisibility(open) {
                Text(
                    DayUiTexts.MARK_HINT,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

/** The end of an item row: the status mark when done, an arrow when it opens a screen, else a quiet ring. */
@Composable
private fun ItemEnd(item: DayItemView, opens: Boolean, size: Int = 24) {
    val tokens = HotThemeTokens.colors
    when {
        item.status != ItemStatus.OPEN -> StatusGlyph(item.status, tokens.modeRovno, Titanium.Depth, size = size.dp)
        opens -> ChevronGlyph(MaterialTheme.colorScheme.onSurfaceVariant, size)
        else -> Spacer(
            Modifier
                .size(size.dp)
                .drawBehind { drawCircle(tokens.rule, this.size.minDimension / 2f - 0.5.dp.toPx(), style = Stroke(1.dp.toPx())) },
        )
    }
}

@Composable
private fun ChevronGlyph(color: Color, size: Int) {
    Spacer(
        Modifier
            .size(size.dp)
            .clearAndSetSemantics { }
            .drawBehind {
                val w = this.size.width
                val s = 1.6.dp.toPx()
                drawLine(color, Offset(w * 0.40f, w * 0.26f), Offset(w * 0.64f, w * 0.5f), s, StrokeCap.Round)
                drawLine(color, Offset(w * 0.64f, w * 0.5f), Offset(w * 0.40f, w * 0.74f), s, StrokeCap.Round)
            },
    )
}

/**
 * «Линия дня» (A37 §10 фишка 10, s2): the plan as nodes on one line — what was (filled in the mode
 * light), now (the dawn node with its glow), what comes (hairline). The bedtime is the last node
 * (R2), once, after midnight when it is after midnight. A tap on a node puts it in focus (Y5);
 * the focused node carries its name under it (G1).
 */
@Composable
fun DayLine(plan: DayView, nowMin: Int, mode: HotMode, selectedId: String?, onSelect: (DayItemView) -> Unit, modifier: Modifier = Modifier) {
    val items = plan.items.filter { it.startMin != null }.sortedBy { dayMin(it.startMin!!) }
    val current = nowItem(plan.items, nowMin)
    val focus = selectedId ?: current?.id
    val tokens = HotThemeTokens.colors
    val modeColor = mode.color()
    val dawn = MaterialTheme.colorScheme.primary
    val hasBedtime = items.any { it.kind == ItemKind.SLEEP && it.startMin == plan.bedtimeMin }
    val nodes: List<Pair<DayItemView?, Int>> = items.map { it to it.startMin!! } + if (hasBedtime) emptyList() else listOf(null to plan.bedtimeMin)
    val now = dayMin(nowMin)
    val doneUntil = nodes.indexOfLast { (item, start) -> (item != null && item.status != ItemStatus.OPEN) || dayMin(start) <= now }
    Column(modifier.fillMaxWidth().testTag(DAY_LINE_TAG)) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .drawBehind {
                    // the line under the nodes: the part that has passed glows dawn
                    val y = 22.dp.toPx() + 26.dp.toPx()
                    val step = 78.dp.toPx()
                    val first = 39.dp.toPx()
                    val last = first + step * (nodes.size - 1)
                    drawLine(tokens.rule, Offset(first, y), Offset(last, y), 1.dp.toPx())
                    if (doneUntil >= 0) {
                        val x = first + step * doneUntil.coerceAtMost(nodes.size - 1)
                        drawLine(dawn.copy(alpha = 0.8f), Offset(first, y), Offset(x, y), 1.5.dp.toPx())
                    }
                },
        ) {
            nodes.forEachIndexed { i, (item, start) ->
                val isNow = item != null && item.id == current?.id
                val isFocus = item != null && item.id == focus
                val done = item != null && item.status != ItemStatus.OPEN
                val bedtime = item == null || item.kind == ItemKind.SLEEP
                val color = when {
                    isNow -> dawn
                    done -> modeColor
                    bedtime -> tokens.modeBerezhno
                    else -> tokens.textTertiary
                }
                Column(
                    Modifier
                        .width(78.dp)
                        .then(if (item != null) Modifier.clickable(role = Role.Button, onClick = { onSelect(item) }) else Modifier)
                        .semantics(mergeDescendants = true) {
                            contentDescription = (item?.title ?: TitanTexts.BEDTIME) + ", " + HotNumbers.clock(start)
                            stateDescription = when {
                                isNow -> TitanTexts.NOW
                                done -> TitanTexts.MARKED
                                else -> ""
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    HotNumber(
                        HotNumbers.clock(start),
                        style = HotType.time,
                        color = if (isNow || isFocus) dawn else tokens.textTertiary,
                        modifier = Modifier.height(22.dp),
                    )
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(52.dp)) {
                        if (isNow && tokens.glow) {
                            Spacer(Modifier.size(52.dp).background(Brush.radialGradient(listOf(dawn.copy(alpha = 0.35f), Color.Transparent)), CircleShape))
                        }
                        KindBadge(
                            item?.kind ?: ItemKind.SLEEP,
                            color,
                            sizeDp = 44,
                            filled = isNow || done,
                            dashed = !isNow && !done && item != null && !bedtime && i > doneUntil,
                        )
                    }
                    val label = when {
                        isFocus -> item?.title
                        item == null || bedtime -> TitanTexts.BEDTIME
                        else -> null
                    }
                    Text(
                        label.orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isFocus) MaterialTheme.colorScheme.onSurface else tokens.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 4.dp).height(18.dp).clearAndSetSemantics { },
                    )
                }
            }
        }
    }
}

/**
 * The full plan (slice 2), folded by default (Y5): the focus card and «Линия дня» already show
 * the day; the list opens on a tap. Rows: a fixed time column (R1), the title, one line of
 * detail; tap opens or unfolds, long press or swipe marks (R3).
 */
@Composable
fun PlanCard(plan: DayView, marks: DayMarks, onOpen: (String) -> Unit, initiallyOpen: Boolean = false) {
    var open by rememberSaveable(plan.dayKey) { mutableStateOf(initiallyOpen) }
    val items = plan.items.sortedWith(compareBy(nullsLast()) { it.startMin?.let(::dayMin) })
    val done = items.count { it.status != ItemStatus.OPEN }
    HotCard(modifier = Modifier.fillMaxWidth().testTag(DAY_PLAN_TAG)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .clickable(role = Role.Button, onClick = { open = !open })
                .semantics(mergeDescendants = true) { stateDescription = if (open) "развёрнут" else "свёрнут" }
                .testTag(DAY_PLAN_TOGGLE_TAG),
        ) {
            HotOverline(DayUiTexts.PLAN, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(10.dp))
            HotNumber(DayUiTexts.planCount(done, items.size), style = MaterialTheme.typography.bodySmall, color = HotThemeTokens.colors.textTertiary)
            Spacer(Modifier.weight(1f))
            Markers(plan)
            Spacer(Modifier.width(10.dp))
            Text(if (open) "⌃" else "⌄", style = MaterialTheme.typography.titleMedium, color = HotThemeTokens.colors.textTertiary)
        }
        AnimatedVisibility(open) {
            Column {
                Spacer(Modifier.height(4.dp))
                items.forEachIndexed { i, item ->
                    if (i > 0) HorizontalDivider(color = HotThemeTokens.colors.hairline)
                    PlanRow(plan.dayKey, item, marks, onOpen)
                }
            }
        }
    }
}

/** «кофе до · экраны в сторону с» as two pictograms with times; the words stay for TalkBack (A39 §7.4). */
@Composable
private fun Markers(plan: DayView) {
    val ink = HotThemeTokens.colors.textTertiary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = DayUiTexts.markers(plan.caffeineCutoffMin, plan.windDownMin, plan.bedtimeMin)
        },
    ) {
        CupGlyph("", ink)
        Spacer(Modifier.width(4.dp))
        HotNumber(HotNumbers.clock(plan.caffeineCutoffMin), style = MaterialTheme.typography.labelSmall, color = ink)
        Spacer(Modifier.width(10.dp))
        ScreenGlyph("", ink)
        Spacer(Modifier.width(4.dp))
        HotNumber(HotNumbers.clock(plan.windDownMin), style = MaterialTheme.typography.labelSmall, color = ink)
    }
}

@Composable
private fun PlanRow(day: LocalDate, item: DayItemView, marks: DayMarks, onOpen: (String) -> Unit) {
    var open by rememberSaveable(day, item.id) { mutableStateOf(false) }
    val done = item.status != ItemStatus.OPEN
    val route = itemRoute(item)
    val tokens = HotThemeTokens.colors
    val gestures = rememberMarkGestures(
        enabled = canMark(item),
        marked = done,
        onOpen = { if (route != null) onOpen(route) else open = !open },
        onMark = { marks.mark(day, item) },
    )
    Column(
        gestures
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics(mergeDescendants = true) { stateDescription = if (done) TitanTexts.MARKED else "" }
            .padding(vertical = 12.dp)
            .testTag(DAY_ROW_TAG + item.id),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HotTimeCell(item.startMin, item.endMin, color = if (done) tokens.textTertiary else MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                item.detail?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = tokens.textTertiary, maxLines = if (open) Int.MAX_VALUE else 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(10.dp))
            ItemEnd(item, route != null)
        }
        AnimatedVisibility(open) {
            Text(
                DayUiTexts.MARK_HINT,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = HotNumbers.TimeColumn + 8.dp, top = 8.dp),
            )
        }
    }
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
                    DayUiTexts.undoLine(p.title, p.marked),
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
