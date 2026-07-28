import axios from 'axios'
import type { AxiosResponse } from 'axios'
import JSONBig from 'json-bigint'
import { ElMessage } from 'element-plus'
import type { IResult } from '@/types/api'

/**
 * 雪花 ID 安全解析：超过 Number.MAX_SAFE_INTEGER 的整数解析为 string。
 * 作为 axios transformResponse，对 /api 响应体统一生效。
 *
 * @param data 原始响应字符串
 * @returns 解析后的对象（id 类字段为 string）
 */
export function parseBigintJson(data: string): unknown {
  // 防御：非字符串（如 mock-adapter 直接传对象、或非 JSON 响应体）原样返回，
  // 与 axios 默认 transformResponse 的 `typeof data === 'string'` 守卫一致。
  if (!data || typeof data !== 'string') return data
  try {
    return JSONBig({ storeAsString: true }).parse(data)
  } catch {
    // 非标准 JSON 兜底用原生 parse（仍可能抛错，交由上层 catch）
    return JSON.parse(data)
  }
}

/** 配置好的 axios 实例（baseURL=/api，注入 JWT，按 Result.code 分流） */
export const http = axios.create({
  baseURL: '/api',
  timeout: 15000,
  transformResponse: [(data) => parseBigintJson(data)],
})

/**
 * 401 处理由外部注入，避免 api ↔ router/store 循环依赖。
 * main.ts 启动时调用 setUnauthorizedHandler 注册：清 authStore + 跳 /login。
 */
let unauthorizedHandler: (() => void) | null = null

export function setUnauthorizedHandler(fn: () => void): void {
  unauthorizedHandler = fn
}

/** 请求拦截器：从 localStorage 取 token 注入 Authorization 头 */
http.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

/**
 * 响应拦截器：按后端 Result.code 分流
 * - 200：解包返回 data
 * - 401：调用注入的 401 handler 并 reject
 * - 其他：ElMessage.error 提示并 reject
 * - 网络错误：ElMessage.error('网络异常...') 并 reject
 */
http.interceptors.response.use(
  (response): AxiosResponse | Promise<AxiosResponse> => {
    const result = response.data as IResult<unknown>
    if (result.code === 200) {
      // 解包返回 Result.data（非 AxiosResponse）——运行时由各 api 函数
      // `as unknown as Promise<T>` 补偿类型；此处 cast 仅为满足 axios 拦截器签名。
      return result.data as unknown as AxiosResponse
    }
    if (result.code === 401) {
      unauthorizedHandler?.()
      return Promise.reject(new Error(result.msg || '未认证'))
    }
    ElMessage.error(result.msg || '请求失败')
    return Promise.reject(new Error(result.msg || '请求失败'))
  },
  (error) => {
    ElMessage.error('网络异常，请稍后重试')
    return Promise.reject(error)
  },
)
