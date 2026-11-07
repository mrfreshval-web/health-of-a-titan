package com.healthofatitan.app.feature.mentor

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.healthofatitan.app.core.model.nav.HotRoute
import com.healthofatitan.app.core.ui.component.StubScreen

/** Tab «Наставник Винс» (A11 §1.1). Slice 0: placeholder. */
fun NavGraphBuilder.mentorScreen() {
    composable(route = HotRoute.MENTOR) {
        MentorScreen()
    }
}

const val MENTOR_SCREEN_TAG = "screen_mentor"

@Composable
fun MentorScreen(modifier: Modifier = Modifier) {
    StubScreen(
        lead = "Я ИИ-наставник, не врач и не терапевт.",
        blocks = listOf("Разговор", "Работа", "Записи"),
        columns = 3,
        testTag = MENTOR_SCREEN_TAG,
        modifier = modifier,
    )
}
