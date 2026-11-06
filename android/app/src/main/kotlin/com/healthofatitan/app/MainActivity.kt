package com.healthofatitan.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.healthofatitan.app.core.ui.theme.HotTheme
import com.healthofatitan.app.navigation.DeepLinks
import com.healthofatitan.app.navigation.HotApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var pendingRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) pendingRoute = routeOf(intent)
        setContent {
            HotTheme {
                HotApp(pendingRoute = pendingRoute, onPendingRouteConsumed = { pendingRoute = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        routeOf(intent)?.let { pendingRoute = it }
    }

    private fun routeOf(intent: Intent?): String? =
        intent?.data?.let { DeepLinks.routeFor(it.scheme, it.host) }
}
