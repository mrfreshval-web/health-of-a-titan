package com.healthofatitan.app.feature.today

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.healthofatitan.app.core.ui.component.HotCard
import com.healthofatitan.app.core.ui.component.HotMode
import com.healthofatitan.app.core.ui.component.HotOverline
import com.healthofatitan.app.core.ui.component.HotPendingCard
import com.healthofatitan.app.core.ui.component.HotTextButton
import com.healthofatitan.app.core.ui.component.ModeWord
import com.healthofatitan.app.core.ui.component.TitanLights
import com.healthofatitan.app.core.ui.component.TitanScene
import com.healthofatitan.app.core.ui.component.rememberReduceMotion
import com.healthofatitan.app.core.ui.component.titanRingPoint
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.core.ui.theme.HotType
import com.healthofatitan.app.core.ui.theme.Titanium
import com.healthofatitan.app.domain.engine.Body
import com.healthofatitan.app.domain.engine.DayTexts
import com.healthofatitan.app.domain.engine.ItemKind
import com.healthofatitan.app.domain.engine.ItemStatus
import com.healthofatitan.app.integration.whoop.day.DayItemView
import com.healthofatitan.app.integration.whoop.day.DayView
import java.time.LocalDate
import kotlin.math.roundToInt

const val DAY_REGIME_TAG = "today_day_regime"
const val DAY_PLAN_TAG = "today_day_plan"
const val DAY_PENDING_TAG = "today_day_pending"
const val DAY_EVENING_TAG = "today_day_evening"
const val DAY_NOW_TAG = "today_day_now"
const val DAY_LINE_TAG = "today_day_line"
const val TODAY_HERO_TAG = "today_hero"

/** What the day block does on a tap (slice 2 logic, unchanged). */
class DayActions(
    val onToggle: (LocalDate, String) -> Unit = { _, _ -> },
    val onEvening: (LocalDate, Int?, Body?) -> Unit = { _, _, _ -> },
)

/**
 * The hero of «Сегодня» (A37 §1 scenario 2, s2): the Titan inside the Заряд ring, lit by the mode,
 * with the Заряд pill at the end of the arc, the mode word under it and three data tokens.
 * Before the norm is learned the ring is the calibration «9/14», dashed, in the theme light.
 *
 * @param charge Заряд 5–95 (50 = your norm); null while calibrating
 * @param normNights nights in the norm while calibrating
 */
@Composable
fun TodayHero(
    mode: HotMode?,
    charge: Int?,
    normNights: Int?,
    tokens: List<String>,
    modifier: Modifier = Modifier,
) {
    val reduce = rememberReduceMotion()
    val fraction = when {
        charge != null -> charge / 100f
        normNights != null -> normNights / TodayTexts.NORM_NIGHTS.toFloat()
        else -> 0f
    }.coerceIn(0f, 1f)
    val ring = remember { Animatable(if (reduce) fraction else 0f) }
    LaunchedEffect(fraction) {
        if (reduce) ring.snapTo(fraction) else ring.animateTo(fraction, spring(dampingRatio = 0.9f, stiffness = 220f))
    }
    val color = mode?.color() ?: MaterialTheme.colorScheme.primary
    val light = mode?.let { TitanLights.of(it) } ?: TitanLights.default()
    val glow = HotThemeTokens.colors.glow
    Column(
        modifier.fillMaxWidth().testTag(TODAY_HERO_TAG),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth, 380.dp)
            var box by remember { mutableStateOf(IntSize.Zero) }
            val density = LocalDensity.current
            Box(
                Modifier
                    .size(side)
                    .onSizeChanged { box = it }
                    .drawBehind {
                        // aura of the mode: radial light 16 % behind the hero (A37 §9.5)
                        if (glow) {
                            drawCircle(
                                Brush.radialGradient(listOf(color.copy(alpha = 0.16f), color.copy(alpha = 0.04f), Color.Transparent)),
                                radius = size.minDimension * 0.62f,
                            )
                        }
                    }
                    .semantics { contentDescription = TitanTexts.heroSpoken(mode, charge) },
            ) {
                TitanScene(
                    modifier = Modifier.fillMaxSize(),
                    light = light,
                    ring = { ring.value },
                    ringColor = color,
                    ringTrack = HotThemeTokens.colors.hairline,
                    ringDashed = mode == HotMode.BEREZHNO || charge == null,
                    ringStroke = 10f,
                    ringFit = 0.9f,
                )
                val pillText = charge?.toString() ?: normNights?.let { "$it/${TodayTexts.NORM_NIGHTS}" }
                if (pillText != null && box != IntSize.Zero) {
                    val p = titanRingPoint(
                        androidx.compose.ui.geometry.Size(box.width.toFloat(), box.height.toFloat()),
                        ring.value.coerceAtLeast(0.001f),
                        0.9f,
                    )
                    val pillW = with(density) { 64.dp.toPx() }
                    val pillH = with(density) { 36.dp.toPx() }
                    Box(
                        Modifier
                            .offset { IntOffset((p.x - pillW / 2).roundToInt(), (p.y - pillH / 2).roundToInt()) }
                            .size(64.dp, 36.dp)
                            .background(color, CircleShape)
                            .clearAndSetSemantics { },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(pillText, style = HotType.ringValue.copy(fontSize = HotType.ringValue.fontSize * 1.25f), color = Titanium.Depth)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (mode != null) {
            ModeWord(mode, style = HotType.headline, color = MaterialTheme.colorScheme.onSurface, glyphSize = 44.dp, modifier = Modifier.testTag(DAY_REGIME_TAG))
        }
        if (tokens.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                tokens.forEach { DataToken(it) }
            }
        }
    }
}

/** A data token «сон 5:50 ↓»: glass capsule, tabular numbers (A37 §6 Сегодня). */
@Composable
fun DataToken(text: String, modifier: Modifier = Modifier) {
    val tokens = HotThemeTokens.colors
    Text(
        text,
        style = HotType.ringValue,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        modifier = modifier
            .border(1.dp, tokens.hairline, CircleShape)
            .background(Brush.verticalGradient(listOf(tokens.glassTop, tokens.glassBottom)), CircleShape)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

/** «Почему так» under the hero: the summary, the rules that fired and the day's flags (A09, A35). */
@Composable
fun RegimeWhy(plan: DayView) {
    var why by rememberSaveable(plan.dayKey) { mutableStateOf(false) }
    val tokens = HotThemeTokens.colors
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        HotTextButton(if (why) DayUiTexts.WHY_HIDE else DayUiTexts.WHY, onClick = { why = !why })
        AnimatedVisibility(why) {
            HotCard(modifier = Modifier.fillMaxWidth()) {
                Text(plan.summary, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                if (plan.flags.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    HotOverline(plan.flags.joinToString(" · ") { DayUiTexts.flag(it) })
                }
                if (plan.fired.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        plan.fired.forEach { rule ->
                            Text(rule.why, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    DayUiTexts.markers(plan.caffeineCutoffMin, plan.windDownMin, plan.bedtimeMin),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textTertiary,
                )
            }
        }
    }
}

/** The item of the moment: in its slot now, else the next open one, else the first open one. */
fun nowItem(items: List<DayItemView>, nowMin: Int): DayItemView? {
    val open = items.filter { it.status == ItemStatus.OPEN }
    return open.firstOrNull { s -> s.startMin != null && s.endMin != null && nowMin in s.startMin!!..s.endMin!! }
        ?: open.filter { it.startMin != null && it.startMin!! >= nowMin }.minByOrNull { it.startMin!! }
        ?: open.firstOrNull()
}

/**
 * «Сейчас» (s2): the one next action as a glass card — time in the dawn accent, the title in
 * 21 sp and the dawn round button that marks it done (WHOOP confirms a workout by itself).
 */
@Composable
fun NowCard(plan: DayView, item: DayItemView, onToggle: (LocalDate, String) -> Unit, mode: HotMode) {
    HotCard(cornice = true, modifier = Modifier.fillMaxWidth().testTag(DAY_NOW_TAG), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            KindBadge(item.kind, mode.color(), sizeDp = 64)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                val slot = DayUiTexts.slot(item.startMin, item.endMin)
                HotOverline(if (slot.isEmpty()) TitanTexts.NOW else slot, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Text(item.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                item.detail?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = HotThemeTokens.colors.textTertiary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(12.dp))
            DawnRoundButton(onClick = { onToggle(plan.dayKey, item.id) }, description = TitanTexts.MARK_DONE)
        }
    }
}

/** The dawn round action (s2 «▶»): 64 dp, the dawn fill with its glow. Marks the item done. */
@Composable
private fun DawnRoundButton(onClick: () -> Unit, description: String) {
    val tokens = HotThemeTokens.colors
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.Transparent,
        modifier = Modifier
            .size(64.dp)
            .drawBehind {
                if (tokens.glow) {
                    drawCircle(Brush.radialGradient(listOf(tokens.dawnFill[1].copy(alpha = 0.35f), Color.Transparent)), radius = size.minDimension * 0.75f)
                }
            }
            .semantics { contentDescription = description; role = Role.Button },
    ) {
        Box(
            Modifier.background(Brush.verticalGradient(tokens.dawnFill), CircleShape).drawBehind {
                // check mark
                val w = size.width
                val s = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
                val p = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.33f, w * 0.52f)
                    lineTo(w * 0.45f, w * 0.64f)
                    lineTo(w * 0.68f, w * 0.38f)
                }
                drawPath(p, Titanium.OnDawn, style = s)
            },
        )
    }
}

/**
 * A plan item's kind as a round badge (until the A37 §6 sphere pictograms arrive in the next slice):
 * a thin ring with a reduced sign — two columns for training, a crescent for sleep, a sun for
 * light, a block for work, a plate for food.
 */
@Composable
fun KindBadge(kind: ItemKind?, color: Color, sizeDp: Int = 44, filled: Boolean = false, dashed: Boolean = false) {
    val tokens = HotThemeTokens.colors
    val ink = if (filled) Titanium.Depth else color
    Spacer(
        Modifier
            .size(sizeDp.dp)
            .clearAndSetSemantics { }
            .drawBehind {
                val r = this.size.minDimension / 2f
                val c = center
                if (filled) {
                    drawCircle(color, r)
                } else {
                    drawCircle(color.copy(alpha = 0.10f), r)
                    drawCircle(
                        if (dashed) tokens.rule else color.copy(alpha = 0.55f), r - 0.5.dp.toPx(),
                        style = Stroke(1.dp.toPx(), pathEffect = if (dashed) androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())) else null),
                    )
                }
                val w = 1.6.dp.toPx() * (sizeDp / 44f).coerceAtLeast(1f)
                val k = r * 0.42f
                when (kind) {
                    ItemKind.TRAINING -> {
                        drawLine(ink, Offset(c.x - k, c.y - k), Offset(c.x - k, c.y + k), w, StrokeCap.Round)
                        drawLine(ink, Offset(c.x + k, c.y - k), Offset(c.x + k, c.y + k), w, StrokeCap.Round)
                        drawLine(ink, Offset(c.x - k, c.y - k * 0.4f), Offset(c.x + k, c.y - k * 0.4f), w, StrokeCap.Round)
                        drawCircle(ink, k * 0.22f, Offset(c.x, c.y - k * 0.8f))
                        drawLine(ink, Offset(c.x, c.y - k * 0.4f), Offset(c.x, c.y + k * 0.5f), w, StrokeCap.Round)
                    }
                    ItemKind.SLEEP, ItemKind.NAP -> {
                        drawArc(ink, 60f, 240f, false, Offset(c.x - k, c.y - k), androidx.compose.ui.geometry.Size(2 * k, 2 * k), style = Stroke(w, cap = StrokeCap.Round))
                        drawArc(ink, 90f, 180f, false, Offset(c.x - k * 0.35f, c.y - k * 0.75f), androidx.compose.ui.geometry.Size(1.5f * k, 1.5f * k), style = Stroke(w, cap = StrokeCap.Round))
                    }
                    ItemKind.LIGHT -> {
                        drawCircle(ink, k * 0.5f, c, style = Stroke(w))
                        for (i in 0 until 8) {
                            val a = Math.toRadians(i * 45.0)
                            val from = Offset(c.x + k * 0.78f * kotlin.math.cos(a).toFloat(), c.y + k * 0.78f * kotlin.math.sin(a).toFloat())
                            val to = Offset(c.x + k * 1.05f * kotlin.math.cos(a).toFloat(), c.y + k * 1.05f * kotlin.math.sin(a).toFloat())
                            drawLine(ink, from, to, w, StrokeCap.Round)
                        }
                    }
                    ItemKind.WORK -> {
                        drawRect(ink, Offset(c.x - k * 0.8f, c.y - k * 0.55f), androidx.compose.ui.geometry.Size(1.6f * k, 1.1f * k), style = Stroke(w))
                        drawLine(ink, Offset(c.x - k * 0.3f, c.y + k * 0.8f), Offset(c.x + k * 0.3f, c.y + k * 0.8f), w, StrokeCap.Round)
                    }
                    ItemKind.NUTRITION -> {
                        drawCircle(ink, k * 0.85f, c, style = Stroke(w))
                        drawCircle(ink, k * 0.45f, c, style = Stroke(w))
                    }
                    null -> drawCircle(ink, k * 0.3f, c)
                }
            },
    )
}

/**
 * «Линия дня» (A37 §10 фишка 10, s2): the plan as nodes on one line — what was (filled in the
 * mode light), now (the dawn node with its glow), what comes (hairline) — and bedtime at the end.
 */
@Composable
fun DayLine(plan: DayView, nowMin: Int, mode: HotMode, modifier: Modifier = Modifier) {
    val items = plan.items.filter { it.startMin != null }.sortedBy { it.startMin }
    val current = nowItem(plan.items, nowMin)
    val tokens = HotThemeTokens.colors
    val modeColor = mode.color()
    val dawn = MaterialTheme.colorScheme.primary
    val nodes = items.map { it to it.startMin!! } + (null to plan.bedtimeMin)
    val doneUntil = nodes.indexOfLast { (item, start) -> item?.status != ItemStatus.OPEN && item != null || start <= nowMin }
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
                val done = item != null && item.status != ItemStatus.OPEN
                val color = when {
                    item == null -> tokens.modeBerezhno
                    isNow -> dawn
                    done -> modeColor
                    else -> tokens.textTertiary
                }
                Column(
                    Modifier
                        .width(78.dp)
                        .semantics(mergeDescendants = true) {
                            contentDescription = (item?.title ?: TitanTexts.BEDTIME) + ", " + DayTexts.time(start)
                            stateDescription = when {
                                isNow -> TitanTexts.NOW
                                done -> TitanTexts.MARKED
                                else -> ""
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        DayTexts.time(start),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isNow) dawn else tokens.textTertiary,
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
                            dashed = !isNow && !done && item != null && i > doneUntil,
                        )
                    }
                }
            }
        }
    }
}

/** The full plan (slice 2) in glass: time, title, detail, the source of a mark (A35), tap to mark. */
@Composable
fun PlanCard(plan: DayView, onToggle: (LocalDate, String) -> Unit) {
    HotCard(modifier = Modifier.fillMaxWidth().testTag(DAY_PLAN_TAG)) {
        HotOverline(DayUiTexts.PLAN)
        Spacer(Modifier.height(4.dp))
        plan.items.forEachIndexed { i, item ->
            if (i > 0) HorizontalDivider(color = HotThemeTokens.colors.hairline)
            PlanRow(item, onClick = { onToggle(plan.dayKey, item.id) })
        }
    }
}

@Composable
private fun PlanRow(item: DayItemView, onClick: () -> Unit) {
    val done = item.status != ItemStatus.OPEN
    val fromWhoop = item.status == ItemStatus.DONE_WHOOP || item.status == ItemStatus.MODIFIED
    val tokens = HotThemeTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = !fromWhoop, role = Role.Checkbox, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            DayUiTexts.slot(item.startMin, item.endMin),
            style = MaterialTheme.typography.labelSmall,
            color = if (done) tokens.textTertiary else MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(92.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            item.detail?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = tokens.textTertiary)
            }
            DayUiTexts.status(item.status)?.let {
                Spacer(Modifier.height(2.dp))
                HotOverline(it, color = tokens.modeRovno)
            }
        }
        Spacer(Modifier.width(10.dp))
        Spacer(
            Modifier
                .size(22.dp)
                .drawBehind {
                    val r = size.minDimension / 2f
                    if (done) {
                        drawCircle(tokens.modeRovno.copy(alpha = 0.9f), r)
                        val p = androidx.compose.ui.graphics.Path().apply {
                            moveTo(r * 0.55f, r * 1.02f)
                            lineTo(r * 0.88f, r * 1.32f)
                            lineTo(r * 1.45f, r * 0.72f)
                        }
                        drawPath(p, Titanium.Depth, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round))
                    } else {
                        drawCircle(tokens.rule, r - 0.5.dp.toPx(), style = Stroke(1.dp.toPx()))
                    }
                },
        )
    }
}

/** No plan for today yet: an honest dashed card (D-027: the plan is made from data, not a guess). */
@Composable
fun DayPending() {
    HotPendingCard(
        title = DayUiTexts.PENDING_TITLE,
        caption = DayUiTexts.PENDING_CAPTION,
        modifier = Modifier.fillMaxWidth().testTag(DAY_PENDING_TAG),
    )
}

/** Asks for POST_NOTIFICATIONS once today's plan is ready (the morning push needs it). */
@Composable
fun NotificationAsk() {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    }
    var dismissed by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        dismissed = true
    }
    if (granted || dismissed) return
    HotCard(modifier = Modifier.fillMaxWidth()) {
        Text(DayUiTexts.NOTIFY_HINT, style = MaterialTheme.typography.bodyMedium)
        Row {
            HotTextButton(DayUiTexts.NOTIFY_ALLOW, onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) })
            HotTextButton(DayUiTexts.SKIP, onClick = { dismissed = true })
        }
    }
}

/** The evening deck (D-027): only what WHOOP does not know — alcohol and how the body feels. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EveningDeckCard(day: LocalDate, onEvening: (LocalDate, Int?, Body?) -> Unit) {
    var alcohol by rememberSaveable(day) { mutableStateOf<Int?>(null) }
    var alcoholDone by rememberSaveable(day) { mutableStateOf(false) }
    val tokens = HotThemeTokens.colors
    val chipColors = FilterChipDefaults.filterChipColors(
        containerColor = Color.Transparent,
        labelColor = MaterialTheme.colorScheme.onSurface,
        selectedContainerColor = MaterialTheme.colorScheme.primary,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
    )
    HotCard(modifier = Modifier.fillMaxWidth().testTag(DAY_EVENING_TAG)) {
        HotOverline(DayUiTexts.EVENING)
        Spacer(Modifier.height(8.dp))
        if (!alcoholDone) {
            Text(DayUiTexts.ALCOHOL_Q, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DayUiTexts.ALCOHOL_OPTIONS.forEach { (value, label) ->
                    FilterChip(
                        selected = alcohol == value,
                        onClick = {
                            alcohol = value
                            alcoholDone = true
                        },
                        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
                        shape = CircleShape,
                        colors = chipColors,
                        border = FilterChipDefaults.filterChipBorder(true, alcohol == value, borderColor = tokens.rule),
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
            HotTextButton(DayUiTexts.SKIP, onClick = { alcoholDone = true })
        } else {
            Text(DayUiTexts.BODY_Q, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DayUiTexts.BODY_OPTIONS.forEach { (value, label) ->
                    FilterChip(
                        selected = false,
                        onClick = { onEvening(day, alcohol, value) },
                        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
                        shape = CircleShape,
                        colors = chipColors,
                        border = FilterChipDefaults.filterChipBorder(true, false, borderColor = tokens.rule),
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
            HotTextButton(DayUiTexts.SKIP, onClick = { onEvening(day, alcohol, null) })
        }
    }
}

/** «Принято. Учту утром.» after the deck. */
@Composable
fun EveningSaved() {
    HotPendingCard(title = DayUiTexts.DONE, modifier = Modifier.fillMaxWidth())
}
