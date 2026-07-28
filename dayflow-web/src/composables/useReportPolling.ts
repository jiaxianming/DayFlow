import { ref, type Ref } from 'vue'
import { getReport, listTraces } from '@/api/report'
import type { IAgentTraceVO, IReportVO } from '@/types/report'

/** 轮询间隔（ms） */
const POLL_INTERVAL = 2500

/**
 * 报告轮询 composable
 * - start：立即拉一次 + 每 POLL_INTERVAL 轮询 report 与 traces
 * - status 为 GENERATED/FAILED 时自动 stop
 * - 不自动 onUnmounted；调用方需在 onUnmounted 调 stop，防定时器泄漏
 *
 * @param reportId 响应式 reportId（Ref）；为 undefined 时 tick 直接返回
 */
export function useReportPolling(reportId: Ref<string | undefined>) {
  const report = ref<IReportVO | null>(null)
  const traces = ref<IAgentTraceVO[]>([])
  const isRunning = ref(false)
  let timer: ReturnType<typeof setInterval> | null = null

  /** 单次轮询：拉 report + traces，三态停止 */
  async function tick(): Promise<void> {
    if (!reportId.value) return
    try {
      report.value = await getReport(reportId.value)
      traces.value = await listTraces(reportId.value)
      if (report.value.status === 'GENERATED' || report.value.status === 'FAILED') {
        stop()
      }
    } catch {
      // 单次轮询失败容忍（网络抖动），不中断整体轮询
    }
  }

  /** 启动轮询 */
  function start(): void {
    if (isRunning.value) return
    isRunning.value = true
    void tick()
    timer = setInterval(() => void tick(), POLL_INTERVAL)
  }

  /** 停止轮询并清定时器 */
  function stop(): void {
    isRunning.value = false
    if (timer !== null) {
      clearInterval(timer)
      timer = null
    }
  }

  return { report, traces, isRunning, start, stop }
}
