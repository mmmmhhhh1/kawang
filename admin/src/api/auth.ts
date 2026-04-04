import { ref } from 'vue'
import { adminHttp, clearStoredAuth, setStoredToken, type ApiResponse } from './http'

const PROFILE_KEY = 'kawang_admin_profile'

export type AdminPermission =
  | 'DISABLE_USER'
  | 'DELETE_PRODUCT'
  | 'DELETE_ORDER'
  | 'CREATE_ADMIN'

export type AdminProfile = {
  id: number
  username: string
  displayName: string
  isSuperAdmin: boolean
  permissions: AdminPermission[]
}

type LoginResponse = {
  token: string
  tokenType: string
  profile: AdminProfile
}

function parseStoredProfile() {
  const raw = localStorage.getItem(PROFILE_KEY)
  if (!raw) {
    return null
  }
  try {
    return JSON.parse(raw) as AdminProfile
  } catch {
    return null
  }
}

function persistProfile(profile: AdminProfile) {
  localStorage.setItem(PROFILE_KEY, JSON.stringify(profile))
  adminProfileState.value = profile
  return profile
}

export const adminProfileState = ref<AdminProfile | null>(parseStoredProfile())

export function hasAdminPermission(permission: AdminPermission, profile = adminProfileState.value) {
  if (!profile) {
    return false
  }
  if (profile.isSuperAdmin) {
    return true
  }
  return Array.isArray(profile.permissions) && profile.permissions.includes(permission)
}

export async function login(username: string, password: string) {
  const response = await adminHttp.post<ApiResponse<LoginResponse>>('/auth/login', { username, password })
  const data = response.data.data
  setStoredToken(data.token)
  persistProfile(data.profile)
  return data
}

export async function fetchMe() {
  const response = await adminHttp.get<ApiResponse<AdminProfile>>('/auth/me')
  return persistProfile(response.data.data)
}

export function getStoredProfile() {
  if (!adminProfileState.value) {
    adminProfileState.value = parseStoredProfile()
  }
  return adminProfileState.value
}

export function logout() {
  clearStoredAuth()
  adminProfileState.value = null
}
