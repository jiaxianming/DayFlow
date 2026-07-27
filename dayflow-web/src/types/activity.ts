import type { ActivityCategory } from './enums'
import type { IPageQuery } from './api'

/**
 * 活动视图（id/userId 为 string：雪花 ID 经 json-bigint 解析）
 */
export interface IActivityVO {
  id: string
  userId: string
  content: string
  category: ActivityCategory
  occurredAt: string
  createdAt: string
}

/**
 * 新增活动入参（occurredAt 可选，后端缺省取当前时间）
 */
export interface IActivityCreateDTO {
  content: string
  category: ActivityCategory
  occurredAt?: string
}

/**
 * 修改活动入参（全字段可选）
 */
export interface IActivityUpdateDTO {
  content?: string
  category?: ActivityCategory
  occurredAt?: string
}

/**
 * 活动分页查询条件
 */
export interface IActivityQuery extends IPageQuery {
  startTime?: string
  endTime?: string
  category?: ActivityCategory
}
