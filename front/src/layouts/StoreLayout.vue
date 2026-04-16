<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search, SwitchButton } from '@element-plus/icons-vue'
import { fetchMemberProfile, logoutMember, memberProfileState } from '@/api/auth'
import { getStoredToken } from '@/api/http'

type FloatingPetal = {
  id: number
  left: string
  top: string
  width: string
  height: string
  opacity: string
  duration: string
  delay: string
  drift: string
}

type CursorPetal = {
  id: number
  left: string
  top: string
  width: string
  height: string
  driftX: string
  driftY: string
  scale: string
  rotate: string
  opacity: string
}

const route = useRoute()
const router = useRouter()
const profile = memberProfileState
const searchKeyword = ref('')
const floatingPetals = ref<FloatingPetal[]>([])
const cursorPetals = ref<CursorPetal[]>([])

const navItems = [
  { to: '/', label: '商城首页' },
  { to: '/query', label: '订单查询' },
]

let nextCursorPetalId = 1
let lastPointerX = -1
let lastPointerY = -1
let lastPointerAt = 0

function normalizeProfileText(value?: string | null) {
  const text = value?.trim()
  return text ? text : null
}

const profileUsername = computed(() => normalizeProfileText(profile.value?.username))
const profileEmail = computed(() => normalizeProfileText(profile.value?.email))
const profilePrimary = computed(() => profileUsername.value ?? profileEmail.value ?? '会员')
const profileSecondary = computed(() => {
  if (profile.value?.balance != null) {
    return `余额 ¥${Number(profile.value.balance).toFixed(2)}`
  }
  if (profileEmail.value && profileEmail.value !== profilePrimary.value) {
    return profileEmail.value
  }
  return '我的订单'
})
const profileInitial = computed(() => (profileUsername.value ?? profileEmail.value ?? 'K').slice(0, 1).toUpperCase())

function syncSearchKeyword() {
  searchKeyword.value = typeof route.query.q === 'string' ? route.query.q : ''
}

function isActive(path: string) {
  if (path === '/') {
    return route.path === '/'
  }
  return route.path.startsWith(path)
}

async function syncProfile() {
  if (!getStoredToken()) {
    return
  }
  try {
    await fetchMemberProfile()
  } catch {
    logoutMember()
  }
}

function submitSearch() {
  const keyword = searchKeyword.value.trim()
  router.push({
    path: '/',
    query: keyword ? { q: keyword } : {},
  })
}

function clearSearch() {
  searchKeyword.value = ''
  router.replace({ path: '/', query: {} })
}

function handleLogout() {
  logoutMember()
  ElMessage.success('已退出会员账号')
  router.push('/')
}

function buildFloatingPetals() {
  const petals: FloatingPetal[] = []
  for (let index = 0; index < 18; index++) {
    petals.push({
      id: index + 1,
      left: `${Math.round((index * 5.4 + Math.random() * 7) % 100)}%`,
      top: `${Math.round(-18 - Math.random() * 80)}vh`,
      width: `${12 + Math.round(Math.random() * 12)}px`,
      height: `${18 + Math.round(Math.random() * 14)}px`,
      opacity: (0.28 + Math.random() * 0.44).toFixed(2),
      duration: `${16 + Math.round(Math.random() * 15)}s`,
      delay: `${(-1 * Math.random() * 24).toFixed(2)}s`,
      drift: `${Math.round(-60 + Math.random() * 120)}px`,
    })
  }
  floatingPetals.value = petals
}

function spawnCursorPetals(clientX: number, clientY: number) {
  const petals: CursorPetal[] = []
  const count = 2 + Math.floor(Math.random() * 2)
  for (let index = 0; index < count; index++) {
    petals.push({
      id: nextCursorPetalId++,
      left: `${clientX}px`,
      top: `${clientY}px`,
      width: `${8 + Math.round(Math.random() * 6)}px`,
      height: `${12 + Math.round(Math.random() * 8)}px`,
      driftX: `${Math.round(-30 + Math.random() * 60)}px`,
      driftY: `${Math.round(-8 + Math.random() * 46)}px`,
      scale: (0.66 + Math.random() * 0.54).toFixed(2),
      rotate: `${Math.round(-80 + Math.random() * 180)}deg`,
      opacity: (0.42 + Math.random() * 0.36).toFixed(2),
    })
  }
  cursorPetals.value = [...cursorPetals.value, ...petals]
  window.setTimeout(() => {
    const expired = new Set(petals.map((item) => item.id))
    cursorPetals.value = cursorPetals.value.filter((item) => !expired.has(item.id))
  }, 1450)
}

function handlePointerMove(event: MouseEvent) {
  const now = Date.now()
  const distanceX = Math.abs(event.clientX - lastPointerX)
  const distanceY = Math.abs(event.clientY - lastPointerY)
  if (now - lastPointerAt < 44 && distanceX < 14 && distanceY < 14) {
    return
  }
  lastPointerAt = now
  lastPointerX = event.clientX
  lastPointerY = event.clientY
  spawnCursorPetals(event.clientX, event.clientY)
}

onMounted(() => {
  syncSearchKeyword()
  buildFloatingPetals()
  void syncProfile()
  window.addEventListener('mousemove', handlePointerMove, { passive: true })
})

onBeforeUnmount(() => {
  window.removeEventListener('mousemove', handlePointerMove)
})

watch(() => route.fullPath, syncSearchKeyword)
</script>

<template>
  <div class="store-app-shell">
    <div class="store-petal-layer" aria-hidden="true">
      <span
        v-for="petal in floatingPetals"
        :key="petal.id"
        class="store-petal"
        :style="{
          left: petal.left,
          top: petal.top,
          width: petal.width,
          height: petal.height,
          opacity: petal.opacity,
          animationDuration: petal.duration,
          animationDelay: petal.delay,
          '--petal-drift': petal.drift,
        }"
      />
    </div>

    <div class="store-cursor-petal-layer" aria-hidden="true">
      <span
        v-for="petal in cursorPetals"
        :key="petal.id"
        class="store-cursor-petal"
        :style="{
          left: petal.left,
          top: petal.top,
          width: petal.width,
          height: petal.height,
          opacity: petal.opacity,
          '--cursor-petal-drift-x': petal.driftX,
          '--cursor-petal-drift-y': petal.driftY,
          '--cursor-petal-scale': petal.scale,
          '--cursor-petal-rotate': petal.rotate,
        }"
      />
    </div>

    <header class="store-topbar">
      <router-link class="brand-mark" to="/">
        <span class="brand-mark__logo">K</span>
        <span class="brand-mark__meta">
          <strong>Kawang</strong>
          <em>AI 会员商城</em>
        </span>
      </router-link>

      <nav class="store-nav">
        <router-link
          v-for="item in navItems"
          :key="item.to"
          :to="item.to"
          class="store-nav__item"
          :class="{ 'is-active': isActive(item.to) }"
        >
          {{ item.label }}
        </router-link>
      </nav>

      <div class="store-topbar__actions">
        <div class="topbar-search">
          <el-input
            v-model="searchKeyword"
            :prefix-icon="Search"
            clearable
            placeholder="搜索商品关键词"
            @keyup.enter="submitSearch"
            @clear="clearSearch"
          />
        </div>

        <template v-if="profile">
          <button class="profile-chip" type="button" @click="router.push('/orders/me')">
            <span class="profile-chip__avatar">{{ profileInitial }}</span>
            <span class="profile-chip__meta">
              <strong>{{ profilePrimary }}</strong>
              <em>{{ profileSecondary }}</em>
            </span>
          </button>
          <button class="topbar-icon-button" type="button" @click="handleLogout">
            <el-icon><SwitchButton /></el-icon>
          </button>
        </template>

        <template v-else>
          <router-link class="topbar-text-link" to="/login">登录</router-link>
          <router-link class="topbar-primary-link" to="/register">注册</router-link>
        </template>
      </div>
    </header>

    <main class="store-main">
      <router-view />
    </main>
  </div>
</template>