package com.healthofatitan.app.feature.checkin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.healthofatitan.app.core.model.mentor.MentorPersona
import com.healthofatitan.app.core.ui.component.HotBackground
import com.healthofatitan.app.core.ui.component.HotCard
import com.healthofatitan.app.core.ui.component.HotOverline
import com.healthofatitan.app.core.ui.component.HotPrimaryButton
import com.healthofatitan.app.core.ui.component.HotTextButton
import com.healthofatitan.app.core.ui.text.LocalAddressForm
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.core.ui.theme.HotType
import com.healthofatitan.app.domain.checkin.Discovery
import com.healthofatitan.app.domain.checkin.Link
import com.healthofatitan.app.domain.checkin.Tile
import com.healthofatitan.app.domain.checkin.TitanLook
import java.util.Locale
import kotlin.math.abs

const val DISCOVERY_TAG = "checkin_discovery"
const val COLLECTION_TAG = "checkin_collection"

@Composable
fun DiscoveryRoute(onClose: () -> Unit, viewModel: CheckinViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { viewModel.loadDiscovery() }
    val d by viewModel.discovery.collectAsStateWithLifecycle()
    val tiles by viewModel.collection.collectAsStateWithLifecycle()
    val mentor by viewModel.mentorPrefs.collectAsStateWithLifecycle()
    DiscoveryScreen(d, tiles, mentor.effective, onClose)
}

/**
 * Открытие (w6, И2.21): the card-reward with the personal link, the number, n «с» and «без» and
 * «связь, не причина»; «Как считал» opens the method; the collection of twelve below it. A gentle
 * (negative) link is shown softly, never in Бережно (the engine filters it).
 */
@Composable
fun DiscoveryScreen(d: Discovery?, tiles: List<Tile>, persona: MentorPersona, onClose: () -> Unit) {
    var how by remember { mutableStateOf(false) }
    HotBackground {
      NightContent {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .testTag(DISCOVERY_TAG),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (d != null) {
                val number = tiles.count { it is Tile.Open }.coerceAtLeast(1)
                HotOverline(if (d.positive) CheckinTexts.discoveryNumber(number) else CheckinTexts.GENTLE_DISCOVERY)
                Spacer(Modifier.height(10.dp))
                LiveTitan(TitanLook(light = if (d.positive) 0.95f else 0.6f), modifier = Modifier.widthIn(max = 220.dp))
                HotCard(cornice = true, modifier = Modifier.fillMaxWidth()) {
                    Text(CheckinTexts.pairTitle(d.pair), style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        (if (d.effect > 0) "+" else "−") + String.format(Locale.forLanguageTag("ru"), "%.1f", abs(d.effect)),
                        style = HotType.display,
                        color = if (d.positive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(CheckinTexts.pairLine(d.pair, d.effect), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(6.dp))
                    HotOverline(CheckinTexts.counts(d.n1, d.n0))
                    Spacer(Modifier.height(10.dp))
                    Text(CheckinTexts.discoveryLine(persona, LocalAddressForm.current), style = HotType.quote)
                    HotTextButton(CheckinTexts.HOW_COUNTED, onClick = { how = !how })
                    AnimatedVisibility(how) {
                        Text(CheckinTexts.howCounted(d.n1, d.n0, d.ciLow, d.ciHigh), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
            Collection(tiles)
            Spacer(Modifier.height(20.dp))
            HotPrimaryButton(CheckinTexts.CLOSE, onClick = onClose, modifier = Modifier.fillMaxWidth())
        }
      }
    }
}

/** The collection: twelve tiles — open, «ещё N утр» with a ring that never resets, or the lock. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Collection(tiles: List<Tile>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().testTag(COLLECTION_TAG)) {
        HotOverline("${CheckinTexts.COLLECTION} · ${CheckinTexts.opened(tiles.count { it is Tile.Open }, Link.entries.size)}")
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 3) {
            tiles.forEach { t -> TileView(t, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun TileView(t: Tile, modifier: Modifier) {
    val tokens = HotThemeTokens.colors
    val open = t is Tile.Open
    val spoken = when (t) {
        is Tile.Open -> CheckinTexts.pairTitle(t.pair) + ": " + CheckinTexts.pairLine(t.pair, t.discovery.effect)
        is Tile.Progress -> CheckinTexts.pairTitle(t.pair) + ": " + CheckinTexts.leftMornings(t.left)
        is Tile.Locked -> CheckinTexts.LOCKED
    }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (open) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, if (open) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else tokens.rule),
        modifier = modifier.aspectRatio(0.9f).semantics(mergeDescendants = true) { contentDescription = spoken },
    ) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            when (t) {
                is Tile.Locked -> LockGlyph(MaterialTheme.colorScheme.onSurfaceVariant, size = 28.dp)
                is Tile.Progress -> Box(contentAlignment = Alignment.Center) {
                    val ink = MaterialTheme.colorScheme.primary
                    Canvas(Modifier.size(36.dp)) {
                        drawCircle(tokens.rule, style = Stroke(3.dp.toPx()))
                        drawArc(ink, -90f, 360f * t.fraction, false, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                    }
                }
                is Tile.Open -> LiveTitan(TitanLook(light = 0.9f), modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                when (t) {
                    is Tile.Locked -> CheckinTexts.LOCKED
                    else -> CheckinTexts.pairTitle(t.pair)
                },
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 3,
            )
            if (t is Tile.Progress && t.left > 0) Text(CheckinTexts.leftMornings(t.left), style = MaterialTheme.typography.labelSmall, color = tokens.textTertiary, textAlign = TextAlign.Center)
        }
    }
}
