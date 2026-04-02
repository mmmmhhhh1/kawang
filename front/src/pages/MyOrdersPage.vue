<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Document, DocumentCopy, UserFilled } from '@element-plus/icons-vue'
import { fetchMemberProfile, memberProfileState } from '@/api/auth'
import { getMyOrders, type CardKeyRecord, type OrderRecord } from '@/api/shop'
import { formatCurrency, formatDateTime, formatOrderStatus } from '@/utils/format'

const profile = memberProfileState
const loading = ref(false)
const orders = ref<OrderRecord[]>([])

const totalAmount = computed(() => orders.value.reduce((sum, item) => sum + Number(item.totalAmount ?? 0), 0))
const totalQuantity = computed(() => orders.value.reduce((sum, item) => sum + Number(item.quantity ?? 0), 0))
const totalCardKeys = computed(() => orders.value.reduce((sum, item) => sum + (item.cardKeys?.length ?? 0), 0))

function formatEnableStatus(status: CardKeyRecord['enableStatus']) {
  return status === 'DISABLED' ? '已停用' : '可用'
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

async function loadOrders() {
  loading.value = true
  try {
    if (!profile.value) {
      await fetchMemberProfile()
    }
    orders.value = await getMyOrders()
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '订单加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(loadOrders)
</script>

<template>
  <div class="shell-body">
    <section class="section-shell member-page-card">
      <div class="section-heading member-page-card__heading">
        <div>
          <span class="section-kicker">会员</span>
          <h2>我的订单</h2>
        </div>
        <p>这里只显示已绑定到当前会员账号的订单记录，并同步展示已发放的卡密。</p>
      </div>

      <div class="member-summary">
        <article class="member-summary__item">
          <span>当前账号</span>
          <strong>{{ profile?.username ?? '-' }}</strong>
        </article>
        <article class="member-summary__item">
          <span>订单数量</span>
          <strong>{{ orders.length }}</strong>
        </article>
        <article class="member-summary__item">
          <span>购买数量</span>
          <strong>{{ totalQuantity }}</strong>
        </article>
        <article class="member-summary__item">
          <span>累计金额</span>
          <strong>{{ formatCurrency(totalAmount) }}</strong>
        </article>
        <article class="member-summary__item">
          <span>卡密条数</span>
          <strong>{{ totalCardKeys }}</strong>
        </article>
      </div>
    </section>

    <section class="section-shell member-page-card">
      <div class="section-heading member-page-card__heading">
        <div>
          <span class="section-kicker">订单</span>
          <h2>绑定订单列表</h2>
        </div>
        <p>登录状态下提交的订单会自动写入 <code>user_id</code> 绑定关系，后续可在这里再次查看卡密。</p>
      </div>

      <el-skeleton :loading="loading" animated :rows="6">
        <div v-if="orders.length" class="member-orders-grid">
          <article v-for="order in orders" :key="order.id" class="member-order-card">
            <div class="member-order-card__top">
              <div>
                <strong>{{ order.productTitle }}</strong>
                <span>{{ order.orderNo }}</span>
              </div>
              <el-tag :type="order.status === 'SUCCESS' ? 'success' : 'info'">
                {{ formatOrderStatus(order.status) }}
              </el-tag>
            </div>

            <div class="member-order-card__meta">
              <div>
                <span>数量</span>
                <strong>{{ order.quantity }}</strong>
              </div>
              <div>
                <span>金额</span>
                <strong>{{ formatCurrency(order.totalAmount) }}</strong>
              </div>
              <div>
                <span>下单时间</span>
                <strong>{{ formatDateTime(order.createdAt) }}</strong>
              </div>
            </div>

            <div class="member-order-card__keys">
              <div class="member-order-card__keys-head">
                <strong>卡密列表</strong>
                <button
                  v-if="order.cardKeys?.length"
                  class="secondary-action member-order-card__copy"
                  type="button"
                  @click="copyCardKeys(order.cardKeys)"
                >
                  <el-icon><DocumentCopy /></el-icon>
                  复制全部卡密
                </button>
              </div>

              <div v-if="order.cardKeys?.length" class="card-key-list">
                <div v-for="item in order.cardKeys" :key="`${order.id}-${item.cardKey}`" class="card-key-item">
                  <strong>{{ item.cardKey }}</strong>
                  <span :class="['card-key-item__status', { 'is-disabled': item.enableStatus === 'DISABLED' }]">
                    {{ formatEnableStatus(item.enableStatus) }}
                  </span>
                </div>
              </div>
              <div v-else class="card-key-empty">该订单没有可展示的卡密，可能是历史旧订单。</div>
            </div>
          </article>
        </div>

        <el-empty v-else description="当前账号还没有绑定订单" />
      </el-skeleton>
    </section>

    <section class="member-tips">
      <article class="glass-card member-tip-card">
        <span class="section-chip">
          <el-icon><UserFilled /></el-icon>
          会员绑定
        </span>
        <p>登录后下单，后端会把订单自动绑定到当前会员账号，方便你后续再次查看卡密。</p>
      </article>

      <article class="glass-card member-tip-card">
        <span class="section-chip">
          <el-icon><Document /></el-icon>
          游客订单
        </span>
        <p>如果你之前是游客购买，仍然可以在订单查询页用“联系方式 + 查单密码”或旧订单兼容方式查询历史记录。</p>
      </article>
    </section>
  </div>
</template>

<style scoped>
.member-page-card__heading {
  margin-bottom: 18px;
}

.member-summary,
.member-orders-grid,
.member-tips {
  display: grid;
  gap: 16px;
}

.member-summary {
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
}

.member-summary__item,
.member-order-card {
  padding: 20px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.3);
  backdrop-filter: blur(26px);
}

.member-summary__item span,
.member-summary__item strong {
  display: block;
}

.member-summary__item span {
  color: var(--text-soft);
  font-size: 12px;
}

.member-summary__item strong {
  margin-top: 8px;
  font-size: 24px;
}

.member-orders-grid {
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
}

.member-order-card__top {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.member-order-card__top strong,
.member-order-card__top span {
  display: block;
}

.member-order-card__top span {
  margin-top: 8px;
  color: var(--text-soft);
  font-size: 12px;
}

.member-order-card__meta {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 18px;
}

.member-order-card__meta div {
  padding: 12px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.24);
}

.member-order-card__meta span,
.member-order-card__meta strong {
  display: block;
}

.member-order-card__meta span {
  color: var(--text-soft);
  font-size: 12px;
}

.member-order-card__meta strong {
  margin-top: 6px;
}

.member-order-card__keys {
  margin-top: 18px;
  padding-top: 18px;
  border-top: 1px solid rgba(255, 255, 255, 0.18);
}

.member-order-card__keys-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.member-order-card__copy {
  padding: 0 12px;
}

.card-key-list {
  display: grid;
  gap: 10px;
  margin-top: 14px;
}

.card-key-item {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.12);
  border: 1px solid rgba(255, 255, 255, 0.24);
}

.card-key-item strong {
  line-height: 1.7;
  word-break: break-all;
}

.card-key-item__status {
  flex-shrink: 0;
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(203, 234, 216, 0.76);
  color: #1f7a45;
  font-size: 12px;
}

.card-key-item__status.is-disabled {
  background: rgba(232, 221, 224, 0.92);
  color: #8b4f5e;
}

.card-key-empty {
  margin-top: 14px;
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.08);
  border: 1px dashed rgba(255, 255, 255, 0.24);
  color: var(--text-secondary);
}

.member-tips {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.member-tip-card p {
  margin: 16px 0 0;
  color: var(--text-secondary);
  line-height: 1.8;
}

@media (max-width: 920px) {
  .member-order-card__meta,
  .member-tips {
    grid-template-columns: 1fr;
  }

  .member-order-card__top,
  .member-order-card__keys-head,
  .card-key-item {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
