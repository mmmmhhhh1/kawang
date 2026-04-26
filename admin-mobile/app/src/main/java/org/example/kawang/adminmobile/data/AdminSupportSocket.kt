package org.example.kawang.adminmobile.data

import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class AdminSupportSocket {

    sealed interface Event {
        data object Connected : Event
        data object Disconnected : Event
        data class SupportDispatch(val payload: SupportDispatchPayload) : Event
        data class MessageCreated(val message: SupportMessage) : Event
        data class SessionUpdated(val session: SupportSessionItem) : Event
        data class UnreadUpdated(val unread: SupportUnread) : Event
        data class RechargeCreated(val notification: RechargeNotificationEvent) : Event
        data class Error(val message: String) : Event
    }

    private val objectMapper = ObjectMapper().findAndRegisterModules()
    private val client = OkHttpClient()
    private var socket: WebSocket? = null

    fun connect(url: String, onEvent: (Event) -> Unit) {
        disconnect()
        socket = client.newWebSocket(
            Request.Builder().url(url).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    onEvent(Event.Connected)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    onEvent(parseEvent(text))
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    onEvent(Event.Disconnected)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    onEvent(Event.Error(t.message ?: "实时连接失败"))
                    onEvent(Event.Disconnected)
                }
            },
        )
    }

    fun disconnect() {
        socket?.close(1000, "bye")
        socket = null
    }

    private fun parseEvent(raw: String): Event {
        val root = objectMapper.readTree(raw)
        val type = root.path("type").asText("")
        val data = root.path("data")
        return when (type) {
            "SUPPORT_DISPATCH" -> Event.SupportDispatch(objectMapper.treeToValue(data, SupportDispatchPayload::class.java))
            "MESSAGE_CREATED" -> Event.MessageCreated(objectMapper.treeToValue(data, SupportMessage::class.java))
            "SESSION_UPDATED" -> Event.SessionUpdated(objectMapper.treeToValue(data, SupportSessionItem::class.java))
            "UNREAD_UPDATED" -> Event.UnreadUpdated(objectMapper.treeToValue(data, SupportUnread::class.java))
            "RECHARGE_CREATED" -> Event.RechargeCreated(objectMapper.treeToValue(data, RechargeNotificationEvent::class.java))
            else -> Event.Error(data.path("message").asText("未知的实时消息"))
        }
    }
}
