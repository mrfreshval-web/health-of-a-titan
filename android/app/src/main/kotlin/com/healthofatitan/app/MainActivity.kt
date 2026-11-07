package com.healthofatitan.app

import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.view.View
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
import com.healthofatitan.app.core.datastore.MentorPrefs
import com.healthofatitan.app.core.datastore.MentorSettings
import com.healthofatitan.app.core.ui.theme.HotTheme
import com.healthofatitan.app.navigation.DeepLinks
import com.healthofatitan.app.integration.whoop.WhoopConnection
import com.healthofatitan.app.navigation.HotApp
import com.healthofatitan.app.core.ui.component.StatusRings
import com.healthofatitan.app.feature.today.toStatusRings
import com.healthofatitan.app.integration.whoop.day.DawnRepository
import com.healthofatitan.app.integration.whoop.day.DailyLoopRepository
import com.healthofatitan.app.integration.whoop.day.DayPlanRepository
import com.healthofatitan.app.integration.healthconnect.PhoneSteps
import com.healthofatitan.app.integration.healthconnect.StepsRepository
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

    @Inject
    lateinit var dailyLoop: DailyLoopRepository

    @Inject
    lateinit var steps: StepsRepository

    @Inject
    lateinit var mentor: MentorSettings

    /**
     * D-029 header rings: the plan, the WHOOP glance (memory only), the phone's steps (Health
     * Connect, memory only) and the clock, once a minute. With steps the middle ring is solid.
     */
    private val rings by lazy {
        combine(plans.latest, whoop.glance, steps.state, minuteTicks()) { plan, glance, phone, now ->
            val ready = phone as? PhoneSteps.Ready
            HeaderRingsMapper.of(plan, glance, now, ZoneId.systemDefault())
                .copy(steps = ready?.today, stepsGoal = ready?.goal)
                .toStatusRings()
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
        // UX review 119, R6: a tap on the top of «Сегодня» opened the system «Открыть в: Календарь».
        // The app sends no such intent; the likely source is the system reading the screen text
        // (content capture → smart actions on «суббота · 7 ноября», «отбой в 02:50»). Health data
        // should not reach system text analysis anyway: the whole window is excluded from it.
        window.decorView.importantForContentCapture = View.IMPORTANT_FOR_CONTENT_CAPTURE_NO_EXCLUDE_DESCENDANTS
        if (savedInstanceState == null) pendingRoute = routeOf(intent)
        setContent {
            HotTheme {
                val syncProgress by whoop.progress.collectAsStateWithLifecycle()
                val headerRings by rings.collectAsStateWithLifecycle(StatusRings.Empty)
                val dawnDue by dawn.due.collectAsStateWithLifecycle(false)
                val mentorPrefs by mentor.prefs.collectAsStateWithLifecycle(MentorPrefs())
                HotApp(
                    pendingRoute = pendingRoute,
                    onPendingRouteConsumed = { pendingRoute = null },
                    syncProgress = syncProgress,
                    rings = headerRings,
                    dawnDue = dawnDue,
                    persona = mentorPrefs.persona,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // A18 И1.2 ①: every app start refreshes the 28-day window unless it is fresh (WorkManager).
        lifecycleScope.launch { runCatching { whoop.onAppOpen() } }
        // A18 И4.4: the date of this open (silence is counted in whole days without one).
        lifecycleScope.launch { runCatching { dailyLoop.recordOpen() } }
        lifecycleScope.launch { runCatching { steps.refresh() } }
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
