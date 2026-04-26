@file:OptIn(ExperimentalMaterial3Api::class)

package org.example.kawang.adminmobile.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class TabSpec(val tab: MobileTab, val label: String, val icon: ImageVector)

@Composable
fun AdminMobileApp(viewModel: MainViewModel) {
    val snackbarHostState = remember { SnackbarHostState() }
    val notice = viewModel.noticeMessage

    LaunchedEffect(notice) {
        viewModel.consumeNotice()?.let { snackbarHostState.showSnackbar(it) }
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            when {
                viewModel.bootstrapping -> LoadingScreen("正在恢复管理员会话")
                viewModel.profile == null -> LoginScreen(viewModel)
                else -> MainShell(viewModel, snackbarHostState)
            }
        }
    }
}

@Composable
private fun LoginScreen(viewModel: MainViewModel) {
    var baseUrl by rememberSaveable { mutableStateOf(viewModel.savedBaseUrl) }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
    ) {
        Spacer(modifier = Modifier.weight(1f))
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Kawang 管理端", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "在手机上处理充值审核并回复会员客服消息。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(18.dp))
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("后端地址") },
                    placeholder = { Text("https://你的域名/") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("管理员账号") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("管理员密码") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(18.dp))
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !viewModel.loginLoading,
                    onClick = { viewModel.login(baseUrl, username, password) },
                ) {
                    Text(if (viewModel.loginLoading) "登录中..." else "登录")
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun MainShell(viewModel: MainViewModel, snackbarHostState: SnackbarHostState) {
    val profile = viewModel.profile ?: return
    val unreadSupportCount = viewModel.supportSessions.sumOf { it.adminUnreadCount }
    val tabs = remember(profile.isSuperAdmin) {
        buildList {
            if (profile.isSuperAdmin) {
                add(TabSpec(MobileTab.Recharges, "充值审核", Icons.Default.CreditCard))
            }
            add(TabSpec(MobileTab.Support, "在线客服", Icons.Default.Chat))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(profile.displayName.ifBlank { profile.username })
                        Text(
                            if (profile.isSuperAdmin) "超级管理员" else "客服管理员",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (viewModel.activeTab == MobileTab.Recharges) {
                                viewModel.refreshRecharges()
                            } else {
                                viewModel.refreshSupportSessions()
                            }
                        },
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                    IconButton(onClick = viewModel::logout) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "退出登录")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEach { spec ->
                    NavigationBarItem(
                        selected = viewModel.activeTab == spec.tab,
                        onClick = { viewModel.selectTab(spec.tab) },
                        icon = {
                            if (spec.tab == MobileTab.Support && unreadSupportCount > 0) {
                                BadgedBox(
                                    badge = {
                                        Badge {
                                            Text(unreadSupportCount.coerceAtMost(99).toString())
                                        }
                                    },
                                ) {
                                    Icon(spec.icon, contentDescription = spec.label)
                                }
                            } else {
                                Icon(spec.icon, contentDescription = spec.label)
                            }
                        },
                        label = { Text(spec.label) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        when (viewModel.activeTab) {
            MobileTab.Recharges -> RechargeScreen(
                modifier = Modifier.padding(paddingValues),
                profile = profile,
                loading = viewModel.rechargesLoading || viewModel.rechargeActionLoading,
                recharges = viewModel.recharges,
                selectedRecharge = viewModel.selectedRecharge,
                onRefresh = viewModel::refreshRecharges,
                onOpenRecharge = viewModel::openRecharge,
                onCloseRecharge = viewModel::closeRechargeDetail,
                onApprove = viewModel::approveSelectedRecharge,
                onReject = viewModel::rejectSelectedRecharge,
                loadProtectedImageFile = viewModel::loadProtectedImageFile,
            )
            MobileTab.Support -> SupportScreen(
                modifier = Modifier.padding(paddingValues),
                loading = viewModel.supportLoading,
                messagesLoading = viewModel.supportMessagesLoading,
                sending = viewModel.supportSending,
                sessions = viewModel.supportSessions,
                selectedSession = viewModel.selectedSupportSession,
                messages = viewModel.supportMessages,
                hasMoreMessages = viewModel.supportMessagesHasMore,
                wsConnected = viewModel.wsConnected,
                loadProtectedImageFile = viewModel::loadProtectedImageFile,
                onRefresh = viewModel::refreshSupportSessions,
                onOpenSession = viewModel::openSupportSession,
                onBack = viewModel::closeSupportSession,
                onLoadOlder = viewModel::loadOlderSupportMessages,
                onSend = viewModel::sendSupportMessage,
                onSendAttachment = viewModel::sendSupportAttachment,
                onOpenAttachment = viewModel::openSupportAttachment,
            )
        }
    }
}
