<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Bell, CreditCard, MagicStick, WalletFilled } from '@element-plus/icons-vue'
import {
  createRecharge,
  fetchMemberProfile,
  getMyRecharges,
  memberProfileState,
  type MemberRechargeItem,
} from '@/api/auth'
import { getMyOrders, type CardKeyRecord, type MemberOrderSummary, type OrderRecord } from '@/api/shop'
import { formatCurrency, formatDateTime, formatOrderStatus } from '@/utils/format'

const ORDER_PAGE_SIZE = 10
const RECHARGE_PAGE_SIZE = 10

const profile = memberProfileState
const loading = ref(false)
const rechargeLoading = ref(false)
const rechargeSubmitting = ref(false)
const rechargeVisible = ref(false)
const orderDetailVisible = ref(false)
const orders = ref<OrderRecord[]>([])
const recharges = ref<MemberRechargeItem[]>([])
const selectedOrder = ref<OrderRecord | null>(null)
const orderSummary = ref<MemberOrderSummary>({
  orderCount: 0,
  totalQuantity: 0,
  totalAmount: 0,
  totalCardKeys: 0,
})
const orderPageIndex = ref(1)
const orderCursorTrail = ref<(string | null)[]>([null])
const orderNextCursor = ref<string | null>(null)
const orderHasMore = ref(false)
const rechargePageIndex = ref(1)
const rechargeCursorTrail = ref<(string | null)[]>([null])
const rechargeNextCursor = ref<string | null>(null)
const rechargeHasMore = ref(false)
const screenshotFile = ref<File | null>(null)

const rechargeForm = reactive({
  amount: '',
  payerRemark: '',
})

const balance = computed(() => Number(profile.value?.balance ?? 0))
const alipayQrUrl = '/api/public/payment/alipay-qr'

function normalizeProfileText(value?: string | null) {
  const text = value?.trim()
  return text ? text : null
}

const displayUsername = computed(() => normalizeProfileText(profile.value?.username))
const displayEmail = computed(() => normalizeProfileText(profile.value?.email))

function formatEnableStatus(status: CardKeyRecord['enableStatus']) {
  return status === 'DISABLED' ? '已停用' : '已启用'
}

function formatRechargeStatus(status: MemberRechargeItem['status']) {
  if (status === 'APPROVED') return '已通过'
  if (status === 'REJECTED') return '已拒绝'
  return '待审核'
}

function rechargeTagType(status: MemberRechargeItem['status']) {
  if (status === 'APPROVED') return 'success'
  if (status === 'REJECTED') return 'danger'
  return 'warning'
}

function openOrderDetail(order: OrderRecord) {
  selectedOrder.value = order
  orderDetailVisible.value = true
}

const totalAmount = computed(() => Number(orderSummary.value.totalAmount ?? 0))
const totalQuantity = computed(() => Number(orderSummary.value.totalQuantity ?? 0))
const totalCardKeys = computed(() => Number(orderSummary.value.totalCardKeys ?? 0))

async function loadOrders(cursor: string | null = orderCursorTrail.value[orderPageIndex.value - 1] ?? null) {
  loading.value = true
  try {
    await fetchMemberProfile()
    const result = await getMyOrders(ORDER_PAGE_SIZE, cursor)
    orders.value = result.page.items
    orderSummary.value = result.summary
    orderNextCursor.value = result.page.nextCursor
    orderHasMore.value = result.page.hasMore
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '订单加载失败')
  } finally {
    loading.value = false
  }
}

async function loadRecharges(cursor: string | null = rechargeCursorTrail.value[rechargePageIndex.value - 1] ?? null) {
  rechargeLoading.value = true
  try {
    const result = await getMyRecharges(RECHARGE_PAGE_SIZE, cursor)
    recharges.value = result.items
    rechargeNextCursor.value = result.nextCursor
    rechargeHasMore.value = result.hasMore
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '充值记录加载失败')
  } finally {
    rechargeLoading.value = false
  }
}

function resetOrderPaging() {
  orderPageIndex.value = 1
  orderCursorTrail.value = [null]
  orderNextCursor.value = null
  orderHasMore.value = false
}

function resetRechargePaging() {
  rechargePageIndex.value = 1
  rechargeCursorTrail.value = [null]
  rechargeNextCursor.value = null
  rechargeHasMore.value = false
}

async function nextOrderPage() {
  if (!orderHasMore.value || !orderNextCursor.value) {
    return
  }
  orderCursorTrail.value.push(orderNextCursor.value)
  orderPageIndex.value += 1
  await loadOrders(orderNextCursor.value)
}

async function prevOrderPage() {
  if (orderPageIndex.value <= 1) {
    return
  }
  orderPageIndex.value -= 1
  await loadOrders(orderCursorTrail.value[orderPageIndex.value - 1] ?? null)
}

async function nextRechargePage() {
  if (!rechargeHasMore.value || !rechargeNextCursor.value) {
    return
  }
  rechargeCursorTrail.value.push(rechargeNextCursor.value)
  rechargePageIndex.value += 1
  await loadRecharges(rechargeNextCursor.value)
}

async function prevRechargePage() {
  if (rechargePageIndex.value <= 1) {
    return
  }
  rechargePageIndex.value -= 1
  await loadRecharges(rechargeCursorTrail.value[rechargePageIndex.value - 1] ?? null)
}

function openRechargeDialog() {
  rechargeForm.amount = ''
  rechargeForm.payerRemark = ''
  screenshotFile.value = null
  rechargeVisible.value = true
}

function handleFileChange(event: Event) {
  const target = event.target as HTMLInputElement
  screenshotFile.value = target.files?.[0] ?? null
}

async function submitRecharge() {
  const amount = Number(rechargeForm.amount)
  if (!Number.isFinite(amount) || amount <= 0) {
    ElMessage.warning('请输入正确的充值金额')
    return
  }
  if (!screenshotFile.value) {
    ElMessage.warning('请上传付款截图')
    return
  }

  const formData = new FormData()
  formData.append('amount', String(amount))
  formData.append('screenshot', screenshotFile.value)
  if (rechargeForm.payerRemark.trim()) {
    formData.append('payerRemark', rechargeForm.payerRemark.trim())
  }

  rechargeSubmitting.value = true
  try {
    await createRecharge(formData)
    ElMessage.success('充值申请已提交，请等待后台审核')
    rechargeVisible.value = false
    resetRechargePaging()
    await loadRecharges(null)
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '充值申请提交失败')
  } finally {
    rechargeSubmitting.value = false
  }
}

onMounted(async () => {
  resetOrderPaging()
  resetRechargePaging()
  await Promise.all([loadOrders(null), loadRecharges(null)])
})
</script>

<template>
  <div class="shell-body">
    <section class="section-shell member-hero page-reveal" :style="{ '--delay': '0.04s' }">
      <div class="member-hero__copy">
        <span class="section-kicker">会员中心</span>
        <h1>余额、订单、卡密与充值审核，都收在一个地方。</h1>
        <p>
          现在的会员中心不再只是订单列表，而是把你的资金状态、购买记录和充值进度整合成一套真正可读的界面。
        </p>
      </div>

      <div class="member-hero__profile">
        <div class="member-hero__identity">
          <span class="soft-chip">
            <el-icon><WalletFilled /></el-icon>
            当前账号
          </span>
          <strong>{{ displayUsername || displayEmail || '会员账号' }}</strong>
          <p>{{ displayEmail || '已绑定会员中心，可统一查看所有当前订单记录。' }}</p>
        </div>
        <button class="hero-action member-hero__recharge" type="button" @click="openRechargeDialog">
          <el-icon><CreditCard /></el-icon>
          立即充值
        </button>
      </div>
    </section>

    <section class="section-shell page-reveal" :style="{ '--delay': '0.08s' }">
      <div class="section-heading">
        <div>
          <span class="section-kicker">资产总览</span>
          <h2>当前账号状态</h2>
        </div>
        <p>余额仍然展示全量状态，订单数量、购买数量、总金额和发放数量会基于后端汇总接口返回，不受分页影响。</p>
      </div>

      <div class="summary-grid">
        <article class="summary-card is-balance">
          <span>当前余额</span>
          <strong>{{ formatCurrency(balance) }}</strong>
        </article>
        <article class="summary-card">
          <span>订单数量</span>
          <strong>{{ orderSummary.orderCount }}</strong>
        </article>
        <article class="summary-card">
          <span>购买总数</span>
          <strong>{{ totalQuantity }}</strong>
        </article>
        <article class="summary-card">
          <span>累计金额</span>
          <strong>{{ formatCurrency(totalAmount) }}</strong>
        </article>
        <article class="summary-card">
          <span>卡密数量</span>
          <strong>{{ totalCardKeys }}</strong>
        </article>
      </div>
    </section>

    <section class="section-shell page-reveal" :style="{ '--delay': '0.12s' }">
      <div class="section-heading">
        <div>
          <span class="section-kicker">订单记录</span>
          <h2>已绑定到账号的订单</h2>
        </div>
        <p>所有当前会员流程下生成的订单都会自动进入这里。现在改成每页 {{ ORDER_PAGE_SIZE }} 条，避免记录太多时页面过长。</p>
      </div>

      <el-skeleton :loading="loading" animated :rows="6">
        <div v-if="orders.length" class="member-orders-grid">
          <article
            v-for="(order, index) in orders"
            :key="order.id"
            class="member-order-card"
            :style="{ '--delay': `${0.14 + index * 0.03}s` }"
          >
            <div class="member-order-card__top">
              <div>
                <strong>{{ order.productTitle }}</strong>
                <span>{{ order.orderNo }}</span>
              </div>
              <el-tag :type="order.status === 'SUCCESS' ? 'success' : 'info'">
                {{ formatOrderStatus(order.status) }}
              </el-tag>
            </div>

            <div class="member-order-card__summary">
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

            <button class="surface-button member-order-card__detail" type="button" @click="openOrderDetail(order)">
              查看详情
            </button>
          </article>
        </div>

        <el-empty v-else description="暂时还没有订单记录" />
      </el-skeleton>

      <div class="record-pager">
        <span>第 {{ orderPageIndex }} 页，每页 {{ ORDER_PAGE_SIZE }} 条</span>
        <div class="record-pager__actions">
          <el-button :disabled="orderPageIndex <= 1 || loading" @click="prevOrderPage">上一页</el-button>
          <el-button :disabled="!orderHasMore || loading" @click="nextOrderPage">下一页</el-button>
        </div>
      </div>
    </section>

    <section class="section-shell page-reveal" :style="{ '--delay': '0.16s' }">
      <div class="section-heading">
        <div>
          <span class="section-kicker">充值审核</span>
          <h2>充值申请与审核记录</h2>
        </div>
        <p>提交充值申请后，这里会显示审核状态与结果。现在也改成分页展示，避免记录太多时拉得很长。</p>
      </div>

      <div v-if="recharges.length" class="recharge-list">
        <article
          v-for="(item, index) in recharges"
          :key="item.id"
          class="recharge-card"
          :style="{ '--delay': `${0.18 + index * 0.03}s` }"
        >
          <div class="recharge-card__top">
            <div>
              <strong>{{ item.requestNo }}</strong>
              <span>{{ formatDateTime(item.createdAt) }}</span>
            </div>
            <el-tag :type="rechargeTagType(item.status)">{{ formatRechargeStatus(item.status) }}</el-tag>
          </div>
          <div class="recharge-card__meta">
            <div>
              <span>金额</span>
              <strong>{{ formatCurrency(item.amount) }}</strong>
            </div>
            <div>
              <span>付款备注</span>
              <strong>{{ item.payerRemark || '-' }}</strong>
            </div>
            <div>
              <span>审核时间</span>
              <strong>{{ item.reviewedAt ? formatDateTime(item.reviewedAt) : '-' }}</strong>
            </div>
          </div>
          <p v-if="item.rejectReason" class="recharge-card__reject">拒绝原因：{{ item.rejectReason }}</p>
        </article>
      </div>
      <el-empty v-else description="暂时还没有充值记录" />

      <div class="record-pager">
        <span>第 {{ rechargePageIndex }} 页，每页 {{ RECHARGE_PAGE_SIZE }} 条</span>
        <div class="record-pager__actions">
          <el-button :disabled="rechargePageIndex <= 1 || rechargeLoading" @click="prevRechargePage">上一页</el-button>
          <el-button :disabled="!rechargeHasMore || rechargeLoading" @click="nextRechargePage">下一页</el-button>
        </div>
      </div>
    </section>

    <section class="member-tips page-reveal" :style="{ '--delay': '0.2s' }">
      <article class="glass-card member-tip-card">
        <span class="section-chip">
          <el-icon><WalletFilled /></el-icon>
          余额支付
        </span>
        <p>当前前台购买统一使用余额支付。充值审核通过后，余额会自动增加到你的账号里。</p>
      </article>

      <article class="glass-card member-tip-card">
        <span class="section-chip">
          <el-icon><Bell /></el-icon>
          实时审核
        </span>
        <p>充值申请会实时推送到后台管理端，管理员审核后，这里的状态也会同步刷新。</p>
      </article>

      <article class="glass-card member-tip-card">
        <span class="section-chip">
          <el-icon><MagicStick /></el-icon>
          统一归档
        </span>
        <p>后续所有新订单都围绕会员体系整理，不再混入旧版兼容入口或历史引导。</p>
      </article>
    </section>

    <el-dialog v-model="rechargeVisible" width="min(960px, 94vw)" title="余额充值申请">
      <div class="recharge-dialog">
        <div class="recharge-dialog__qr">
          <span class="soft-chip">支付宝收款码</span>
          <div class="recharge-dialog__qr-card">
            <img :src="alipayQrUrl" alt="支付宝收款码" />
          </div>
          <p>请先完成转账，再上传付款截图提交审核。审核通过后，余额会自动入账。</p>
        </div>

        <div class="recharge-dialog__form">
          <el-form label-position="top">
            <el-form-item label="充值金额">
              <el-input v-model="rechargeForm.amount" placeholder="请输入充值金额，例如 100" />
            </el-form-item>
            <el-form-item label="付款备注或流水号">
              <el-input
                v-model="rechargeForm.payerRemark"
                type="textarea"
                :rows="4"
                maxlength="200"
                show-word-limit
                placeholder="可以填写付款昵称、转账流水号或其他备注"
              />
            </el-form-item>
            <el-form-item label="付款截图">
              <input class="recharge-file-input" type="file" accept="image/*" @change="handleFileChange" />
              <small v-if="screenshotFile">已选择：{{ screenshotFile.name }}</small>
            </el-form-item>
          </el-form>
        </div>
      </div>

      <template #footer>
        <el-button @click="rechargeVisible = false">取消</el-button>
        <el-button type="primary" :loading="rechargeSubmitting" @click="submitRecharge">提交申请</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="orderDetailVisible"
      width="min(920px, 94vw)"
      :title="selectedOrder ? `订单 ${selectedOrder.orderNo}` : '订单详情'"
    >
      <div v-if="selectedOrder" class="order-detail-dialog">
        <div class="order-detail-dialog__head">
          <div>
            <span class="soft-chip">订单详情</span>
            <h3>{{ selectedOrder.productTitle }}</h3>
            <p>{{ formatDateTime(selectedOrder.createdAt) }}</p>
          </div>
          <el-tag :type="selectedOrder.status === 'SUCCESS' ? 'success' : 'info'">
            {{ formatOrderStatus(selectedOrder.status) }}
          </el-tag>
        </div>

        <div class="order-detail-dialog__meta">
          <div>
            <span>订单号</span>
            <strong>{{ selectedOrder.orderNo }}</strong>
          </div>
          <div>
            <span>数量</span>
            <strong>{{ selectedOrder.quantity }}</strong>
          </div>
          <div>
            <span>金额</span>
            <strong>{{ formatCurrency(selectedOrder.totalAmount) }}</strong>
          </div>
        </div>

        <div class="member-order-card__keys">
          <div class="member-order-card__keys-head">
            <strong>卡密列表</strong>
            <span class="ghost-pill">{{ selectedOrder.cardKeys?.length ?? 0 }} 条</span>
          </div>

          <div v-if="selectedOrder.cardKeys?.length" class="card-key-list">
            <div v-for="item in selectedOrder.cardKeys" :key="`${selectedOrder.id}-${item.cardKey}`" class="card-key-item">
              <strong>{{ item.cardKey }}</strong>
              <span :class="['card-key-item__status', { 'is-disabled': item.enableStatus === 'DISABLED' }]">
                {{ formatEnableStatus(item.enableStatus) }}
              </span>
            </div>
          </div>
          <div v-else class="card-key-empty">当前订单还没有可展示的卡密。</div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.member-hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(320px, 360px);
  gap: 22px;
}

.member-hero__copy {
  display: grid;
  align-content: center;
  gap: 18px;
}

.member-hero__copy h1 {
  margin: 0;
  font-size: clamp(34px, 5vw, 56px);
  line-height: 1.06;
}

.member-hero__copy p {
  margin: 0;
  max-width: 640px;
  color: var(--text-secondary);
  line-height: 1.9;
}

.member-hero__profile {
  padding: 24px;
  border-radius: 28px;
  background:
    radial-gradient(circle at top right, rgba(255, 213, 232, 0.24), transparent 30%),
    linear-gradient(180deg, rgba(255, 251, 253, 0.94), rgba(248, 244, 255, 0.9));
  border: 1px solid rgba(255, 255, 255, 0.86);
  box-shadow: 0 22px 54px rgba(108, 85, 135, 0.14);
}

.member-hero__identity strong,
.member-hero__identity p {
  display: block;
}

.member-hero__identity strong {
  margin-top: 14px;
  font-size: 28px;
}

.member-hero__identity p {
  margin: 10px 0 0;
  color: var(--text-secondary);
  line-height: 1.82;
}

.member-hero__recharge {
  width: 100%;
  margin-top: 20px;
}

.summary-grid,
.member-orders-grid,
.member-tips,
.recharge-list {
  display: grid;
  gap: 16px;
}

.summary-grid {
  grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
}

.summary-card,
.member-order-card,
.recharge-card {
  padding: 22px;
  border-radius: 26px;
  background: rgba(255, 251, 253, 0.72);
  border: 1px solid rgba(255, 255, 255, 0.84);
  box-shadow: 0 16px 34px rgba(110, 86, 137, 0.1);
}

.summary-card span,
.summary-card strong {
  display: block;
}

.summary-card span {
  color: var(--text-muted);
  font-size: 12px;
}

.summary-card strong {
  margin-top: 10px;
  font-size: 30px;
}

.summary-card.is-balance {
  background:
    radial-gradient(circle at top right, rgba(255, 213, 232, 0.2), transparent 36%),
    rgba(255, 248, 251, 0.84);
}

.member-orders-grid {
  grid-template-columns: 1fr;
}

.member-order-card,
.recharge-card {
  display: grid;
  align-items: center;
  gap: 14px;
  padding: 16px 18px;
  border-radius: 22px;
}

.member-order-card__top,
.member-order-card__keys-head,
.card-key-item,
.recharge-card__top,
.order-detail-dialog__head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.member-order-card {
  grid-template-columns: minmax(240px, 1.5fr) minmax(220px, 1.1fr) auto;
}

.member-order-card__top strong,
.member-order-card__top span,
.recharge-card__top strong,
.recharge-card__top span {
  display: block;
}

.member-order-card__top span,
.recharge-card__top span {
  margin-top: 8px;
  color: var(--text-muted);
  font-size: 12px;
}

.member-order-card__summary,
.recharge-card__meta {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.member-order-card__summary div,
.recharge-card__meta div {
  padding: 10px 12px;
  border-radius: 16px;
  background: rgba(255, 247, 251, 0.62);
  border: 1px solid rgba(255, 255, 255, 0.72);
}

.member-order-card__summary span,
.member-order-card__summary strong,
.recharge-card__meta span,
.recharge-card__meta strong {
  display: block;
}

.member-order-card__summary span,
.recharge-card__meta span {
  color: var(--text-muted);
  font-size: 12px;
}

.member-order-card__detail {
  width: fit-content;
  min-width: 108px;
}

.recharge-card {
  grid-template-columns: minmax(220px, 1.1fr) minmax(0, 1.6fr);
}

.recharge-card__top {
  align-items: center;
}

.member-order-card__keys {
  padding-top: 18px;
  border-top: 1px solid rgba(235, 218, 230, 0.84);
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

.card-key-empty,
.recharge-card__reject {
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(255, 251, 253, 0.8);
  border: 1px dashed rgba(233, 205, 219, 0.88);
  color: var(--text-secondary);
}

.record-pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 18px;
}

.record-pager span {
  color: var(--text-secondary);
  font-size: 13px;
}

.record-pager__actions {
  display: flex;
  gap: 10px;
}

.recharge-card__reject {
  grid-column: 1 / -1;
}

.member-tips {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.member-tip-card p {
  margin: 16px 0 0;
  color: var(--text-secondary);
  line-height: 1.85;
}

.recharge-dialog {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 18px;
}

.recharge-dialog__qr,
.recharge-dialog__form {
  padding: 22px;
  border-radius: 28px;
  background:
    radial-gradient(circle at top right, rgba(255, 213, 232, 0.2), transparent 30%),
    rgba(255, 251, 253, 0.78);
  border: 1px solid rgba(255, 255, 255, 0.84);
}

.recharge-dialog__qr-card {
  margin-top: 14px;
  padding: 18px;
  border-radius: 24px;
  background: #fff;
  box-shadow: inset 0 0 0 1px rgba(245, 220, 232, 0.92);
}

.recharge-dialog__qr-card img {
  display: block;
  width: 100%;
  border-radius: 16px;
  object-fit: contain;
}

.recharge-dialog__qr p {
  margin: 14px 0 0;
  color: var(--text-secondary);
  line-height: 1.8;
}

.order-detail-dialog {
  display: grid;
  gap: 18px;
}

.order-detail-dialog__head h3,
.order-detail-dialog__head p {
  display: block;
}

.order-detail-dialog__head h3 {
  margin: 14px 0 0;
}

.order-detail-dialog__head p {
  margin: 8px 0 0;
  color: var(--text-muted);
}

.order-detail-dialog__meta {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.order-detail-dialog__meta div {
  padding: 14px;
  border-radius: 18px;
  background: rgba(255, 247, 251, 0.78);
  border: 1px solid rgba(255, 255, 255, 0.84);
}

.order-detail-dialog__meta span,
.order-detail-dialog__meta strong {
  display: block;
}

.order-detail-dialog__meta span {
  color: var(--text-muted);
  font-size: 12px;
}

.recharge-file-input {
  width: 100%;
}

@media (max-width: 980px) {
  .member-hero,
  .recharge-dialog,
  .member-tips {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .member-order-card,
  .recharge-card {
    grid-template-columns: 1fr;
  }

  .member-order-card__summary,
  .recharge-card__meta,
  .order-detail-dialog__meta {
    grid-template-columns: 1fr;
  }

  .member-order-card__top,
  .member-order-card__keys-head,
  .card-key-item,
  .recharge-card__top,
  .record-pager,
  .order-detail-dialog__head {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
