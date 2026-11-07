package com.healthofatitan.app.feature.path

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.core.ui.theme.PathColors
import com.healthofatitan.app.core.ui.theme.Titanium
import com.healthofatitan.app.domain.path.Element
import com.healthofatitan.app.domain.path.PathCopy

/** The type roles of «Путь» (A58-VP §5.0, D-049): Geist everywhere; Cormorant only in HotType.voice/haiku/horizonName. */
internal object PathText {
    val goalTitle = TextStyle(fontSize = 21.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold)
    val ritualH1 = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold)
    val ritualH1First = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold)
    val cardTitle = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
    val body = TextStyle(fontSize = 15.sp, lineHeight = 22.sp)
    val caption = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontFeatureSettings = "tnum")
    val option = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
    val sheetTitle = TextStyle(fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
}

/** The colour of an element's material (A58-VP §1.5): never a mode colour, never red. */
@Composable
internal fun materialOf(e: Element): Color {
    val c = PathColors.current
    return when (e) {
        Element.SKY -> c.sky
        Element.EARTH -> c.earth
        Element.WATER -> c.water
    }
}

/** The accent of action texts: «Рассвет» on dark, its text tone on light. */
@Composable
internal fun dawnText(): Color = if (HotThemeTokens.colors.isDark) Titanium.Dawn else Titanium.DawnText

/** A chip 40 dp (radius 20), in a 48 dp touch zone; selected = a «Рассвет» border on its container. */
@Composable
internal fun PathChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tokens = HotThemeTokens.colors
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier
            .heightIn(min = 48.dp)
            .semantics {
                role = Role.RadioButton
                this.selected = selected
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .defaultMinSize(minHeight = 40.dp)
                .clip(shape)
                .background(if (selected) Titanium.DawnContainer.copy(alpha = if (tokens.isDark) 1f else 0.18f) else tokens.glassBottom)
                .border(if (selected) 1.5.dp else 1.dp, if (selected) dawnText() else tokens.rule, shape)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, style = PathText.body, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PathChips(items: List<String>, selected: Int?, onPick: (Int) -> Unit, modifier: Modifier = Modifier, tag: String = "path_chip") {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEachIndexed { i, t -> PathChip(t, selected == i, { onPick(i) }, Modifier.testTag("${tag}_$i")) }
    }
}

/** The mark of a draft text: «черновик · ждёт ревью 10, 3, 7». */
@Composable
internal fun ReviewNote(modifier: Modifier = Modifier) {
    Text(PathCopy.REVIEW, style = PathText.caption, color = HotThemeTokens.colors.textTertiary, modifier = modifier)
}

/** A card-switch ≥ 96 dp (Э6) or ≥ 64 dp (Перекрёсток): a radio button with a «Рассвет» border when chosen. */
@Composable
internal fun OptionCard(
    title: String,
    sub: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    overline: String? = null,
    minHeight: Int = 64,
) {
    val tokens = HotThemeTokens.colors
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(min = minHeight.dp)
            .clip(shape)
            .background(tokens.glassBottom)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) dawnText() else tokens.rule, shape)
            .semantics {
                role = Role.RadioButton
                this.selected = selected
            }
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        if (overline != null) {
            Text(overline, style = PathText.caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp), color = tokens.textTertiary)
        }
        Text(title, style = if (overline != null) PathText.option else PathText.cardTitle, color = MaterialTheme.colorScheme.onSurface)
        if (sub != null) Text(sub, style = PathText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
