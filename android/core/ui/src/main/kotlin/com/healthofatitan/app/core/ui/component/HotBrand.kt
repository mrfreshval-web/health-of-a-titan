package com.healthofatitan.app.core.ui.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.healthofatitan.app.core.ui.R
import com.healthofatitan.app.core.ui.theme.HotType

/** Spoken name of the brand for TalkBack (the wordmark is decorative text). */
const val HOT_BRAND_NAME = "Health of a Titan"

/**
 * The HoT mark at UI sizes: ring, two columns and the titan as silhouettes (brand/mark-v2 §4,
 * the same reduction as the notification icon). Line art of the master does not survive < 120 dp
 * (A37 §14): there the scene uses the silhouettes too.
 */
@Composable
fun HotMark(modifier: Modifier = Modifier, size: Dp = 28.dp, tint: Color = MaterialTheme.colorScheme.onSurface) {
    Icon(
        painter = painterResource(R.drawable.core_ui_ic_hot_mark),
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(size),
    )
}

/**
 * Short lockup «знак + HoT»: in the app the wordmark is Geist Mono with wide tracking (A37 §3
 * «Логотип»; Cormorant stays in the store and the splash). Mixed case on purpose: «HoT» is never
 * set in capitals.
 *
 * @param sync when not null and not idle, the mark becomes the compact sync indicator
 *   ([HotSyncMark]): the app's global «data is coming» signal in every tab header.
 */
@Composable
fun HotLockup(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface, sync: SyncMarkState? = null) {
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = HOT_BRAND_NAME },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (sync != null && sync.phase != SyncMarkPhase.IDLE) {
            HotSyncMark(state = sync, size = 28.dp)
        } else {
            HotMark(tint = color)
        }
        Spacer(Modifier.width(10.dp))
        Text("HoT", style = HotType.wordmark, color = color)
    }
}
