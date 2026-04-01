<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
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

const petals = [
  { left: '4%', top: '10%', size: '16px', delay: '0s', duration: '18s', drift: '42px', opacity: 0.52 },
  { left: '14%', top: '26%', size: '12px', delay: '3s', duration: '22s', drift: '58px', opacity: 0.44 },
  { left: '27%', top: '6%', size: '14px', delay: '1.4s', duration: '20s', drift: '50px', opacity: 0.5 },
  { left: '38%', top: '18%', size: '10px', delay: '4s', duration: '24s', drift: '46px', opacity: 0.38 },
  { left: '52%', top: '8%', size: '18px', delay: '2s', duration: '19s', drift: '64px', opacity: 0.48 },
  { left: '63%', top: '30%', size: '13px', delay: '5.2s', duration: '23s', drift: '48px', opacity: 0.4 },
  { left: '74%', top: '12%', size: '15px', delay: '2.6s', duration: '20s', drift: '54px', opacity: 0.46 },
  { left: '84%', top: '24%', size: '11px', delay: '6.4s', duration: '26s', drift: '42px', opacity: 0.34 },
  { left: '92%', top: '8%', size: '17px', delay: '1s', duration: '21s', drift: '60px', opacity: 0.5 },
]

const profile = memberProfileState
const searchKeyword = ref('')

const profileInitial = computed(() => profile.value?.username.slice(0, 1).toUpperCase() ?? 'K')

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

watch(
  () => route.fullPath,
  () => {
    syncSearchKeyword()
  },
  { immediate: true },
)

onMounted(syncProfile)
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
          opacity: petal.opacity.toString(),
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
              <strong>{{ profile.username }}</strong>
              <em>我的订单</em>
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
