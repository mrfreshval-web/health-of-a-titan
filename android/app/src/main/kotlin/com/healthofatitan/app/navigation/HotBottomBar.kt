package com.healthofatitan.app.navigation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.healthofatitan.app.core.model.nav.HotRoute
import com.healthofatitan.app.core.ui.R
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.core.ui.theme.Titanium

const val PLUS_TAG = "bottom_bar_plus"
const val PLUS_SHEET_TAG = "plus_sheet"

/**
 * The bottom bar of A66 §2.3: a floating glass pill, 16 dp from the edges, 72 dp high, five slots —
 * Сегодня · Неделя · «+» · Путь · За плечами. Line icons; the active tab is the dawn icon on a soft
 * light. A tab is a selectable with the role Tab, so TalkBack reads «выбрано».
 */
@Composable
internal fun HotBottomBar(currentTab: HotTab, onTabClick: (HotTab) -> Unit, onPlus: () -> Unit) {
    val tokens = HotThemeTokens.colors
    val shape = RoundedCornerShape(36.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(tokens.glassTop, tokens.glassBottom)), shape)
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.72f), shape)
                .border(1.dp, tokens.glassEdge, shape),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val (left, right) = HotTab.entries.partition { it.ordinal < 2 }
            left.forEach { TabSlot(it, it == currentTab, onTabClick) }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { PlusButton(onPlus) }
            right.forEach { TabSlot(it, it == currentTab, onTabClick) }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TabSlot(tab: HotTab, selected: Boolean, onClick: (HotTab) -> Unit) {
    val tokens = HotThemeTokens.colors
    val color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 56.dp)
            .selectable(selected = selected, role = Role.Tab, onClick = { onClick(tab) })
            .testTag(tab.testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(width = 48.dp, height = 28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (selected) tokens.ruleSoft else androidx.compose.ui.graphics.Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(tab.icon),
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else color,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(tab.label, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1, overflow = TextOverflow.Visible, softWrap = false)
    }
}

@Composable
private fun PlusButton(onClick: () -> Unit) {
    val tokens = HotThemeTokens.colors
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(Brush.verticalGradient(tokens.dawnFill), CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = PlusCopy.A11Y }
            .testTag(PLUS_TAG),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.core_ui_ic_plus), contentDescription = null, tint = Titanium.OnDawn, modifier = Modifier.size(24.dp))
    }
}

/** One tile of the «+» sheet: what it records and where it leads. */
internal data class PlusTile(val title: String, val sub: String, val route: String, @param:DrawableRes val icon: Int)

/** Texts of «+» (A66 §2.4, draft for Agents 16 and 10). */
internal object PlusCopy {
    const val A11Y = "Добавить: еда, тренировка, питьё, самочувствие, передышка"
    const val ARSENAL = "Арсенал: упражнения · практики · тарелки ›"

    /**
     * The tiles. «Заметка» of A66 §2.4 waits for the notes of «За плечами» (A66 §6): there is no
     * store for a note yet, and a tile that leads nowhere is worse than no tile.
     */
    val tiles = listOf(
        PlusTile("Еда", "фото · план", HotRoute.NUTRITION_DAY, R.drawable.core_ui_ic_today),
        PlusTile("Тренировка", "начать", HotRoute.TRAINING, R.drawable.core_ui_ic_path),
        PlusTile("Питьё", "+ стакан", HotRoute.support("water"), R.drawable.core_ui_ic_week),
        PlusTile("Как ты", "отметить", HotRoute.CHECKIN, R.drawable.core_ui_ic_me),
        PlusTile("Передышка", "2 мин", HotRoute.BREATHER, R.drawable.core_ui_ic_behind),
    )
}

/** «+» (A66 §2.4): a bottom sheet with the entries of the day; a tap opens the place it records in. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlusSheet(onDismiss: () -> Unit, onOpen: (String) -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, modifier = Modifier.testTag(PLUS_SHEET_TAG)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            PlusCopy.tiles.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { tile -> PlusTileView(tile, Modifier.weight(1f)) { onOpen(tile.route) } }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Text(
                PlusCopy.ARSENAL,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable { onOpen(HotRoute.ARSENAL) }
                    .padding(vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun PlusTileView(tile: PlusTile, modifier: Modifier, onClick: () -> Unit) {
    val tokens = HotThemeTokens.colors
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .height(96.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(tokens.glassTop, tokens.glassBottom)), shape)
            .border(1.dp, tokens.hairline, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(painterResource(tile.icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Column {
            Text(tile.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text(tile.sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}
