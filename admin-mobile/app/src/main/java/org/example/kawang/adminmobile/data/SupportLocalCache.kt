package org.example.kawang.adminmobile.data

import android.content.Context
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale

data class CachedSupportMessages(
    val sessionId: Long,
    val nextCursor: String?,
    val hasMore: Boolean,
    val items: List<SupportMessage>,
)

class SupportLocalCache(
    context: Context,
    private val objectMapper: ObjectMapper,
) {

    companion object {
        private const val ROOT_DIR_NAME = "admin-support-local-cache"
        private const val STATE_DIR_NAME = "state"
        private const val ATTACHMENT_DIR_NAME = "attachments"
        private const val SESSION_CACHE_LIMIT = 200
        private const val MESSAGE_CACHE_LIMIT = 200
    }

    private val rootDir = File(context.filesDir, ROOT_DIR_NAME)
    private val stateDir = File(rootDir, STATE_DIR_NAME)
    private val attachmentDir = File(rootDir, ATTACHMENT_DIR_NAME)
    private val sessionsFile = File(stateDir, "support-sessions.json")

    fun readSupportSessions(): List<SupportSessionItem> {
        return readJson(sessionsFile, object : TypeReference<List<SupportSessionItem>>() {})
            ?.takeLast(SESSION_CACHE_LIMIT)
            .orEmpty()
    }

    fun writeSupportSessions(items: List<SupportSessionItem>) {
        writeJson(sessionsFile, items.takeLast(SESSION_CACHE_LIMIT))
    }

    fun readSupportMessages(sessionId: Long): CachedSupportMessages? {
        val cache = readJson(messageFile(sessionId), object : TypeReference<CachedSupportMessages>() {}) ?: return null
        return cache.copy(items = cache.items.takeLast(MESSAGE_CACHE_LIMIT))
    }

    fun writeSupportMessages(sessionId: Long, nextCursor: String?, hasMore: Boolean, items: List<SupportMessage>) {
        writeJson(
            messageFile(sessionId),
            CachedSupportMessages(
                sessionId = sessionId,
                nextCursor = nextCursor,
                hasMore = hasMore,
                items = items.takeLast(MESSAGE_CACHE_LIMIT),
            ),
        )
    }

    fun findAttachment(cacheKey: String): File? {
        if (cacheKey.isBlank() || !attachmentDir.exists()) {
            return null
        }
        val prefix = hashKey(cacheKey)
        return attachmentDir.listFiles()
            ?.firstOrNull { it.isFile && it.name.startsWith(prefix) && it.length() > 0L }
    }

    fun storeAttachment(cacheKey: String, fileNameHint: String?, contentType: String?, input: InputStream): File {
        attachmentDir.mkdirs()
        val target = attachmentFile(cacheKey, fileNameHint, contentType)
        findAttachment(cacheKey)?.takeIf { it.absolutePath != target.absolutePath }?.delete()
        val tempFile = File(attachmentDir, "${target.name}.tmp")
        input.use { source ->
            tempFile.outputStream().use { output -> source.copyTo(output) }
        }
        if (target.exists()) {
            target.delete()
        }
        tempFile.renameTo(target)
        return target
    }

    fun clear() {
        rootDir.deleteRecursively()
    }

    private fun messageFile(sessionId: Long): File = File(stateDir, "support-messages-$sessionId.json")

    private fun attachmentFile(cacheKey: String, fileNameHint: String?, contentType: String?): File {
        val extension = resolveExtension(fileNameHint, contentType)
        return File(attachmentDir, "${hashKey(cacheKey)}$extension")
    }

    private fun resolveExtension(fileNameHint: String?, contentType: String?): String {
        val sanitizedName = fileNameHint?.trim().orEmpty().substringAfterLast('/').substringAfterLast('\\')
        val dotIndex = sanitizedName.lastIndexOf('.')
        val explicitExtension = if (dotIndex > 0) sanitizedName.substring(dotIndex).lowercase(Locale.ROOT) else ""
        if (explicitExtension.matches(Regex("\\.[a-z0-9]{1,10}"))) {
            return explicitExtension
        }

        val normalizedContentType = contentType?.lowercase(Locale.ROOT).orEmpty()
        return when {
            normalizedContentType.startsWith("image/jpeg") -> ".jpg"
            normalizedContentType.startsWith("image/png") -> ".png"
            normalizedContentType.startsWith("image/webp") -> ".webp"
            normalizedContentType.startsWith("image/heic") -> ".heic"
            normalizedContentType.startsWith("image/heif") -> ".heif"
            normalizedContentType.startsWith("image/gif") -> ".gif"
            normalizedContentType == "application/pdf" -> ".pdf"
            else -> ""
        }
    }

    private fun hashKey(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return buildString(digest.size * 2) {
            digest.forEach { append("%02x".format(it)) }
        }
    }

    private fun <T> readJson(file: File, typeReference: TypeReference<T>): T? {
        if (!file.exists() || file.length() <= 0L) {
            return null
        }
        return runCatching { objectMapper.readValue(file, typeReference) }.getOrNull()
    }

    private fun writeJson(file: File, value: Any) {
        runCatching {
            stateDir.mkdirs()
            file.parentFile?.mkdirs()
            val tempFile = File(file.parentFile, "${file.name}.tmp")
            objectMapper.writeValue(tempFile, value)
            if (file.exists()) {
                file.delete()
            }
            tempFile.renameTo(file)
        }
    }
}
