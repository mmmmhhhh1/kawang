import { adminHttp, normalizeAdminRequestPath, type ApiResponse, type CursorPageResponse } from './http'

export type PaymentQrRecord = {
  id: number
  name: string
  status: 'ACTIVE' | 'DISABLED'
  imageUrl: string
  createdByName: string | null
  activatedByName: string | null
  activatedAt: string | null
  createdAt: string
}

export type PaymentQrCursorQuery = {
  size: number
  cursor?: string | null
}

export async function getPaymentQrList() {
  const response = await adminHttp.get<ApiResponse<PaymentQrRecord[]>>('/payment-qr')
  return response.data.data
}

export async function getPaymentQrPage(params: PaymentQrCursorQuery) {
  const response = await adminHttp.get<ApiResponse<CursorPageResponse<PaymentQrRecord>>>('/payment-qr/page', {
    params: {
      size: params.size,
      cursor: params.cursor || undefined,
    },
  })
  return response.data.data
}

export async function uploadPaymentQr(name: string, file: File) {
  const formData = new FormData()
  formData.append('name', name)
  formData.append('file', file)
  const response = await adminHttp.post<ApiResponse<PaymentQrRecord>>('/payment-qr', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  })
  return response.data.data
}

export async function activatePaymentQr(id: number) {
  const response = await adminHttp.patch<ApiResponse<PaymentQrRecord>>(`/payment-qr/${id}/activate`)
  return response.data.data
}

export async function disablePaymentQr(id: number) {
  const response = await adminHttp.patch<ApiResponse<PaymentQrRecord>>(`/payment-qr/${id}/disable`)
  return response.data.data
}

export async function getPaymentQrImageBlob(imageUrl: string) {
  const response = await adminHttp.get<Blob>(normalizeAdminRequestPath(imageUrl), {
    responseType: 'blob',
  })
  return response.data
}