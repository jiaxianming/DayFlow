import { describe, expect, it } from 'vitest'
import { resolveRoute } from '../index'

describe('resolveRoute', () => {
  it('未登录访问受保护页 → 跳 /login 带 redirect', () => {
    expect(resolveRoute({ path: '/input' }, false)).toBe(
      '/login?redirect=' + encodeURIComponent('/input'),
    )
  })

  it('未登录访问公开页 → 放行', () => {
    expect(resolveRoute({ path: '/login', meta: { public: true } }, false)).toBe(true)
  })

  it('已登录访问 /login → 跳 /input', () => {
    expect(resolveRoute({ path: '/login', meta: { public: true } }, true)).toBe('/input')
  })

  it('已登录访问 /register → 跳 /input', () => {
    expect(resolveRoute({ path: '/register', meta: { public: true } }, true)).toBe('/input')
  })

  it('已登录访问受保护页 → 放行', () => {
    expect(resolveRoute({ path: '/input' }, true)).toBe(true)
  })
})
