<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Bell, ShoppingBag } from '@element-plus/icons-vue'
import { createOrder, getNotices, getProduct, getProducts, type Notice, type OrderResult, type Product } from '@/api/shop'
import { fetchMemberProfile, memberProfileState } from '@/api/auth'
import { formatCurrency, formatDateTime, formatOrderStatus } from '@/utils/format'

const route = useRoute()
const router = useRouter()
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
  quantity: 1,
  remark: '',
})

const keyword = computed(() => (typeof route.query.q === 'string' ? route.query.q.trim() : ''))
const balance = computed(() => Number(profile.value?.balance ?? 0))
const totalAmount = computed(() => Number(selectedProduct.value?.price ?? 0) * Number(form.quantity || 0))
const balanceEnough = computed(() => balance.value >= totalAmount.value)

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

async function ensureMemberReady() {
  if (profile.value) {
    return true
  }
  const redirect = encodeURIComponent(route.fullPath || '/')
  ElMessage.warning('请先登录会员账号后再购买')
  await router.push(`/login?redirect=${redirect}`)
  return false
}

async function openPurchase(product: Product) {
  if (!(await ensureMemberReady())) {
    return
  }

  try {
    selectedProduct.value = await getProduct(product.id)
    await fetchMemberProfile()
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
  if (form.quantity < 1) {
    ElMessage.warning('购买数量至少为 1')
    return
  }
  if (!balanceEnough.value) {
    ElMessage.warning('余额不足，请先充值')
    return
  }

  submitting.value = true
  try {
    orderResult.value = await createOrder({
      productId: selectedProduct.value.id,
      quantity: form.quantity,
      remark: form.remark.trim(),
    })
    await Promise.all([fetchMemberProfile(), loadHomeData()])
    ElMessage.success('下单成功，已从余额中扣款')
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '下单失败，请稍后再试')
  } finally {
    submitting.value = false
  }
}

function formatEnableStatus(status: 'ENABLED' | 'DISABLED') {
  return status === 'DISABLED' ? '已停用' : '可用'
}

onMounted(async () => {
  await loadHomeData()
  if (profile.value) {
    void fetchMemberProfile()
  }
})
</script>

<template>
  <div class="shell-body">
    <section class="section-shell public-panel">
      <div class="section-heading public-panel__heading">
        <div>
          <span class="section-kicker">公告</span>
          <h2>最新公告与购买说明</h2>
        </div>
        <p>公告全部展示，点击后可在大弹窗里完整阅读。</p>
      </div>

      <el-skeleton :loading="loading" animated :rows="4">
        <div v-if="notices.length" class="public-list">
          <button
            v-for="notice in notices"
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
        <p v-else>商品基础信息、库存和已售均来自后端实时接口。</p>
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

              <button class="primary-action product-card__buy" type="button" @click="openPurchase(item)">
                <el-icon><ShoppingBag /></el-icon>
                {{ profile ? '余额购买' : '登录后购买' }}
              </button>
            </div>
          </article>
        </div>
        <el-empty v-else description="没有找到符合关键词的商品" />
      </el-skeleton>
    </section>

    <el-dialog
      v-model="noticeVisible"
      width="min(980px, 96vw)"
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
      width="min(920px, 94vw)"
      :title="selectedProduct ? `${selectedProduct.title} · 余额下单` : '余额下单'"
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
              <span>当前余额</span>
              <strong>{{ formatCurrency(balance) }}</strong>
            </div>
          </div>
        </aside>

        <div class="purchase-form">
          <div class="purchase-form__panel">
            <div class="purchase-form__hint">
              <strong>余额支付说明</strong>
              <p>下单会直接从当前会员余额中扣款。余额不足时请先到“我的订单”页面发起充值申请。</p>
            </div>

            <el-form label-position="top">
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
                  placeholder="可填写你的备注信息"
                />
              </el-form-item>
            </el-form>
          </div>

          <div class="purchase-total">
            <span>合计金额</span>
            <strong>{{ formatCurrency(totalAmount) }}</strong>
          </div>

          <el-alert
            :type="balanceEnough ? 'success' : 'warning'"
            :closable="false"
            show-icon
            :title="balanceEnough ? '余额充足，可直接购买' : '余额不足，请先充值'"
            :description="`当前余额 ${formatCurrency(balance)}，本次需支付 ${formatCurrency(totalAmount)}`"
          />

          <el-alert
            v-if="orderResult"
            class="purchase-result-alert"
            type="success"
            show-icon
            :closable="false"
            :title="`订单 ${orderResult.orderNo} 已创建`"
            :description="`状态：${formatOrderStatus(orderResult.status)}，金额：${formatCurrency(orderResult.totalAmount)}，剩余余额：${formatCurrency(orderResult.remainingBalance)}`"
          />

          <div v-if="orderResult" class="purchase-card-keys">
            <div class="purchase-card-keys__header">
              <strong>本次发放卡密</strong>
            </div>
            <div v-if="orderResult.cardKeys?.length" class="purchase-card-keys__list">
              <div v-for="item in orderResult.cardKeys" :key="item.cardKey" class="purchase-card-key-item">
                <strong>{{ item.cardKey }}</strong>
                <span :class="['purchase-card-key-item__status', { 'is-disabled': item.enableStatus === 'DISABLED' }]">
                  {{ formatEnableStatus(item.enableStatus) }}
                </span>
              </div>
            </div>
            <div v-else class="purchase-card-keys__empty">当前订单未返回卡密，请稍后到“我的订单”里查看。</div>
          </div>
        </div>
      </div>

      <template #footer>
        <el-button @click="purchaseVisible = false">关闭</el-button>
        <el-button
          v-if="!orderResult"
          type="primary"
          :disabled="!balanceEnough || selectedProduct?.availableStock === 0"
          :loading="submitting"
          @click="submitOrder"
        >
          立即支付
        </el-button>
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
  background: rgba(236, 245, 255, 0.46);
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
  background: rgba(79, 106, 145, 0.12);
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
  white-space: pre-wrap;
  word-break: break-word;
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
  background: rgba(241, 247, 255, 0.52);
  border: 1px solid rgba(255, 255, 255, 0.94);
  box-shadow: 0 18px 42px rgba(128, 147, 168, 0.16);
}

.product-card__body {
  padding: 22px;
  display: grid;
  gap: 18px;
}

.product-card__title-row,
.product-card__price-row,
.purchase-card-keys__header,
.purchase-card-key-item {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.product-card__title-copy {
  display: grid;
  gap: 10px;
}

.product-card__title-row h3,
.purchase-summary h3 {
  margin: 0;
}

.product-card__plan {
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.24);
  color: var(--text-secondary);
  font-size: 12px;
  white-space: nowrap;
}

.product-card__sku {
  color: var(--text-soft);
  font-size: 12px;
}

.product-card__price-row strong,
.purchase-total strong {
  color: var(--price-color);
  font-size: 32px;
  font-weight: 700;
}

.product-card__meta,
.purchase-summary__meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.product-card__meta div,
.purchase-summary__meta div {
  padding: 14px 12px;
  border-radius: 16px;
  background: rgba(247, 251, 255, 0.8);
  border: 1px solid rgba(223, 233, 244, 0.96);
}

.product-card__meta span,
.product-card__meta strong,
.purchase-summary__meta span,
.purchase-summary__meta strong {
  display: block;
}

.product-card__meta span,
.purchase-summary__meta span {
  color: var(--text-soft);
  font-size: 12px;
}

.product-card__buy {
  width: 100%;
}

.notice-detail {
  display: grid;
  gap: 16px;
  line-height: 1.85;
}

.notice-detail__summary,
.notice-detail__content {
  white-space: pre-wrap;
  word-break: break-word;
}

.notice-detail__content {
  min-height: 240px;
  padding: 18px 20px;
  border-radius: 20px;
  background: rgba(245, 250, 255, 0.78);
  border: 1px solid rgba(214, 228, 243, 0.92);
}

.purchase-dialog {
  display: grid;
  grid-template-columns: minmax(260px, 300px) minmax(0, 1fr);
  gap: 20px;
}

.purchase-summary,
.purchase-form__panel,
.purchase-card-keys {
  padding: 20px;
  border-radius: 24px;
  background: linear-gradient(180deg, rgba(243, 249, 255, 0.82), rgba(231, 242, 252, 0.72));
  border: 1px solid rgba(219, 231, 244, 0.96);
}

.purchase-form {
  display: grid;
  gap: 16px;
}

.purchase-form__hint {
  margin-bottom: 14px;
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(219, 235, 251, 0.72);
  border: 1px solid rgba(193, 215, 238, 0.9);
}

.purchase-form__hint p {
  margin: 8px 0 0;
  color: var(--text-secondary);
  line-height: 1.7;
}

.purchase-total {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 18px;
  border-radius: 18px;
  background: rgba(242, 248, 255, 0.84);
  border: 1px solid rgba(216, 228, 242, 0.96);
}

.purchase-result-alert {
  margin-top: 4px;
}

.purchase-card-keys__list {
  display: grid;
  gap: 10px;
  margin-top: 14px;
}

.purchase-card-key-item {
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(214, 228, 243, 0.96);
}

.purchase-card-key-item strong {
  word-break: break-all;
}

.purchase-card-key-item__status {
  flex-shrink: 0;
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(203, 234, 216, 0.76);
  color: #1f7a45;
  font-size: 12px;
}

.purchase-card-key-item__status.is-disabled {
  background: rgba(232, 221, 224, 0.92);
  color: #8b4f5e;
}

.purchase-card-keys__empty {
  margin-top: 14px;
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(247, 250, 255, 0.84);
  border: 1px dashed rgba(205, 220, 237, 0.96);
  color: var(--text-secondary);
}

@media (max-width: 860px) {
  .public-item,
  .purchase-dialog {
    grid-template-columns: 1fr;
  }

  .public-item,
  .product-card__title-row,
  .product-card__price-row,
  .purchase-card-keys__header,
  .purchase-card-key-item {
    align-items: flex-start;
    flex-direction: column;
  }
}

@media (max-width: 640px) {
  .product-card__meta,
  .purchase-summary__meta {
    grid-template-columns: 1fr;
  }
}
</style>