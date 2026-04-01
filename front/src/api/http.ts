import axios from 'axios'

export const http = axios.create({
  baseURL: '/api',
  timeout: 12000,
})

export type ApiResponse<T> = {
  code: number
  message: string
  data: T
}

const TOKEN_KEY = 'kawang_member_token'
const PROFILE_KEY = 'kawang_member_profile'

http.interceptors.request.use((config) => {
  const token = getStoredToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error?.response?.status === 401) {
      clearStoredAuth()
      if (window.location.pathname === '/orders/me') {
        const redirect = encodeURIComponent(window.location.pathname)
        window.location.href = `/login?redirect=${redirect}`
      }
    }
    return Promise.reject(error)
  },
)

export function getStoredToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function setStoredToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token)
}

export function getStoredProfileRaw() {
  return localStorage.getItem(PROFILE_KEY)
}

export function setStoredProfileRaw(profile: string) {
  localStorage.setItem(PROFILE_KEY, profile)
}

export function clearStoredAuth() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(PROFILE_KEY)
}
