import { adminHttp, type ApiResponse } from './http'
import type { AdminPermission } from './auth'

export type AdminUserItem = {
  id: number
  username: string
  displayName: string
  isSuperAdmin: boolean
  permissions: AdminPermission[]
  createdAt: string
}

export type AdminUserDetail = {
  id: number
  username: string
  displayName: string
  isSuperAdmin: boolean
  permissions: AdminPermission[]
  createdAt: string
  updatedAt: string
}

export type CreateAdminPayload = {
  username: string
  displayName: string
  password: string
  permissions: AdminPermission[]
}

export async function getAdmins() {
  const response = await adminHttp.get<ApiResponse<AdminUserItem[]>>('/admins')
  return response.data.data
}

export async function createAdmin(payload: CreateAdminPayload) {
  const response = await adminHttp.post<ApiResponse<AdminUserDetail>>('/admins', payload)
  return response.data.data
}

export async function updateAdminPermissions(id: number, permissions: AdminPermission[]) {
  const response = await adminHttp.patch<ApiResponse<AdminUserDetail>>(`/admins/${id}/permissions`, {
    permissions,
  })
  return response.data.data
}

export async function deleteAdmin(id: number) {
  await adminHttp.delete<ApiResponse<null>>(`/admins/${id}`)
}
