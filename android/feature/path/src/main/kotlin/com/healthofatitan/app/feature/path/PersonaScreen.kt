package com.healthofatitan.app.feature.path

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.healthofatitan.app.core.datastore.MentorPrefs
import com.healthofatitan.app.core.datastore.MentorSettings
import com.healthofatitan.app.core.model.mentor.MentorPersona
import com.healthofatitan.app.core.model.nav.HotRoute
import com.healthofatitan.app.core.ui.component.HotCard
import com.healthofatitan.app.core.ui.component.HotCardShape
import com.healthofatitan.app.core.ui.component.HotOverline
import com.healthofatitan.app.core.ui.component.HotSpacing
import com.healthofatitan.app.core.ui.component.PersonaAvatar
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

const val PERSONA_SCREEN_TAG = "screen_mentor_persona"
const val PERSONA_STRONG_WORDS_TAG = "persona_strong_words"

fun personaTag(p: MentorPersona): String = "persona_${p.id}"

/**
 * «Аватар › Наставник» (D-030, A38 §9 п. 2): the mentor's character chosen by one preview line —
 * the same situation for all three (A38 §7.1), so the difference is heard at once. One tap
 * changes it from the next line on; memory, goals and marks stay. «Крепкие слова» of Бро is off
 * and cannot be turned on until the founder's decision and the 18+ consent (D-030 п. 3).
 */
fun NavGraphBuilder.mentorPersonaScreen(stateful: Boolean = true) {
    composable(route = HotRoute.MENTOR_PERSONA) {
        if (stateful) PersonaRoute() else PersonaScreen(MentorPrefs(), onPick = {})
    }
}

@HiltViewModel
class PersonaViewModel @Inject constructor(private val settings: MentorSettings) : ViewModel() {
    val prefs: StateFlow<MentorPrefs> = settings.prefs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MentorPrefs())

    fun pick(persona: MentorPersona) {
        viewModelScope.launch { settings.setPersona(persona) }
    }
}

@Composable
private fun PersonaRoute(viewModel: PersonaViewModel = hiltViewModel()) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    PersonaScreen(prefs, onPick = viewModel::pick)
}

@Composable
fun PersonaScreen(prefs: MentorPrefs, onPick: (MentorPersona) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = HotSpacing.edge, end = HotSpacing.edge, top = 4.dp, bottom = HotSpacing.sectionGap)
            .testTag(PERSONA_SCREEN_TAG),
        verticalArrangement = Arrangement.spacedBy(HotSpacing.cardGap),
    ) {
        HotOverline(PersonaTexts.SITUATION)
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(HotSpacing.cardGap)) {
            MentorPersona.entries.forEach { p ->
                PersonaCard(p, selected = prefs.effective == p, onClick = { onPick(p) })
            }
        }
        if (prefs.effective == MentorPersona.BRO) StrongWordsRow(prefs.strongWords)
        Text(PersonaTexts.NOTE, style = MaterialTheme.typography.bodySmall, color = HotThemeTokens.colors.textTertiary)
    }
}

@Composable
private fun PersonaCard(persona: MentorPersona, selected: Boolean, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    HotCard(
        cornice = selected,
        modifier = Modifier
            .fillMaxWidth()
            .clip(HotCardShape)
            .then(if (selected) Modifier.border(1.5.dp, primary, HotCardShape) else Modifier)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .testTag(personaTag(persona)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PersonaAvatar(persona, size = 48.dp, line = if (selected) primary else MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(persona.nameForm, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(PersonaTexts.essence(persona), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(PersonaTexts.preview(persona), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** A38 §5.1: off by default; here it cannot be turned on at all until D-030 п. 3 is decided. */
@Composable
private fun StrongWordsRow(enabled: Boolean) {
    HotCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(PersonaTexts.STRONG_WORDS, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(PersonaTexts.STRONG_WORDS_OFF, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = enabled, onCheckedChange = {}, enabled = false, modifier = Modifier.testTag(PERSONA_STRONG_WORDS_TAG))
        }
    }
}
