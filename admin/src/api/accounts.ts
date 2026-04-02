import { adminHttp, type ApiResponse } from './http'

export type AccountRecord = {
  id: number
  productId: number
  productTitle: string
  cardKey: string
  saleStatus: 'UNSOLD' | 'SOLD'
  enableStatus: 'ENABLED' | 'DISABLED'
  assignedOrderId: number | null
  assignedAt: string | null
  createdAt: string
}

export type BatchAccountPayload = {
  productId: number
  items: Array<{
    cardKey: string
    note: string
  }>
}

export async function getAccounts(productId?: number, saleStatus?: string, enableStatus?: string) {
  const response = await adminHttp.get<ApiResponse<AccountRecord[]>>('/accounts', {
    params: {
      productId,
      saleStatus: saleStatus || undefined,
      enableStatus: enableStatus || undefined,
    },
  })
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