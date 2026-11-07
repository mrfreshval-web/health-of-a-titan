package com.healthofatitan.app.feature.mentor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.healthofatitan.app.core.model.nav.HotRoute
import com.healthofatitan.app.core.ui.R
import com.healthofatitan.app.core.ui.component.StubScreen

/** Tab «Наставник Винс» (A11 §1.1). Slice 0: placeholder; «⋮ › Помощь» opens help (D-029). */
fun NavGraphBuilder.mentorScreen(onOpenHelp: () -> Unit = {}) {
    composable(route = HotRoute.MENTOR) {
        MentorScreen(onOpenHelp = onOpenHelp)
    }
}

const val MENTOR_SCREEN_TAG = "screen_mentor"
const val MENTOR_MENU_TAG = "mentor_menu"
const val MENTOR_HELP_TAG = "mentor_menu_help"

@Composable
fun MentorScreen(modifier: Modifier = Modifier, onOpenHelp: () -> Unit = {}) {
    var menu by rememberSaveable { mutableStateOf(false) }
    Box(modifier.fillMaxSize()) {
        StubScreen(
            lead = "Я ИИ-наставник, не врач и не терапевт.",
            blocks = listOf("Разговор", "Работа", "Записи"),
            columns = 3,
            testTag = MENTOR_SCREEN_TAG,
        )
        Box(Modifier.align(Alignment.TopEnd).padding(end = 8.dp)) {
            IconButton(onClick = { menu = true }, modifier = Modifier.testTag(MENTOR_MENU_TAG)) {
                Icon(
                    painterResource(R.drawable.core_ui_ic_more),
                    contentDescription = "Меню",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Помощь", style = MaterialTheme.typography.bodyLarge) },
                    onClick = {
                        menu = false
                        onOpenHelp()
                    },
                    modifier = Modifier.testTag(MENTOR_HELP_TAG),
                )
            }
        }
    }
}
