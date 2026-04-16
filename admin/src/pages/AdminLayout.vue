<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Bell, Box, Goods, List, Setting, SwitchButton, UserFilled } from '@element-plus/icons-vue'
import { ElMessage, ElNotification } from 'element-plus'
import {
  adminProfileState,
  fetchMe,
  hasAdminPermission,
  logout,
  type AdminPermission,
} from '@/api/auth'
import { getRechargePage } from '@/api/recharges'
import { getStoredToken } from '@/api/http'

const router = useRouter()
const route = useRoute()
const profile = adminProfileState

const menuItems: Array<{
  index: string
  label: string
  icon: any
  permission?: AdminPermission
  superAdminOnly?: boolean
}> = [
  { index: '/products', label: '商品管理', icon: Goods },
  { index: '/accounts', label: '卡密池管理', icon: Box },
  { index: '/orders', label: '订单管理', icon: List },
  { index: '/users', label: '会员管理', icon: UserFilled },
  { index: '/recharges', label: '充值审核', icon: Bell },
  { index: '/admins', label: '管理员管理', icon: Setting, permission: 'CREATE_ADMIN' },
  { index: '/payment-qr', label: '收款码管理', icon: Setting, superAdminOnly: true },
  { index: '/notices', label: '公告管理', icon: Bell },
]

const RECHARGE_POLL_INTERVAL = 12_000

let notificationSocket: WebSocket | null = null
let reconnectTimer: number | null = null
let rechargePollTimer: number | null = null
let shouldReconnect = false
let lastRechargeNotificationId: number | null = null

const visibleMenuItems = computed(() =>
  menuItems.filter((item) => {
    if (item.superAdminOnly) {
      return !!profile.value?.isSuperAdmin
    }
    return !item.permission || hasAdminPermission(item.permission, profile.value)
  }),
)

const currentMenuLabel = computed(() => {
  const current = menuItems.find((item) => route.path.startsWith(item.index))
  return current?.label ?? '后台工作台'
})

const roleText = computed(() => (profile.value?.isSuperAdmin ? '超级管理员' : '管理员'))
const permissionSummary = computed(() => {
  if (!profile.value) {
    return '正在加载管理员资料'
  }
  if (profile.value.isSuperAdmin) {
    return '已开通全部权限'
  }
  const count = profile.value.permissions?.length ?? 0
  return count > 0 ? `已分配 ${count} 项权限` : '基础权限账号'
})

const profileTitle = computed(() => profile.value?.displayName?.trim() || profile.value?.username?.trim() || '管理员')
const profileSubtitle = computed(() => {
  const username = profile.value?.username?.trim()
  if (username && username !== profileTitle.value) {
    return `@${username}`
  }
  return ''
})
const profileInitial = computed(() => profileTitle.value.trim().slice(0, 1).toUpperCase() || '管')

function clearReconnectTimer() {
  if (reconnectTimer !== null) {
    window.clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
}

function stopRechargePolling() {
  if (rechargePollTimer !== null) {
    window.clearInterval(rechargePollTimer)
    rechargePollTimer = null
  }
}

function speakNotification(text: string) {
  if (!('speechSynthesis' in window)) {
    return
  }
  try {
    window.speechSynthesis.cancel()
    const utterance = new SpeechSynthesisUtterance(text)
    utterance.lang = 'zh-CN'
    utterance.rate = 1
    const voices = window.speechSynthesis.getVoices()
    const chineseVoice = voices.find((voice) => voice.lang?.toLowerCase().startsWith('zh'))
    if (chineseVoice) {
      utterance.voice = chineseVoice
    }
    window.speechSynthesis.speak(utterance)
  } catch {
    // ignore browser speech errors
  }
}

function showRechargeNotification(title: string, message: string) {
  ElNotification({
    title,
    message,
    type: 'warning',
    duration: 8000,
    onClick: () => router.push('/recharges'),
  })
  speakNotification(message)
}

function handleNotificationMessage(raw: string) {
  try {
    const payload = JSON.parse(raw) as {
      type?: string
      title?: string
      message?: string
      requestId?: number
    }
    if (payload.type !== 'RECHARGE_CREATED') {
      return
    }
    if (typeof payload.requestId === 'number') {
      lastRechargeNotificationId = Math.max(lastRechargeNotificationId ?? 0, payload.requestId)
    }
    const title = payload.title || '收到新的充值申请'
    const message = payload.message || '有用户提交了新的充值申请，请尽快审核。'
    showRechargeNotification(title, message)
  } catch {
    // ignore invalid payloads
  }
}

async function syncLatestRechargeBaseline() {
  try {
    const result = await getRechargePage({
      size: 1,
      status: 'PENDING',
    })
    const latest = result.items[0]
    if (latest) {
      lastRechargeNotificationId = Math.max(lastRechargeNotificationId ?? 0, latest.id)
    }
  } catch {
    // ignore polling errors during bootstrap
  }
}

async function pollRechargeNotifications() {
  if (!getStoredToken()) {
    return
  }
  try {
    const result = await getRechargePage({
      size: 1,
      status: 'PENDING',
    })
    const latest = result.items[0]
    if (!latest) {
      return
    }
    if (lastRechargeNotificationId == null) {
      lastRechargeNotificationId = latest.id
      return
    }
    if (latest.id <= lastRechargeNotificationId) {
      return
    }
    lastRechargeNotificationId = latest.id
    const name = latest.username?.trim() || latest.email?.trim() || `用户${latest.userId}`
    const amount = Number(latest.amount ?? 0).toFixed(2)
    showRechargeNotification('收到新的充值申请', `用户“${name}”提交了 ${amount} 元充值申请，请尽快审核。`)
  } catch {
    // ignore polling errors
  }
}

function startRechargePolling() {
  stopRechargePolling()
  rechargePollTimer = window.setInterval(() => {
    void pollRechargeNotifications()
  }, RECHARGE_POLL_INTERVAL)
}

function scheduleReconnect() {
  if (!shouldReconnect || reconnectTimer !== null || !getStoredToken()) {
    return
  }
  reconnectTimer = window.setTimeout(() => {
    reconnectTimer = null
    connectNotificationSocket()
  }, 3000)
}

function disconnectNotificationSocket() {
  shouldReconnect = false
  clearReconnectTimer()
  if (notificationSocket) {
    notificationSocket.onopen = null
    notificationSocket.onmessage = null
    notificationSocket.onerror = null
    notificationSocket.onclose = null
    notificationSocket.close()
    notificationSocket = null
  }
}

function connectNotificationSocket() {
  clearReconnectTimer()
  const token = getStoredToken()
  if (!token) {
    return
  }
  if (
    notificationSocket &&
    (notificationSocket.readyState === WebSocket.CONNECTING || notificationSocket.readyState === WebSocket.OPEN)
  ) {
    return
  }

  shouldReconnect = true
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const socketUrl = `${protocol}//${window.location.host}/ws/admin/notifications?token=${encodeURIComponent(token)}`
  const socket = new WebSocket(socketUrl)
  notificationSocket = socket

  socket.onmessage = (event) => handleNotificationMessage(event.data)
  socket.onerror = () => {
    if (notificationSocket === socket) {
      try {
        socket.close()
      } catch {
        notificationSocket = null
        scheduleReconnect()
      }
    }
  }
  socket.onclose = () => {
    if (notificationSocket === socket) {
      notificationSocket = null
      scheduleReconnect()
    }
  }
}

async function syncProfile() {
  try {
    const currentProfile = await fetchMe()
    if (!currentProfile) {
      logoutAndRedirect()
      return
    }
    await syncLatestRechargeBaseline()
    connectNotificationSocket()
    startRechargePolling()
  } catch {
    logoutAndRedirect()
  }
}

function logoutAndRedirect() {
  stopRechargePolling()
  disconnectNotificationSocket()
  logout()
  ElMessage.success('已退出登录')
  router.replace('/login')
}

watch(
  () => [route.path, profile.value],
  () => {
    const current = menuItems.find((item) => route.path.startsWith(item.index))
    if (!current || !profile.value) {
      return
    }
    if (current.superAdminOnly && !profile.value.isSuperAdmin) {
      router.replace('/products')
      return
    }
    if (current.permission && !hasAdminPermission(current.permission, profile.value)) {
      router.replace('/products')
    }
  },
  { immediate: true },
)

onMounted(() => {
  void syncProfile()
})

onBeforeUnmount(() => {
  stopRechargePolling()
  disconnectNotificationSocket()
})
</script>

<template>
  <div class="layout-shell">
    <aside class="sidebar">
      <div class="brand">
        <p class="brand-kicker">卡王后台</p>
        <h1>管理后台</h1>
        <span class="brand-caption">商品、卡密、订单、充值和权限统一管理。</span>
      </div>

      <el-menu
        :default-active="route.path"
        class="side-menu"
        background-color="transparent"
        text-color="#d8e7f4"
        active-text-color="#ffffff"
        router
      >
        <el-menu-item v-for="item in visibleMenuItems" :key="item.index" :index="item.index">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </el-menu-item>
      </el-menu>
    </aside>

    <div class="layout-main">
      <header class="layout-header">
        <div>
          <p class="header-kicker">运营中心</p>
          <h2>{{ currentMenuLabel }}</h2>
        </div>
        <div class="header-actions">
          <div class="profile-chip">
            <div class="profile-avatar">{{ profileInitial }}</div>
            <div class="profile-copy">
              <div class="profile-copy__top">
                <strong>{{ profileTitle }}</strong>
                <span class="profile-role">{{ roleText }}</span>
              </div>
              <span v-if="profileSubtitle">{{ profileSubtitle }}</span>
              <small>{{ permissionSummary }}</small>
            </div>
          </div>
          <el-button plain class="logout-button" @click="logoutAndRedirect">
            <el-icon><SwitchButton /></el-icon>
            退出登录
          </el-button>
        </div>
      </header>

      <main class="content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped>
.layout-shell {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
}

.sidebar {
  position: relative;
  padding: 26px 18px;
  background:
    radial-gradient(circle at top, rgba(110, 231, 255, 0.16), transparent 32%),
    linear-gradient(180deg, #0b1f33 0%, #102844 54%, #14304f 100%);
  color: #fff;
  border-right: 1px solid rgba(148, 163, 184, 0.14);
  box-shadow: inset -1px 0 0 rgba(255, 255, 255, 0.04);
}

.brand {
  padding: 12px 14px 24px;
}

.brand-kicker {
  margin: 0;
  font-size: 11px;
  letter-spacing: 0.26em;
  color: #8bd5ff;
}

.brand h1 {
  margin: 10px 0 6px;
  font-size: 29px;
  letter-spacing: -0.03em;
}

.brand-caption {
  display: inline-flex;
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.08);
  color: rgba(216, 231, 244, 0.88);
  font-size: 12px;
}

.side-menu {
  border-right: none;
  padding-top: 8px;
}

.side-menu :deep(.el-menu-item) {
  height: 50px;
  margin-bottom: 10px;
  border-radius: 16px;
  font-weight: 600;
}

.side-menu :deep(.el-menu-item:hover) {
  background: rgba(255, 255, 255, 0.08);
}

.side-menu :deep(.el-menu-item.is-active) {
  background: linear-gradient(135deg, rgba(96, 165, 250, 0.34), rgba(59, 130, 246, 0.2));
  box-shadow: inset 0 0 0 1px rgba(191, 219, 254, 0.14);
}

.layout-main {
  padding: 20px;
}

.layout-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  padding: 20px 24px;
  border-radius: 26px;
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid rgba(148, 163, 184, 0.18);
  backdrop-filter: blur(18px);
  box-shadow: 0 20px 50px rgba(15, 23, 42, 0.08);
}

.header-kicker {
  margin: 0;
  font-size: 12px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: #6b89a3;
}

.layout-header h2 {
  margin: 8px 0 0;
  font-size: 30px;
  letter-spacing: -0.03em;
  color: #112031;
}

.header-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}

.profile-chip {
  display: flex;
  gap: 14px;
  align-items: center;
  min-width: 330px;
  padding: 12px 14px;
  border-radius: 18px;
  background: linear-gradient(135deg, rgba(240, 247, 255, 0.96), rgba(249, 252, 255, 0.9));
  border: 1px solid rgba(191, 219, 254, 0.56);
}

.profile-avatar {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 14px;
  background: linear-gradient(135deg, #0f766e, #2563eb);
  color: #fff;
  font-weight: 700;
  letter-spacing: 0.04em;
}

.profile-copy {
  min-width: 0;
  flex: 1;
}

.profile-copy__top {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
}

.profile-copy strong,
.profile-copy span,
.profile-copy small {
  display: block;
}

.profile-copy strong {
  color: #112031;
  font-size: 15px;
}

.profile-copy span {
  margin-top: 2px;
  color: #567086;
  font-size: 13px;
}

.profile-copy small {
  margin-top: 4px;
  color: #7b8fa1;
  font-size: 12px;
}

.profile-role {
  display: inline-flex !important;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(37, 99, 235, 0.1);
  color: #1d4ed8 !important;
  font-size: 12px !important;
  font-weight: 600;
}

.logout-button {
  border-radius: 14px;
}

.content {
  padding-top: 18px;
}

@media (max-width: 1120px) {
  .layout-shell {
    grid-template-columns: 1fr;
  }

  .sidebar {
    padding-bottom: 12px;
  }
}

@media (max-width: 860px) {
  .layout-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .header-actions {
    width: 100%;
    flex-direction: column;
    align-items: stretch;
  }

  .profile-chip {
    min-width: 0;
    width: 100%;
  }

  .logout-button {
    width: 100%;
  }
}
</style>