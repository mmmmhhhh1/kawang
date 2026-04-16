<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminProfileState, hasAdminPermission } from '@/api/auth'
import {
  createProduct,
  deleteProduct,
  getProductPage,
  updateProduct,
  updateProductStatus,
  type ProductPayload,
  type ProductRecord,
} from '@/api/products'
import { useCursorPager } from '@/utils/cursorPager'

const PAGE_SIZE = 10

const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const products = ref<ProductRecord[]>([])

const filters = reactive({
  keyword: '',
  status: '' as '' | 'ACTIVE' | 'INACTIVE',
})

const form = reactive<ProductPayload>({
  sku: '',
  title: '',
  vendor: '',
  planName: '',
  description: '',
  price: 99,
  status: 'ACTIVE',
  sortOrder: 10,
})

const pager = useCursorPager()
const canDeleteProduct = computed(() => hasAdminPermission('DELETE_PRODUCT', adminProfileState.value))

async function loadProducts(page = 1) {
  loading.value = true
  try {
    const result = await getProductPage({
      size: PAGE_SIZE,
      cursor: pager.getCursor(page),
      keyword: filters.keyword || undefined,
      status: filters.status || undefined,
    })
    products.value = result.items
    pager.commit(page, result.nextCursor, result.hasMore)
  } finally {
    loading.value = false
  }
}

function resetAndLoad() {
  pager.reset()
  void loadProducts(1)
}

function goPrevPage() {
  if (!pager.canPrev.value) {
    return
  }
  void loadProducts(pager.currentPage.value - 1)
}

function goNextPage() {
  if (!pager.canNext.value) {
    return
  }
  void loadProducts(pager.currentPage.value + 1)
}

function resetFilters() {
  filters.keyword = ''
  filters.status = ''
  resetAndLoad()
}

function resetForm() {
  form.sku = ''
  form.title = ''
  form.vendor = ''
  form.planName = ''
  form.description = ''
  form.price = 99
  form.status = 'ACTIVE'
  form.sortOrder = 10
}

function openCreate() {
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: ProductRecord) {
  editingId.value = row.id
  form.sku = row.sku
  form.title = row.title
  form.vendor = row.vendor
  form.planName = row.planName
  form.description = row.description
  form.price = row.price
  form.status = row.status
  form.sortOrder = row.sortOrder
  dialogVisible.value = true
}

async function submit() {
  saving.value = true
  try {
    if (editingId.value) {
      await updateProduct(editingId.value, form)
      ElMessage.success('商品更新成功')
    } else {
      await createProduct(form)
      ElMessage.success('商品创建成功')
    }
    dialogVisible.value = false
    resetAndLoad()
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '商品保存失败')
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row: ProductRecord) {
  const nextStatus = row.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
  try {
    await updateProductStatus(row.id, nextStatus)
    ElMessage.success(nextStatus === 'ACTIVE' ? '商品已上架' : '商品已下架')
    resetAndLoad()
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '状态更新失败')
  }
}

async function removeProduct(row: ProductRecord) {
  if (!canDeleteProduct.value) {
    return
  }

  try {
    await ElMessageBox.confirm(
      `确定删除商品“${row.title}”吗？如果该商品已有历史订单，后端会拒绝删除。`,
      '删除商品',
      {
        type: 'warning',
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
      },
    )
    await deleteProduct(row.id)
    ElMessage.success('商品已删除')
    resetAndLoad()
  } catch (error: any) {
    if (error === 'cancel' || error === 'close') {
      return
    }
    ElMessage.error(error?.response?.data?.message ?? '商品删除失败')
  }
}

onMounted(() => {
  resetAndLoad()
})
</script>

<template>
  <div class="admin-page">
    <el-card class="page-card" shadow="never">
      <div class="page-header">
        <div>
          <p>商品列表现在按固定每页 10 条展示，底层仍然是高性能游标分页，不会因为翻得很深而越来越慢。</p>
          <h1>商品管理</h1>
        </div>
        <el-button type="primary" @click="openCreate">新建商品</el-button>
      </div>

      <div class="toolbar">
        <el-input v-model="filters.keyword" clearable placeholder="搜索商品名称、SKU、品牌或套餐" @keyup.enter="resetAndLoad" />
        <el-select v-model="filters.status" clearable placeholder="按状态筛选">
          <el-option label="上架中" value="ACTIVE" />
          <el-option label="已下架" value="INACTIVE" />
        </el-select>
        <el-button type="primary" @click="resetAndLoad">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>

      <el-table :data="products" v-loading="loading" border>
        <el-table-column prop="title" label="商品名称" min-width="220" />
        <el-table-column prop="sku" label="SKU" min-width="140" />
        <el-table-column prop="vendor" label="厂商" width="120" />
        <el-table-column prop="planName" label="套餐" width="140" />
        <el-table-column label="价格" width="120">
          <template #default="{ row }">¥{{ Number(row.price).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="availableStock" label="库存" width="90" />
        <el-table-column prop="soldCount" label="已售" width="90" />
        <el-table-column prop="sortOrder" label="排序" width="90" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">
              {{ row.status === 'ACTIVE' ? '上架中' : '已下架' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link :type="row.status === 'ACTIVE' ? 'warning' : 'success'" @click="toggleStatus(row)">
              {{ row.status === 'ACTIVE' ? '下架' : '上架' }}
            </el-button>
            <el-button v-if="canDeleteProduct" link type="danger" @click="removeProduct(row)">删除</el-button>
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

    <el-dialog v-model="dialogVisible" width="min(760px, 94vw)" :title="editingId ? '编辑商品' : '新建商品'">
      <el-form label-position="top">
        <div class="product-grid">
          <el-form-item label="SKU">
            <el-input v-model="form.sku" />
          </el-form-item>
          <el-form-item label="商品名称">
            <el-input v-model="form.title" />
          </el-form-item>
          <el-form-item label="厂商">
            <el-input v-model="form.vendor" />
          </el-form-item>
          <el-form-item label="套餐名称">
            <el-input v-model="form.planName" />
          </el-form-item>
          <el-form-item label="售价">
            <el-input-number v-model="form.price" :min="0.01" :step="1" :precision="2" />
          </el-form-item>
          <el-form-item label="排序">
            <el-input-number v-model="form.sortOrder" :min="0" :max="9999" />
          </el-form-item>
        </div>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio-button value="ACTIVE">上架</el-radio-button>
            <el-radio-button value="INACTIVE">下架</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="商品描述">
          <el-input v-model="form.description" type="textarea" :rows="4" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.product-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
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
  .product-grid {
    grid-template-columns: 1fr;
  }

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