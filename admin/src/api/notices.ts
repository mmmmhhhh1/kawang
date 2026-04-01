import { adminHttp, type ApiResponse } from './http'

export type NoticeRecord = {
  id: number
  title: string
  summary: string
  content: string
  status: 'PUBLISHED' | 'HIDDEN'
  sortOrder: number
  publishedAt: string
  updatedAt: string
}

export type NoticePayload = {
  title: string
  summary: string
  content: string
  status: 'PUBLISHED' | 'HIDDEN'
  sortOrder: number
}

export async function getNotices() {
  const response = await adminHttp.get<ApiResponse<NoticeRecord[]>>('/notices')
  return response.data.data
}

export async function createNotice(payload: NoticePayload) {
  const response = await adminHttp.post<ApiResponse<NoticeRecord>>('/notices', payload)
  return response.data.data
}

export async function updateNotice(id: number, payload: NoticePayload) {
  const response = await adminHttp.put<ApiResponse<NoticeRecord>>(`/notices/${id}`, payload)
  return response.data.data
}

export async function updateNoticeStatus(id: number, status: 'PUBLISHED' | 'HIDDEN') {
  const response = await adminHttp.patch<ApiResponse<NoticeRecord>>(`/notices/${id}/status`, { status })
  return response.data.data
}
