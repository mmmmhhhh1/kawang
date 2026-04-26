package org.example.kawang.adminmobile

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.kawang.adminmobile.ui.AdminMobileApp
import org.example.kawang.adminmobile.ui.MainViewModel

class MainActivity : ComponentActivity() {

    private val pendingRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingRoute.value = extractRoute(intent)
        setContent {
            val appViewModel: MainViewModel = viewModel(
                factory = MainViewModel.factory(application),
            )
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
            ) { }
            val currentRoute = pendingRoute.value

            LaunchedEffect(Unit) {
                appViewModel.bootstrap()
            }
            LaunchedEffect(appViewModel.profile) {
                if (appViewModel.profile != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            LaunchedEffect(currentRoute, appViewModel.profile) {
                currentRoute?.let {
                    appViewModel.handleExternalRoute(it)
                    pendingRoute.value = null
                }
            }

            AdminMobileApp(viewModel = appViewModel)
        }
    }

    override fun onStart() {
        super.onStart()
        AppForegroundState.isInForeground = true
    }

    override fun onStop() {
        AppForegroundState.isInForeground = false
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingRoute.value = extractRoute(intent)
    }

    private fun extractRoute(intent: Intent?): String? {
        if (intent == null) {
            return null
        }
        intent.getStringExtra("route")?.takeIf { it.isNotBlank() }?.let { return it }
        intent.data?.getQueryParameter("route")?.takeIf { it.isNotBlank() }?.let { return it }
        val data = intent.data ?: return null
        val host = data.host
        val path = data.path?.trim('/') ?: ""
        return when {
            !host.isNullOrBlank() && path.isNotBlank() -> "$host/$path"
            !host.isNullOrBlank() -> host
            path.isNotBlank() -> path
            else -> null
        }
    }
}
