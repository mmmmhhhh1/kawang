import { adminHttp, type ApiResponse } from './http'

export type ProductRecord = {
  id: number
  sku: string
  title: string
  vendor: string
  planName: string
  description: string
  price: number
  availableStock: number
  soldCount: number
  status: 'ACTIVE' | 'INACTIVE'
  sortOrder: number
  updatedAt: string
}

export type ProductPayload = {
  sku: string
  title: string
  vendor: string
  planName: string
  description: string
  price: number
  status: 'ACTIVE' | 'INACTIVE'
  sortOrder: number
}

export async function getProducts() {
  const response = await adminHttp.get<ApiResponse<ProductRecord[]>>('/products')
  return response.data.data
}

export async function createProduct(payload: ProductPayload) {
  const response = await adminHttp.post<ApiResponse<ProductRecord>>('/products', payload)
  return response.data.data
}

export async function updateProduct(id: number, payload: ProductPayload) {
  const response = await adminHttp.put<ApiResponse<ProductRecord>>(`/products/${id}`, payload)
  return response.data.data
}

export async function updateProductStatus(id: number, status: 'ACTIVE' | 'INACTIVE') {
  const response = await adminHttp.patch<ApiResponse<ProductRecord>>(`/products/${id}/status`, { status })
  return response.data.data
}

export async function deleteProduct(id: number) {
  await adminHttp.delete<ApiResponse<void>>(`/products/${id}`)
}