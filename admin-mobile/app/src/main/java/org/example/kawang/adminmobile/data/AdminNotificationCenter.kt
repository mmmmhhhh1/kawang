package org.example.kawang.adminmobile.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.example.kawang.adminmobile.MainActivity

class AdminNotificationCenter(
    private val context: Context,
) {

    fun showRechargeNotification(requestId: Long?, title: String, message: String) {
        showNotification(
            notificationId = (requestId ?: System.currentTimeMillis()).toInt(),
            title = title,
            message = message,
            route = requestId?.let { "recharges/$it" },
        )
    }

    fun showSupportNotification(sessionId: Long, title: String, message: String) {
        showNotification(
            notificationId = (sessionId + 100_000L).toInt(),
            title = title,
            message = message,
            route = "support/$sessionId",
        )
    }

    fun showRouteNotification(notificationId: Int, title: String, message: String, route: String?) {
        showNotification(
            notificationId = notificationId,
            title = title,
            message = message,
            route = route,
        )
    }

    private fun showNotification(notificationId: Int, title: String, message: String, route: String?) {
        ensureChannel()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(
                if (route?.startsWith("support/") == true) {
                    NotificationCompat.CATEGORY_MESSAGE
                } else {
                    NotificationCompat.CATEGORY_STATUS
                },
            )
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(buildIntent(route))
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }

    private fun buildIntent(route: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("route", route)
        }
        return PendingIntent.getActivity(
            context,
            route?.hashCode() ?: 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) {
            return
        }
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Kawang Admin",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "充值审核与客服消息提醒"
            enableVibration(true)
            setShowBadge(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "kawang_admin_live"
    }
}
