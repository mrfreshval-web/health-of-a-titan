package com.healthofatitan.app.feature.nutrition

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.healthofatitan.app.core.ui.component.HotCard
import com.healthofatitan.app.core.ui.component.HotOverline
import com.healthofatitan.app.core.ui.component.HotSecondaryButton
import com.healthofatitan.app.core.ui.component.HotSpacing
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.core.ui.theme.HotType
import com.healthofatitan.app.domain.nutrition.NutritionDayPlan
import com.healthofatitan.app.domain.nutrition.PlannedSlot

const val MEAL_SCREEN_TAG = "screen_nutrition_meal"

/**
 * Карточка блюда (A41 §10, mockup n4): the plate as a ring, hand portions, the allergen shield
 * «проверено по твоему профилю», automatic swaps, «почему» from rules.yaml, and «Заменить» for the
 * next library meal of this slot. Numbers (time, minutes, counts) disappear in ed_care.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MealCardScreen(state: NutritionState, slotId: String, onReplace: (String) -> Unit, modifier: Modifier = Modifier) {
    val plan = state.plan
    val content = state.content
    val slot = plan?.slots?.firstOrNull { it.slot == slotId }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = HotSpacing.edge, end = HotSpacing.edge, top = 4.dp, bottom = HotSpacing.sectionGap)
            .testTag(MEAL_SCREEN_TAG),
        verticalArrangement = Arrangement.spacedBy(HotSpacing.cardGap),
    ) {
        if (plan == null || content == null || slot == null) return@Column
        val resolved = slot.meal
        val mealPlate = resolved?.meal?.plate?.let(content::plate) ?: slot.plate
        HotOverline(
            listOfNotNull(slot.ru, slot.timeMin?.let(NutritionTexts::clock), NutritionTexts.AFTER_TRAINING.takeIf { slot.afterTraining }).joinToString(" · "),
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            PlateDonut(mealPlate, 220.dp, stroke = 44.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PLATE_ROLES.filter { (mealPlate.share[it] ?: 0.0) > 0 }.forEach { r ->
                    Text(NutritionTexts.roleWord(r), style = MaterialTheme.typography.labelSmall, color = roleColor(r))
                }
            }
        }
        Text(
            resolved?.meal?.ru ?: NutritionTexts.UNIVERSAL_PLATE,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!plan.hideNumbers) resolved?.meal?.prepMin?.takeIf { it > 0 }?.let { Pill(NutritionTexts.minutes(it), glyph = FoodGlyphs.clock) }
            Pill(slot.plate.ru, selected = true)
        }

        // Hand portions: the plate of the slot, by role; counts only when numbers are allowed.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HAND_ORDER.filter { (slot.plate.hands[it] ?: 0.0) > 0 }.forEach { role ->
                val count = slot.hands?.get(role)
                val what = resolved?.components?.firstOrNull { it.first.role == role || (role == "starch" && it.first.role == "fruit") }?.first?.ru
                HotCard(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 14.dp, horizontal = 6.dp)) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FoodIcon(FoodGlyphs.hand(role), roleColor(role), size = 28.dp)
                        Text(
                            listOfNotNull(count?.takeIf { it != 1.0 }?.let(NutritionTexts::hands), NutritionTexts.handWord(role)).joinToString(" "),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                        what?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = HotThemeTokens.colors.textTertiary, textAlign = TextAlign.Center, maxLines = 2)
                        }
                    }
                }
            }
        }

        // Allergen shield: the profile's allergens are not in this meal (derived from components).
        if (plan.shieldAllergens.isNotEmpty() && resolved != null) {
            HotCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FoodIcon(FoodGlyphs.seal, HotThemeTokens.colors.modeRovno, size = 34.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(NutritionTexts.CHECKED, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            plan.shieldAllergens.mapNotNull { content.profile.allergen(it)?.ru?.lowercase() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = muted,
                        )
                    }
                    plan.shieldAllergens.take(3).forEach { id ->
                        FoodGlyphs.allergens[id]?.let { FoodIcon(it, muted, size = 24.dp, crossed = HotThemeTokens.colors.modeRovno) }
                    }
                }
                resolved.mayContainNotes.takeIf { it.isNotEmpty() }?.let { notes ->
                    Text(
                        NutritionTexts.MAY_CONTAIN + notes.mapNotNull { content.profile.allergen(it)?.ru?.lowercase() }.joinToString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = muted,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
                if (plan.restaurantWarning) {
                    Text(NutritionTexts.RESTAURANT, style = MaterialTheme.typography.bodySmall, color = muted, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }

        // Swaps the filter made, «миндаль → тыквенные семечки».
        resolved?.swaps?.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) {}) {
                FoodIcon(FoodGlyphs.swap, HotThemeTokens.colors.modeRovno, size = 20.dp)
                Spacer(Modifier.width(10.dp))
                Text("${s.from.ru} → ${s.to.ru}", style = MaterialTheme.typography.bodyMedium, color = HotThemeTokens.colors.modeRovno)
            }
        }

        // Composition, from components (what the shield was checked against).
        if (resolved != null) {
            HotOverline(NutritionTexts.COMPOSITION)
            Text(resolved.components.joinToString(" · ") { it.first.ru }, style = MaterialTheme.typography.bodyMedium, color = muted)
        }

        // «Почему» — rules.yaml why_ru, never generated. TODO(review: Agents 3/10): content is draft.
        val why = mealWhy(plan, slot)
        if (why.isNotEmpty()) {
            HotOverline(NutritionTexts.WHY)
            why.forEach { Text(it, style = HotType.quote.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize), color = MaterialTheme.colorScheme.onSurface) }
        }
        if (plan.shieldAllergens.isNotEmpty()) {
            Text(content.profile.disclaimer, style = MaterialTheme.typography.bodySmall, color = HotThemeTokens.colors.textTertiary)
        }
        Spacer(Modifier.height(4.dp))
        if (slot.options > 1) {
            HotSecondaryButton(
                text = NutritionTexts.REPLACE,
                onClick = { onReplace(slot.slot) },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "${NutritionTexts.REPLACE}: ${slot.ru}" },
            )
        }
    }
}

/** Lines of «почему» that concern this slot (by rule id), else the first rule of the day. */
private fun mealWhy(plan: NutritionDayPlan, slot: PlannedSlot): List<String> {
    val ids = buildList {
        if (slot.afterTraining || slot.slot == "post_training") add("NR-31")
        if (slot.slot == "pre_training") addAll(listOf("NR-11", "NR-30"))
        if (slot.slot == "during_training") add("NR-32")
        if (slot.slot == "dinner") addAll(listOf("NR-34", "NR-35", "NR-37"))
        if (slot.slot == "breakfast") add("NR-20")
        addAll(listOf("NR-01", "NR-02", "NR-03", "NR-10", "NR-40", "NR-41"))
    }
    return plan.why.filter { it.ruleId in ids }.map { it.text }.take(2)
}
