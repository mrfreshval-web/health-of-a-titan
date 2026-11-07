package com.healthofatitan.app.navigation

import com.healthofatitan.app.core.model.nav.HotRoute

/**
 * Maps `hot://<route>` (tile, shortcut, notifications — A11 §1.2) to a NavHost route.
 * Unknown links are ignored. Pure function so it is unit-testable without Android.
 */
object DeepLinks {
    private val known = HotRoute.tabs.toSet() + HotRoute.SOS + HotRoute.ME + HotRoute.TRAINING_PLAYER + HotRoute.CHECKIN +
        HotRoute.MENTOR + HotRoute.BREATHER

    fun routeFor(scheme: String?, host: String?): String? =
        if (scheme == HotRoute.DEEP_LINK_SCHEME && host != null && host in known) host else null
}
