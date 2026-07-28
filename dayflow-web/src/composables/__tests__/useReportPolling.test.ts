import { ref } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as reportApi from '@/api/report'
import { useReportPolling } from '../useReportPolling'
import type { IReportVO } from '@/types/report'

describe('useReportPolling', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  it('start 立即拉一次并周期轮询，GENERATED 时停止', async () => {
    const reportId = ref('1')
    const generating: IReportVO = { id: '1', userId: 'u', type: 'DAILY', periodStart: '', periodEnd: '', title: '', content: '', status: 'GENERATING', errorMsg: null, tokenUsage: 0, createdAt: '' }
    const generated: IReportVO = { ...generating, status: 'GENERATED', content: '# done' }
    const getReport = vi.spyOn(reportApi, 'getReport')
      .mockResolvedValueOnce(generating)
      .mockResolvedValueOnce(generated)
    const listTraces = vi.spyOn(reportApi, 'listTraces').mockResolvedValue([])

    const { report, isRunning, start } = useReportPolling(reportId)
    start()

    await vi.advanceTimersByTimeAsync(0)
    expect(getReport).toHaveBeenCalledTimes(1)
    expect(report.value?.status).toBe('GENERATING')
    expect(isRunning.value).toBe(true)

    await vi.advanceTimersByTimeAsync(2500)
    expect(getReport).toHaveBeenCalledTimes(2)
    expect(listTraces).toHaveBeenCalledTimes(2)
    expect(report.value?.status).toBe('GENERATED')
    expect(isRunning.value).toBe(false)
  })

  it('FAILED 时也停止', async () => {
    const reportId = ref('1')
    const failed: IReportVO = { id: '1', userId: 'u', type: 'DAILY', periodStart: '', periodEnd: '', title: '', content: '', status: 'FAILED', errorMsg: '出错了', tokenUsage: 0, createdAt: '' }
    vi.spyOn(reportApi, 'getReport').mockResolvedValue(failed)
    vi.spyOn(reportApi, 'listTraces').mockResolvedValue([])

    const { report, isRunning, start } = useReportPolling(reportId)
    start()

    await vi.advanceTimersByTimeAsync(0)
    expect(report.value?.status).toBe('FAILED')
    expect(isRunning.value).toBe(false)
  })

  it('stop 清定时器，不再轮询', async () => {
    const reportId = ref('1')
    const generating: IReportVO = { id: '1', userId: 'u', type: 'DAILY', periodStart: '', periodEnd: '', title: '', content: '', status: 'GENERATING', errorMsg: null, tokenUsage: 0, createdAt: '' }
    const getReport = vi.spyOn(reportApi, 'getReport').mockResolvedValue(generating)
    vi.spyOn(reportApi, 'listTraces').mockResolvedValue([])

    const { start, stop } = useReportPolling(reportId)
    start()
    await vi.advanceTimersByTimeAsync(0)
    expect(getReport).toHaveBeenCalledTimes(1)

    stop()
    await vi.advanceTimersByTimeAsync(5000)
    expect(getReport).toHaveBeenCalledTimes(1) // stop 后不再增加
  })
})
