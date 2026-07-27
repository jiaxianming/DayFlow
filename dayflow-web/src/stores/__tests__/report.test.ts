import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as reportApi from '@/api/report'
import { useReportStore } from '../report'

describe('useReportStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('triggerGenerate 调 generateReport 返回 reportId', async () => {
    const spy = vi.spyOn(reportApi, 'generateReport').mockResolvedValue('99')
    const store = useReportStore()
    const id = await store.triggerGenerate({ type: 'DAILY', date: '2026-07-10' })
    expect(id).toBe('99')
    expect(spy).toHaveBeenCalledWith({ type: 'DAILY', date: '2026-07-10' })
  })

  it('triggerGenerate 期间 isGenerating 为 true，结束恢复 false', async () => {
    vi.spyOn(reportApi, 'generateReport').mockResolvedValue('99')
    const store = useReportStore()
    expect(store.isGenerating).toBe(false)
    const p = store.triggerGenerate({ type: 'DAILY', date: '2026-07-10' })
    expect(store.isGenerating).toBe(true)
    await p
    expect(store.isGenerating).toBe(false)
  })
})
