import type { ReportType, ReportStatus, AgentName } from './enums'
import type { IPageQuery } from './api'

/**
 * 报告视图（errorMsg 生成成功时为 null；tokenUsage 为普通整数，用 number）
 */
export interface IReportVO {
  id: string
  userId: string
  type: ReportType
  periodStart: string
  periodEnd: string
  title: string
  content: string
  status: ReportStatus
  errorMsg: string | null
  tokenUsage: number
  createdAt: string
}

/**
 * 触发生成入参（M4 运行时只用 type=DAILY；date 为 'YYYY-MM-DD'）
 */
export interface IReportGenerateDTO {
  type: ReportType
  date: string
}

/**
 * 报告分页查询条件（按类型过滤）
 */
export interface IReportQuery extends IPageQuery {
  type?: ReportType
}

/**
 * Agent 执行轨迹视图（tokens/latencyMs/step/retryCount 为普通整数）
 */
export interface IAgentTraceVO {
  id: string
  reportId: string
  agentName: AgentName
  step: number
  inputSummary: string
  outputSummary: string
  tokens: number
  latencyMs: number
  retryCount: number
  createdAt: string
}
