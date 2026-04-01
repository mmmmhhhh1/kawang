import { adminHttp, type ApiResponse } from './http'

export type AccountRecord = {
  id: number
  productId: number
  productTitle: string
  accountNameMasked: string
  status: 'AVAILABLE' | 'ASSIGNED' | 'DISABLED'
  assignedOrderId: number | null
  assignedAt: string | null
  createdAt: string
}

export type BatchAccountPayload = {
  productId: number
  items: Array<{
    accountName: string
    secret: string
    note: string
  }>
}

export async function getAccounts(productId?: number, status?: string) {
  const response = await adminHttp.get<ApiResponse<AccountRecord[]>>('/accounts', {
    params: {
      productId,
      status: status || undefined,
    },
  })
  return response.data.data
}

export async function createAccounts(payload: BatchAccountPayload) {
  const response = await adminHttp.post<ApiResponse<AccountRecord[]>>('/accounts/batch', payload)
  return response.data.data
}

export async function updateAccountStatus(id: number, status: 'AVAILABLE' | 'DISABLED') {
  const response = await adminHttp.patch<ApiResponse<AccountRecord>>(`/accounts/${id}/status`, { status })
  return response.data.data
}
