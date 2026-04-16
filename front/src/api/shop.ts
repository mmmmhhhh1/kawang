import { http, type ApiResponse } from './http'

export type Product = {
  id: number
  sku: string
  title: string
  vendor: string
  planName: string
  description: string
  price: number
  availableStock: number
  soldCount: number
}

export type Notice = {
  id: number
  title: string
  summary: string
  content: string
  publishedAt: string
}

export type CreateOrderPayload = {
  productId: number
  quantity: number
  remark: string
}

export type CardKeyRecord = {
  cardKey: string
  enableStatus: 'ENABLED' | 'DISABLED'
}

export type OrderResult = {
  orderNo: string
  status: string
  quantity: number
  totalAmount: number
  message: string
  cardKeys: CardKeyRecord[]
  remainingBalance: number
}

export type QueryOrdersPayload = {
  buyerContact: string
  lookupSecret?: string
  orderNo?: string
}

export type OrderRecord = {
  id: number
  orderNo: string
  productTitle: string
  quantity: number
  totalAmount: number
  status: 'SUCCESS' | 'CLOSED'
  createdAt: string
  cardKeys: CardKeyRecord[]
}

export async function getProducts() {
  const response = await http.get<ApiResponse<Product[]>>('/products')
  return response.data.data
}

export async function getProduct(id: number) {
  const response = await http.get<ApiResponse<Product>>(`/products/${id}`)
  return response.data.data
}

export async function createOrder(payload: CreateOrderPayload) {
  const response = await http.post<ApiResponse<OrderResult>>('/orders', payload)
  return response.data.data
}

export async function getNotices() {
  const response = await http.get<ApiResponse<Notice[]>>('/notices')
  return response.data.data
}

export async function queryOrders(payload: QueryOrdersPayload) {
  const response = await http.post<ApiResponse<OrderRecord[]>>('/orders/query', payload)
  return response.data.data
}

export async function getMyOrders() {
  const response = await http.get<ApiResponse<OrderRecord[]>>('/auth/orders')
  return response.data.data
}