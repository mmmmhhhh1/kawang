package org.example.kawang.adminmobile.push

import android.os.Build
import com.huawei.hms.push.HmsMessageService
import com.huawei.hms.push.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.example.kawang.adminmobile.AppForegroundState
import org.example.kawang.adminmobile.data.AdminNotificationCenter
import org.example.kawang.adminmobile.data.AdminRepository
import org.json.JSONObject

class HuaweiPushMessageService : HmsMessageService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        if (AppForegroundState.isInForeground) {
            return
        }

        val payload = parsePayload(message)
        val title = payload.title.ifBlank { payload.dataTitle.ifBlank { defaultTitle(payload.kind) } }
        val body = payload.body.ifBlank { payload.dataMessage.ifBlank { "你有一条新的通知" } }
        val notificationCenter = AdminNotificationCenter(applicationContext)

        when (payload.kind) {
            "recharge" -> {
                notificationCenter.showRechargeNotification(
                    requestId = payload.requestId,
                    title = title,
                    message = body,
                )
            }

            "support" -> {
                val sessionId = payload.sessionId
                    ?: payload.route?.substringAfter("support/")?.toLongOrNull()
                    ?: return
                notificationCenter.showSupportNotification(
                    sessionId = sessionId,
                    title = title,
                    message = body,
                )
            }

            else -> {
                notificationCenter.showRouteNotification(
                    notificationId = (payload.route ?: body).hashCode(),
                    title = title,
                    message = body,
                    route = payload.route,
                )
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        if (token.isBlank()) {
            return
        }
        serviceScope.launch {
            val repository = AdminRepository(applicationContext)
            if (repository.sessionStore.getBaseUrl().isBlank() || repository.authToken().isBlank()) {
                return@launch
            }
            runCatching {
                repository.registerDevice(
                    vendor = "HUAWEI",
                    token = token,
                    deviceName = "${Build.MANUFACTURER} ${Build.MODEL}",
                )
            }
        }
    }

    private fun parsePayload(message: RemoteMessage): PushPayload {
        val data = message.data.takeIf { it.isNotBlank() }?.let(::JSONObject)
        return PushPayload(
            kind = data?.optString("kind").orEmpty(),
            route = data?.optString("route")?.takeIf { it.isNotBlank() },
            sessionId = data?.optString("sessionId")?.toLongOrNull(),
            requestId = data?.optString("requestId")?.toLongOrNull(),
            dataTitle = data?.optString("title").orEmpty(),
            dataMessage = data?.optString("message").orEmpty(),
            title = message.notification?.title.orEmpty(),
            body = message.notification?.body.orEmpty(),
        )
    }

    private fun defaultTitle(kind: String): String {
        return when (kind) {
            "recharge" -> "收到新的充值审核"
            "support" -> "新的客服消息"
            else -> "Kawang Admin"
        }
    }

    private data class PushPayload(
        val kind: String,
        val route: String?,
        val sessionId: Long?,
        val requestId: Long?,
        val dataTitle: String,
        val dataMessage: String,
        val title: String,
        val body: String,
    )
}
