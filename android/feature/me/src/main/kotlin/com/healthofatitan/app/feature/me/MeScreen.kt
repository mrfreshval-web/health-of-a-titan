package com.healthofatitan.app.feature.me

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import com.healthofatitan.app.core.database.ProfileSettingsStore
import com.healthofatitan.app.core.model.nav.HotRoute
import com.healthofatitan.app.core.model.profile.ProfileSettings
import com.healthofatitan.app.core.model.safety.HelpNumbers
import com.healthofatitan.app.core.ui.component.HotCard
import com.healthofatitan.app.core.ui.component.HotListRow
import com.healthofatitan.app.core.ui.component.HotPendingCard
import com.healthofatitan.app.core.ui.component.HotSpacing
import com.healthofatitan.app.core.ui.theme.HotType
import com.healthofatitan.app.feature.me.profile.ProfileTexts
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
 * «Настройки» under the avatar in the header corner (D-033: the tab «Я» became «Путь») and
 * «Настройки → Подключения → WHOOP» (slice 1). Help stays the first row.
 *
 * @param stateful false renders the screens from default state without ViewModels (navigation
 *   tests run without Hilt).
 */
fun NavGraphBuilder.meScreen(
    onOpenWhoop: () -> Unit,
    onOpenHelp: () -> Unit = {},
    onOpenMentor: () -> Unit = {},
    stateful: Boolean = true,
    onOpenNutrition: () -> Unit = {},
    /** Rows of other modules under «Еда и тело» (state cards: «Что спрашивать»); :app puts them in. */
    rows: @Composable ColumnScope.() -> Unit = {},
    /** The card «Ты» (A61 §5.2, D-055): the profile. */
    onOpenProfile: () -> Unit = {},
) {
    composable(route = HotRoute.ME) {
        if (stateful) {
            MeRoute(onOpenWhoop, onOpenHelp, onOpenMentor, onOpenNutrition, onOpenProfile, rows)
        } else {
            MeScreen(
                whoop = null,
                onOpenWhoop = onOpenWhoop,
                onOpenHelp = onOpenHelp,
                onOpenMentor = onOpenMentor,
                onOpenNutrition = onOpenNutrition,
                onOpenProfile = onOpenProfile,
                rows = rows,
            )
        }
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
const val ME_MENTOR_ROW_TAG = "me_mentor_row"
const val ME_NUTRITION_ROW_TAG = "me_nutrition_row"
const val ME_PROFILE_ROW_TAG = "me_profile_row"

@HiltViewModel
class MeViewModel @Inject constructor(connection: WhoopConnection, profile: ProfileSettingsStore) : ViewModel() {
    val whoop: StateFlow<WhoopStatus?> = connection.status
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val profile: StateFlow<ProfileSettings?> = profile.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
private fun MeRoute(
    onOpenWhoop: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenMentor: () -> Unit,
    onOpenNutrition: () -> Unit,
    onOpenProfile: () -> Unit,
    rows: @Composable ColumnScope.() -> Unit,
    viewModel: MeViewModel = hiltViewModel(),
) {
    val whoop by viewModel.whoop.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    MeScreen(
        whoop = whoop,
        onOpenWhoop = onOpenWhoop,
        onOpenHelp = onOpenHelp,
        onOpenMentor = onOpenMentor,
        onOpenNutrition = onOpenNutrition,
        onOpenProfile = onOpenProfile,
        profile = profile,
        rows = rows,
    )
}

@Composable
fun MeScreen(
    whoop: WhoopStatus?,
    onOpenWhoop: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenHelp: () -> Unit = {},
    onOpenMentor: () -> Unit = {},
    onOpenNutrition: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    profile: ProfileSettings? = null,
    rows: @Composable ColumnScope.() -> Unit = {},
) {
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
        // A61 §5.2 (D-055): the card «Ты» on top, help right under it — still two taps from any tab.
        HotCard(modifier = Modifier.fillMaxWidth()) {
            HotListRow(
                title = ProfileTexts.SECTION_YOU,
                onClick = onOpenProfile,
                caption = profileCaption(profile),
                divider = false,
                modifier = Modifier.testTag(ME_PROFILE_ROW_TAG),
            )
        }
        // D-029 / D-033: help is «аватар › Помощь» in two taps from any tab and the first row here;
        // the quick settings tile and the launcher shortcut still open it in one.
        HotCard(modifier = Modifier.fillMaxWidth()) {
            HotListRow(
                title = "Помощь",
                onClick = onOpenHelp,
                caption = HelpNumbers.CAPTION,
                divider = false,
                modifier = Modifier.testTag(ME_HELP_ROW_TAG),
            )
        }
        HotCard(modifier = Modifier.fillMaxWidth()) {
            HotListRow(
                title = "Наставник",
                onClick = onOpenMentor,
                caption = "Сенсей · Бро · Дворецкий",
                divider = false,
                modifier = Modifier.testTag(ME_MENTOR_ROW_TAG),
            )
        }
        // Stream C (A41 §10): the profile of specifics, shared by food and training.
        HotCard(modifier = Modifier.fillMaxWidth()) {
            HotListRow(
                title = "Еда и тело",
                onClick = onOpenNutrition,
                caption = "Аллергены · тип питания · уровень",
                divider = false,
                modifier = Modifier.testTag(ME_NUTRITION_ROW_TAG),
            )
        }
        rows()
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
            caption = "Стек · Практики · Скрининг",
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        )
    }
}

/** «Как к тебе обращаться: не важно»; the sex for calculations is not shown here (S3). */
private fun profileCaption(profile: ProfileSettings?): String =
    profile?.let { "${ProfileTexts.ADDRESS_TITLE}: ${ProfileTexts.address(it.addressForm)}" } ?: ProfileTexts.ROW_CAPTION

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
