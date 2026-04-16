<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminProfileState, hasAdminPermission } from '@/api/auth'
import {
  closeOrder,
  deleteOrder,
  getOrderDetail,
  getOrders,
  type OrderDetail,
  type OrderRecord,
} from '@/api/orders'
import { getProducts, type ProductRecord } from '@/api/products'
import { useCursorPager } from '@/utils/cursorPager'

const PAGE_SIZE = 10

const loading = ref(false)
const detailVisible = ref(false)
const products = ref<ProductRecord[]>([])
const orders = ref<OrderRecord[]>([])
const detail = ref<OrderDetail | null>(null)
const pager = useCursorPager()

const query = reactive({
  status: '',
  productId: undefined as number | undefined,
  keyword: '',
})

const canDeleteOrder = computed(() => hasAdminPermission('DELETE_ORDER', adminProfileState.value))

function formatMoney(value: number) {
  return `¥${Number(value ?? 0).toFixed(2)}`
}

function orderStatusLabel(status: string) {
  if (status === 'SUCCESS') return '已成功'
  if (status === 'CLOSED') return '已关闭'
  return status
}

function enableStatusLabel(status?: string | null) {
  if (!status) return '-'
  return status === 'ENABLED' ? '启用' : '停用'
}

function usedStatusLabel(status?: string | null) {
  if (!status) return '-'
  return status === 'USED' ? '已使用' : '未使用'
}

async function loadProducts() {
  products.value = await getProducts()
}

async function loadOrders(page = 1) {
  loading.value = true
  try {
    const result = await getOrders({
      size: PAGE_SIZE,
      cursor: pager.getCursor(page),
      status: query.status || undefined,
      productId: query.productId,
      keyword: query.keyword || undefined,
    })
    orders.value = result.items
    pager.commit(page, result.nextCursor, result.hasMore)
  } finally {
    loading.value = false
  }
}

function resetAndLoad() {
  pager.reset()
  void loadOrders(1)
}

function goPrevPage() {
  if (!pager.canPrev.value) {
    return
  }
  void loadOrders(pager.currentPage.value - 1)
}

function goNextPage() {
  if (!pager.canNext.value) {
    return
  }
  void loadOrders(pager.currentPage.value + 1)
}

function resetFilters() {
  query.status = ''
  query.productId = undefined
  query.keyword = ''
  resetAndLoad()
}

async function openDetail(id: number) {
  detail.value = await getOrderDetail(id)
  detailVisible.value = true
}

async function handleClose(row: OrderRecord) {
  try {
    const { value } = await ElMessageBox.prompt('请输入关闭原因', '关闭订单', {
      confirmButtonText: '确认关闭',
      cancelButtonText: '取消',
      inputValidator: (input) => !!input || '关闭原因不能为空',
    })
    await closeOrder(row.id, value)
    ElMessage.success('订单已关闭')
    if (detail.value?.id === row.id) {
      detail.value = await getOrderDetail(row.id)
    }
    resetAndLoad()
  } catch (error: any) {
    if (error === 'cancel') {
      return
    }
    ElMessage.error(error?.response?.data?.message ?? '关闭订单失败')
  }
}

async function handleDelete(row: OrderRecord) {
  if (!canDeleteOrder.value) {
    return
  }

  try {
    await ElMessageBox.confirm(`确定删除订单“${row.orderNo}”吗？`, '删除订单', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
    })
    await deleteOrder(row.id)
    ElMessage.success('订单已删除')
    if (detail.value?.id === row.id) {
      detailVisible.value = false
      detail.value = null
    }
    resetAndLoad()
  } catch (error: any) {
    if (error === 'cancel' || error === 'close') {
      return
    }
    ElMessage.error(error?.response?.data?.message ?? '删除订单失败')
  }
}

onMounted(async () => {
  await loadProducts()
  pager.reset()
  await loadOrders(1)
})
</script>

<template>
  <div class="admin-page">
    <el-card class="page-card" shadow="never">
      <div class="page-header">
        <div>
          <p>订单列表现在固定每页 10 条，翻页仍然走游标分页。关闭订单会执行退款与卡密回滚，余额订单不允许直接硬删除。</p>
          <h1>订单管理</h1>
        </div>
      </div>

      <div class="toolbar toolbar--wrap">
        <el-select v-model="query.productId" clearable placeholder="按商品筛选">
          <el-option v-for="item in products" :key="item.id" :label="item.title" :value="item.id" />
        </el-select>
        <el-select v-model="query.status" clearable placeholder="按状态筛选">
          <el-option label="成功" value="SUCCESS" />
          <el-option label="已关闭" value="CLOSED" />
        </el-select>
        <el-input v-model="query.keyword" placeholder="搜索订单号或联系方式前缀" clearable @keyup.enter="resetAndLoad" />
        <el-button type="primary" @click="resetAndLoad">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>

      <el-table :data="orders" v-loading="loading" border>
        <el-table-column prop="orderNo" label="订单号" min-width="180" />
        <el-table-column prop="productTitleSnapshot" label="商品" min-width="220" />
        <el-table-column prop="buyerName" label="买家" width="120" />
        <el-table-column prop="buyerContact" label="联系方式" min-width="180" />
        <el-table-column prop="quantity" label="数量" width="80" />
        <el-table-column label="金额" width="120">
          <template #default="{ row }">{{ formatMoney(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === 'SUCCESS' ? 'success' : 'info'">{{ orderStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" min-width="180" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row.id)">详情</el-button>
            <el-button v-if="row.status === 'SUCCESS'" link type="warning" @click="handleClose(row)">关闭</el-button>
            <el-button v-if="canDeleteOrder" link type="danger" @click="handleDelete(row)">删除</el-button>
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

    <el-drawer v-model="detailVisible" size="min(760px, 92vw)" title="订单详情">
      <template v-if="detail">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="订单号">{{ detail.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="商品">{{ detail.productTitleSnapshot }}</el-descriptions-item>
          <el-descriptions-item label="买家">{{ detail.buyerName }}</el-descriptions-item>
          <el-descriptions-item label="联系方式">{{ detail.buyerContact }}</el-descriptions-item>
          <el-descriptions-item label="数量">{{ detail.quantity }}</el-descriptions-item>
          <el-descriptions-item label="金额">{{ formatMoney(detail.totalAmount) }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ orderStatusLabel(detail.status) }}</el-descriptions-item>
          <el-descriptions-item label="备注">{{ detail.buyerRemark || '-' }}</el-descriptions-item>
          <el-descriptions-item label="关闭原因">{{ detail.closedReason || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div class="order-keys-section">
          <div class="order-keys-section__head">
            <h3>订单发放卡密</h3>
          </div>
          <el-empty v-if="!detail.cardKeys.length" description="该订单没有可展示的卡密" />
          <div v-else class="order-key-list">
            <article
              v-for="item in detail.cardKeys"
              :key="item.accountId || item.cardKey"
              class="order-key-card"
            >
              <div class="order-key-card__main">
                <strong>{{ item.cardKey }}</strong>
                <div class="order-key-card__tags">
                  <el-tag v-if="item.enableStatus" :type="item.enableStatus === 'ENABLED' ? 'success' : 'info'" effect="plain">
                    {{ enableStatusLabel(item.enableStatus) }}
                  </el-tag>
                  <el-tag v-if="item.usedStatus" :type="item.usedStatus === 'USED' ? 'warning' : 'success'" effect="plain">
                    {{ usedStatusLabel(item.usedStatus) }}
                  </el-tag>
                </div>
              </div>
            </article>
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

.order-keys-section {
  margin-top: 24px;
}

.order-keys-section__head {
  margin-bottom: 16px;
}

.order-key-list {
  display: grid;
  gap: 12px;
}

.order-key-card {
  padding: 16px 18px;
  border-radius: 18px;
  background: #f8fbfd;
  border: 1px solid #e5edf3;
}

.order-key-card__main {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.order-key-card__tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
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

  .order-key-card__main {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>