package com.healthofatitan.app.feature.today

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthofatitan.app.core.model.nav.HotRoute
import com.healthofatitan.app.core.ui.component.HotCard
import com.healthofatitan.app.core.ui.component.HotOverline
import com.healthofatitan.app.core.ui.component.HotPrimaryButton
import com.healthofatitan.app.core.ui.component.HotSecondaryButton
import com.healthofatitan.app.core.ui.component.HotTextButton
import com.healthofatitan.app.core.ui.component.MetricTile
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.core.ui.theme.HotType
import com.healthofatitan.app.core.ui.theme.Titanium
import com.healthofatitan.app.domain.today.DayTask
import com.healthofatitan.app.domain.today.Ritual
import com.healthofatitan.app.domain.today.RitualDot
import com.healthofatitan.app.domain.today.SupportTone
import com.healthofatitan.app.domain.today.SupportView
import com.healthofatitan.app.domain.today.TaskKind
import com.healthofatitan.app.domain.today.TaskRules
import com.healthofatitan.app.domain.today.TodayBlock
import com.healthofatitan.app.domain.today.TodayCopy
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

const val ME_WHY_TAG = "me_why"
const val SUPPORT_TAG = "support_"
const val WATER_ADD_TAG = "water_add"
const val PLATE_LINE_TAG = "today_plate_line"
const val TASK_TAG = "task_"
const val TASK_PLAY_TAG = "task_play"
const val TASK_NOT_TODAY_TAG = "task_not_today_"
const val RITUAL_TAG = "ritual_"
const val BREATHER_TAG = "today_breather"
const val ARSENAL_TAG = "today_arsenal"

/**
 * The colours of the columns (T-04). There is no red here at all: red belongs to SOS and SAF-01
 * only. A deviation is lilac and quiet, never an alarm. Plain values, so a test can check them.
 */
object SupportPalette {
    private val CALM_DARK = Color(0xFF8C919B)
    private val CALM_LIGHT = Color(0xFF6E7380)
    private val LIT_DARK = Color(0xFFFFD08A)
    private val LIT_LIGHT = Color(0xFFC9852E)
    private val NOTE_DARK = Color(0xFFC9C5FF)
    private val NOTE_LIGHT = Color(0xFF7A72D9)

    fun of(tone: SupportTone, dark: Boolean): Color = when (tone) {
        SupportTone.CALM -> if (dark) CALM_DARK else CALM_LIGHT
        SupportTone.LIT -> if (dark) LIT_DARK else LIT_LIGHT
        SupportTone.NOTE -> if (dark) NOTE_DARK else NOTE_LIGHT
    }

    fun all(): List<Color> = SupportTone.entries.flatMap { listOf(of(it, true), of(it, false)) }

    /** A colour reads as red when its hue is within 20° of red and it is saturated. */
    fun reddish(c: Color): Boolean {
        val max = maxOf(c.red, c.green, c.blue)
        val min = minOf(c.red, c.green, c.blue)
        if (max - min < 0.25f) return false
        val hue = when (max) {
            c.red -> 60f * (((c.green - c.blue) / (max - min)) % 6f)
            c.green -> 60f * ((c.blue - c.red) / (max - min) + 2f)
            else -> 60f * ((c.red - c.green) / (max - min) + 4f)
        }.let { if (it < 0f) it + 360f else it }
        return hue < 20f || hue > 340f
    }
}

@Composable
private fun BlockTitle(title: String, hint: String? = null) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        if (hint != null) {
            Spacer(Modifier.width(8.dp))
            HotOverline(hint)
        }
    }
}

// ---------------------------------------------------------------- «Я сегодня»

/**
 * The top of «Сегодня» by mockup 05b (A66 §3.2): the scene (sky of the mode, the sun of Заряд on
 * the axis of the Titan, the glow of the columns), the haiku of «Рассвет» all day (T-07) and the
 * three tiles «Заряд · Ночь · Ты». A tap on the scene opens «Почему так». The sober screen (SAF-01,
 * crisis, CARE) has no scene, no haiku and no tiles (A66 §3.8).
 */
@Composable
fun BlockMe(view: TodayView, onOpen: () -> Unit, onRoute: (String) -> Unit = {}) {
    val me = view.me
    if (!me.showTitan) {
        HotCard(
            cornice = true,
            modifier = Modifier
                .padding(horizontal = TODAY_EDGE)
                .fillMaxWidth()
                .testTag(TodayBlock.ME.tag)
                .clickable(role = Role.Button, onClickLabel = TodayCopy.WHY_TITLE, onClick = onOpen),
        ) {
            me.lead?.let { Text(it, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface) }
            Spacer(Modifier.height(4.dp))
            HotTextButton(TodayCopy.WHY_LINK, onClick = onOpen, modifier = Modifier.testTag(ME_WHY_TAG))
        }
        return
    }
    Column(Modifier.fillMaxWidth().testTag(TodayBlock.ME.tag), horizontalAlignment = Alignment.CenterHorizontally) {
        TodayScene(
            mode = me.mode?.hotMode(),
            charge = me.charge,
            dayShare = view.dayShare,
            estimate = me.estimate,
            spoken = view.sceneSpoken,
            onOpen = onOpen,
        )
        me.lead?.let {
            Text(
                it,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = TODAY_EDGE, vertical = 4.dp),
            )
        }
        me.haiku?.let { lines ->
            // A66 §3.2: three lines, Cormorant italic 18/22, Steel, centred on the ground
            Column(
                Modifier.padding(horizontal = TODAY_EDGE).testTag(ME_HAIKU_TAG).semantics(mergeDescendants = true) { },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                lines.forEach {
                    Text(
                        it,
                        style = HotType.haikuCard.copy(lineHeight = 22.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        if (view.tiles.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = TODAY_EDGE).testTag(ME_NUMBERS_TAG),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                view.tiles.forEach { tile ->
                    MetricTile(
                        label = tile.label,
                        value = tile.shown,
                        word = tile.word,
                        arc = tile.arc,
                        band = tile.band,
                        spoken = tile.spoken,
                        onClick = { onRoute(tile.route) },
                        onClickLabel = if (tile.route == HotRoute.WHY) TodayCopy.WHY_TITLE else null,
                        modifier = Modifier.weight(1f).testTag(ME_TILE_TAG + tile.kind.name.lowercase()),
                    )
                }
            }
        }
        me.calibration?.let {
            Spacer(Modifier.height(8.dp))
            HotOverline(it, modifier = Modifier.padding(horizontal = TODAY_EDGE).testTag(ME_CALIBRATION_TAG))
        }
    }
}

const val ME_HAIKU_TAG = "me_haiku"
const val ME_NUMBERS_TAG = "me_numbers"
const val ME_TILE_TAG = "me_tile_"
const val ME_CALIBRATION_TAG = "me_calibration"
const val ME_ESTIMATE_TAG = "me_estimate"

// ---------------------------------------------------------------- «Опоры»

/**
 * «Опоры» (A46 §2.3): five columns — усердие, движение, питание, питьё (D-047), сон сегодня — never red
 * (T-04). A tap opens the support with its source; «+ стакан» adds a glass at once.
 */
@Composable
fun BlockSupports(view: TodayView, today: LocalDate, actions: DayActions) {
    val dark = HotThemeTokens.colors.isDark
    HotCard(modifier = Modifier.fillMaxWidth().testTag(TodayBlock.SUPPORTS.tag)) {
        BlockTitle(TodayCopy.BLOCK_SUPPORTS, TodayCopy.BLOCK_SUPPORTS_HINT)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            view.supports.forEach { s ->
                SupportColumn(s, SupportPalette.of(s.tone, dark), Modifier.weight(1f)) { actions.onOpen(HotRoute.support(s.id.key)) }
            }
        }
        // «Тарелка дня» shares the row of «+ стакан» (S3): the block keeps its height (A46 v0.3)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val plate = view.plate
            if (plate != null) {
                Text(
                    plate.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(PLATE_LINE_TAG)
                        .clickable(role = Role.Button) { actions.onOpen(plate.route) }
                        .padding(vertical = 12.dp),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            HotTextButton(TodayCopy.WATER_ADD, onClick = { actions.onWater(today, 1) }, modifier = Modifier.testTag(WATER_ADD_TAG))
        }
    }
}

@Composable
private fun SupportColumn(s: SupportView, color: Color, modifier: Modifier, onClick: () -> Unit) {
    val rule = HotThemeTokens.colors.rule
    Column(
        modifier
            .testTag(SUPPORT_TAG + s.id.key)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = s.spoken + (if (s.estimate) ", ${TodayCopy.ESTIMATE}" else "")
                role = Role.Button
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .width(28.dp)
                .height(76.dp)
                .drawBehind {
                    val r = CornerRadius(size.width / 2f)
                    if (s.pending) {
                        drawRoundRect(
                            rule,
                            cornerRadius = r,
                            style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))),
                        )
                    } else {
                        drawRoundRect(color.copy(alpha = 0.14f), cornerRadius = r)
                        val h = size.height * s.fill.coerceIn(0f, 1f)
                        if (h > 0f) {
                            drawRoundRect(color, topLeft = Offset(0f, size.height - h), size = Size(size.width, h), cornerRadius = r)
                        }
                    }
                },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            s.value,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
        )
        Text(
            s.label,
            // «движение», «сон сегодня» are wider than a fifth of the card: two short lines, never cut
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, letterSpacing = 0.sp),
            color = HotThemeTokens.colors.textTertiary,
            maxLines = 2,
            minLines = 2,
            textAlign = TextAlign.Center,
        )
        s.caption?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = HotThemeTokens.colors.textTertiary, maxLines = 1, softWrap = false)
        }
    }
}

// ---------------------------------------------------------------- «Дела дня»

/** Where a tap on a task leads: a route, an action of the screen, or nothing (it unfolds). */
internal sealed interface TaskTap {
    data class Route(val route: String) : TaskTap
    data object Connect : TaskTap
    data object Help : TaskTap
    data object StartSmall : TaskTap
    data object Unfold : TaskTap
}

internal fun taskTap(task: DayTask): TaskTap = when (task.kind) {
    TaskKind.CONNECT_WHOOP -> TaskTap.Connect
    TaskKind.HELP -> TaskTap.Help
    TaskKind.START_QUESTIONS -> TaskTap.Route(HotRoute.CHECKIN)
    TaskKind.ASSEMBLE_WEEK -> TaskTap.Route(HotRoute.WEEK)
    TaskKind.START_SMALL -> TaskTap.StartSmall
    else -> task.route?.let { TaskTap.Route(it) } ?: TaskTap.Unfold
}

/** What a task does on a tap, a mark and «Не сегодня»; shared by «Сейчас» and the rows under it. */
internal class TaskHandlers(val onTap: () -> Unit, val onDone: () -> Unit, val onNotToday: (() -> Unit)?)

internal fun taskHandlers(task: DayTask, today: LocalDate, actions: DayActions, marks: DayMarks): TaskHandlers = TaskHandlers(
    onTap = {
        when (val tap = taskTap(task)) {
            is TaskTap.Route -> actions.onOpen(tap.route)
            TaskTap.Connect -> actions.onConnectWhoop()
            TaskTap.Help -> actions.onOpenHelp()
            TaskTap.StartSmall -> {
                actions.onStartSmall()
                actions.onTaskDone(today, task, true)
            }
            TaskTap.Unfold -> Unit
        }
    },
    onDone = {
        val now = !task.done
        actions.onTaskDone(today, task, now)
        marks.show(today, task.id, task.title, marked = now) { actions.onTaskDone(today, task, !now) }
    },
    onNotToday = if (TaskRules.movable(task) && !task.done) {
        {
            actions.onTaskToWeek(TaskRules.notToday(task, today))
            marks.show(today, task.id, task.title, marked = false, text = TodayCopy.BACK_TO_WEEK) { actions.onTaskBack(today, task.id) }
        }
    } else {
        null
    },
)

const val TASKS_MORE_TAG = "today_tasks_more"

/**
 * «Дела дня» as «Сейчас» (A66 §3.4, A46 §2.4): the first open task in one card with ▶ and the line
 * of its link to the week and the Path; «ещё N дела ›» unfolds the rest in place. A tap opens a
 * task, a swipe to the right marks it with «Отменить» for 5 s, a swipe to the left (or «Не
 * сегодня») sends it back to its week goal — never to tomorrow (T-08). Only «Сейчас» has ▶ (T-02).
 */
@Composable
fun BlockTasks(view: TodayView, today: LocalDate, actions: DayActions, marks: DayMarks) {
    val pick = view.now
    var open by rememberSaveable(today) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().testTag(TodayBlock.TASKS.tag)) {
        pick.now?.let { task ->
            val h = taskHandlers(task, today, actions, marks)
            NowCard(task, pick.link, view.me.mode?.hotMode(), h.onTap, h.onDone, h.onNotToday)
        }
        if (pick.rest.isNotEmpty()) {
            val folded = pick.now != null
            if (folded) {
                MoreLink(if (open) TodayTopCopy.LESS else TodayTopCopy.more(pick.rest.size), open) { open = !open }
            }
            if (open || !folded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    pick.rest.forEach { task ->
                        val h = taskHandlers(task, today, actions, marks)
                        TaskRow(task = task, playable = false, onTap = h.onTap, onDone = h.onDone, onNotToday = h.onNotToday)
                    }
                }
            }
        }
    }
}

@Composable
internal fun TaskRow(task: DayTask, playable: Boolean, onTap: () -> Unit, onDone: () -> Unit, onNotToday: (() -> Unit)?) {
    val tokens = HotThemeTokens.colors
    val scope = rememberCoroutineScope()
    val drag = remember(task.id) { Animatable(0f) }
    // the gesture's own sum: the decision at the end never waits for the animation
    val travel = remember(task.id) { floatArrayOf(0f) }
    val done by rememberUpdatedState(onDone)
    val notToday by rememberUpdatedState(onNotToday)
    val threshold = with(LocalDensity.current) { 96.dp.toPx() }
    var open by rememberSaveable(task.id) { mutableStateOf(false) }
    var mini by rememberSaveable(task.id, task.mini) { mutableStateOf(task.mini) }
    val connect = task.kind == TaskKind.CONNECT_WHOOP
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, tokens.rule),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .testTag(if (connect) TODAY_CONNECT_TAG else TASK_TAG + task.id)
            .offset { IntOffset(drag.value.roundToInt(), 0) }
            .pointerInput(task.id) {
                detectHorizontalDragGestures(
                    onDragStart = { travel[0] = 0f },
                    onDragEnd = {
                        val x = travel[0]
                        travel[0] = 0f
                        val back = notToday
                        when {
                            x > threshold -> done()
                            x < -threshold && back != null -> back()
                        }
                        scope.launch { drag.animateTo(0f) }
                    },
                    onDragCancel = {
                        travel[0] = 0f
                        scope.launch { drag.animateTo(0f) }
                    },
                ) { change, dx ->
                    change.consume()
                    travel[0] += dx
                    val shown = travel[0].coerceIn(-threshold * 1.4f, threshold * 1.4f)
                    scope.launch { drag.snapTo(shown) }
                }
            }
            .clickable(role = Role.Button) {
                if (taskTap(task) == TaskTap.Unfold) open = !open
                onTap()
            }
            .semantics {
                stateDescription = if (task.done) TodayCopy.DONE else ""
                customActions = listOfNotNull(
                    CustomAccessibilityAction(if (task.done) DayUiTexts.UNDO else TodayCopy.DONE) { onDone(); true },
                    onNotToday?.let { f -> CustomAccessibilityAction(TodayCopy.NOT_TODAY) { f(); true } },
                )
            },
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (playable) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .background(tokens.dawnFill[1], CircleShape)
                            .testTag(TASK_PLAY_TAG)
                            .clearAndSetSemantics { contentDescription = TodayCopy.START },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("▶", style = MaterialTheme.typography.labelLarge, color = Titanium.OnDawn)
                    }
                    Spacer(Modifier.width(12.dp))
                } else {
                    DoneDot(task.done)
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        task.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (task.done) tokens.textTertiary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // A67 С-4: which goal the step serves; the mode still decides full or mini
                    val sub = listOfNotNull(task.goal?.let(TodayCopy::goalMark), task.week, if (task.hasMini) (if (mini) TodayCopy.MINI else TodayCopy.FULL) else null)
                    if (sub.isNotEmpty()) HotOverline(sub.joinToString(" · "))
                }
            }
            if ((open || task.kind == TaskKind.START_SMALL || task.kind == TaskKind.ASSEMBLE_WEEK) && task.why != null) {
                Spacer(Modifier.height(6.dp))
                Text(task.why!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (open) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (task.hasMini) HotTextButton(if (mini) TodayCopy.FULL else TodayCopy.MINI, onClick = { mini = !mini })
                    if (onNotToday != null) {
                        HotTextButton(TodayCopy.NOT_TODAY, onClick = onNotToday, modifier = Modifier.testTag(TASK_NOT_TODAY_TAG + task.id))
                    }
                }
            }
        }
    }
}

@Composable
private fun DoneDot(done: Boolean) {
    val tokens = HotThemeTokens.colors
    val ink = MaterialTheme.colorScheme.onSurface
    Spacer(
        Modifier
            .size(36.dp)
            .clearAndSetSemantics { }
            .drawBehind {
                val r = size.minDimension / 2f - 1.dp.toPx()
                if (done) {
                    drawCircle(ink.copy(alpha = 0.16f), r)
                    drawLine(ink, Offset(center.x - r * 0.38f, center.y), Offset(center.x - r * 0.08f, center.y + r * 0.3f), 1.6.dp.toPx())
                    drawLine(ink, Offset(center.x - r * 0.08f, center.y + r * 0.3f), Offset(center.x + r * 0.42f, center.y - r * 0.32f), 1.6.dp.toPx())
                } else {
                    drawCircle(tokens.rule, r, style = Stroke(1.dp.toPx()))
                }
            },
    )
}

// ---------------------------------------------------------------- «Ритм дня»

/**
 * «Ритм дня» (A46 §2.5, D-043): the points of the rituals (Рассвет, Закрыть день; Переход and
 * Приземление when on), the windows of the body, «Передышка» and «Арсенал». «Закрыть день»
 * unfolds here once its point is active; the state cards of A44 follow.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BlockRhythm(view: TodayView, day: DayUiState?, actions: DayActions, slots: TodaySlots) {
    var closing by rememberSaveable(day?.plan?.dayKey) { mutableStateOf(false) }
    val plan = day?.plan
    HotCard(modifier = Modifier.fillMaxWidth().testTag(TodayBlock.RHYTHM.tag)) {
        BlockTitle(TodayCopy.BLOCK_RHYTHM)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            view.rhythm.dots.forEach { dot ->
                RitualPoint(dot, Modifier.weight(1f)) {
                    if (dot.ritual == Ritual.CLOSE_DAY && plan != null) closing = !closing else actions.onRitual(dot.ritual)
                }
            }
        }
        if (view.rhythm.windows.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                view.rhythm.windows.forEach { HotOverline(it.text) }
                if (view.rhythm.estimate) HotOverline(TodayCopy.ESTIMATE)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val breather = Modifier.weight(1f).testTag(BREATHER_TAG)
            if (view.rhythm.breatherLit) {
                HotPrimaryButton(TodayCopy.BREATHER, onClick = { actions.onOpen(HotRoute.BREATHER) }, modifier = breather)
            } else {
                HotSecondaryButton(TodayCopy.BREATHER, onClick = { actions.onOpen(HotRoute.BREATHER) }, modifier = breather)
            }
            Spacer(Modifier.width(8.dp))
            HotTextButton(TodayCopy.ARSENAL, onClick = { actions.onOpen(HotRoute.ARSENAL) }, modifier = Modifier.testTag(ARSENAL_TAG))
        }
    }
    if (closing && plan != null && day != null) {
        val mode = plan.regime.mode()
        val evening = day.eveningFor
        val closed = day.closedFor
        when {
            evening != null -> CloseDayCard(
                plan = plan,
                day = evening,
                mode = mode,
                onClose = actions.onCloseDay,
                onCrisis = actions.onCrisis,
                onOpenHelp = actions.onOpenHelp,
                extras = slots.closeDay,
            )
            closed != null -> DayClosedCard(plan, day.closed, mode)
            else -> CloseDayCard(plan, plan.dayKey, mode, actions.onCloseDay, actions.onCrisis, actions.onOpenHelp, extras = slots.closeDay)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { slots.top(this) }
    if (day?.planToday == true) NotificationAsk()
}

@Composable
private fun RitualPoint(dot: RitualDot, modifier: Modifier, onClick: () -> Unit) {
    val tokens = HotThemeTokens.colors
    val lit = tokens.dawnFill[1]
    val ink = MaterialTheme.colorScheme.onSurface
    Column(
        modifier
            .testTag(RITUAL_TAG + dot.ritual.name.lowercase())
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                stateDescription = when {
                    dot.done -> TodayCopy.DONE
                    dot.active -> TodayCopy.NOW
                    else -> ""
                }
            }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(
            Modifier
                .size(18.dp)
                .drawBehind {
                    val r = size.minDimension / 2f - 1.dp.toPx()
                    when {
                        dot.done -> drawCircle(ink, r)
                        dot.active -> {
                            drawCircle(lit.copy(alpha = 0.25f), r + 1.dp.toPx())
                            drawCircle(lit, r * 0.6f)
                        }
                        else -> drawCircle(tokens.rule, r, style = Stroke(1.dp.toPx()))
                    }
                },
        )
        Spacer(Modifier.height(6.dp))
        Text(
            dot.ritual.word,
            style = MaterialTheme.typography.labelSmall,
            color = if (dot.active) ink else tokens.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
