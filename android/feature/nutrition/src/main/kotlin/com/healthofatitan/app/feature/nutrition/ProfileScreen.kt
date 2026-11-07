package com.healthofatitan.app.feature.nutrition

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.healthofatitan.app.core.model.profile.AllergySeverity
import com.healthofatitan.app.core.model.profile.ProfileCategory
import com.healthofatitan.app.core.ui.component.HotCard
import com.healthofatitan.app.core.ui.component.HotOverline
import com.healthofatitan.app.core.ui.component.HotPrimaryButton
import com.healthofatitan.app.core.ui.component.HotSpacing
import com.healthofatitan.app.core.ui.component.HotTextButton
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.core.ui.theme.HotType

const val PROFILE_SCREEN_TAG = "screen_nutrition_profile"
const val PROFILE_CONSENT_TAG = "nutrition_consent"

/** Actions of the profile screen (all stored at once, S4, by category). */
data class ProfileActions(
    val onConsent: (Boolean) -> Unit = {},
    val onAllergen: (String) -> Unit = {},
    val onDiet: (String) -> Unit = {},
    val onIntolerance: (String) -> Unit = {},
    val onLifeStage: (String) -> Unit = {},
    val onMedical: (String) -> Unit = {},
    val onInjury: (String) -> Unit = {},
    val onDelete: (ProfileCategory) -> Unit = {},
    val onDeleteAll: () -> Unit = {},
    val onOpenLevel: () -> Unit = {},
)

/**
 * «Я › Еда и тело» (A41 §6, §10, mockup n2): separate explicit consent first (Agent 7), then
 * allergens as a grid of pictograms (one tap cycles «избегаю → аллергия → тяжёлая»), the type of
 * eating as chips, conditions and injuries. Defaults are «ничего не отмечено»; every label comes
 * from `content/profile/constraints.yaml`. Deletion by category and in full.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NutritionProfileScreen(state: NutritionState, actions: ProfileActions, modifier: Modifier = Modifier) {
    val dict = state.content?.profile
    val snap = state.profile
    val p = snap.constraints
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = HotSpacing.edge, end = HotSpacing.edge, top = 4.dp, bottom = HotSpacing.sectionGap)
            .testTag(PROFILE_SCREEN_TAG),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(NutritionTexts.ONLY_ON_PHONE, selected = true, glyph = FoodGlyphs.lock)
            snap.consentAtMs?.takeIf { snap.consented }?.let { Pill(NutritionTexts.consentDate(it)) }
            if (snap.consented && !p.isEmpty) Pill(NutritionTexts.DELETE_ALL, glyph = FoodGlyphs.trash, onClick = actions.onDeleteAll)
        }
        if (dict == null) return@Column
        if (!snap.consented) {
            Consent(actions)
            Text(dict.disclaimer, style = MaterialTheme.typography.bodySmall, color = HotThemeTokens.colors.textTertiary)
            return@Column
        }

        // Allergens: 16 tiles, 4 per row.
        SectionHead(
            NutritionTexts.ALLERGENS,
            p.allergies.size.takeIf { it > 0 }?.toString(),
            { actions.onDelete(ProfileCategory.ALLERGENS) }.takeIf { p.has(ProfileCategory.ALLERGENS) },
        )
        Text(NutritionTexts.ALLERGEN_TAP, style = MaterialTheme.typography.bodySmall, color = HotThemeTokens.colors.textTertiary)
        dict.allergens.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { a ->
                    AllergenTile(
                        name = a.ru,
                        glyph = FoodGlyphs.allergens[a.id],
                        severity = p.allergies[a.id],
                        severityRu = p.allergies[a.id]?.let { sev -> SEVERITY_SHORT[sev] },
                        onClick = { actions.onAllergen(a.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        SectionHead(NutritionTexts.DIETS, null, { actions.onDelete(ProfileCategory.DIETS) }.takeIf { p.has(ProfileCategory.DIETS) })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            dict.diets.forEach { d ->
                val on = d.id in p.diets || (d.id == "omnivore" && p.diets.isEmpty())
                Pill(d.ru, selected = on, glyph = FoodGlyphs.check.takeIf { on }) { actions.onDiet(d.id) }
            }
        }

        SectionHead(NutritionTexts.INTOLERANCES, null, { actions.onDelete(ProfileCategory.INTOLERANCES) }.takeIf { p.has(ProfileCategory.INTOLERANCES) })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            dict.intolerances.forEach { i ->
                Pill(i.ru, selected = i.id in p.intolerances) { actions.onIntolerance(i.id) }
            }
        }

        // TODO(review: Agent 3 — medical flags and life stage are draft content, veto before release).
        val hasConditions = p.has(ProfileCategory.MEDICAL) || p.has(ProfileCategory.LIFE_STAGE)
        SectionHead(NutritionTexts.CONDITIONS, null, {
            actions.onDelete(ProfileCategory.MEDICAL)
            actions.onDelete(ProfileCategory.LIFE_STAGE)
        }.takeIf { hasConditions })
        Text(NutritionTexts.CONDITIONS_NOTE, style = MaterialTheme.typography.bodySmall, color = HotThemeTokens.colors.textTertiary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val iris = HotThemeTokens.colors.modeBerezhno
            dict.lifeStages.forEach { f -> Pill(f.ru, selected = f.id in p.lifeStage, accent = iris) { actions.onLifeStage(f.id) } }
            dict.medicalFlags.forEach { f -> Pill(f.ru, selected = f.id in p.medical, accent = iris) { actions.onMedical(f.id) } }
        }

        SectionHead(NutritionTexts.INJURIES, null, { actions.onDelete(ProfileCategory.INJURIES) }.takeIf { p.has(ProfileCategory.INJURIES) })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val dawn = MaterialTheme.colorScheme.primary
            dict.injuryPresets.filter { it.lifeStage == null && it.medicalFlag == null }.forEach { j ->
                Pill(j.ru, selected = j.id in p.injuries, accent = dawn) { actions.onInjury(j.id) }
            }
        }

        state.content?.levels?.get(state.chosenLevel)?.let { level ->
            SectionHead(NutritionTexts.LEVEL, null, null)
            Pill(level, selected = true, glyph = FoodGlyphs.plate, onClick = actions.onOpenLevel)
        }

        Text(dict.disclaimer, style = MaterialTheme.typography.bodySmall, color = HotThemeTokens.colors.textTertiary)
        HotTextButton(NutritionTexts.CONSENT_REVOKE, onClick = { actions.onConsent(false) })
    }
}

private val SEVERITY_SHORT = NutritionTexts.SEVERITY_SHORT

@Composable
private fun Consent(actions: ProfileActions) {
    HotCard(cornice = true, modifier = Modifier.fillMaxWidth().testTag(PROFILE_CONSENT_TAG)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FoodIcon(FoodGlyphs.shield, HotThemeTokens.colors.modeRovno, size = 28.dp)
            Spacer(Modifier.width(12.dp))
            Text(NutritionTexts.CONSENT_TITLE, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(12.dp))
        // TODO(review: Agent 7): consent wording and the consent id `profile_s4` v1.
        Text(NutritionTexts.CONSENT_TEXT, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(18.dp))
        HotPrimaryButton(NutritionTexts.CONSENT_YES, onClick = { actions.onConsent(true) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun SectionHead(text: String, count: String?, onDelete: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        HotOverline(text, modifier = Modifier.weight(1f).semantics { heading() })
        count?.let { HotOverline(it) }
        if (onDelete != null) {
            Spacer(Modifier.width(8.dp))
            Pill(NutritionTexts.DELETE, glyph = FoodGlyphs.trash, onClick = onDelete)
        }
    }
}

@Composable
private fun AllergenTile(
    name: String,
    glyph: FoodGlyph?,
    severity: AllergySeverity?,
    severityRu: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent: Color = when (severity) {
        null -> HotThemeTokens.colors.rule
        AllergySeverity.AVOID -> HotThemeTokens.colors.modeBerezhno
        AllergySeverity.ALLERGY -> MaterialTheme.colorScheme.primary
        // Red is for red flags (A11 §12.1): a severe allergy is one.
        AllergySeverity.ANAPHYLAXIS -> MaterialTheme.colorScheme.error
    }
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier
            .aspectRatio(0.92f)
            .clip(shape)
            .border(if (severity == null) 1.dp else 1.5.dp, accent, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = name
                stateDescription = severityRu ?: NutritionTexts.NOT_MARKED
            },
    ) {
        Column(Modifier.fillMaxSize().padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            glyph?.let { FoodIcon(it, if (severity == null) MaterialTheme.colorScheme.onSurfaceVariant else accent, size = 28.dp) }
            Spacer(Modifier.height(8.dp))
            Text(
                name.substringBefore(" ("),
                // A long single word («Ракообразные») would break mid-word in a quarter-width tile.
                style = MaterialTheme.typography.labelMedium.let { s ->
                    if (name.substringBefore(" (").split(' ').any { it.length > 10 }) s.copy(fontSize = s.fontSize * 0.86f) else s
                },
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (severityRu != null) {
            Text(
                severityRu.uppercase(),
                style = HotType.mono.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.85f),
                color = accent,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 6.dp),
            )
        }
    }
}
