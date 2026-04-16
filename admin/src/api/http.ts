import axios from 'axios'

export type ApiResponse<T> = {
  code: number
  message: string
  data: T
}

export type PageResult<T> = {
  items: T[]
  total: number
  page: number
  size: number
}

export type CursorPageResponse<T> = {
  items: T[]
  nextCursor: string | null
  hasMore: boolean
}

const TOKEN_KEY = 'kawang_admin_token'
const ADMIN_BASE_PATH = '/api/admin'

export const adminHttp = axios.create({
  baseURL: ADMIN_BASE_PATH,
  timeout: 10000,
})

adminHttp.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

adminHttp.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error?.response?.status === 401) {
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem('kawang_admin_profile')
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
    }
    return Promise.reject(error)
  },
)

export function normalizeAdminRequestPath(url: string) {
  if (!url) {
    return url
  }
  if (/^https?:\/\//i.test(url) || url.startsWith('//')) {
    return url
  }
  if (url.startsWith(ADMIN_BASE_PATH)) {
    const normalized = url.slice(ADMIN_BASE_PATH.length)
    return normalized || '/'
  }
  return url
}

export function getStoredToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function setStoredToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearStoredAuth() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem('kawang_admin_profile')
}