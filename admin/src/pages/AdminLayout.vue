<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Bell, Box, Goods, List, SwitchButton } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { fetchMe, getStoredProfile, logout, type AdminProfile } from '@/api/auth'

const router = useRouter()
const route = useRoute()
const profile = ref<AdminProfile | null>(getStoredProfile())

const menuItems = [
  { index: '/products', label: '商品管理', icon: Goods },
  { index: '/accounts', label: '账号池', icon: Box },
  { index: '/orders', label: '订单管理', icon: List },
  { index: '/notices', label: '公告管理', icon: Bell },
]

async function syncProfile() {
  try {
    profile.value = await fetchMe()
  } catch {
    logoutAndRedirect()
  }
}

function logoutAndRedirect() {
  logout()
  ElMessage.success('已退出登录')
  router.replace('/login')
}

onMounted(syncProfile)
</script>

<template>
  <div class="layout-shell">
    <aside class="sidebar">
      <div class="brand">
        <p>KAWANG</p>
        <h1>后台管理</h1>
      </div>

      <el-menu
        :default-active="route.path"
        class="side-menu"
        background-color="transparent"
        text-color="#d8e7f4"
        active-text-color="#ffffff"
        router
      >
        <el-menu-item v-for="item in menuItems" :key="item.index" :index="item.index">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </el-menu-item>
      </el-menu>
    </aside>

    <div class="layout-main">
      <header class="layout-header">
        <div>
          <p class="muted">当前页面</p>
          <h2>{{ menuItems.find((item) => route.path.startsWith(item.index))?.label ?? '管理台' }}</h2>
        </div>
        <div class="header-actions">
          <div class="profile-chip">
            <strong>{{ profile?.displayName ?? '管理员' }}</strong>
            <span>{{ profile?.username ?? '-' }}</span>
          </div>
          <el-button plain @click="logoutAndRedirect">
            <el-icon><SwitchButton /></el-icon>
            退出
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
  grid-template-columns: 260px minmax(0, 1fr);
}

.sidebar {
  padding: 28px 18px;
  background: linear-gradient(180deg, #102a43, #163f64);
  color: #fff;
}

.brand {
  padding: 14px 14px 24px;
}

.brand p {
  margin: 0;
  font-size: 12px;
  letter-spacing: 0.24em;
  color: #8bd5ff;
}

.brand h1 {
  margin: 8px 0 0;
  font-size: 28px;
}

.side-menu {
  border-right: none;
}

.side-menu :deep(.el-menu-item) {
  margin-bottom: 8px;
  border-radius: 14px;
}

.side-menu :deep(.el-menu-item.is-active) {
  background: rgba(255, 255, 255, 0.14);
}

.layout-main {
  padding: 18px;
}

.layout-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  padding: 20px 24px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.82);
  box-shadow: 0 16px 45px rgba(15, 23, 42, 0.08);
}

.layout-header h2 {
  margin: 6px 0 0;
  font-size: 28px;
}

.header-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}

.profile-chip {
  padding: 12px 14px;
  border-radius: 16px;
  background: #f4f8fb;
}

.profile-chip strong,
.profile-chip span {
  display: block;
}

.profile-chip span {
  margin-top: 4px;
  color: #697b8c;
  font-size: 13px;
}

.content {
  padding-top: 18px;
}

@media (max-width: 960px) {
  .layout-shell {
    grid-template-columns: 1fr;
  }

  .sidebar {
    padding-bottom: 12px;
  }

  .layout-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .header-actions {
    width: 100%;
    justify-content: space-between;
  }
}
</style>
