/**
 * 活动类别（对应后端 ActivityCategory 枚举）
 */
export type ActivityCategory = 'WORK' | 'STUDY' | 'MEETING' | 'OTHER'

/**
 * 任务状态（对应后端 TaskStatus 枚举）
 */
export type TaskStatus = 'TODO' | 'DOING' | 'DONE'

/**
 * 报告类型（对应后端 ReportType 枚举；M4 运行时只用 DAILY）
 */
export type ReportType = 'DAILY' | 'WEEKLY'

/**
 * 报告状态（对应后端 ReportStatus 枚举）
 */
export type ReportStatus = 'GENERATING' | 'GENERATED' | 'FAILED'

/**
 * Agent 名称（编辑部 4 Agent，对应后端 AgentName 枚举）
 */
export type AgentName = 'PLANNER' | 'COLLECTOR' | 'WRITER' | 'REVIEWER'
