import { describe, it, expect } from 'vitest'

/**
 * 脚手架冒烟测试：验证 Vitest 配置可用
 */
describe('scaffold smoke', () => {
  it('vitest runs and asserts', () => {
    expect(1 + 1).toBe(2)
  })
})
