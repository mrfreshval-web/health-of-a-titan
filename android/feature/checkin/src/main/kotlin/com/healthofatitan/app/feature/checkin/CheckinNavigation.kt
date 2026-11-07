package com.healthofatitan.app.feature.checkin

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.healthofatitan.app.core.model.nav.HotRoute
import com.healthofatitan.app.core.ui.component.HotCard
import com.healthofatitan.app.core.ui.component.HotListRow
import com.healthofatitan.app.core.ui.component.HotSpacing
import com.healthofatitan.app.domain.checkin.Phase

const val ME_CHECKIN_ROW_TAG = "me_checkin_row"

/** Routes that draw their own full screen (no header, no tabs). */
val CHECKIN_FULL_SCREEN: Set<String> = setOf(
    HotRoute.CHECKIN,
    HotRoute.CHECKIN_RITUAL,
    HotRoute.CHECKIN_REVEAL,
    HotRoute.CHECKIN_CARE,
    HotRoute.CHECKIN_DISCOVERY,
)

/**
 * The state cards' screens (A44 v0.2, D-036). The module never navigates by itself: [onGo] opens a
 * route (replace = the current screen leaves the back stack), [onBack] returns, [onHelp] is the
 * SOS screen (its texts unchanged), [onTalk] the mentor.
 */
fun NavGraphBuilder.checkinScreens(
    onBack: () -> Unit,
    onGo: (route: String, replace: Boolean) -> Unit,
    onHelp: () -> Unit,
    onTalk: () -> Unit,
    stateful: Boolean = true,
) {
    composable(HotRoute.CHECKIN) {
        if (stateful) {
            Deck(
                Phase.WAKE,
                onClose = onBack,
                onReveal = { onGo(HotRoute.CHECKIN_REVEAL, true) },
                onCare = { onGo(HotRoute.CHECKIN_CARE, true) },
            )
        }
    }
    composable(HotRoute.CHECKIN_RITUAL) {
        if (stateful) Deck(Phase.RITUAL, onClose = onBack, onReveal = onBack, onCare = { onGo(HotRoute.CHECKIN_CARE, true) })
    }
    composable(HotRoute.CHECKIN_REVEAL) {
        if (stateful) RevealRoute(onDone = onBack)
    }
    composable(HotRoute.CHECKIN_CARE) {
        // Sober: the plain background over the night sky, no Titan (A07 §4.3 level 1).
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            CareScreen(onHelp = onHelp, onTalk = onTalk, onClose = onBack)
        }
    }
    composable(HotRoute.CHECKIN_DISCOVERY) {
        if (stateful) DiscoveryRoute(onClose = onBack)
    }
    composable(HotRoute.CHECKIN_COLLECTION) {
        if (stateful) CollectionRoute()
    }
    composable(HotRoute.CHECKIN_SETTINGS) {
        if (stateful) CheckinSettingsRoute(onOpenCollection = { onGo(HotRoute.CHECKIN_COLLECTION, false) })
    }
}

@Composable
private fun Deck(phase: Phase, onClose: () -> Unit, onReveal: () -> Unit, onCare: () -> Unit, viewModel: CheckinViewModel = hiltViewModel()) {
    val mentor by viewModel.mentorPrefs.collectAsStateWithLifecycle()
    DeckRoute(phase = phase, persona = mentor.effective, onClose = onClose, onReveal = onReveal, onCare = onCare)
}

@Composable
private fun CollectionRoute(viewModel: CheckinViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { viewModel.loadCollection() }
    val tiles by viewModel.collection.collectAsStateWithLifecycle()
    Collection(
        tiles,
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = HotSpacing.edge, vertical = 8.dp),
    )
}

/** «Что спрашивать» as a row of «Настройки» (the screen of :feature:me has a slot for it). */
@Composable
fun CheckinSettingsRow(onOpen: () -> Unit) {
    HotCard(modifier = Modifier.fillMaxWidth()) {
        HotListRow(
            title = CheckinTexts.SETTINGS,
            onClick = onOpen,
            caption = CheckinTexts.SETTINGS_CAPTION,
            divider = false,
            modifier = Modifier.testTag(ME_CHECKIN_ROW_TAG),
        )
    }
}
