package com.healthofatitan.app

import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.healthofatitan.app.core.ui.theme.HotTheme
import com.healthofatitan.app.navigation.DeepLinks
import com.healthofatitan.app.integration.whoop.WhoopConnection
import com.healthofatitan.app.navigation.HotApp
import com.healthofatitan.app.core.ui.component.StatusRings
import com.healthofatitan.app.feature.today.toStatusRings
import com.healthofatitan.app.integration.whoop.day.DawnRepository
import com.healthofatitan.app.integration.whoop.day.DayPlanRepository
import com.healthofatitan.app.integration.whoop.day.HeaderRingsMapper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var whoop: WhoopConnection

    @Inject
    lateinit var plans: DayPlanRepository

    @Inject
    lateinit var dawn: DawnRepository

    /** D-029 header rings: the plan, the WHOOP glance (memory only) and the clock, once a minute. */
    private val rings by lazy {
        combine(plans.latest, whoop.glance, minuteTicks()) { plan, glance, now ->
            HeaderRingsMapper.of(plan, glance, now, ZoneId.systemDefault()).toStatusRings()
        }
    }

    private var pendingRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Cold start from the launcher only: let «пульс оживляет титана» finish (≤ 1000 ms from
        // process start, brand/mark-v2 §7). Never longer, never for SOS (tile, shortcut, deep link
        // must open at once) and not at all when the user turned animations off.
        val holdForIcon = savedInstanceState == null &&
            intent?.action == Intent.ACTION_MAIN &&
            ValueAnimator.areAnimatorsEnabled()
        splash.setKeepOnScreenCondition {
            holdForIcon && SystemClock.uptimeMillis() - Process.getStartUptimeMillis() < SPLASH_ICON_MS
        }
        splash.setOnExitAnimationListener { provider ->
            provider.view.animate()
                .alpha(0f)
                .setDuration(SPLASH_FADE_MS)
                .withEndAction { provider.remove() }
                .start()
        }
        // The app is always dark («Ночь»): light status and navigation bar icons in any system theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        if (savedInstanceState == null) pendingRoute = routeOf(intent)
        setContent {
            HotTheme {
                val syncProgress by whoop.progress.collectAsStateWithLifecycle()
                val headerRings by rings.collectAsStateWithLifecycle(StatusRings.Empty)
                val dawnDue by dawn.due.collectAsStateWithLifecycle(false)
                HotApp(
                    pendingRoute = pendingRoute,
                    onPendingRouteConsumed = { pendingRoute = null },
                    syncProgress = syncProgress,
                    rings = headerRings,
                    dawnDue = dawnDue,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // A18 И1.2 ①: every app start refreshes the 28-day window unless it is fresh (WorkManager).
        lifecycleScope.launch { runCatching { whoop.onAppOpen() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        routeOf(intent)?.let { pendingRoute = it }
    }

    private fun routeOf(intent: Intent?): String? =
        intent?.data?.let { DeepLinks.routeFor(it.scheme, it.host) }

    private fun minuteTicks() = flow {
        while (true) {
            emit(LocalDateTime.now())
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }

    private companion object {
        const val SPLASH_ICON_MS = 1_000L
        const val SPLASH_FADE_MS = 200L
    }
}
