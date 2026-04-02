<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createProduct,
  deleteProduct,
  getProducts,
  updateProduct,
  updateProductStatus,
  type ProductPayload,
  type ProductRecord,
} from '@/api/products'

const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const products = ref<ProductRecord[]>([])

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

async function loadProducts() {
  loading.value = true
  try {
    products.value = await getProducts()
  } finally {
    loading.value = false
  }
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
    await loadProducts()
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
    await loadProducts()
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '状态更新失败')
  }
}

async function removeProduct(row: ProductRecord) {
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
    await loadProducts()
  } catch (error: any) {
    if (error === 'cancel' || error === 'close') {
      return
    }
    ElMessage.error(error?.response?.data?.message ?? '商品删除失败')
  }
}

onMounted(loadProducts)
</script>

<template>
  <div class="admin-page">
    <el-card class="page-card" shadow="never">
      <div class="page-header">
        <div>
          <p>维护商品基础信息、价格、上下架和排序。删除商品前，系统会校验该商品是否已有历史订单。</p>
          <h1>商品管理</h1>
        </div>
        <el-button type="primary" @click="openCreate">新建商品</el-button>
      </div>

      <el-table :data="products" v-loading="loading" border>
        <el-table-column prop="title" label="商品名称" min-width="220" />
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
            <el-button link type="danger" @click="removeProduct(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
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

@media (max-width: 768px) {
  .product-grid {
    grid-template-columns: 1fr;
  }
}
</style>