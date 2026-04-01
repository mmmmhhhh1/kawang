<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Bell, ShoppingBag } from '@element-plus/icons-vue'
import {
  createOrder,
  getNotices,
  getProduct,
  getProducts,
  type Notice,
  type OrderResult,
  type Product,
} from '@/api/shop'
import { memberProfileState } from '@/api/auth'
import { formatCurrency, formatDateTime, formatOrderStatus } from '@/utils/format'

const route = useRoute()
const profile = memberProfileState

const loading = ref(false)
const submitting = ref(false)
const noticeVisible = ref(false)
const purchaseVisible = ref(false)
const notices = ref<Notice[]>([])
const products = ref<Product[]>([])
const selectedNotice = ref<Notice | null>(null)
const selectedProduct = ref<Product | null>(null)
const orderResult = ref<OrderResult | null>(null)

const form = reactive({
  buyerName: '',
  buyerContact: '',
  quantity: 1,
  remark: '',
})

const keyword = computed(() => (typeof route.query.q === 'string' ? route.query.q.trim() : ''))

const filteredProducts = computed(() => {
  const q = keyword.value.toLowerCase()
  if (!q) {
    return products.value
  }
  return products.value.filter((item) =>
    [item.title, item.vendor, item.planName, item.sku].some((field) => field.toLowerCase().includes(q)),
  )
})

async function loadHomeData() {
  loading.value = true
  try {
    const [productData, noticeData] = await Promise.all([getProducts(), getNotices()])
    products.value = productData
    notices.value = noticeData
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '首页数据加载失败')
  } finally {
    loading.value = false
  }
}

function openNotice(notice: Notice) {
  selectedNotice.value = notice
  noticeVisible.value = true
}

async function openPurchase(product: Product) {
  try {
    selectedProduct.value = await getProduct(product.id)
    form.buyerName = profile.value?.username ?? ''
    form.buyerContact = ''
    form.quantity = 1
    form.remark = ''
    orderResult.value = null
    purchaseVisible.value = true
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '商品详情加载失败')
  }
}

async function submitOrder() {
  if (!selectedProduct.value) {
    return
  }
  if (!form.buyerName.trim() || !form.buyerContact.trim()) {
    ElMessage.warning('请填写买家姓名和联系方式')
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
    ElMessage.success('订单已创建')
    await loadHomeData()
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '下单失败，请稍后再试')
  } finally {
    submitting.value = false
  }
}

onMounted(loadHomeData)
</script>

<template>
  <div class="shell-body">
    <section class="section-shell public-panel">
      <div class="section-heading public-panel__heading">
        <div>
          <span class="section-kicker">公共</span>
          <h2>公告与购买说明</h2>
        </div>
        <p>这里只保留最直接的购买说明和公告，不堆叠多余信息。</p>
      </div>

      <el-skeleton :loading="loading" animated :rows="4">
        <div v-if="notices.length" class="public-list">
          <button
            v-for="notice in notices.slice(0, 3)"
            :key="notice.id"
            class="public-item"
            type="button"
            @click="openNotice(notice)"
          >
            <span class="public-item__icon">
              <el-icon><Bell /></el-icon>
            </span>
            <div class="public-item__content">
              <strong>{{ notice.title }}</strong>
              <p>{{ notice.summary }}</p>
            </div>
            <em>{{ formatDateTime(notice.publishedAt) }}</em>
          </button>
        </div>
        <el-empty v-else description="当前没有可展示的公告" />
      </el-skeleton>
    </section>

    <section class="section-shell product-panel">
      <div class="section-heading product-panel__heading">
        <div>
          <span class="section-kicker">购买</span>
          <h2>商品列表</h2>
        </div>
        <p v-if="keyword">当前关键词：{{ keyword }}</p>
        <p v-else>价格、库存、已售均来自真实后端接口。</p>
      </div>

      <el-skeleton :loading="loading" animated :rows="8">
        <div v-if="filteredProducts.length" class="product-grid">
          <article v-for="item in filteredProducts" :key="item.id" class="product-card">
            <div class="product-card__body">
              <div class="product-card__title-row">
                <div class="product-card__title-copy">
                  <span class="soft-chip">{{ item.vendor }}</span>
                  <h3>{{ item.title }}</h3>
                </div>
                <span class="product-card__plan">{{ item.planName }}</span>
              </div>

              <div class="product-card__price-row">
                <strong>{{ formatCurrency(item.price) }}</strong>
                <span class="product-card__sku">{{ item.sku }}</span>
              </div>

              <div class="product-card__meta">
                <div>
                  <span>库存</span>
                  <strong>{{ item.availableStock }}</strong>
                </div>
                <div>
                  <span>已售</span>
                  <strong>{{ item.soldCount }}</strong>
                </div>
              </div>

              <button
                class="primary-action product-card__buy"
                type="button"
                :disabled="item.availableStock === 0"
                @click="openPurchase(item)"
              >
                <el-icon><ShoppingBag /></el-icon>
                {{ item.availableStock === 0 ? '暂时缺货' : '立即下单' }}
              </button>
            </div>
          </article>
        </div>
        <el-empty v-else description="没有找到符合关键词的商品" />
      </el-skeleton>
    </section>

    <el-dialog
      v-model="noticeVisible"
      width="min(720px, 94vw)"
      :title="selectedNotice?.title ?? '公告详情'"
    >
      <div v-if="selectedNotice" class="notice-detail">
        <span class="soft-chip">{{ formatDateTime(selectedNotice.publishedAt) }}</span>
        <p class="notice-detail__summary">{{ selectedNotice.summary }}</p>
        <div class="notice-detail__content">{{ selectedNotice.content }}</div>
      </div>
    </el-dialog>

    <el-dialog
      v-model="purchaseVisible"
      width="min(860px, 94vw)"
      :title="selectedProduct ? `${selectedProduct.title} · 下单` : '下单'"
    >
      <div v-if="selectedProduct" class="purchase-dialog">
        <aside class="purchase-summary">
          <span class="soft-chip">{{ selectedProduct.vendor }}</span>
          <h3>{{ selectedProduct.title }}</h3>
          <p>{{ selectedProduct.planName }}</p>

          <div class="purchase-summary__meta">
            <div>
              <span>单价</span>
              <strong>{{ formatCurrency(selectedProduct.price) }}</strong>
            </div>
            <div>
              <span>库存</span>
              <strong>{{ selectedProduct.availableStock }}</strong>
            </div>
            <div>
              <span>已售</span>
              <strong>{{ selectedProduct.soldCount }}</strong>
            </div>
            <div>
              <span>SKU</span>
              <strong>{{ selectedProduct.sku }}</strong>
            </div>
          </div>
        </aside>

        <div class="purchase-form">
          <el-form label-position="top">
            <el-form-item label="买家姓名">
              <el-input v-model="form.buyerName" maxlength="32" placeholder="用于订单记录" />
            </el-form-item>
            <el-form-item label="联系方式">
              <el-input
                v-model="form.buyerContact"
                maxlength="64"
                placeholder="微信 / QQ / 手机 / 邮箱"
              />
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
                placeholder="可填写发货偏好或其它说明"
              />
            </el-form-item>
          </el-form>

          <div class="purchase-total">
            <span>合计金额</span>
            <strong>{{ formatCurrency(Number(selectedProduct.price) * form.quantity) }}</strong>
          </div>

          <el-alert
            v-if="orderResult"
            type="success"
            show-icon
            :closable="false"
            :title="`订单 ${orderResult.orderNo} 已创建`"
            :description="`状态：${formatOrderStatus(orderResult.status)}，数量：${orderResult.quantity}，金额：${formatCurrency(orderResult.totalAmount)}`"
          />
        </div>
      </div>

      <template #footer>
        <el-button @click="purchaseVisible = false">关闭</el-button>
        <el-button type="primary" :loading="submitting" @click="submitOrder">提交订单</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.public-panel__heading,
.product-panel__heading {
  margin-bottom: 20px;
}

.public-list {
  display: grid;
  gap: 14px;
}

.public-item {
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: 16px;
  align-items: center;
  padding: 18px 20px;
  border: 1px solid var(--surface-stroke);
  border-radius: 22px;
  background: var(--surface-muted);
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.public-item__icon {
  width: 42px;
  height: 42px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 14px;
  background: rgba(79, 106, 145, 0.1);
  color: var(--accent);
}

.public-item__content strong,
.public-item__content p,
.public-item em {
  display: block;
}

.public-item__content p {
  margin: 8px 0 0;
  color: var(--text-secondary);
  line-height: 1.75;
}

.public-item em {
  color: var(--text-soft);
  font-size: 13px;
  font-style: normal;
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 18px;
}

.product-card {
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(255, 255, 255, 0.92);
  box-shadow: 0 18px 42px rgba(128, 147, 168, 0.16);
}

.product-card__body {
  padding: 22px;
  display: grid;
  gap: 18px;
}

.product-card__title-row {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  align-items: flex-start;
}

.product-card__title-copy {
  display: grid;
  gap: 10px;
}

.product-card__title-row h3 {
  margin: 0;
  font-size: 21px;
  line-height: 1.45;
}

.product-card__plan {
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.16);
  color: var(--text-secondary);
  font-size: 12px;
  white-space: nowrap;
}

.product-card__price-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-end;
}

.product-card__sku {
  color: var(--text-soft);
  font-size: 12px;
  letter-spacing: 0.06em;
}

.product-card__price-row strong {
  color: var(--price-color);
  font-size: 34px;
  font-weight: 700;
}

.product-card__meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.product-card__meta div {
  padding: 14px 12px;
  border-radius: 16px;
  background: rgba(245, 248, 252, 0.92);
  border: 1px solid rgba(230, 237, 244, 0.88);
}

.product-card__meta span,
.product-card__meta strong {
  display: block;
}

.product-card__meta span {
  color: var(--text-soft);
  font-size: 12px;
}

.product-card__meta strong {
  margin-top: 6px;
  font-size: 18px;
}

.product-card__buy {
  width: 100%;
}

.notice-detail {
  display: grid;
  gap: 16px;
  color: var(--text-primary);
  line-height: 1.85;
}

.notice-detail__summary {
  margin: 0;
  color: var(--text-secondary);
  font-weight: 600;
}

.notice-detail__content {
  white-space: pre-wrap;
}

.purchase-dialog {
  display: grid;
  grid-template-columns: minmax(250px, 290px) minmax(0, 1fr);
  gap: 20px;
}

.purchase-summary {
  display: grid;
  gap: 16px;
  align-content: start;
  padding: 22px;
  border-radius: 24px;
  background:
    radial-gradient(circle at 14% 16%, rgba(255, 255, 255, 0.82), transparent 22%),
    linear-gradient(135deg, rgba(223, 232, 242, 0.96), rgba(245, 249, 252, 0.92));
  border: 1px solid rgba(255, 255, 255, 0.9);
}

.purchase-summary h3 {
  margin: 0;
  font-size: 26px;
}

.purchase-summary p {
  margin: 0;
  color: var(--text-secondary);
}

.purchase-summary__meta {
  display: grid;
  gap: 12px;
}

.purchase-summary__meta div {
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(245, 248, 252, 0.92);
  border: 1px solid rgba(230, 237, 244, 0.88);
}

.purchase-summary__meta span,
.purchase-summary__meta strong {
  display: block;
}

.purchase-summary__meta span {
  color: var(--text-soft);
  font-size: 12px;
}

.purchase-summary__meta strong {
  margin-top: 8px;
}

.purchase-form {
  display: grid;
  gap: 16px;
}

.purchase-total {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 18px;
  border-radius: 18px;
  background: rgba(245, 248, 252, 0.92);
  border: 1px solid rgba(230, 237, 244, 0.88);
}

.purchase-total strong {
  color: var(--price-color);
  font-size: 26px;
}

@media (max-width: 860px) {
  .public-item,
  .purchase-dialog {
    grid-template-columns: 1fr;
  }

  .public-item {
    align-items: flex-start;
  }

  .public-item em {
    margin-left: 58px;
  }

  .product-card__title-row,
  .product-card__price-row {
    flex-direction: column;
    align-items: flex-start;
  }
}

@media (max-width: 640px) {
  .product-card__meta {
    grid-template-columns: 1fr;
  }
}
</style>
