import { http, type ApiResponse } from './http'

export type CursorPageResponse<T> = {
  items: T[]
  nextCursor: string | null
  hasMore: boolean
}

export type MemberSupportSession = {
  id: number
  status: 'OPEN' | 'CLOSED'
  lastMessagePreview: string | null
  lastMessageAt: string | null
  memberUnreadCount: number
  adminUnreadCount: number
  createdAt: string
  updatedAt: string
}

export type SupportMessage = {
  id: number
  sessionId: number
  senderScope: 'MEMBER' | 'ADMIN'
  senderId: number
  messageType: 'TEXT' | 'IMAGE' | 'FILE'
  content: string
  attachmentName: string | null
  attachmentContentType: string | null
  attachmentSize: number | null
  attachmentUrl: string | null
  createdAt: string
}

export type SupportUnread = {
  sessionId: number
  memberId: number
  memberUnreadCount: number
  adminUnreadCount: number
}

export type SupportDispatchPayload = {
  message: SupportMessage
  adminSession: {
    id: number
    memberId: number
    memberUsername: string | null
    memberEmail: string | null
    status: 'OPEN' | 'CLOSED'
    lastMessagePreview: string | null
    lastMessageAt: string | null
    memberUnreadCount: number
    adminUnreadCount: number
    createdAt: string
    updatedAt: string
  }
  memberSession: MemberSupportSession
  unread: SupportUnread
}

export type SupportWsEnvelope<T = unknown> = {
  type: 'SUPPORT_DISPATCH' | 'MESSAGE_CREATED' | 'SESSION_UPDATED' | 'UNREAD_UPDATED' | 'ERROR' | 'RECHARGE_CREATED'
  data: T
}

export async function getSupportSession() {
  const response = await http.get<ApiResponse<MemberSupportSession>>('/auth/support/session')
  return response.data.data
}

export async function getSupportMessages(size = 30, cursor?: string | null, after?: string | null) {
  const response = await http.get<ApiResponse<CursorPageResponse<SupportMessage>>>('/auth/support/messages', {
    params: {
      size,
      cursor: cursor || undefined,
      after: after || undefined,
    },
  })
  return response.data.data
}

export async function sendSupportMessage(sessionId: number, content: string) {
  const response = await http.post<ApiResponse<SupportDispatchPayload>>('/auth/support/messages', {
    sessionId,
    content,
  })
  return response.data.data
}

export async function sendSupportAttachment(sessionId: number, file: File, content?: string) {
  const formData = new FormData()
  formData.append('sessionId', String(sessionId))
  formData.append('file', file)
  if (content?.trim()) {
    formData.append('content', content.trim())
  }
  const response = await http.post<ApiResponse<SupportDispatchPayload>>('/auth/support/attachments', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  })
  return response.data.data
}

export async function downloadSupportAttachment(attachmentUrl: string) {
  const requestPath = attachmentUrl.startsWith('/api/') ? attachmentUrl.slice(4) : attachmentUrl
  const response = await http.get<Blob>(requestPath, {
    responseType: 'blob',
  })
  return response.data
}

export async function markSupportRead() {
  const response = await http.post<ApiResponse<SupportUnread>>('/auth/support/read')
  return response.data.data
}
