package org.example.kawang.adminmobile.data

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import java.io.File
import java.io.IOException
import java.net.URLConnection
import java.util.Locale
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory

class AdminRepository(context: Context) {

    companion object {
        private const val TAG = "AdminRepository"
        private const val CACHE_DIR_NAME = "admin-support-attachments"
        private const val UPLOAD_CACHE_DIR_NAME = "admin-support-upload-staging"
    }

    private val appContext = context.applicationContext
    private val objectMapper = ObjectMapper()
        .findAndRegisterModules()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    private val supportLocalCache = SupportLocalCache(appContext, objectMapper)

    val sessionStore = SessionStore(appContext)

    private fun createApi(baseUrl: String, token: String? = sessionStore.getToken()): AdminApi {
        val client = createHttpClient(token = token, enableLogging = true)
        return Retrofit.Builder()
            .baseUrl(normalizeBaseUrl(baseUrl))
            .client(client)
            .addConverterFactory(JacksonConverterFactory.create(objectMapper))
            .build()
            .create(AdminApi::class.java)
    }

    private fun createHttpClient(token: String? = sessionStore.getToken(), enableLogging: Boolean): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            val builder = original.newBuilder()
            if (!token.isNullOrBlank()) {
                builder.addHeader("Authorization", "Bearer $token")
            }
            chain.proceed(builder.build())
        }
        val builder = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
        if (enableLogging) {
            builder.addInterceptor(logging)
        }
        return builder.build()
    }

    suspend fun login(baseUrl: String, username: String, password: String): AdminLoginResponse {
        val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
        Log.d(TAG, "login start baseUrl=$normalizedBaseUrl username=$username")
        return runCatching {
            val apiResponse = createApi(normalizedBaseUrl, token = null).login(AdminLoginRequest(username, password))
            Log.d(
                TAG,
                "login api response code=${apiResponse.code} message=${apiResponse.message} hasData=${apiResponse.data != null}",
            )
            val response = apiResponse.data
            sessionStore.saveSession(normalizedBaseUrl, response)
            Log.d(
                TAG,
                "login success username=$username adminId=${response.profile.id} tokenLength=${response.token.length}",
            )
            response
        }.onFailure {
            Log.e(TAG, "login failed baseUrl=$normalizedBaseUrl username=$username", it)
        }.getOrThrow()
    }

    suspend fun fetchMe(): AdminProfile {
        val api = createApi(sessionStore.getBaseUrl())
        Log.d(TAG, "fetchMe start baseUrl=${sessionStore.getBaseUrl()}")
        return runCatching {
            val response = api.me()
            Log.d(TAG, "fetchMe response code=${response.code} message=${response.message}")
            response.data
        }.onFailure {
            Log.e(TAG, "fetchMe failed", it)
        }.getOrThrow()
    }

    suspend fun loadPendingRecharges(): List<RechargeItem> {
        val api = createApi(sessionStore.getBaseUrl())
        return api.getRecharges(status = "PENDING").data.items
    }

    suspend fun loadRechargeDetail(id: Long): RechargeDetail {
        val api = createApi(sessionStore.getBaseUrl())
        return api.getRechargeDetail(id).data
    }

    suspend fun approveRecharge(id: Long): RechargeDetail {
        val api = createApi(sessionStore.getBaseUrl())
        return api.approveRecharge(id).data
    }

    suspend fun rejectRecharge(id: Long, reason: String): RechargeDetail {
        val api = createApi(sessionStore.getBaseUrl())
        return api.rejectRecharge(id, RejectRechargeRequest(reason)).data
    }

    suspend fun loadSupportSessions(): List<SupportSessionItem> {
        val api = createApi(sessionStore.getBaseUrl())
        return api.getSupportSessions().data.items
    }

    fun loadCachedSupportSessions(): List<SupportSessionItem> = supportLocalCache.readSupportSessions()

    fun saveSupportSessions(items: List<SupportSessionItem>) {
        supportLocalCache.writeSupportSessions(items)
    }

    suspend fun loadSupportSessionsUpdatedAfter(updatedAfter: String): List<SupportSessionItem> {
        val api = createApi(sessionStore.getBaseUrl())
        return api.getSupportSessions(size = 100, updatedAfter = updatedAfter).data.items
    }

    suspend fun loadSupportMessages(sessionId: Long, cursor: String? = null): CursorPageResponse<SupportMessage> {
        val api = createApi(sessionStore.getBaseUrl())
        return api.getSupportMessages(sessionId, cursor = cursor).data
    }

    fun loadCachedSupportMessages(sessionId: Long): CachedSupportMessages? = supportLocalCache.readSupportMessages(sessionId)

    fun saveSupportMessages(sessionId: Long, nextCursor: String?, hasMore: Boolean, items: List<SupportMessage>) {
        supportLocalCache.writeSupportMessages(sessionId, nextCursor, hasMore, items)
    }

    suspend fun loadSupportMessagesAfter(sessionId: Long, after: String): CursorPageResponse<SupportMessage> {
        val api = createApi(sessionStore.getBaseUrl())
        return api.getSupportMessages(sessionId, after = after).data
    }

    suspend fun sendSupportMessage(sessionId: Long, content: String): SupportDispatchPayload {
        val api = createApi(sessionStore.getBaseUrl())
        return api.sendSupportMessage(
            sessionId = sessionId,
            request = SupportSendMessageRequest(sessionId = sessionId, content = content),
        ).data
    }

    suspend fun sendSupportAttachment(sessionId: Long, fileUri: Uri, content: String?): SupportDispatchPayload {
        val api = createApi(sessionStore.getBaseUrl())
        val attachment = prepareAttachment(fileUri)
        Log.d(
            TAG,
            "sendSupportAttachment sessionId=$sessionId uri=$fileUri fileName=${attachment.fileName} contentType=${attachment.contentType} size=${attachment.tempFile.length()}",
        )
        val payload = attachment.tempFile.asRequestBody(attachment.contentType?.toMediaTypeOrNull())
        val contentBody = content?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.toRequestBody("text/plain".toMediaTypeOrNull())
        return try {
            api.sendSupportAttachment(
                sessionId = sessionId,
                file = MultipartBody.Part.createFormData("file", attachment.fileName, payload),
                content = contentBody,
            ).data
        } finally {
            attachment.tempFile.delete()
        }
    }

    suspend fun markSupportRead(sessionId: Long): SupportUnread {
        val api = createApi(sessionStore.getBaseUrl())
        return api.markSupportRead(sessionId).data
    }

    suspend fun registerDevice(vendor: String, token: String, deviceName: String?) {
        val api = createApi(sessionStore.getBaseUrl())
        api.registerDevice(DeviceRegisterRequest(vendor = vendor, deviceToken = token, deviceName = deviceName))
    }

    suspend fun unregisterDevice(vendor: String, token: String) {
        val api = createApi(sessionStore.getBaseUrl())
        api.unregisterDevice(DeviceUnregisterRequest(vendor = vendor, deviceToken = token))
    }

    fun supportSocketUrl(): String {
        val base = normalizeBaseUrl(sessionStore.getBaseUrl()).removeSuffix("/")
        val wsBase = if (base.startsWith("https://")) {
            base.replaceFirst("https://", "wss://")
        } else {
            base.replaceFirst("http://", "ws://")
        }
        return "$wsBase/ws/admin/support?token=${sessionStore.getToken()}"
    }

    fun absoluteUrl(path: String): String {
        return if (path.startsWith("http://") || path.startsWith("https://")) {
            path
        } else {
            normalizeBaseUrl(sessionStore.getBaseUrl()).removeSuffix("/") + path
        }
    }

    fun screenshotUrl(path: String): String = absoluteUrl(path)

    suspend fun downloadSupportAttachment(attachmentUrl: String, attachmentName: String?): File {
        return downloadProtectedFile(attachmentUrl, attachmentName)
    }

    suspend fun downloadProtectedFile(path: String, fileNameHint: String?): File {
        supportLocalCache.findAttachment(path)?.let { return it }
        val api = createApi(sessionStore.getBaseUrl())
        val responseBody = api.downloadFile(absoluteUrl(path))
        val fileNameSource = fileNameHint ?: path.substringAfterLast('/')
        responseBody.use { body ->
            body.byteStream().use { input ->
                return supportLocalCache.storeAttachment(
                    cacheKey = path,
                    fileNameHint = fileNameSource,
                    contentType = body.contentType()?.toString()
                        ?: URLConnection.guessContentTypeFromName(fileNameSource),
                    input = input,
                )
            }
        }
    }

    fun authToken(): String = sessionStore.getToken()

    fun clearSession() {
        sessionStore.clear()
        supportLocalCache.clear()
    }

    fun describeSupportAttachment(uri: Uri): LocalSupportAttachmentDraft {
        val metadata = runCatching {
            appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) readAttachmentMetadata(cursor) else null
            }
        }.getOrNull()
        val fallbackName = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "attachment"
        val contentType = appContext.contentResolver.getType(uri)
            ?: URLConnection.guessContentTypeFromName(metadata?.fileName ?: fallbackName)
            ?: "application/octet-stream"
        val fileName = sanitizeFileName(metadata?.fileName ?: fallbackName, contentType)
        return LocalSupportAttachmentDraft(
            fileName = fileName,
            contentType = contentType,
            size = metadata?.size,
            isImage = contentType.lowercase(Locale.ROOT).startsWith("image/"),
        )
    }

    private fun normalizeBaseUrl(value: String): String {
        val trimmed = value.trim()
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }

    private fun prepareAttachment(uri: Uri): SelectedAttachment {
        val metadata = runCatching {
            appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) readAttachmentMetadata(cursor) else null
            }
        }.getOrNull()
        val fallbackName = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "attachment"
        val contentType = appContext.contentResolver.getType(uri)
            ?: URLConnection.guessContentTypeFromName(metadata?.fileName ?: fallbackName)
            ?: "application/octet-stream"
        val fileName = sanitizeFileName(metadata?.fileName ?: fallbackName, contentType)
        val tempFile = copyUriToCache(uri, fileName)
        return SelectedAttachment(fileName = fileName, contentType = contentType, tempFile = tempFile)
    }

    private fun copyUriToCache(uri: Uri, fileName: String): File {
        val cacheDir = File(appContext.cacheDir, UPLOAD_CACHE_DIR_NAME).apply { mkdirs() }
        val target = File(cacheDir, "${System.currentTimeMillis()}-$fileName")
        val bytesCopied = appContext.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IOException("无法读取所选文件，请重新选择后再试")
        if (bytesCopied <= 0L || !target.exists() || target.length() <= 0L) {
            target.delete()
            throw IOException("所选文件内容为空，请重新选择后再试")
        }
        return target
    }

    private fun readAttachmentMetadata(cursor: Cursor): AttachmentMetadata {
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
        val fileName = if (nameIndex >= 0) cursor.getString(nameIndex) else null
        val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else null
        return AttachmentMetadata(fileName = fileName, size = size)
    }

    private fun sanitizeFileName(value: String, contentType: String?): String {
        val fallbackExtension = guessExtension(contentType)
        val trimmed = value.trim().ifBlank { "attachment$fallbackExtension" }
        val baseName = trimmed.substringAfterLast('/').substringAfterLast('\\')
        val withoutControls = buildString(baseName.length) {
            baseName.codePoints()
                .filter { !Character.isISOControl(it) }
                .filter { it <= Char.MAX_VALUE.code }
                .forEach { appendCodePoint(it) }
        }
        val cleaned = withoutControls
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { "attachment$fallbackExtension" }

        val extensionIndex = cleaned.lastIndexOf('.')
        var extension = if (extensionIndex > 0) cleaned.substring(extensionIndex) else ""
        var name = if (extensionIndex > 0) cleaned.substring(0, extensionIndex) else cleaned
        if (extension.isBlank() || extension.length > 16 || !extension.matches(Regex("\\.[A-Za-z0-9]{1,10}"))) {
            extension = fallbackExtension
        }
        if (name.isBlank()) {
            name = "attachment"
        }
        val maxNameLength = (180 - extension.length).coerceAtLeast(1)
        if (name.length > maxNameLength) {
            name = name.take(maxNameLength)
        }
        val result = "$name$extension".trim().trimEnd('.', ' ')
        return result.ifBlank { "attachment$fallbackExtension" }
    }

    private fun guessExtension(contentType: String?): String {
        val normalized = contentType?.lowercase(Locale.ROOT)?.trim().orEmpty()
        return when {
            normalized.startsWith("image/jpeg") -> ".jpg"
            normalized.startsWith("image/png") -> ".png"
            normalized.startsWith("image/webp") -> ".webp"
            normalized.startsWith("image/heic") -> ".heic"
            normalized.startsWith("image/heif") -> ".heif"
            normalized.startsWith("image/gif") -> ".gif"
            normalized == "application/pdf" -> ".pdf"
            else -> ""
        }
    }

    private data class AttachmentMetadata(
        val fileName: String?,
        val size: Long?,
    )

    private data class SelectedAttachment(
        val fileName: String,
        val contentType: String,
        val tempFile: File,
    )
}
