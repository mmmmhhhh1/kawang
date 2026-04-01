<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowRight, Box, ChatDotRound, GoodsFilled, ShoppingCartFull } from '@element-plus/icons-vue'
import { createOrder, getProduct, getProducts, type OrderResult, type Product } from '@/api/shop'

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const products = ref<Product[]>([])
const selectedProduct = ref<Product | null>(null)
const orderResult = ref<OrderResult | null>(null)

const form = reactive({
  buyerName: '',
  buyerContact: '',
  remark: '',
  quantity: 1,
})

const stats = computed(() => {
  const totalStock = products.value.reduce((sum, item) => sum + item.availableStock, 0)
  const totalSold = products.value.reduce((sum, item) => sum + item.soldCount, 0)
  return {
    totalProducts: products.value.length,
    totalStock,
    totalSold,
  }
})

async function fetchProducts() {
  loading.value = true
  try {
    products.value = await getProducts()
  } finally {
    loading.value = false
  }
}

function scrollToProducts() {
  document.querySelector('.product-grid')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

async function openOrderDialog(productId: number) {
  selectedProduct.value = await getProduct(productId)
  form.quantity = 1
  form.buyerName = ''
  form.buyerContact = ''
  form.remark = ''
  orderResult.value = null
  dialogVisible.value = true
}

async function submitOrder() {
  if (!selectedProduct.value) {
    return
  }
  if (!form.buyerName.trim() || !form.buyerContact.trim()) {
    ElMessage.warning('请先填写姓名和联系方式')
    return
  }
  submitting.value = true
  try {
    orderResult.value = await createOrder({
      productId: selectedProduct.value.id,
      quantity: form.quantity,
      buyerName: form.buyerName.trim(),
      buyerContact: form.buyerContact.trim(),
      remark: form.remark.trim(),
    })
    ElMessage.success('订单创建成功')
    await fetchProducts()
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '下单失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}

onMounted(fetchProducts)
</script>

<template>
  <div class="shop-shell">
    <section class="hero-panel">
      <div class="hero-copy">
        <p class="eyebrow">KAWANG AI MARKET</p>
        <h1>AI 会员商城</h1>
        <p class="hero-desc">
          只做高频 AI 服务账号，前台直接浏览价格、库存与销量，下单后由后端模拟履约并锁定账号池库存。
        </p>
        <div class="hero-actions">
          <el-button type="primary" size="large" @click="scrollToProducts">
            立即选购
            <el-icon class="el-icon--right"><ArrowRight /></el-icon>
          </el-button>
          <a class="admin-link" href="http://localhost:5174" target="_blank" rel="noreferrer">
            进入后台管理
          </a>
        </div>
      </div>

      <div class="hero-stats">
        <div class="stat-card">
          <el-icon><GoodsFilled /></el-icon>
          <div>
            <strong>{{ stats.totalProducts }}</strong>
            <span>在售商品</span>
          </div>
        </div>
        <div class="stat-card">
          <el-icon><Box /></el-icon>
          <div>
            <strong>{{ stats.totalStock }}</strong>
            <span>可用库存</span>
          </div>
        </div>
        <div class="stat-card">
          <el-icon><ShoppingCartFull /></el-icon>
          <div>
            <strong>{{ stats.totalSold }}</strong>
            <span>累计已售</span>
          </div>
        </div>
      </div>
    </section>

    <section class="board">
      <div class="board-head">
        <div>
          <p class="eyebrow">LIVE INVENTORY</p>
          <h2>商品列表</h2>
        </div>
        <el-tag type="info" effect="plain">后端真实库存联动</el-tag>
      </div>

      <el-skeleton :loading="loading" animated :rows="6">
        <div class="product-grid">
          <el-card v-for="item in products" :key="item.id" shadow="hover" class="product-card">
            <template #header>
              <div class="product-card__header">
                <div>
                  <span class="vendor">{{ item.vendor }}</span>
                  <h3>{{ item.title }}</h3>
                </div>
                <el-tag type="success">{{ item.planName }}</el-tag>
              </div>
            </template>

            <p class="description">{{ item.description }}</p>

            <div class="metrics">
              <div>
                <span>价格</span>
                <strong>¥{{ item.price.toFixed(2) }}</strong>
              </div>
              <div>
                <span>已售</span>
                <strong>{{ item.soldCount }}</strong>
              </div>
              <div>
                <span>库存</span>
                <strong>{{ item.availableStock }}</strong>
              </div>
            </div>

            <el-button
              class="buy-button"
              type="primary"
              :disabled="item.availableStock === 0"
              @click="openOrderDialog(item.id)"
            >
              {{ item.availableStock === 0 ? '暂时缺货' : '立即下单' }}
            </el-button>
          </el-card>
        </div>
      </el-skeleton>
    </section>

    <section class="notice-board">
      <div class="notice-card">
        <el-icon><ChatDotRound /></el-icon>
        <div>
          <h3>安全说明</h3>
          <p>前台不返回真实账号密码，下单仅展示订单结果；账号池仅在后端加密保存并由管理员后台维护。</p>
        </div>
      </div>
    </section>

    <el-dialog
      v-model="dialogVisible"
      width="min(720px, 92vw)"
      :title="selectedProduct ? `${selectedProduct.title} 下单` : '下单'"
    >
      <div v-if="selectedProduct" class="order-layout">
        <div class="order-summary">
          <h3>{{ selectedProduct.title }}</h3>
          <p>{{ selectedProduct.vendor }} / {{ selectedProduct.planName }}</p>
          <div class="summary-row">
            <span>单价</span>
            <strong>¥{{ selectedProduct.price.toFixed(2) }}</strong>
          </div>
          <div class="summary-row">
            <span>可用库存</span>
            <strong>{{ selectedProduct.availableStock }}</strong>
          </div>
          <div class="summary-row">
            <span>已售数量</span>
            <strong>{{ selectedProduct.soldCount }}</strong>
          </div>
        </div>

        <div class="order-form">
          <el-form label-position="top">
            <el-form-item label="买家姓名">
              <el-input v-model="form.buyerName" maxlength="32" placeholder="填写收货备注名" />
            </el-form-item>
            <el-form-item label="联系方式">
              <el-input v-model="form.buyerContact" maxlength="64" placeholder="微信 / QQ / 手机号 / 邮箱" />
            </el-form-item>
            <el-form-item label="购买数量">
              <el-input-number
                v-model="form.quantity"
                :min="1"
                :max="Math.max(1, selectedProduct.availableStock)"
                controls-position="right"
              />
            </el-form-item>
            <el-form-item label="备注">
              <el-input
                v-model="form.remark"
                type="textarea"
                maxlength="200"
                show-word-limit
                placeholder="比如指定发货时间、偏好说明等"
              />
            </el-form-item>
          </el-form>

          <div class="total-row">
            <span>合计金额</span>
            <strong>¥{{ (selectedProduct.price * form.quantity).toFixed(2) }}</strong>
          </div>

          <el-alert
            v-if="orderResult"
            type="success"
            :closable="false"
            show-icon
            :title="`订单 ${orderResult.orderNo} 已创建`"
            :description="`状态：${orderResult.status}，数量：${orderResult.quantity}，金额：¥${orderResult.totalAmount.toFixed(2)}`"
          />
        </div>
      </div>

      <template #footer>
        <el-button @click="dialogVisible = false">关闭</el-button>
        <el-button type="primary" :loading="submitting" @click="submitOrder">提交订单</el-button>
      </template>
    </el-dialog>
  </div>
</template>
