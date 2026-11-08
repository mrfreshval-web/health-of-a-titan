package com.healthofatitan.app.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.healthofatitan.app.core.model.mentor.MentorPersona
import com.healthofatitan.app.core.ui.R
import com.healthofatitan.app.core.ui.component.PersonaAvatar
import com.healthofatitan.app.core.ui.theme.HotThemeTokens

const val AVATAR_TAG = "top_bar_avatar"
const val AVATAR_HELP_TAG = "avatar_menu_help"
const val AVATAR_MENTOR_TAG = "avatar_menu_mentor"
const val AVATAR_SETTINGS_TAG = "avatar_menu_settings"
const val MENTOR_CIRCLE_TAG = "top_bar_mentor"

/** Винс on the left of the header (D-038): one tap opens the conversation. */
@Composable
fun MentorCircle(persona: MentorPersona?, onClick: () -> Unit) {
    val tokens = HotThemeTokens.colors
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, tokens.rule),
        modifier = Modifier
            .size(44.dp)
            .semantics { contentDescription = "Винс, наставник" }
            .testTag(MENTOR_CIRCLE_TAG),
    ) {
        Box(contentAlignment = Alignment.Center) {
            PersonaAvatar(persona ?: MentorPersona.DEFAULT, size = 26.dp)
        }
    }
}

/**
 * The profile circle in the right corner of every header (D-033, D-038): one tap opens «Помощь ·
 * Наставник · Настройки». Help is the first item, so it stays two taps from any tab (D-029, T-06).
 * The crisis screen and its texts are unchanged; only the way in moved.
 */
@Composable
fun AvatarMenu(
    persona: MentorPersona?,
    onHelp: () -> Unit,
    onMentor: () -> Unit,
    onSettings: () -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val tokens = HotThemeTokens.colors
    Box {
        Surface(
            onClick = { open = true },
            shape = CircleShape,
            color = Color.Transparent,
            border = BorderStroke(1.dp, tokens.rule),
            modifier = Modifier
                .size(44.dp)
                .semantics { contentDescription = "Меню: помощь, наставник, настройки" }
                .testTag(AVATAR_TAG),
        ) {
            Box(contentAlignment = Alignment.Center) {
                // «≡» in a circle (A66 §2.2): a person here promised a profile the menu does not have.
                Icon(painterResource(R.drawable.core_ui_ic_menu), contentDescription = null, modifier = Modifier.size(22.dp))
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Помощь", style = MaterialTheme.typography.bodyLarge) },
                leadingIcon = { Icon(painterResource(R.drawable.core_ui_ic_sos), contentDescription = null, modifier = Modifier.size(20.dp)) },
                onClick = {
                    open = false
                    onHelp()
                },
                modifier = Modifier.testTag(AVATAR_HELP_TAG),
            )
            DropdownMenuItem(
                text = { Text("Наставник", style = MaterialTheme.typography.bodyLarge) },
                leadingIcon = { PersonaAvatar(persona ?: MentorPersona.DEFAULT, size = 20.dp) },
                onClick = {
                    open = false
                    onMentor()
                },
                modifier = Modifier.testTag(AVATAR_MENTOR_TAG),
            )
            DropdownMenuItem(
                text = { Text("Настройки", style = MaterialTheme.typography.bodyLarge) },
                leadingIcon = { Icon(painterResource(R.drawable.core_ui_ic_me), contentDescription = null, modifier = Modifier.size(20.dp)) },
                onClick = {
                    open = false
                    onSettings()
                },
                modifier = Modifier.testTag(AVATAR_SETTINGS_TAG),
            )
        }
    }
}
