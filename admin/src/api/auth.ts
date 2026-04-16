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

function normalizeAdminProfile(raw: unknown): AdminProfile | null {
  if (!raw || typeof raw !== 'object') {
    return null
  }

  const source = raw as Record<string, unknown>
  const id = Number(source.id ?? 0)
  const username = typeof source.username === 'string' ? source.username : ''
  const displayName = typeof source.displayName === 'string' ? source.displayName : username
  const permissions = Array.isArray(source.permissions)
    ? source.permissions.filter((item): item is AdminPermission => typeof item === 'string')
    : []
  const isSuperAdmin = Boolean(source.isSuperAdmin ?? source.superAdmin ?? source.is_super_admin)

  if (!Number.isFinite(id) || !username) {
    return null
  }

  return {
    id,
    username,
    displayName,
    isSuperAdmin,
    permissions,
  }
}

function parseStoredProfile() {
  const raw = localStorage.getItem(PROFILE_KEY)
  if (!raw) {
    return null
  }
  try {
    return normalizeAdminProfile(JSON.parse(raw))
  } catch {
    return null
  }
}

function persistProfile(profile: AdminProfile) {
  const normalized = normalizeAdminProfile(profile)
  if (!normalized) {
    localStorage.removeItem(PROFILE_KEY)
    adminProfileState.value = null
    return null
  }
  localStorage.setItem(PROFILE_KEY, JSON.stringify(normalized))
  adminProfileState.value = normalized
  return normalized
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
  const profile = persistProfile(data.profile)
  return {
    ...data,
    profile: profile ?? data.profile,
  }
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