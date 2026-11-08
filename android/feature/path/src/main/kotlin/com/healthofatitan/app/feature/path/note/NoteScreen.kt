package com.healthofatitan.app.feature.path.note

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.healthofatitan.app.core.database.HotDatabase
import com.healthofatitan.app.core.database.dao.NoteDao
import com.healthofatitan.app.core.database.entity.NoteEntity
import com.healthofatitan.app.core.model.nav.HotRoute
import com.healthofatitan.app.core.ui.component.HotPrimaryButton
import com.healthofatitan.app.core.ui.component.HotTextButton
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.core.ui.theme.HotType
import com.healthofatitan.app.domain.engine.CrisisGuard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

const val NOTE_SCREEN_TAG = "screen_note"
const val NOTE_FIELD_TAG = "note_field"
const val NOTE_SAVE_TAG = "note_save"

/** Texts of «Заметка» (A66 §2.4; draft for Agents 16 and 10). */
object NoteCopy {
    const val TITLE = "Заметка"
    const val HINT = "Что важно про этот день — своими словами"
    const val PRIVATE = "Только на телефоне. Найдётся в «За плечами»."
    const val SAVE = "Сохранить"
    const val HELP = "Помощь рядом"
    const val MAX = 500
}

/** Rules of a note: trimmed, cut to [NoteCopy.MAX], blank is not saved. */
object NoteRules {
    fun clean(text: String): String? = text.trim().take(NoteCopy.MAX).takeIf { it.isNotEmpty() }
}

@HiltViewModel
class NoteViewModel @Inject constructor(
    private val dao: NoteDao,
    private val clock: Clock,
) : ViewModel() {
    /** CrisisGuard on the phone (A07): 1 keeps help visible, ≥ 2 opens SOS. */
    fun crisis(text: String): Int = CrisisGuard.assess(text).level.value

    fun save(text: String, onDone: () -> Unit) {
        val clean = NoteRules.clean(text) ?: return onDone()
        viewModelScope.launch {
            runCatching {
                dao.insert(
                    NoteEntity(
                        userId = HotDatabase.LOCAL_USER,
                        dayKey = LocalDate.now(clock).toString(),
                        text = clean,
                        at = clock.millis(),
                    ),
                )
            }
            onDone()
        }
    }
}

/** «Заметка» of «+»: one field, «Сохранить»; free text never leaves the phone (P-21). */
fun NavGraphBuilder.noteScreen(onDone: () -> Unit, onOpenHelp: () -> Unit) {
    composable(route = HotRoute.NOTE) {
        val vm: NoteViewModel = hiltViewModel()
        NoteScreen(onSave = { vm.save(it, onDone) }, crisis = vm::crisis, onOpenHelp = onOpenHelp)
    }
}

@Composable
fun NoteScreen(onSave: (String) -> Unit, crisis: (String) -> Int, onOpenHelp: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var level by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(level) { if (level >= 2) onOpenHelp() }
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .padding(16.dp)
            .testTag(NOTE_SCREEN_TAG),
    ) {
        BasicTextField(
            value = text,
            onValueChange = {
                text = it.take(NoteCopy.MAX)
                level = maxOf(level, crisis(it))
            },
            textStyle = HotType.voice.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(HotThemeTokens.colors.glassTop, HotThemeTokens.colors.glassBottom)), shape)
                .border(1.dp, HotThemeTokens.colors.rule, shape)
                .padding(16.dp)
                .testTag(NOTE_FIELD_TAG),
            decorationBox = { inner ->
                Box {
                    if (text.isEmpty()) Text(NoteCopy.HINT, style = HotType.voice, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    inner()
                }
            },
        )
        Spacer(Modifier.height(8.dp))
        Text(NoteCopy.PRIVATE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (level >= 1) HotTextButton(NoteCopy.HELP, onOpenHelp)
        Spacer(Modifier.weight(1f))
        HotPrimaryButton(NoteCopy.SAVE, { onSave(text) }, Modifier.fillMaxWidth().testTag(NOTE_SAVE_TAG), enabled = text.isNotBlank())
    }
}
