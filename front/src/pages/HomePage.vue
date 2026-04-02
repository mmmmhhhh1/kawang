<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Bell, DocumentCopy, ShoppingBag } from '@element-plus/icons-vue'
import {
  createOrder,
  getNotices,
  getProduct,
  getProducts,
  type CardKeyRecord,
  type Notice,
  type OrderResult,
  type Product,
} from '@/api/shop'
import { memberProfileState } from '@/api/auth'
import { formatCurrency, formatDateTime, formatOrderStatus } from '@/utils/format'

const route = useRoute()
const profile = memberProfileState
const lookupSecretPattern = /^[A-Za-z0-9]{6,20}$/

const loading = ref(false)
const submitting = ref(false)
const noticeVisible = ref(false)
const purchaseVisible = ref(false)
const notices = ref<Notice[]>([])
const products = ref<Product[]>([])
const selectedNotice = ref<Notice | null>(null)
const selectedProduct = ref<Product | null>(null)
const orderResult = ref<OrderResult | null>(null)
const orderCredential = ref<{ buyerContact: string; lookupSecret: string } | null>(null)

const form = reactive({
  buyerName: '',
  buyerContact: '',
  lookupSecret: '',
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

function formatEnableStatus(status: CardKeyRecord['enableStatus']) {
  return status === 'DISABLED' ? '已停用' : '可用'
}

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
    form.lookupSecret = ''
    form.quantity = 1
    form.remark = ''
    orderResult.value = null
    orderCredential.value = null
    purchaseVisible.value = true
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '商品详情加载失败')
  }
}

function validateOrderForm() {
  if (!form.buyerName.trim() || !form.buyerContact.trim()) {
    ElMessage.warning('请填写买家姓名和联系方式')
    return false
  }
  if (!lookupSecretPattern.test(form.lookupSecret.trim())) {
    ElMessage.warning('查单密码需为 6-20 位字母或数字')
    return false
  }
  return true
}

async function copyCardKeys(cardKeys: CardKeyRecord[]) {
  if (!cardKeys.length) {
    ElMessage.warning('当前订单没有可复制的卡密')
    return
  }

  try {
    await navigator.clipboard.writeText(cardKeys.map((item) => item.cardKey).join('\n'))
    ElMessage.success('卡密已复制')
  } catch {
    ElMessage.error('复制失败，请手动复制')
  }
}

async function submitOrder() {
  if (!selectedProduct.value || !validateOrderForm()) {
    return
  }

  submitting.value = true
  try {
    const buyerContact = form.buyerContact.trim()
    const lookupSecret = form.lookupSecret.trim()
    orderResult.value = await createOrder({
      productId: selectedProduct.value.id,
      quantity: form.quantity,
      buyerName: form.buyerName.trim(),
      buyerContact,
      lookupSecret,
      remark: form.remark.trim(),
    })
    orderCredential.value = { buyerContact, lookupSecret }
    ElMessage.success('订单已创建，请立即保存查单信息')
    await loadHomeData()
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '下单失败，请稍后再试')
  } finally {
    submitting.value = false
  }
}

async function confirmCredentialSaved() {
  if (!orderResult.value || !orderCredential.value) {
    return true
  }
  try {
    await ElMessageBox.confirm(
      '新订单后续需要同时输入联系方式和查单密码才能查询。请确认你已经保存好这两项信息。',
      '请先保存查单信息',
      {
        confirmButtonText: '我已保存',
        cancelButtonText: '返回继续查看',
        type: 'warning',
        closeOnClickModal: false,
        closeOnPressEscape: false,
      },
    )
    return true
  } catch {
    return false
  }
}

async function requestClosePurchase() {
  if (await confirmCredentialSaved()) {
    purchaseVisible.value = false
  }
}

async function handlePurchaseDialogClose(done: () => void) {
  if (await confirmCredentialSaved()) {
    done()
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
        <p>保留最直接的公告与购买提示，避免堆叠无用信息。</p>
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
      width="min(920px, 94vw)"
      :title="selectedProduct ? `${selectedProduct.title} · 下单` : '下单'"
      :before-close="handlePurchaseDialogClose"
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
          <div class="purchase-form__panel">
            <div class="purchase-form__hint">
              <strong>新订单查单规则已升级</strong>
              <p>下单后请务必保存“联系方式 + 查单密码”。新订单不再支持只输入联系方式查询。</p>
            </div>

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
              <el-form-item label="查单密码">
                <el-input
                  v-model="form.lookupSecret"
                  maxlength="20"
                  show-password
                  placeholder="6-20 位字母或数字"
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
                  placeholder="可填写发货偏好或其他说明"
                />
              </el-form-item>
            </el-form>
          </div>

          <div class="purchase-total">
            <span>合计金额</span>
            <strong>{{ formatCurrency(Number(selectedProduct.price) * form.quantity) }}</strong>
          </div>

          <el-alert
            v-if="orderResult"
            class="purchase-result-alert"
            type="success"
            show-icon
            :closable="false"
            :title="`订单 ${orderResult.orderNo} 已创建`"
            :description="`状态：${formatOrderStatus(orderResult.status)}，数量：${orderResult.quantity}，金额：${formatCurrency(orderResult.totalAmount)}`"
          />

          <div v-if="orderResult" class="purchase-card-keys">
            <div class="purchase-card-keys__header">
              <strong>本次发放卡密</strong>
              <button
                v-if="orderResult.cardKeys?.length"
                class="secondary-action purchase-card-keys__copy"
                type="button"
                @click="copyCardKeys(orderResult.cardKeys)"
              >
                <el-icon><DocumentCopy /></el-icon>
                复制全部卡密
              </button>
            </div>

            <div v-if="orderResult.cardKeys?.length" class="purchase-card-keys__list">
              <div v-for="item in orderResult.cardKeys" :key="item.cardKey" class="purchase-card-key-item">
                <strong>{{ item.cardKey }}</strong>
                <span :class="['purchase-card-key-item__status', { 'is-disabled': item.enableStatus === 'DISABLED' }]">
                  {{ formatEnableStatus(item.enableStatus) }}
                </span>
              </div>
            </div>
            <div v-else class="purchase-card-keys__empty">当前订单未返回卡密，请稍后通过查单页再次确认。</div>
          </div>

          <div v-if="orderResult && orderCredential" class="purchase-reminder">
            <div class="purchase-reminder__header">
              <strong>请立即保存查单信息</strong>
              <span>关闭窗口前请再次确认</span>
            </div>
            <p>后续查询这个新订单时，必须同时输入下面两项信息：</p>
            <div class="credential-grid">
              <div>
                <span>联系方式</span>
                <strong>{{ orderCredential.buyerContact }}</strong>
              </div>
              <div>
                <span>查单密码</span>
                <strong>{{ orderCredential.lookupSecret }}</strong>
              </div>
            </div>
          </div>
        </div>
      </div>

      <template #footer>
        <el-button @click="requestClosePurchase">{{ orderResult ? '我已保存，关闭窗口' : '关闭' }}</el-button>
        <el-button v-if="!orderResult" type="primary" :loading="submitting" @click="submitOrder">提交订单</el-button>
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
  background: rgba(255, 255, 255, 0.24);
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
  background: rgba(247, 251, 255, 0.8);
  border: 1px solid rgba(223, 233, 244, 0.96);
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
  grid-template-columns: minmax(260px, 300px) minmax(0, 1fr);
  gap: 20px;
}

.purchase-summary {
  display: grid;
  gap: 16px;
  align-content: start;
  padding: 22px;
  border-radius: 24px;
  background: linear-gradient(165deg, rgba(230, 242, 255, 0.88), rgba(216, 233, 247, 0.78));
  border: 1px solid rgba(255, 255, 255, 0.96);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.55);
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
  background: rgba(248, 252, 255, 0.88);
  border: 1px solid rgba(221, 232, 243, 0.96);
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

.purchase-form__panel {
  padding: 20px;
  border-radius: 24px;
  background: linear-gradient(180deg, rgba(243, 249, 255, 0.82), rgba(231, 242, 252, 0.72));
  border: 1px solid rgba(219, 231, 244, 0.96);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.58);
}

.purchase-form__hint {
  margin-bottom: 14px;
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(219, 235, 251, 0.72);
  border: 1px solid rgba(193, 215, 238, 0.9);
}

.purchase-form__hint strong,
.purchase-form__hint p {
  display: block;
}

.purchase-form__hint strong {
  margin-bottom: 6px;
}

.purchase-form__hint p {
  margin: 0;
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

.purchase-total strong {
  color: var(--price-color);
  font-size: 26px;
}

.purchase-result-alert {
  margin-top: 4px;
}

.purchase-card-keys {
  padding: 18px 20px;
  border-radius: 22px;
  background: rgba(240, 248, 255, 0.82);
  border: 1px solid rgba(198, 217, 238, 0.92);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.68);
}

.purchase-card-keys__header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.purchase-card-keys__copy {
  padding: 0 12px;
}

.purchase-card-keys__list {
  display: grid;
  gap: 10px;
  margin-top: 14px;
}

.purchase-card-key-item {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(214, 228, 243, 0.96);
}

.purchase-card-key-item strong {
  line-height: 1.7;
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

.purchase-reminder {
  padding: 18px 20px;
  border-radius: 22px;
  background: rgba(255, 244, 248, 0.76);
  border: 1px solid rgba(238, 195, 209, 0.86);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.65);
}

.purchase-reminder__header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.purchase-reminder__header strong,
.purchase-reminder__header span {
  display: block;
}

.purchase-reminder__header span {
  color: var(--text-secondary);
  font-size: 13px;
}

.purchase-reminder p {
  margin: 12px 0 0;
  color: var(--text-secondary);
  line-height: 1.75;
}

.credential-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 14px;
}

.credential-grid div {
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(241, 206, 219, 0.94);
}

.credential-grid span,
.credential-grid strong {
  display: block;
}

.credential-grid span {
  color: var(--text-soft);
  font-size: 12px;
}

.credential-grid strong {
  margin-top: 8px;
  word-break: break-all;
}

@media (max-width: 860px) {
  .public-item,
  .purchase-dialog,
  .credential-grid {
    grid-template-columns: 1fr;
  }

  .public-item {
    align-items: flex-start;
  }

  .public-item em {
    margin-left: 58px;
  }

  .product-card__title-row,
  .product-card__price-row,
  .purchase-card-keys__header,
  .purchase-card-key-item,
  .purchase-reminder__header {
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
