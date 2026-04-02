<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { closeOrder, getOrderDetail, getOrders, type OrderDetail, type OrderRecord } from '@/api/orders'
import { getProducts, type ProductRecord } from '@/api/products'

const loading = ref(false)
const detailVisible = ref(false)
const products = ref<ProductRecord[]>([])
const orders = ref<OrderRecord[]>([])
const total = ref(0)
const detail = ref<OrderDetail | null>(null)

const query = reactive({
  page: 1,
  size: 10,
  status: '',
  productId: undefined as number | undefined,
  keyword: '',
})

function formatMoney(value: number) {
  return `¥${value.toFixed(2)}`
}

async function loadProducts() {
  products.value = await getProducts()
}

async function loadOrders() {
  loading.value = true
  try {
    const result = await getOrders({
      page: query.page,
      size: query.size,
      status: query.status || undefined,
      productId: query.productId,
      keyword: query.keyword || undefined,
    })
    orders.value = result.items
    total.value = result.total
  } finally {
    loading.value = false
  }
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
    await loadOrders()
  } catch (error: any) {
    if (error === 'cancel') {
      return
    }
    ElMessage.error(error?.response?.data?.message ?? '关闭订单失败')
  }
}

onMounted(async () => {
  await loadProducts()
  await loadOrders()
})
</script>

<template>
  <div class="admin-page">
    <el-card class="page-card" shadow="never">
      <div class="page-header">
        <div>
          <p>订单创建后默认成功。关闭订单会释放已分配卡密并回滚商品库存与销量。</p>
          <h1>订单管理</h1>
        </div>
      </div>

      <div class="toolbar">
        <el-select v-model="query.productId" clearable placeholder="按商品筛选">
          <el-option v-for="item in products" :key="item.id" :label="item.title" :value="item.id" />
        </el-select>
        <el-select v-model="query.status" clearable placeholder="按状态筛选">
          <el-option label="成功" value="SUCCESS" />
          <el-option label="已关闭" value="CLOSED" />
        </el-select>
        <el-input v-model="query.keyword" placeholder="搜索订单号或联系方式" clearable />
        <el-button type="primary" @click="query.page = 1; loadOrders()">查询</el-button>
      </div>

      <el-table :data="orders" v-loading="loading" border>
        <el-table-column prop="orderNo" label="订单号" min-width="180" />
        <el-table-column prop="productTitleSnapshot" label="商品" min-width="220" />
        <el-table-column prop="buyerName" label="买家" width="110" />
        <el-table-column prop="buyerContact" label="联系方式" min-width="180" />
        <el-table-column prop="quantity" label="数量" width="80" />
        <el-table-column label="金额" width="120">
          <template #default="{ row }">{{ formatMoney(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === 'SUCCESS' ? 'success' : 'info'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" min-width="180" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row.id)">详情</el-button>
            <el-button v-if="row.status === 'SUCCESS'" link type="warning" @click="handleClose(row)">关闭</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div style="display: flex; justify-content: flex-end; margin-top: 18px;">
        <el-pagination
          background
          layout="prev, pager, next, total"
          :current-page="query.page"
          :page-size="query.size"
          :total="total"
          @current-change="(page: number) => { query.page = page; loadOrders() }"
        />
      </div>
    </el-card>

    <el-drawer v-model="detailVisible" size="min(680px, 92vw)" title="订单详情">
      <template v-if="detail">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="订单号">{{ detail.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="商品">{{ detail.productTitleSnapshot }}</el-descriptions-item>
          <el-descriptions-item label="买家">{{ detail.buyerName }}</el-descriptions-item>
          <el-descriptions-item label="联系方式">{{ detail.buyerContact }}</el-descriptions-item>
          <el-descriptions-item label="数量">{{ detail.quantity }}</el-descriptions-item>
          <el-descriptions-item label="金额">{{ formatMoney(detail.totalAmount) }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ detail.status }}</el-descriptions-item>
          <el-descriptions-item label="备注">{{ detail.buyerRemark || '-' }}</el-descriptions-item>
          <el-descriptions-item label="关闭原因">{{ detail.closedReason || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div style="margin-top: 20px;">
          <h3>已分配卡密</h3>
          <div style="display: flex; gap: 8px; flex-wrap: wrap;">
            <el-tag v-for="item in detail.cardKeys" :key="item" type="info" effect="plain">{{ item }}</el-tag>
          </div>
        </div>
      </template>
    </el-drawer>
  </div>
</template>