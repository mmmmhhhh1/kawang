import { adminHttp, type ApiResponse, type CursorPageResponse } from './http'

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

export type NoticeCursorQuery = {
  size: number
  cursor?: string | null
  keyword?: string
  status?: 'PUBLISHED' | 'HIDDEN' | ''
}

export async function getNotices() {
  const response = await adminHttp.get<ApiResponse<NoticeRecord[]>>('/notices')
  return response.data.data
}

export async function getNoticePage(params: NoticeCursorQuery) {
  const response = await adminHttp.get<ApiResponse<CursorPageResponse<NoticeRecord>>>('/notices/page', {
    params: {
      size: params.size,
      cursor: params.cursor || undefined,
      keyword: params.keyword || undefined,
      status: params.status || undefined,
    },
  })
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

export async function deleteNotice(id: number) {
  await adminHttp.delete<ApiResponse<null>>(`/notices/${id}`)
}
