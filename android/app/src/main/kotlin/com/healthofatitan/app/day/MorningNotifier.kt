package com.healthofatitan.app.day

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.healthofatitan.app.MainActivity
import com.healthofatitan.app.R
import com.healthofatitan.app.core.model.nav.HotRoute
import com.healthofatitan.app.integration.whoop.day.DayNotifier
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The morning push (A11 §2): channel «Утро», default importance, title = regime, text = the day
 * in one line. No S4/S5 content (the engine never puts alcohol or intimacy into it). On the lock
 * screen only «HoT · день собран». A tap opens «Сегодня».
 */
@Singleton
class MorningNotifier @Inject constructor(@param:ApplicationContext private val context: Context) : DayNotifier {

    override fun showMorning(title: String, text: String): Boolean {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted) return false
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = CHANNEL_DESCRIPTION
            },
        )
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(Intent.ACTION_VIEW, "${HotRoute.DEEP_LINK_SCHEME}://${HotRoute.TODAY}".toUri(), context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val lockScreen = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_hot)
            .setContentTitle(PUBLIC_TITLE)
            .build()
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_hot)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(lockScreen)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        return try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private companion object {
        const val CHANNEL = "morning"
        const val CHANNEL_NAME = "Утро"
        const val CHANNEL_DESCRIPTION = "Режим дня — когда WHOOP посчитает ночь"
        const val PUBLIC_TITLE = "HoT · день собран"
        const val NOTIFICATION_ID = 1001
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DayModule {
    @Binds
    abstract fun bindDayNotifier(impl: MorningNotifier): DayNotifier
}
