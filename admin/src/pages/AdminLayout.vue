<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Bell, Box, Goods, List, Setting, SwitchButton, UserFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import {
  adminProfileState,
  fetchMe,
  hasAdminPermission,
  logout,
  type AdminPermission,
} from '@/api/auth'

const router = useRouter()
const route = useRoute()
const profile = adminProfileState

const menuItems: Array<{
  index: string
  label: string
  icon: any
  permission?: AdminPermission
}> = [
  { index: '/products', label: '商品管理', icon: Goods },
  { index: '/accounts', label: '卡密池管理', icon: Box },
  { index: '/orders', label: '订单管理', icon: List },
  { index: '/users', label: '会员管理', icon: UserFilled },
  { index: '/admins', label: '管理员管理', icon: Setting, permission: 'CREATE_ADMIN' },
  { index: '/notices', label: '公告管理', icon: Bell },
]

const visibleMenuItems = computed(() =>
  menuItems.filter((item) => !item.permission || hasAdminPermission(item.permission, profile.value)),
)

const currentMenuLabel = computed(() => {
  const current = menuItems.find((item) => route.path.startsWith(item.index))
  return current?.label ?? '管理台'
})

async function syncProfile() {
  try {
    await fetchMe()
  } catch {
    logoutAndRedirect()
  }
}

function logoutAndRedirect() {
  logout()
  ElMessage.success('已退出登录')
  router.replace('/login')
}

watch(
  () => [route.path, profile.value],
  () => {
    const current = menuItems.find((item) => route.path.startsWith(item.index))
    if (current?.permission && profile.value && !hasAdminPermission(current.permission, profile.value)) {
      router.replace('/products')
    }
  },
  { immediate: true },
)

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
        <el-menu-item v-for="item in visibleMenuItems" :key="item.index" :index="item.index">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </el-menu-item>
      </el-menu>
    </aside>

    <div class="layout-main">
      <header class="layout-header">
        <div>
          <p class="muted">当前页面</p>
          <h2>{{ currentMenuLabel }}</h2>
        </div>
        <div class="header-actions">
          <div class="profile-chip">
            <strong>{{ profile?.displayName ?? '管理员' }}</strong>
            <span>{{ profile?.username ?? '-' }}</span>
            <small>{{ profile?.isSuperAdmin ? '最高权限管理员' : '普通管理员' }}</small>
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
.profile-chip span,
.profile-chip small {
  display: block;
}

.profile-chip span {
  margin-top: 4px;
  color: #697b8c;
  font-size: 13px;
}

.profile-chip small {
  margin-top: 4px;
  color: #94a3b8;
  font-size: 12px;
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
