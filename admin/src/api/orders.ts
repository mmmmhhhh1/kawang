import { adminHttp, type ApiResponse, type CursorPageResponse } from './http'
import type { AccountUsedStatus } from './accounts'

export type OrderRecord = {
  id: number
  orderNo: string
  productId: number
  productTitleSnapshot: string
  quantity: number
  unitPrice: number
  totalAmount: number
  buyerName: string
  buyerContact: string
  buyerRemark: string
  status: 'SUCCESS' | 'CLOSED'
  closedReason: string | null
  createdAt: string
}

export type OrderCardKeyRecord = {
  accountId?: number | null
  cardKey: string
  enableStatus?: 'ENABLED' | 'DISABLED' | null
  usedStatus?: AccountUsedStatus | null
}

export type OrderDetail = OrderRecord & {
  cardKeys: OrderCardKeyRecord[]
}

export type OrderCursorQuery = {
  size: number
  cursor?: string | null
  status?: string
  productId?: number
  keyword?: string
}

export async function getOrders(params: OrderCursorQuery) {
  const response = await adminHttp.get<ApiResponse<CursorPageResponse<OrderRecord>>>('/orders', {
    params: {
      size: params.size,
      cursor: params.cursor || undefined,
      status: params.status || undefined,
      productId: params.productId,
      keyword: params.keyword || undefined,
    },
  })
  return response.data.data
}

export async function getOrderDetail(id: number) {
  const response = await adminHttp.get<ApiResponse<OrderDetail>>(`/orders/${id}`)
  return response.data.data
}

export async function closeOrder(id: number, reason: string) {
  const response = await adminHttp.patch<ApiResponse<OrderDetail>>(`/orders/${id}/close`, { reason })
  return response.data.data
}

export async function deleteOrder(id: number) {
  await adminHttp.delete<ApiResponse<null>>(`/orders/${id}`)
}