package com.healthofatitan.app.feature.mentor

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.healthofatitan.app.core.model.mentor.MentorPersona
import com.healthofatitan.app.core.model.nav.HotRoute
import com.healthofatitan.app.core.ui.R
import com.healthofatitan.app.core.ui.component.HotCard
import com.healthofatitan.app.core.ui.component.HotPrimaryButton
import com.healthofatitan.app.core.ui.component.HotSpacing
import com.healthofatitan.app.core.ui.component.HotTextButton
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.domain.mentor.Speaker
import com.healthofatitan.app.feature.mentor.data.TalkSummary

/**
 * The conversation with Винс (A62, D-056): opened by the circle in the header (D-038). In this
 * slice: chat (A62 §3 «по умолчанию — чат»), three characters, level 1 and the chronicle of
 * summaries, the crisis protocol on every message.
 *
 * @param stateful false draws the screen from its default state without a ViewModel (navigation
 *   tests run without Hilt).
 */
fun NavGraphBuilder.mentorScreen(stateful: Boolean = true, onOpenHelp: () -> Unit = {}) {
    composable(route = HotRoute.MENTOR) {
        if (stateful) {
            MentorRoute(onOpenHelp = onOpenHelp)
        } else {
            val persona = MentorPersona.DEFAULT
            MentorScreen(
                ui = MentorUi(persona = persona, lines = listOf(UiLine(Speaker.MENTOR, MentorTexts.greeting(persona)))),
                summaries = emptyList(),
                actions = MentorActions(onOpenHelp = onOpenHelp),
            )
        }
    }
}

const val MENTOR_SCREEN_TAG = "screen_mentor"
const val MENTOR_MENU_TAG = "mentor_menu"
const val MENTOR_HELP_TAG = "mentor_menu_help"
const val MENTOR_INPUT_TAG = "mentor_input"
const val MENTOR_SEND_TAG = "mentor_send"
const val MENTOR_CRISIS_TAG = "mentor_crisis_card"
const val MENTOR_CRISIS_HELP_TAG = "mentor_crisis_help"
const val MENTOR_EMERGENCY_TAG = "mentor_emergency_card"
const val MENTOR_HELP_CHIP_TAG = "mentor_help_chip"
const val MENTOR_SUBTITLE_TAG = "mentor_subtitle"

/** G2: one line on the first visit. Review: Agent 12 (copy), Agent 10 (tone). */
const val MENTOR_INTRO = MentorTexts.DISCLOSURE
private const val MENTOR_PREFS = "hot_mentor_ui"
private const val INTRO_SEEN = "intro_seen"

data class MentorActions(
    val onSend: (String) -> Unit = {},
    val onOpenHelp: () -> Unit = {},
    val onHelpOpened: () -> Unit = {},
    val onNewConversation: () -> Unit = {},
    val onForgetAll: () -> Unit = {},
)

@Composable
private fun MentorRoute(onOpenHelp: () -> Unit, viewModel: MentorViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val summaries by viewModel.summaries.collectAsStateWithLifecycle()
    MentorScreen(
        ui = ui,
        summaries = summaries,
        actions = MentorActions(
            onSend = viewModel::send,
            onOpenHelp = onOpenHelp,
            onHelpOpened = viewModel::helpOpened,
            onNewConversation = viewModel::newConversation,
            onForgetAll = viewModel::forgetAll,
        ),
    )
}

@Composable
fun MentorScreen(ui: MentorUi, summaries: List<TalkSummary>, actions: MentorActions, modifier: Modifier = Modifier) {
    // CrisisGuard ≥ 2 or the model's marker: «Помощь» opens by itself (A07 §4.1, D-029).
    LaunchedEffect(ui.openHelp) {
        if (ui.openHelp) {
            actions.onHelpOpened()
            actions.onOpenHelp()
        }
    }
    val context = LocalContext.current
    val firstVisit = rememberSaveable {
        val prefs = context.getSharedPreferences(MENTOR_PREFS, Context.MODE_PRIVATE)
        val first = !prefs.getBoolean(INTRO_SEEN, false)
        if (first) prefs.edit().putBoolean(INTRO_SEEN, true).apply()
        first
    }
    var memoryOpen by rememberSaveable { mutableStateOf(false) }
    val list = rememberLazyListState()
    LaunchedEffect(ui.lines.size, ui.sending) {
        val count = ui.lines.size + if (ui.sending) 1 else 0
        if (count > 0) list.animateScrollToItem(count - 1)
    }

    Column(modifier.fillMaxSize().imePadding().testTag(MENTOR_SCREEN_TAG)) {
        Header(ui, actions, onMemory = { memoryOpen = true })
        if (firstVisit) {
            Text(
                MentorTexts.disclosure(ui.persona),
                style = MaterialTheme.typography.bodySmall,
                color = HotThemeTokens.colors.textTertiary,
                modifier = Modifier.padding(horizontal = HotSpacing.edge, vertical = 4.dp),
            )
        }
        LazyColumn(
            state = list,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(ui.lines) { line ->
                when (line.kind) {
                    LineKind.CRISIS -> CrisisCard(line.text, actions.onOpenHelp)
                    LineKind.EMERGENCY -> CrisisCard(line.text, actions.onOpenHelp, tag = MENTOR_EMERGENCY_TAG)
                    LineKind.NOTICE -> Notice(line.text)
                    LineKind.TALK -> Bubble(line)
                }
            }
            if (ui.sending) {
                item { Notice(MentorTexts.THINKING) }
            }
        }
        if (ui.helpVisible) {
            // М2/М10: after CrisisGuard ≥ 1, in the crisis window or after an emergency «Помощь» stays in sight.
            HotTextButton(
                MentorTexts.HELP_CHIP,
                onClick = actions.onOpenHelp,
                modifier = Modifier.padding(horizontal = 12.dp).testTag(MENTOR_HELP_CHIP_TAG),
            )
        }
        InputRow(enabled = !ui.sending, hint = MentorTexts.inputHint(ui.persona), onSend = actions.onSend)
    }

    if (memoryOpen) {
        MemoryDialog(summaries, onForget = actions.onForgetAll, onClose = { memoryOpen = false })
    }
}

@Composable
private fun Header(ui: MentorUi, actions: MentorActions, onMemory: () -> Unit) {
    var menu by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = HotSpacing.edge, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VinceFace(ui.persona, size = 40.dp, thinking = ui.sending, care = ui.crisis)
        Spacer(Modifier.width(12.dp))
        // The name and, always under it on this screen, «ИИ-наставник» (М6 ревью 08.11); the longer
        // disclosure stays the first-visit line.
        Column(Modifier.weight(1f)) {
            Text(
                MentorTexts.name(ui.persona),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                MentorTexts.SUBTITLE,
                style = MaterialTheme.typography.bodySmall,
                color = HotThemeTokens.colors.textTertiary,
                modifier = Modifier.testTag(MENTOR_SUBTITLE_TAG),
            )
        }
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.testTag(MENTOR_MENU_TAG)) {
                Icon(painterResource(R.drawable.core_ui_ic_more), contentDescription = "Меню", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                // Help first: two taps from the chat (D-029, T-06).
                DropdownMenuItem(
                    text = { Text(MentorTexts.MENU_HELP, style = MaterialTheme.typography.bodyLarge) },
                    onClick = { menu = false; actions.onOpenHelp() },
                    modifier = Modifier.testTag(MENTOR_HELP_TAG),
                )
                DropdownMenuItem(
                    text = { Text(MentorTexts.MENU_NEW, style = MaterialTheme.typography.bodyLarge) },
                    onClick = { menu = false; actions.onNewConversation() },
                )
                DropdownMenuItem(
                    text = { Text(MentorTexts.MENU_MEMORY, style = MaterialTheme.typography.bodyLarge) },
                    onClick = { menu = false; onMemory() },
                )
            }
        }
    }
}

@Composable
private fun Bubble(line: UiLine) {
    val mine = line.speaker == Speaker.PERSON
    val tokens = HotThemeTokens.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Surface(
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = if (mine) 18.dp else 4.dp, bottomEnd = if (mine) 4.dp else 18.dp),
            color = if (mine) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            border = if (mine) null else BorderStroke(1.dp, tokens.hairline),
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Text(
                line.text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun Notice(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = HotThemeTokens.colors.textTertiary,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/**
 * The crisis line of A62 block 7 §3 (or the emergency line of `[[SOS_MED]]`) and the way to
 * «Помощь» (112 · 911), the same for every character.
 */
@Composable
private fun CrisisCard(text: String, onOpenHelp: () -> Unit, tag: String = MENTOR_CRISIS_TAG) {
    HotCard(modifier = Modifier.fillMaxWidth().testTag(tag)) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        )
        Spacer(Modifier.size(12.dp))
        HotPrimaryButton(MentorTexts.CRISIS_BUTTON, onClick = onOpenHelp, modifier = Modifier.fillMaxWidth().testTag(MENTOR_CRISIS_HELP_TAG))
    }
}

@Composable
private fun InputRow(enabled: Boolean, hint: String, onSend: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val send = {
        if (text.isNotBlank() && enabled) {
            onSend(text)
            text = ""
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(MentorConversation.MAX_INPUT) },
            placeholder = { Text(hint) },
            modifier = Modifier.weight(1f).testTag(MENTOR_INPUT_TAG),
            maxLines = 5,
            shape = RoundedCornerShape(22.dp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = { send() }),
        )
        Spacer(Modifier.width(8.dp))
        HotTextButton(MentorTexts.SEND, onClick = send, enabled = enabled && text.isNotBlank(), modifier = Modifier.testTag(MENTOR_SEND_TAG))
    }
}

/** «Что Винс помнит» (A62 §4 §5, §11 п. 2): the summaries, visible and deletable. */
@Composable
private fun MemoryDialog(summaries: List<TalkSummary>, onForget: () -> Unit, onClose: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(MentorTexts.MEMORY_TITLE) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (summaries.isEmpty()) {
                    Text(MentorTexts.MEMORY_EMPTY, style = MaterialTheme.typography.bodyMedium)
                } else {
                    summaries.takeLast(5).reversed().forEach { Text(it.text, style = MaterialTheme.typography.bodyMedium) }
                    Text(MentorTexts.MEMORY_NOTE, style = MaterialTheme.typography.bodySmall, color = HotThemeTokens.colors.textTertiary)
                }
            }
        },
        confirmButton = { HotTextButton(MentorTexts.MEMORY_CLOSE, onClick = onClose) },
        dismissButton = {
            if (summaries.isNotEmpty()) {
                HotTextButton(if (confirm) "Точно забыть?" else MentorTexts.MEMORY_FORGET, onClick = {
                    if (confirm) {
                        onForget()
                        onClose()
                    } else {
                        confirm = true
                    }
                })
            }
        },
    )
}
