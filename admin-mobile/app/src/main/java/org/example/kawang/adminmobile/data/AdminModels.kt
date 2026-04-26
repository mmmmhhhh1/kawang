package org.example.kawang.adminmobile.data

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class ApiResponse<T>(
    val code: Int,
    val message: String,
    val data: T,
)

data class CursorPageResponse<T>(
    val items: List<T>,
    val nextCursor: String?,
    val hasMore: Boolean,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AdminLoginRequest(
    val username: String,
    val password: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AdminProfile(
    val id: Long,
    val username: String,
    val displayName: String,
    val isSuperAdmin: Boolean,
    val permissions: List<String>,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AdminLoginResponse(
    val token: String,
    val tokenType: String,
    val profile: AdminProfile,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RechargeItem(
    val id: Long,
    val requestNo: String,
    val userId: Long,
    val username: String?,
    val email: String?,
    val amount: Double,
    val status: String,
    val payerRemark: String?,
    val createdAt: String,
    val reviewedAt: String?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RechargeDetail(
    val id: Long,
    val requestNo: String,
    val userId: Long,
    val username: String?,
    val email: String?,
    val amount: Double,
    val status: String,
    val payerRemark: String?,
    val rejectReason: String?,
    val screenshotUrl: String,
    val reviewedByName: String?,
    val createdAt: String,
    val reviewedAt: String?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RejectRechargeRequest(
    val reason: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SupportSessionItem(
    val id: Long,
    val memberId: Long,
    val memberUsername: String?,
    val memberEmail: String?,
    val status: String,
    val lastMessagePreview: String?,
    val lastMessageAt: String?,
    val memberUnreadCount: Int,
    val adminUnreadCount: Int,
    val createdAt: String,
    val updatedAt: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SupportMessage(
    val id: Long,
    val sessionId: Long,
    val senderScope: String,
    val senderId: Long,
    val messageType: String,
    val content: String,
    val attachmentName: String?,
    val attachmentContentType: String?,
    val attachmentSize: Long?,
    val attachmentUrl: String?,
    val createdAt: String,
    val pending: Boolean = false,
    val tempKey: String? = null,
    val localAttachmentPath: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SupportUnread(
    val sessionId: Long,
    val memberId: Long,
    val memberUnreadCount: Int,
    val adminUnreadCount: Int,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MemberSupportSession(
    val id: Long,
    val status: String,
    val lastMessagePreview: String?,
    val lastMessageAt: String?,
    val memberUnreadCount: Int,
    val adminUnreadCount: Int,
    val createdAt: String,
    val updatedAt: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SupportSendMessageRequest(
    val sessionId: Long,
    val content: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SupportDispatchPayload(
    val message: SupportMessage,
    val adminSession: SupportSessionItem,
    val memberSession: MemberSupportSession,
    val unread: SupportUnread,
)

data class LocalSupportAttachmentDraft(
    val fileName: String,
    val contentType: String,
    val size: Long?,
    val isImage: Boolean,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class DeviceRegisterRequest(
    val vendor: String,
    val deviceToken: String,
    val deviceName: String?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class DeviceUnregisterRequest(
    val vendor: String,
    val deviceToken: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RechargeNotificationEvent(
    val type: String,
    val title: String,
    val message: String,
    val requestId: Long?,
    val createdAt: String?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class WsEnvelope<T>(
    val type: String,
    val data: T,
)
