import { defineStore } from 'pinia'
import { ref } from 'vue'
import { generateReport } from '@/api/report'
import type { IReportGenerateDTO } from '@/types/report'

/**
 * 报告 Store
 * 仅管「触发生成」动作（跨 HistoryView/ReportView 复用）；
 * 报告详情态（report/traces）由 useReportPolling composable 局部管理。
 */
export const useReportStore = defineStore('report', () => {
  /** 生成请求进行中（控制按钮 loading） */
  const isGenerating = ref(false)

  /**
   * 触发日报生成：POST /api/reports/generate → 返回 reportId
   */
  async function triggerGenerate(dto: IReportGenerateDTO): Promise<string> {
    isGenerating.value = true
    try {
      return await generateReport(dto)
    } finally {
      isGenerating.value = false
    }
  }

  return { isGenerating, triggerGenerate }
})
