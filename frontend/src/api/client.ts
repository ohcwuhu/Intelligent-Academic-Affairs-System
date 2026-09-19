import axios, { AxiosError } from 'axios'
import type { ApiEnvelope } from './types'

/** 后端业务错误。message 已是可读中文，直接展示给用户。 */
export class ApiError extends Error {
  code: number

  constructor(code: number, message: string) {
    super(message)
    this.code = code
    this.name = 'ApiError'
  }

  get isUnauthorized() {
    return this.code === 401
  }

  get isForbidden() {
    return this.code === 403
  }
}

export const TOKEN_KEY = 'iaas.token'

export const http = axios.create({ baseURL: '/api', timeout: 20000 })

http.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

/** 401 时清理本地令牌并跳回登录页，由路由守卫完成跳转。 */
function handleUnauthorized() {
  localStorage.removeItem(TOKEN_KEY)
  if (!location.hash.startsWith('#/login')) {
    location.hash = '#/login'
  }
}

/**
 * 统一拆包：把 { code, message, data } 拆成 data 或在 code 非 0 时抛 ApiError。
 * 所有接口调用都走这里，避免每个视图各写一遍判断。
 */
async function unwrap<T>(promise: Promise<{ data: ApiEnvelope<T> }>): Promise<T> {
  try {
    const res = await promise
    const body = res.data
    if (body.code !== 0) {
      const err = new ApiError(body.code, body.message)
      if (err.isUnauthorized) handleUnauthorized()
      throw err
    }
    return body.data
  } catch (e) {
    if (e instanceof ApiError) throw e
    const ax = e as AxiosError<ApiEnvelope<unknown>>
    const status = ax.response?.status
    const msg = ax.response?.data?.message
    if (status === 401) handleUnauthorized()
    throw new ApiError(status ?? 0, msg ?? describeTransportError(ax))
  }
}

function describeTransportError(e: AxiosError): string {
  if (e.code === 'ECONNABORTED') return '请求超时，请确认后端服务是否在运行'
  if (!e.response) return '连接不上后端服务，请确认 8080 端口已启动'
  return '请求失败，请稍后重试'
}

export function get<T>(url: string, params?: Record<string, unknown>) {
  return unwrap<T>(http.get<ApiEnvelope<T>>(url, { params }))
}

export function post<T>(url: string, body?: unknown, params?: Record<string, unknown>) {
  return unwrap<T>(http.post<ApiEnvelope<T>>(url, body ?? {}, { params }))
}

export function del<T>(url: string, params?: Record<string, unknown>) {
  return unwrap<T>(http.delete<ApiEnvelope<T>>(url, { params }))
}
