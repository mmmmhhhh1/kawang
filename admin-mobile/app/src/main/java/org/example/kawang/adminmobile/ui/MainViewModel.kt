package org.example.kawang.adminmobile.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.io.File
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.example.kawang.adminmobile.AppForegroundState
import org.example.kawang.adminmobile.data.AdminNotificationCenter
import org.example.kawang.adminmobile.data.AdminProfile
import org.example.kawang.adminmobile.data.AdminRepository
import org.example.kawang.adminmobile.data.AdminSupportSocket
import org.example.kawang.adminmobile.data.HuaweiPushRegistrar
import org.example.kawang.adminmobile.data.RechargeDetail
import org.example.kawang.adminmobile.data.RechargeItem
import org.example.kawang.adminmobile.data.RechargeNotificationEvent
import org.example.kawang.adminmobile.data.SupportDispatchPayload
import org.example.kawang.adminmobile.data.SupportMessage
import org.example.kawang.adminmobile.data.SupportSessionItem
import org.example.kawang.adminmobile.data.SupportUnread
import org.example.kawang.adminmobile.data.userMessage

enum class MobileTab { Recharges, Support }

class MainViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val SUPPORT_POLL_INTERVAL_INACTIVE_MS = 15_000L
        private const val SUPPORT_POLL_INTERVAL_LIST_MS = 6_000L
        private const val SUPPORT_POLL_INTERVAL_CHAT_WS_MS = 10_000L
        private const val SUPPORT_POLL_INTERVAL_CHAT_FALLBACK_MS = 3_500L
        private const val RECHARGE_POLL_INTERVAL_WS_MS = 7_000L
        private const val RECHARGE_POLL_INTERVAL_FALLBACK_MS = 4_000L

        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MainViewModel(application) as T
                }
            }

        private fun parseDateTime(value: String?): LocalDateTime {
            if (value.isNullOrBlank()) {
                return LocalDateTime.MIN
            }
            return runCatching { LocalDateTime.parse(value) }
                .recoverCatching { OffsetDateTime.parse(value).toLocalDateTime() }
                .recoverCatching { LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) }
                .getOrDefault(LocalDateTime.MIN)
        }

        private fun encodeCursor(createdAt: LocalDateTime, id: Long): String {
            val raw = "${createdAt.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()}:$id"
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray())
        }
    }

    private val repository = AdminRepository(application)
    private val socket = AdminSupportSocket()
    private val pushRegistrar = HuaweiPushRegistrar(application, repository)
    private val notificationCenter = AdminNotificationCenter(application)

    var profile by mutableStateOf<AdminProfile?>(null)
        private set
    var bootstrapping by mutableStateOf(true)
        private set
    var loginLoading by mutableStateOf(false)
        private set
    var rechargesLoading by mutableStateOf(false)
        private set
    var rechargeActionLoading by mutableStateOf(false)
        private set
    var supportLoading by mutableStateOf(false)
        private set
    var supportMessagesLoading by mutableStateOf(false)
        private set
    var supportSending by mutableStateOf(false)
        private set
    var wsConnected by mutableStateOf(false)
        private set
    var activeTab by mutableStateOf(MobileTab.Recharges)
        private set
    var recharges by mutableStateOf<List<RechargeItem>>(emptyList())
        private set
    var selectedRecharge by mutableStateOf<RechargeDetail?>(null)
        private set
    var supportSessions by mutableStateOf<List<SupportSessionItem>>(emptyList())
        private set
    var selectedSupportSession by mutableStateOf<SupportSessionItem?>(null)
        private set
    var supportMessages by mutableStateOf<List<SupportMessage>>(emptyList())
        private set
    var supportMessagesNextCursor by mutableStateOf<String?>(null)
        private set
    var supportMessagesHasMore by mutableStateOf(false)
        private set
    var noticeMessage by mutableStateOf<String?>(null)
        private set

    private var pendingExternalRoute: String? = null
    private var socketDesired = false
    private var socketReconnectJob: Job? = null
    private var supportPollingJob: Job? = null
    private var rechargePollingJob: Job? = null
    private var rechargesRefreshing = false
    private var supportSessionsRefreshing = false
    private var supportMessagesRefreshing = false

    val savedBaseUrl: String
        get() = repository.sessionStore.getBaseUrl()

    fun bootstrap() {
        if (!bootstrapping) {
            return
        }
        val baseUrl = repository.sessionStore.getBaseUrl()
        val token = repository.authToken()
        if (baseUrl.isBlank() || token.isBlank()) {
            bootstrapping = false
            return
        }
        viewModelScope.launch {
            runCatching { repository.fetchMe() }
                .onSuccess(::onAuthenticated)
                .onFailure {
                    repository.clearSession()
                    stopLiveSync()
                    profile = null
                }
            bootstrapping = false
        }
    }

    fun login(baseUrl: String, username: String, password: String) {
        if (loginLoading) {
            return
        }
        loginLoading = true
        viewModelScope.launch {
            runCatching { repository.login(baseUrl, username, password) }
                .onSuccess {
                    onAuthenticated(it.profile)
                    noticeMessage = "登录成功"
                }
                .onFailure {
                    noticeMessage = it.userMessage("登录失败")
                }
            loginLoading = false
            bootstrapping = false
        }
    }

    fun logout() {
        stopLiveSync()
        repository.clearSession()
        profile = null
        recharges = emptyList()
        selectedRecharge = null
        supportSessions = emptyList()
        selectedSupportSession = null
        supportMessages = emptyList()
        supportMessagesNextCursor = null
        supportMessagesHasMore = false
        activeTab = MobileTab.Recharges
        noticeMessage = "已退出登录"
    }

    fun selectTab(tab: MobileTab) {
        activeTab = if (tab == MobileTab.Recharges && profile?.isSuperAdmin != true) {
            MobileTab.Support
        } else {
            tab
        }
        syncRealtimeDemand()
    }

    fun refreshRecharges() {
        if (profile?.isSuperAdmin != true) {
            return
        }
        viewModelScope.launch {
            refreshRechargesInternal(showLoading = true, silentNotice = false)
        }
    }

    fun openRecharge(item: RechargeItem) {
        viewModelScope.launch {
            rechargeActionLoading = true
            runCatching { repository.loadRechargeDetail(item.id) }
                .onSuccess { selectedRecharge = it }
                .onFailure { noticeMessage = it.userMessage("加载充值详情失败") }
            rechargeActionLoading = false
        }
    }

    fun closeRechargeDetail() {
        selectedRecharge = null
    }

    fun approveSelectedRecharge() {
        val detail = selectedRecharge ?: return
        if (profile?.isSuperAdmin != true) {
            noticeMessage = "只有超级管理员可以审核充值"
            return
        }
        viewModelScope.launch {
            rechargeActionLoading = true
            runCatching { repository.approveRecharge(detail.id) }
                .onSuccess {
                    selectedRecharge = it
                    refreshRecharges()
                    noticeMessage = "充值已通过"
                }
                .onFailure { noticeMessage = it.userMessage("审核通过失败") }
            rechargeActionLoading = false
        }
    }

    fun rejectSelectedRecharge(reason: String) {
        val detail = selectedRecharge ?: return
        if (profile?.isSuperAdmin != true) {
            noticeMessage = "只有超级管理员可以审核充值"
            return
        }
        viewModelScope.launch {
            rechargeActionLoading = true
            runCatching { repository.rejectRecharge(detail.id, reason) }
                .onSuccess {
                    selectedRecharge = it
                    refreshRecharges()
                    noticeMessage = "充值已驳回"
                }
                .onFailure { noticeMessage = it.userMessage("驳回充值失败") }
            rechargeActionLoading = false
        }
    }

    fun refreshSupportSessions() {
        viewModelScope.launch {
            refreshSupportSessionsInternal(showLoading = true, silentNotice = false)
        }
    }

    fun openSupportSession(session: SupportSessionItem) {
        selectedSupportSession = session
        activeTab = MobileTab.Support
        val hasCachedMessages = restoreCachedSupportMessages(session.id)
        if (!hasCachedMessages) {
            supportMessages = emptyList()
            supportMessagesNextCursor = null
            supportMessagesHasMore = false
        }
        syncRealtimeDemand()
        viewModelScope.launch {
            refreshSelectedSupportMessages(
                showLoading = !hasCachedMessages,
                replaceExisting = !hasCachedMessages,
                silentNotice = false,
            )
        }
    }

    fun closeSupportSession() {
        selectedSupportSession = null
        supportMessages = emptyList()
        supportMessagesNextCursor = null
        supportMessagesHasMore = false
        syncRealtimeDemand()
    }

    fun loadOlderSupportMessages() {
        val session = selectedSupportSession ?: return
        val cursor = supportMessagesNextCursor ?: return
        if (supportMessagesLoading) {
            return
        }
        viewModelScope.launch {
            supportMessagesLoading = true
            runCatching { repository.loadSupportMessages(session.id, cursor) }
                .onSuccess { page ->
                    updateSupportMessageState(
                        mergeMessages(page.items, supportMessages),
                        nextCursor = page.nextCursor,
                        hasMore = page.hasMore,
                    )
                }
                .onFailure { noticeMessage = it.userMessage("加载更早消息失败") }
            supportMessagesLoading = false
        }
    }

    fun sendSupportMessage(content: String) {
        val session = selectedSupportSession ?: return
        val trimmed = content.trim()
        if (trimmed.isBlank() || supportSending) {
            return
        }
        val tempKey = "${System.currentTimeMillis()}-${session.id}"
        updateSupportMessageState(
            mergeMessages(
                supportMessages,
                listOf(
                    SupportMessage(
                        id = -System.currentTimeMillis(),
                        sessionId = session.id,
                        senderScope = "ADMIN",
                        senderId = profile?.id ?: 0L,
                        messageType = "TEXT",
                        content = trimmed,
                        attachmentName = null,
                        attachmentContentType = null,
                        attachmentSize = null,
                        attachmentUrl = null,
                        createdAt = LocalDateTime.now().toString(),
                        pending = true,
                        tempKey = tempKey,
                    ),
                ),
            ),
        )
        supportSending = true
        viewModelScope.launch {
            runCatching { repository.sendSupportMessage(session.id, trimmed) }
                .onSuccess { applySupportDispatch(it, tempKey) }
                .onFailure {
                    removePendingSupportMessage(tempKey)
                    noticeMessage = it.userMessage("发送消息失败")
                }
            supportSending = false
        }
    }

    fun sendSupportAttachment(fileUri: Uri, content: String) {
        val session = selectedSupportSession ?: return
        if (supportSending) {
            return
        }
        viewModelScope.launch {
            val tempKey = "${System.currentTimeMillis()}-${session.id}"
            val attachmentDraft = repository.describeSupportAttachment(fileUri)
            updateSupportMessageState(
                mergeMessages(
                    supportMessages,
                    listOf(
                        SupportMessage(
                            id = -System.currentTimeMillis(),
                            sessionId = session.id,
                            senderScope = "ADMIN",
                            senderId = profile?.id ?: 0L,
                            messageType = if (attachmentDraft.isImage) "IMAGE" else "FILE",
                            content = content.trim(),
                            attachmentName = attachmentDraft.fileName,
                            attachmentContentType = attachmentDraft.contentType,
                            attachmentSize = attachmentDraft.size,
                            attachmentUrl = null,
                            createdAt = LocalDateTime.now().toString(),
                            pending = true,
                            tempKey = tempKey,
                            localAttachmentPath = fileUri.toString(),
                        ),
                    ),
                ),
            )
            supportSending = true
            runCatching { repository.sendSupportAttachment(session.id, fileUri, content) }
                .onSuccess { applySupportDispatch(it, tempKey) }
                .onFailure {
                    removePendingSupportMessage(tempKey)
                    noticeMessage = it.userMessage("发送附件失败")
                }
            supportSending = false
        }
    }

    fun handleExternalRoute(route: String) {
        if (profile == null) {
            pendingExternalRoute = route
            return
        }
        when {
            route.startsWith("support/") -> {
                route.substringAfter("support/").toLongOrNull()?.let { sessionId ->
                    activeTab = MobileTab.Support
                    supportSessions.firstOrNull { it.id == sessionId }?.let(::openSupportSession)
                        ?: refreshSupportSessionsAndOpen(sessionId)
                }
            }
            route.startsWith("recharges/") && profile?.isSuperAdmin == true -> {
                route.substringAfter("recharges/").toLongOrNull()?.let { rechargeId ->
                    activeTab = MobileTab.Recharges
                    viewModelScope.launch {
                        rechargeActionLoading = true
                        runCatching { repository.loadRechargeDetail(rechargeId) }
                            .onSuccess { selectedRecharge = it }
                            .onFailure { noticeMessage = it.userMessage("加载充值详情失败") }
                        rechargeActionLoading = false
                    }
                }
            }
        }
    }

    fun consumeNotice(): String? = noticeMessage.also { noticeMessage = null }

    fun screenshotUrl(path: String): String = repository.screenshotUrl(path)

    fun mediaUrl(path: String): String = repository.absoluteUrl(path)

    suspend fun loadProtectedImageFile(path: String, fileNameHint: String? = null): File {
        return withContext(Dispatchers.IO) {
            repository.downloadProtectedFile(path, fileNameHint)
        }
    }

    fun openSupportAttachment(message: SupportMessage) {
        if (openLocalAttachment(message.localAttachmentPath, message)) {
            return
        }
        val attachmentUrl = message.attachmentUrl
        if (attachmentUrl.isNullOrBlank()) {
            noticeMessage = "附件不可用"
            return
        }
        viewModelScope.launch {
            runCatching {
                val file = withContext(Dispatchers.IO) {
                    repository.downloadSupportAttachment(attachmentUrl, message.attachmentName)
                }
                val resolvedMessage = message.copy(localAttachmentPath = file.absolutePath)
                replaceLocalMessage(resolvedMessage)
                openDownloadedAttachment(file, resolvedMessage)
            }.onFailure {
                noticeMessage = it.userMessage("打开附件失败")
            }
        }
    }

    private fun onAuthenticated(nextProfile: AdminProfile) {
        profile = nextProfile
        activeTab = if (nextProfile.isSuperAdmin) MobileTab.Recharges else MobileTab.Support
        val cachedSessions = repository.loadCachedSupportSessions()
        if (cachedSessions.isNotEmpty()) {
            supportSessions = sortSessions(cachedSessions)
        }
        startLiveSync()
        viewModelScope.launch {
            refreshSupportSessionsInternal(showLoading = true, silentNotice = true)
            if (nextProfile.isSuperAdmin) {
                refreshRechargesInternal(showLoading = true, silentNotice = true)
            } else {
                recharges = emptyList()
                selectedRecharge = null
            }
        }
        maybeRegisterHuaweiPush()
        pendingExternalRoute?.let {
            pendingExternalRoute = null
            handleExternalRoute(it)
        }
    }

    private fun startLiveSync() {
        stopLiveSync()
        socketDesired = true
        startSupportPolling()
        startRechargePolling()
        syncRealtimeDemand()
    }

    private fun stopLiveSync() {
        socketDesired = false
        socketReconnectJob?.cancel()
        supportPollingJob?.cancel()
        rechargePollingJob?.cancel()
        socket.disconnect()
        wsConnected = false
        supportSending = false
    }

    private fun connectSupportSocket() {
        if (!socketDesired || !needsRealtimeSupportConnection() || repository.authToken().isBlank() || repository.sessionStore.getBaseUrl().isBlank()) {
            return
        }
        socket.connect(repository.supportSocketUrl()) { event ->
            viewModelScope.launch(Dispatchers.Main.immediate) {
                when (event) {
                    AdminSupportSocket.Event.Connected -> wsConnected = true
                    AdminSupportSocket.Event.Disconnected -> {
                        wsConnected = false
                        scheduleSocketReconnect()
                    }
                    is AdminSupportSocket.Event.Error -> {
                        wsConnected = false
                        supportSending = false
                        noticeMessage = event.message
                    }
                    is AdminSupportSocket.Event.SupportDispatch -> {
                        supportSending = false
                        applySupportDispatch(event.payload)
                    }
                    is AdminSupportSocket.Event.MessageCreated -> {
                        supportSending = false
                        handleIncomingMessage(event.message)
                    }
                    is AdminSupportSocket.Event.SessionUpdated -> {
                        applySupportSessions(mergeSession(event.session))
                    }
                    is AdminSupportSocket.Event.UnreadUpdated -> applyUnreadUpdate(event.unread)
                    is AdminSupportSocket.Event.RechargeCreated -> handleRechargeEvent(event.notification)
                }
            }
        }
    }

    private fun scheduleSocketReconnect() {
        if (!socketDesired || !needsRealtimeSupportConnection() || profile == null || socketReconnectJob?.isActive == true) {
            return
        }
        socketReconnectJob = viewModelScope.launch {
            delay(2000)
            if (socketDesired && needsRealtimeSupportConnection() && profile != null) {
                connectSupportSocket()
            }
        }
    }

    private fun startSupportPolling() {
        supportPollingJob?.cancel()
        supportPollingJob = viewModelScope.launch {
            while (isActive && profile != null) {
                refreshSupportSessionsInternal(showLoading = supportSessions.isEmpty(), silentNotice = true)
                if (activeTab == MobileTab.Support && selectedSupportSession != null) {
                    refreshSelectedSupportMessages(
                        showLoading = supportMessages.isEmpty(),
                        replaceExisting = false,
                        silentNotice = true,
                    )
                }
                delay(resolveSupportPollingInterval())
            }
        }
    }

    private fun startRechargePolling() {
        rechargePollingJob?.cancel()
        if (profile?.isSuperAdmin != true) {
            return
        }
        rechargePollingJob = viewModelScope.launch {
            while (isActive && profile?.isSuperAdmin == true) {
                refreshRechargesInternal(showLoading = recharges.isEmpty(), silentNotice = true)
                delay(if (wsConnected) RECHARGE_POLL_INTERVAL_WS_MS else RECHARGE_POLL_INTERVAL_FALLBACK_MS)
            }
        }
    }

    private suspend fun refreshRechargesInternal(showLoading: Boolean, silentNotice: Boolean) {
        if (profile?.isSuperAdmin != true || rechargesRefreshing) {
            return
        }
        rechargesRefreshing = true
        if (showLoading) {
            rechargesLoading = true
        }
        runCatching { repository.loadPendingRecharges() }
            .onSuccess { items ->
                recharges = items.sortedWith(
                    compareByDescending<RechargeItem> { parseDateTime(it.createdAt) }
                        .thenByDescending { it.id },
                )
            }
            .onFailure {
                if (!silentNotice) {
                    noticeMessage = it.userMessage("刷新充值列表失败")
                }
            }
        if (showLoading) {
            rechargesLoading = false
        }
        rechargesRefreshing = false
    }

    private suspend fun refreshSupportSessionsInternal(showLoading: Boolean, silentNotice: Boolean) {
        if (supportSessionsRefreshing) {
            return
        }
        supportSessionsRefreshing = true
        if (showLoading) {
            supportLoading = true
        }
        val latestUpdatedAt = latestSupportSessionUpdatedAt()
        val loader = when {
            showLoading || supportSessions.isEmpty() || latestUpdatedAt == null -> suspend { repository.loadSupportSessions() }
            else -> suspend { repository.loadSupportSessionsUpdatedAfter(latestUpdatedAt) }
        }
        runCatching { loader() }
            .onSuccess {
                applySupportSessions(if (showLoading || supportSessions.isEmpty() || latestUpdatedAt == null) {
                    sortSessions(it)
                } else if (it.isEmpty()) {
                    supportSessions
                } else {
                    sortSessions(mergeSessionUpdates(it))
                })
            }
            .onFailure {
                if (!silentNotice) {
                    noticeMessage = it.userMessage("刷新客服会话失败")
                }
            }
        if (showLoading) {
            supportLoading = false
        }
        supportSessionsRefreshing = false
    }

    private suspend fun refreshSelectedSupportMessages(
        showLoading: Boolean,
        replaceExisting: Boolean,
        silentNotice: Boolean,
    ) {
        val session = selectedSupportSession ?: return
        if (supportMessagesRefreshing) {
            return
        }
        supportMessagesRefreshing = true
        if (showLoading) {
            supportMessagesLoading = true
        }
        val latestCursor = latestSupportMessageCursor()
        val loader = when {
            replaceExisting || supportMessages.isEmpty() -> suspend { repository.loadSupportMessages(session.id) }
            latestCursor != null -> suspend { repository.loadSupportMessagesAfter(session.id, latestCursor) }
            else -> suspend { repository.loadSupportMessages(session.id) }
        }
        runCatching { loader() }
            .onSuccess { page ->
                val nextMessages = if (replaceExisting || supportMessages.isEmpty()) {
                    page.items.sortedWith(compareBy<SupportMessage> { parseDateTime(it.createdAt) }.thenBy { it.id })
                } else if (page.items.isEmpty()) {
                    supportMessages
                } else {
                    mergeMessages(supportMessages, page.items)
                }
                val nextPageCursor = if (replaceExisting || supportMessagesNextCursor == null) page.nextCursor else supportMessagesNextCursor
                val nextHasMore = if (replaceExisting || supportMessagesNextCursor == null) page.hasMore else supportMessagesHasMore
                updateSupportMessageState(nextMessages, nextPageCursor, nextHasMore)
                if ((selectedSupportSession?.adminUnreadCount ?: 0) > 0) {
                    markCurrentSupportRead()
                }
            }
            .onFailure {
                if (!silentNotice) {
                    noticeMessage = it.userMessage("刷新客服消息失败")
                }
            }
        if (showLoading) {
            supportMessagesLoading = false
        }
        supportMessagesRefreshing = false
    }

    private fun handleIncomingMessage(message: SupportMessage) {
        val fromMember = message.senderScope.equals("member", ignoreCase = true)
        if (selectedSupportSession?.id == message.sessionId) {
            updateSupportMessageState(mergeMessages(supportMessages, listOf(message)))
            if (fromMember) {
                markCurrentSupportRead()
            }
            return
        }
        if (fromMember && AppForegroundState.isInForeground) {
            notificationCenter.showSupportNotification(
                sessionId = message.sessionId,
                title = "新的客服消息",
                message = buildSupportMessagePreview(message),
            )
        }
    }

    private fun applySupportDispatch(payload: SupportDispatchPayload, tempKey: String? = null) {
        val pendingMessage = tempKey?.let { key -> supportMessages.firstOrNull { it.tempKey == key } }
        val message = if (pendingMessage?.localAttachmentPath != null && payload.message.localAttachmentPath.isNullOrBlank()) {
            payload.message.copy(localAttachmentPath = pendingMessage.localAttachmentPath)
        } else {
            payload.message
        }
        applySupportSessions(mergeSession(payload.adminSession))
        applyUnreadUpdate(payload.unread)
        if (selectedSupportSession?.id == message.sessionId) {
            val baseMessages = if (tempKey == null) supportMessages else supportMessages.filterNot { it.tempKey == tempKey }
            updateSupportMessageState(mergeMessages(baseMessages, listOf(message)))
            return
        }
        if (
            message.senderScope.equals("member", ignoreCase = true) &&
            AppForegroundState.isInForeground
        ) {
            notificationCenter.showSupportNotification(
                sessionId = message.sessionId,
                title = "新的客服消息",
                message = buildSupportMessagePreview(message),
            )
        }
    }

    private fun applyUnreadUpdate(unread: SupportUnread) {
        applySupportSessions(supportSessions.map { session ->
            if (session.id == unread.sessionId) {
                session.copy(
                    memberUnreadCount = unread.memberUnreadCount,
                    adminUnreadCount = unread.adminUnreadCount,
                )
            } else {
                session
            }
        })
        if (selectedSupportSession?.id == unread.sessionId) {
            selectedSupportSession = selectedSupportSession?.copy(
                memberUnreadCount = unread.memberUnreadCount,
                adminUnreadCount = unread.adminUnreadCount,
            )
        }
    }

    private fun handleRechargeEvent(notification: RechargeNotificationEvent) {
        if (profile?.isSuperAdmin != true) {
            return
        }
        if (AppForegroundState.isInForeground) {
            notificationCenter.showRechargeNotification(
                requestId = notification.requestId,
                title = notification.title,
                message = notification.message,
            )
        }
        noticeMessage = "${notification.title}: ${notification.message}"
        viewModelScope.launch {
            refreshRechargesInternal(showLoading = false, silentNotice = true)
        }
    }

    private fun markCurrentSupportRead() {
        val session = selectedSupportSession ?: return
        viewModelScope.launch {
            runCatching { repository.markSupportRead(session.id) }
                .onSuccess(::applyUnreadUpdate)
        }
    }

    private fun maybeRegisterHuaweiPush() {
        val brand = Build.BRAND.orEmpty().lowercase(Locale.ROOT)
        val manufacturer = Build.MANUFACTURER.orEmpty().lowercase(Locale.ROOT)
        if (
            !brand.contains("huawei") &&
            !brand.contains("honor") &&
            !manufacturer.contains("huawei") &&
            !manufacturer.contains("honor")
        ) {
            return
        }
        viewModelScope.launch {
            pushRegistrar.registerIfPossible()
        }
    }

    private fun buildSupportMessagePreview(message: SupportMessage): String {
        val text = message.content.trim()
        if (text.isNotBlank()) {
            return text
        }
        val attachmentName = message.attachmentName?.takeIf { it.isNotBlank() } ?: "附件"
        return if (message.messageType.equals("IMAGE", ignoreCase = true)) {
            "[图片] $attachmentName"
        } else {
            "[文件] $attachmentName"
        }
    }

    private fun openDownloadedAttachment(file: File, message: SupportMessage) {
        val application = getApplication<Application>()
        val uri = FileProvider.getUriForFile(application, "${application.packageName}.fileprovider", file)
        openAttachmentUri(uri, message)
    }

    private fun openLocalAttachment(localAttachmentPath: String?, message: SupportMessage): Boolean {
        if (localAttachmentPath.isNullOrBlank()) {
            return false
        }
        val application = getApplication<Application>()
        val uri = when {
            localAttachmentPath.startsWith("content://") || localAttachmentPath.startsWith("file://") -> Uri.parse(localAttachmentPath)
            else -> {
                val file = File(localAttachmentPath)
                if (!file.exists() || !file.isFile) {
                    return false
                }
                FileProvider.getUriForFile(application, "${application.packageName}.fileprovider", file)
            }
        }
        return runCatching {
            openAttachmentUri(uri, message)
        }.isSuccess
    }

    private fun openAttachmentUri(uri: Uri, message: SupportMessage) {
        val application = getApplication<Application>()
        val mimeType = message.attachmentContentType
            ?: application.contentResolver.getType(uri)
            ?: "application/octet-stream"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, message.attachmentName ?: "打开附件").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        application.startActivity(chooser)
    }

    private fun applySupportSessions(items: List<SupportSessionItem>) {
        supportSessions = items
        repository.saveSupportSessions(items)
        syncSelectedSession()
    }

    private fun updateSupportMessageState(
        items: List<SupportMessage>,
        nextCursor: String? = supportMessagesNextCursor,
        hasMore: Boolean = supportMessagesHasMore,
    ) {
        supportMessages = items
        supportMessagesNextCursor = nextCursor
        supportMessagesHasMore = hasMore
        persistSelectedSupportMessages()
    }

    private fun persistSelectedSupportMessages() {
        val sessionId = selectedSupportSession?.id ?: return
        repository.saveSupportMessages(
            sessionId,
            supportMessagesNextCursor,
            supportMessagesHasMore,
            supportMessages.filterNot { it.pending },
        )
    }

    private fun restoreCachedSupportMessages(sessionId: Long): Boolean {
        val cached = repository.loadCachedSupportMessages(sessionId) ?: return false
        updateSupportMessageState(
            items = cached.items.sortedWith(compareBy<SupportMessage> { parseDateTime(it.createdAt) }.thenBy { it.id }),
            nextCursor = cached.nextCursor,
            hasMore = cached.hasMore,
        )
        return cached.items.isNotEmpty()
    }

    private fun removePendingSupportMessage(tempKey: String) {
        updateSupportMessageState(supportMessages.filterNot { it.tempKey == tempKey })
    }

    private fun replaceLocalMessage(message: SupportMessage) {
        updateSupportMessageState(
            supportMessages.map { current ->
                if (current.id == message.id) {
                    message
                } else {
                    current
                }
            },
        )
    }

    private fun latestSupportMessageCursor(): String? {
        val latest = supportMessages.lastOrNull() ?: return null
        val createdAt = parseDateTime(latest.createdAt)
        return if (createdAt == LocalDateTime.MIN) {
            null
        } else {
            encodeCursor(createdAt, latest.id)
        }
    }

    private fun mergeMessages(existing: List<SupportMessage>, incoming: List<SupportMessage>): List<SupportMessage> {
        val merged = linkedMapOf<Long, SupportMessage>()
        (existing + incoming)
            .sortedWith(compareBy<SupportMessage> { parseDateTime(it.createdAt) }.thenBy { it.id })
            .forEach { merged[it.id] = it }
        return merged.values.toList()
    }

    private fun sortSessions(items: List<SupportSessionItem>): List<SupportSessionItem> {
        return items.sortedWith(
            compareByDescending<SupportSessionItem> { parseDateTime(it.lastMessageAt) ?: parseDateTime(it.updatedAt) }
                .thenByDescending { it.id },
        )
    }

    private fun mergeSession(session: SupportSessionItem): List<SupportSessionItem> {
        val merged = supportSessions.toMutableList()
        val index = merged.indexOfFirst { it.id == session.id }
        if (index >= 0) {
            merged[index] = session
        } else {
            merged += session
        }
        return sortSessions(merged)
    }

    private fun mergeSessionUpdates(incoming: List<SupportSessionItem>): List<SupportSessionItem> {
        val merged = supportSessions.associateBy { it.id }.toMutableMap()
        incoming.forEach { merged[it.id] = it }
        return merged.values.toList()
    }

    private fun latestSupportSessionUpdatedAt(): String? {
        val latest = supportSessions.maxByOrNull { parseDateTime(it.updatedAt) } ?: return null
        return latest.updatedAt
    }

    private fun syncSelectedSession() {
        val selectedId = selectedSupportSession?.id ?: return
        selectedSupportSession = supportSessions.firstOrNull { it.id == selectedId }
    }

    private fun syncRealtimeDemand() {
        if (needsRealtimeSupportConnection()) {
            connectSupportSocket()
        } else {
            socketReconnectJob?.cancel()
            socket.disconnect()
            wsConnected = false
        }
    }

    private fun needsRealtimeSupportConnection(): Boolean {
        return socketDesired && activeTab == MobileTab.Support && selectedSupportSession != null
    }

    private fun resolveSupportPollingInterval(): Long {
        if (activeTab != MobileTab.Support) {
            return SUPPORT_POLL_INTERVAL_INACTIVE_MS
        }
        if (selectedSupportSession == null) {
            return SUPPORT_POLL_INTERVAL_LIST_MS
        }
        return if (wsConnected) SUPPORT_POLL_INTERVAL_CHAT_WS_MS else SUPPORT_POLL_INTERVAL_CHAT_FALLBACK_MS
    }

    private fun refreshSupportSessionsAndOpen(sessionId: Long) {
        viewModelScope.launch {
            runCatching { repository.loadSupportSessions() }
                .onSuccess { sessions ->
                    applySupportSessions(sortSessions(sessions))
                    supportSessions.firstOrNull { it.id == sessionId }?.let(::openSupportSession)
                }
                .onFailure { noticeMessage = it.userMessage("加载客服会话失败") }
        }
    }

    override fun onCleared() {
        stopLiveSync()
        super.onCleared()
    }

}
