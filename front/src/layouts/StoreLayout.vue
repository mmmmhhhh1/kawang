<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search, SwitchButton } from '@element-plus/icons-vue'
import { fetchMemberProfile, logoutMember, memberProfileState } from '@/api/auth'
import { getStoredToken } from '@/api/http'
import SupportChatWidget from '@/components/SupportChatWidget.vue'

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
const effectsEnabled = ref(true)

const navItems = [
  { to: '/', label: '首页' },
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
  return '进入会员中心'
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
  for (let index = 0; index < 16; index++) {
    petals.push({
      id: index + 1,
      left: `${Math.round((index * 6.4 + Math.random() * 8) % 100)}%`,
      top: `${Math.round(-16 - Math.random() * 66)}vh`,
      width: `${14 + Math.round(Math.random() * 10)}px`,
      height: `${18 + Math.round(Math.random() * 12)}px`,
      opacity: (0.18 + Math.random() * 0.34).toFixed(2),
      duration: `${18 + Math.round(Math.random() * 14)}s`,
      delay: `${(-1 * Math.random() * 20).toFixed(2)}s`,
      drift: `${Math.round(-72 + Math.random() * 144)}px`,
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
      height: `${12 + Math.round(Math.random() * 6)}px`,
      driftX: `${Math.round(-28 + Math.random() * 56)}px`,
      driftY: `${Math.round(-10 + Math.random() * 42)}px`,
      scale: (0.7 + Math.random() * 0.4).toFixed(2),
      rotate: `${Math.round(-80 + Math.random() * 176)}deg`,
      opacity: (0.38 + Math.random() * 0.26).toFixed(2),
    })
  }
  cursorPetals.value = [...cursorPetals.value, ...petals]
  window.setTimeout(() => {
    const expired = new Set(petals.map((item) => item.id))
    cursorPetals.value = cursorPetals.value.filter((item) => !expired.has(item.id))
  }, 1280)
}

function handlePointerMove(event: MouseEvent) {
  const now = Date.now()
  const distanceX = Math.abs(event.clientX - lastPointerX)
  const distanceY = Math.abs(event.clientY - lastPointerY)
  if (now - lastPointerAt < 54 && distanceX < 18 && distanceY < 18) {
    return
  }
  lastPointerAt = now
  lastPointerX = event.clientX
  lastPointerY = event.clientY
  spawnCursorPetals(event.clientX, event.clientY)
}

function shouldEnableEffects() {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
    return true
  }
  return !(
    window.matchMedia('(pointer: coarse)').matches ||
    window.matchMedia('(prefers-reduced-motion: reduce)').matches
  )
}

onMounted(() => {
  syncSearchKeyword()
  effectsEnabled.value = shouldEnableEffects()
  if (effectsEnabled.value) {
    buildFloatingPetals()
    window.addEventListener('mousemove', handlePointerMove, { passive: true })
  }
  void syncProfile()
})

onBeforeUnmount(() => {
  window.removeEventListener('mousemove', handlePointerMove)
})

watch(() => route.fullPath, syncSearchKeyword)
</script>

<template>
  <div class="store-app-shell">
    <div v-if="effectsEnabled" class="store-petal-layer" aria-hidden="true">
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

    <div v-if="effectsEnabled" class="store-cursor-petal-layer" aria-hidden="true">
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

    <header class="store-topbar page-reveal">
      <router-link class="brand-mark" to="/">
        <span class="brand-mark__logo">花</span>
        <span class="brand-mark__meta">
          <strong>Kawang</strong>
          <em>樱落会员商店</em>
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
          <button class="topbar-icon-button" type="button" aria-label="退出登录" @click="handleLogout">
            <el-icon><SwitchButton /></el-icon>
          </button>
        </template>

        <template v-else>
          <router-link class="topbar-text-link" to="/login">登录</router-link>
          <router-link class="topbar-primary-link" to="/register">立即加入</router-link>
        </template>
      </div>
    </header>

    <main class="store-main">
      <router-view />
    </main>

    <SupportChatWidget v-if="profile" />
  </div>
</template>
