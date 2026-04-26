@file:OptIn(ExperimentalMaterial3Api::class)

package org.example.kawang.adminmobile.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.example.kawang.adminmobile.data.AdminProfile
import org.example.kawang.adminmobile.data.RechargeDetail
import org.example.kawang.adminmobile.data.RechargeItem
import org.example.kawang.adminmobile.data.SupportMessage
import org.example.kawang.adminmobile.data.SupportSessionItem
import java.io.File
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun RechargeScreen(
    modifier: Modifier = Modifier,
    profile: AdminProfile,
    loading: Boolean,
    recharges: List<RechargeItem>,
    selectedRecharge: RechargeDetail?,
    onRefresh: () -> Unit,
    onOpenRecharge: (RechargeItem) -> Unit,
    onCloseRecharge: () -> Unit,
    onApprove: () -> Unit,
    onReject: (String) -> Unit,
    loadProtectedImageFile: suspend (String, String?) -> File,
) {
    if (!profile.isSuperAdmin) {
        EmptyState(
            modifier = modifier,
            title = "没有审核权限",
            subtitle = "当前账号可以回复客服消息，但不能处理充值审核。",
        )
        return
    }
    if (selectedRecharge != null) {
        RechargeDetailScreen(
            modifier = modifier,
            detail = selectedRecharge,
            loading = loading,
            onBack = onCloseRecharge,
            onApprove = onApprove,
            onReject = onReject,
            loadProtectedImageFile = loadProtectedImageFile,
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionHeader("充值审核", "新的待审核充值会自动出现在这里。") {
            OutlinedButton(onClick = onRefresh, enabled = !loading) { Text("刷新") }
        }
        when {
            loading && recharges.isEmpty() -> LoadingScreen("正在加载充值申请")
            recharges.isEmpty() -> EmptyState(
                modifier = Modifier.fillMaxSize(),
                title = "暂无待审核充值",
                subtitle = "新的充值申请会自动出现在这里。",
            )
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(recharges, key = { it.id }) { item ->
                    Card(onClick = { onOpenRecharge(item) }) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(item.requestNo, fontWeight = FontWeight.Bold)
                            Text(
                                item.username ?: item.email ?: "会员 ${item.userId}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text("RM ${item.amount}", style = MaterialTheme.typography.titleMedium)
                            Text(
                                formatDateTime(item.createdAt),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            item.payerRemark?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RechargeDetailScreen(
    modifier: Modifier,
    detail: RechargeDetail,
    loading: Boolean,
    onBack: () -> Unit,
    onApprove: () -> Unit,
    onReject: (String) -> Unit,
    loadProtectedImageFile: suspend (String, String?) -> File,
) {
    var showRejectDialog by rememberSaveable { mutableStateOf(false) }
    var rejectReason by rememberSaveable { mutableStateOf("") }

    if (showRejectDialog) {
        AlertDialog(
            onDismissRequest = { showRejectDialog = false },
            title = { Text("驳回原因") },
            text = {
                OutlinedTextField(
                    value = rejectReason,
                    onValueChange = { rejectReason = it },
                    label = { Text("请输入驳回原因") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onReject(rejectReason.ifBlank { "请补充更清晰的付款截图。" })
                        showRejectDialog = false
                        rejectReason = ""
                    },
                ) { Text("驳回") }
            },
            dismissButton = { TextButton(onClick = { showRejectDialog = false }) { Text("取消") } },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("充值详情", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        ElevatedCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LabeledValue("申请单号", detail.requestNo)
                LabeledValue("会员", detail.username ?: detail.email ?: "会员 ${detail.userId}")
                LabeledValue("金额", "RM ${detail.amount}")
                LabeledValue("状态", detail.status)
                LabeledValue("提交时间", formatDateTime(detail.createdAt))
                detail.payerRemark?.takeIf { it.isNotBlank() }?.let { LabeledValue("备注", it) }
                detail.rejectReason?.takeIf { it.isNotBlank() }?.let { LabeledValue("驳回原因", it) }
            }
        }

        ElevatedCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("上传截图", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                ProtectedAsyncImage(
                    path = detail.screenshotUrl,
                    fileNameHint = "recharge-screenshot.jpg",
                    localPath = null,
                    loadProtectedImageFile = loadProtectedImageFile,
                    contentDescription = "充值截图",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    contentScale = ContentScale.Fit,
                )
            }
        }

        if (detail.status == "PENDING") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(onClick = onApprove, enabled = !loading, modifier = Modifier.weight(1f)) {
                    Text("通过")
                }
                FilledTonalButton(
                    onClick = { showRejectDialog = true },
                    enabled = !loading,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("驳回")
                }
            }
        }
    }
}

@Composable
fun SupportScreen(
    modifier: Modifier = Modifier,
    loading: Boolean,
    messagesLoading: Boolean,
    sending: Boolean,
    sessions: List<SupportSessionItem>,
    selectedSession: SupportSessionItem?,
    messages: List<SupportMessage>,
    hasMoreMessages: Boolean,
    wsConnected: Boolean,
    loadProtectedImageFile: suspend (String, String?) -> File,
    onRefresh: () -> Unit,
    onOpenSession: (SupportSessionItem) -> Unit,
    onBack: () -> Unit,
    onLoadOlder: () -> Unit,
    onSend: (String) -> Unit,
    onSendAttachment: (Uri, String) -> Unit,
    onOpenAttachment: (SupportMessage) -> Unit,
) {
    if (selectedSession == null) {
        SupportSessionList(
            modifier = modifier,
            loading = loading,
            sessions = sessions,
            wsConnected = wsConnected,
            onRefresh = onRefresh,
            onOpenSession = onOpenSession,
        )
        return
    }

    var draft by rememberSaveable(selectedSession.id) { mutableStateOf("") }
    var previewMessage by remember(selectedSession.id) { mutableStateOf<SupportMessage?>(null) }
    val context = LocalContext.current
    val messageListState = rememberLazyListState()
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            onSendAttachment(uri, draft)
            draft = ""
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            onSendAttachment(uri, draft)
            draft = ""
        }
    }

    LaunchedEffect(selectedSession.id, messages.lastOrNull()?.id) {
        if (messages.isNotEmpty()) {
            messageListState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF2F4F7)),
    ) {
        Surface(
            tonalElevation = 2.dp,
            shadowElevation = 4.dp,
            color = Color.White,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        selectedSession.memberUsername ?: selectedSession.memberEmail ?: "会员 ${selectedSession.memberId}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (wsConnected) "在线沟通中" else "连接中，消息会自动补齐",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF5F6B7A),
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (wsConnected) Color(0xFFE7F7EC) else Color(0xFFFFF1D6),
                ) {
                    Text(
                        text = if (wsConnected) "实时" else "同步",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        color = if (wsConnected) Color(0xFF1E8E5A) else Color(0xFF9A6B00),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFFECEFF4)),
        ) {
            when {
                messagesLoading && messages.isEmpty() -> LoadingScreen("正在加载消息")
                messages.isEmpty() -> EmptyState(
                    modifier = Modifier.fillMaxSize(),
                    title = "暂无消息",
                    subtitle = "该会员暂时还没有发送消息。",
                )
                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    state = messageListState,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        if (hasMoreMessages) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                OutlinedButton(onClick = onLoadOlder, enabled = !messagesLoading) {
                                    Text(if (messagesLoading) "加载中..." else "加载更早消息")
                                }
                            }
                        }
                    }
                    itemsIndexed(messages, key = { _, item -> item.id }) { index, message ->
                        if (shouldShowMessageTimestamp(messages, index)) {
                            ChatTimestampChip(formatDateTime(message.createdAt))
                        }
                        SupportMessageRow(
                            message = message,
                            isAdmin = message.senderScope.equals("admin", ignoreCase = true),
                            loadProtectedImageFile = loadProtectedImageFile,
                            onPreviewImage = { previewMessage = it },
                            onOpenAttachment = onOpenAttachment,
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }

        Surface(
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
            color = Color.White,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("输入消息，或发送图片/文件") },
                    minLines = 1,
                    maxLines = 4,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(onClick = { imagePicker.launch(arrayOf("image/*")) }, enabled = !sending) {
                        Icon(Icons.Default.Image, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("图片")
                    }
                    OutlinedButton(onClick = { filePicker.launch(arrayOf("*/*")) }, enabled = !sending) {
                        Icon(Icons.Default.AttachFile, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("文件")
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        onClick = {
                            onSend(draft)
                            draft = ""
                        },
                        enabled = draft.isNotBlank() && !sending,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .height(38.dp)
                            .width(78.dp),
                    ) {
                        Text(if (sending) "发送中" else "发送")
                    }
                }
            }
        }
    }

    previewMessage?.let { message ->
        ZoomableProtectedImageDialog(
            message = message,
            loadProtectedImageFile = loadProtectedImageFile,
            onDismiss = { previewMessage = null },
        )
    }
}

@Composable
private fun ChatTimestampChip(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = Color(0xFFD6DBE3),
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                color = Color(0xFF647082),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun SupportMessageRow(
    message: SupportMessage,
    isAdmin: Boolean,
    loadProtectedImageFile: suspend (String, String?) -> File,
    onPreviewImage: (SupportMessage) -> Unit,
    onOpenAttachment: (SupportMessage) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isAdmin) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!isAdmin) {
            ChatAvatar("会", Color(0xFFEEF3FF), Color(0xFF3E74D8))
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier.widthIn(max = 280.dp),
            horizontalAlignment = if (isAdmin) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val bubbleColor = if (isAdmin) Color(0xFF2D8CFF) else Color.White
            val textColor = if (isAdmin) Color.White else Color(0xFF1D2433)
            val subTextColor = if (isAdmin) Color.White.copy(alpha = 0.76f) else Color(0xFF6C778A)

            if (hasImageAttachment(message)) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White,
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { onPreviewImage(message) },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        ProtectedAsyncImage(
                            path = message.attachmentUrl.orEmpty(),
                            fileNameHint = message.attachmentName,
                            localPath = message.localAttachmentPath,
                            loadProtectedImageFile = loadProtectedImageFile,
                            contentDescription = message.attachmentName ?: "图片附件",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp, max = 320.dp)
                                .background(Color(0xFFF7F8FA)),
                            contentScale = ContentScale.Fit,
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = message.attachmentName ?: "图片",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF536074),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { onPreviewImage(message) }) {
                                Text("查看")
                            }
                        }
                    }
                }
            }

            if (hasFileAttachment(message)) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = bubbleColor,
                    shadowElevation = 1.dp,
                ) {
                    AttachmentCard(
                        name = message.attachmentName ?: "附件",
                        size = message.attachmentSize,
                        actionLabel = if (message.pending) "发送中" else "打开文件",
                        onClick = { onOpenAttachment(message) },
                        textColor = textColor,
                        subTextColor = subTextColor,
                    )
                }
            }

            if (message.content.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (isAdmin) 18.dp else 6.dp,
                        bottomEnd = if (isAdmin) 6.dp else 18.dp,
                    ),
                    color = bubbleColor,
                    shadowElevation = 1.dp,
                ) {
                    Text(
                        text = message.content,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        color = textColor,
                    )
                }
            }

            if (message.pending) {
                Text(
                    text = "发送中...",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF7A8699),
                )
            }
        }

        if (isAdmin) {
            Spacer(modifier = Modifier.width(8.dp))
            ChatAvatar("管", Color(0xFF2D8CFF), Color.White)
        }
    }
}

@Composable
private fun ChatAvatar(label: String, backgroundColor: Color, textColor: Color) {
    Surface(
        modifier = Modifier.size(36.dp),
        shape = CircleShape,
        color = backgroundColor,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = textColor, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ZoomableProtectedImageDialog(
    message: SupportMessage,
    loadProtectedImageFile: suspend (String, String?) -> File,
    onDismiss: () -> Unit,
) {
    var scale by remember(message.id) { mutableStateOf(1f) }
    var offsetX by remember(message.id) { mutableStateOf(0f) }
    var offsetY by remember(message.id) { mutableStateOf(0f) }
    val localModel = remember(message.localAttachmentPath) {
        resolveProtectedLocalModel(message.localAttachmentPath)
    }
    val fileState = produceState<File?>(initialValue = null, message.attachmentUrl, message.attachmentName, message.localAttachmentPath) {
        if (localModel != null || message.attachmentUrl.isNullOrBlank()) {
            value = null
        } else {
            value = runCatching {
                loadProtectedImageFile(message.attachmentUrl, message.attachmentName)
            }.getOrNull()
        }
    }
    val imageModel = localModel ?: fileState.value

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
            ) {
                Icon(Icons.Default.Close, contentDescription = "关闭", tint = Color.White)
            }

            if (message.attachmentName?.isNotBlank() == true) {
                Text(
                    text = message.attachmentName,
                    color = Color.White.copy(alpha = 0.88f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 48.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (imageModel == null) {
                    androidx.compose.material3.CircularProgressIndicator(color = Color.White)
                } else {
                    AsyncImage(
                        model = imageModel,
                        contentDescription = message.attachmentName ?: "图片预览",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(message.id) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val nextScale = (scale * zoom).coerceIn(1f, 4f)
                                    val scaleChanged = nextScale / scale
                                    scale = nextScale
                                    if (scale <= 1.02f) {
                                        offsetX = 0f
                                        offsetY = 0f
                                    } else {
                                        offsetX += pan.x * scaleChanged
                                        offsetY += pan.y * scaleChanged
                                    }
                                }
                            }
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offsetX
                                translationY = offsetY
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun SupportSessionList(
    modifier: Modifier,
    loading: Boolean,
    sessions: List<SupportSessionItem>,
    wsConnected: Boolean,
    onRefresh: () -> Unit,
    onOpenSession: (SupportSessionItem) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionHeader(
            title = "客服会话",
            subtitle = if (wsConnected) {
                "实时通道已连接。"
            } else {
                "实时通道正在重连，轮询同步仍在继续。"
            },
        ) {
            OutlinedButton(onClick = onRefresh, enabled = !loading) { Text("刷新") }
        }
        when {
            loading && sessions.isEmpty() -> LoadingScreen("正在加载客服会话")
            sessions.isEmpty() -> EmptyState(
                modifier = Modifier.fillMaxSize(),
                title = "暂无会员消息",
                subtitle = "新的客服会话会自动出现在这里。",
            )
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(sessions, key = { it.id }) { session ->
                    Card(onClick = { onOpenSession(session) }) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = session.memberUsername ?: session.memberEmail ?: "会员 ${session.memberId}",
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = session.memberEmail ?: "会员ID：${session.memberId}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (session.adminUnreadCount > 0) {
                                    Surface(
                                        shape = RoundedCornerShape(99.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                    ) {
                                        Text(
                                            text = session.adminUnreadCount.coerceAtMost(99).toString(),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        )
                                    }
                                }
                            }
                            Text(text = session.lastMessagePreview ?: "暂无预览", maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                text = formatDateTime(session.lastMessageAt ?: session.updatedAt),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentCard(
    name: String,
    size: Long?,
    actionLabel: String,
    onClick: () -> Unit,
    textColor: androidx.compose.ui.graphics.Color,
    subTextColor: androidx.compose.ui.graphics.Color,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.18f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(name, color = textColor, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(formatAttachmentSize(size), color = subTextColor, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onClick) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, subtitle: String, action: @Composable (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (action != null) {
            Spacer(modifier = Modifier.width(12.dp))
            action()
        }
    }
}

@Composable
fun LabeledValue(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
        Divider(modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
fun EmptyState(modifier: Modifier = Modifier, title: String, subtitle: String) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun LoadingScreen(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            androidx.compose.material3.CircularProgressIndicator()
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun hasImageAttachment(message: SupportMessage): Boolean {
    return message.messageType.equals("IMAGE", ignoreCase = true) &&
        (!message.attachmentUrl.isNullOrBlank() || !message.localAttachmentPath.isNullOrBlank())
}

private fun hasFileAttachment(message: SupportMessage): Boolean {
    return message.messageType.equals("FILE", ignoreCase = true) &&
        (!message.attachmentUrl.isNullOrBlank() || !message.localAttachmentPath.isNullOrBlank())
}

private fun shouldShowMessageTimestamp(messages: List<SupportMessage>, index: Int): Boolean {
    if (index == 0) {
        return true
    }
    val current = parseDateTime(messages.getOrNull(index)?.createdAt) ?: return false
    val previous = parseDateTime(messages.getOrNull(index - 1)?.createdAt) ?: return true
    return java.time.Duration.between(previous, current).toMinutes() >= 5
}

private fun resolveProtectedLocalModel(localPath: String?): Any? {
    return when {
        localPath.isNullOrBlank() -> null
        localPath.startsWith("content://") || localPath.startsWith("file://") -> Uri.parse(localPath)
        else -> File(localPath).takeIf { it.exists() && it.isFile }
    }
}

private fun formatAttachmentSize(size: Long?): String {
    if (size == null || size <= 0) {
        return "大小未知"
    }
    val kb = size / 1024.0
    if (kb < 1024) {
        return String.format(Locale.US, "%.1f KB", kb)
    }
    return String.format(Locale.US, "%.1f MB", kb / 1024.0)
}

private fun formatDateTime(value: String?): String {
    val parsed = parseDateTime(value) ?: return value ?: "-"
    return parsed.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
}

@Composable
private fun ProtectedAsyncImage(
    path: String,
    fileNameHint: String?,
    localPath: String?,
    loadProtectedImageFile: suspend (String, String?) -> File,
    contentDescription: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val localModel = remember(localPath) {
        resolveProtectedLocalModel(localPath)
    }

    if (localModel != null) {
        AsyncImage(
            model = localModel,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
        )
        return
    }

    val fileState = produceState<File?>(initialValue = null, path, fileNameHint) {
        value = runCatching { loadProtectedImageFile(path, fileNameHint) }.getOrNull()
    }

    if (fileState.value == null) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator()
        }
        return
    }

    AsyncImage(
        model = fileState.value,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
    )
}

private fun parseDateTime(value: String?): LocalDateTime? {
    if (value.isNullOrBlank()) {
        return null
    }
    return runCatching { LocalDateTime.parse(value) }
        .recoverCatching { OffsetDateTime.parse(value).toLocalDateTime() }
        .recoverCatching { LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) }
        .getOrNull()
}
