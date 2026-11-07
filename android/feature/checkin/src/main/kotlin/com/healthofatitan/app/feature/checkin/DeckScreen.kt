package com.healthofatitan.app.feature.checkin

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.healthofatitan.app.core.model.mentor.MentorPersona
import com.healthofatitan.app.core.ui.R
import com.healthofatitan.app.core.ui.component.HotBackground
import com.healthofatitan.app.core.ui.component.HotCard
import com.healthofatitan.app.core.ui.component.HotOverline
import com.healthofatitan.app.core.ui.component.HotPrimaryButton
import com.healthofatitan.app.core.ui.component.HotTextButton
import com.healthofatitan.app.core.ui.component.SecureScreen
import com.healthofatitan.app.core.ui.text.text
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.core.ui.theme.HotType
import com.healthofatitan.app.domain.checkin.Answer
import com.healthofatitan.app.domain.checkin.BodyArea
import com.healthofatitan.app.domain.checkin.BodyFeel
import com.healthofatitan.app.domain.checkin.CardId
import com.healthofatitan.app.domain.checkin.Charge
import com.healthofatitan.app.domain.checkin.Drive
import com.healthofatitan.app.domain.checkin.Head
import com.healthofatitan.app.domain.checkin.MentorLine
import com.healthofatitan.app.domain.checkin.Mood
import com.healthofatitan.app.domain.checkin.Phase
import com.healthofatitan.app.domain.checkin.PrivateAnswer
import com.healthofatitan.app.domain.checkin.RitualItem
import com.healthofatitan.app.domain.checkin.Sky
import com.healthofatitan.app.domain.checkin.Stack
import com.healthofatitan.app.domain.checkin.TitanLook
import com.healthofatitan.app.domain.checkin.Yesterday
import java.time.format.DateTimeFormatter

const val DECK_TAG = "checkin_deck"
const val DECK_QUICK_TAG = "checkin_deck_quick"
const val DECK_SKIP_TAG = "checkin_deck_skip"
const val DECK_BACK_TAG = "checkin_deck_back"
const val DECK_CLOSE_TAG = "checkin_deck_close"
const val DECK_NEXT_TAG = "checkin_deck_next"
const val DELTA_TAG = "checkin_ritual_delta"

fun answerTag(card: CardId, value: String) = "checkin_${card.key}_$value"

/** Callbacks of the deck screen (stateless, so the screenshots render it without Hilt). */
class DeckActions(
    val onAnswer: (Answer) -> Unit = {},
    val onPrivate: (PrivateAnswer) -> Unit = {},
    val onUnlock: () -> Unit = {},
    val onSkip: () -> Unit = {},
    val onBack: () -> Unit = {},
    val onQuick: () -> Unit = {},
    val onClose: () -> Unit = {},
    val onAddStack: (String) -> Unit = {},
)

@Composable
fun DeckRoute(
    phase: Phase,
    persona: MentorPersona,
    onClose: () -> Unit,
    onReveal: () -> Unit,
    onCare: () -> Unit,
    viewModel: DeckViewModel = hiltViewModel(),
) {
    LaunchedEffect(phase) { viewModel.start(phase) }
    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            when (e) {
                DeckEvent.Reveal -> onReveal()
                DeckEvent.Care -> onCare()
            }
        }
    }
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val state = ui ?: return
    DeckScreen(
        ui = state,
        persona = persona,
        actions = DeckActions(
            onAnswer = viewModel::answer,
            onPrivate = viewModel::answerPrivate,
            onUnlock = viewModel::unlocked,
            onSkip = viewModel::skip,
            onBack = viewModel::back,
            onQuick = viewModel::quick,
            onClose = {
                viewModel.leave()
                onClose()
            },
            onAddStack = viewModel::addToStack,
        ),
    )
}

/**
 * The deck (w1, w2, w4): the overline with the step and the seconds left, the live Titan, the
 * chain of answers, one card at a time, «назад» and «пропустить», «Быстро» always visible.
 * FLAG_SECURE while «Только ты» is on screen.
 */
@Composable
fun DeckScreen(ui: DeckUi, persona: MentorPersona = MentorPersona.DEFAULT, actions: DeckActions = DeckActions()) {
    val card = ui.deck.current
    SecureScreen(enabled = card?.private == true) {
        HotBackground {
          NightContent {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .testTag(DECK_TAG),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    HotOverline(
                        if (ui.phase == Phase.RITUAL) CheckinTexts.RITUAL else CheckinTexts.progress(ui.deck.index.coerceAtMost(ui.deck.cards.size - 1), ui.deck.cards.size),
                        modifier = Modifier.weight(1f),
                    )
                    if (!ui.deck.done) HotOverline(CheckinTexts.left(ui.deck.remainingSeconds))
                    Spacer(Modifier.width(6.dp))
                    Surface(onClick = actions.onClose, shape = CircleShape, color = Color.Transparent, modifier = Modifier.size(44.dp).testTag(DECK_CLOSE_TAG)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(painterResource(R.drawable.core_ui_ic_close), contentDescription = CheckinTexts.CLOSE, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (ui.phase == Phase.RITUAL && ui.deck.done) {
                    RitualDelta(ui, persona, onDone = actions.onClose)
                    return@Column
                }
                LiveTitan(
                    look = if (card?.private == true) TitanLook(light = ui.look.light) else ui.look,
                    modifier = Modifier.widthIn(max = 330.dp).align(Alignment.CenterHorizontally),
                    spoken = CheckinTexts.TAP_HINT,
                )
                AnswerChain(ui)
                Spacer(Modifier.height(14.dp))
                AnimatedContent(
                    targetState = card,
                    transitionSpec = {
                        (fadeIn(tween(150)) + slideInVertically(tween(150)) { it / 6 }) togetherWith (fadeOut(tween(120)) + slideOutVertically(tween(120)) { -it / 6 })
                    },
                    label = "card",
                ) { current ->
                    if (current != null) DeckCard(current, ui, actions)
                }
                Spacer(Modifier.height(14.dp))
                if (ui.phase == Phase.WAKE && !ui.deck.done) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (ui.deck.cards != com.healthofatitan.app.domain.checkin.DeckEngine.QUICK) {
                            Surface(
                                onClick = actions.onQuick,
                                shape = CircleShape,
                                color = Color.Transparent,
                                border = BorderStroke(1.dp, HotThemeTokens.colors.rule),
                                modifier = Modifier.testTag(DECK_QUICK_TAG),
                            ) {
                                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    QuickGlyph(MaterialTheme.colorScheme.onSurface)
                                    Spacer(Modifier.width(8.dp))
                                    Text(CheckinTexts.QUICK, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                        }
                        CheckinTexts.nextUp(ui.deck.next)?.let { HotOverline(it, modifier = Modifier.weight(1f)) }
                    }
                }
            }
          }
        }
    }
}

/** The chain of answered tokens under the Titan (w1): Заряд cells, the sky, the zones. */
@Composable
private fun AnswerChain(ui: DeckUi) {
    val answers = ui.deck.answers.values.filter { !it.skipped && !it.card.private }
    if (answers.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        answers.forEach { a ->
            when (a.card) {
                CardId.CHARGE, CardId.CHARGE_AFTER -> a.level?.let { l -> AnswerToken(CheckinTexts.charge(l)) { c -> ChargeGlyph(l, c, size = 18.dp) } }
                CardId.WEATHER -> Sky.of(a.level)?.let { s -> AnswerToken(CheckinTexts.sky(s)) { c -> SkyGlyph(s, c, size = 18.dp) } }
                CardId.BODY -> BodyFeel.of(a.level)?.let { b ->
                    val zones = a.choices.mapNotNull { n -> BodyArea.entries.firstOrNull { it.name == n } }
                    val text = if (zones.isEmpty()) CheckinTexts.body(b) else zones.joinToString(", ") { CheckinTexts.bodyArea(it) }
                    AnswerToken(text) { c ->
                        BodyGlyph(b, c, size = 18.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(text, style = MaterialTheme.typography.labelMedium, color = if (b == BodyFeel.SORE) MaterialTheme.colorScheme.primary else c)
                    }
                }
                CardId.HEAD -> Head.of(a.level)?.let { h -> AnswerToken(CheckinTexts.head(h)) { c -> Text(CheckinTexts.head(h), style = MaterialTheme.typography.labelMedium, color = c) } }
                CardId.DRIVE -> Drive.of(a.level)?.let { d -> AnswerToken(CheckinTexts.drive(d)) { c -> DriveGlyph(d, c, size = 18.dp) } }
                else -> Unit
            }
        }
    }
}

@Composable
private fun DeckCard(card: CardId, ui: DeckUi, actions: DeckActions) {
    val chosen = ui.deck.answers[card]?.takeIf { !it.skipped }
    HotCard(cornice = true, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(CheckinTexts.title(card), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).semantics { heading() })
            Dots(ui.deck.index, ui.deck.cards.size)
        }
        Spacer(Modifier.height(14.dp))
        val haptics = ui.settings.haptics
        when (card) {
            CardId.CHARGE, CardId.CHARGE_AFTER, CardId.SLEPT -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (l in Charge.MIN..Charge.MAX) {
                    val label = if (card == CardId.SLEPT) CheckinTexts.SLEPT_LEVELS[l - 1] else CheckinTexts.charge(l)
                    AnswerTile(label, chosen?.level == l, onClick = { actions.onAnswer(Answer(card, level = l)) }, haptics = haptics, modifier = Modifier.weight(1f).testTag(answerTag(card, "$l"))) { c ->
                        ChargeGlyph(l, c, size = 30.dp)
                    }
                }
            }
            CardId.WEATHER -> WeatherBody(card, chosen, haptics, actions)
            CardId.BODY -> BodyBody(chosen, haptics, actions)
            CardId.HEAD -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Head.entries.forEach { h ->
                    AnswerTile(CheckinTexts.head(h), chosen?.level == h.level, onClick = { actions.onAnswer(Answer(card, level = h.level)) }, haptics = haptics, minHeight = 150, modifier = Modifier.weight(1f).testTag(answerTag(card, h.name))) { _ ->
                        HeadTitan(blur = when (h) { Head.FOG -> 1f; Head.MID -> 0.4f; Head.CLEAR -> 0f })
                    }
                }
            }
            CardId.DRIVE -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Drive.entries.forEach { d ->
                    AnswerTile(CheckinTexts.drive(d), chosen?.level == d.level, onClick = { actions.onAnswer(Answer(card, level = d.level)) }, haptics = haptics, modifier = Modifier.weight(1f).testTag(answerTag(card, d.name))) { c ->
                        DriveGlyph(d, c, size = 34.dp)
                    }
                }
            }
            CardId.YESTERDAY -> YesterdayBody(ui, chosen, haptics, actions)
            CardId.ONLY_YOU -> if (ui.unlocked) {
                OnlyYouContent(ui.settings.privateLayer, haptics, onDone = actions.onPrivate)
            } else {
                OnlyYouLock(onUnlocked = actions.onUnlock)
            }
            CardId.RITUAL -> RitualBody(ui, chosen, haptics, actions)
            else -> Unit
        }
        Spacer(Modifier.height(10.dp))
        HotOverline(CheckinTexts.TAP_HINT, modifier = Modifier.align(Alignment.CenterHorizontally))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (ui.deck.index > 0) HotTextButton("← ${CheckinTexts.BACK}", onClick = actions.onBack, modifier = Modifier.testTag(DECK_BACK_TAG))
            Spacer(Modifier.weight(1f))
            HotTextButton("↑ ${CheckinTexts.SKIP}", onClick = actions.onSkip, modifier = Modifier.testTag(DECK_SKIP_TAG))
        }
    }
}

@Composable
private fun Dots(index: Int, total: Int) {
    val tokens = HotThemeTokens.colors
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(total) { i ->
            Surface(
                shape = CircleShape,
                color = if (i <= index) MaterialTheme.colorScheme.onSurface.copy(alpha = if (i == index) 0.9f else 0.6f) else tokens.rule,
                modifier = Modifier.size(width = if (i == index) 18.dp else 6.dp, height = 6.dp),
            ) {}
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeatherBody(card: CardId, chosen: Answer?, haptics: Boolean, actions: DeckActions) {
    var words by remember { mutableStateOf(false) }
    var sky by remember { mutableStateOf(Sky.of(chosen?.level)) }
    var moods by remember { mutableStateOf(chosen?.choices.orEmpty()) }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Sky.entries.forEach { s ->
            AnswerTile(
                CheckinTexts.sky(s),
                sky == s,
                onClick = {
                    sky = s
                    if (!words) actions.onAnswer(Answer(card, level = s.level))
                },
                haptics = haptics,
                modifier = Modifier.weight(1f).testTag(answerTag(card, s.name)),
            ) { c -> SkyGlyph(s, c, size = 30.dp) }
        }
    }
    Spacer(Modifier.height(10.dp))
    if (!words) {
        HotTextButton(CheckinTexts.IN_WORDS, onClick = { words = true })
    } else {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Mood.entries.forEach { m ->
                CheckinChip(CheckinTexts.mood(m), m.name in moods, onClick = { moods = if (m.name in moods) moods - m.name else moods + m.name })
            }
        }
        Spacer(Modifier.height(10.dp))
        HotPrimaryButton(
            CheckinTexts.NEXT,
            enabled = sky != null,
            onClick = { sky?.let { actions.onAnswer(Answer(card, level = it.level, choices = moods)) } },
            modifier = Modifier.fillMaxWidth().testTag(DECK_NEXT_TAG),
        )
    }
}

@Composable
private fun BodyBody(chosen: Answer?, haptics: Boolean, actions: DeckActions) {
    var sore by remember { mutableStateOf(BodyFeel.of(chosen?.level) == BodyFeel.SORE) }
    var zones by remember { mutableStateOf(chosen?.choices?.mapNotNull { n -> BodyArea.entries.firstOrNull { it.name == n } }?.toSet().orEmpty()) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(BodyFeel.LIGHT, BodyFeel.SORE, BodyFeel.SICK).forEach { b ->
            AnswerTile(
                CheckinTexts.body(b),
                if (b == BodyFeel.SORE) sore else BodyFeel.of(chosen?.level) == b,
                onClick = {
                    if (b == BodyFeel.SORE) sore = true else actions.onAnswer(Answer(CardId.BODY, level = b.level))
                },
                haptics = haptics,
                modifier = Modifier.weight(1f).testTag(answerTag(CardId.BODY, b.name)),
            ) { c -> BodyGlyph(b, c, size = 34.dp) }
        }
    }
    if (sore) {
        Spacer(Modifier.height(12.dp))
        BodyMap(zones, onToggle = { a -> zones = if (a in zones) zones - a else zones + a }, onPain = {}, compact = true)
        Spacer(Modifier.height(8.dp))
        HotPrimaryButton(
            CheckinTexts.NEXT,
            onClick = { actions.onAnswer(Answer(CardId.BODY, level = BodyFeel.SORE.level, choices = zones.map { it.name }.toSet())) },
            modifier = Modifier.fillMaxWidth().testTag(DECK_NEXT_TAG),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun YesterdayBody(ui: DeckUi, chosen: Answer?, haptics: Boolean, actions: DeckActions) {
    var picked by remember { mutableStateOf(chosen?.choices.orEmpty()) }
    var servings by remember { mutableStateOf(chosen?.count) }
    val options = ui.yesterdayOptions
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 3) {
        options.forEach { y ->
            AnswerTile(
                CheckinTexts.yesterday(y),
                y.name in picked,
                onClick = {
                    picked = when {
                        y == Yesterday.NOTHING -> if (y.name in picked) emptySet() else setOf(y.name)
                        y.name in picked -> picked - y.name
                        else -> picked - Yesterday.NOTHING.name + y.name
                    }
                    if (Yesterday.ALCOHOL.name !in picked) servings = null
                },
                haptics = haptics,
                minHeight = 88,
                modifier = Modifier.weight(1f).testTag(answerTag(CardId.YESTERDAY, y.name)),
            ) { c -> YesterdayGlyph(y, c, size = 30.dp) }
        }
    }
    if (Yesterday.ALCOHOL.name in picked) {
        Spacer(Modifier.height(12.dp))
        Text(CheckinTexts.HOW_MANY, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CheckinTexts.SERVINGS.forEach { (n, label) ->
                CheckinChip(label, servings == n, onClick = { servings = n }, modifier = Modifier.widthIn(min = 56.dp))
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    HotPrimaryButton(
        CheckinTexts.NEXT,
        enabled = picked.isNotEmpty(),
        onClick = { actions.onAnswer(Answer(CardId.YESTERDAY, choices = picked, count = servings.takeIf { Yesterday.ALCOHOL.name in picked })) },
        modifier = Modifier.fillMaxWidth().testTag(DECK_NEXT_TAG),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RitualBody(ui: DeckUi, chosen: Answer?, haptics: Boolean, actions: DeckActions) {
    var picked by remember(ui.prefill) { mutableStateOf(chosen?.choices ?: ui.prefill) }
    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    val own = (ui.settings.stack.map(Stack::own) + picked.filter(Stack::isOwn)).distinct()
    if (ui.prefill.isNotEmpty()) {
        HotPrimaryButton(
            CheckinTexts.AS_YESTERDAY,
            onClick = { actions.onAnswer(Answer(CardId.RITUAL, choices = ui.prefill)) },
            modifier = Modifier.fillMaxWidth().testTag(answerTag(CardId.RITUAL, "as_yesterday")),
        )
        Spacer(Modifier.height(12.dp))
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 3) {
        RitualItem.entries.forEach { r ->
            AnswerTile(CheckinTexts.ritual(r), r.name in picked, onClick = { picked = if (r.name in picked) picked - r.name else picked + r.name }, haptics = haptics, minHeight = 80, modifier = Modifier.weight(1f)) { c ->
                RitualGlyph(r, c, size = 28.dp)
            }
        }
        own.forEach { code ->
            AnswerTile(Stack.nameOf(code), code in picked, onClick = { picked = if (code in picked) picked - code else picked + code }, haptics = haptics, minHeight = 80, modifier = Modifier.weight(1f)) { c ->
                StackGlyph(c, size = 28.dp)
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(CheckinTexts.STACK_HINT.text(), style = MaterialTheme.typography.bodySmall, color = HotThemeTokens.colors.textTertiary)
    if (!adding) {
        HotTextButton("+ ${CheckinTexts.ADD_TO_STACK}", onClick = { adding = true })
    } else {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(40) },
            singleLine = true,
            placeholder = { Text(CheckinTexts.STACK_NAME_HINT) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                actions.onAddStack(name)
                if (name.isNotBlank()) picked = picked + Stack.own(name)
                name = ""
                adding = false
            }),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Spacer(Modifier.height(10.dp))
    HotPrimaryButton(CheckinTexts.NEXT, onClick = { actions.onAnswer(Answer(CardId.RITUAL, choices = picked)) }, modifier = Modifier.fillMaxWidth().testTag(DECK_NEXT_TAG))
}

/** w4: two Titans «6:41 → 7:41», the delta in levels, the honest note on sleep inertia. */
@Composable
private fun RitualDelta(ui: DeckUi, persona: MentorPersona, onDone: () -> Unit) {
    val wake = ui.wakeCharge
    val now = ui.deck.answers[CardId.CHARGE_AFTER]?.takeIf { !it.skipped }?.level
    val fmt = DateTimeFormatter.ofPattern("H:mm")
    val after = ui.deck.answers[CardId.CHARGE_AFTER]?.atMs?.takeIf { it > 0 }?.let { java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(it), java.time.ZoneId.systemDefault()) }
    Column(Modifier.fillMaxWidth().testTag(DELTA_TAG), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                LiveTitan(TitanLook(light = TitanLook.lightOf(wake ?: 1)), modifier = Modifier.widthIn(max = 150.dp))
                HotOverline(listOfNotNull(ui.wakeAt?.format(fmt), wake?.let(CheckinTexts::charge)).joinToString(" · "))
            }
            Text("→", style = HotType.numeral, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                LiveTitan(TitanLook(light = TitanLook.lightOf(now ?: 1)), modifier = Modifier.widthIn(max = 150.dp))
                HotOverline(listOfNotNull(after?.format(fmt), now?.let(CheckinTexts::charge)).joinToString(" · "))
            }
        }
        ui.delta?.let { d ->
            Text(CheckinTexts.deltaLevels(d), style = HotType.headline, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        Spacer(Modifier.height(8.dp))
        val line = CheckinTexts.line(persona, MentorLine.ritual(ui.delta))
        Text(line[MentorLine.pick(line.size, ui.day)], style = HotType.quote, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(CheckinTexts.DELTA_NOTE, style = MaterialTheme.typography.bodySmall, color = HotThemeTokens.colors.textTertiary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        HotPrimaryButton(CheckinTexts.DONE, onClick = onDone, modifier = Modifier.fillMaxWidth())
    }
}
