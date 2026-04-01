<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createAccounts, getAccounts, updateAccountStatus, type AccountRecord } from '@/api/accounts'
import { getProducts, type ProductRecord } from '@/api/products'

const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const products = ref<ProductRecord[]>([])
const accounts = ref<AccountRecord[]>([])

const filters = reactive({
  productId: undefined as number | undefined,
  status: '',
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
    accounts.value = await getAccounts(filters.productId, filters.status)
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
      const [accountName, secret, note = ''] = line.split('----')
      if (!accountName || !secret) {
        throw new Error('每行必须使用 账号----密码----备注 格式，备注可省略')
      }
      return {
        accountName: accountName.trim(),
        secret: secret.trim(),
        note: note.trim(),
      }
    })

  if (!items.length) {
    throw new Error('请至少填写一行账号')
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
    ElMessage.success('账号导入成功')
    dialogVisible.value = false
    await loadAccounts()
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? error?.message ?? '账号导入失败')
  } finally {
    saving.value = false
  }
}

async function switchStatus(row: AccountRecord) {
  const nextStatus = row.status === 'AVAILABLE' ? 'DISABLED' : 'AVAILABLE'
  try {
    await updateAccountStatus(row.id, nextStatus)
    ElMessage.success(nextStatus === 'AVAILABLE' ? '账号已启用' : '账号已禁用')
    await loadAccounts()
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '状态修改失败')
  }
}

onMounted(loadBaseData)
</script>

<template>
  <div class="admin-page">
    <el-card class="page-card" shadow="never">
      <div class="page-header">
        <div>
          <p>账号池负责真实库存。新增可用账号会同步增加库存，禁用可用账号会同步减少库存。</p>
          <h1>账号池管理</h1>
        </div>
        <el-button type="primary" @click="openDialog">批量导入账号</el-button>
      </div>

      <div class="toolbar">
        <el-select v-model="filters.productId" clearable placeholder="按商品筛选">
          <el-option v-for="item in products" :key="item.id" :label="item.title" :value="item.id" />
        </el-select>
        <el-select v-model="filters.status" clearable placeholder="按状态筛选">
          <el-option label="可用" value="AVAILABLE" />
          <el-option label="已分配" value="ASSIGNED" />
          <el-option label="已禁用" value="DISABLED" />
        </el-select>
        <el-button @click="loadAccounts">刷新</el-button>
      </div>

      <el-table :data="accounts" v-loading="loading" border>
        <el-table-column prop="productTitle" label="所属商品" min-width="200" />
        <el-table-column prop="accountNameMasked" label="账号脱敏值" width="180" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.status === 'AVAILABLE' ? 'success' : row.status === 'ASSIGNED' ? 'warning' : 'info'">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="assignedOrderId" label="绑定订单" width="120" />
        <el-table-column prop="createdAt" label="创建时间" min-width="180" />
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <el-button
              v-if="row.status !== 'ASSIGNED'"
              link
              :type="row.status === 'AVAILABLE' ? 'warning' : 'success'"
              @click="switchStatus(row)"
            >
              {{ row.status === 'AVAILABLE' ? '禁用' : '启用' }}
            </el-button>
            <span v-else class="muted">已随订单占用</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" width="min(760px, 94vw)" title="批量导入账号">
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
            placeholder="每行一个账号，格式：账号----密码----备注"
          />
        </el-form-item>
        <p class="muted">示例：chatgpt-001@example.com----Pass@123----首批账号</p>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitAccounts">导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>
