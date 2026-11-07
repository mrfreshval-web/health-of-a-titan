package com.healthofatitan.app.feature.checkin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.healthofatitan.app.core.model.mentor.MentorPersona
import com.healthofatitan.app.core.ui.component.HotOverline
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.core.ui.theme.HotType
import com.healthofatitan.app.domain.checkin.Answer
import com.healthofatitan.app.domain.checkin.BodyArea
import com.healthofatitan.app.domain.checkin.CardId
import com.healthofatitan.app.domain.checkin.DeckEngine
import com.healthofatitan.app.domain.checkin.MentorLine
import com.healthofatitan.app.domain.checkin.Sky
import com.healthofatitan.app.domain.engine.Body
import java.time.LocalDate

const val EVENING_WEATHER_TAG = "checkin_evening_weather"
const val EVENING_KNOWN_TAG = "checkin_evening_known"

/**
 * «Уже знаю — спрашивать не буду» (A44 §1.4, e1): what WHOOP, the phone and «Путь» already told.
 * Stays on top of «Закрыть день».
 */
@Composable
fun EveningKnownLine(modifier: Modifier = Modifier) {
    HotOverline(
        CheckinTexts.alreadyKnow(listOf(CheckinTexts.KNOW_TRAINING, CheckinTexts.KNOW_STEPS, CheckinTexts.KNOW_PATH)),
        modifier = modifier.testTag(EVENING_KNOWN_TAG),
    )
}

/** «Забито → где» under the body row of «Закрыть день» (sc5): zones are saved at once. */
@Composable
fun EveningAreas(day: LocalDate, body: Body?, viewModel: CheckinViewModel = hiltViewModel()) {
    if (body != Body.SORE) return
    LaunchedEffect(day) { viewModel.loadEvening(day) }
    val saved by viewModel.evening.collectAsStateWithLifecycle()
    var zones by remember(saved) {
        mutableStateOf(saved[CardId.EVENING_AREAS]?.choices?.mapNotNull { n -> BodyArea.entries.firstOrNull { it.name == n } }?.toSet().orEmpty())
    }
    EveningRowFrame(CheckinTexts.WHERE) {
        BodyMap(
            zones,
            onToggle = { a ->
                zones = if (a in zones) zones - a else zones + a
                viewModel.saveEvening(day, Answer(CardId.EVENING_AREAS, choices = zones.map { it.name }.toSet()))
            },
            onPain = {},
            compact = true,
        )
    }
}

/**
 * В-3 «Погода вечера» and, on Sunday, the week (A44 §1.4): the same five skies as the morning, the
 * storm net on both, then one line of the character. Placed after «Алкоголь» in «Закрыть день».
 */
@Composable
fun EveningWeather(day: LocalDate, onCare: () -> Unit, viewModel: CheckinViewModel = hiltViewModel()) {
    LaunchedEffect(day) { viewModel.loadEvening(day) }
    LaunchedEffect(Unit) { viewModel.care.collect { onCare() } }
    val saved by viewModel.evening.collectAsStateWithLifecycle()
    val morning by viewModel.morning.collectAsStateWithLifecycle()
    val mentor by viewModel.mentorPrefs.collectAsStateWithLifecycle()
    val settings = morning?.settings ?: return
    val cards = DeckEngine.evening(settings, day)
    if (cards.isEmpty()) return
    EveningWeatherContent(
        day = day,
        cards = cards,
        saved = saved,
        persona = mentor.effective,
        haptics = settings.haptics,
        onAnswer = { viewModel.saveEvening(day, it) },
        onCloseness = { viewModel.saveCloseness(day, it) },
    )
}

@Composable
fun EveningWeatherContent(
    day: LocalDate,
    cards: List<CardId>,
    saved: Map<CardId, Answer>,
    persona: MentorPersona,
    haptics: Boolean = true,
    onAnswer: (Answer) -> Unit = {},
    onCloseness: (Int) -> Unit = {},
) {
    var sky by remember(saved) { mutableStateOf(Sky.of(saved[CardId.EVENING_WEATHER]?.level)) }
    var people by remember(saved) { mutableStateOf(saved[CardId.WEEK_PEOPLE]?.level) }
    var closeness by remember { mutableStateOf<Int?>(null) }
    var unlocked by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (CardId.EVENING_WEATHER in cards) {
            EveningRowFrame(CheckinTexts.title(CardId.EVENING_WEATHER), Modifier.testTag(EVENING_WEATHER_TAG)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Sky.entries.forEach { s ->
                        AnswerTile(CheckinTexts.sky(s), sky == s, onClick = {
                            sky = s
                            onAnswer(Answer(CardId.EVENING_WEATHER, level = s.level))
                        }, haptics = haptics, minHeight = 72, modifier = Modifier.weight(1f).testTag(answerTag(CardId.EVENING_WEATHER, s.name))) { c ->
                            SkyGlyph(s, c, size = 24.dp)
                        }
                    }
                }
                sky?.let { s ->
                    val lines = CheckinTexts.line(persona, MentorLine.evening(s))
                    Spacer(Modifier.height(8.dp))
                    Text(lines[MentorLine.pick(lines.size, day)], style = HotType.quote, textAlign = TextAlign.Start)
                }
            }
        }
        if (CardId.WEEK_PEOPLE in cards) {
            EveningRowFrame("${CheckinTexts.WEEK} · ${CheckinTexts.title(CardId.WEEK_PEOPLE)}") {
                Scale(people, CheckinTexts.WEEK_SCALE_LOW, CheckinTexts.WEEK_SCALE_HIGH) { v ->
                    people = v
                    onAnswer(Answer(CardId.WEEK_PEOPLE, level = v))
                }
            }
        }
        if (CardId.WEEK_CLOSENESS in cards) {
            EveningRowFrame(CheckinTexts.title(CardId.WEEK_CLOSENESS)) {
                if (!unlocked) {
                    OnlyYouLock(onUnlocked = { unlocked = true })
                } else if (closeness == null) {
                    Scale(null, CheckinTexts.CLOSENESS_LOW, CheckinTexts.CLOSENESS_HIGH) { v ->
                        closeness = v
                        onCloseness(v)
                    }
                } else {
                    HotOverline(CheckinTexts.ACCEPTED)
                }
            }
        }
    }
}

/** A 1–5 row with the two ends named (satisfaction, never frequency). */
@Composable
private fun Scale(value: Int?, low: String, high: String, onPick: (Int) -> Unit) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (v in 1..5) {
                CheckinChip("$v", value == v, onClick = { onPick(v) }, modifier = Modifier.weight(1f))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            HotOverline(low, modifier = Modifier.weight(1f))
            HotOverline(high)
        }
    }
}

/** The look of a «Закрыть день» row (A37 s5), so the additions sit in the same rhythm. */
@Composable
fun EveningRowFrame(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, HotThemeTokens.colors.hairline),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}
