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
import com.healthofatitan.app.integration.whoop.day.ReturnNotifier
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The warm nudge after a pause (A18 И4.4 ②, A11 §2 «Возвращение»): its own quiet channel, low
 * importance, no sound; one action «Минимальная версия» instead of «Тренировка». On the lock
 * screen only «HoT». Sent at most once per pause (ReturnPolicy), never at night.
 */
@Singleton
class ReturnNudgeNotifier @Inject constructor(@param:ApplicationContext private val context: Context) : ReturnNotifier {

    override fun showReturn(title: String, text: String): Boolean {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted) return false
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW).apply {
                description = CHANNEL_DESCRIPTION
            },
        )
        val today = Intent(Intent.ACTION_VIEW, "${HotRoute.DEEP_LINK_SCHEME}://${HotRoute.TODAY}".toUri(), context, MainActivity::class.java)
        val open = PendingIntent.getActivity(context, REQUEST_OPEN, today, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val minimal = PendingIntent.getActivity(context, REQUEST_MINIMAL, today, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
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
            .addAction(0, ACTION_MINIMAL, minimal)
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
        const val CHANNEL = "return"
        const val CHANNEL_NAME = "Возвращение"
        const val CHANNEL_DESCRIPTION = "Одно тёплое сообщение после паузы — без отчётов"
        const val PUBLIC_TITLE = "HoT"
        const val ACTION_MINIMAL = "Минимальная версия"
        const val NOTIFICATION_ID = 1002
        const val REQUEST_OPEN = 20
        const val REQUEST_MINIMAL = 21
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ReturnModule {
    @Binds
    abstract fun bindReturnNotifier(impl: ReturnNudgeNotifier): ReturnNotifier
}
