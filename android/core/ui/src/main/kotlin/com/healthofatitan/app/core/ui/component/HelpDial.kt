package com.healthofatitan.app.core.ui.component

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Opens the dialer with a help number pre-filled (112 or 911, core/model HelpNumbers); the user
 * confirms the call (no CALL_PHONE).
 */
fun Context.dialHelp(number: String) {
    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(intent)
    } catch (@Suppress("UNUSED_PARAMETER") e: ActivityNotFoundException) {
        // No dialer (a tablet without telephony): the number stays visible on screen.
    }
}
