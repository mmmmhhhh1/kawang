<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search, SwitchButton } from '@element-plus/icons-vue'
import { fetchMemberProfile, logoutMember, memberProfileState } from '@/api/auth'
import { getStoredToken } from '@/api/http'

const route = useRoute()
const router = useRouter()

const navItems = [
  { to: '/', label: '购物' },
  { to: '/query', label: '订单查询' },
]

const petals = Array.from({ length: 26 }, (_, index) => {
  const sizes = ['10px', '12px', '14px', '16px', '18px', '20px']
  const drifts = ['42px', '56px', '68px', '84px', '96px']
  const durations = ['16s', '18s', '21s', '24s', '27s']
  return {
    left: `${(index * 9 + 3) % 98}%`,
    top: `${(index * 11 + 4) % 36}%`,
    size: sizes[index % sizes.length],
    delay: `${(index % 7) * 1.2}s`,
    duration: durations[index % durations.length],
    drift: drifts[index % drifts.length],
    opacity: (0.36 + (index % 5) * 0.1).toFixed(2),
  }
})

const profile = memberProfileState
const searchKeyword = ref('')
const cursorPetals = ref<
  Array<{
    id: number
    left: string
    top: string
    size: string
    delay: string
    duration: string
    opacity: string
    driftX: string
    driftY: string
    rotate: string
    scale: string
  }>
>([])

function normalizeProfileText(value?: string | null) {
  const text = value?.trim()
  return text ? text : null
}

const profileUsername = computed(() => normalizeProfileText(profile.value?.username))
const profileEmail = computed(() => normalizeProfileText(profile.value?.email))
const profilePrimary = computed(() => profileUsername.value ?? profileEmail.value ?? '会员')
const profileSecondary = computed(() => {
  if (profileUsername.value && profileEmail.value) {
    return profileEmail.value
  }
  if (profileEmail.value) {
    return null
  }
  return profileUsername.value ? '我的订单' : null
})
const profileInitial = computed(() =>
  (profileUsername.value ?? profileEmail.value ?? 'K').slice(0, 1).toUpperCase(),
)
const pointerTimers = new Map<number, number>()

let nextCursorPetalId = 0
let lastTrailAt = 0
let lastPointerX = -1000
let lastPointerY = -1000

function isActive(path: string) {
  if (path === '/') {
    return route.path === '/'
  }
  return route.path.startsWith(path)
}

function syncSearchKeyword() {
  searchKeyword.value = typeof route.query.q === 'string' ? route.query.q : ''
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
  if (route.path === '/' && route.query.q) {
    router.replace({ path: '/', query: {} })
  }
}

function handleLogout() {
  logoutMember()
  ElMessage.success('已退出会员账号')
  router.push('/')
}

function removeCursorPetal(id: number) {
  cursorPetals.value = cursorPetals.value.filter((petal) => petal.id !== id)
  const timer = pointerTimers.get(id)
  if (timer) {
    window.clearTimeout(timer)
    pointerTimers.delete(id)
  }
}

function spawnCursorTrail(clientX: number, clientY: number) {
  const batch = Array.from({ length: 4 }, (_, index) => {
    const id = nextCursorPetalId++
    const size = 6.2 + Math.random() * 4.4
    const lifetime = 1.12 + Math.random() * 0.34

    return {
      id,
      left: `${clientX + (Math.random() - 0.5) * 14}px`,
      top: `${clientY + (Math.random() - 0.5) * 10}px`,
      size: `${size.toFixed(1)}px`,
      delay: `${index * 0.03}s`,
      duration: `${lifetime.toFixed(2)}s`,
      opacity: (0.5 + Math.random() * 0.26).toFixed(2),
      driftX: `${(-14 + Math.random() * 28).toFixed(1)}px`,
      driftY: `${(12 + Math.random() * 18).toFixed(1)}px`,
      rotate: `${(-46 + Math.random() * 92).toFixed(0)}deg`,
      scale: (0.9 + Math.random() * 0.28).toFixed(2),
    }
  })

  cursorPetals.value = [...cursorPetals.value, ...batch].slice(-56)

  batch.forEach((petal) => {
    const timer = window.setTimeout(() => removeCursorPetal(petal.id), 1850)
    pointerTimers.set(petal.id, timer)
  })
}

function handlePointerMove(event: PointerEvent) {
  if (event.pointerType === 'touch') {
    return
  }

  const now = performance.now()
  const movedEnough =
    Math.abs(event.clientX - lastPointerX) + Math.abs(event.clientY - lastPointerY) > 16

  if (!movedEnough || now - lastTrailAt < 48) {
    return
  }

  lastTrailAt = now
  lastPointerX = event.clientX
  lastPointerY = event.clientY
  spawnCursorTrail(event.clientX, event.clientY)
}

watch(
  () => route.fullPath,
  () => {
    syncSearchKeyword()
  },
  { immediate: true },
)

onMounted(() => {
  syncProfile()
  window.addEventListener('pointermove', handlePointerMove, { passive: true })
})

onBeforeUnmount(() => {
  window.removeEventListener('pointermove', handlePointerMove)
  pointerTimers.forEach((timer) => window.clearTimeout(timer))
  pointerTimers.clear()
})
</script>

<template>
  <div class="store-app-shell">
    <div class="store-rain-layer rain-layer-one"></div>
    <div class="store-rain-layer rain-layer-two"></div>
    <div class="store-petal-layer">
      <span
        v-for="(petal, index) in petals"
        :key="index"
        class="store-petal"
        :style="{
          left: petal.left,
          top: petal.top,
          width: petal.size,
          height: petal.size,
          animationDelay: petal.delay,
          animationDuration: petal.duration,
          '--petal-drift': petal.drift,
          opacity: petal.opacity,
        }"
      ></span>
    </div>
    <div class="store-cursor-petal-layer">
      <span
        v-for="petal in cursorPetals"
        :key="petal.id"
        class="store-cursor-petal"
        :style="{
          left: petal.left,
          top: petal.top,
          width: petal.size,
          height: petal.size,
          animationDelay: petal.delay,
          animationDuration: petal.duration,
          opacity: petal.opacity,
          '--cursor-petal-drift-x': petal.driftX,
          '--cursor-petal-drift-y': petal.driftY,
          '--cursor-petal-rotate': petal.rotate,
          '--cursor-petal-scale': petal.scale,
        }"
      ></span>
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
          <button class="topbar-search__submit" type="button" @click="submitSearch">搜索</button>
        </div>

        <template v-if="profile">
          <button class="profile-chip" type="button" @click="router.push('/orders/me')">
            <span class="profile-chip__avatar">{{ profileInitial }}</span>
            <span class="profile-chip__meta">
              <strong>{{ profilePrimary }}</strong>
              <em v-if="profileSecondary">{{ profileSecondary }}</em>
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
