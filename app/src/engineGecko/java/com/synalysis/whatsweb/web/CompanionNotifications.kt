package com.synalysis.whatsweb.web

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.synalysis.whatsweb.MainActivity
import com.synalysis.whatsweb.R
import org.mozilla.geckoview.WebNotification
import org.mozilla.geckoview.WebNotificationDelegate

private const val CHANNEL_ID = "messages"
private const val ACTION_OPEN = "com.synalysis.whatsweb.action.OPEN_NOTIFICATION"
private const val ACTION_DISMISS = "com.synalysis.whatsweb.action.DISMISS_NOTIFICATION"
private const val EXTRA_TAG = "tag"
private const val EXTRA_WEB_ACTION = "webAction"

internal class CompanionNotifications(
    context: Context,
) : WebNotificationDelegate {
    private val app = context.applicationContext
    private val shown = HashMap<String, WebNotification>()

    override fun onShowNotification(notification: WebNotification) {
        if (!LinkRouter.isTrustedCompanionOrigin(notification.origin)) {
            notification.dismiss()
            return
        }
        val tag = tag(notification)
        synchronized(shown) { shown[tag] = notification }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = NotificationManagerCompat.from(app)
        ensureChannel(manager)
        val open = pending(ACTION_OPEN, tag, 0, null)
        val dismiss = pending(ACTION_DISMISS, tag, 1, null)
        val builder = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(notification.title)
            .setContentText(notification.text)
            .setAutoCancel(true)
            .setSilent(notification.silent)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(open)
            .setDeleteIntent(dismiss)
        notification.actions?.take(3)?.forEachIndexed { index, webAction ->
            val label = webAction.title.ifBlank { webAction.name }
            if (label.isBlank()) return@forEachIndexed
            builder.addAction(0, label, pending(ACTION_OPEN, tag, index + 2, webAction.name))
        }
        val built = builder.build()
        try {
            manager.notify(tag, 0, built)
        } catch (_: SecurityException) {
        }
    }

    override fun onCloseNotification(notification: WebNotification) {
        val tag = tag(notification)
        synchronized(shown) { shown.remove(tag) }
        NotificationManagerCompat.from(app).cancel(tag, 0)
    }

    fun open(tag: String, webAction: String?) {
        val notification = synchronized(shown) { shown[tag] } ?: return
        if (webAction.isNullOrEmpty()) notification.click() else notification.click(webAction)
    }

    fun dismiss(tag: String) {
        val notification = synchronized(shown) { shown.remove(tag) } ?: return
        notification.dismiss()
    }

    private fun ensureChannel(manager: NotificationManagerCompat) {
        if (Build.VERSION.SDK_INT < 26) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            app.getString(R.string.app_name_calls),
            NotificationManager.IMPORTANCE_HIGH,
        )
        manager.createNotificationChannel(channel)
    }

    private fun pending(action: String, tag: String, salt: Int, webAction: String?): PendingIntent {
        val intent = Intent(app, NotificationActionReceiver::class.java)
            .setAction(action)
            .putExtra(EXTRA_TAG, tag)
        if (!webAction.isNullOrEmpty()) intent.putExtra(EXTRA_WEB_ACTION, webAction)
        return PendingIntent.getBroadcast(
            app,
            tag.hashCode() xor salt,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun tag(notification: WebNotification): String {
        val webTag = notification.tag
        return if (webTag.isNullOrEmpty()) "whatsweb-${System.identityHashCode(notification)}" else webTag
    }
}

internal object NotificationBridge {
    var notifications: CompanionNotifications? = null
}

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val tag = intent.getStringExtra(EXTRA_TAG) ?: return
        val notifications = NotificationBridge.notifications
        when (intent.action) {
            ACTION_OPEN -> {
                notifications?.open(tag, intent.getStringExtra(EXTRA_WEB_ACTION))
                context.startActivity(
                    Intent(context, MainActivity::class.java).addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP,
                    ),
                )
            }
            ACTION_DISMISS -> notifications?.dismiss(tag)
        }
    }
}
