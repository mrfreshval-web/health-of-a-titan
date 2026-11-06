package com.healthofatitan.app.feature.me

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.healthofatitan.app.core.model.nav.HotRoute
import com.healthofatitan.app.core.ui.component.StubScreen

/** Tab «Я» (A11 §1.1). Slice 0: placeholder. */
fun NavGraphBuilder.meScreen() {
    composable(route = HotRoute.ME) {
        MeScreen()
    }
}

const val ME_SCREEN_TAG = "screen_me"

@Composable
fun MeScreen(modifier: Modifier = Modifier) {
    StubScreen(
        lead = "HoT — wellness-приложение, не медицинское изделие: не ставит диагнозов и не назначает лечение.",
        blocks = listOf("Цели и условия жизни", "Стек и покупки", "Практики", "Скрининг", "Настройки"),
        testTag = ME_SCREEN_TAG,
        modifier = modifier,
    )
}
