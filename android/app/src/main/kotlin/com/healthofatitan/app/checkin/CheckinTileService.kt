package com.healthofatitan.app.checkin

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.healthofatitan.app.MainActivity
import com.healthofatitan.app.core.model.nav.HotRoute

/**
 * Quick settings tile «HoT Утро» (A44, D-036): one tap opens the blind morning deck after unlock.
 * Shows nothing about the answers (S5 never reaches a tile).
 */
class CheckinTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val link = "${HotRoute.DEEP_LINK_SCHEME}://${HotRoute.CHECKIN}"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link), this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(
            this,
            1,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (isLocked) unlockAndRun { startActivityAndCollapse(pending) } else startActivityAndCollapse(pending)
    }
}
