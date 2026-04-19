<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, shallowRef, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Connection, Cpu, DataAnalysis, Histogram, WarningFilled } from '@element-plus/icons-vue'
import {
  getRuntimeDetails,
  getRuntimeOverview,
  type RuntimeMetricSection,
  type RuntimeOverview,
} from '@/api/runtime'

const OVERVIEW_POLL_INTERVAL = 15_000
const DETAIL_SECTION_META = [
  { key: 'health', title: '系统健康' },
  { key: 'orders', title: '订单与预占' },
  { key: 'rate-limit', title: '限流明细' },
  { key: 'cache', title: '缓存' },
  { key: 'balance', title: '余额与入账' },
  { key: 'process', title: 'JVM 与进程' },
]
const DEFAULT_ACTIVE_SECTIONS = ['health', 'orders']

const overviewLoading = ref(false)
const detailsRefreshing = ref(false)
const overview = shallowRef<RuntimeOverview | null>(null)
const detailsGeneratedAt = ref<string | null>(null)
const sectionMap = shallowRef<Record<string, RuntimeMetricSection>>({})
const loadingSectionKeys = ref<string[]>([])
const activeSections = ref<string[]>([...DEFAULT_ACTIVE_SECTIONS])

let overviewTimer: number | null = null
let deferredDetailsTimer: number | null = null

const allSectionKeys = DETAIL_SECTION_META.map((item) => item.key)

const healthCards = computed(() => {
  if (!overview.value) {
    return []
  }
  return [
    { key: 'service', label: '服务状态', value: overview.value.health.serviceStatus },
    { key: 'database', label: 'MySQL', value: overview.value.health.databaseStatus },
    { key: 'redis', label: 'Redis', value: overview.value.health.redisStatus },
  ]
})

const renderedSections = computed(() =>
  DETAIL_SECTION_META.map((meta) => ({
    ...meta,
    section: sectionMap.value[meta.key] ?? null,
    loading: loadingSectionKeys.value.includes(meta.key),
  })),
)

function formatDateTime(value?: string | null) {
  if (!value) {
    return '--'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return date.toLocaleString('zh-CN', { hour12: false })
}

function formatCount(value?: number | null) {
  return Math.round(Number(value ?? 0)).toLocaleString('zh-CN')
}

function formatDuration(value?: number | null) {
  return `${Number(value ?? 0).toFixed(1)} ms`
}

function formatPercent(value?: number | null) {
  return `${Number(value ?? 0).toFixed(1)}%`
}

function formatBytes(bytes?: number | null) {
  const value = Number(bytes ?? 0)
  if (value <= 0) {
    return '0 B'
  }
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let size = value
  let index = 0
  while (size >= 1024 && index < units.length - 1) {
    size /= 1024
    index += 1
  }
  return `${index === 0 ? size.toFixed(0) : size.toFixed(1)} ${units[index]}`
}

function clampPercent(value?: number | null) {
  return `${Math.min(100, Math.max(0, Number(value ?? 0)))}%`
}

function statusTagType(status?: string) {
  if (status === 'UP') return 'success'
  if (status === 'DOWN' || status === 'OUT_OF_SERVICE') return 'danger'
  return 'warning'
}

function setSectionLoading(keys: string[], loading: boolean) {
  const next = new Set(loadingSectionKeys.value)
  keys.forEach((key) => {
    if (loading) {
      next.add(key)
    } else {
      next.delete(key)
    }
  })
  loadingSectionKeys.value = [...next]
}

function mergeSections(sections: RuntimeMetricSection[]) {
  const next = { ...sectionMap.value }
  sections.forEach((section) => {
    next[section.key] = section
  })
  sectionMap.value = next
}

function stopDeferredDetailsLoad() {
  if (deferredDetailsTimer !== null) {
    window.clearTimeout(deferredDetailsTimer)
    deferredDetailsTimer = null
  }
}

async function loadOverview(silent = false) {
  if (!silent) {
    overviewLoading.value = true
  }
  try {
    overview.value = await getRuntimeOverview()
  } catch (error: any) {
    if (!silent) {
      ElMessage.error(error?.response?.data?.message ?? '监控概览加载失败')
    }
  } finally {
    if (!silent) {
      overviewLoading.value = false
    }
  }
}

async function ensureSectionDetails(
  keys: string[],
  options: { silent?: boolean; force?: boolean; markRefreshing?: boolean } = {},
) {
  const normalized = [...new Set(keys)].filter((key) => allSectionKeys.includes(key))
  const targetKeys = options.force ? normalized : normalized.filter((key) => !sectionMap.value[key])
  if (!targetKeys.length) {
    return
  }

  setSectionLoading(targetKeys, true)
  if (options.markRefreshing) {
    detailsRefreshing.value = true
  }

  try {
    const response = await getRuntimeDetails(targetKeys)
    mergeSections(response.sections)
    detailsGeneratedAt.value = response.generatedAt
  } catch (error: any) {
    if (!options.silent) {
      ElMessage.error(error?.response?.data?.message ?? '监控明细加载失败')
    }
  } finally {
    setSectionLoading(targetKeys, false)
    if (options.markRefreshing) {
      detailsRefreshing.value = false
    }
  }
}

function scheduleDefaultDetailsLoad() {
  stopDeferredDetailsLoad()
  const runner = () => {
    deferredDetailsTimer = null
    void ensureSectionDetails(DEFAULT_ACTIVE_SECTIONS, { silent: true })
  }

  const idleWindow = window as Window & { requestIdleCallback?: (callback: IdleRequestCallback) => number }
  if (idleWindow.requestIdleCallback) {
    deferredDetailsTimer = window.setTimeout(() => {
      idleWindow.requestIdleCallback?.(() => runner())
    }, 120)
    return
  }

  deferredDetailsTimer = window.setTimeout(runner, 120)
}

function stopOverviewPolling() {
  if (overviewTimer !== null) {
    window.clearInterval(overviewTimer)
    overviewTimer = null
  }
}

function startOverviewPolling() {
  stopOverviewPolling()
  if (document.visibilityState !== 'visible') {
    return
  }
  overviewTimer = window.setInterval(() => {
    void loadOverview(true)
  }, OVERVIEW_POLL_INTERVAL)
}

function handleVisibilityChange() {
  if (document.visibilityState === 'visible') {
    void loadOverview(true)
    startOverviewPolling()
    return
  }
  stopOverviewPolling()
}

function expandAllSections() {
  activeSections.value = [...allSectionKeys]
  void ensureSectionDetails(allSectionKeys, { silent: true })
}

function collapseAllSections() {
  activeSections.value = []
}

function refreshDetails() {
  const targetKeys = activeSections.value.length ? activeSections.value : DEFAULT_ACTIVE_SECTIONS
  return ensureSectionDetails(targetKeys, { force: true, markRefreshing: true })
}

watch(
  activeSections,
  (keys) => {
    if (!keys.length) {
      return
    }
    void ensureSectionDetails(keys, { silent: true })
  },
  { flush: 'post' },
)

onMounted(async () => {
  await loadOverview()
  scheduleDefaultDetailsLoad()
  startOverviewPolling()
  document.addEventListener('visibilitychange', handleVisibilityChange)
})

onBeforeUnmount(() => {
  stopOverviewPolling()
  stopDeferredDetailsLoad()
  document.removeEventListener('visibilitychange', handleVisibilityChange)
})
</script>

<template>
  <div class="admin-page runtime-page">
    <el-card class="page-card" shadow="never">
      <div class="page-header">
        <div>
          <p>
            这里展示的是给后台看的系统运行概览，不直接暴露原始监控数据。概览会在当前页面可见时每
            15 秒轻量刷新一次，明细按分组懒加载，只在需要时请求对应数据。
          </p>
          <h1>系统监控</h1>
        </div>
        <div class="page-header__actions">
          <el-button :loading="overviewLoading" @click="loadOverview()">刷新概览</el-button>
          <el-button type="primary" :loading="detailsRefreshing" @click="refreshDetails()">刷新明细</el-button>
        </div>
      </div>

      <div v-if="overview" class="runtime-overview">
        <div class="runtime-health-grid">
          <article v-for="item in healthCards" :key="item.key" class="runtime-health-card">
            <span>{{ item.label }}</span>
            <el-tag size="large" :type="statusTagType(item.value)">{{ item.value }}</el-tag>
          </article>
        </div>

        <div class="runtime-summary-grid">
          <article class="runtime-summary-card">
            <div class="runtime-summary-card__icon is-orders">
              <el-icon><Histogram /></el-icon>
            </div>
            <div class="runtime-summary-card__copy">
              <span>订单与预占</span>
              <strong>{{ formatCount(overview.orders.orderSuccess) }} / {{ formatCount(overview.orders.orderFailure) }}</strong>
              <small>成功 / 失败，平均耗时 {{ formatDuration(overview.orders.averageSuccessDurationMs) }}</small>
            </div>
          </article>

          <article class="runtime-summary-card">
            <div class="runtime-summary-card__icon is-rate-limit">
              <el-icon><WarningFilled /></el-icon>
            </div>
            <div class="runtime-summary-card__copy">
              <span>限流状态</span>
              <strong>{{ formatCount(overview.rateLimit.allowedTotal) }} / {{ formatCount(overview.rateLimit.blockedTotal) }}</strong>
              <small>放行 / 拦截总量</small>
            </div>
          </article>

          <article class="runtime-summary-card">
            <div class="runtime-summary-card__icon is-cache">
              <el-icon><DataAnalysis /></el-icon>
            </div>
            <div class="runtime-summary-card__copy">
              <span>缓存命中率</span>
              <strong>{{ formatPercent(overview.cache.productBaseHitRate) }}</strong>
              <small>商品基础命中率，公告 {{ formatPercent(overview.cache.noticeHitRate) }}</small>
            </div>
          </article>

          <article class="runtime-summary-card">
            <div class="runtime-summary-card__icon is-process">
              <el-icon><Cpu /></el-icon>
            </div>
            <div class="runtime-summary-card__copy">
              <span>进程状态</span>
              <strong>{{ formatPercent(overview.process.processCpuUsage) }}</strong>
              <small>应用 CPU，堆内存占用 {{ formatPercent(overview.process.heapUsagePercent) }}</small>
            </div>
          </article>
        </div>

        <div class="runtime-progress-grid">
          <article class="runtime-progress-card">
            <div class="runtime-progress-card__top">
              <span>商品基础缓存命中率</span>
              <strong>{{ formatPercent(overview.cache.productBaseHitRate) }}</strong>
            </div>
            <div class="runtime-progress">
              <span class="runtime-progress__bar" :style="{ width: clampPercent(overview.cache.productBaseHitRate) }" />
            </div>
          </article>

          <article class="runtime-progress-card">
            <div class="runtime-progress-card__top">
              <span>商品统计缓存命中率</span>
              <strong>{{ formatPercent(overview.cache.productStatsHitRate) }}</strong>
            </div>
            <div class="runtime-progress">
              <span class="runtime-progress__bar is-stats" :style="{ width: clampPercent(overview.cache.productStatsHitRate) }" />
            </div>
          </article>

          <article class="runtime-progress-card">
            <div class="runtime-progress-card__top">
              <span>堆内存占用</span>
              <strong>{{ formatPercent(overview.process.heapUsagePercent) }}</strong>
            </div>
            <div class="runtime-progress">
              <span class="runtime-progress__bar is-heap" :style="{ width: clampPercent(overview.process.heapUsagePercent) }" />
            </div>
            <small>{{ formatBytes(overview.process.heapUsedBytes) }} / {{ formatBytes(overview.process.heapMaxBytes) }}</small>
          </article>

          <article class="runtime-progress-card">
            <div class="runtime-progress-card__top">
              <span>运行时长</span>
              <strong>{{ Math.round(overview.process.uptimeSeconds / 60) }} 分钟</strong>
            </div>
            <small>系统 CPU {{ formatPercent(overview.process.systemCpuUsage) }}，应用 CPU {{ formatPercent(overview.process.processCpuUsage) }}</small>
          </article>
        </div>
      </div>

      <div class="runtime-meta">
        <span>概览更新时间：{{ formatDateTime(overview?.generatedAt) }}</span>
        <span>明细更新时间：{{ formatDateTime(detailsGeneratedAt) }}</span>
      </div>

      <el-empty v-if="!overview && !overviewLoading" description="暂时还没有可展示的运行数据" />

      <div class="runtime-details">
        <div class="runtime-details__heading">
          <div>
            <span class="muted">指标明细</span>
            <h3>按模块展开查看</h3>
          </div>
          <div class="runtime-details__tools">
            <div class="runtime-details__legend">
              <span><el-icon><Connection /></el-icon> 仅当前页可见时轮询概览</span>
            </div>
            <div class="runtime-details__actions">
              <el-button text @click="expandAllSections">展开全部</el-button>
              <el-button text @click="collapseAllSections">收起全部</el-button>
            </div>
          </div>
        </div>

        <el-collapse v-model="activeSections">
          <el-collapse-item
            v-for="section in renderedSections"
            :key="section.key"
            :name="section.key"
            :title="section.title"
          >
            <div v-if="section.loading" class="runtime-section-skeleton">
              <el-skeleton animated :rows="4" />
            </div>
            <div v-else-if="section.section" class="runtime-section-grid">
              <article v-for="item in section.section.items" :key="item.key" class="runtime-section-item">
                <span>{{ item.label }}</span>
                <strong>{{ item.formattedValue }}</strong>
              </article>
            </div>
            <div v-else class="runtime-section-placeholder">
              <span>展开后会按需加载这一组指标。</span>
            </div>
          </el-collapse-item>
        </el-collapse>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.runtime-page .page-card .el-card__body {
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.runtime-overview,
.runtime-details {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.runtime-health-grid,
.runtime-summary-grid,
.runtime-progress-grid {
  display: grid;
  gap: 16px;
}

.runtime-health-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.runtime-summary-grid,
.runtime-progress-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.runtime-health-card,
.runtime-summary-card,
.runtime-progress-card,
.runtime-section-item {
  border-radius: 22px;
  border: 1px solid rgba(148, 163, 184, 0.22);
  background: rgba(255, 255, 255, 0.84);
  box-shadow: 0 18px 42px rgba(15, 23, 42, 0.06);
}

.runtime-health-card {
  padding: 18px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.runtime-health-card span {
  color: var(--admin-text-soft);
  font-weight: 600;
}

.runtime-summary-card {
  padding: 18px;
  display: flex;
  gap: 14px;
  align-items: center;
}

.runtime-summary-card__icon {
  width: 46px;
  height: 46px;
  border-radius: 16px;
  display: grid;
  place-items: center;
  color: #fff;
  font-size: 20px;
}

.runtime-summary-card__icon.is-orders {
  background: linear-gradient(135deg, #2563eb, #1d4ed8);
}

.runtime-summary-card__icon.is-rate-limit {
  background: linear-gradient(135deg, #f97316, #ea580c);
}

.runtime-summary-card__icon.is-cache {
  background: linear-gradient(135deg, #0ea5e9, #0284c7);
}

.runtime-summary-card__icon.is-process {
  background: linear-gradient(135deg, #14b8a6, #0f766e);
}

.runtime-summary-card__copy {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.runtime-summary-card__copy span,
.runtime-progress-card span {
  color: var(--admin-text-soft);
  font-weight: 600;
}

.runtime-summary-card__copy strong,
.runtime-progress-card strong {
  font-size: 24px;
  letter-spacing: -0.03em;
}

.runtime-summary-card__copy small,
.runtime-progress-card small,
.runtime-meta,
.runtime-details__legend,
.runtime-section-placeholder {
  color: var(--admin-text-soft);
}

.runtime-progress-card {
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.runtime-progress-card__top {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.runtime-progress {
  position: relative;
  height: 10px;
  border-radius: 999px;
  background: rgba(226, 232, 240, 0.7);
  overflow: hidden;
}

.runtime-progress__bar {
  position: absolute;
  inset: 0 auto 0 0;
  border-radius: inherit;
  background: linear-gradient(90deg, #2563eb, #38bdf8);
}

.runtime-progress__bar.is-stats {
  background: linear-gradient(90deg, #14b8a6, #2dd4bf);
}

.runtime-progress__bar.is-heap {
  background: linear-gradient(90deg, #f97316, #fb7185);
}

.runtime-meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  font-size: 13px;
}

.runtime-details__heading,
.runtime-details__tools {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.runtime-details__heading h3 {
  margin: 6px 0 0;
  font-size: 22px;
}

.runtime-details__legend {
  display: inline-flex;
  gap: 6px;
  align-items: center;
}

.runtime-details__actions {
  display: inline-flex;
  gap: 4px;
  align-items: center;
}

.runtime-section-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
  content-visibility: auto;
  contain-intrinsic-size: 280px;
}

.runtime-section-item {
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.runtime-section-item span {
  color: var(--admin-text-soft);
  font-size: 13px;
}

.runtime-section-item strong {
  font-size: 18px;
}

.runtime-section-skeleton,
.runtime-section-placeholder {
  padding: 8px 4px 4px;
}

@media (max-width: 1280px) {
  .runtime-summary-grid,
  .runtime-progress-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .runtime-health-grid,
  .runtime-summary-grid,
  .runtime-progress-grid {
    grid-template-columns: 1fr;
  }

  .runtime-details__heading,
  .runtime-details__tools {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
