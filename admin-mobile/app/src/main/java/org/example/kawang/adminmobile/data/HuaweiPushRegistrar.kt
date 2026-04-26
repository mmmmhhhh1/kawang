package org.example.kawang.adminmobile.data

import android.content.Context
import android.os.Build
import com.huawei.hms.aaid.HmsInstanceId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.example.kawang.adminmobile.BuildConfig

class HuaweiPushRegistrar(
    private val context: Context,
    private val repository: AdminRepository,
) {

    suspend fun registerIfPossible() = withContext(Dispatchers.IO) {
        runCatching {
            val appId = BuildConfig.HUAWEI_PUSH_APP_ID
            if (appId.isNullOrBlank()) {
                return@runCatching
            }
            val token = HmsInstanceId.getInstance(context).getToken(appId, "HCM")
            if (!token.isNullOrBlank()) {
                repository.registerDevice(
                    vendor = "HUAWEI",
                    token = token,
                    deviceName = "${Build.MANUFACTURER} ${Build.MODEL}",
                )
            }
        }
    }
}
