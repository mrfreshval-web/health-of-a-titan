package com.healthofatitan.app.feature.path

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.healthofatitan.app.core.model.nav.HotRoute
import com.healthofatitan.app.core.ui.R
import com.healthofatitan.app.core.ui.component.HotTextButton
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.core.ui.theme.Titanium
import com.healthofatitan.app.domain.engine.CrisisGuard
import com.healthofatitan.app.domain.path.PathCopy
import com.healthofatitan.app.domain.today.DayMode
import com.healthofatitan.app.feature.path.logic.BehindCopy
import com.healthofatitan.app.feature.path.logic.BehindFilter
import com.healthofatitan.app.feature.path.logic.BehindPeriod
import com.healthofatitan.app.feature.path.logic.BehindRecord
import com.healthofatitan.app.feature.path.logic.BehindSearch
import com.healthofatitan.app.feature.path.logic.CalendarCell
import com.healthofatitan.app.feature.path.logic.CellKind
import com.healthofatitan.app.feature.path.logic.FactCard
import com.healthofatitan.app.feature.path.logic.MonthRow
import com.healthofatitan.app.feature.path.logic.PeriodFacts
import com.healthofatitan.app.feature.path.logic.PeriodSel
import com.healthofatitan.app.feature.path.logic.RuWords
import com.healthofatitan.app.feature.path.logic.YearCalendar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/** The same tag the app's navigation looks for (`BEHIND_SCREEN_TAG` in HotApp). */
const val BEHIND_TAG = "screen_behind"
const val BEHIND_SEARCH_TAG = "behind_search"
const val BEHIND_HERO_TAG = "behind_hero"
const val BEHIND_CALENDAR_TAG = "behind_calendar"
const val BEHIND_RESULTS_TAG = "behind_results"
const val BEHIND_EMPTY_TAG = "behind_empty"
const val BEHIND_CRISIS_TAG = "behind_crisis"
const val BEHIND_PREV_TAG = "behind_prev"
const val BEHIND_NEXT_TAG = "behind_next"

fun behindFilterTag(f: BehindFilter) = "behind_filter_${f.name.lowercase()}"

fun behindPeriodTag(p: BehindPeriod) = "behind_period_${p.name.lowercase()}"

/**
 * The tab «За плечами» (A66 §6, mockup 08): the search, the period, the hero of the period (the
 * Titan from the back and three facts), the year calendar by the light of the mode, the filters.
 *
 * @param stateful false renders the empty archive without ViewModels (navigation tests)
 * @param onHelp «Помощь» (112 · 911): the search is free text and goes through CrisisGuard (§6.3)
 */
fun NavGraphBuilder.behindScreen(stateful: Boolean = true, onHelp: () -> Unit = {}) {
    composable(route = HotRoute.BEHIND) {
        if (stateful) {
            BehindRoute(onHelp)
        } else {
            BehindScreen(BehindData.empty(LocalDate.now()), onHelp = onHelp)
        }
    }
}

@HiltViewModel
class BehindViewModel @Inject constructor(repository: BehindRepository) : ViewModel() {
    val data: StateFlow<BehindData?> = repository.data.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
private fun BehindRoute(onHelp: () -> Unit, viewModel: BehindViewModel = hiltViewModel()) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    BehindScreen(data ?: BehindData.empty(LocalDate.now()), onHelp = onHelp)
}

/**
 * @param initialQuery, initialFilter, initialPeriod the state to open with (tests, screenshots)
 */
@Composable
fun BehindScreen(
    data: BehindData,
    onHelp: () -> Unit,
    modifier: Modifier = Modifier,
    initialQuery: String = "",
    initialFilter: BehindFilter? = null,
    initialPeriod: BehindPeriod = BehindPeriod.YEAR,
) {
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var periodName by rememberSaveable { mutableStateOf(initialPeriod.name) }
    var anchorDay by rememberSaveable { mutableStateOf(data.today.toEpochDay()) }
    var filterName by rememberSaveable { mutableStateOf(initialFilter?.name) }
    val sel = PeriodSel(BehindPeriod.valueOf(periodName), LocalDate.ofEpochDay(anchorDay))
    // «Трудные периоды» never stays open when the state turns hard (§6.5 п. 7)
    val filter = filterName?.let { BehindFilter.valueOf(it) }?.takeIf { it != BehindFilter.HARD || data.hardAvailable }
    val crisis = remember(query) { if (query.isBlank()) 0 else CrisisGuard.assess(query).level.value }
    LaunchedEffect(crisis) { if (crisis >= 2) onHelp() }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 24.dp)
            .testTag(BEHIND_TAG),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SearchField(query, onQuery = { query = it })
        if (crisis >= 1) {
            Row(Modifier.fillMaxWidth().testTag(BEHIND_CRISIS_TAG), verticalAlignment = Alignment.CenterVertically) {
                Text(PathCopy.CRISIS_LINE, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                HotTextButton(PathCopy.HELP, onClick = onHelp)
            }
        }
        PeriodBar(
            sel,
            onPeriod = { periodName = it.name },
            onStep = { by -> sel.step(by, data.today, data.since)?.let { anchorDay = it.anchor.toEpochDay() } },
            canStep = { by -> sel.step(by, data.today, data.since) != null },
        )
        if (query.isNotBlank()) {
            Results(data, query, sel, onAllTime = { periodName = BehindPeriod.ALL.name })
        } else {
            Hero(PeriodFacts.of(data.records, sel, data.edCare), onFact = { filterName = it.name })
            Calendar(data, sel, onMonth = { ym ->
                periodName = BehindPeriod.MONTH.name
                anchorDay = ym.atDay(1).toEpochDay()
            })
            Legend()
            Filters(filter, data.hardAvailable, onFilter = { f -> filterName = if (filter == f) null else f.name })
            filter?.let { FilterList(BehindSearch.filtered(data.records, it, sel)) }
        }
    }
}

// ── The search ─────────────────────────────────────────────────────────────────────────────────

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit) {
    val tokens = HotThemeTokens.colors
    val steel = MaterialTheme.colorScheme.onSurfaceVariant
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(tokens.glassBottom)
            .border(1.dp, tokens.hairline, shape)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(18.dp)) {
            val r = size.minDimension * 0.33f
            val c = Offset(r + 1.dp.toPx(), r + 1.dp.toPx())
            drawCircle(steel, r, c, style = Stroke(1.6.dp.toPx()))
            drawLine(steel, Offset(c.x + r * 0.72f, c.y + r * 0.72f), Offset(size.width - 1.dp.toPx(), size.height - 1.dp.toPx()), strokeWidth = 1.6.dp.toPx())
        }
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) Text(BehindCopy.SEARCH, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp), color = steel)
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().testTag(BEHIND_SEARCH_TAG).semantics { contentDescription = BehindCopy.SEARCH },
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQuery("") }, modifier = Modifier.size(40.dp)) {
                Icon(painterResource(R.drawable.core_ui_ic_close), contentDescription = BehindCopy.CLEAR, tint = steel, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun Results(data: BehindData, query: String, sel: PeriodSel, onAllTime: () -> Unit) {
    val groups = BehindSearch.search(data.records, query, sel)
    Column(Modifier.fillMaxWidth().testTag(BEHIND_RESULTS_TAG), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (groups.isEmpty()) {
            Text(BehindCopy.nothingFound(sel.label), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (sel.period != BehindPeriod.ALL) HotTextButton(BehindCopy.SEARCH_ALL, onClick = onAllTime)
        }
        groups.forEach { g ->
            Text(g.title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.semantics { heading() })
            g.items.forEach { RecordRow(it) }
            if (g.more > 0) Text(BehindCopy.more(g.more), style = MaterialTheme.typography.bodySmall, color = dawnText())
        }
    }
}

@Composable
private fun RecordRow(r: BehindRecord) {
    Column(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 4.dp), verticalArrangement = Arrangement.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(r.title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Text(RuWords.dayMonth(r.day), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, softWrap = false)
        }
        r.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2) }
    }
}

// ── The period ─────────────────────────────────────────────────────────────────────────────────

@Composable
private fun PeriodBar(sel: PeriodSel, onPeriod: (BehindPeriod) -> Unit, onStep: (Int) -> Unit, canStep: (Int) -> Boolean) {
    val tokens = HotThemeTokens.colors
    val shape = RoundedCornerShape(20.dp)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.clip(shape).border(1.dp, tokens.rule, shape)) {
            BehindPeriod.entries.forEach { p ->
                val on = p == sel.period
                Box(
                    Modifier
                        .heightIn(min = 48.dp)
                        .background(if (on) Titanium.Dawn.copy(alpha = 0.14f) else Color.Transparent)
                        .semantics {
                            role = Role.Tab
                            selected = on
                        }
                        .clickable { onPeriod(p) }
                        .padding(horizontal = 14.dp)
                        .testTag(behindPeriodTag(p)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(p.label, style = MaterialTheme.typography.bodyMedium, color = if (on) dawnText() else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        if (sel.period != BehindPeriod.ALL) {
            StepButton("‹", BehindCopy.PERIOD_PREV, enabled = canStep(-1), tag = BEHIND_PREV_TAG) { onStep(-1) }
            Text(
                if (sel.period == BehindPeriod.YEAR) sel.label else RuWords.monthShort(sel.anchor.monthValue) + " " + sel.anchor.year,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                softWrap = false,
            )
            StepButton("›", BehindCopy.PERIOD_NEXT, enabled = canStep(1), tag = BEHIND_NEXT_TAG) { onStep(1) }
        }
    }
}

@Composable
private fun StepButton(glyph: String, label: String, enabled: Boolean, tag: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(40.dp, 48.dp).testTag(tag).semantics { contentDescription = label }) {
        Text(
            glyph,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.38f),
        )
    }
}

// ── The hero ───────────────────────────────────────────────────────────────────────────────────

/**
 * `PeriodHero` (A66 §6.2): the Titan from the back in the sunset light on the left, three facts on
 * the right, each a button to its records. With nothing to count yet — the starting point (§6.7).
 */
@Composable
private fun Hero(facts: List<FactCard>, onFact: (BehindFilter) -> Unit) {
    val tokens = HotThemeTokens.colors
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    if (tokens.isDark) listOf(Color(0xFF1A1714), Color(0xFF120F0D), Color(0xFF0E1013))
                    else listOf(Color(0xFFF6EBDD), Color(0xFFF1E6D8), Color(0xFFEDE8E1)),
                ),
            )
            .border(1.dp, tokens.hairline, shape)
            .testTag(BEHIND_HERO_TAG),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                Brush.radialGradient(listOf(Color(0xFFFF9650).copy(alpha = 0.30f), Color.Transparent), center = Offset(90.dp.toPx(), 120.dp.toPx()), radius = 180.dp.toPx()),
                radius = 180.dp.toPx(),
                center = Offset(90.dp.toPx(), 120.dp.toPx()),
            )
        }
        Image(
            painterResource(R.drawable.core_ui_titan_back),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.offset((-40).dp, 6.dp).size(240.dp),
        )
        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 16.dp).width(174.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (facts.isEmpty()) {
                Text(BehindCopy.EMPTY, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.testTag(BEHIND_EMPTY_TAG))
            }
            facts.forEach { f -> Fact(f, onClick = { onFact(f.filter) }) }
        }
    }
}

@Composable
private fun Fact(f: FactCard, onClick: () -> Unit) {
    val tokens = HotThemeTokens.colors
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
            .border(1.dp, tokens.hairline, shape)
            .clickable(onClickLabel = f.words, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(f.number.toString(), style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraLight, fontSize = 28.sp, lineHeight = 30.sp), color = MaterialTheme.colorScheme.onSurface, softWrap = false)
            Text(f.words, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(painterResource(R.drawable.core_ui_ic_chevron), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
    }
}

// ── The calendar ───────────────────────────────────────────────────────────────────────────────

/**
 * `YearCalendar` (A66 §6.2, §6.9): 12 rows × 31 cells by the light of the mode; dashed — no data;
 * hatched — a pause; a dot — a workout. Each month is one TalkBack node and a tap opens it.
 */
@Composable
private fun Calendar(data: BehindData, sel: PeriodSel, onMonth: (YearMonth) -> Unit) {
    Column(Modifier.fillMaxWidth().testTag(BEHIND_CALENDAR_TAG), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (sel.period) {
            BehindPeriod.YEAR -> YearCalendar.year(sel.anchor.year, data.today, data.days, data.since).forEach { MonthLine(it, onMonth) }
            BehindPeriod.MONTH -> MonthGrid(YearCalendar.month(YearMonth.from(sel.anchor), data.today, data.days, data.since))
            BehindPeriod.ALL -> YearCalendar.years(data.today, data.since).reversed().forEach { y ->
                Text(y.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                YearCalendar.year(y, data.today, data.days, data.since).forEach { MonthLine(it, onMonth) }
            }
        }
    }
}

@Composable
private fun MonthLine(row: MonthRow, onMonth: (YearMonth) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(15.dp)
            .clearAndSetSemantics { contentDescription = row.spoken }
            .clickable(onClickLabel = RuWords.month(row.month.monthValue)) { onMonth(row.month) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(row.label, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 13.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(32.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            row.cells.forEach { c -> DayCell(c, Modifier.weight(1f).height(13.dp), radius = 2.dp.value) }
        }
    }
}

/** The month view (A66 §6.2): cells by weeks, Monday first, each with its date. */
@Composable
private fun MonthGrid(row: MonthRow) {
    val first = row.month.atDay(1).dayOfWeek.value - 1
    val days = row.cells.filter { it.kind != CellKind.NONE }
    val slots: List<CalendarCell?> = List(first) { null } + days
    Text(row.spoken, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    slots.chunked(7).forEach { week ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (0 until 7).forEach { i ->
                val c = week.getOrNull(i)
                Box(Modifier.weight(1f).aspectRatio(1f)) {
                    if (c != null) {
                        DayCell(c, Modifier.fillMaxSize(), radius = 8.dp.value)
                        Text(
                            c.day?.dayOfMonth?.toString().orEmpty(),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun modeColor(m: DayMode): Color {
    val t = HotThemeTokens.colors
    return when (m) {
        DayMode.RAZGON -> t.modeRazgon
        DayMode.ROVNO -> t.modeRovno
        DayMode.BEREZHNO -> t.modeBerezhno
    }
}

@Composable
private fun DayCell(c: CalendarCell, modifier: Modifier, radius: Float) {
    val ink = MaterialTheme.colorScheme.onSurface
    val steel = MaterialTheme.colorScheme.onSurfaceVariant
    val dawn = dawnText()
    val fill = c.mode?.let { modeColor(it).copy(alpha = 0.7f) }
    Box(
        modifier.drawBehind {
            val r = CornerRadius(radius * density)
            when (c.kind) {
                CellKind.NONE -> Unit
                CellKind.QUIET -> drawRoundRect(ink.copy(alpha = 0.035f), cornerRadius = r)
                CellKind.MODE -> drawRoundRect(fill ?: ink.copy(alpha = 0.1f), cornerRadius = r)
                CellKind.NO_DATA -> drawRoundRect(
                    steel.copy(alpha = 0.7f), cornerRadius = r,
                    style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx()))),
                )
                CellKind.PAUSE -> clipRect {
                    val step = 3.5.dp.toPx()
                    var x = -size.height
                    while (x < size.width) {
                        drawLine(steel.copy(alpha = 0.5f), Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = 1.5.dp.toPx())
                        x += step
                    }
                }
            }
            if (c.training) drawCircle(ink.copy(alpha = 0.85f), radius = minOf(size.width, size.height) * 0.16f)
            if (c.today) drawRoundRect(dawn, cornerRadius = r, style = Stroke(1.5.dp.toPx()))
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend() {
    val t = HotThemeTokens.colors
    val steel = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().padding(start = 32.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(DayMode.RAZGON to t.modeRazgon, DayMode.ROVNO to t.modeRovno, DayMode.BEREZHNO to t.modeBerezhno).forEach { (m, c) ->
                LegendItem(m.word) { Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(c)) }
            }
            LegendItem(BehindCopy.LEGEND_NO_DATA) {
                Box(
                    Modifier.size(8.dp).drawBehind {
                        drawRoundRect(steel, cornerRadius = CornerRadius(2.dp.toPx()), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 1.5.dp.toPx()))))
                    },
                )
            }
            LegendItem(BehindCopy.LEGEND_PAUSE) {
                Box(Modifier.size(8.dp).border(1.dp, steel.copy(alpha = 0.6f), RoundedCornerShape(2.dp)))
            }
        }
        Text(BehindCopy.DATA_WINDOW, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = steel, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun LegendItem(text: String, mark: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        mark()
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ── The filters ────────────────────────────────────────────────────────────────────────────────

/**
 * The chips in one scrolling row (A66 §6.4). «Трудные периоды» is off by default and is not offered
 * at all in a hard state (§6.5 п. 1, 7): crisis, STORM, grief, illness, the return, Бережно.
 */
@Composable
private fun Filters(selected: BehindFilter?, hardAvailable: Boolean, onFilter: (BehindFilter) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BehindFilter.entries.filter { it != BehindFilter.HARD || hardAvailable }.forEach { f ->
            PathChip(f.label, selected == f, onClick = { onFilter(f) }, modifier = Modifier.testTag(behindFilterTag(f)))
        }
    }
}

@Composable
private fun FilterList(records: List<BehindRecord>) {
    Column(Modifier.fillMaxWidth()) {
        if (records.isEmpty()) {
            Text(BehindCopy.NOTHING_IN_FILTER, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        records.take(LIST_MAX).forEach { RecordRow(it) }
        if (records.size > LIST_MAX) {
            Text(BehindCopy.more(records.size - LIST_MAX), style = MaterialTheme.typography.bodySmall, color = dawnText())
        }
    }
}

private const val LIST_MAX = 30
