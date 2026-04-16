<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminProfileState, hasAdminPermission } from '@/api/auth'
import {
  getUserActivities,
  getUserActivity,
  getUserDetail,
  getUserPage,
  updateUserStatus,
  type MemberActivity,
  type MemberDetail,
  type MemberListItem,
  type MemberStatus,
} from '@/api/users'
import { useCursorPager } from '@/utils/cursorPager'

type MemberListRow = MemberListItem & {
  lastSeenAt: string | null
  lastLoginAt: string | null
}

const PAGE_SIZE = 10
const POLL_INTERVAL = 30_000

const loading = ref(false)
const detailLoading = ref(false)
const detailVisible = ref(false)
const members = ref<MemberListRow[]>([])
const detail = ref<MemberDetail | null>(null)
const detailActivity = ref<MemberActivity | null>(null)
const pager = useCursorPager()
let pollTimer: number | null = null

const filters = reactive({
  keyword: '',
  status: '' as '' | MemberStatus,
})

const canToggleUser = computed(() => hasAdminPermission('DISABLE_USER', adminProfileState.value))

function displayText(value?: string | null) {
  return value?.trim() ? value : '-'
}

function statusLabel(status: MemberStatus) {
  return status === 'ACTIVE' ? '正常' : '已停用'
}

function timeText(value?: string | null) {
  return value || '-'
}

function memberOrderStatusLabel(status: string) {
  if (status === 'SUCCESS') return '已成功'
  if (status === 'CLOSED') return '已关闭'
  return status
}

function mergeActivities(activities: MemberActivity[]) {
  if (!activities.length) {
    return
  }
  const activityMap = new Map(activities.map((item) => [item.userId, item]))
  members.value = members.value.map((member) => {
    const activity = activityMap.get(member.id)
    if (!activity) {
      return member
    }
    return {
      ...member,
      lastSeenAt: activity.lastSeenAt,
      lastLoginAt: activity.lastLoginAt,
    }
  })
}

async function refreshMemberActivities(silent = false) {
  const ids = members.value.map((item) => item.id)
  if (!ids.length) {
    return
  }

  try {
    const activities = await getUserActivities(ids)
    mergeActivities(activities)
  } catch (error: any) {
    if (!silent) {
      ElMessage.error(error?.response?.data?.message ?? '会员活动时间加载失败')
    }
  }
}

async function refreshDetailActivity(silent = false) {
  if (!detailVisible.value || !detail.value) {
    return
  }

  try {
    detailActivity.value = await getUserActivity(detail.value.id)
  } catch (error: any) {
    if (!silent) {
      ElMessage.error(error?.response?.data?.message ?? '会员活动时间加载失败')
    }
  }
}

async function loadMembers(page = 1) {
  loading.value = true
  try {
    const result = await getUserPage({
      size: PAGE_SIZE,
      cursor: pager.getCursor(page),
      keyword: filters.keyword || undefined,
      status: filters.status || undefined,
    })
    members.value = result.items.map((item) => ({
      ...item,
      lastSeenAt: null,
      lastLoginAt: null,
    }))
    pager.commit(page, result.nextCursor, result.hasMore)
    await refreshMemberActivities(true)
  } finally {
    loading.value = false
  }
}

function resetAndLoad() {
  pager.reset()
  void loadMembers(1)
}

function goPrevPage() {
  if (!pager.canPrev.value) {
    return
  }
  void loadMembers(pager.currentPage.value - 1)
}

function goNextPage() {
  if (!pager.canNext.value) {
    return
  }
  void loadMembers(pager.currentPage.value + 1)
}

async function openDetail(id: number) {
  detailVisible.value = true
  detailLoading.value = true
  try {
    const [detailData, activityData] = await Promise.all([getUserDetail(id), getUserActivity(id)])
    detail.value = detailData
    detailActivity.value = activityData
  } catch (error: any) {
    detailVisible.value = false
    ElMessage.error(error?.response?.data?.message ?? '会员详情加载失败')
  } finally {
    detailLoading.value = false
  }
}

function resetFilters() {
  filters.keyword = ''
  filters.status = ''
  resetAndLoad()
}

function stopPolling() {
  if (pollTimer !== null) {
    window.clearInterval(pollTimer)
    pollTimer = null
  }
}

function startPolling() {
  stopPolling()
  pollTimer = window.setInterval(() => {
    void refreshMemberActivities(true)
    if (detailVisible.value) {
      void refreshDetailActivity(true)
    }
  }, POLL_INTERVAL)
}

async function toggleStatus(row: MemberListRow) {
  if (!canToggleUser.value) {
    return
  }

  const nextStatus: MemberStatus = row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  const actionLabel = nextStatus === 'ACTIVE' ? '启用' : '停用'
  try {
    await ElMessageBox.confirm(`确定${actionLabel}会员“${displayText(row.username)}”吗？`, `${actionLabel}会员`, {
      type: 'warning',
      confirmButtonText: `确认${actionLabel}`,
      cancelButtonText: '取消',
    })
    await updateUserStatus(row.id, nextStatus)
    ElMessage.success(`会员已${actionLabel}`)
    if (detail.value?.id === row.id) {
      const [detailData, activityData] = await Promise.all([getUserDetail(row.id), getUserActivity(row.id)])
      detail.value = detailData
      detailActivity.value = activityData
    }
    resetAndLoad()
  } catch (error: any) {
    if (error === 'cancel' || error === 'close') {
      return
    }
    ElMessage.error(error?.response?.data?.message ?? `${actionLabel}会员失败`)
  }
}

watch(detailVisible, (visible) => {
  if (!visible) {
    detail.value = null
    detailActivity.value = null
  }
})

onMounted(async () => {
  pager.reset()
  await loadMembers(1)
  startPolling()
})

onBeforeUnmount(() => {
  stopPolling()
})
</script>

<template>
  <div class="admin-page">
    <el-card class="page-card" shadow="never">
      <div class="page-header">
        <div>
          <p>会员列表现在固定每页 10 条，页内继续轮询最近活跃和最近登录时间，翻页底层保持高性能游标分页。</p>
          <h1>会员管理</h1>
        </div>
      </div>

      <div class="toolbar">
        <el-input v-model="filters.keyword" clearable placeholder="搜索用户名或邮箱" @keyup.enter="resetAndLoad" />
        <el-select v-model="filters.status" clearable placeholder="按状态筛选">
          <el-option label="正常" value="ACTIVE" />
          <el-option label="已停用" value="DISABLED" />
        </el-select>
        <el-button type="primary" @click="resetAndLoad">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>

      <el-table :data="members" v-loading="loading" border>
        <el-table-column label="用户名" min-width="180">
          <template #default="{ row }">{{ displayText(row.username) }}</template>
        </el-table-column>
        <el-table-column label="邮箱" min-width="220">
          <template #default="{ row }">{{ displayText(row.email) }}</template>
        </el-table-column>
        <el-table-column label="账号状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'danger'">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="上次活跃时间" min-width="180">
          <template #default="{ row }">{{ timeText(row.lastSeenAt) }}</template>
        </el-table-column>
        <el-table-column label="最近登录时间" min-width="180">
          <template #default="{ row }">{{ timeText(row.lastLoginAt) }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="注册时间" min-width="180" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row.id)">详情</el-button>
            <el-button
              v-if="canToggleUser"
              link
              :type="row.status === 'ACTIVE' ? 'warning' : 'success'"
              @click="toggleStatus(row)"
            >
              {{ row.status === 'ACTIVE' ? '停用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager-bar">
        <span class="pager-bar__summary">第 {{ pager.currentPage }} 页，每页 {{ PAGE_SIZE }} 条</span>
        <div class="pager-bar__actions">
          <el-button :disabled="!pager.canPrev || loading" @click="goPrevPage">上一页</el-button>
          <el-button type="primary" :disabled="!pager.canNext || loading" @click="goNextPage">下一页</el-button>
        </div>
      </div>
    </el-card>

    <el-drawer v-model="detailVisible" size="min(860px, 94vw)" title="会员详情">
      <el-skeleton :loading="detailLoading" animated :rows="8">
        <template v-if="detail">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="用户名">{{ displayText(detail.username) }}</el-descriptions-item>
            <el-descriptions-item label="邮箱">{{ displayText(detail.email) }}</el-descriptions-item>
            <el-descriptions-item label="账号状态">{{ statusLabel(detail.status) }}</el-descriptions-item>
            <el-descriptions-item label="上次活跃时间">{{ timeText(detailActivity?.lastSeenAt) }}</el-descriptions-item>
            <el-descriptions-item label="最近登录时间">{{ timeText(detailActivity?.lastLoginAt) }}</el-descriptions-item>
            <el-descriptions-item label="注册时间">{{ timeText(detail.createdAt) }}</el-descriptions-item>
            <el-descriptions-item label="更新时间">{{ timeText(detail.updatedAt) }}</el-descriptions-item>
          </el-descriptions>

          <div class="member-orders-section">
            <div class="member-orders-section__head">
              <h3>已购订单</h3>
              <span>共 {{ detail.orders.length }} 笔</span>
            </div>

            <el-empty v-if="!detail.orders.length" description="该会员暂时没有订单" />

            <div v-else class="member-orders-grid">
              <article v-for="order in detail.orders" :key="order.id" class="member-order-card">
                <div class="member-order-card__head">
                  <div>
                    <strong>{{ order.productTitle }}</strong>
                    <span>{{ order.orderNo }}</span>
                  </div>
                  <el-tag :type="order.status === 'SUCCESS' ? 'success' : 'info'">{{ memberOrderStatusLabel(order.status) }}</el-tag>
                </div>

                <div class="member-order-card__meta">
                  <span>数量：{{ order.quantity }}</span>
                  <span>金额：¥{{ Number(order.totalAmount).toFixed(2) }}</span>
                  <span>联系方式：{{ order.buyerContact }}</span>
                  <span>下单时间：{{ order.createdAt }}</span>
                </div>

                <div class="member-order-card__keys">
                  <strong>卡密列表</strong>
                  <div v-if="order.cardKeys.length" class="member-order-card__tags">
                    <el-tag v-for="item in order.cardKeys" :key="item" type="info" effect="plain">{{ item }}</el-tag>
                  </div>
                  <p v-else class="muted">该订单没有可展示的卡密。</p>
                </div>
              </article>
            </div>
          </div>
        </template>
      </el-skeleton>
    </el-drawer>
  </div>
</template>

<style scoped>
.pager-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-top: 18px;
}

.pager-bar__summary {
  color: #5f7285;
  font-size: 13px;
}

.pager-bar__actions {
  display: flex;
  gap: 12px;
}

.member-orders-section {
  margin-top: 24px;
}

.member-orders-section__head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  margin-bottom: 16px;
}

.member-orders-grid {
  display: grid;
  gap: 16px;
}

.member-order-card {
  padding: 18px;
  border-radius: 18px;
  background: #f8fbfd;
  border: 1px solid #e5edf3;
}

.member-order-card__head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.member-order-card__head strong,
.member-order-card__head span {
  display: block;
}

.member-order-card__head span {
  margin-top: 6px;
  color: #6b7a88;
  font-size: 13px;
}

.member-order-card__meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 14px;
  color: #364654;
  font-size: 14px;
}

.member-order-card__keys {
  margin-top: 16px;
}

.member-order-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

@media (max-width: 768px) {
  .pager-bar {
    flex-direction: column;
    align-items: stretch;
  }

  .pager-bar__actions {
    width: 100%;
  }

  .pager-bar__actions :deep(.el-button) {
    flex: 1;
  }

  .member-order-card__head,
  .member-orders-section__head {
    flex-direction: column;
    align-items: flex-start;
  }

  .member-order-card__meta {
    grid-template-columns: 1fr;
  }
}
</style>