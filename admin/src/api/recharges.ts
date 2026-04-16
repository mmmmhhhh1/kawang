import { adminHttp, normalizeAdminRequestPath, type ApiResponse, type CursorPageResponse } from './http'

export type RechargeRecord = {
  id: number
  requestNo: string
  userId: number
  username: string | null
  email: string | null
  amount: number
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  payerRemark: string | null
  createdAt: string
  reviewedAt: string | null
}

export type RechargeDetail = RechargeRecord & {
  rejectReason: string | null
  screenshotUrl: string
  reviewedByName: string | null
}

export type RechargeCursorQuery = {
  size: number
  cursor?: string | null
  status?: string
  userKeyword?: string
}

export async function getRechargePage(params: RechargeCursorQuery) {
  const response = await adminHttp.get<ApiResponse<CursorPageResponse<RechargeRecord>>>('/recharges', {
    params: {
      size: params.size,
      cursor: params.cursor || undefined,
      status: params.status || undefined,
      userKeyword: params.userKeyword || undefined,
    },
  })
  return response.data.data
}

export async function getRechargeDetail(id: number) {
  const response = await adminHttp.get<ApiResponse<RechargeDetail>>(`/recharges/${id}`)
  return response.data.data
}

export async function approveRecharge(id: number) {
  const response = await adminHttp.patch<ApiResponse<RechargeDetail>>(`/recharges/${id}/approve`)
  return response.data.data
}

export async function rejectRecharge(id: number, reason: string) {
  const response = await adminHttp.patch<ApiResponse<RechargeDetail>>(`/recharges/${id}/reject`, { reason })
  return response.data.data
}

export async function getRechargeScreenshotBlob(screenshotUrl: string) {
  const response = await adminHttp.get<Blob>(normalizeAdminRequestPath(screenshotUrl), {
    responseType: 'blob',
  })
  return response.data
}