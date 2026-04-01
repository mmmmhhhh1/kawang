import { adminHttp, type ApiResponse } from './http'

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

export type OrderDetail = OrderRecord & {
  accounts: string[]
}

export type PageResult<T> = {
  items: T[]
  total: number
  page: number
  size: number
}

export async function getOrders(params: {
  page: number
  size: number
  status?: string
  productId?: number
  keyword?: string
}) {
  const response = await adminHttp.get<ApiResponse<PageResult<OrderRecord>>>('/orders', { params })
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
