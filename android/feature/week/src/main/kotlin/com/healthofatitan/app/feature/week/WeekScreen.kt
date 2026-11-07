package com.healthofatitan.app.feature.week

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.healthofatitan.app.core.model.nav.HotRoute
import com.healthofatitan.app.core.ui.component.HotCard
import com.healthofatitan.app.core.ui.component.HotOverline
import com.healthofatitan.app.core.ui.component.HotPendingCard
import com.healthofatitan.app.core.ui.component.HotPrimaryButton
import com.healthofatitan.app.core.ui.component.HotSecondaryButton
import com.healthofatitan.app.core.ui.component.HotSpacing
import com.healthofatitan.app.core.ui.component.HotTextButton
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.core.ui.theme.HotType
import com.healthofatitan.app.domain.today.WeekGoal
import com.healthofatitan.app.domain.today.week.BodyArrow
import com.healthofatitan.app.domain.today.week.BodyRow
import com.healthofatitan.app.domain.today.week.DayColumn
import com.healthofatitan.app.domain.today.week.FitnessView
import com.healthofatitan.app.domain.today.week.GoalLine
import com.healthofatitan.app.domain.today.week.PatternView
import com.healthofatitan.app.domain.today.week.RitualAnswers
import com.healthofatitan.app.domain.today.week.Sex
import com.healthofatitan.app.domain.today.week.SupportWeekRow
import com.healthofatitan.app.domain.today.week.TitanWeekLook
import com.healthofatitan.app.domain.today.week.WeekCopy
import com.healthofatitan.app.domain.today.week.WeekScoring
import com.healthofatitan.app.integration.whoop.week.BodyInputs
import com.healthofatitan.app.integration.whoop.week.DayDetail
import com.healthofatitan.app.integration.whoop.week.WeekScreenState
import java.time.LocalDate

/** Tab «Неделя» (A52, D-044, D-045): seven blocks, the Titan of the week and the Sunday ritual. */
fun NavGraphBuilder.weekScreen(stateful: Boolean = true, onOpenHelp: () -> Unit = {}) {
    composable(route = HotRoute.WEEK) {
        if (stateful) WeekRoute(onOpenHelp) else WeekScreen(WeekUiState(), WeekActions(onHelp = onOpenHelp))
    }
}

const val WEEK_SCREEN_TAG = "screen_week"
const val WEEK_GLANCE_TAG = "week_glance"
const val WEEK_COLUMNS_TAG = "week_columns"
const val WEEK_DAYS_TAG = "week_days_in_support"
const val WEEK_GOALS_TAG = "week_goals"
const val WEEK_SUPPORTS_TAG = "week_supports"
const val WEEK_BODY_TAG = "week_body"
const val WEEK_PATTERNS_TAG = "week_patterns"
const val WEEK_RITUAL_CARD_TAG = "week_ritual_card"
const val WEEK_FITNESS_TAG = "week_fitness"
const val WEEK_HELP_TAG = "week_help"
const val WEEK_RIBBON_TAG = "week_ribbon"
const val WEEK_DAY_DETAIL_TAG = "week_day_detail"

/** What the screen can do; empty by default so previews and tests need no ViewModel. */
data class WeekActions(
    val onLighter: (String) -> Unit = {},
    val onHeavier: (String) -> Unit = {},
    val onRemove: (String) -> Unit = {},
    val onAddReminder: (String, Int) -> Unit = { _, _ -> },
    val onTry: (WeekGoal) -> Unit = {},
    val onSaveInputs: (Int?, Sex?, Double?) -> Unit = { _, _, _ -> },
    val onFinishRitual: (RitualAnswers) -> Unit = {},
    val onShare: (TitanWeekLook) -> Unit = {},
    val onHelp: () -> Unit = {},
)

@Composable
private fun WeekRoute(onOpenHelp: () -> Unit, viewModel: WeekViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    WeekScreen(
        state,
        WeekActions(
            onLighter = viewModel::lighter,
            onHeavier = viewModel::heavier,
            onRemove = viewModel::remove,
            onAddReminder = viewModel::addReminder,
            onTry = viewModel::tryPattern,
            onSaveInputs = viewModel::saveInputs,
            onFinishRitual = viewModel::finishRitual,
            onShare = { WeekShare.share(context, it) },
            onHelp = onOpenHelp,
        ),
    )
}

/**
 * The screen of A52 §1, top to bottom: the Titan of the week with the seven columns under it and
 * «Дни в опоре N из 7» (mark 4); the Sunday card; «Цели недели»; «Опоры за неделю»; «Тело за
 * неделю»; «Закономерности» (≤ 3); the fitness age behind its flag; the year ribbon. No red
 * anywhere except «Помощь» (W-02).
 */
@Composable
fun WeekScreen(state: WeekUiState, actions: WeekActions = WeekActions(), modifier: Modifier = Modifier, ritualOpen: Boolean = false) {
    var ritual by rememberSaveable { mutableStateOf(ritualOpen) }
    val week = state.week
    if (ritual && week?.ritual != null) {
        WeekRitualScreen(week, state.tried, onDone = { actions.onFinishRitual(it); ritual = false }, onClose = { ritual = false }, onShare = actions.onShare)
        return
    }
    Column(
        modifier
            .fillMaxSize()
            .testTag(WEEK_SCREEN_TAG)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = HotSpacing.edge, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(HotSpacing.cardGap),
    ) {
        if (week == null) {
            HotPendingCard(WeekCopy.TITAN_FORMING, caption = WeekCopy.GOAL_EMPTY)
            return@Column
        }
        val v = week.view
        HotOverline(WeekCopy.weekNumber(v.number))
        BlockGlance(week)
        if (v.ritualCard) RitualCard(onOpen = { ritual = true })
        BlockGoals(week, actions)
        BlockSupports(week)
        BlockBody(week, actions.onHelp)
        BlockPatterns(v.patterns, v.patternsEmpty, state.tried, actions.onTry)
        v.fitness?.let { BlockFitness(it, week.bodyInputs, actions.onSaveInputs) }
        if (week.frames.size >= 2) Ribbon(week.frames)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
internal fun BlockTitle(title: String, hint: String? = null) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.semantics { heading() })
        if (hint != null) {
            Spacer(Modifier.width(8.dp))
            HotOverline(hint)
        }
    }
}

// ---------------------------------------------------------------- Неделя одним взглядом

@Composable
private fun BlockGlance(week: WeekScreenState) {
    val v = week.view
    var picked by remember { mutableStateOf<LocalDate?>(null) }
    HotCard(cornice = true, modifier = Modifier.fillMaxWidth().testTag(WEEK_GLANCE_TAG)) {
        BlockTitle(WeekCopy.TITAN, if (v.titan.revealed) null else WeekCopy.TITAN_FORMING.lowercase())
        Spacer(Modifier.height(12.dp))
        WeekTitanFigure(v.titan, Modifier.fillMaxWidth().aspectRatio(1.35f))
        Spacer(Modifier.height(14.dp))
        Columns(v.columns, picked) { d -> picked = if (picked == d) null else d }
        picked?.let { d -> week.dayDetails[d]?.let { DayCard(it, v.columns.first { c -> c.day == d }) } }
        Spacer(Modifier.height(16.dp))
        DaysInSupport(v.daysInSupport, v.mark)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(WeekCopy.GOALS_TOTAL, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(WeekCopy.ofTotal(v.goalsDone, v.goalsTotal), style = HotType.ringValue, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** Seven columns Monday–Sunday (W-08): the mode colour, the fill = supports, the dot = a task, today ringed, the future an outline. */
@Composable
private fun Columns(columns: List<DayColumn>, picked: LocalDate?, onPick: (LocalDate) -> Unit) {
    val tokens = HotThemeTokens.colors
    val outline = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
    val quiet = tokens.ruleSoft
    Row(
        Modifier.fillMaxWidth().testTag(WEEK_COLUMNS_TAG).semantics { contentDescription = WeekCopy.SPOKEN_COLUMNS },
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        columns.forEach { c ->
            val color = c.mode?.hotMode()?.color() ?: MaterialTheme.colorScheme.onSurfaceVariant
            val spoken = WeekScoring.short(c.day) + ": " + when {
                c.future -> WeekCopy.DAY_FUTURE
                c.sick -> WeekCopy.DAY_REST
                c.noData -> WeekCopy.DAY_NO_DATA
                c.inSupport -> WeekCopy.DAY_IN_SUPPORT
                else -> "${WeekCopy.SUPPORTS_FILLED} ${c.supportsFilled}"
            }
            Column(
                Modifier
                    .width(40.dp)
                    .testTag("week_col_${c.day.dayOfWeek.value}")
                    .clickable(enabled = !c.future, role = Role.Button) { onPick(c.day) }
                    .semantics(mergeDescendants = true) { contentDescription = spoken },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Canvas(Modifier.width(22.dp).height(64.dp)) {
                    val r = CornerRadius(size.width / 2)
                    if (c.future) {
                        drawRoundRect(outline, cornerRadius = r, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f))))
                    } else {
                        drawRoundRect(quiet, cornerRadius = r)
                        val h = size.height * (if (c.sick) 0.5f else c.fill.coerceAtLeast(if (c.noData) 0f else 0.08f))
                        if (h > 0f) drawRoundRect(color.copy(alpha = if (c.inSupport) 0.95f else 0.55f), Offset(0f, size.height - h), Size(size.width, h), r)
                        if (c.today || c.day == picked) {
                            drawRoundRect(color, Offset(-3.dp.toPx(), -3.dp.toPx()), Size(size.width + 6.dp.toPx(), size.height + 6.dp.toPx()), CornerRadius(size.width), style = Stroke(1.5.dp.toPx()))
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.size(6.dp)) {
                    if (c.taskDone) Canvas(Modifier.fillMaxSize()) { drawCircle(color) }
                }
                Spacer(Modifier.height(4.dp))
                Text(WeekScoring.short(c.day), style = HotType.mono, color = if (c.today) MaterialTheme.colorScheme.onSurface else tokens.textTertiary)
            }
        }
    }
}

@Composable
private fun DayCard(d: DayDetail, c: DayColumn) {
    Spacer(Modifier.height(12.dp))
    Column(Modifier.fillMaxWidth().testTag(WEEK_DAY_DETAIL_TAG).padding(vertical = 4.dp)) {
        HotOverline("${WeekScoring.short(d.day)} · ${d.day.dayOfMonth} · ${WeekCopy.READ_ONLY}")
        Spacer(Modifier.height(6.dp))
        val head = listOfNotNull(d.mode?.word, if (c.inSupport) WeekCopy.DAY_IN_SUPPORT else null, if (c.sick) WeekCopy.DAY_REST else null).joinToString(" · ")
        if (head.isNotEmpty()) Text(head, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Text("${WeekCopy.SUPPORTS_FILLED}: ${WeekCopy.ofTotal(d.supportsFilled, 5)} · ${WeekCopy.TASKS_DONE}: ${d.tasksDone}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        d.tasks.take(3).forEach { Text("· $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        d.haiku?.forEach { Text(it, style = HotType.quote, color = MaterialTheme.colorScheme.onSurface) }
    }
}

/** «Дни в опоре N из 7» on a scale with the mark (4; 3 in the first week; 2 in a week of return). */
@Composable
private fun DaysInSupport(days: Int, mark: Int) {
    val accent = MaterialTheme.colorScheme.primary
    val track = HotThemeTokens.colors.rule
    val ink = MaterialTheme.colorScheme.onSurface
    Column(Modifier.fillMaxWidth().testTag(WEEK_DAYS_TAG).semantics(mergeDescendants = true) { contentDescription = "${WeekCopy.DAYS_IN_SUPPORT}: ${WeekCopy.ofTotal(days, 7)}, ${WeekCopy.markHint(mark)}" }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(WeekCopy.DAYS_IN_SUPPORT, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(WeekCopy.ofTotal(days, 7), style = HotType.numeral, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(8.dp))
        Canvas(Modifier.fillMaxWidth().height(14.dp).clearAndSetSemantics { }) {
            val y = size.height / 2
            val h = 4.dp.toPx()
            drawLine(track, Offset(h, y), Offset(size.width - h, y), h, StrokeCap.Round)
            val step = (size.width - 2 * h) / 7f
            if (days > 0) drawLine(accent, Offset(h, y), Offset(h + step * days, y), h, StrokeCap.Round)
            val mx = h + step * mark
            drawLine(ink, Offset(mx, 0f), Offset(mx, size.height), 1.5.dp.toPx())
            drawCircle(accent, 5.dp.toPx(), Offset(h + step * days, y))
        }
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(mark.toFloat()))
            HotOverline(mark.toString())
            Spacer(Modifier.weight((7 - mark).toFloat().coerceAtLeast(0.01f)))
        }
    }
}

@Composable
private fun RitualCard(onOpen: () -> Unit) {
    HotCard(modifier = Modifier.fillMaxWidth().testTag(WEEK_RITUAL_CARD_TAG)) {
        BlockTitle(WeekCopy.BLOCK_RITUAL)
        Spacer(Modifier.height(6.dp))
        Text(WeekCopy.RITUAL_OPEN_UNTIL, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        HotPrimaryButton(WeekCopy.RITUAL_CARD, onClick = onOpen, modifier = Modifier.fillMaxWidth(), trailingArrow = true)
    }
}

// ---------------------------------------------------------------- Цели недели

@Composable
private fun BlockGoals(week: WeekScreenState, actions: WeekActions) {
    val v = week.view
    var open by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    HotCard(modifier = Modifier.fillMaxWidth().testTag(WEEK_GOALS_TAG)) {
        BlockTitle(WeekCopy.BLOCK_GOALS)
        v.goalsNote?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = HotThemeTokens.colors.textTertiary, modifier = Modifier.testTag("week_goals_note"))
        }
        Spacer(Modifier.height(8.dp))
        v.goals.forEach { line ->
            GoalRow(line, expanded = open == line.goal.id, onTap = { open = if (open == line.goal.id) null else line.goal.id }, actions = actions)
        }
        if (v.returnedToday > 0) HotOverline("${WeekCopy.GOAL_RETURNED}: ${v.returnedToday}", modifier = Modifier.padding(top = 6.dp))
        if (adding) {
            ReminderForm(onSave = { t, d -> actions.onAddReminder(t, d); adding = false }, onCancel = { adding = false })
        } else {
            HotTextButton(WeekCopy.REMINDER_ADD, onClick = { adding = true })
        }
    }
}

@Composable
private fun GoalRow(line: GoalLine, expanded: Boolean, onTap: () -> Unit, actions: WeekActions) {
    val accent = MaterialTheme.colorScheme.primary
    val dim = HotThemeTokens.colors.rule
    Column(Modifier.fillMaxWidth().testTag("week_goal_${line.goal.id}")) {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = WeekCopy.GOAL_CHANGE, onClick = onTap).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(line.goal.title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            line.dots?.let { dots ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(end = 10.dp).clearAndSetSemantics { }) {
                    dots.forEach { on ->
                        Canvas(Modifier.size(8.dp)) {
                            if (on) drawCircle(accent) else drawCircle(dim, style = Stroke(1.dp.toPx()))
                        }
                    }
                }
            }
            Text(line.text, style = HotType.ringValue, color = MaterialTheme.colorScheme.onSurface)
        }
        if (expanded) {
            Text(WeekCopy.GOAL_NO_PENALTY, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                HotTextButton(WeekCopy.GOAL_LIGHTER, onClick = { actions.onLighter(line.goal.id) })
                HotTextButton(WeekCopy.GOAL_MORE, onClick = { actions.onHeavier(line.goal.id) })
                HotTextButton(WeekCopy.GOAL_REMOVE, onClick = { actions.onRemove(line.goal.id) })
            }
        }
    }
}

@Composable
private fun ReminderForm(onSave: (String, Int) -> Unit, onCancel: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var days by remember { mutableStateOf(3) }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        OutlinedTextField(title, { title = it.take(WeekRepositoryLimits.TITLE) }, label = { Text(WeekCopy.REMINDER_TITLE) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Text(WeekCopy.REMINDER_DAYS, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            (1..7).forEach { n ->
                HotTextButton(if (n == days) "[$n]" else "$n", onClick = { days = n })
            }
        }
        Text(WeekCopy.REMINDER_HINT, style = MaterialTheme.typography.bodySmall, color = HotThemeTokens.colors.textTertiary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HotTextButton(WeekCopy.CANCEL, onClick = onCancel)
            HotTextButton(WeekCopy.SAVE, onClick = { onSave(title, days) }, enabled = title.isNotBlank())
        }
    }
}

internal object WeekRepositoryLimits {
    const val TITLE = 60
}

// ---------------------------------------------------------------- Опоры за неделю

@Composable
private fun BlockSupports(week: WeekScreenState) {
    val v = week.view
    HotCard(modifier = Modifier.fillMaxWidth().testTag(WEEK_SUPPORTS_TAG)) {
        BlockTitle(WeekCopy.BLOCK_SUPPORTS)
        v.shelterLine?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = HotType.quote, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(6.dp))
        v.supports.forEach { SupportRow(it) }
    }
}

@Composable
private fun SupportRow(row: SupportWeekRow) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("week_support_${row.id.name.lowercase()}")) {
        Row(verticalAlignment = Alignment.Top) {
            Text(row.label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(132.dp))
            Column(Modifier.weight(1f)) {
                row.lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        row.skew?.let {
            Row(Modifier.padding(top = 6.dp).testTag("week_skew"), verticalAlignment = Alignment.CenterVertically) {
                ScalesGlyph(Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(it.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/** «Весы»: the pictogram of a skew — a tilt, not a grade; in the text colour, never red. */
@Composable
private fun ScalesGlyph(modifier: Modifier) {
    val c = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier.clearAndSetSemantics { }) {
        val w = size.width
        val h = size.height
        val s = 1.5.dp.toPx()
        drawLine(c, Offset(w / 2, h * 0.15f), Offset(w / 2, h * 0.9f), s)
        drawLine(c, Offset(w * 0.1f, h * 0.4f), Offset(w * 0.9f, h * 0.25f), s, StrokeCap.Round)
        drawCircle(c, w * 0.12f, Offset(w * 0.15f, h * 0.62f), style = Stroke(s))
        drawCircle(c, w * 0.12f, Offset(w * 0.85f, h * 0.47f), style = Stroke(s))
        drawLine(c, Offset(w * 0.3f, h * 0.9f), Offset(w * 0.7f, h * 0.9f), s, StrokeCap.Round)
    }
}

// ---------------------------------------------------------------- Тело за неделю

@Composable
private fun BlockBody(week: WeekScreenState, onHelp: () -> Unit) {
    val body = week.view.body
    HotCard(modifier = Modifier.fillMaxWidth().testTag(WEEK_BODY_TAG)) {
        BlockTitle(WeekCopy.BLOCK_BODY, WeekCopy.B_CORRIDOR)
        Spacer(Modifier.height(8.dp))
        if (!week.whoopConnected) {
            Text(WeekCopy.B_NO_DATA, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            body.rows.forEach { BodyLine(it) }
        }
        if (body.redFlag) {
            Spacer(Modifier.height(10.dp))
            Text(WeekCopy.B_FLAG, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))
            // the only red of the module: help (A11 §12.1)
            androidx.compose.material3.Button(
                onClick = onHelp,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                modifier = Modifier.testTag(WEEK_HELP_TAG),
            ) { Text(WeekCopy.B_HELP) }
        }
    }
}

@Composable
private fun BodyLine(row: BodyRow) {
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).semantics(mergeDescendants = true) { contentDescription = row.spoken }, verticalAlignment = Alignment.CenterVertically) {
        Text(row.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Spark(row.days, Modifier.width(72.dp).height(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            when (row.arrow) {
                BodyArrow.UP -> "↗"
                BodyArrow.DOWN -> "↘"
                BodyArrow.SAME -> "="
                null -> "·"
            },
            style = HotType.ringValue, color = ink, modifier = Modifier.width(18.dp),
        )
    }
}

/** A 7-day mini graph, no axes, no numbers. */
@Composable
private fun Spark(values: List<Double?>, modifier: Modifier) {
    val c = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier.clearAndSetSemantics { }) {
        val pts = values.withIndex().filter { it.value != null }
        if (pts.size < 2) return@Canvas
        val lo = pts.minOf { it.value!! }
        val hi = pts.maxOf { it.value!! }
        val span = (hi - lo).takeIf { it > 0 } ?: 1.0
        val step = size.width / 6f
        pts.zipWithNext().forEach { (a, b) ->
            val pa = Offset(a.index * step, (size.height * (1 - (a.value!! - lo) / span)).toFloat())
            val pb = Offset(b.index * step, (size.height * (1 - (b.value!! - lo) / span)).toFloat())
            drawLine(c, pa, pb, 1.5.dp.toPx(), StrokeCap.Round)
        }
    }
}

// ---------------------------------------------------------------- Закономерности

@Composable
private fun BlockPatterns(patterns: List<PatternView>, empty: String?, tried: List<WeekGoal>, onTry: (WeekGoal) -> Unit) {
    HotCard(modifier = Modifier.fillMaxWidth().testTag(WEEK_PATTERNS_TAG)) {
        BlockTitle(WeekCopy.BLOCK_PATTERNS)
        Spacer(Modifier.height(8.dp))
        empty?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        patterns.forEach { p ->
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag("week_pattern")) {
                Text("«${p.pattern.text}»", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                HotOverline("${p.label} · ${p.evidence}")
                p.actionGoal?.let { g ->
                    if (tried.any { it.id == g.id }) {
                        HotOverline(WeekCopy.PATTERN_TAKEN, modifier = Modifier.padding(top = 4.dp))
                    } else {
                        HotTextButton(WeekCopy.PATTERN_TRY, onClick = { onTry(g) })
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Фитнес-возраст (флаг)

@Composable
private fun BlockFitness(f: FitnessView, inputs: BodyInputs, onSave: (Int?, Sex?, Double?) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    HotCard(modifier = Modifier.fillMaxWidth().testTag(WEEK_FITNESS_TAG)) {
        BlockTitle(WeekCopy.FIT_TITLE, WeekCopy.REVIEW)
        Spacer(Modifier.height(8.dp))
        f.age?.let { Text(it.toString(), style = HotType.headline, color = MaterialTheme.colorScheme.onSurface) }
        f.pace?.let { Text(it, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface) }
        f.room?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Text(f.drivers, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HotOverline(f.note, modifier = Modifier.padding(top = 6.dp))
        f.missing?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 6.dp)) }
        if (f.waistDue && f.missing == null) Text(WeekCopy.FIT_WAIST_DUE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (editing) {
            var age by remember { mutableStateOf(inputs.ageYears?.toString().orEmpty()) }
            var waist by remember { mutableStateOf(inputs.waistCm?.toString().orEmpty()) }
            var sex by remember { mutableStateOf(inputs.sex) }
            OutlinedTextField(age, { age = it.filter(Char::isDigit).take(3) }, label = { Text(WeekCopy.FIT_AGE) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(waist, { waist = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(5) }, label = { Text(WeekCopy.FIT_WAIST) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            Row {
                HotTextButton((if (sex == Sex.MALE) "● " else "○ ") + WeekCopy.FIT_MALE, onClick = { sex = Sex.MALE })
                HotTextButton((if (sex == Sex.FEMALE) "● " else "○ ") + WeekCopy.FIT_FEMALE, onClick = { sex = Sex.FEMALE })
            }
            HotSecondaryButton(WeekCopy.SAVE, onClick = {
                onSave(age.toIntOrNull(), sex, waist.replace(',', '.').toDoubleOrNull())
                editing = false
            })
        } else {
            HotTextButton(WeekCopy.FIT_WAIST, onClick = { editing = true })
        }
    }
}

// ---------------------------------------------------------------- Лента года

@Composable
private fun Ribbon(frames: List<TitanWeekLook>) {
    Column(Modifier.fillMaxWidth().testTag(WEEK_RIBBON_TAG)) {
        BlockTitle(WeekCopy.TITAN_YEAR)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            frames.takeLast(52).forEach { f ->
                WeekTitanFigure(f, Modifier.width(72.dp).height(96.dp))
            }
        }
    }
}

/** Colours this screen may use (W-02): the mode lights, the accent, text; red only for help. */
object WeekPalette {
    @Composable
    fun used(): Map<String, Color> {
        val t = HotThemeTokens.colors
        val s = MaterialTheme.colorScheme
        return mapOf(
            "razgon" to t.modeRazgon, "rovno" to t.modeRovno, "berezhno" to t.modeBerezhno, "accent" to s.primary,
            "text" to s.onSurface, "textVariant" to s.onSurfaceVariant, "rule" to t.rule, "ruleSoft" to t.ruleSoft,
        ) + WeekTitanArt.sky(com.healthofatitan.app.domain.today.week.WeekSky.CLEAR, t.isDark).mapIndexed { i, c -> "skyClear$i" to c } +
            WeekTitanArt.sky(com.healthofatitan.app.domain.today.week.WeekSky.HAZE, t.isDark).mapIndexed { i, c -> "skyHaze$i" to c } +
            WeekTitanArt.sky(com.healthofatitan.app.domain.today.week.WeekSky.DUSK, t.isDark).mapIndexed { i, c -> "skyDusk$i" to c }
    }

    /** The one exception: «Помощь». */
    @Composable
    fun help(): Color = MaterialTheme.colorScheme.error
}
