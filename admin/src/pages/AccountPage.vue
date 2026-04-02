<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  bulkDisableAccounts,
  bulkEnableAccounts,
  createAccounts,
  deleteAccount,
  getAccounts,
  updateAccountStatus,
  type AccountRecord,
} from '@/api/accounts'
import { getProducts, type ProductRecord } from '@/api/products'

const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const products = ref<ProductRecord[]>([])
const accounts = ref<AccountRecord[]>([])

const filters = reactive({
  productId: undefined as number | undefined,
  saleStatus: '',
  enableStatus: '',
})

const form = reactive({
  productId: undefined as number | undefined,
  batchText: '',
})

async function loadBaseData() {
  products.value = await getProducts()
  await loadAccounts()
}

async function loadAccounts() {
  loading.value = true
  try {
    accounts.value = await getAccounts(filters.productId, filters.saleStatus, filters.enableStatus)
  } finally {
    loading.value = false
  }
}

function openDialog() {
  form.productId = filters.productId
  form.batchText = ''
  dialogVisible.value = true
}

function parseBatchInput() {
  const items = form.batchText
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => {
      const [cardKey, note = ''] = line.split('----')
      if (!cardKey) {
        throw new Error('每行至少需要一条卡密')
      }
      return {
        cardKey: cardKey.trim(),
        note: note.trim(),
      }
    })

  if (!items.length) {
    throw new Error('请至少填写一行卡密')
  }
  return items
}

async function submitAccounts() {
  if (!form.productId) {
    ElMessage.warning('请先选择商品')
    return
  }
  saving.value = true
  try {
    const items = parseBatchInput()
    await createAccounts({ productId: form.productId, items })
    ElMessage.success('卡密导入成功')
    dialogVisible.value = false
    await loadAccounts()
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? error?.message ?? '卡密导入失败')
  } finally {
    saving.value = false
  }
}

async function switchStatus(row: AccountRecord) {
  const nextStatus = row.enableStatus === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  try {
    await updateAccountStatus(row.id, nextStatus)
    ElMessage.success(nextStatus === 'ENABLED' ? '卡密已启用' : '卡密已停用')
    await loadAccounts()
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '卡密状态修改失败')
  }
}

async function runBulk(scope: 'PRODUCT' | 'ALL', enable: boolean) {
  if (scope === 'PRODUCT' && !filters.productId) {
    ElMessage.warning('请先选择商品，再执行当前商品批量操作')
    return
  }

  const actionLabel = enable ? '启用' : '停用'
  const scopeLabel = scope === 'PRODUCT' ? '当前商品全部卡密' : '全站全部卡密'
  try {
    await ElMessageBox.confirm(`确定${actionLabel}${scopeLabel}吗？`, `${actionLabel}卡密`, {
      type: 'warning',
      confirmButtonText: `确认${actionLabel}`,
      cancelButtonText: '取消',
    })
    const updated = enable
      ? await bulkEnableAccounts(scope, filters.productId)
      : await bulkDisableAccounts(scope, filters.productId)
    ElMessage.success(`已${actionLabel}${updated}条卡密`)
    await loadAccounts()
  } catch (error: any) {
    if (error === 'cancel' || error === 'close') {
      return
    }
    ElMessage.error(error?.response?.data?.message ?? `${actionLabel}失败`)
  }
}

async function removeAccount(row: AccountRecord) {
  try {
    await ElMessageBox.confirm(
      `确定删除卡密“${row.cardKey}”吗？已售卡密会被后端拒绝删除。`,
      '删除卡密',
      {
        type: 'warning',
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
      },
    )
    await deleteAccount(row.id)
    ElMessage.success('卡密已删除')
    await loadAccounts()
  } catch (error: any) {
    if (error === 'cancel' || error === 'close') {
      return
    }
    ElMessage.error(error?.response?.data?.message ?? '卡密删除失败')
  }
}

function saleStatusLabel(status: string) {
  return status === 'SOLD' ? '已售' : '未售'
}

function enableStatusLabel(status: string) {
  return status === 'ENABLED' ? '启用' : '停用'
}

onMounted(loadBaseData)
</script>

<template>
  <div class="admin-page">
    <el-card class="page-card" shadow="never">
      <div class="page-header">
        <div>
          <p>只有“未售 + 启用”的卡密会参与库存统计。管理员可以单条删除、单条启停，也可以按商品或全站批量启停。</p>
          <h1>卡密池管理</h1>
        </div>
        <div class="page-header__actions">
          <el-button @click="runBulk('PRODUCT', true)">当前商品全部启用</el-button>
          <el-button @click="runBulk('PRODUCT', false)">当前商品全部停用</el-button>
          <el-button type="success" plain @click="runBulk('ALL', true)">全站全部启用</el-button>
          <el-button type="danger" plain @click="runBulk('ALL', false)">全站全部停用</el-button>
          <el-button type="primary" @click="openDialog">批量导入卡密</el-button>
        </div>
      </div>

      <div class="toolbar toolbar-grid">
        <el-select v-model="filters.productId" clearable placeholder="按商品筛选">
          <el-option v-for="item in products" :key="item.id" :label="item.title" :value="item.id" />
        </el-select>
        <el-select v-model="filters.saleStatus" clearable placeholder="按售卖状态筛选">
          <el-option label="未售" value="UNSOLD" />
          <el-option label="已售" value="SOLD" />
        </el-select>
        <el-select v-model="filters.enableStatus" clearable placeholder="按启用状态筛选">
          <el-option label="启用" value="ENABLED" />
          <el-option label="停用" value="DISABLED" />
        </el-select>
        <el-button @click="loadAccounts">刷新</el-button>
      </div>

      <el-table :data="accounts" v-loading="loading" border>
        <el-table-column prop="productTitle" label="所属商品" min-width="220" />
        <el-table-column prop="cardKey" label="卡密" min-width="260" />
        <el-table-column label="售卖状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.saleStatus === 'SOLD' ? 'warning' : 'success'">
              {{ saleStatusLabel(row.saleStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="启用状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.enableStatus === 'ENABLED' ? 'success' : 'info'">
              {{ enableStatusLabel(row.enableStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="assignedOrderId" label="绑定订单" width="130" />
        <el-table-column prop="createdAt" label="创建时间" min-width="180" />
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button
              link
              :type="row.enableStatus === 'ENABLED' ? 'warning' : 'success'"
              @click="switchStatus(row)"
            >
              {{ row.enableStatus === 'ENABLED' ? '停用' : '启用' }}
            </el-button>
            <el-button link type="danger" @click="removeAccount(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" width="min(760px, 94vw)" title="批量导入卡密">
      <el-form label-position="top">
        <el-form-item label="所属商品">
          <el-select v-model="form.productId" placeholder="选择商品">
            <el-option v-for="item in products" :key="item.id" :label="item.title" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="批量内容">
          <el-input
            v-model="form.batchText"
            type="textarea"
            :rows="10"
            placeholder="每行一条卡密，可选格式：卡密----备注"
          />
        </el-form-item>
        <p class="muted">示例：CHATGPT-PLUS-CARD-001----首批卡密</p>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitAccounts">导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-header__actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.toolbar-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

@media (max-width: 1080px) {
  .toolbar-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 680px) {
  .toolbar-grid {
    grid-template-columns: 1fr;
  }
}
</style>