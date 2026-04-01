import { adminHttp, clearStoredAuth, setStoredToken, type ApiResponse } from './http'

export type AdminProfile = {
  id: number
  username: string
  displayName: string
}

type LoginResponse = {
  token: string
  tokenType: string
  profile: AdminProfile
}

export async function login(username: string, password: string) {
  const response = await adminHttp.post<ApiResponse<LoginResponse>>('/auth/login', { username, password })
  const data = response.data.data
  setStoredToken(data.token)
  localStorage.setItem('kawang_admin_profile', JSON.stringify(data.profile))
  return data
}

export async function fetchMe() {
  const response = await adminHttp.get<ApiResponse<AdminProfile>>('/auth/me')
  const profile = response.data.data
  localStorage.setItem('kawang_admin_profile', JSON.stringify(profile))
  return profile
}

export function getStoredProfile() {
  const raw = localStorage.getItem('kawang_admin_profile')
  return raw ? (JSON.parse(raw) as AdminProfile) : null
}

export function logout() {
  clearStoredAuth()
}
