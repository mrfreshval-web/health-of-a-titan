package com.healthofatitan.app.sos

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.healthofatitan.app.MainActivity
import com.healthofatitan.app.core.model.nav.HotRoute

/**
 * Quick settings tile «HoT SOS» (A11 §1.2, A14 §7): one tap opens the SOS screen after unlock
 * via startActivityAndCollapse(PendingIntent) (API 34+).
 */
class SosTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(HotRoute.SOS_DEEP_LINK), this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (isLocked) unlockAndRun { startActivityAndCollapse(pending) } else startActivityAndCollapse(pending)
    }
}
