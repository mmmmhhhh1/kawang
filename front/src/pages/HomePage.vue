<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Bell, Cherry, MagicStick, ShoppingBag } from '@element-plus/icons-vue'
import { createOrder, getHomeData, getProduct, type Notice, type OrderResult, type Product } from '@/api/shop'
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
const heroX = ref(0)
const heroY = ref(0)
const HOME_CACHE_KEY = 'kawang_home_cache_v1'
const HOME_CACHE_TTL = 5 * 60 * 1000

type HomeCachePayload = {
  savedAt: number
  notices: Notice[]
  products: Product[]
}

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

const featuredProducts = computed(() => filteredProducts.value.slice(0, 2))
const catalogProducts = computed(() => filteredProducts.value.slice(2))
const visibleNotices = computed(() => notices.value.slice(0, 4))
const totalStock = computed(() => filteredProducts.value.reduce((sum, item) => sum + Number(item.availableStock ?? 0), 0))
const totalSold = computed(() => filteredProducts.value.reduce((sum, item) => sum + Number(item.soldCount ?? 0), 0))
const heroTransform = computed(() => ({
  '--hero-offset-x': `${heroX.value}px`,
  '--hero-offset-y': `${heroY.value}px`,
}))

function readHomeCache() {
  try {
    const raw = window.localStorage.getItem(HOME_CACHE_KEY)
    if (!raw) {
      return null
    }

    const parsed = JSON.parse(raw) as HomeCachePayload
    if (!parsed?.savedAt || Date.now() - parsed.savedAt > HOME_CACHE_TTL) {
      window.localStorage.removeItem(HOME_CACHE_KEY)
      return null
    }

    if (!Array.isArray(parsed.products) || !Array.isArray(parsed.notices)) {
      return null
    }

    return parsed
  } catch {
    return null
  }
}

function writeHomeCache(nextProducts: Product[], nextNotices: Notice[]) {
  try {
    const payload: HomeCachePayload = {
      savedAt: Date.now(),
      notices: nextNotices,
      products: nextProducts,
    }
    window.localStorage.setItem(HOME_CACHE_KEY, JSON.stringify(payload))
  } catch {
    // Ignore storage failures.
  }
}

async function loadHomeData() {
  const cached = readHomeCache()
  if (cached) {
    products.value = cached.products
    notices.value = cached.notices
  }

  loading.value = !cached
  try {
    const homeData = await getHomeData()
    products.value = homeData.products
    notices.value = homeData.notices
    writeHomeCache(homeData.products, homeData.notices)
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
    ElMessage.success('订单已创建，卡密已经发放到你的账号')
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '下单失败，请稍后再试')
  } finally {
    submitting.value = false
  }
}

function formatEnableStatus(status: 'ENABLED' | 'DISABLED') {
  return status === 'DISABLED' ? '已停用' : '可用'
}

function scrollToCatalog() {
  document.getElementById('catalog-section')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function handleHeroMove(event: MouseEvent) {
  const target = event.currentTarget as HTMLElement
  const rect = target.getBoundingClientRect()
  const centerX = rect.left + rect.width / 2
  const centerY = rect.top + rect.height / 2
  heroX.value = (event.clientX - centerX) / 22
  heroY.value = (event.clientY - centerY) / 22
}

function resetHeroMove() {
  heroX.value = 0
  heroY.value = 0
}

onMounted(async () => {
  await loadHomeData()
  if (profile.value) {
    void fetchMemberProfile()
  }
})
</script>

<template>
  <div class="shell-body home-page">
    <section
      class="section-shell hero-section page-reveal"
      :style="{ '--delay': '0.02s', ...heroTransform }"
      @mousemove="handleHeroMove"
      @mouseleave="resetHeroMove"
    >
      <div class="hero-copy">
        <span class="section-kicker">樱落计划</span>
        <h1>把常用会员收进一间更好逛、更好买、也更像作品的商店。</h1>

        <div class="hero-actions">
          <button class="hero-action" type="button" @click="scrollToCatalog">
            <el-icon><ShoppingBag /></el-icon>
            立即挑选套餐
          </button>
          <router-link v-if="profile" class="secondary-action" to="/orders/me">进入会员中心</router-link>
          <router-link v-else class="secondary-action" to="/register">先注册会员</router-link>
        </div>

        <div class="hero-metrics">
          <article class="hero-metric-card">
            <span>当前可购商品</span>
            <strong>{{ filteredProducts.length }}</strong>
          </article>
          <article class="hero-metric-card">
            <span>实时可用库存</span>
            <strong>{{ totalStock }}</strong>
          </article>
          <article class="hero-metric-card">
            <span>累计展示销量</span>
            <strong>{{ totalSold }}</strong>
          </article>
        </div>
      </div>

      <div class="hero-art">
        <div class="hero-art__halo" />
        <div class="hero-art__panel hero-art__panel--main">
          <span class="ghost-pill">
            <el-icon><Cherry /></el-icon>
            樱花主题
          </span>
          <strong>买完即回卡密</strong>
        </div>
        <div class="hero-art__panel hero-art__panel--floating">
          <span class="ghost-pill">实时库存</span>
          <strong>{{ totalStock }}</strong>
        </div>
        <div class="hero-art__panel hero-art__panel--accent">
          <span class="ghost-pill">
            <el-icon><MagicStick /></el-icon>
            体验升级
          </span>
        </div>
      </div>
    </section>

    <section class="section-shell bulletin-section page-reveal" :style="{ '--delay': '0.08s' }">
      <div class="section-heading">
        <div>
          <span class="section-kicker">情报板</span>
          <h2>公告与购买提醒</h2>
        </div>
      </div>

      <el-skeleton :loading="loading" animated :rows="4">
        <div v-if="visibleNotices.length" class="bulletin-grid">
          <button
            v-for="(notice, index) in visibleNotices"
            :key="notice.id"
            class="bulletin-card"
            type="button"
            :style="{ '--delay': `${0.12 + index * 0.04}s` }"
            @click="openNotice(notice)"
          >
            <span class="soft-chip">
              <el-icon><Bell /></el-icon>
              最新公告
            </span>
            <strong>{{ notice.title }}</strong>
            <p>{{ notice.summary }}</p>
            <em>{{ formatDateTime(notice.publishedAt) }}</em>
          </button>
        </div>
        <el-empty v-else description="暂时还没有可展示的公告" />
      </el-skeleton>
    </section>

    <section class="section-shell featured-section page-reveal" :style="{ '--delay': '0.14s' }">
      <div class="section-heading">
        <div>
          <span class="section-kicker">精选</span>
          <h2>{{ keyword ? `和 “${keyword}” 最接近的热门套餐` : '优先推荐的热门套餐' }}</h2>
        </div>
      </div>

      <el-skeleton :loading="loading" animated :rows="6">
        <div v-if="featuredProducts.length" class="featured-grid">
          <article
            v-for="(item, index) in featuredProducts"
            :key="item.id"
            class="featured-card"
            :style="{ '--delay': `${0.18 + index * 0.06}s` }"
          >
            <div class="featured-card__eyebrow">
              <span class="soft-chip">{{ item.vendor }}</span>
              <span class="metric-chip">{{ item.planName }}</span>
            </div>

            <div class="featured-card__title">
              <h3>{{ item.title }}</h3>
              <p>{{ item.description || '适合日常高频使用，购买后立即发放卡密。' }}</p>
            </div>

            <div class="featured-card__price">
              <strong>{{ formatCurrency(item.price) }}</strong>
              <span>{{ item.sku }}</span>
            </div>

            <div class="featured-card__meta">
              <div>
                <span>当前库存</span>
                <strong>{{ item.availableStock }}</strong>
              </div>
              <div>
                <span>累计销量</span>
                <strong>{{ item.soldCount }}</strong>
              </div>
            </div>

            <div class="featured-card__actions">
              <button class="primary-action" type="button" @click="openPurchase(item)">
                <el-icon><ShoppingBag /></el-icon>
                {{ profile ? '余额立即购买' : '登录后购买' }}
              </button>
            </div>
          </article>
        </div>
        <el-empty v-else description="没有找到符合当前关键词的商品" />
      </el-skeleton>
    </section>

    <section id="catalog-section" class="section-shell catalog-section page-reveal" :style="{ '--delay': '0.2s' }">
      <div class="section-heading">
        <div>
          <span class="section-kicker">商品陈列</span>
          <h2>全部可购商品</h2>
        </div>
      </div>

      <el-skeleton :loading="loading" animated :rows="8">
        <div v-if="filteredProducts.length" class="catalog-grid">
          <article
            v-for="(item, index) in filteredProducts"
            :key="item.id"
            class="catalog-card"
            :style="{ '--delay': `${0.22 + index * 0.02}s` }"
          >
            <div class="catalog-card__top">
              <div>
                <span class="soft-chip">{{ item.vendor }}</span>
                <h3>{{ item.title }}</h3>
              </div>
              <span class="catalog-card__plan">{{ item.planName }}</span>
            </div>

            <p class="catalog-card__desc">{{ item.description || '下单后同步发放卡密，适合作为常用会员库存补充。' }}</p>

            <div class="catalog-card__footer">
              <div class="catalog-card__price">
                <strong>{{ formatCurrency(item.price) }}</strong>
                <span>{{ item.sku }}</span>
              </div>
              <div class="catalog-card__stats">
                <span>库存 {{ item.availableStock }}</span>
                <span>已售 {{ item.soldCount }}</span>
              </div>
            </div>

            <button class="surface-button catalog-card__button" type="button" @click="openPurchase(item)">
              查看并购买
            </button>
          </article>
        </div>
        <el-empty v-else description="没有找到符合当前关键词的商品" />
      </el-skeleton>
    </section>

    <el-dialog
      v-model="noticeVisible"
      width="min(920px, 96vw)"
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
      width="min(960px, 94vw)"
      :title="selectedProduct ? `${selectedProduct.title} · 余额下单` : '余额下单'"
    >
      <div v-if="selectedProduct" class="purchase-dialog">
        <aside class="purchase-summary">
          <span class="soft-chip">{{ selectedProduct.vendor }}</span>
          <h3>{{ selectedProduct.title }}</h3>
          <p>{{ selectedProduct.planName }}</p>

          <div class="purchase-summary__price">
            <strong>{{ formatCurrency(selectedProduct.price) }}</strong>
            <span>{{ selectedProduct.sku }}</span>
          </div>

          <div class="purchase-summary__meta">
            <div>
              <span>当前库存</span>
              <strong>{{ selectedProduct.availableStock }}</strong>
            </div>
            <div>
              <span>累计销量</span>
              <strong>{{ selectedProduct.soldCount }}</strong>
            </div>
            <div>
              <span>当前余额</span>
              <strong>{{ formatCurrency(balance) }}</strong>
            </div>
            <div>
              <span>结算方式</span>
              <strong>余额支付</strong>
            </div>
          </div>
        </aside>

        <div class="purchase-form">
          <div class="purchase-form__panel">
            <div class="purchase-form__intro">
              <strong>确认购买数量与备注</strong>
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
                />
              </el-form-item>
            </el-form>
          </div>

          <div class="purchase-total">
            <span>本次支付</span>
            <strong>{{ formatCurrency(totalAmount) }}</strong>
          </div>

          <el-alert
            :type="balanceEnough ? 'success' : 'warning'"
            :closable="false"
            show-icon
            :title="balanceEnough ? '余额充足，可以直接购买' : '余额不足，请先前往会员中心充值'"
          />

          <el-alert
            v-if="orderResult"
            class="purchase-result-alert"
            type="success"
            show-icon
            :closable="false"
            :title="`订单 ${orderResult.orderNo} 已创建`"
          />

          <div v-if="orderResult" class="purchase-card-keys">
            <div class="purchase-card-keys__header">
              <strong>本次发放卡密</strong>
              <span class="ghost-pill">{{ orderResult.cardKeys?.length ?? 0 }} 条</span>
            </div>
            <div v-if="orderResult.cardKeys?.length" class="purchase-card-keys__list">
              <div v-for="item in orderResult.cardKeys" :key="item.cardKey" class="purchase-card-key-item">
                <strong>{{ item.cardKey }}</strong>
                <span :class="['purchase-card-key-item__status', { 'is-disabled': item.enableStatus === 'DISABLED' }]">
                  {{ formatEnableStatus(item.enableStatus) }}
                </span>
              </div>
            </div>
            <div v-else class="purchase-card-keys__empty">暂无卡密</div>
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
.hero-section {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(320px, 430px);
  gap: 24px;
  min-height: 580px;
}

.hero-copy {
  position: relative;
  z-index: 1;
  display: grid;
  align-content: center;
  gap: 22px;
}

.hero-copy h1 {
  margin: 0;
  max-width: 780px;
  font-size: clamp(40px, 7vw, 66px);
  line-height: 1.02;
  letter-spacing: -0.03em;
}

.hero-copy p {
  margin: 0;
  max-width: 640px;
  color: var(--hero-ink);
  font-size: 16px;
  line-height: 1.95;
}

.hero-actions,
.featured-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.hero-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.hero-metric-card,
.bulletin-card,
.featured-card,
.catalog-card {
  position: relative;
  overflow: hidden;
  opacity: 0;
  animation: page-rise 0.76s cubic-bezier(0.22, 1, 0.36, 1) forwards;
  animation-delay: var(--delay, 0s);
}

.hero-metric-card {
  padding: 18px 18px 20px;
  border-radius: 24px;
  background: rgba(255, 252, 254, 0.72);
  border: 1px solid rgba(255, 255, 255, 0.84);
  box-shadow: 0 18px 34px rgba(111, 89, 139, 0.12);
}

.hero-metric-card span,
.hero-metric-card strong,
.hero-metric-card em {
  display: block;
}

.hero-metric-card span {
  color: var(--text-muted);
  font-size: 12px;
}

.hero-metric-card strong {
  margin-top: 10px;
  font-size: 30px;
}

.hero-metric-card em {
  margin-top: 8px;
  color: var(--text-secondary);
  font-size: 13px;
  font-style: normal;
  line-height: 1.65;
}

.hero-art {
  position: relative;
  min-height: 100%;
}

.hero-art__halo {
  position: absolute;
  inset: 12% 8% 8%;
  border-radius: 40px;
  background:
    radial-gradient(circle at 30% 24%, rgba(255, 189, 219, 0.86), transparent 30%),
    radial-gradient(circle at 72% 16%, rgba(175, 171, 255, 0.64), transparent 24%),
    linear-gradient(180deg, rgba(255, 249, 251, 0.92), rgba(246, 241, 255, 0.82));
  filter: blur(2px);
  transform: translate3d(calc(var(--hero-offset-x) * 0.45), calc(var(--hero-offset-y) * 0.45), 0);
}

.hero-art__panel {
  position: absolute;
  border-radius: 32px;
  border: 1px solid rgba(255, 255, 255, 0.88);
  box-shadow: 0 24px 54px rgba(108, 85, 135, 0.18);
  backdrop-filter: blur(24px);
}

.hero-art__panel strong,
.hero-art__panel p {
  display: block;
}

.hero-art__panel p {
  margin: 10px 0 0;
  color: var(--text-secondary);
  line-height: 1.75;
}

.hero-art__panel--main {
  top: 6%;
  right: 0;
  width: min(100%, 310px);
  padding: 24px;
  background: rgba(255, 252, 254, 0.78);
  transform: translate3d(calc(var(--hero-offset-x) * 1.15), calc(var(--hero-offset-y) * 1.15), 0);
}

.hero-art__panel--main strong {
  margin-top: 14px;
  font-size: 30px;
}

.hero-art__panel--floating {
  left: 2%;
  top: 32%;
  width: min(100%, 220px);
  padding: 22px;
  background: rgba(255, 245, 249, 0.84);
  transform: translate3d(calc(var(--hero-offset-x) * -0.9), calc(var(--hero-offset-y) * -0.9), 0);
}

.hero-art__panel--floating strong {
  margin-top: 12px;
  font-size: 42px;
  color: var(--price-color);
}

.hero-art__panel--accent {
  right: 8%;
  bottom: 4%;
  width: min(100%, 280px);
  padding: 22px;
  background: linear-gradient(135deg, rgba(42, 40, 74, 0.88), rgba(106, 75, 140, 0.84));
  color: var(--text-on-dark);
  transform: translate3d(calc(var(--hero-offset-x) * 0.8), calc(var(--hero-offset-y) * 0.8), 0);
}

.hero-art__panel--accent p {
  color: rgba(255, 243, 249, 0.78);
}

.bulletin-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.bulletin-card,
.catalog-card {
  text-align: left;
  cursor: pointer;
}

.bulletin-card {
  display: grid;
  align-content: start;
  gap: 14px;
  padding: 20px;
  border: 1px solid rgba(255, 255, 255, 0.82);
  border-radius: 24px;
  background: rgba(255, 250, 252, 0.72);
  box-shadow: 0 16px 34px rgba(106, 83, 133, 0.1);
}

.bulletin-card strong,
.bulletin-card p,
.bulletin-card em {
  display: block;
}

.bulletin-card strong {
  font-size: 18px;
}

.bulletin-card p {
  margin: 0;
  color: var(--text-secondary);
  line-height: 1.8;
}

.bulletin-card em {
  color: var(--text-muted);
  font-size: 12px;
  font-style: normal;
}

.bulletin-card:hover,
.featured-card:hover,
.catalog-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 24px 48px rgba(110, 86, 137, 0.16);
}

.featured-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

.featured-card {
  display: grid;
  gap: 18px;
  padding: 24px;
  border-radius: 28px;
  background:
    radial-gradient(circle at top right, rgba(255, 213, 232, 0.38), transparent 34%),
    linear-gradient(180deg, rgba(255, 252, 254, 0.92), rgba(249, 245, 255, 0.84));
  border: 1px solid rgba(255, 255, 255, 0.88);
  box-shadow: 0 22px 54px rgba(106, 83, 133, 0.12);
}

.featured-card__eyebrow,
.catalog-card__top,
.catalog-card__footer,
.purchase-card-keys__header,
.purchase-card-key-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.featured-card__title h3,
.catalog-card__top h3,
.purchase-summary h3 {
  margin: 0;
}

.featured-card__title p,
.catalog-card__desc {
  margin: 10px 0 0;
  color: var(--text-secondary);
  line-height: 1.82;
}

.featured-card__price strong,
.catalog-card__price strong,
.purchase-summary__price strong,
.purchase-total strong {
  color: var(--price-color);
  font-size: clamp(32px, 4vw, 42px);
  line-height: 1;
}

.featured-card__price span,
.catalog-card__price span,
.purchase-summary__price span {
  display: block;
  margin-top: 8px;
  color: var(--text-muted);
  font-size: 12px;
}

.featured-card__meta,
.purchase-summary__meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.featured-card__meta div,
.purchase-summary__meta div {
  padding: 14px;
  border-radius: 18px;
  background: rgba(255, 251, 253, 0.78);
  border: 1px solid rgba(255, 255, 255, 0.8);
}

.featured-card__meta span,
.featured-card__meta strong,
.purchase-summary__meta span,
.purchase-summary__meta strong,
.catalog-card__stats span {
  display: block;
}

.featured-card__meta span,
.purchase-summary__meta span {
  color: var(--text-muted);
  font-size: 12px;
}

.catalog-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(258px, 1fr));
  gap: 18px;
}

.catalog-card {
  display: grid;
  gap: 18px;
  padding: 22px;
  border-radius: 26px;
  background: rgba(255, 251, 253, 0.72);
  border: 1px solid rgba(255, 255, 255, 0.82);
  box-shadow: 0 16px 34px rgba(110, 86, 137, 0.1);
}

.catalog-card__plan {
  flex-shrink: 0;
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(244, 235, 255, 0.84);
  color: var(--accent-violet);
  font-size: 12px;
}

.catalog-card__stats {
  display: grid;
  gap: 6px;
  color: var(--text-secondary);
  font-size: 13px;
}

.catalog-card__button {
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
  min-height: 220px;
  padding: 18px 20px;
  border-radius: 24px;
  background: rgba(255, 250, 252, 0.74);
  border: 1px solid rgba(255, 255, 255, 0.84);
}

.purchase-dialog {
  display: grid;
  grid-template-columns: minmax(280px, 320px) minmax(0, 1fr);
  gap: 20px;
}

.purchase-summary,
.purchase-form__panel,
.purchase-card-keys {
  padding: 22px;
  border-radius: 28px;
  background:
    radial-gradient(circle at top right, rgba(255, 214, 229, 0.24), transparent 30%),
    rgba(255, 251, 253, 0.74);
  border: 1px solid rgba(255, 255, 255, 0.82);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.42);
}

.purchase-summary p,
.purchase-form__intro p {
  margin: 8px 0 0;
  color: var(--text-secondary);
  line-height: 1.75;
}

.purchase-summary__price {
  margin-top: 18px;
}

.purchase-form {
  display: grid;
  gap: 16px;
}

.purchase-form__intro {
  margin-bottom: 14px;
}

.purchase-total {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 18px 20px;
  border-radius: 20px;
  background: rgba(255, 248, 251, 0.84);
  border: 1px solid rgba(255, 255, 255, 0.82);
}

.purchase-result-alert {
  margin-top: 2px;
}

.purchase-card-keys__list {
  display: grid;
  gap: 10px;
  margin-top: 14px;
}

.purchase-card-key-item {
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid rgba(255, 255, 255, 0.84);
}

.purchase-card-key-item strong {
  word-break: break-all;
}

.purchase-card-key-item__status {
  flex-shrink: 0;
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(204, 236, 215, 0.82);
  color: var(--success);
  font-size: 12px;
}

.purchase-card-key-item__status.is-disabled {
  background: rgba(248, 223, 233, 0.9);
  color: var(--danger);
}

.purchase-card-keys__empty {
  margin-top: 14px;
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(255, 251, 253, 0.8);
  border: 1px dashed rgba(233, 205, 219, 0.88);
  color: var(--text-secondary);
}

@media (max-width: 1080px) {
  .hero-section,
  .purchase-dialog,
  .bulletin-grid,
  .featured-grid {
    grid-template-columns: 1fr;
  }

  .hero-art {
    min-height: 420px;
  }
}

@media (max-width: 780px) {
  .hero-metrics,
  .featured-card__meta,
  .purchase-summary__meta {
    grid-template-columns: 1fr;
  }

  .catalog-card__top,
  .catalog-card__footer,
  .purchase-card-keys__header,
  .purchase-card-key-item {
    flex-direction: column;
    align-items: flex-start;
  }
}

@media (pointer: coarse), (max-width: 720px) {
  .hero-section {
    min-height: auto;
  }

  .hero-art {
    min-height: 260px;
  }

  .hero-art__halo {
    filter: none;
    transform: none;
  }

  .hero-art__panel {
    backdrop-filter: none;
    -webkit-backdrop-filter: none;
    box-shadow: 0 14px 30px rgba(97, 74, 124, 0.16);
    transform: none;
  }

  .bulletin-card:hover,
  .featured-card:hover,
  .catalog-card:hover {
    transform: none;
  }

  .notice-detail__content {
    min-height: 0;
    padding: 16px 18px;
    background: linear-gradient(180deg, rgba(255, 252, 254, 0.98), rgba(249, 246, 255, 0.96));
  }
}
</style>
