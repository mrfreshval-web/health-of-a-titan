package com.healthofatitan.app.feature.week

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.healthofatitan.app.core.model.nav.HotRoute
import com.healthofatitan.app.core.ui.component.StubScreen

/** Tab «Неделя» (A11 §1.1). Slice 0: placeholder. */
fun NavGraphBuilder.weekScreen() {
    composable(route = HotRoute.WEEK) {
        WeekScreen()
    }
}

const val WEEK_SCREEN_TAG = "screen_week"

@Composable
fun WeekScreen(modifier: Modifier = Modifier) {
    StubScreen(
        headline = "Неделя",
        lead = "Паспорт недели: Монограмма, грани по дням, одно наблюдение и цель недели.",
        blocks = listOf("Монограмма недели", "Грани по дням", "Наблюдение недели", "Цель недели"),
        testTag = WEEK_SCREEN_TAG,
        modifier = modifier,
    )
}
