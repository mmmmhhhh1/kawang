<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  bulkDisableAccounts,
  bulkEnableAccounts,
  createAccounts,
  deleteAccount,
  getAccountDetail,
  getAccountPage,
  type AccountDetail,
  type AccountRecord,
  type AccountUsedStatus,
  updateAccountStatus,
  updateAccountUsedStatus,
} from '@/api/accounts'
import { searchProductOptions, type ProductOptionRecord } from '@/api/products'
import { useCursorPager } from '@/utils/cursorPager'

const PAGE_SIZE = 10
const PRODUCT_OPTION_PAGE_SIZE = 20
const PRODUCT_SEARCH_DELAY = 220

const loading = ref(false)
const creating = ref(false)
const detailVisible = ref(false)
const createVisible = ref(false)
const productsLoading = ref(false)
const products = ref<ProductOptionRecord[]>([])
const accounts = ref<AccountRecord[]>([])
const detail = ref<AccountDetail | null>(null)
const pager = useCursorPager()
const productCache = new Map<number, ProductOptionRecord>()
let productSearchTimer: number | null = null

const filters = reactive({
  productId: undefined as number | undefined,
  saleStatus: '' as '' | 'UNSOLD' | 'SOLD',
  enableStatus: '' as '' | 'ENABLED' | 'DISABLED',
  usedStatus: '' as '' | AccountUsedStatus,
  keyword: '',
})

const createForm = reactive({
  productId: undefined as number | undefined,
  rawText: '',
})

const currentProductSelected = computed(() => typeof filters.productId === 'number')

function saleStatusLabel(status: string) {
  return status === 'SOLD' ? '已售出' : '未售出'
}

function enableStatusLabel(status: string) {
  return status === 'ENABLED' ? '启用' : '停用'
}

function usedStatusLabel(status: string) {
  return status === 'USED' ? '已使用' : '未使用'
}

function parseBatchInput(rawText: string) {
  return rawText
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => {
      const [firstPart = '', ...noteParts] = line.split('----')
      return {
        cardKey: firstPart.trim(),
        note: noteParts.join('----').trim(),
      }
    })
    .filter((item) => item.cardKey)
}

async function loadProducts() {
  await loadProductsByKeyword()
}

function syncProductOptions(items: ProductOptionRecord[]) {
  for (const item of items) {
    productCache.set(item.id, item)
  }

  const selectedIds = [filters.productId, createForm.productId].filter((id): id is number => typeof id === 'number')
  const merged = new Map<number, ProductOptionRecord>()
  for (const item of items) {
    merged.set(item.id, item)
  }
  for (const id of selectedIds) {
    const cached = productCache.get(id)
    if (cached) {
      merged.set(id, cached)
    }
  }
  products.value = Array.from(merged.values())
}

async function loadProductsByKeyword(keyword = '') {
  productsLoading.value = true
  try {
    const result = await searchProductOptions(keyword || undefined, PRODUCT_OPTION_PAGE_SIZE)
    syncProductOptions(result)
  } finally {
    productsLoading.value = false
  }
}

function clearProductSearchTimer() {
  if (productSearchTimer !== null) {
    window.clearTimeout(productSearchTimer)
    productSearchTimer = null
  }
}

function handleProductSearch(keyword: string) {
  clearProductSearchTimer()
  productSearchTimer = window.setTimeout(() => {
    productSearchTimer = null
    void loadProductsByKeyword(keyword.trim())
  }, PRODUCT_SEARCH_DELAY)
}

async function loadAccounts(page = 1) {
  loading.value = true
  try {
    const result = await getAccountPage({
      size: PAGE_SIZE,
      cursor: pager.getCursor(page),
      productId: filters.productId,
      saleStatus: filters.saleStatus || undefined,
      enableStatus: filters.enableStatus || undefined,
      usedStatus: filters.usedStatus || undefined,
      keyword: filters.keyword || undefined,
    })
    accounts.value = result.items
    pager.commit(page, result.nextCursor, result.hasMore)
  } finally {
    loading.value = false
  }
}

function resetAndLoad() {
  pager.reset()
  void loadAccounts(1)
}

function goPrevPage() {
  if (!pager.canPrev.value) {
    return
  }
  void loadAccounts(pager.currentPage.value - 1)
}

function goNextPage() {
  if (!pager.canNext.value) {
    return
  }
  void loadAccounts(pager.currentPage.value + 1)
}

function resetFilters() {
  filters.productId = undefined
  filters.saleStatus = ''
  filters.enableStatus = ''
  filters.usedStatus = ''
  filters.keyword = ''
  resetAndLoad()
}

function openCreate() {
  createForm.productId = filters.productId
  createForm.rawText = ''
  createVisible.value = true
}

async function submitCreate() {
  if (!createForm.productId) {
    ElMessage.warning('请先选择商品')
    return
  }

  const items = parseBatchInput(createForm.rawText)
  if (!items.length) {
    ElMessage.warning('请至少输入一条卡密')
    return
  }

  creating.value = true
  try {
    await createAccounts({
      productId: createForm.productId,
      items,
    })
    ElMessage.success(`成功导入 ${items.length} 条卡密`)
    createVisible.value = false
    resetAndLoad()
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '导入卡密失败')
  } finally {
    creating.value = false
  }
}

async function openDetail(row: AccountRecord) {
  detail.value = await getAccountDetail(row.id)
  detailVisible.value = true
}

async function toggleEnable(row: AccountRecord) {
  const nextStatus = row.enableStatus === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  try {
    await updateAccountStatus(row.id, nextStatus)
    ElMessage.success(nextStatus === 'ENABLED' ? '卡密已启用' : '卡密已停用')
    if (detail.value?.id === row.id) {
      detail.value = await getAccountDetail(row.id)
    }
    resetAndLoad()
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '更新卡密状态失败')
  }
}

async function toggleUsed(row: AccountRecord) {
  const nextStatus: AccountUsedStatus = row.usedStatus === 'USED' ? 'UNUSED' : 'USED'
  try {
    await updateAccountUsedStatus(row.id, nextStatus)
    ElMessage.success(nextStatus === 'USED' ? '卡密已标记为已使用' : '卡密已标记为未使用')
    if (detail.value?.id === row.id) {
      detail.value = await getAccountDetail(row.id)
    }
    resetAndLoad()
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '更新使用状态失败')
  }
}

async function removeAccount(row: AccountRecord) {
  try {
    await ElMessageBox.confirm(`确定删除卡密“${row.cardKey}”吗？已售卡密后端会拒绝删除。`, '删除卡密', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
    })
    await deleteAccount(row.id)
    ElMessage.success('卡密已删除')
    if (detail.value?.id === row.id) {
      detailVisible.value = false
      detail.value = null
    }
    resetAndLoad()
  } catch (error: any) {
    if (error === 'cancel' || error === 'close') {
      return
    }
    ElMessage.error(error?.response?.data?.message ?? '删除卡密失败')
  }
}

async function handleBulkEnable(scope: 'PRODUCT' | 'ALL') {
  try {
    await bulkEnableAccounts(scope, scope === 'PRODUCT' ? filters.productId : undefined)
    ElMessage.success(scope === 'PRODUCT' ? '当前商品卡密已批量启用' : '全站卡密已批量启用')
    resetAndLoad()
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '批量启用失败')
  }
}

async function handleBulkDisable(scope: 'PRODUCT' | 'ALL') {
  try {
    await bulkDisableAccounts(scope, scope === 'PRODUCT' ? filters.productId : undefined)
    ElMessage.success(scope === 'PRODUCT' ? '当前商品卡密已批量停用' : '全站卡密已批量停用')
    resetAndLoad()
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '批量停用失败')
  }
}

onMounted(async () => {
  await loadProducts()
  pager.reset()
  await loadAccounts(1)
})

onBeforeUnmount(clearProductSearchTimer)
</script>

<template>
  <div class="admin-page">
    <el-card class="page-card" shadow="never">
      <div class="page-header">
        <div>
          <p>卡密池现在固定每页 10 条展示，翻页仍然走游标分页，避免记录越多越慢。这里专门管理卡密资源本身，完整订单信息请到订单管理查看。</p>
          <h1>卡密池管理</h1>
        </div>
        <el-button type="primary" @click="openCreate">导入卡密</el-button>
      </div>

      <div class="toolbar toolbar--wrap">
        <el-select
          v-model="filters.productId"
          clearable
          filterable
          remote
          reserve-keyword
          :loading="productsLoading"
          placeholder="搜索商品"
          :remote-method="handleProductSearch"
        >
          <el-option v-for="item in products" :key="item.id" :label="item.title" :value="item.id" />
        </el-select>
        <el-select v-model="filters.saleStatus" clearable placeholder="按销售状态筛选">
          <el-option label="未售出" value="UNSOLD" />
          <el-option label="已售出" value="SOLD" />
        </el-select>
        <el-select v-model="filters.enableStatus" clearable placeholder="按启用状态筛选">
          <el-option label="启用" value="ENABLED" />
          <el-option label="停用" value="DISABLED" />
        </el-select>
        <el-select v-model="filters.usedStatus" clearable placeholder="按使用状态筛选">
          <el-option label="未使用" value="UNUSED" />
          <el-option label="已使用" value="USED" />
        </el-select>
        <el-input v-model="filters.keyword" clearable placeholder="搜索商品、卡密或订单号前缀" @keyup.enter="resetAndLoad" />
        <el-button type="primary" @click="resetAndLoad">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>

      <div class="toolbar toolbar--actions">
        <el-button :disabled="!currentProductSelected" @click="handleBulkEnable('PRODUCT')">启用当前商品</el-button>
        <el-button :disabled="!currentProductSelected" @click="handleBulkDisable('PRODUCT')">停用当前商品</el-button>
        <el-button @click="handleBulkEnable('ALL')">启用全站卡密</el-button>
        <el-button type="warning" @click="handleBulkDisable('ALL')">停用全站卡密</el-button>
      </div>

      <el-table :data="accounts" v-loading="loading" border>
        <el-table-column prop="productTitle" label="商品" min-width="200" />
        <el-table-column prop="cardKey" label="卡密" min-width="220" show-overflow-tooltip />
        <el-table-column label="销售状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.saleStatus === 'SOLD' ? 'warning' : 'success'" effect="plain">
              {{ saleStatusLabel(row.saleStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="启用状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.enableStatus === 'ENABLED' ? 'success' : 'info'" effect="plain">
              {{ enableStatusLabel(row.enableStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="使用状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.usedStatus === 'USED' ? 'warning' : 'success'" effect="plain">
              {{ usedStatusLabel(row.usedStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="assignedOrderNo" label="归属订单号" min-width="180">
          <template #default="{ row }">{{ row.assignedOrderNo || '-' }}</template>
        </el-table-column>
        <el-table-column prop="assignedAt" label="分配时间" min-width="180">
          <template #default="{ row }">{{ row.assignedAt || '-' }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" min-width="180" />
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link :type="row.enableStatus === 'ENABLED' ? 'warning' : 'success'" @click="toggleEnable(row)">
              {{ row.enableStatus === 'ENABLED' ? '停用' : '启用' }}
            </el-button>
            <el-button v-if="row.saleStatus === 'SOLD'" link type="success" @click="toggleUsed(row)">
              {{ row.usedStatus === 'USED' ? '标记未使用' : '标记已使用' }}
            </el-button>
            <el-button link type="danger" @click="removeAccount(row)">删除</el-button>
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

    <el-dialog v-model="createVisible" width="min(760px, 94vw)" title="批量导入卡密">
      <el-form label-position="top">
        <el-form-item label="所属商品">
          <el-select
            v-model="createForm.productId"
            filterable
            remote
            reserve-keyword
            :loading="productsLoading"
            placeholder="请选择商品"
            :remote-method="handleProductSearch"
          >
            <el-option v-for="item in products" :key="item.id" :label="item.title" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="卡密内容">
          <el-input
            v-model="createForm.rawText"
            type="textarea"
            :rows="12"
            placeholder="每行一条卡密，可选格式：卡密----备注"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">导入</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" size="min(720px, 92vw)" title="卡密详情">
      <template v-if="detail">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="商品">{{ detail.productTitle }}</el-descriptions-item>
          <el-descriptions-item label="卡密">{{ detail.cardKey }}</el-descriptions-item>
          <el-descriptions-item label="销售状态">{{ saleStatusLabel(detail.saleStatus) }}</el-descriptions-item>
          <el-descriptions-item label="启用状态">{{ enableStatusLabel(detail.enableStatus) }}</el-descriptions-item>
          <el-descriptions-item label="使用状态">{{ usedStatusLabel(detail.usedStatus) }}</el-descriptions-item>
          <el-descriptions-item label="归属订单号">{{ detail.assignedOrderNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="分配时间">{{ detail.assignedAt || '-' }}</el-descriptions-item>
          <el-descriptions-item label="备注">{{ detail.note || '-' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ detail.createdAt }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ detail.updatedAt || '-' }}</el-descriptions-item>
        </el-descriptions>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.toolbar--wrap {
  flex-wrap: wrap;
}

.toolbar--actions {
  justify-content: flex-start;
  margin-top: -4px;
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