<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { memberProfileState } from '@/api/auth'
import { queryOrders, type OrderRecord } from '@/api/shop'
import { formatCurrency, formatDateTime, formatOrderStatus } from '@/utils/format'

const profile = memberProfileState
const loading = ref(false)
const queried = ref(false)
const orders = ref<OrderRecord[]>([])

const form = reactive({
  buyerContact: '',
  orderNo: '',
})

const totalAmount = computed(() => orders.value.reduce((sum, item) => sum + Number(item.totalAmount ?? 0), 0))

async function handleQuery() {
  if (!form.buyerContact.trim()) {
    ElMessage.warning('请先输入联系方式')
    return
  }

  loading.value = true
  try {
    orders.value = await queryOrders({
      buyerContact: form.buyerContact.trim(),
      orderNo: form.orderNo.trim(),
    })
    queried.value = true
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '订单查询失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="shell-body">
    <section class="section-shell compact-page-card">
      <div class="section-heading compact-page-card__heading">
        <div>
          <span class="section-kicker">查询</span>
          <h2>订单查询</h2>
        </div>
        <p>游客和会员都可以通过下单时填写的联系方式查询订单。</p>
      </div>

      <div class="query-form-card">
        <div class="query-form-card__head">
          <span class="section-chip">
            <el-icon><Search /></el-icon>
            查询表单
          </span>
          <p>订单号为可选项，填写后会缩小查询范围。</p>
        </div>

        <el-form label-position="top" class="query-form-grid">
          <el-form-item label="联系方式">
            <el-input
              v-model="form.buyerContact"
              maxlength="64"
              placeholder="微信 / QQ / 手机 / 邮箱"
              clearable
            />
          </el-form-item>
          <el-form-item label="订单号（可选）">
            <el-input v-model="form.orderNo" maxlength="64" placeholder="例如 2026032912345678" clearable />
          </el-form-item>
        </el-form>

        <div class="query-form-card__actions">
          <button class="primary-action" type="button" :disabled="loading" @click="handleQuery">
            {{ loading ? '查询中...' : '查询订单' }}
          </button>
          <router-link v-if="profile" class="secondary-action" to="/orders/me">我的订单</router-link>
          <router-link v-else class="secondary-action" to="/login">登录后查看绑定订单</router-link>
        </div>
      </div>
    </section>

    <section v-if="queried" class="section-shell compact-page-card">
      <div class="section-heading compact-page-card__heading">
        <div>
          <span class="section-kicker">结果</span>
          <h2>查询结果</h2>
        </div>
        <p>共找到 {{ orders.length }} 笔订单，总金额 {{ formatCurrency(totalAmount) }}</p>
      </div>

      <div v-if="orders.length" class="query-result-grid">
        <article v-for="order in orders" :key="order.id" class="result-card">
          <div class="result-card__top">
            <div>
              <strong>{{ order.productTitle }}</strong>
              <span>{{ order.orderNo }}</span>
            </div>
            <el-tag :type="order.status === 'SUCCESS' ? 'success' : 'info'">
              {{ formatOrderStatus(order.status) }}
            </el-tag>
          </div>

          <div class="result-card__meta">
            <div>
              <span>数量</span>
              <strong>{{ order.quantity }}</strong>
            </div>
            <div>
              <span>金额</span>
              <strong>{{ formatCurrency(order.totalAmount) }}</strong>
            </div>
            <div>
              <span>时间</span>
              <strong>{{ formatDateTime(order.createdAt) }}</strong>
            </div>
          </div>
        </article>
      </div>

      <el-empty v-else description="没有查询到匹配的订单" />
    </section>
  </div>
</template>

<style scoped>
.compact-page-card__heading {
  margin-bottom: 18px;
}

.query-form-card {
  padding: 22px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.3);
  backdrop-filter: blur(26px);
}

.query-form-card__head {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  align-items: center;
  margin-bottom: 18px;
}

.query-form-card__head p {
  margin: 0;
  color: var(--text-secondary);
}

.query-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 18px;
}

.query-form-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.query-result-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 16px;
}

.result-card {
  padding: 20px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.3);
  backdrop-filter: blur(26px);
}

.result-card__top {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.result-card__top strong,
.result-card__top span {
  display: block;
}

.result-card__top span {
  margin-top: 8px;
  color: var(--text-soft);
  font-size: 12px;
}

.result-card__meta {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 18px;
}

.result-card__meta div {
  padding: 12px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.24);
}

.result-card__meta span,
.result-card__meta strong {
  display: block;
}

.result-card__meta span {
  color: var(--text-soft);
  font-size: 12px;
}

.result-card__meta strong {
  margin-top: 6px;
}

@media (max-width: 860px) {
  .query-form-grid,
  .result-card__meta {
    grid-template-columns: 1fr;
  }

  .query-form-card__head,
  .result-card__top {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
