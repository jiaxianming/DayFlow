import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as authApi from '@/api/auth'
import type { ILoginDTO, IRegisterDTO } from '@/types/auth'

/**
 * 鉴权 Store
 * token + userInfo 持久化到 localStorage；
 * token key 为 'token'，与 api/index.ts 请求拦截器约定一致。
 */
export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(localStorage.getItem('token') || '')
  const userId = ref<string>(localStorage.getItem('userId') || '')
  const username = ref<string>(localStorage.getItem('username') || '')
  const nickname = ref<string>(localStorage.getItem('nickname') || '')

  /** 是否已登录（仅依赖 token） */
  const isAuthed = computed(() => !!token.value)

  /** 写入登录态并持久化 */
  function applyLogin(t: string, id: string, name: string, nick: string | null): void {
    token.value = t
    userId.value = id
    username.value = name
    nickname.value = nick || ''
    localStorage.setItem('token', t)
    localStorage.setItem('userId', id)
    localStorage.setItem('username', name)
    localStorage.setItem('nickname', nick || '')
  }

  /** 清空登录态 */
  function clear(): void {
    token.value = ''
    userId.value = ''
    username.value = ''
    nickname.value = ''
    localStorage.removeItem('token')
    localStorage.removeItem('userId')
    localStorage.removeItem('username')
    localStorage.removeItem('nickname')
  }

  /** 登录：调 api → 持久化 */
  async function login(dto: ILoginDTO): Promise<void> {
    const vo = await authApi.login(dto)
    applyLogin(vo.token, vo.userId, vo.username, vo.nickname)
  }

  /** 注册：调 api（注册即登录返回 LoginVO）→ 持久化 */
  async function register(dto: IRegisterDTO): Promise<void> {
    const vo = await authApi.register(dto)
    applyLogin(vo.token, vo.userId, vo.username, vo.nickname)
  }

  /** 登出：清登录态（跳转由调用方决定） */
  function logout(): void {
    clear()
  }

  return { token, userId, username, nickname, isAuthed, login, register, logout }
})
