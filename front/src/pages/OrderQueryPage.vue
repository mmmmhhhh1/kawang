<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { DocumentCopy, Lock, Search } from '@element-plus/icons-vue'
import { memberProfileState } from '@/api/auth'
import { queryOrders, type CardKeyRecord, type OrderRecord } from '@/api/shop'
import { formatCurrency, formatDateTime, formatOrderStatus } from '@/utils/format'

const profile = memberProfileState
const loading = ref(false)
const queried = ref(false)
const legacyMode = ref(false)
const orders = ref<OrderRecord[]>([])
const lookupSecretPattern = /^[A-Za-z0-9]{6,20}$/

const form = reactive({
  buyerContact: '',
  lookupSecret: '',
  orderNo: '',
})

const totalAmount = computed(() => orders.value.reduce((sum, item) => sum + Number(item.totalAmount ?? 0), 0))

function formatEnableStatus(status: CardKeyRecord['enableStatus']) {
  return status === 'DISABLED' ? '已停用' : '可用'
}

function useLookupMode() {
  legacyMode.value = false
  form.orderNo = ''
  queried.value = false
}

function useLegacyMode() {
  legacyMode.value = true
  form.lookupSecret = ''
  queried.value = false
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
  if (!buyerContact) {
    ElMessage.warning('请先输入联系方式')
    return
  }

  if (legacyMode.value) {
    if (!form.orderNo.trim()) {
      ElMessage.warning('旧订单兼容查询需要输入订单号')
      return
    }
  } else if (!lookupSecretPattern.test(form.lookupSecret.trim())) {
    ElMessage.warning('查单密码需为 6-20 位字母或数字')
    return
  }

  loading.value = true
  try {
    orders.value = await queryOrders({
      buyerContact,
      lookupSecret: legacyMode.value ? undefined : form.lookupSecret.trim(),
      orderNo: legacyMode.value ? form.orderNo.trim() : undefined,
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
        <p>新订单默认使用“联系方式 + 查单密码”查询，旧订单仍兼容“联系方式 + 订单号”。</p>
      </div>

      <div class="query-form-card">
        <div class="query-form-card__head">
          <span class="section-chip">
            <el-icon><Lock /></el-icon>
            安全查单
          </span>
          <p>新订单会直接显示已分配的卡密，请妥善保管查单凭证。</p>
        </div>

        <div class="query-mode-switch">
          <button
            class="query-mode-switch__item"
            :class="{ 'is-active': !legacyMode }"
            type="button"
            @click="useLookupMode"
          >
            新订单查询
          </button>
          <button
            class="query-mode-switch__item"
            :class="{ 'is-active': legacyMode }"
            type="button"
            @click="useLegacyMode"
          >
            旧订单兼容
          </button>
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
          <el-form-item v-if="!legacyMode" label="查单密码">
            <el-input
              v-model="form.lookupSecret"
              maxlength="20"
              show-password
              placeholder="6-20 位字母或数字"
              clearable
            />
          </el-form-item>
          <el-form-item v-else label="订单号">
            <el-input v-model="form.orderNo" maxlength="64" placeholder="例如 OD20260401000001" clearable />
          </el-form-item>
        </el-form>

        <div class="query-query-note" v-if="!legacyMode">
          <strong>新订单查询说明</strong>
          <p>请输入下单时使用的联系方式和查单密码。系统会返回订单信息和本次分配到的卡密。</p>
        </div>
        <div class="query-query-note query-query-note--legacy" v-else>
          <strong>旧订单兼容说明</strong>
          <p>仅旧订单支持“联系方式 + 订单号”兼容查询。若订单属于卡密新流程，请切回查单密码模式。</p>
        </div>

        <div class="query-form-card__actions">
          <button class="primary-action" type="button" :disabled="loading" @click="handleQuery">
            <el-icon><Search /></el-icon>
            {{ loading ? '查询中...' : legacyMode ? '查询旧订单' : '查询订单' }}
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

          <div class="result-card__keys">
            <div class="result-card__keys-head">
              <strong>卡密列表</strong>
              <button
                v-if="order.cardKeys?.length"
                class="secondary-action result-card__copy"
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
            <div v-else class="card-key-empty">该订单没有可展示的卡密，可能是旧账号订单或已无快照。</div>
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
  padding: 24px;
  border-radius: 24px;
  background: linear-gradient(180deg, rgba(243, 249, 255, 0.82), rgba(232, 242, 252, 0.72));
  border: 1px solid rgba(220, 232, 246, 0.96);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.6);
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

.query-mode-switch {
  display: inline-grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 18px;
  padding: 6px;
  border-radius: 18px;
  background: rgba(224, 236, 248, 0.74);
  border: 1px solid rgba(208, 223, 239, 0.92);
}

.query-mode-switch__item {
  min-height: 40px;
  padding: 0 16px;
  border: none;
  border-radius: 14px;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
}

.query-mode-switch__item.is-active {
  background: rgba(255, 255, 255, 0.74);
  color: var(--text-primary);
  box-shadow: 0 10px 18px rgba(135, 154, 176, 0.12);
}

.query-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 18px;
}

.query-query-note {
  margin-top: 4px;
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(223, 237, 252, 0.72);
  border: 1px solid rgba(193, 215, 238, 0.9);
}

.query-query-note--legacy {
  background: rgba(248, 244, 255, 0.72);
  border-color: rgba(214, 202, 240, 0.88);
}

.query-query-note strong,
.query-query-note p {
  display: block;
}

.query-query-note p {
  margin: 8px 0 0;
  color: var(--text-secondary);
  line-height: 1.7;
}

.query-form-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 18px;
}

.query-result-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 16px;
}

.result-card {
  padding: 20px;
  border-radius: 24px;
  background: rgba(240, 247, 255, 0.58);
  border: 1px solid rgba(224, 235, 247, 0.92);
  box-shadow: 0 16px 32px rgba(128, 146, 168, 0.12);
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
  background: rgba(248, 252, 255, 0.84);
  border: 1px solid rgba(225, 235, 246, 0.94);
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

.result-card__keys {
  margin-top: 18px;
  padding-top: 18px;
  border-top: 1px solid rgba(210, 224, 238, 0.94);
}

.result-card__keys-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.result-card__copy {
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
  background: rgba(248, 252, 255, 0.82);
  border: 1px solid rgba(222, 232, 244, 0.96);
}

.card-key-item strong {
  font-size: 14px;
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
  background: rgba(246, 248, 252, 0.82);
  border: 1px dashed rgba(206, 217, 231, 0.96);
  color: var(--text-secondary);
  line-height: 1.7;
}

@media (max-width: 860px) {
  .query-form-grid,
  .result-card__meta {
    grid-template-columns: 1fr;
  }

  .query-form-card__head,
  .result-card__top,
  .result-card__keys-head,
  .card-key-item {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
