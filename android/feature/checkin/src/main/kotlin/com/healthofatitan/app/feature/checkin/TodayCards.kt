package com.healthofatitan.app.feature.checkin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.healthofatitan.app.core.ui.component.HotCard
import com.healthofatitan.app.core.ui.component.HotCardShape
import com.healthofatitan.app.core.ui.component.HotOverline
import com.healthofatitan.app.core.ui.component.HotPrimaryButton
import com.healthofatitan.app.core.ui.component.HotTextButton
import com.healthofatitan.app.domain.checkin.CardId
import com.healthofatitan.app.domain.checkin.TitanLook

const val TODAY_WAKE_TAG = "checkin_today_wake"
const val TODAY_REVEAL_TAG = "checkin_today_reveal"
const val TODAY_RITUAL_TAG = "checkin_today_ritual"
const val TODAY_DISCOVERY_TAG = "checkin_today_discovery"

/** Where the «Сегодня» cards lead. */
class CheckinNav(
    val onWake: () -> Unit = {},
    val onReveal: () -> Unit = {},
    val onRitual: () -> Unit = {},
    val onDiscovery: () -> Unit = {},
)

/**
 * The state cards on «Сегодня» (the top slot): the one explicit «Только ты» offer (D-036), the
 * morning deck if it was closed, «WHOOP досчитал ночь» for the reveal, «Как сейчас? · 15 с» after
 * the ritual (until 12:00, no push), a new discovery, the 14-day removal offer and, once, the way to
 * a specialist. Nothing here blocks «Сегодня».
 */
@Composable
fun CheckinTodayCards(nav: CheckinNav, viewModel: CheckinViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { viewModel.refreshToday() }
    val today by viewModel.today.collectAsStateWithLifecycle()
    CheckinTodayContent(
        today = today,
        nav = nav,
        onPrivate = viewModel::privateOffer,
        onRemoval = viewModel::removal,
        onSpecialist = viewModel::specialistSeen,
    )
}

@Composable
fun CheckinTodayContent(
    today: TodayCheckin,
    nav: CheckinNav,
    onPrivate: (Boolean) -> Unit = {},
    onRemoval: (CardId, Boolean) -> Unit = { _, _ -> },
    onSpecialist: () -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (today.privateOffer) PrivateOfferCard(onYes = { onPrivate(true) }, onNo = { onPrivate(false) })
        when {
            today.wakeDue -> SmallCard(CheckinTexts.START_WAKE, CheckinTexts.START_WAKE_LINE, nav.onWake, TODAY_WAKE_TAG, light = 0.3f)
            today.revealReady -> SmallCard(CheckinTexts.REVEAL_READY, CheckinTexts.REVEAL_OPEN, nav.onReveal, TODAY_REVEAL_TAG, light = 0.7f)
            today.ritualDue -> SmallCard("${CheckinTexts.RITUAL_CARD} · ${CheckinTexts.RITUAL_CARD_TIME}", CheckinTexts.RITUAL, nav.onRitual, TODAY_RITUAL_TAG, light = 0.55f)
        }
        today.discovery?.let { SmallCard(CheckinTexts.NEW_DISCOVERY, CheckinTexts.pairTitle(it.pair), nav.onDiscovery, TODAY_DISCOVERY_TAG, light = 0.9f) }
        today.removal?.let { card ->
            HotCard(modifier = Modifier.fillMaxWidth()) {
                Text(CheckinTexts.removalOffer(card), style = MaterialTheme.typography.bodyLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HotTextButton(CheckinTexts.S_REMOVE, onClick = { onRemoval(card, true) })
                    HotTextButton(CheckinTexts.KEEP, onClick = { onRemoval(card, false) })
                }
            }
        }
        if (today.specialist) {
            HotCard(modifier = Modifier.fillMaxWidth()) {
                Text(CheckinTexts.SPECIALIST, style = MaterialTheme.typography.bodyLarge)
                HotTextButton(CheckinTexts.SPECIALIST_OK, onClick = onSpecialist)
            }
        }
    }
}

@Composable
private fun SmallCard(title: String, caption: String, onClick: () -> Unit, tag: String, light: Float) {
    HotCard(cornice = true, modifier = Modifier.fillMaxWidth().clip(HotCardShape).clickable(onClick = onClick).testTag(tag)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LiveTitan(TitanLook(light = light), modifier = Modifier.widthIn(max = 64.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                HotOverline(caption)
            }
        }
    }
}

/** Kept for a primary action inside a card (the deck start on «Сегодня» without WHOOP). */
@Composable
internal fun StartButton(text: String, onClick: () -> Unit) = HotPrimaryButton(text, onClick = onClick, trailingArrow = true, modifier = Modifier.fillMaxWidth())
