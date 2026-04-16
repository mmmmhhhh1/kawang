import { ref } from 'vue'
import {
  clearStoredAuth,
  getStoredProfileRaw,
  http,
  setStoredProfileRaw,
  setStoredToken,
  type ApiResponse,
} from './http'

export type MemberProfile = {
  id: number
  username?: string | null
  email?: string | null
  balance?: number | null
}

export type MemberAuthResponse = {
  token: string
  tokenType: string
  profile: MemberProfile
}

export type MemberCredentials = {
  username: string
  password: string
}

export type EmailCodeScene = 'login' | 'register'

export type EmailCodePayload = {
  email: string
  scene: EmailCodeScene
}

export type EmailLoginPayload = {
  email: string
  code: string
}

export type EmailRegisterPayload = {
  email: string
  code: string
  username: string
  password: string
}

export type CursorPageResponse<T> = {
  items: T[]
  nextCursor: string | null
  hasMore: boolean
}

export type MemberRechargeItem = {
  id: number
  requestNo: string
  amount: number
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  payerRemark: string | null
  rejectReason: string | null
  reviewedAt: string | null
  createdAt: string
}

function parseStoredProfile() {
  const raw = getStoredProfileRaw()
  if (!raw) {
    return null
  }
  try {
    return JSON.parse(raw) as MemberProfile
  } catch {
    return null
  }
}

function persistSession(session: MemberAuthResponse) {
  setStoredToken(session.token)
  setStoredProfileRaw(JSON.stringify(session.profile))
  memberProfileState.value = session.profile
  return session
}

export const memberProfileState = ref<MemberProfile | null>(parseStoredProfile())

export async function registerMember(payload: MemberCredentials) {
  const response = await http.post<ApiResponse<MemberAuthResponse>>('/auth/register', payload)
  return persistSession(response.data.data)
}

export async function loginMember(payload: MemberCredentials) {
  const response = await http.post<ApiResponse<MemberAuthResponse>>('/auth/login', payload)
  return persistSession(response.data.data)
}

export async function sendEmailCode(payload: EmailCodePayload) {
  await http.post<ApiResponse<void>>('/auth/mail/send-code', payload)
}

export async function loginMemberByEmail(payload: EmailLoginPayload) {
  const response = await http.post<ApiResponse<MemberAuthResponse>>('/auth/mail/login', payload)
  return persistSession(response.data.data)
}

export async function registerMemberByEmail(payload: EmailRegisterPayload) {
  const response = await http.post<ApiResponse<MemberAuthResponse>>('/auth/mail/register', payload)
  return persistSession(response.data.data)
}

export async function fetchMemberProfile() {
  const response = await http.get<ApiResponse<MemberProfile>>('/auth/me')
  const profile = response.data.data
  setStoredProfileRaw(JSON.stringify(profile))
  memberProfileState.value = profile
  return profile
}

export async function getMyRecharges(size = 10, cursor?: string | null) {
  const response = await http.get<ApiResponse<CursorPageResponse<MemberRechargeItem>>>('/auth/recharges', {
    params: {
      size,
      cursor: cursor || undefined,
    },
  })
  return response.data.data
}

export async function createRecharge(payload: FormData) {
  const response = await http.post<ApiResponse<MemberRechargeItem>>('/auth/recharges', payload, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  })
  return response.data.data
}

export function logoutMember() {
  clearStoredAuth()
  memberProfileState.value = null
}

export function getStoredMemberProfile() {
  if (!memberProfileState.value) {
    memberProfileState.value = parseStoredProfile()
  }
  return memberProfileState.value
}