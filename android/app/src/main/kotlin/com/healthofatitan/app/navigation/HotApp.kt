package com.healthofatitan.app.navigation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.healthofatitan.app.core.model.nav.HotRoute
import com.healthofatitan.app.core.ui.R
import com.healthofatitan.app.core.ui.component.HotBackground
import com.healthofatitan.app.core.ui.component.HotTopBar
import com.healthofatitan.app.core.ui.theme.HotThemeTokens
import com.healthofatitan.app.feature.me.meScreen
import com.healthofatitan.app.feature.mentor.mentorScreen
import com.healthofatitan.app.feature.sos.sosScreen
import com.healthofatitan.app.feature.today.todayScreen
import com.healthofatitan.app.feature.week.weekScreen
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** Bottom tabs «Сегодня · Наставник · Неделя · Я» (A11 §1.1). */
enum class HotTab(val route: String, val label: String, @param:DrawableRes val icon: Int) {
    TODAY(HotRoute.TODAY, "Сегодня", R.drawable.core_ui_ic_today),
    MENTOR(HotRoute.MENTOR, "Наставник", R.drawable.core_ui_ic_mentor),
    WEEK(HotRoute.WEEK, "Неделя", R.drawable.core_ui_ic_week),
    ME(HotRoute.ME, "Я", R.drawable.core_ui_ic_me),
    ;

    val testTag: String get() = "tab_$route"
}

/**
 * App shell: one Activity, NavHost with 4 tabs + `sos` outside the tabs (A14 §7, ADR-01).
 * The SOS button sits in the top bar of every tab screen: 1 tap to 988/911 (A11 §1.3).
 *
 * @param pendingRoute route requested from outside (tile, shortcut, deep link), consumed once.
 */
@Composable
fun HotApp(
    pendingRoute: String? = null,
    onPendingRouteConsumed: () -> Unit = {},
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val inSos = currentRoute == HotRoute.SOS
    val currentTab = HotTab.entries.firstOrNull { it.route == currentRoute } ?: HotTab.TODAY

    LaunchedEffect(pendingRoute) {
        if (pendingRoute != null) {
            navController.currentBackStackEntryFlow.first() // wait until NavHost has set the graph
            if (pendingRoute == HotRoute.SOS) navController.openSos() else navController.openTab(pendingRoute)
            onPendingRouteConsumed()
        }
    }

    // «Ночное небо» behind every tab (A36 §4); SOS draws its own sober background on top.
    HotBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                if (!inSos) {
                    HotTopBar(
                        title = currentTab.label,
                        overline = TabHeaders.overline(currentTab, LocalDate.now()),
                        onSosClick = { navController.openSos() },
                    )
                }
            },
            bottomBar = {
                if (!inSos) HotBottomBar(currentTab = currentTab, onTabClick = { navController.openTab(it.route) })
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = HotRoute.TODAY,
                modifier = if (inSos) Modifier else Modifier.padding(padding),
            ) {
                todayScreen()
                mentorScreen()
                weekScreen()
                meScreen()
                sosScreen(onClose = { if (!navController.popBackStack()) navController.openTab(HotRoute.TODAY) })
            }
        }
    }
}

/**
 * Bottom tabs in «Колоннада» (A36 §5): line icons; the active tab is a gold icon on a «gold 14 %»
 * pill; labels in Manrope. A hairline separates the bar from the content — no shadow.
 */
@Composable
private fun HotBottomBar(currentTab: HotTab, onTabClick: (HotTab) -> Unit) {
    val hairline = HotThemeTokens.colors.hairline
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        modifier = Modifier.drawBehind {
            drawLine(hairline, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
        },
    ) {
        HotTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == currentTab,
                onClick = { onTabClick(tab) },
                icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                label = { Text(tab.label, style = MaterialTheme.typography.labelMedium) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = HotThemeTokens.colors.ruleSoft,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                modifier = Modifier.testTag(tab.testTag),
            )
        }
    }
}

private fun NavHostController.openTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavHostController.openSos() {
    navigate(HotRoute.SOS) { launchSingleTop = true }
}
