<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminProfileState } from '@/api/auth'
import {
  approveRecharge,
  getRechargeDetail,
  getRechargePage,
  getRechargeScreenshotBlob,
  rejectRecharge,
  type RechargeDetail,
  type RechargeRecord,
} from '@/api/recharges'
import { useCursorPager } from '@/utils/cursorPager'

const PAGE_SIZE = 10

const loading = ref(false)
const detailVisible = ref(false)
const screenshotLoading = ref(false)
const detail = ref<RechargeDetail | null>(null)
const detailScreenshotUrl = ref('')
const records = ref<RechargeRecord[]>([])
const pager = useCursorPager()

const filters = reactive({
  status: 'PENDING',
  userKeyword: '',
})

const canReview = computed(() => Boolean(adminProfileState.value?.isSuperAdmin))

function revokeDetailScreenshot() {
  if (detailScreenshotUrl.value) {
    window.URL.revokeObjectURL(detailScreenshotUrl.value)
    detailScreenshotUrl.value = ''
  }
}

function formatStatus(status: RechargeRecord['status']) {
  if (status === 'APPROVED') return '已通过'
  if (status === 'REJECTED') return '已拒绝'
  return '待审核'
}

function tagType(status: RechargeRecord['status']) {
  if (status === 'APPROVED') return 'success'
  if (status === 'REJECTED') return 'danger'
  return 'warning'
}

async function loadRecharges(page = 1) {
  loading.value = true
  try {
    const result = await getRechargePage({
      size: PAGE_SIZE,
      cursor: pager.getCursor(page),
      status: filters.status || undefined,
      userKeyword: filters.userKeyword || undefined,
    })
    records.value = result.items
    pager.commit(page, result.nextCursor, result.hasMore)
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '充值记录加载失败')
  } finally {
    loading.value = false
  }
}

function resetAndLoad() {
  pager.reset()
  void loadRecharges(1)
}

function goPrevPage() {
  if (!pager.canPrev.value) {
    return
  }
  void loadRecharges(pager.currentPage.value - 1)
}

function goNextPage() {
  if (!pager.canNext.value) {
    return
  }
  void loadRecharges(pager.currentPage.value + 1)
}

function resetFilters() {
  filters.status = 'PENDING'
  filters.userKeyword = ''
  resetAndLoad()
}

async function loadDetailScreenshot(screenshotUrl: string) {
  screenshotLoading.value = true
  revokeDetailScreenshot()
  try {
    const blob = await getRechargeScreenshotBlob(screenshotUrl)
    detailScreenshotUrl.value = window.URL.createObjectURL(blob)
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '付款截图加载失败')
  } finally {
    screenshotLoading.value = false
  }
}

async function openDetail(id: number) {
  try {
    const detailData = await getRechargeDetail(id)
    detail.value = detailData
    detailVisible.value = true
    await loadDetailScreenshot(detailData.screenshotUrl)
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '充值详情加载失败')
  }
}

async function handleApprove(row: RechargeRecord) {
  if (!canReview.value) {
    return
  }
  try {
    await ElMessageBox.confirm(`确定通过充值申请 ${row.requestNo} 吗？`, '通过充值', {
      type: 'warning',
      confirmButtonText: '确认通过',
      cancelButtonText: '取消',
    })
    detail.value = await approveRecharge(row.id)
    ElMessage.success('充值已审核通过并完成入账')
    resetAndLoad()
  } catch (error: any) {
    if (error === 'cancel' || error === 'close') {
      return
    }
    ElMessage.error(error?.response?.data?.message ?? '审核通过失败')
  }
}

async function handleReject(row: RechargeRecord) {
  if (!canReview.value) {
    return
  }
  try {
    const { value } = await ElMessageBox.prompt('请输入拒绝原因', '拒绝充值', {
      confirmButtonText: '确认拒绝',
      cancelButtonText: '取消',
      inputValidator: (input) => !!input || '拒绝原因不能为空',
    })
    detail.value = await rejectRecharge(row.id, value)
    ElMessage.success('充值申请已拒绝')
    resetAndLoad()
  } catch (error: any) {
    if (error === 'cancel') {
      return
    }
    ElMessage.error(error?.response?.data?.message ?? '拒绝充值失败')
  }
}

watch(detailVisible, (visible) => {
  if (!visible) {
    detail.value = null
    revokeDetailScreenshot()
  }
})

onMounted(() => {
  resetAndLoad()
})

onBeforeUnmount(() => {
  revokeDetailScreenshot()
})
</script>

<template>
  <div class="admin-page">
    <el-card class="page-card" shadow="never">
      <div class="page-header">
        <div>
          <p>充值审核现在固定每页 10 条，管理端收到用户充值申请后会实时弹窗和语音提示。所有管理员都能看到记录，只有超级管理员可以审核入账。</p>
          <h1>充值审核</h1>
        </div>
      </div>

      <div class="toolbar toolbar--wrap">
        <el-select v-model="filters.status" clearable placeholder="按状态筛选">
          <el-option label="待审核" value="PENDING" />
          <el-option label="已通过" value="APPROVED" />
          <el-option label="已拒绝" value="REJECTED" />
        </el-select>
        <el-input v-model="filters.userKeyword" clearable placeholder="搜索用户名或邮箱前缀" @keyup.enter="resetAndLoad" />
        <el-button type="primary" @click="resetAndLoad">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>

      <el-table :data="records" v-loading="loading" border>
        <el-table-column prop="requestNo" label="申请单号" min-width="180" />
        <el-table-column prop="username" label="用户名" min-width="140">
          <template #default="{ row }">{{ row.username || '-' }}</template>
        </el-table-column>
        <el-table-column prop="email" label="邮箱" min-width="200">
          <template #default="{ row }">{{ row.email || '-' }}</template>
        </el-table-column>
        <el-table-column prop="amount" label="金额" width="120" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="tagType(row.status)">{{ formatStatus(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="申请时间" min-width="180" />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row.id)">详情</el-button>
            <el-button v-if="canReview && row.status === 'PENDING'" link type="success" @click="handleApprove(row)">通过</el-button>
            <el-button v-if="canReview && row.status === 'PENDING'" link type="danger" @click="handleReject(row)">拒绝</el-button>
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

    <el-drawer v-model="detailVisible" size="min(780px, 94vw)" title="充值详情">
      <template v-if="detail">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="申请单号">{{ detail.requestNo }}</el-descriptions-item>
          <el-descriptions-item label="用户名">{{ detail.username || '-' }}</el-descriptions-item>
          <el-descriptions-item label="邮箱">{{ detail.email || '-' }}</el-descriptions-item>
          <el-descriptions-item label="金额">{{ detail.amount }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ formatStatus(detail.status) }}</el-descriptions-item>
          <el-descriptions-item label="付款备注">{{ detail.payerRemark || '-' }}</el-descriptions-item>
          <el-descriptions-item label="拒绝原因">{{ detail.rejectReason || '-' }}</el-descriptions-item>
          <el-descriptions-item label="审核人">{{ detail.reviewedByName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="申请时间">{{ detail.createdAt }}</el-descriptions-item>
          <el-descriptions-item label="审核时间">{{ detail.reviewedAt || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div class="detail-screenshot">
          <h3>付款截图</h3>
          <div class="detail-screenshot__card" v-loading="screenshotLoading">
            <img v-if="detailScreenshotUrl" :src="detailScreenshotUrl" alt="付款截图" />
            <el-empty v-else description="暂未加载付款截图" />
          </div>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.toolbar--wrap {
  flex-wrap: wrap;
}

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

.detail-screenshot {
  margin-top: 20px;
}

.detail-screenshot__card {
  margin-top: 12px;
  padding: 16px;
  border-radius: 20px;
  background: #f6fbff;
  border: 1px solid #dfeaf3;
  min-height: 240px;
}

.detail-screenshot__card img {
  display: block;
  width: 100%;
  border-radius: 16px;
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
}
</style>