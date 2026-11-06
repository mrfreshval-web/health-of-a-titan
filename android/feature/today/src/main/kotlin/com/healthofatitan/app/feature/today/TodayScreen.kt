package com.healthofatitan.app.feature.today

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.healthofatitan.app.core.model.nav.HotRoute
import com.healthofatitan.app.core.ui.component.StubScreen

/** Tab «Сегодня» (A11 §1.1). Slice 0: placeholder. */
fun NavGraphBuilder.todayScreen() {
    composable(route = HotRoute.TODAY) {
        TodayScreen()
    }
}

const val TODAY_SCREEN_TAG = "screen_today"

@Composable
fun TodayScreen(modifier: Modifier = Modifier) {
    StubScreen(
        headline = "Сегодня",
        lead = "Утро за пару минут: состояние, план и одно главное. Карточки появятся с первыми данными WHOOP.",
        blocks = listOf("Состояние и Монограмма дня", "Утро · 1 мин", "Тренировка дня", "Тарелка дня", "Работа", "Стек · утро"),
        testTag = TODAY_SCREEN_TAG,
        modifier = modifier,
    )
}
