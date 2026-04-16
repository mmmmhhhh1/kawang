import { adminHttp, type ApiResponse, type CursorPageResponse } from './http'

export type MemberStatus = 'ACTIVE' | 'DISABLED'

export type MemberListItem = {
  id: number
  username: string | null
  email: string | null
  status: MemberStatus
  createdAt: string
}

export type MemberOrderDetail = {
  id: number
  orderNo: string
  productId: number
  productTitle: string
  quantity: number
  totalAmount: number
  buyerContact: string
  status: 'SUCCESS' | 'CLOSED'
  createdAt: string
  cardKeys: string[]
}

export type MemberDetail = {
  id: number
  username: string | null
  email: string | null
  status: MemberStatus
  createdAt: string
  updatedAt: string
  orders: MemberOrderDetail[]
}

export type MemberActivity = {
  userId: number
  lastSeenAt: string | null
  lastLoginAt: string | null
}

export type MemberCursorQuery = {
  size: number
  cursor?: string | null
  keyword?: string
  status?: MemberStatus | ''
}

export async function getUsers() {
  const response = await adminHttp.get<ApiResponse<MemberListItem[]>>('/users')
  return response.data.data
}

export async function getUserPage(params: MemberCursorQuery) {
  const response = await adminHttp.get<ApiResponse<CursorPageResponse<MemberListItem>>>('/users/page', {
    params: {
      size: params.size,
      cursor: params.cursor || undefined,
      keyword: params.keyword || undefined,
      status: params.status || undefined,
    },
  })
  return response.data.data
}

export async function getUserDetail(id: number) {
  const response = await adminHttp.get<ApiResponse<MemberDetail>>(`/users/${id}`)
  return response.data.data
}

export async function getUserActivities(ids: number[]) {
  const response = await adminHttp.get<ApiResponse<MemberActivity[]>>('/users/activity', {
    params: { ids: ids.join(',') },
  })
  return response.data.data
}

export async function getUserActivity(id: number) {
  const response = await adminHttp.get<ApiResponse<MemberActivity>>(`/users/${id}/activity`)
  return response.data.data
}

export async function updateUserStatus(id: number, status: MemberStatus) {
  const response = await adminHttp.patch<ApiResponse<MemberListItem>>(`/users/${id}/status`, { status })
  return response.data.data
}