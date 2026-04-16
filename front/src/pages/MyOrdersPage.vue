<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Bell, Document, UserFilled } from '@element-plus/icons-vue'
import {
  createRecharge,
  fetchMemberProfile,
  getMyRecharges,
  memberProfileState,
  type MemberRechargeItem,
} from '@/api/auth'
import { getMyOrders, type CardKeyRecord, type OrderRecord } from '@/api/shop'
import { formatCurrency, formatDateTime, formatOrderStatus } from '@/utils/format'

const profile = memberProfileState
const loading = ref(false)
const rechargeLoading = ref(false)
const rechargeSubmitting = ref(false)
const rechargeVisible = ref(false)
const orders = ref<OrderRecord[]>([])
const recharges = ref<MemberRechargeItem[]>([])
const rechargeCursor = ref<string | null>(null)
const rechargeHasMore = ref(false)
const screenshotFile = ref<File | null>(null)

const rechargeForm = reactive({
  amount: '',
  payerRemark: '',
})

const totalAmount = computed(() => orders.value.reduce((sum, item) => sum + Number(item.totalAmount ?? 0), 0))
const totalQuantity = computed(() => orders.value.reduce((sum, item) => sum + Number(item.quantity ?? 0), 0))
const totalCardKeys = computed(() => orders.value.reduce((sum, item) => sum + (item.cardKeys?.length ?? 0), 0))
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

async function loadOrders() {
  loading.value = true
  try {
    await fetchMemberProfile()
    orders.value = await getMyOrders()
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '订单加载失败')
  } finally {
    loading.value = false
  }
}

async function loadRecharges(reset = false) {
  rechargeLoading.value = true
  try {
    const result = await getMyRecharges(10, reset ? null : rechargeCursor.value)
    recharges.value = reset ? result.items : [...recharges.value, ...result.items]
    rechargeCursor.value = result.nextCursor
    rechargeHasMore.value = result.hasMore
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '充值记录加载失败')
  } finally {
    rechargeLoading.value = false
  }
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
    await loadRecharges(true)
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message ?? '充值申请提交失败')
  } finally {
    rechargeSubmitting.value = false
  }
}

onMounted(async () => {
  await Promise.all([loadOrders(), loadRecharges(true)])
})
</script>

<template>
  <div class="shell-body">
    <section class="section-shell member-page-card">
      <div class="section-heading member-page-card__heading">
        <div>
          <span class="section-kicker">会员中心</span>
          <h2>我的订单与余额</h2>
        </div>
        <p>这里可以查看当前余额、历史订单和充值申请进度。</p>
      </div>

      <div class="member-summary">
        <article class="member-summary__item">
          <span>当前账号</span>
          <div class="member-summary__identity">
            <strong v-if="displayUsername">{{ displayUsername }}</strong>
            <small v-if="displayEmail">{{ displayEmail }}</small>
            <strong v-if="!displayUsername && !displayEmail">-</strong>
          </div>
        </article>
        <article class="member-summary__item is-balance">
          <span>当前余额</span>
          <strong>{{ formatCurrency(balance) }}</strong>
          <el-button type="primary" size="small" @click="openRechargeDialog">去充值</el-button>
        </article>
        <article class="member-summary__item">
          <span>订单数</span>
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
          <span>卡密数量</span>
          <strong>{{ totalCardKeys }}</strong>
        </article>
      </div>
    </section>

    <section class="section-shell member-page-card">
      <div class="section-heading member-page-card__heading">
        <div>
          <span class="section-kicker">订单</span>
          <h2>关联订单</h2>
        </div>
        <p>使用余额支付的订单会自动绑定到当前会员账号。</p>
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
              </div>

              <div v-if="order.cardKeys?.length" class="card-key-list">
                <div v-for="item in order.cardKeys" :key="`${order.id}-${item.cardKey}`" class="card-key-item">
                  <strong>{{ item.cardKey }}</strong>
                  <span :class="['card-key-item__status', { 'is-disabled': item.enableStatus === 'DISABLED' }]">
                    {{ formatEnableStatus(item.enableStatus) }}
                  </span>
                </div>
              </div>
              <div v-else class="card-key-empty">该订单暂时还没有卡密。</div>
            </div>
          </article>
        </div>

        <el-empty v-else description="暂时还没有订单" />
      </el-skeleton>
    </section>

    <section class="section-shell member-page-card">
      <div class="section-heading member-page-card__heading">
        <div>
          <span class="section-kicker">充值</span>
          <h2>充值记录</h2>
        </div>
        <p>提交充值申请后，等待后台审核通过即可自动入账。</p>
      </div>

      <div v-if="recharges.length" class="recharge-list">
        <article v-for="item in recharges" :key="item.id" class="recharge-card">
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

      <div v-if="rechargeHasMore" class="recharge-load-more">
        <el-button :loading="rechargeLoading" @click="loadRecharges(false)">加载更多</el-button>
      </div>
    </section>

    <section class="member-tips">
      <article class="glass-card member-tip-card">
        <span class="section-chip">
          <el-icon><Bell /></el-icon>
          余额支付
        </span>
        <p>新订单仅支持余额支付，充值审核通过后会自动增加会员余额。</p>
      </article>

      <article class="glass-card member-tip-card">
        <span class="section-chip">
          <el-icon><Document /></el-icon>
          游客查单
        </span>
        <p>历史游客订单仍可在查单页查询，但新订单需要登录会员账号后才能查看。</p>
      </article>

      <article class="glass-card member-tip-card">
        <span class="section-chip">
          <el-icon><UserFilled /></el-icon>
          人工审核
        </span>
        <p>充值申请会实时推送到后台，只有审核通过后余额才会到账。</p>
      </article>
    </section>

    <el-dialog v-model="rechargeVisible" width="min(920px, 94vw)" title="余额充值申请">
      <div class="recharge-dialog">
        <div class="recharge-dialog__qr">
          <span class="soft-chip">支付宝收款码</span>
          <div class="recharge-dialog__qr-card">
            <img :src="alipayQrUrl" alt="支付宝收款码" />
          </div>
          <p>请先完成转账，再上传付款截图提交审核。</p>
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
                placeholder="可填写付款昵称、转账流水号或备注信息"
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
  </div>
</template>

<style scoped>
.member-page-card__heading {
  margin-bottom: 18px;
}

.member-summary,
.member-orders-grid,
.member-tips,
.recharge-list {
  display: grid;
  gap: 16px;
}

.member-summary {
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
}

.member-summary__item,
.member-order-card,
.recharge-card {
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

.member-summary__item.is-balance {
  background: rgba(235, 246, 255, 0.18);
}

.member-summary__identity {
  display: grid;
  gap: 6px;
  margin-top: 8px;
}

.member-summary__identity small {
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.5;
  word-break: break-all;
}

.member-orders-grid {
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
}

.member-order-card__top,
.member-order-card__keys-head,
.card-key-item,
.recharge-card__top {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
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
  color: var(--text-soft);
  font-size: 12px;
}

.member-order-card__meta,
.recharge-card__meta {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 18px;
}

.member-order-card__meta div,
.recharge-card__meta div {
  padding: 12px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.24);
}

.member-order-card__meta span,
.member-order-card__meta strong,
.recharge-card__meta span,
.recharge-card__meta strong {
  display: block;
}

.member-order-card__meta span,
.recharge-card__meta span {
  color: var(--text-soft);
  font-size: 12px;
}

.member-order-card__keys {
  margin-top: 18px;
  padding-top: 18px;
  border-top: 1px solid rgba(255, 255, 255, 0.18);
}

.card-key-list {
  display: grid;
  gap: 10px;
  margin-top: 14px;
}

.card-key-item {
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

.card-key-empty,
.recharge-card__reject {
  margin-top: 14px;
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.08);
  border: 1px dashed rgba(255, 255, 255, 0.24);
  color: var(--text-secondary);
}

.recharge-load-more {
  display: flex;
  justify-content: center;
  margin-top: 18px;
}

.member-tips {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.member-tip-card p {
  margin: 16px 0 0;
  color: var(--text-secondary);
  line-height: 1.8;
}

.recharge-dialog {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 18px;
}

.recharge-dialog__qr,
.recharge-dialog__form {
  padding: 20px;
  border-radius: 24px;
  background: rgba(244, 249, 255, 0.82);
  border: 1px solid rgba(214, 228, 243, 0.92);
}

.recharge-dialog__qr-card {
  margin-top: 14px;
  padding: 18px;
  border-radius: 20px;
  background: #fff;
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
  line-height: 1.7;
}

.recharge-file-input {
  width: 100%;
}

@media (max-width: 920px) {
  .member-order-card__meta,
  .recharge-card__meta,
  .member-tips,
  .recharge-dialog {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .member-order-card__top,
  .member-order-card__keys-head,
  .card-key-item,
  .recharge-card__top {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>