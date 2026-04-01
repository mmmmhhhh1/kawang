<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Document, UserFilled } from '@element-plus/icons-vue'
import { fetchMemberProfile, memberProfileState } from '@/api/auth'
import { getMyOrders, type OrderRecord } from '@/api/shop'
import { formatCurrency, formatDateTime, formatOrderStatus } from '@/utils/format'

const profile = memberProfileState
const loading = ref(false)
const orders = ref<OrderRecord[]>([])

const totalAmount = computed(() => orders.value.reduce((sum, item) => sum + Number(item.totalAmount ?? 0), 0))
const totalQuantity = computed(() => orders.value.reduce((sum, item) => sum + Number(item.quantity ?? 0), 0))

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
        <p>这里只显示已绑定到当前会员账号的订单记录。</p>
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
      </div>
    </section>

    <section class="section-shell member-page-card">
      <div class="section-heading member-page-card__heading">
        <div>
          <span class="section-kicker">订单</span>
          <h2>绑定订单列表</h2>
        </div>
        <p>登录状态下提交的订单会自动写入 `user_id` 绑定关系。</p>
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
        <p>登录后下单，后端会把订单自动绑定到当前会员账号。</p>
      </article>

      <article class="glass-card member-tip-card">
        <span class="section-chip">
          <el-icon><Document /></el-icon>
          游客订单
        </span>
        <p>如果你之前是游客购买，仍然可以在订单查询页按联系方式查询历史订单。</p>
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
  grid-template-columns: repeat(4, minmax(0, 1fr));
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
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
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

.member-tips {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.member-tip-card p {
  margin: 16px 0 0;
  color: var(--text-secondary);
  line-height: 1.8;
}

@media (max-width: 920px) {
  .member-summary,
  .member-order-card__meta,
  .member-tips {
    grid-template-columns: 1fr;
  }

  .member-order-card__top {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
