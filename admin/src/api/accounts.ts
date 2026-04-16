import { adminHttp, type ApiResponse, type CursorPageResponse } from './http'

export type AccountUsedStatus = 'USED' | 'UNUSED'

export type AccountRecord = {
  id: number
  productId: number
  productTitle: string
  cardKey: string
  saleStatus: 'UNSOLD' | 'SOLD'
  enableStatus: 'ENABLED' | 'DISABLED'
  usedStatus: AccountUsedStatus
  assignedOrderId: number | null
  assignedOrderNo?: string | null
  assignedAt: string | null
  createdAt: string
}

export type AccountDetail = AccountRecord & {
  note: string | null
  updatedAt?: string | null
}

export type BatchAccountPayload = {
  productId: number
  items: Array<{
    cardKey: string
    note: string
  }>
}

export type AccountCursorQuery = {
  size: number
  cursor?: string | null
  productId?: number
  saleStatus?: string
  enableStatus?: string
  usedStatus?: AccountUsedStatus | ''
  keyword?: string
}

export async function getAccounts(productId?: number, saleStatus?: string, enableStatus?: string) {
  const response = await adminHttp.get<ApiResponse<AccountRecord[]>>('/accounts/all', {
    params: {
      productId,
      saleStatus: saleStatus || undefined,
      enableStatus: enableStatus || undefined,
    },
  })
  return response.data.data
}

export async function getAccountPage(params: AccountCursorQuery) {
  const response = await adminHttp.get<ApiResponse<CursorPageResponse<AccountRecord>>>('/accounts', {
    params: {
      size: params.size,
      cursor: params.cursor || undefined,
      productId: params.productId,
      saleStatus: params.saleStatus || undefined,
      enableStatus: params.enableStatus || undefined,
      usedStatus: params.usedStatus || undefined,
      keyword: params.keyword || undefined,
    },
  })
  return response.data.data
}

export async function getAccountDetail(id: number) {
  const response = await adminHttp.get<ApiResponse<AccountDetail>>(`/accounts/${id}`)
  return response.data.data
}

export async function createAccounts(payload: BatchAccountPayload) {
  const response = await adminHttp.post<ApiResponse<AccountRecord[]>>('/accounts/batch', payload)
  return response.data.data
}

export async function updateAccountStatus(id: number, enableStatus: 'ENABLED' | 'DISABLED') {
  const response = await adminHttp.patch<ApiResponse<AccountRecord>>(`/accounts/${id}/status`, { enableStatus })
  return response.data.data
}

export async function updateAccountUsedStatus(id: number, usedStatus: AccountUsedStatus) {
  const response = await adminHttp.patch<ApiResponse<AccountRecord>>(`/accounts/${id}/used-status`, { usedStatus })
  return response.data.data
}

export async function bulkDisableAccounts(scope: 'PRODUCT' | 'ALL', productId?: number) {
  const response = await adminHttp.patch<ApiResponse<number>>('/accounts/bulk-disable', {
    scope,
    productId,
  })
  return response.data.data
}

export async function bulkEnableAccounts(scope: 'PRODUCT' | 'ALL', productId?: number) {
  const response = await adminHttp.patch<ApiResponse<number>>('/accounts/bulk-enable', {
    scope,
    productId,
  })
  return response.data.data
}

export async function deleteAccount(id: number) {
  await adminHttp.delete<ApiResponse<void>>(`/accounts/${id}`)
}