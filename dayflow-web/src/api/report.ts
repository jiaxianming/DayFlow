import { http } from './index'
import type { IPage } from '@/types/api'
import type { IReportVO, IReportGenerateDTO, IReportQuery, IAgentTraceVO } from '@/types/report'

/**
 * 触发报告生成（POST /api/reports/generate）
 * 异步：立即返回 reportId，前端轮询状态与轨迹
 */
export function generateReport(dto: IReportGenerateDTO): Promise<string> {
  return http.post('/reports/generate', dto) as unknown as Promise<string>
}

/** 查询单个报告 */
export function getReport(id: string): Promise<IReportVO> {
  return http.get(`/reports/${id}`) as unknown as Promise<IReportVO>
}

/** 删除报告 */
export function deleteReport(id: string): Promise<void> {
  return http.delete(`/reports/${id}`) as unknown as Promise<void>
}

/** 分页查询报告（GET /api/reports） */
export function pageReports(query: IReportQuery): Promise<IPage<IReportVO>> {
  return http.get('/reports', { params: query }) as unknown as Promise<IPage<IReportVO>>
}

/** 查询某报告的 Agent 执行轨迹（GET /api/reports/{id}/traces） */
export function listTraces(reportId: string): Promise<IAgentTraceVO[]> {
  return http.get(`/reports/${reportId}/traces`) as unknown as Promise<IAgentTraceVO[]>
}
