package org.example.kawang.adminmobile.data

import java.io.IOException
import org.json.JSONObject
import retrofit2.HttpException

fun Throwable.userMessage(fallback: String): String {
    return when (this) {
        is HttpException -> parseServerMessage() ?: fallback
        is IOException -> "无法连接到服务端，请检查后端地址和局域网连接"
        else -> message?.takeIf { it.isNotBlank() } ?: fallback
    }
}

private fun HttpException.parseServerMessage(): String? {
    val body = response()?.errorBody()?.string().orEmpty()
    if (body.isBlank()) {
        return message()
    }
    return runCatching {
        JSONObject(body).optString("message").takeIf { it.isNotBlank() && it != "null" }
    }.getOrNull() ?: message()
}
