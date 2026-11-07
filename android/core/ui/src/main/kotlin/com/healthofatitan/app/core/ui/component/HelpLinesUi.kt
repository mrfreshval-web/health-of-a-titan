package com.healthofatitan.app.core.ui.component

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.TelephonyManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.healthofatitan.app.core.model.safety.HelpDirectory
import com.healthofatitan.app.core.model.safety.HelpLines
import java.util.Locale

/**
 * Help numbers for where the phone is now (Y6, A07 §4.5): the mobile network's country, then the
 * SIM's, then the phone's region. Reads no permission and needs no network, so SOS stays instant.
 */
fun Context.helpLines(): HelpLines {
    val tm = runCatching { getSystemService(TelephonyManager::class.java) }.getOrNull()
    return HelpDirectory.resolve(
        runCatching { tm?.networkCountryIso }.getOrNull(),
        runCatching { tm?.simCountryIso }.getOrNull(),
        Locale.getDefault().country,
    )
}

/**
 * A country that wins over the phone's own (ISO code): the country chosen in the profile once it
 * exists (A07 §4.5 «страна в профиле»), and tests. Null — the phone decides.
 */
val LocalHelpCountry = staticCompositionLocalOf<String?> { null }

/** [helpLines] for a screen; read once per composition entry. */
@Composable
fun rememberHelpLines(): HelpLines {
    val context = LocalContext.current
    val country = LocalHelpCountry.current
    return remember(context, country) { country?.let { HelpDirectory.forCountry(it) } ?: context.helpLines() }
}

/** Opens the dialer with the number pre-filled; the user confirms the call (no CALL_PHONE). */
fun Context.dialHelp(number: String) {
    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(intent)
    } catch (@Suppress("UNUSED_PARAMETER") e: ActivityNotFoundException) {
        // No dialer (a tablet without telephony): the number stays visible on screen.
    }
}

/** Opens the crisis lines directory when the country has no line of ours (needs the network). */
fun Context.openHelpDirectory() {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(HelpDirectory.DIRECTORY_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(intent)
    } catch (@Suppress("UNUSED_PARAMETER") e: ActivityNotFoundException) {
        // No browser: the address is written on screen.
    }
}
