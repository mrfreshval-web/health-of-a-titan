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
import com.healthofatitan.app.domain.today.week.WeekCopy
import com.healthofatitan.app.integration.whoop.week.WeekNotifier
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Sunday push of «Неделя» (A52 §2.7): «Твой Титан недели готов» — the only push of the module,
 * its own quiet channel, no numbers. On the lock screen only «HoT». A tap opens «Неделя».
 */
@Singleton
class WeekPushNotifier @Inject constructor(@param:ApplicationContext private val context: Context) : WeekNotifier {

    override fun showWeek(title: String, text: String): Boolean {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted) return false
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, WeekCopy.PUSH_CHANNEL, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = WeekCopy.PUSH_CHANNEL_DESCRIPTION
            },
        )
        val open = PendingIntent.getActivity(
            context,
            REQUEST_OPEN,
            Intent(Intent.ACTION_VIEW, "${HotRoute.DEEP_LINK_SCHEME}://${HotRoute.WEEK}".toUri(), context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val lockScreen = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_hot)
            .setContentTitle(WeekCopy.PUSH_PUBLIC)
            .build()
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_hot)
            .setContentTitle(title)
            .setContentText(text)
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
        const val CHANNEL = "week"
        const val NOTIFICATION_ID = 1003
        const val REQUEST_OPEN = 30
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class WeekPushModule {
    @Binds
    abstract fun bindWeekNotifier(impl: WeekPushNotifier): WeekNotifier
}
