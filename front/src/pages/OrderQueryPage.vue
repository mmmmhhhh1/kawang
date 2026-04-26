<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { DocumentCopy, Search } from '@element-plus/icons-vue'
import { memberProfileState } from '@/api/auth'
import { queryOrders, type CardKeyRecord, type OrderRecord } from '@/api/shop'
import { formatCurrency, formatDateTime, formatOrderStatus } from '@/utils/format'

const profile = memberProfileState
const loading = ref(false)
const queried = ref(false)
const orders = ref<OrderRecord[]>([])
const lookupSecretPattern = /^[A-Za-z0-9]{6,20}$/

const form = reactive({
  buyerContact: '',
  lookupSecret: '',
})

const totalAmount = computed(() => orders.value.reduce((sum, item) => sum + Number(item.totalAmount ?? 0), 0))
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

async function handleQuery() {
  const buyerContact = form.buyerContact.trim()
  const lookupSecret = form.lookupSecret.trim()
  if (!buyerContact) {
    ElMessage.warning('请先输入联系方式')
    return
  }
  if (!lookupSecretPattern.test(lookupSecret)) {
    ElMessage.warning('查单密码需为 6 到 20 位字母或数字')
    return
  }

  loading.value = true
  try {
    orders.value = await queryOrders({
      buyerContact,
      lookupSecret,
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
    <section class="section-shell query-hero page-reveal" :style="{ '--delay': '0.04s' }">
      <div class="query-hero__copy">
        <span class="section-kicker">订单查询</span>
        <h1>查询订单</h1>
      </div>

      <div class="query-hero__panel">
        <el-form label-position="top">
          <el-form-item label="联系方式">
            <el-input v-model="form.buyerContact" maxlength="64" clearable />
          </el-form-item>
          <el-form-item label="查单密码">
            <el-input v-model="form.lookupSecret" maxlength="20" show-password clearable />
          </el-form-item>
        </el-form>

        <div class="query-hero__actions">
          <button class="primary-action" type="button" :disabled="loading" @click="handleQuery">
            <el-icon><Search /></el-icon>
            {{ loading ? '查询中...' : '查询' }}
          </button>
          <router-link v-if="profile" class="secondary-action" to="/orders/me">我的订单</router-link>
          <router-link v-else class="secondary-action" to="/login">登录</router-link>
        </div>
      </div>
    </section>

    <section v-if="queried" class="section-shell page-reveal" :style="{ '--delay': '0.1s' }">
      <div class="section-heading">
        <div>
          <span class="section-kicker">查询结果</span>
          <h2>找到 {{ orders.length }} 笔订单</h2>
        </div>
      </div>

      <div v-if="orders.length" class="result-grid">
        <article
          v-for="(order, index) in orders"
          :key="order.id"
          class="result-card"
          :style="{ '--delay': `${0.12 + index * 0.03}s` }"
        >
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

          <div class="result-card__keys">
            <div class="result-card__keys-head">
              <strong>卡密</strong>
              <button
                v-if="order.cardKeys?.length"
                class="secondary-action result-card__copy"
                type="button"
                @click="copyCardKeys(order.cardKeys)"
              >
                <el-icon><DocumentCopy /></el-icon>
                复制
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
            <div v-else class="card-key-empty">暂无卡密</div>
          </div>
        </article>
      </div>

      <el-empty v-else description="没有查询结果" />
    </section>
  </div>
</template>

<style scoped>
.query-hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(360px, 430px);
  gap: 22px;
}

.query-hero__copy {
  display: grid;
  align-content: center;
  gap: 18px;
}

.query-hero__copy h1 {
  margin: 0;
  font-size: clamp(34px, 5vw, 56px);
  line-height: 1.06;
}

.query-hero__panel {
  padding: 24px;
  border-radius: 28px;
  background:
    radial-gradient(circle at top right, rgba(255, 213, 232, 0.24), transparent 30%),
    linear-gradient(180deg, rgba(255, 251, 253, 0.94), rgba(248, 244, 255, 0.9));
  border: 1px solid rgba(255, 255, 255, 0.86);
  box-shadow: 0 22px 54px rgba(108, 85, 135, 0.14);
}

.query-hero__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 18px;
}

.result-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 16px;
}

.result-card {
  display: grid;
  gap: 18px;
  padding: 22px;
  border-radius: 26px;
  background: rgba(255, 251, 253, 0.72);
  border: 1px solid rgba(255, 255, 255, 0.84);
  box-shadow: 0 16px 34px rgba(110, 86, 137, 0.1);
}

.result-card__top,
.result-card__keys-head,
.card-key-item {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.result-card__top strong,
.result-card__top span {
  display: block;
}

.result-card__top span {
  margin-top: 8px;
  color: var(--text-muted);
  font-size: 12px;
}

.result-card__meta {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.result-card__meta div {
  padding: 12px;
  border-radius: 18px;
  background: rgba(255, 247, 251, 0.78);
  border: 1px solid rgba(255, 255, 255, 0.84);
}

.result-card__meta span,
.result-card__meta strong {
  display: block;
}

.result-card__meta span {
  color: var(--text-muted);
  font-size: 12px;
}

.result-card__keys {
  padding-top: 18px;
  border-top: 1px solid rgba(235, 218, 230, 0.84);
}

.result-card__copy {
  padding-inline: 12px;
}

.card-key-list {
  display: grid;
  gap: 10px;
  margin-top: 14px;
}

.card-key-item {
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.86);
}

.card-key-item strong {
  line-height: 1.7;
  word-break: break-all;
}

.card-key-item__status {
  flex-shrink: 0;
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(203, 234, 216, 0.82);
  color: var(--success);
  font-size: 12px;
}

.card-key-item__status.is-disabled {
  background: rgba(248, 223, 233, 0.9);
  color: var(--danger);
}

.card-key-empty {
  margin-top: 14px;
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(255, 251, 253, 0.8);
  border: 1px dashed rgba(233, 205, 219, 0.88);
  color: var(--text-secondary);
}

@media (max-width: 960px) {
  .query-hero {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .result-card__meta {
    grid-template-columns: 1fr;
  }

  .result-card__top,
  .result-card__keys-head,
  .card-key-item {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
