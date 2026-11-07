package com.healthofatitan.app.feature.checkin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.healthofatitan.app.core.model.mentor.MentorPersona
import com.healthofatitan.app.core.ui.component.HotBackground
import com.healthofatitan.app.core.ui.component.HotMode
import com.healthofatitan.app.core.ui.component.HotOverline
import com.healthofatitan.app.core.ui.component.HotPrimaryButton
import com.healthofatitan.app.core.ui.component.ModeWord
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.core.ui.theme.HotType
import com.healthofatitan.app.domain.checkin.Agreement
import com.healthofatitan.app.domain.checkin.LineContext
import com.healthofatitan.app.domain.checkin.MentorLine
import com.healthofatitan.app.domain.checkin.Reveal
import com.healthofatitan.app.domain.checkin.TitanLook
import com.healthofatitan.app.domain.engine.Regime

const val REVEAL_TAG = "checkin_reveal"
const val REVEAL_AGREE_TAG = "checkin_reveal_agree"
const val REVEAL_DONE_TAG = "checkin_reveal_done"

internal fun Regime.hotMode(): HotMode = when (this) {
    Regime.BOOST -> HotMode.RAZGON
    Regime.STEADY -> HotMode.ROVNO
    Regime.GENTLE -> HotMode.BEREZHNO
}

@Composable
fun RevealRoute(onDone: () -> Unit, viewModel: CheckinViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { viewModel.loadReveal() }
    val ui by viewModel.reveal.collectAsStateWithLifecycle()
    val mentor by viewModel.mentorPrefs.collectAsStateWithLifecycle()
    ui?.let { RevealScreen(it, mentor.effective, onDone) }
}

/**
 * Раскрытие «ты ↔ WHOOP» (w3, И2.13′): the Titan of the feeling next to the Titan of the data,
 * «сошлись» or «разошлись» (never «wrong»), the mode, one offline line of the character and the
 * counts without streaks. WHOOP late: only «ты» and the waiting line; the A34 push brings it back.
 */
@Composable
fun RevealScreen(ui: RevealUi, persona: MentorPersona, onDone: () -> Unit) {
    val agreement = if (ui.you != null && ui.data != null) Reveal.agreement(ui.you, ui.data) else null
    val ctx = MentorLine.morning(ui.you, ui.data, ui.gentle, ui.quick, ui.whoopConnected)
    val lines = CheckinTexts.line(persona, ctx)
    HotBackground {
      NightContent {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .testTag(REVEAL_TAG),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HotOverline(CheckinTexts.WAKE)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Side(CheckinTexts.YOU, ui.you, Modifier.weight(1f))
                if (ui.data != null) Side(CheckinTexts.WHOOP, ui.data, Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            if (agreement != null) {
                Text(
                    if (agreement == Agreement.MATCH) CheckinTexts.AGREE else CheckinTexts.DIVERGE,
                    style = HotType.headline,
                    modifier = Modifier.semantics { heading() }.testTag(REVEAL_AGREE_TAG),
                )
                if (agreement != Agreement.MATCH) {
                    Spacer(Modifier.height(6.dp))
                    Text(CheckinTexts.DIVERGE_LINE, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
            ui.regime?.let { r ->
                Spacer(Modifier.height(14.dp))
                ModeWord(r.hotMode(), style = MaterialTheme.typography.headlineMedium, glyphSize = 28.dp)
            }
            Spacer(Modifier.height(16.dp))
            Text(lines[MentorLine.pick(lines.size, ui.day)], style = HotType.quote, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            if (ui.facts.total > 0) HotOverline(CheckinTexts.matched(ui.facts.matched, ui.facts.total))
            HotOverline(CheckinTexts.mornings(ui.facts.mornings))
            if (ui.ritualLater && ctx != LineContext.WAITING) {
                Spacer(Modifier.height(10.dp))
                Text(CheckinTexts.RITUAL_LATER, style = MaterialTheme.typography.bodySmall, color = HotThemeTokens.colors.textTertiary, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(22.dp))
            HotPrimaryButton(CheckinTexts.TO_DAY, onClick = onDone, trailingArrow = true, modifier = Modifier.fillMaxWidth().testTag(REVEAL_DONE_TAG))
        }
      }
    }
}

@Composable
private fun Side(who: String, level: Int?, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        LiveTitan(
            TitanLook(light = TitanLook.lightOf(level ?: 1)),
            modifier = Modifier.widthIn(max = 200.dp),
            spoken = level?.let { CheckinTexts.levelSpoken(who, it) },
        )
        HotOverline(who)
        level?.let { Text(CheckinTexts.charge(it), style = MaterialTheme.typography.titleMedium) }
    }
}
