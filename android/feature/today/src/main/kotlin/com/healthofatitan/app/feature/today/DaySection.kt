package com.healthofatitan.app.feature.today

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
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
import com.healthofatitan.app.domain.engine.CrisisAssessment
import com.healthofatitan.app.domain.engine.DayClose
import com.healthofatitan.app.domain.engine.DayFlag
import com.healthofatitan.app.domain.engine.DayTexts
import com.healthofatitan.app.domain.engine.FiredRule
import com.healthofatitan.app.domain.engine.ItemKind
import com.healthofatitan.app.domain.engine.ItemStatus
import com.healthofatitan.app.domain.engine.RuleSet
import com.healthofatitan.app.domain.today.DayTask
import com.healthofatitan.app.domain.today.Ritual
import com.healthofatitan.app.domain.today.TaskMove
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
const val DAY_SAFETY_TAG = "today_day_safety"
const val DAY_SUMMARY_TAG = "today_day_summary"
const val DAY_WHY_TAG = "today_day_why"

/** What the day block does on a tap (slice 2 logic) and in the evening (stream A). */
class DayActions(
    val onToggle: (LocalDate, String) -> Unit = { _, _ -> },
    val onEvening: (LocalDate, Int?, Body?) -> Unit = { _, _, _ -> },
    /** «Закрыть день»: the answers of the evening (A18 И4.1). */
    val onCloseDay: (LocalDate, DayClose) -> Unit = { _, _ -> },
    /** CrisisGuard level ≥ 1 in the evening note: logged without text; ≥ 2 opens the crisis card. */
    val onCrisis: (CrisisAssessment) -> Unit = {},
    /** The crisis card (SOS), one tap away at level 1. */
    val onOpenHelp: () -> Unit = {},
    /** Health Connect answered the permission request, or the steps should be re-read. */
    val onSteps: () -> Unit = {},
    /** Every tap that leaves «Сегодня» for a route: «Почему так», a support, a task, the week. */
    val onOpen: (String) -> Unit = {},
    /** «питьё» (D-047, was «вода»): one glass more (+1) or less (−1). */
    val onWater: (LocalDate, Int) -> Unit = { _, _ -> },
    /** A task of the day marked done or taken back (A46 §2.4). */
    val onTaskDone: (LocalDate, DayTask, Boolean) -> Unit = { _, _, _ -> },
    /** T-08: «Не сегодня» — back to its week goal. */
    val onTaskToWeek: (TaskMove) -> Unit = {},
    /** The undo of «Не сегодня». */
    val onTaskBack: (LocalDate, String) -> Unit = { _, _ -> },
    /** A tap on a ritual point of «Ритм дня». */
    val onRitual: (Ritual) -> Unit = {},
    val onConnectWhoop: () -> Unit = NO_CONNECT,
    /** «Начать с малого» (4.9): the return is seen. */
    val onStartSmall: () -> Unit = {},
    /** «+ Еда» in «День» (A66 §3.5): a meal slot marked eaten (or taken back). */
    val onMeal: (LocalDate, String, Boolean) -> Unit = { _, _, _ -> },
) {
    fun copyWith(onConnectWhoop: () -> Unit): DayActions = DayActions(
        onToggle, onEvening, onCloseDay, onCrisis, onOpenHelp, onSteps, onOpen,
        onWater, onTaskDone, onTaskToWeek, onTaskBack, onRitual, onConnectWhoop, onStartSmall, onMeal,
    )
}

/** No «Подключить WHOOP» given: the screen's own callback is used. */
val NO_CONNECT: () -> Unit = {}

/** Where a fired rule stands in «Почему так» (A39 §7): safety → data → regime → context. */
internal enum class WhyKind { SAFETY, DATA, REGIME, CONTEXT }

internal fun whyKind(id: String): WhyKind? = when {
    id.startsWith("SAF-") -> WhyKind.SAFETY
    id == "REG-05" -> WhyKind.DATA
    id.startsWith("REG-") -> WhyKind.REGIME
    // SLP, NUT, WRK repeat the plan: hidden here (A39 §7)
    id.startsWith("SLP-") || id.startsWith("NUT-") || id.startsWith("WRK-") -> null
    else -> WhyKind.CONTEXT
}

/** The lines of «Почему так»: at most 4, one per rule, in the A39 §7 order; SAF lines stay on the safety card. */
internal fun whyLines(fired: List<FiredRule>, max: Int = 4): List<FiredRule> =
    fired.filter { r -> whyKind(r.ref.id).let { it != null && it != WhyKind.SAFETY } }
        .distinctBy { it.ref.id }
        .sortedBy { whyKind(it.ref.id)!!.ordinal }
        .take(max)

/** The sphere of a «почему» line as a pictogram (sleep, body, food, work, travel, data). */
@Composable
private fun WhyGlyph(rule: FiredRule, color: Color) {
    val id = rule.ref.id
    when {
        whyKind(id) == WhyKind.DATA -> FlagGlyph(DayFlag.MANUAL, color, size = 24.dp)
        id == "CTX-03" || id == "CTX-04" -> FlagGlyph(DayFlag.TRAVEL, color, size = 24.dp)
        id == "REG-06" -> FlagGlyph(DayFlag.LIGHT_WEEK, color, size = 24.dp)
        id == "REG-02" -> KindBadge(ItemKind.SLEEP, color, sizeDp = 24)
        id == "CTX-01" || id.startsWith("EVE-") -> KindBadge(ItemKind.NUTRITION, color, sizeDp = 24)
        id == "CTX-02" -> KindBadge(ItemKind.WORK, color, sizeDp = 24)
        else -> KindBadge(ItemKind.TRAINING, color, sizeDp = 24)
    }
}

/**
 * Safety first (A39 §7.1): only when a SAF rule fired. Glass with no glow and no alarm colour
 * (red is SOS and SAF-01 only); one line of `why`, and for SAF-03@2 «Что делать» opens the rest.
 */
@Composable
fun SafetyCard(plan: DayView) {
    val saf = plan.fired.filter { whyKind(it.ref.id) == WhyKind.SAFETY }
    if (saf.isEmpty()) return
    var more by rememberSaveable(plan.dayKey) { mutableStateOf(false) }
    HotCard(modifier = Modifier.fillMaxWidth().testTag(DAY_SAFETY_TAG)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FlagGlyph(if (DayFlag.SICK in plan.flags) DayFlag.SICK else DayFlag.DATA_LIMITED, MaterialTheme.colorScheme.onSurface, size = 28.dp)
            Spacer(Modifier.width(12.dp))
            Text(saf.first().why, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        }
        val rest = saf.drop(1)
        if (rest.isNotEmpty() && saf.any { it.ref == RuleSet.SAF_03 }) {
            AnimatedVisibility(more) {
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    rest.forEach { Text(it.why, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            if (!more) HotTextButton(TitanTexts.SAFETY_MORE, onClick = { more = true })
        }
    }
}

/** «Почему такой день ⌄» under the hero (Y4): folded, unfolds in place; at most 4 lines with a sphere pictogram each (A39 §7.3). */
@Composable
fun RegimeWhy(plan: DayView) {
    var why by rememberSaveable(plan.dayKey) { mutableStateOf(false) }
    val lines = whyLines(plan.fired)
    if (lines.isEmpty()) return
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().testTag(DAY_WHY_TAG), horizontalAlignment = Alignment.CenterHorizontally) {
        HotTextButton(if (why) DayUiTexts.WHY_HIDE else DayUiTexts.WHY, onClick = { why = !why })
        AnimatedVisibility(why) {
            HotCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    lines.forEach { rule ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            WhyGlyph(rule, ink)
                            Spacer(Modifier.width(12.dp))
                            Text(rule.why, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }
}

/**
 * A plan item's kind as a round badge (until the A37 §6 sphere pictograms arrive in the next slice):
 * a thin ring with a reduced sign — two columns for training, a crescent for sleep, a sun for
 * light, a block for work, a plate with a fork and a knife for food (G1).
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
                        // G1: a plate between a fork and a knife (it read as a target before)
                        drawCircle(ink, k * 0.62f, c, style = Stroke(w))
                        drawCircle(ink, k * 0.36f, c, style = Stroke(w * 0.8f))
                        val fx = c.x - k * 1.05f
                        drawLine(ink, Offset(fx, c.y - k * 0.7f), Offset(fx, c.y + k * 0.7f), w, StrokeCap.Round)
                        drawLine(ink, Offset(fx - k * 0.16f, c.y - k * 0.7f), Offset(fx - k * 0.16f, c.y - k * 0.3f), w * 0.8f, StrokeCap.Round)
                        drawLine(ink, Offset(fx + k * 0.16f, c.y - k * 0.7f), Offset(fx + k * 0.16f, c.y - k * 0.3f), w * 0.8f, StrokeCap.Round)
                        val kx = c.x + k * 1.05f
                        drawLine(ink, Offset(kx, c.y - k * 0.7f), Offset(kx, c.y + k * 0.7f), w, StrokeCap.Round)
                        drawLine(ink, Offset(kx, c.y - k * 0.7f), Offset(kx + k * 0.14f, c.y - k * 0.1f), w * 0.8f, StrokeCap.Round)
                    }
                    null -> drawCircle(ink, k * 0.3f, c)
                }
            },
    )
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

/**
 * The evening deck (D-027, A39 §7.5): first «Как тело?» as three Titans-feelings, then «Алкоголь
 * сегодня» — the evening never opens with drinking (Agent 10). «Пропустить» is an equal button.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EveningDeckCard(day: LocalDate, onEvening: (LocalDate, Int?, Body?) -> Unit) {
    var body by rememberSaveable(day) { mutableStateOf<Body?>(null) }
    var bodyDone by rememberSaveable(day) { mutableStateOf(false) }
    HotCard(modifier = Modifier.fillMaxWidth().testTag(DAY_EVENING_TAG)) {
        HotOverline(DayUiTexts.EVENING)
        Spacer(Modifier.height(8.dp))
        if (!bodyDone) {
            Text(DayUiTexts.BODY_Q, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DayUiTexts.BODY_OPTIONS.forEach { (value, label) ->
                    EveningOption(
                        label = label,
                        onClick = {
                            body = value
                            bodyDone = true
                        },
                        modifier = Modifier.weight(1f),
                    ) { BodyGlyph(value, label, MaterialTheme.colorScheme.onSurface) }
                }
                EveningOption(label = DayUiTexts.SKIP, onClick = { bodyDone = true }, modifier = Modifier.weight(1f), muted = true) {
                    Spacer(Modifier.size(40.dp))
                }
            }
        } else {
            Text(DayUiTexts.ALCOHOL_Q, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DayUiTexts.ALCOHOL_OPTIONS.forEach { (value, label) ->
                    EveningChip(label, onClick = { onEvening(day, value, body) })
                }
                EveningChip(DayUiTexts.SKIP, onClick = { onEvening(day, null, body) }, muted = true)
            }
        }
    }
}

@Composable
private fun EveningOption(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
    glyph: @Composable () -> Unit,
) {
    val tokens = HotThemeTokens.colors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, tokens.rule),
        modifier = modifier.heightIn(min = 96.dp),
    ) {
        Column(
            Modifier.padding(vertical = 12.dp, horizontal = 4.dp).semantics(mergeDescendants = true) { },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.clearAndSetSemantics { }) { glyph() }
            Spacer(Modifier.height(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EveningChip(label: String, onClick: () -> Unit, muted: Boolean = false) {
    val tokens = HotThemeTokens.colors
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, tokens.rule),
        modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 56.dp),
    ) {
        Box(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** «Принято. Утро это учтёт.» after the deck. */
@Composable
fun EveningSaved() {
    HotPendingCard(title = DayUiTexts.DONE, modifier = Modifier.fillMaxWidth())
}
