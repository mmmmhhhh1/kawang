import { adminHttp, type ApiResponse, type CursorPageResponse } from './http'

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

export type ProductOptionRecord = Pick<ProductRecord, 'id' | 'sku' | 'title' | 'status'>

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

export type ProductCursorQuery = {
  size: number
  cursor?: string | null
  keyword?: string
  status?: 'ACTIVE' | 'INACTIVE'
}

export async function getProductPage(params: ProductCursorQuery) {
  const response = await adminHttp.get<ApiResponse<CursorPageResponse<ProductRecord>>>('/products/page', {
    params: {
      size: params.size,
      cursor: params.cursor || undefined,
      keyword: params.keyword || undefined,
      status: params.status || undefined,
    },
  })
  return response.data.data
}

export async function searchProductOptions(keyword?: string, size = 20) {
  const response = await adminHttp.get<ApiResponse<ProductOptionRecord[]>>('/products/options', {
    params: {
      keyword: keyword || undefined,
      size,
    },
  })
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