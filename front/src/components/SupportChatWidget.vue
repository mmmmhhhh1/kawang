<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { ChatDotRound, CloseBold, Promotion } from '@element-plus/icons-vue'
import { memberProfileState } from '@/api/auth'
import { getStoredToken } from '@/api/http'
import {
  downloadSupportAttachment,
  getSupportMessages,
  getSupportSession,
  markSupportRead,
  sendSupportAttachment,
  sendSupportMessage,
  type MemberSupportSession,
  type SupportDispatchPayload,
  type SupportMessage,
  type SupportUnread,
  type SupportWsEnvelope,
} from '@/api/support'
import { formatDateTime } from '@/utils/format'
import {
  moveCachedSupportAttachment,
  readCachedSupportAttachment,
  readCachedSupportState,
  removeCachedSupportAttachment,
  writeCachedSupportAttachment,
  writeCachedSupportState,
} from '@/utils/supportCache'

type UiSupportMessage = SupportMessage & {
  pending?: boolean
  tempKey?: string
  localAttachmentUrl?: string | null
}

const CHAT_PAGE_SIZE = 30
const SESSION_POLL_INTERVAL_OPEN = 12000
const SESSION_POLL_INTERVAL_CLOSED = 20000
const MESSAGE_POLL_INTERVAL_WS = 12000
const MESSAGE_POLL_INTERVAL_FALLBACK = 4000
const MAX_ATTACHMENT_SIZE = 10 * 1024 * 1024
const MAX_CACHED_MESSAGES = 200

const profile = memberProfileState
const drawerVisible = ref(false)
const loadingSession = ref(false)
const loadingMessages = ref(false)
const loadingOlder = ref(false)
const sending = ref(false)
const socketConnected = ref(false)
const draft = ref('')
const session = ref<MemberSupportSession | null>(null)
const messages = ref<UiSupportMessage[]>([])
const nextCursor = ref<string | null>(null)
const hasMore = ref(false)
const listRef = ref<HTMLElement | null>(null)
const drawerSize = ref('420px')
const attachmentInputRef = ref<HTMLInputElement | null>(null)
const attachmentAccept = ref('*/*')
const attachmentPreviewUrls = ref<Record<string, string>>({})

let socket: WebSocket | null = null
let reconnectTimer: number | null = null
let sessionPollTimer: number | null = null
let messagePollTimer: number | null = null
let shouldReconnect = false
let sessionSyncing = false
let latestMessagesSyncing = false
const downloadingAttachments = new Set<string>()

const isAuthenticated = computed(() => Boolean(getStoredToken() && profile.value?.id))
const unreadCount = computed(() => session.value?.memberUnreadCount ?? 0)
const isSessionOpen = computed(() => session.value?.status !== 'CLOSED')
const connectionHint = computed(() =>
  socketConnected.value
    ? '实时客服通道已连接。'
    : '实时通道正在重连，HTTP 同步仍会继续。',
)

function isMemberMessage(senderScope: string) {
  return senderScope.toLowerCase() === 'member'
}

function isAdminMessage(senderScope: string) {
  return senderScope.toLowerCase() === 'admin'
}

function isImageMessage(message: Pick<SupportMessage, 'messageType'>) {
  return message.messageType.toUpperCase() === 'IMAGE'
}

function isFileMessage(message: Pick<SupportMessage, 'messageType'>) {
  return message.messageType.toUpperCase() === 'FILE'
}

function sortMessages(items: UiSupportMessage[]) {
  return [...items].sort((left, right) => new Date(left.createdAt).getTime() - new Date(right.createdAt).getTime())
}

function getLatestCursor() {
  const latest = messages.value[messages.value.length - 1]
  if (!latest) {
    return null
  }
  return btoa(`${new Date(latest.createdAt).getTime()}:${latest.id}`).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '')
}

function scrollToBottom() {
  if (!listRef.value) {
    return
  }
  listRef.value.scrollTop = listRef.value.scrollHeight
}

function currentMemberId() {
  return profile.value?.id ?? 0
}

function attachmentCacheKeyOf(message: Pick<UiSupportMessage, 'attachmentUrl' | 'tempKey'>) {
  return message.attachmentUrl || message.tempKey || ''
}

function serializeCachedMessage(message: UiSupportMessage): SupportMessage {
  return {
    id: message.id,
    sessionId: message.sessionId,
    senderScope: message.senderScope,
    senderId: message.senderId,
    messageType: message.messageType,
    content: message.content,
    attachmentName: message.attachmentName,
    attachmentContentType: message.attachmentContentType,
    attachmentSize: message.attachmentSize,
    attachmentUrl: message.attachmentUrl,
    createdAt: message.createdAt,
  }
}

function hydrateSupportStateFromCache() {
  const memberId = currentMemberId()
  if (!memberId) {
    return
  }

  const cachedState = readCachedSupportState(memberId)
  if (!cachedState) {
    return
  }

  session.value = cachedState.session
  messages.value = sortMessages(cachedState.messages)
  nextCursor.value = cachedState.nextCursor
  hasMore.value = cachedState.hasMore
}

function persistSupportState() {
  const memberId = currentMemberId()
  if (!memberId || !isAuthenticated.value) {
    return
  }

  writeCachedSupportState(memberId, {
    session: session.value,
    messages: messages.value.filter((item) => !item.pending).slice(-MAX_CACHED_MESSAGES).map(serializeCachedMessage),
    nextCursor: nextCursor.value,
    hasMore: hasMore.value,
    savedAt: new Date().toISOString(),
  })
}

function syncDrawerSize() {
  drawerSize.value = window.innerWidth < 768 ? '100%' : '420px'
}

function clearReconnectTimer() {
  if (reconnectTimer !== null) {
    window.clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
}

function stopSessionPolling() {
  if (sessionPollTimer !== null) {
    window.clearInterval(sessionPollTimer)
    sessionPollTimer = null
  }
}

function startSessionPolling() {
  stopSessionPolling()
  if (!isAuthenticated.value) {
    return
  }
  const interval = drawerVisible.value ? SESSION_POLL_INTERVAL_OPEN : SESSION_POLL_INTERVAL_CLOSED
  sessionPollTimer = window.setInterval(() => {
    void refreshSessionState(true)
  }, interval)
}

function stopMessagePolling() {
  if (messagePollTimer !== null) {
    window.clearInterval(messagePollTimer)
    messagePollTimer = null
  }
}

function startMessagePolling() {
  stopMessagePolling()
  if (!isAuthenticated.value || !drawerVisible.value) {
    return
  }
  const interval = socketConnected.value ? MESSAGE_POLL_INTERVAL_WS : MESSAGE_POLL_INTERVAL_FALLBACK
  messagePollTimer = window.setInterval(() => {
    void syncLatestMessages(true)
  }, interval)
}

function scheduleReconnect() {
  if (!shouldReconnect || reconnectTimer !== null || !isAuthenticated.value) {
    return
  }
  reconnectTimer = window.setTimeout(() => {
    reconnectTimer = null
    connectSocket()
  }, 2500)
}

function connectSocket() {
  const token = getStoredToken()
  if (!token || !isAuthenticated.value) {
    return
  }
  if (socket && (socket.readyState === WebSocket.CONNECTING || socket.readyState === WebSocket.OPEN)) {
    return
  }

  clearReconnectTimer()
  shouldReconnect = true
  socketConnected.value = false

  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  socket = new WebSocket(`${protocol}//${window.location.host}/ws/member/support?token=${encodeURIComponent(token)}`)
  socket.onopen = () => {
    socketConnected.value = true
  }
  socket.onmessage = (event) => handleSocketMessage(event.data)
  socket.onclose = () => {
    socketConnected.value = false
    socket = null
    scheduleReconnect()
  }
  socket.onerror = () => {
    socketConnected.value = false
    if (socket) {
      try {
        socket.close()
      } catch {
        socket = null
        scheduleReconnect()
      }
    }
  }
}

function disconnectSocket() {
  shouldReconnect = false
  socketConnected.value = false
  clearReconnectTimer()
  if (socket) {
    socket.onopen = null
    socket.onmessage = null
    socket.onclose = null
    socket.onerror = null
    socket.close()
    socket = null
  }
}

function applyUnread(unread: SupportUnread) {
  if (!session.value || unread.sessionId !== session.value.id) {
    return
  }
  session.value = {
    ...session.value,
    memberUnreadCount: unread.memberUnreadCount,
    adminUnreadCount: unread.adminUnreadCount,
  }
  persistSupportState()
}

function applySessionUpdate(nextSession: MemberSupportSession) {
  if (!session.value || session.value.id !== nextSession.id) {
    session.value = nextSession
    persistSupportState()
    return
  }
  session.value = {
    ...session.value,
    ...nextSession,
  }
  persistSupportState()
}

function revokeLocalAttachmentUrl(message: UiSupportMessage) {
  if (message.localAttachmentUrl) {
    URL.revokeObjectURL(message.localAttachmentUrl)
  }
}

function revokeDownloadedAttachmentUrls() {
  Object.values(attachmentPreviewUrls.value).forEach((url) => URL.revokeObjectURL(url))
  attachmentPreviewUrls.value = {}
}

function findMatchingPendingIndex(nextMessage: SupportMessage) {
  if (!isMemberMessage(nextMessage.senderScope)) {
    return -1
  }
  return messages.value.findIndex((item) => {
    return (
      item.pending &&
      item.sessionId === nextMessage.sessionId &&
      item.messageType === nextMessage.messageType &&
      item.content === nextMessage.content &&
      (item.attachmentName ?? '') === (nextMessage.attachmentName ?? '') &&
      (item.attachmentSize ?? 0) === (nextMessage.attachmentSize ?? 0)
    )
  })
}

function upsertMessage(nextMessage: SupportMessage) {
  const existingIndex = messages.value.findIndex((item) => item.id === nextMessage.id)
  if (existingIndex >= 0) {
    const existing = messages.value[existingIndex]
    if (existing) {
      revokeLocalAttachmentUrl(existing)
    }
    messages.value.splice(existingIndex, 1, nextMessage)
    messages.value = sortMessages(messages.value)
    return
  }

  const pendingIndex = findMatchingPendingIndex(nextMessage)
  if (pendingIndex >= 0) {
    const pending = messages.value[pendingIndex]
    if (pending) {
      revokeLocalAttachmentUrl(pending)
    }
    messages.value.splice(pendingIndex, 1, nextMessage)
    messages.value = sortMessages(messages.value)
    return
  }

  messages.value = sortMessages([...messages.value, nextMessage])
}

function replacePendingMessage(tempKey: string, nextMessage: SupportMessage) {
  const pendingIndex = messages.value.findIndex((item) => item.tempKey === tempKey)
  if (pendingIndex >= 0) {
    const pending = messages.value[pendingIndex]
    if (pending) {
      revokeLocalAttachmentUrl(pending)
    }
    messages.value.splice(pendingIndex, 1, nextMessage)
    messages.value = sortMessages(messages.value)
    return
  }
  upsertMessage(nextMessage)
}

function applyDispatchPayload(payload: SupportDispatchPayload, tempKey?: string) {
  applySessionUpdate(payload.memberSession)
  applyUnread(payload.unread)
  if (tempKey) {
    replacePendingMessage(tempKey, payload.message)
  } else {
    upsertMessage(payload.message)
  }
}

async function ensureSupportSession(silent = false) {
  if (!isAuthenticated.value || sessionSyncing) {
    return
  }

  sessionSyncing = true
  if (!silent) {
    loadingSession.value = true
  }

  try {
    applySessionUpdate(await getSupportSession())
  } catch (error: any) {
    if (!silent) {
      ElMessage.error(error?.response?.data?.message ?? '初始化客服会话失败')
    }
  } finally {
    sessionSyncing = false
    if (!silent) {
      loadingSession.value = false
    }
  }
}

async function refreshSessionState(silent = false) {
  await ensureSupportSession(silent)
}

async function loadMessages(reset = false) {
  if (!session.value) {
    await ensureSupportSession()
  }
  if (!session.value) {
    return
  }

  if (reset) {
    loadingMessages.value = true
  } else {
    loadingOlder.value = true
  }

  try {
    const result = await getSupportMessages(CHAT_PAGE_SIZE, reset ? null : nextCursor.value)
    nextCursor.value = result.nextCursor
    hasMore.value = result.hasMore

    if (reset) {
      messages.value.forEach(revokeLocalAttachmentUrl)
      messages.value = sortMessages(result.items)
    } else if (result.items.length) {
      messages.value = sortMessages([...result.items, ...messages.value])
    }

    await nextTick()
    if (reset) {
      scrollToBottom()
    }
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '加载聊天记录失败')
  } finally {
    loadingMessages.value = false
    loadingOlder.value = false
  }
}

async function syncLatestMessages(silent = false) {
  if (!drawerVisible.value || !session.value || latestMessagesSyncing) {
    return
  }

  latestMessagesSyncing = true
  try {
    const result = await getSupportMessages(CHAT_PAGE_SIZE, null, getLatestCursor())
    const beforeCount = messages.value.length
    result.items.forEach((item) => upsertMessage(item))
    if (messages.value.length > beforeCount) {
      await nextTick()
      scrollToBottom()
    }
  } catch (error: any) {
    if (!silent) {
      ElMessage.error(error?.response?.data?.message ?? '同步最新消息失败')
    }
  } finally {
    latestMessagesSyncing = false
  }
}

async function syncReadState() {
  if (!session.value || session.value.memberUnreadCount <= 0) {
    return
  }

  try {
    applyUnread(await markSupportRead())
  } catch {
    // Keep local unread state until the next successful sync.
  }
}

function handleSocketMessage(raw: string) {
  try {
    const envelope = JSON.parse(raw) as SupportWsEnvelope<any>

    if (envelope.type === 'ERROR') {
      ElMessage.error(envelope.data?.message ?? '发送客服消息失败')
      sending.value = false
      return
    }

    if (envelope.type === 'SUPPORT_DISPATCH') {
      applyDispatchPayload(envelope.data as SupportDispatchPayload)
      sending.value = false
      nextTick(() => scrollToBottom())
      if (drawerVisible.value && isAdminMessage((envelope.data as SupportDispatchPayload).message.senderScope)) {
        void syncReadState()
      }
      return
    }

    if (envelope.type === 'MESSAGE_CREATED') {
      const nextMessage = envelope.data as SupportMessage
      upsertMessage(nextMessage)
      sending.value = false
      nextTick(() => scrollToBottom())
      if (drawerVisible.value && isAdminMessage(nextMessage.senderScope)) {
        void syncReadState()
      }
      return
    }

    if (envelope.type === 'SESSION_UPDATED') {
      applySessionUpdate(envelope.data as MemberSupportSession)
      return
    }

    if (envelope.type === 'UNREAD_UPDATED') {
      applyUnread(envelope.data as SupportUnread)
    }
  } catch {
    // Ignore invalid websocket payloads.
  }
}

async function ensureAttachmentPreview(message: UiSupportMessage) {
  if (!isImageMessage(message) || message.pending || !message.attachmentUrl) {
    return
  }
  const cacheKey = attachmentCacheKeyOf(message)
  if (message.localAttachmentUrl || attachmentPreviewUrls.value[cacheKey] || downloadingAttachments.has(cacheKey)) {
    return
  }
  downloadingAttachments.add(cacheKey)
  try {
    const cachedBlob = await readCachedSupportAttachment(cacheKey)
    const blob = cachedBlob ?? (await downloadSupportAttachment(message.attachmentUrl))
    if (!cachedBlob) {
      await writeCachedSupportAttachment(cacheKey, blob)
    }
    attachmentPreviewUrls.value = {
      ...attachmentPreviewUrls.value,
      [cacheKey]: URL.createObjectURL(blob),
    }
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '加载图片附件失败')
  } finally {
    downloadingAttachments.delete(cacheKey)
  }
}

function getAttachmentPreviewUrl(message: UiSupportMessage) {
  if (message.localAttachmentUrl) {
    return message.localAttachmentUrl
  }
  const cacheKey = attachmentCacheKeyOf(message)
  if (!cacheKey) {
    return ''
  }
  return attachmentPreviewUrls.value[cacheKey] ?? ''
}

async function openDrawer() {
  drawerVisible.value = true
  connectSocket()
  await ensureSupportSession()
  if (!messages.value.length) {
    await loadMessages(true)
  } else {
    await syncLatestMessages(true)
    await nextTick()
    scrollToBottom()
  }
  await syncReadState()
}

function closeDrawer() {
  drawerVisible.value = false
}

function openFromEvent() {
  void openDrawer()
}

async function loadOlder() {
  if (!hasMore.value || loadingOlder.value) {
    return
  }
  await loadMessages(false)
}

async function sendMessage() {
  if (!session.value) {
    await ensureSupportSession()
  }
  if (!session.value) {
    return
  }

  const content = draft.value.trim()
  if (!content) {
    return
  }
  if (content.length > 1000) {
    ElMessage.warning('消息内容不能超过 1000 个字符')
    return
  }

  const tempKey = `${Date.now()}-${Math.random()}`
  messages.value = sortMessages([
    ...messages.value,
    {
      id: -Date.now(),
      sessionId: session.value.id,
      senderScope: 'MEMBER',
      senderId: profile.value?.id ?? 0,
      messageType: 'TEXT',
      content,
      attachmentName: null,
      attachmentContentType: null,
      attachmentSize: null,
      attachmentUrl: null,
      createdAt: new Date().toISOString(),
      pending: true,
      tempKey,
    },
  ])

  draft.value = ''
  sending.value = true
  await nextTick()
  scrollToBottom()

  try {
    const payload = await sendSupportMessage(session.value.id, content)
    applyDispatchPayload(payload, tempKey)
    await nextTick()
    scrollToBottom()
    if (drawerVisible.value) {
      await syncReadState()
    }
  } catch (error: any) {
    messages.value = messages.value.filter((item) => item.tempKey !== tempKey)
    if (!draft.value) {
      draft.value = content
    }
    ElMessage.error(error?.response?.data?.message ?? '发送客服消息失败')
  } finally {
    sending.value = false
  }
}

function openAttachmentPicker(accept: string) {
  attachmentAccept.value = accept
  attachmentInputRef.value?.click()
}

async function handleAttachmentSelected(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) {
    return
  }
  await sendAttachment(file)
}

async function sendAttachment(file: File) {
  if (!session.value) {
    await ensureSupportSession()
  }
  if (!session.value) {
    return
  }
  if (file.size > MAX_ATTACHMENT_SIZE) {
    ElMessage.warning('附件大小不能超过 10 MB')
    return
  }

  const tempKey = `${Date.now()}-${Math.random()}`
  const now = Date.now()
  const localAttachmentUrl = file.type.startsWith('image/') ? URL.createObjectURL(file) : null
  const content = draft.value.trim()
  await writeCachedSupportAttachment(tempKey, file)
  messages.value = sortMessages([
    ...messages.value,
    {
      id: -now,
      sessionId: session.value.id,
      senderScope: 'MEMBER',
      senderId: profile.value?.id ?? 0,
      messageType: file.type.startsWith('image/') ? 'IMAGE' : 'FILE',
      content,
      attachmentName: file.name,
      attachmentContentType: file.type || 'application/octet-stream',
      attachmentSize: file.size,
      attachmentUrl: null,
      localAttachmentUrl,
      createdAt: new Date().toISOString(),
      pending: true,
      tempKey,
    },
  ])

  draft.value = ''
  sending.value = true
  await nextTick()
  scrollToBottom()

  try {
    const payload = await sendSupportAttachment(session.value.id, file, content)
    if (payload.message.attachmentUrl) {
      await moveCachedSupportAttachment(tempKey, payload.message.attachmentUrl)
    }
    applyDispatchPayload(payload, tempKey)
    await nextTick()
    scrollToBottom()
    if (drawerVisible.value) {
      await syncReadState()
    }
  } catch (error: any) {
    await removeCachedSupportAttachment(tempKey)
    const pendingMessage = messages.value.find((item) => item.tempKey === tempKey)
    if (pendingMessage) {
      revokeLocalAttachmentUrl(pendingMessage)
    }
    messages.value = messages.value.filter((item) => item.tempKey !== tempKey)
    if (!draft.value && content) {
      draft.value = content
    }
    ElMessage.error(error?.response?.data?.message ?? '发送附件失败')
  } finally {
    sending.value = false
  }
}

async function downloadAttachment(message: UiSupportMessage) {
  if (!message.attachmentUrl || message.pending) {
    return
  }
  try {
    const cacheKey = attachmentCacheKeyOf(message)
    const cachedBlob = await readCachedSupportAttachment(cacheKey)
    const blob = cachedBlob ?? (await downloadSupportAttachment(message.attachmentUrl))
    if (!cachedBlob) {
      await writeCachedSupportAttachment(cacheKey, blob)
    }
    const objectUrl = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = objectUrl
    link.download = message.attachmentName || 'support-attachment'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(objectUrl)
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '下载附件失败')
  }
}

function previewAttachment(message: UiSupportMessage) {
  const previewUrl = getAttachmentPreviewUrl(message)
  if (previewUrl) {
    window.open(previewUrl, '_blank', 'noopener')
    return
  }
  void downloadAttachment(message)
}

function formatAttachmentSize(size: number | null) {
  if (!size || size <= 0) {
    return '大小未知'
  }
  const kb = size / 1024
  if (kb < 1024) {
    return `${kb.toFixed(1)} KB`
  }
  return `${(kb / 1024).toFixed(1)} MB`
}

watch(
  isAuthenticated,
  async (authed) => {
    if (!authed) {
      stopSessionPolling()
      stopMessagePolling()
      disconnectSocket()
      messages.value.forEach(revokeLocalAttachmentUrl)
      session.value = null
      messages.value = []
      drawerVisible.value = false
      revokeDownloadedAttachmentUrls()
      return
    }

    hydrateSupportStateFromCache()
    startSessionPolling()
    await ensureSupportSession()
  },
  { immediate: true },
)

watch(drawerVisible, (visible) => {
  startSessionPolling()
  if (!visible) {
    stopMessagePolling()
    disconnectSocket()
    return
  }

  connectSocket()
  startMessagePolling()
  void syncLatestMessages(true)
  void syncReadState()
  nextTick(() => scrollToBottom())
})

watch(socketConnected, () => {
  if (drawerVisible.value) {
    startMessagePolling()
  }
})

watch(
  messages,
  (currentMessages) => {
    currentMessages.forEach((message) => {
      if (isImageMessage(message)) {
        void ensureAttachmentPreview(message)
      }
    })
    persistSupportState()
  },
  { deep: false },
)

watch([session, nextCursor, hasMore], () => {
  persistSupportState()
})

onMounted(() => {
  syncDrawerSize()
  window.addEventListener('resize', syncDrawerSize, { passive: true })
  window.addEventListener('support-chat:open', openFromEvent as EventListener)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', syncDrawerSize)
  window.removeEventListener('support-chat:open', openFromEvent as EventListener)
  stopSessionPolling()
  stopMessagePolling()
  disconnectSocket()
  messages.value.forEach(revokeLocalAttachmentUrl)
  revokeDownloadedAttachmentUrls()
})
</script>

<template>
  <div v-if="isAuthenticated" class="support-chat-widget">
    <button class="support-chat-widget__trigger" type="button" @click="openDrawer">
      <el-badge :hidden="unreadCount <= 0" :value="unreadCount" :max="99">
        <span class="support-chat-widget__icon">
          <el-icon><ChatDotRound /></el-icon>
        </span>
      </el-badge>
      <span class="support-chat-widget__copy">
        <strong>在线客服</strong>
        <em>{{ unreadCount > 0 ? `有 ${unreadCount} 条新消息` : '需要帮助？现在就联系在线客服。' }}</em>
      </span>
    </button>

    <el-drawer
      v-model="drawerVisible"
      :size="drawerSize"
      direction="rtl"
      class="support-chat-drawer"
      :with-header="false"
      append-to-body
      @close="closeDrawer"
    >
      <div class="support-chat-drawer__shell">
        <header class="support-chat-drawer__header">
          <div>
            <span class="support-chat-drawer__kicker">{{ socketConnected ? '实时在线' : '正在重连' }}</span>
            <h3>在线客服</h3>
            <p>{{ session?.lastMessageAt ? `最近更新：${formatDateTime(session.lastMessageAt)}` : connectionHint }}</p>
          </div>
          <button class="support-chat-drawer__close" type="button" @click="closeDrawer">
            <el-icon><CloseBold /></el-icon>
          </button>
        </header>

        <section class="support-chat-drawer__body">
          <div class="support-chat-drawer__toolbar">
            <el-button text :disabled="!hasMore || loadingOlder" @click="loadOlder">
              {{ loadingOlder ? '加载中...' : hasMore ? '加载更早消息' : '没有更多消息了' }}
            </el-button>
          </div>

          <div ref="listRef" class="support-chat-drawer__list">
            <el-skeleton v-if="loadingSession || loadingMessages" animated :rows="8" />
            <el-empty v-else-if="!messages.length" description="暂时还没有消息，开始对话吧。" />

            <div v-else class="support-message-list">
              <article
                v-for="item in messages"
                :key="item.pending ? item.tempKey : item.id"
                class="support-message"
                :class="{
                  'is-self': isMemberMessage(item.senderScope),
                  'is-pending': item.pending,
                }"
              >
                <div class="support-message__bubble">
                  <template v-if="isImageMessage(item) && getAttachmentPreviewUrl(item)">
                    <img
                      class="support-message__image"
                      :src="getAttachmentPreviewUrl(item)"
                      :alt="item.attachmentName || '图片附件'"
                      @click="previewAttachment(item)"
                    />
                    <button
                      v-if="!item.pending"
                      type="button"
                      class="support-message__link"
                      @click="previewAttachment(item)"
                    >
                      查看图片
                    </button>
                  </template>

                  <div v-else-if="isFileMessage(item)" class="support-message__file">
                    <strong>{{ item.attachmentName || '附件' }}</strong>
                    <em>{{ formatAttachmentSize(item.attachmentSize) }}</em>
                    <button
                      v-if="!item.pending"
                      type="button"
                      class="support-message__link"
                      @click="downloadAttachment(item)"
                    >
                      下载
                    </button>
                  </div>

                  <p v-if="item.content">{{ item.content }}</p>
                  <span>{{ item.pending ? '发送中...' : formatDateTime(item.createdAt) }}</span>
                </div>
              </article>
            </div>
          </div>
        </section>

        <footer class="support-chat-drawer__footer">
          <div v-if="!isSessionOpen" class="support-chat-drawer__closed">
            当前客服会话已关闭，暂时不能继续发送新消息。
          </div>
          <div v-else class="support-chat-drawer__composer">
            <input
              ref="attachmentInputRef"
              class="support-chat-drawer__file-input"
              type="file"
              :accept="attachmentAccept"
              @change="handleAttachmentSelected"
            />
            <el-input
              v-model="draft"
              type="textarea"
              :autosize="{ minRows: 2, maxRows: 4 }"
              maxlength="1000"
              show-word-limit
              placeholder="请输入你的问题、充值情况或需要协助的内容"
              @keyup.ctrl.enter="sendMessage"
            />
            <div class="support-chat-drawer__composer-actions">
              <el-button plain :disabled="sending" @click="openAttachmentPicker('image/*')">图片</el-button>
              <el-button plain :disabled="sending" @click="openAttachmentPicker('*/*')">文件</el-button>
              <el-button type="primary" :loading="sending" @click="sendMessage">
                <el-icon><Promotion /></el-icon>
                发送
              </el-button>
            </div>
          </div>
        </footer>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.support-chat-widget {
  position: fixed;
  right: 22px;
  bottom: 22px;
  z-index: 30;
}

.support-chat-widget__trigger {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 220px;
  padding: 14px 18px;
  border: none;
  border-radius: 22px;
  background:
    radial-gradient(circle at top right, rgba(255, 218, 232, 0.32), transparent 34%),
    linear-gradient(135deg, rgba(255, 252, 253, 0.96), rgba(255, 246, 250, 0.94));
  box-shadow: 0 18px 42px rgba(102, 74, 126, 0.18);
  cursor: pointer;
}

.support-chat-widget__icon {
  display: grid;
  place-items: center;
  width: 46px;
  height: 46px;
  border-radius: 16px;
  background: linear-gradient(135deg, #ec4899, #f97316);
  color: #fff;
  font-size: 20px;
}

.support-chat-widget__copy {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}

.support-chat-widget__copy strong {
  color: #2f2233;
  font-size: 15px;
}

.support-chat-widget__copy em {
  margin-top: 4px;
  color: #81677d;
  font-style: normal;
  font-size: 12px;
}

.support-chat-drawer :deep(.el-drawer__body) {
  padding: 0;
}

.support-chat-drawer__shell {
  height: 100%;
  display: grid;
  grid-template-rows: auto 1fr auto;
  background:
    radial-gradient(circle at top, rgba(255, 222, 233, 0.36), transparent 28%),
    linear-gradient(180deg, #fffafc, #fff6fa 58%, #fffdfd);
}

.support-chat-drawer__header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  padding: 22px 22px 16px;
  border-bottom: 1px solid rgba(241, 216, 229, 0.84);
}

.support-chat-drawer__kicker {
  display: inline-flex;
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(255, 232, 241, 0.9);
  color: #b83280;
  font-size: 12px;
}

.support-chat-drawer__header h3 {
  margin: 14px 0 0;
  font-size: 28px;
  color: #281f2d;
}

.support-chat-drawer__header p {
  margin: 8px 0 0;
  color: #8b6f81;
  font-size: 12px;
}

.support-chat-drawer__close {
  width: 40px;
  height: 40px;
  border: none;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.9);
  cursor: pointer;
}

.support-chat-drawer__body {
  min-height: 0;
  display: grid;
  grid-template-rows: auto 1fr;
}

.support-chat-drawer__toolbar {
  padding: 12px 22px 4px;
}

.support-chat-drawer__list {
  min-height: 0;
  overflow-y: auto;
  padding: 10px 22px 18px;
}

.support-message-list {
  display: grid;
  gap: 12px;
}

.support-message {
  display: flex;
}

.support-message.is-self {
  justify-content: flex-end;
}

.support-message__bubble {
  max-width: min(82%, 320px);
  padding: 14px 16px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(244, 224, 234, 0.92);
  box-shadow: 0 10px 24px rgba(120, 89, 115, 0.08);
}

.support-message.is-self .support-message__bubble {
  background: linear-gradient(135deg, #ec4899, #f97316);
  border-color: transparent;
  color: #fff;
}

.support-message.is-pending .support-message__bubble {
  opacity: 0.72;
}

.support-message__bubble p {
  margin: 0;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.support-message__bubble span {
  display: block;
  margin-top: 8px;
  font-size: 11px;
  color: rgba(106, 80, 100, 0.82);
}

.support-message.is-self .support-message__bubble span {
  color: rgba(255, 255, 255, 0.84);
}

.support-message__image {
  display: block;
  width: 100%;
  max-height: 240px;
  border-radius: 16px;
  object-fit: cover;
  cursor: pointer;
}

.support-message__file {
  display: grid;
  gap: 6px;
}

.support-message__file strong {
  font-size: 14px;
}

.support-message__file em {
  font-style: normal;
  font-size: 12px;
  color: rgba(106, 80, 100, 0.82);
}

.support-message.is-self .support-message__file em {
  color: rgba(255, 255, 255, 0.84);
}

.support-message__link {
  width: fit-content;
  padding: 0;
  border: none;
  background: transparent;
  color: inherit;
  font-size: 12px;
  cursor: pointer;
  text-decoration: underline;
}

.support-chat-drawer__footer {
  padding: 16px 22px 22px;
  border-top: 1px solid rgba(241, 216, 229, 0.84);
}

.support-chat-drawer__composer {
  display: grid;
  gap: 12px;
}

.support-chat-drawer__composer-actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
  flex-wrap: wrap;
}

.support-chat-drawer__file-input {
  display: none;
}

.support-chat-drawer__closed {
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(255, 244, 246, 0.95);
  color: #ad4f7f;
}

@media (max-width: 768px) {
  .support-chat-widget {
    right: 14px;
    bottom: 14px;
    left: 14px;
  }

  .support-chat-widget__trigger {
    width: 100%;
    min-width: 0;
    justify-content: center;
  }

  .support-chat-drawer__header,
  .support-chat-drawer__toolbar,
  .support-chat-drawer__list,
  .support-chat-drawer__footer {
    padding-left: 16px;
    padding-right: 16px;
  }

  .support-chat-drawer__composer-actions {
    justify-content: stretch;
  }
}
</style>
