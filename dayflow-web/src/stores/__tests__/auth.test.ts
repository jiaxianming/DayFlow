import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import * as authApi from '@/api/auth'
import { useAuthStore } from '../auth'

describe('useAuthStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  it('login 成功写入 token/userInfo 并持久化 localStorage', async () => {
    vi.spyOn(authApi, 'login').mockResolvedValue({
      token: 'tok', userId: '1', username: 'alice', nickname: '小A',
    })
    const store = useAuthStore()
    await store.login({ username: 'alice', password: 'pw' })
    expect(store.token).toBe('tok')
    expect(store.userId).toBe('1')
    expect(store.username).toBe('alice')
    expect(store.nickname).toBe('小A')
    expect(store.isAuthed).toBe(true)
    expect(localStorage.getItem('token')).toBe('tok')
  })

  it('register 成功即登录（nickname 为 null 时存空串）', async () => {
    vi.spyOn(authApi, 'register').mockResolvedValue({
      token: 't2', userId: '2', username: 'bob', nickname: null,
    })
    const store = useAuthStore()
    await store.register({ username: 'bob', password: 'pw' })
    expect(store.isAuthed).toBe(true)
    expect(store.username).toBe('bob')
    expect(store.nickname).toBe('')
  })

  it('logout 清空状态与 localStorage', () => {
    const store = useAuthStore()
    store.token = 'x'
    localStorage.setItem('token', 'x')
    store.logout()
    expect(store.token).toBe('')
    expect(store.isAuthed).toBe(false)
    expect(localStorage.getItem('token')).toBeNull()
  })

  it('初始 token 从 localStorage 读取（刷新保登录态）', () => {
    localStorage.setItem('token', 'restored')
    const store = useAuthStore()
    expect(store.token).toBe('restored')
    expect(store.isAuthed).toBe(true)
  })
})
