package com.healthofatitan.app.feature.me

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.healthofatitan.app.core.model.nav.HotRoute
import com.healthofatitan.app.core.ui.component.HotCard
import com.healthofatitan.app.core.ui.component.HotListRow
import com.healthofatitan.app.core.ui.component.HotPendingCard
import com.healthofatitan.app.core.ui.component.HotSpacing
import com.healthofatitan.app.core.ui.theme.HotType
import com.healthofatitan.app.feature.me.whoop.WhoopActions
import com.healthofatitan.app.feature.me.whoop.WhoopRoute
import com.healthofatitan.app.feature.me.whoop.WhoopScreen
import com.healthofatitan.app.feature.me.whoop.WhoopTexts
import com.healthofatitan.app.feature.me.whoop.WhoopUiState
import com.healthofatitan.app.integration.whoop.WhoopConnection
import com.healthofatitan.app.integration.whoop.WhoopStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Tab «Я» (A11 §1.1) and «Я → Подключения → WHOOP» (slice 1).
 *
 * @param stateful false renders the screens from default state without ViewModels (navigation
 *   tests run without Hilt).
 */
fun NavGraphBuilder.meScreen(onOpenWhoop: () -> Unit, onOpenHelp: () -> Unit = {}, stateful: Boolean = true) {
    composable(route = HotRoute.ME) {
        if (stateful) MeRoute(onOpenWhoop, onOpenHelp) else MeScreen(whoop = null, onOpenWhoop = onOpenWhoop, onOpenHelp = onOpenHelp)
    }
}

fun NavGraphBuilder.whoopScreen(stateful: Boolean = true) {
    composable(route = HotRoute.WHOOP) {
        if (stateful) WhoopRoute() else WhoopScreen(WhoopUiState(), WhoopActions())
    }
}

const val ME_SCREEN_TAG = "screen_me"
const val ME_WHOOP_ROW_TAG = "me_whoop_row"
const val ME_HELP_ROW_TAG = "me_help_row"

@HiltViewModel
class MeViewModel @Inject constructor(connection: WhoopConnection) : ViewModel() {
    val whoop: StateFlow<WhoopStatus?> = connection.status
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
private fun MeRoute(onOpenWhoop: () -> Unit, onOpenHelp: () -> Unit, viewModel: MeViewModel = hiltViewModel()) {
    val whoop by viewModel.whoop.collectAsStateWithLifecycle()
    MeScreen(whoop = whoop, onOpenWhoop = onOpenWhoop, onOpenHelp = onOpenHelp)
}

@Composable
fun MeScreen(whoop: WhoopStatus?, onOpenWhoop: () -> Unit, modifier: Modifier = Modifier, onOpenHelp: () -> Unit = {}) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = HotSpacing.edge, end = HotSpacing.edge, top = 4.dp, bottom = HotSpacing.sectionGap)
            .testTag(ME_SCREEN_TAG),
        verticalArrangement = Arrangement.spacedBy(HotSpacing.cardGap),
    ) {
        HotCard(cornice = true, modifier = Modifier.fillMaxWidth()) {
            Text(
                "HoT — wellness-приложение, не медицинское изделие: не ставит диагнозов и не назначает лечение.",
                style = HotType.quote,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        // D-029: the way to help moved from the header to «Я › Помощь» (≤ 2 taps from any tab);
        // the quick settings tile and the launcher shortcut still open it in one.
        HotCard(modifier = Modifier.fillMaxWidth()) {
            HotListRow(
                title = "Помощь",
                onClick = onOpenHelp,
                caption = "988 · 911",
                divider = false,
                modifier = Modifier.testTag(ME_HELP_ROW_TAG),
            )
        }
        Section("Подключения")
        HotCard(modifier = Modifier.fillMaxWidth()) {
            HotListRow(
                title = "WHOOP",
                onClick = onOpenWhoop,
                caption = whoopCaption(whoop),
                divider = false,
                modifier = Modifier.testTag(ME_WHOOP_ROW_TAG),
            )
        }
        HotPendingCard(
            title = "Скоро",
            caption = "Цели · Стек · Практики · Скрининг · Настройки",
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        )
    }
}

private fun whoopCaption(status: WhoopStatus?): String = when {
    status == null || !status.connected -> WhoopTexts.NOT_CONNECTED
    else -> WhoopTexts.statusProblem(status.syncStatus) ?: status.lastSyncAt?.let { WhoopTexts.syncLine(it) } ?: WhoopTexts.CONNECTED
}

@Composable
private fun Section(text: String) {
    Spacer(Modifier.height(4.dp))
    Text(
        text,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.semantics { heading() },
    )
}
