import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'

/**
 * ElMessage 在 jsdom 环境无 DOM 通知容器会报警告，统一 mock
 */
vi.mock('element-plus', () => ({
  ElMessage: {
    error: vi.fn(),
    warning: vi.fn(),
    success: vi.fn(),
    info: vi.fn(),
  },
}))

import { parseBigintJson } from '@/api/index'

describe('parseBigintJson', () => {
  it('雪花 ID（19 位）解析为 string，保精度', () => {
    const raw = '{"id":1901234567890123456,"name":"x"}'
    const parsed = parseBigintJson(raw) as { id: unknown; name: string }
    expect(parsed.id).toBe('1901234567890123456')
    expect(typeof parsed.id).toBe('string')
  })

  it('小整数仍为 number', () => {
    const parsed = parseBigintJson('{"age":30}') as { age: unknown }
    expect(parsed.age).toBe(30)
    expect(typeof parsed.age).toBe('number')
  })

  it('空数据原样返回', () => {
    expect(parseBigintJson('')).toBe('')
  })

  it('非法 JSON 抛错（兜底也用 JSON.parse）', () => {
    expect(() => parseBigintJson('{bad')).toThrow()
  })
})

import MockAdapter from 'axios-mock-adapter'
import { http, setUnauthorizedHandler } from '@/api/index'
import { ElMessage } from 'element-plus'

describe('http interceptors', () => {
  let mock: MockAdapter

  beforeEach(() => {
    mock = new MockAdapter(http)
    localStorage.clear()
  })

  afterEach(() => {
    mock.restore()
  })

  it('code=200 解包返回 data', async () => {
    mock.onGet('/x').reply(200, { code: 200, msg: 'ok', data: { id: '1' } })
    const res = (await http.get('/x')) as unknown as { id: string }
    expect(res).toEqual({ id: '1' })
  })

  it('code=401 调用 unauthorizedHandler 并 reject', async () => {
    const handler = vi.fn()
    setUnauthorizedHandler(handler)
    mock.onGet('/x').reply(200, { code: 401, msg: '未认证', data: null })
    await expect(http.get('/x')).rejects.toThrow('未认证')
    expect(handler).toHaveBeenCalledTimes(1)
  })

  it('code=500 走 ElMessage.error 并 reject', async () => {
    mock.onGet('/x').reply(200, { code: 500, msg: '系统异常', data: null })
    await expect(http.get('/x')).rejects.toThrow('系统异常')
    expect(ElMessage.error).toHaveBeenCalled()
  })

  it('请求拦截器从 localStorage 注入 Bearer token', async () => {
    localStorage.setItem('token', 'abc123')
    let captured: string | undefined
    mock.onGet('/x').reply((config) => {
      captured = config.headers?.Authorization as string | undefined
      return [200, { code: 200, msg: 'ok', data: null }]
    })
    await http.get('/x').catch(() => undefined)
    expect(captured).toBe('Bearer abc123')
  })

  it('网络错误走 ElMessage.error 并 reject', async () => {
    // 模拟无响应（连接拒绝/超时等）：走 axios 的 reject 分支而非 Result.code 分支
    mock.onGet('/x').networkError()
    const before = (ElMessage.error as ReturnType<typeof vi.fn>).mock.calls.length
    await expect(http.get('/x')).rejects.toBeTruthy()
    expect((ElMessage.error as ReturnType<typeof vi.fn>).mock.calls.length).toBe(before + 1)
  })
})
