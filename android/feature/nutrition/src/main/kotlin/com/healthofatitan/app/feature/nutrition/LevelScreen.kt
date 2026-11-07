package com.healthofatitan.app.feature.nutrition

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.healthofatitan.app.core.ui.component.HotCard
import com.healthofatitan.app.core.ui.component.HotCardShape
import com.healthofatitan.app.core.ui.component.HotOverline
import com.healthofatitan.app.core.ui.component.HotSpacing
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.domain.nutrition.Level

const val LEVEL_SCREEN_TAG = "screen_nutrition_level"

/** Levels open in the MVP (A41 §13): «Точность» waits for Agent 3 (question 3, flag later). */
val MVP_LEVELS = setOf(Level.BASE, Level.STRUCTURED)

/**
 * Выбор уровня (A41 §4, mockup n1): three cards; the user picks, safety may only lower — a level
 * above the profile cap is shown closed with a lock, never as a reward to earn. The methods under
 * each name come from `methods.yaml` (`from_level`, on by default).
 */
@Composable
fun NutritionLevelScreen(state: NutritionState, onPick: (Level) -> Unit, modifier: Modifier = Modifier) {
    val content = state.content
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = HotSpacing.edge, end = HotSpacing.edge, top = 4.dp, bottom = HotSpacing.sectionGap)
            .testTag(LEVEL_SCREEN_TAG),
        verticalArrangement = Arrangement.spacedBy(HotSpacing.cardGap),
    ) {
        if (content == null) return@Column
        Level.entries.forEach { level ->
            val chosen = level == state.chosenLevel
            val capped = level.ordinal > state.levelCap.ordinal
            val later = level !in MVP_LEVELS
            val open = !capped && !later
            val methods = content.methods.values
                .filter { it.fromLevel == level && !it.blocked && it.default == "on" }
                .map { it.ru }
            val rim = if (chosen) MaterialTheme.colorScheme.primary else HotThemeTokens.colors.hairline
            HotCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(HotCardShape)
                    .then(if (chosen) Modifier.border(1.5.dp, rim, HotCardShape) else Modifier)
                    .clickable(enabled = open, role = Role.RadioButton) { onPick(level) }
                    .semantics {
                        selected = chosen
                        if (!open) stateDescription = if (capped) NutritionTexts.LEVEL_LOCKED else NutritionTexts.LEVEL_LATER
                    },
                cornice = chosen,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HotOverline(
                        "${level.ordinal + 1} / 3",
                        color = if (chosen) MaterialTheme.colorScheme.primary else HotThemeTokens.colors.textTertiary,
                        modifier = Modifier.weight(1f),
                    )
                    when {
                        chosen -> FoodIcon(FoodGlyphs.check, MaterialTheme.colorScheme.primary, size = 22.dp)
                        !open -> FoodIcon(FoodGlyphs.lock, HotThemeTokens.colors.textTertiary, size = 20.dp)
                    }
                }
                Text(
                    content.levels[level].orEmpty(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (open || chosen) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    methods.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                if (!open) {
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        FoodIcon(FoodGlyphs.shield, HotThemeTokens.colors.modeBerezhno, size = 16.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (capped) NutritionTexts.LEVEL_LOCKED else NutritionTexts.LEVEL_LATER,
                            style = MaterialTheme.typography.bodySmall,
                            color = HotThemeTokens.colors.modeBerezhno,
                        )
                    }
                }
            }
        }
    }
}
