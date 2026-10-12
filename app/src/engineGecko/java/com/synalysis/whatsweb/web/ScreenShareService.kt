package com.synalysis.whatsweb.web

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.synalysis.whatsweb.MainActivity
import com.synalysis.whatsweb.R

/**
 * Android requires a media-projection foreground service before a screen
 * capture can start. The system capture prompt is the user's consent.
 */
class ScreenShareService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            val notification = notification()
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            deliver(ready)
        } catch (_: Exception) {
            deliver(failed)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun notification(): android.app.Notification {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.screen_share_active),
                NotificationManager.IMPORTANCE_LOW,
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, ScreenShareService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(getString(R.string.screen_share_active))
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(open)
            .addAction(0, getString(R.string.screen_share_stop), stop)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "screen-share"
        private const val NOTIFICATION_ID = 42
        private const val ACTION_STOP = "com.synalysis.whatsweb.action.STOP_SCREEN_SHARE"
        private val main = Handler(Looper.getMainLooper())
        private var ready: (() -> Unit)? = null
        private var failed: (() -> Unit)? = null

        fun start(context: Context, onReady: () -> Unit, onFailed: () -> Unit) {
            ready = onReady
            failed = onFailed
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, ScreenShareService::class.java),
                )
            } catch (_: Exception) {
                ready = null
                failed = null
                onFailed()
            }
        }

        fun stop(context: Context) {
            ready = null
            failed = null
            try {
                context.startService(
                    Intent(context, ScreenShareService::class.java).setAction(ACTION_STOP),
                )
            } catch (_: Exception) {
            }
        }

        private fun deliver(callback: (() -> Unit)?) {
            ready = null
            failed = null
            if (callback != null) main.post(callback)
        }
    }
}
