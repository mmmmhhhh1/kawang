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
  username: string
}

type MemberAuthResponse = {
  token: string
  tokenType: string
  profile: MemberProfile
}

type MemberCredentials = {
  username: string
  password: string
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

export async function fetchMemberProfile() {
  const response = await http.get<ApiResponse<MemberProfile>>('/auth/me')
  const profile = response.data.data
  setStoredProfileRaw(JSON.stringify(profile))
  memberProfileState.value = profile
  return profile
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
