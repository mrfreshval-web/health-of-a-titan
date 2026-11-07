package com.healthofatitan.app.feature.nutrition

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.healthofatitan.app.core.ui.component.HotCard
import com.healthofatitan.app.core.ui.component.HotOverline
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.domain.nutrition.DayCondition
import com.healthofatitan.app.domain.nutrition.EveningMark
import com.healthofatitan.app.domain.nutrition.NutritionDayPlan
import com.healthofatitan.app.domain.nutrition.PlannedSlot

const val PLATE_CARD_TAG = "nutrition_plate_card"

/** What the card can do; the app wires the routes (features never depend on each other). */
data class PlateActions(
    val onOpenMeal: (String) -> Unit = {},
    val onOpenProfile: () -> Unit = {},
    val onOpenLevel: () -> Unit = {},
    val onChip: (DayCondition) -> Unit = {},
    val onEvening: (EveningMark) -> Unit = {},
)

/**
 * «Тарелка дня» for «Сегодня» (A41 §10, mockup n3): one card from this module, so «Сегодня»
 * itself is not rebuilt. Stateful: reads [NutritionViewModel].
 */
@Composable
fun PlateOfDay(onOpenMeal: (String) -> Unit, onOpenProfile: () -> Unit, onOpenLevel: () -> Unit, modifier: Modifier = Modifier) {
    val vm: NutritionViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    PlateOfDayCard(
        state = state,
        actions = PlateActions(onOpenMeal, onOpenProfile, onOpenLevel, vm::toggleChip, vm::setEvening),
        modifier = modifier,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlateOfDayCard(state: NutritionState, actions: PlateActions, modifier: Modifier = Modifier) {
    val plan = state.plan ?: return
    val content = state.content ?: return
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    HotCard(modifier = modifier.fillMaxWidth().testTag(PLATE_CARD_TAG)) {
        // Head: the plate of the day, the level, one line of «почему» from content
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlateDonut(plan.plateOfDay, 96.dp)
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                HotOverline(
                    listOfNotNull(content.levels[plan.level], NutritionTexts.PLATE_OF_DAY).joinToString(" · "),
                    color = HotThemeTokens.colors.modeRovno,
                )
                Text(plan.template.ru, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                headWhy(plan, state)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = muted) }
            }
        }
        if (plan.lowered) Note(NutritionTexts.LOWERED, HotThemeTokens.colors.modeBerezhno)
        // TODO(review: Agents 3/10) hints are content/nutrition/rules.yaml → hints, status draft.
        plan.hints.firstOrNull()?.let { Note(it, muted) }
        if (!plan.consented) {
            Row(
                Modifier.padding(top = 12.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = actions.onOpenProfile),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FoodIcon(FoodGlyphs.lock, muted, size = 18.dp)
                Spacer(Modifier.width(8.dp))
                Text(NutritionTexts.NO_CONSENT, style = MaterialTheme.typography.bodySmall, color = muted)
            }
        }
        if (plan.shieldAllergens.isNotEmpty()) Shield(plan, state, Modifier.padding(top = 12.dp))

        Spacer(Modifier.height(16.dp))
        plan.slots.forEachIndexed { i, slot ->
            SlotRow(slot, last = i == plan.slots.lastIndex, onClick = { actions.onOpenMeal(slot.slot) })
        }

        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(NutritionTexts.CHIP_NO_TIME, selected = DayCondition.NO_TIME in state.chips, glyph = FoodGlyphs.clock) {
                actions.onChip(DayCondition.NO_TIME)
            }
            Pill(NutritionTexts.CHIP_TRAVEL, selected = DayCondition.TRAVEL in state.chips, glyph = FoodGlyphs.road) {
                actions.onChip(DayCondition.TRAVEL)
            }
            content.levels[plan.chosenLevel]?.let { Pill(it, glyph = FoodGlyphs.plate, onClick = actions.onOpenLevel) }
            Pill(NutritionTexts.SET_UP, glyph = FoodGlyphs.shield, onClick = actions.onOpenProfile)
        }
        plan.caffeineCutoffMin?.let { cutoff ->
            Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                FoodIcon(FoodGlyphs.sun, MaterialTheme.colorScheme.primary, size = 18.dp)
                Spacer(Modifier.width(8.dp))
                Text("${NutritionTexts.CAFFEINE} ${NutritionTexts.clock(cutoff)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (state.eveningDue) Evening(state.evening, actions.onEvening)
    }
}

/** The line under the title: the first rule of the day that is not the coffee line, else the plate method. */
private fun headWhy(plan: NutritionDayPlan, state: NutritionState): String? =
    plan.why.firstOrNull { it.ruleId != "NR-21" }?.text ?: state.content?.methods?.get("plate")?.whatRu

@Composable
private fun Note(text: String, color: Color) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color, modifier = Modifier.padding(top = 12.dp))
}

/** «Щит»: the allergens of the profile, crossed — none of them is in today's meals. */
@Composable
internal fun Shield(plan: NutritionDayPlan, state: NutritionState, modifier: Modifier = Modifier) {
    val aqua = HotThemeTokens.colors.modeRovno
    val names = plan.shieldAllergens.mapNotNull { state.content?.profile?.allergen(it)?.ru?.lowercase() }
    Row(
        modifier.semantics(mergeDescendants = true) { contentDescription = "${NutritionTexts.CHECKED}: ${names.joinToString()}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FoodIcon(FoodGlyphs.seal, aqua, size = 18.dp)
        Spacer(Modifier.width(8.dp))
        plan.shieldAllergens.take(6).forEach { id ->
            FoodGlyphs.allergens[id]?.let { FoodIcon(it, MaterialTheme.colorScheme.onSurfaceVariant, size = 20.dp, crossed = aqua) }
            Spacer(Modifier.width(4.dp))
        }
    }
}

@Composable
private fun SlotRow(slot: PlannedSlot, last: Boolean, onClick: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val nodeColor = if (slot.afterTraining) MaterialTheme.colorScheme.primary else HotThemeTokens.colors.modeRovno
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClickLabel = slot.ru, role = Role.Button, onClick = onClick)
            .heightIn(min = 64.dp),
    ) {
        // time column: empty when numbers are hidden (ed_care) — the order still reads
        Box(Modifier.width(46.dp).padding(top = 10.dp)) {
            slot.timeMin?.let { Text(NutritionTexts.clock(it), style = MaterialTheme.typography.labelSmall, color = HotThemeTokens.colors.textTertiary) }
        }
        Column(Modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Node(nodeColor, if (slot.plate.id == "snack") FoodGlyphs.half else FoodGlyphs.plate, size = 34.dp)
            if (!last) {
                Box(Modifier.padding(vertical = 2.dp).width(1.dp).weight(1f).background(HotThemeTokens.colors.rule))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            HotOverline(
                listOfNotNull(slot.ru, NutritionTexts.AFTER_TRAINING.takeIf { slot.afterTraining }, NutritionTexts.OPTIONAL.takeIf { slot.optional })
                    .joinToString(" · "),
                color = if (slot.afterTraining) MaterialTheme.colorScheme.primary else HotThemeTokens.colors.textTertiary,
            )
            Text(
                slot.meal?.meal?.ru ?: NutritionTexts.UNIVERSAL_PLATE,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                HAND_ORDER.forEach { role ->
                    val n = slot.plate.hands[role] ?: return@forEach
                    if (n > 0) HandPortion(role, slot.hands?.get(role))
                }
                slot.meal?.swaps?.firstOrNull()?.let { s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FoodIcon(FoodGlyphs.swap, HotThemeTokens.colors.modeRovno, size = 16.dp)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "${s.from.ru.lowercase()} → ${s.to.ru.lowercase()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = HotThemeTokens.colors.modeRovno,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (slot.meal != null && slot.hands == null) Text(slot.plate.ru, style = MaterialTheme.typography.bodySmall, color = muted)
        }
    }
}

internal val HAND_ORDER = listOf("protein", "starch", "veg", "fat")

/** The evening one-tap mark (A41 §10): three pictograms with a word; skipping is fine. */
@Composable
private fun Evening(current: EveningMark?, onMark: (EveningMark) -> Unit) {
    Spacer(Modifier.height(18.dp))
    HotOverline(if (current == null) NutritionTexts.EVENING_QUESTION else "${NutritionTexts.EVENING_QUESTION} · ${NutritionTexts.EVENING_SAVED}")
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EveningMark.entries.forEach { mark ->
            val on = mark == current
            val glyph = when (mark) {
                EveningMark.PLATE -> FoodGlyphs.seal
                EveningMark.PARTLY -> FoodGlyphs.half
                EveningMark.ATE -> FoodGlyphs.plate
            }
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .then(
                        if (on) {
                            Modifier.background(HotThemeTokens.colors.ruleSoft)
                        } else {
                            Modifier
                        },
                    )
                    .clickable(role = Role.RadioButton, onClick = { onMark(mark) })
                    .heightIn(min = 72.dp)
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FoodIcon(glyph, if (on) HotThemeTokens.colors.modeRovno else MaterialTheme.colorScheme.onSurfaceVariant, size = 26.dp)
                Text(NutritionTexts.eveningWord(mark), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
