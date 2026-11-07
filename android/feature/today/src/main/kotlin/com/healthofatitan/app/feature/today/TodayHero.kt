package com.healthofatitan.app.feature.today

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.healthofatitan.app.core.ui.component.HotCard
import com.healthofatitan.app.core.ui.component.HotLearningBar
import com.healthofatitan.app.core.ui.component.HotMode
import com.healthofatitan.app.core.ui.component.HotNumber
import com.healthofatitan.app.core.ui.component.ModeWord
import com.healthofatitan.app.core.ui.component.TitanLights
import com.healthofatitan.app.core.ui.component.TitanScene
import com.healthofatitan.app.core.ui.component.rememberReduceMotion
import com.healthofatitan.app.core.ui.component.titanRingPoint
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.core.ui.theme.HotType
import com.healthofatitan.app.core.ui.theme.Titanium
import com.healthofatitan.app.domain.engine.DayFlag
import com.healthofatitan.app.integration.whoop.SleepStages
import kotlin.math.roundToInt

const val HERO_CHIP_TAG = "today_hero_chip_"
const val HERO_FACT_TAG = "today_hero_fact"
const val CHARGE_SCALE_TAG = "today_charge_scale"

/** The three facts under the mode word (UX review 119, Y2): each a pictogram and one number. */
enum class FactKind { SLEEP, PULSE, LOAD }

/**
 * One fact of the night or the day as the hero shows it. [explain] is what a tap opens; facts
 * the app does not have are honest about it there («нет данных»), never a zero.
 */
class HeroFact(
    val kind: FactKind,
    val value: String,
    val spoken: String,
    val explain: @Composable () -> Unit,
)

/** Заряд in three zones by the engine's own level boundaries (|z| < 0.5 ≈ 43–57, A25 §4). */
enum class ChargeZone {
    LOW,
    NORM,
    HIGH,
    ;

    companion object {
        fun of(charge: Int): ChargeZone = when {
            charge < 43 -> LOW
            charge > 57 -> HIGH
            else -> NORM
        }
    }
}

/**
 * The colour scale of Заряд (Y3): the mode palette, not red / green — red belongs to SOS only
 * (A39 §7.1). Low reads as «Бережно», around the norm as «Ровно», above it as «Разгон».
 */
@Composable
fun ChargeZone.color(): Color = when (this) {
    ChargeZone.LOW -> HotThemeTokens.colors.modeBerezhno
    ChargeZone.NORM -> HotThemeTokens.colors.modeRovno
    ChargeZone.HIGH -> HotThemeTokens.colors.modeRazgon
}

/**
 * The hero of «Сегодня» (A37 §1 scenario 2, s2): the Titan inside the Заряд ring, lit by the mode,
 * the Заряд pill at the end of the arc, the mode word, three facts and the Заряд scale. While the
 * norm is learned the pill holds the nights alone (no «9/14», R1) and the learning bar stands
 * under the hero (R5).
 *
 * @param charge Заряд 5–95 (50 = your norm); null while calibrating
 * @param normNights nights in the norm while calibrating (0–28)
 */
@Composable
fun TodayHero(
    mode: HotMode?,
    charge: Int?,
    normNights: Int?,
    facts: List<HeroFact>,
    modifier: Modifier = Modifier,
    summary: String? = null,
    flags: Set<DayFlag> = emptySet(),
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
    val pillColor = charge?.let { ChargeZone.of(it).color() } ?: color
    val light = mode?.let { TitanLights.of(it) } ?: TitanLights.default()
    val glow = HotThemeTokens.colors.glow
    Column(
        modifier.fillMaxWidth().testTag(TODAY_HERO_TAG),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth, 320.dp)
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
                // R1: one number in the pill, never a fraction.
                val pillText = charge?.toString() ?: normNights?.toString()
                if (pillText != null && box != IntSize.Zero) {
                    val p = titanRingPoint(Size(box.width.toFloat(), box.height.toFloat()), ring.value.coerceAtLeast(0.001f), 0.9f)
                    val pillW = with(density) { 64.dp.toPx() }
                    val pillH = with(density) { 36.dp.toPx() }
                    Box(
                        Modifier
                            .offset { IntOffset((p.x - pillW / 2).roundToInt(), (p.y - pillH / 2).roundToInt()) }
                            .size(64.dp, 36.dp)
                            .background(pillColor, CircleShape)
                            .clearAndSetSemantics { },
                        contentAlignment = Alignment.Center,
                    ) {
                        HotNumber(pillText, style = HotType.ringValue.copy(fontSize = HotType.ringValue.fontSize * 1.25f), color = Titanium.Depth)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (mode != null) {
            ModeWord(mode, style = HotType.headline, color = MaterialTheme.colorScheme.onSurface, glyphSize = 44.dp, modifier = Modifier.testTag(DAY_REGIME_TAG))
        }
        if (!summary.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                summary,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag(DAY_SUMMARY_TAG),
            )
        }
        if (flags.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                flags.sortedBy { it.ordinal }.forEach { FlagGlyph(it, MaterialTheme.colorScheme.onSurfaceVariant, size = 28.dp) }
            }
        }
        if (facts.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            HeroFacts(facts)
        }
        Spacer(Modifier.height(14.dp))
        when {
            charge != null -> ChargeScale(charge)
            normNights != null -> HotLearningBar(normNights)
        }
    }
}

/** The three facts as chips (Y2): pictogram + number; a tap opens one explanation under the row. */
@Composable
private fun HeroFacts(facts: List<HeroFact>) {
    var open by remember { mutableStateOf<FactKind?>(null) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            facts.forEach { f ->
                FactChip(f, selected = open == f.kind, onClick = { open = if (open == f.kind) null else f.kind })
            }
        }
        val current = facts.firstOrNull { it.kind == open }
        AnimatedVisibility(current != null) {
            if (current != null) {
                HotCard(modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag(HERO_FACT_TAG)) { current.explain() }
            }
        }
    }
}

@Composable
private fun FactChip(fact: HeroFact, selected: Boolean, onClick: () -> Unit) {
    val tokens = HotThemeTokens.colors
    val ink = MaterialTheme.colorScheme.onSurface
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else tokens.hairline, CircleShape)
            .background(Brush.verticalGradient(listOf(tokens.glassTop, tokens.glassBottom)), CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = fact.spoken
                stateDescription = if (selected) "пояснение открыто" else "нажми, чтобы узнать"
                role = Role.Button
            }
            .padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
            .testTag(HERO_CHIP_TAG + fact.kind.name.lowercase()),
    ) {
        FactGlyph(fact.kind, ink, size = 20.dp)
        Spacer(Modifier.width(8.dp))
        HotNumber(fact.value, style = HotType.ringValue, color = ink)
    }
}

/** Pictograms of the facts: a crescent (sleep), a heart (resting pulse), a bolt (load). */
@Composable
fun FactGlyph(kind: FactKind, color: Color, modifier: Modifier = Modifier, size: Dp = 20.dp) {
    Spacer(
        modifier
            .size(size)
            .clearAndSetSemantics { }
            .drawBehind {
                val w = this.size.width
                val s = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val p = Path()
                when (kind) {
                    FactKind.SLEEP -> {
                        p.moveTo(w * 0.60f, w * 0.08f)
                        p.cubicTo(w * 0.12f, w * 0.14f, w * 0.10f, w * 0.90f, w * 0.60f, w * 0.92f)
                        p.cubicTo(w * 0.76f, w * 0.93f, w * 0.88f, w * 0.86f, w * 0.94f, w * 0.76f)
                        p.cubicTo(w * 0.54f, w * 0.78f, w * 0.36f, w * 0.32f, w * 0.60f, w * 0.08f)
                    }
                    FactKind.PULSE -> {
                        p.moveTo(w * 0.5f, w * 0.88f)
                        p.cubicTo(w * 0.05f, w * 0.58f, w * 0.06f, w * 0.18f, w * 0.30f, w * 0.16f)
                        p.cubicTo(w * 0.42f, w * 0.15f, w * 0.48f, w * 0.24f, w * 0.5f, w * 0.30f)
                        p.cubicTo(w * 0.52f, w * 0.24f, w * 0.58f, w * 0.15f, w * 0.70f, w * 0.16f)
                        p.cubicTo(w * 0.94f, w * 0.18f, w * 0.95f, w * 0.58f, w * 0.5f, w * 0.88f)
                    }
                    FactKind.LOAD -> {
                        p.moveTo(w * 0.58f, w * 0.04f)
                        p.lineTo(w * 0.24f, w * 0.56f)
                        p.lineTo(w * 0.50f, w * 0.56f)
                        p.lineTo(w * 0.42f, w * 0.96f)
                        p.lineTo(w * 0.78f, w * 0.42f)
                        p.lineTo(w * 0.52f, w * 0.42f)
                    }
                }
                p.close()
                drawPath(p, color, style = s)
            },
    )
}

/** Sleep (Y2): the hours against the need and the composition of the night, or an honest «нет данных». */
@Composable
fun SleepFact(hours: Double, needHours: Double?, stages: SleepStages?) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    FactTitle(FactKind.SLEEP, HeroTexts.SLEEP_TITLE)
    Text(HeroTexts.sleepLine(hours, needHours), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    Spacer(Modifier.height(10.dp))
    if (stages == null || stages.lightMin + stages.deepMin + stages.remMin == 0) {
        Text(HeroTexts.STAGES_NONE, style = MaterialTheme.typography.bodySmall, color = muted)
        return
    }
    val total = (stages.lightMin + stages.deepMin + stages.remMin + stages.awakeMin).coerceAtLeast(1)
    val tokens = HotThemeTokens.colors
    val rows = listOf(
        Triple(HeroTexts.STAGE_DEEP, stages.deepMin, tokens.modeBerezhno),
        Triple(HeroTexts.STAGE_REM, stages.remMin, tokens.modeRovno),
        Triple(HeroTexts.STAGE_LIGHT, stages.lightMin, MaterialTheme.colorScheme.onSurfaceVariant),
        Triple(HeroTexts.STAGE_AWAKE, stages.awakeMin, tokens.textTertiary),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { (label, min, c) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) { }) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(112.dp), maxLines = 1)
                Spacer(
                    Modifier
                        .weight(1f)
                        .height(8.dp)
                        .drawBehind {
                            val h = size.height
                            drawLine(tokens.ruleSoft, Offset(h / 2, h / 2), Offset(size.width - h / 2, h / 2), h, StrokeCap.Round)
                            val f = min / total.toFloat()
                            if (f > 0f) drawLine(c, Offset(h / 2, h / 2), Offset(h / 2 + (size.width - h) * f, h / 2), h, StrokeCap.Round)
                        },
                )
                Spacer(Modifier.width(10.dp))
                HotNumber(HeroTexts.hm(min), style = HotType.ringValue, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(64.dp), textAlign = TextAlign.End)
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(HeroTexts.STAGES_SOURCE, style = MaterialTheme.typography.bodySmall, color = muted)
}

/** Resting pulse (Y2): what kind of pulse this is. */
@Composable
fun PulseFact(bpm: Int) {
    FactTitle(FactKind.PULSE, HeroTexts.PULSE_TITLE)
    Text(HeroTexts.pulseLine(bpm), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    Spacer(Modifier.height(6.dp))
    Text(HeroTexts.PULSE_WHAT, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** The load ceiling (Y2): what it is and in which units — strain WHOOP, 0–21. */
@Composable
fun LoadFact(ceiling: Double, strain: Double?, mode: HotMode) {
    FactTitle(FactKind.LOAD, HeroTexts.LOAD_TITLE)
    Text(HeroTexts.loadLine(ceiling, strain), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    Spacer(Modifier.height(10.dp))
    StrainScale(ceiling, strain, mode.color())
    Spacer(Modifier.height(8.dp))
    Text(HeroTexts.loadWhat(mode), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun FactTitle(kind: FactKind, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
        FactGlyph(kind, MaterialTheme.colorScheme.primary, size = 22.dp)
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** 0 — 21 with the ceiling as a tick and today's strain as the fill. */
@Composable
private fun StrainScale(ceiling: Double, strain: Double?, color: Color) {
    val tokens = HotThemeTokens.colors
    val tick = MaterialTheme.colorScheme.onSurface
    Column {
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(12.dp)
                .drawBehind {
                    val h = 4.dp.toPx()
                    val y = size.height / 2
                    drawLine(tokens.ruleSoft, Offset(h / 2, y), Offset(size.width - h / 2, y), h, StrokeCap.Round)
                    val s = ((strain ?: 0.0) / HeroTexts.STRAIN_MAX).toFloat().coerceIn(0f, 1f)
                    if (s > 0f) drawLine(color, Offset(h / 2, y), Offset(h / 2 + (size.width - h) * s, y), h, StrokeCap.Round)
                    val x = h / 2 + (size.width - h) * (ceiling / HeroTexts.STRAIN_MAX).toFloat()
                    drawLine(tick, Offset(x, 0f), Offset(x, size.height), 1.5.dp.toPx())
                },
        )
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            HotNumber("0", style = HotType.mono, color = tokens.textTertiary)
            HotNumber("${HeroTexts.STRAIN_MAX.roundToInt()}", style = HotType.mono, color = tokens.textTertiary)
        }
    }
}

/**
 * Заряд as a scale (Y3): the number in its zone colour, the three zones, the norm (50) as a tick
 * with «твоя норма — 50»; a tap says what Заряд is and where «what changed it» lives.
 */
@Composable
fun ChargeScale(charge: Int, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val tokens = HotThemeTokens.colors
    val zone = ChargeZone.of(charge)
    val zoneColor = zone.color()
    val low = ChargeZone.LOW.color()
    val norm = ChargeZone.NORM.color()
    val high = ChargeZone.HIGH.color()
    val tick = MaterialTheme.colorScheme.onSurface
    Column(
        modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = { open = !open })
            .semantics(mergeDescendants = true) {
                contentDescription = HeroTexts.chargeSpoken(charge)
                stateDescription = if (open) "пояснение открыто" else "нажми, чтобы узнать"
            }
            .padding(vertical = 6.dp)
            .testTag(CHARGE_SCALE_TAG),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clearAndSetSemantics { }) {
            Text(HeroTexts.CHARGE, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(8.dp))
            HotNumber("$charge", style = MaterialTheme.typography.titleMedium, color = zoneColor)
            Spacer(Modifier.width(8.dp))
            Text(HeroTexts.zone(zone), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (open) "⌃" else "⌄", style = MaterialTheme.typography.titleMedium, color = tokens.textTertiary)
        }
        Spacer(Modifier.height(10.dp))
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clearAndSetSemantics { }
                .drawBehind {
                    val h = 4.dp.toPx()
                    val y = size.height / 2
                    fun x(v: Float) = h / 2 + (size.width - h) * ((v - 5f) / 90f).coerceIn(0f, 1f)
                    drawLine(low.copy(alpha = 0.55f), Offset(x(5f), y), Offset(x(43f), y), h, StrokeCap.Round)
                    drawLine(norm.copy(alpha = 0.55f), Offset(x(43f), y), Offset(x(57f), y), h)
                    drawLine(high.copy(alpha = 0.55f), Offset(x(57f), y), Offset(x(95f), y), h, StrokeCap.Round)
                    drawLine(tick.copy(alpha = 0.6f), Offset(x(50f), 0f), Offset(x(50f), size.height), 1.dp.toPx())
                    drawCircle(zoneColor, 6.dp.toPx(), Offset(x(charge.toFloat()), y))
                    drawCircle(Color.Black.copy(alpha = 0.35f), 6.dp.toPx(), Offset(x(charge.toFloat()), y), style = Stroke(1.dp.toPx()))
                },
        )
        Row(Modifier.fillMaxWidth().padding(top = 4.dp).clearAndSetSemantics { }, horizontalArrangement = Arrangement.Center) {
            HotNumber(HeroTexts.NORM_TICK, style = HotType.mono, color = tokens.textTertiary)
        }
        AnimatedVisibility(open) {
            Text(
                HeroTexts.CHARGE_WHAT,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
