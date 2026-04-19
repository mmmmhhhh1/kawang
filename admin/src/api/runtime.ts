import { adminHttp, type ApiResponse } from './http'

export type RuntimeOverview = {
  generatedAt: string
  health: {
    serviceStatus: string
    databaseStatus: string
    redisStatus: string
  }
  orders: {
    reservationSuccess: number
    reservationFailure: number
    reservationRollback: number
    reservationRecover: number
    orderSuccess: number
    orderFailure: number
    averageSuccessDurationMs: number
  }
  rateLimit: {
    allowedTotal: number
    blockedTotal: number
  }
  cache: {
    productBaseHitRate: number
    productStatsHitRate: number
    noticeHitRate: number
  }
  balance: {
    debitSuccess: number
    debitConflict: number
    rechargeDuplicate: number
    refundDuplicate: number
  }
  process: {
    uptimeSeconds: number
    systemCpuUsage: number
    processCpuUsage: number
    heapUsedBytes: number
    heapMaxBytes: number
    heapUsagePercent: number
  }
}

export type RuntimeMetricItem = {
  key: string
  label: string
  formattedValue: string
}

export type RuntimeMetricSection = {
  key: string
  title: string
  items: RuntimeMetricItem[]
}

export type RuntimeDetails = {
  generatedAt: string
  sections: RuntimeMetricSection[]
}

export async function getRuntimeOverview() {
  const response = await adminHttp.get<ApiResponse<RuntimeOverview>>('/runtime/overview')
  return response.data.data
}

export async function getRuntimeDetails(keys?: string[]) {
  const response = await adminHttp.get<ApiResponse<RuntimeDetails>>('/runtime/details', {
    params: keys && keys.length ? { keys: keys.join(',') } : undefined,
  })
  return response.data.data
}
