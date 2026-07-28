import { vi } from 'vitest'

/**
 * Vitest 全局 setup
 * 补 ResizeObserver stub：Element Plus 的 el-table / el-select 等组件在 jsdom 下
 * 依赖 ResizeObserver 做布局计算，无 stub 时渲染异常/行不出现。
 */
class ResizeObserverStub {
  observe(): void {}
  unobserve(): void {}
  disconnect(): void {}
}
vi.stubGlobal('ResizeObserver', ResizeObserverStub)
