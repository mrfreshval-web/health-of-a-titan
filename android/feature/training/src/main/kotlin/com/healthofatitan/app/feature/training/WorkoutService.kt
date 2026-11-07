package com.healthofatitan.app.feature.training

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import com.healthofatitan.app.core.model.nav.HotRoute
import com.healthofatitan.app.domain.training.PlayerPhase
import com.healthofatitan.app.domain.training.WorkoutStep
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The app's monochrome notification icon (lives in :app, bound there). */
fun interface WorkoutNotificationIcon {
    fun smallIcon(): Int
}

/**
 * Keeps the workout clock alive with the screen off (A40 §7): foreground service of type
 * specialUse, an ongoing silent notification with the step and a countdown chronometer, and a
 * partial wake lock with a timeout. It does nothing itself: [WorkoutController] ticks and cues.
 */
@AndroidEntryPoint
class WorkoutService : Service() {

    @Inject lateinit var controller: WorkoutController

    @Inject lateinit var icon: WorkoutNotificationIcon

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(
            NotificationChannel(CHANNEL, TrainingTexts.CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW).apply {
                description = TrainingTexts.CHANNEL_DESCRIPTION
                setSound(null, null)
                enableVibration(false)
            },
        )
        startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        if (!started) {
            started = true
            wakeLock = getSystemService(PowerManager::class.java)
                ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "hot:workout")
                ?.apply { acquire(WAKE_LOCK_MS) }
            scope.launch {
                controller.run
                    .map { r -> r?.let { Triple(it.state.index, it.state.phase, it.state.paused) } }
                    .distinctUntilChanged()
                    .collect { key ->
                        val r = controller.run.value
                        if (key == null || r == null || r.state.phase == PlayerPhase.DONE) {
                            stopSelf()
                        } else {
                            manager?.notify(NOTIFICATION_ID, notification())
                        }
                    }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        super.onDestroy()
    }

    private fun notification(): Notification {
        val r = controller.run.value
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(Intent.ACTION_VIEW, "${HotRoute.DEEP_LINK_SCHEME}://${HotRoute.TRAINING_PLAYER}".toUri()).setPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val step = r?.player?.step(r.state)
        val title = when {
            r == null -> TrainingTexts.NOTIFICATION_TITLE
            r.state.paused -> TrainingTexts.PAUSED
            step is WorkoutStep.Rest -> TrainingTexts.REST
            step is WorkoutStep.Work -> step.exercise.nameRu
            else -> TrainingTexts.NOTIFICATION_TITLE
        }
        val text = when (step) {
            is WorkoutStep.Work -> TrainingTexts.dose(step)
            is WorkoutStep.Rest -> r?.let { it.player.nextWork(it.state) }?.let { TrainingTexts.next(it.exercise.nameRu) }
            null -> null
        }
        val builder = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(icon.smallIcon())
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(Notification.CATEGORY_WORKOUT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        val left = r?.takeIf { !it.state.paused }?.state?.remainingMs(controller.now())
        if (left != null) {
            builder.setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(System.currentTimeMillis() + left)
                .setShowWhen(true)
        }
        return builder.build()
    }

    private companion object {
        const val CHANNEL = "workout"
        const val NOTIFICATION_ID = 2001

        /** Longer than any template; released earlier when the workout ends. */
        const val WAKE_LOCK_MS = 2 * 60 * 60 * 1000L
    }
}
