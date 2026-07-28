import type { TaskStatus } from './enums'
import type { IPageQuery } from './api'

/**
 * 任务视图（completedAt 未完成时为 null）
 */
export interface ITaskVO {
  id: string
  userId: string
  title: string
  status: TaskStatus
  completedAt: string | null
  createdAt: string
}

/**
 * 新增任务入参（title 必填；status 可选，后端缺省 TODO）
 */
export interface ITaskCreateDTO {
  title: string
  status?: TaskStatus
}

/**
 * 修改任务入参（全字段可选）
 */
export interface ITaskUpdateDTO {
  title?: string
  status?: TaskStatus
}

/**
 * 任务分页查询条件（按状态过滤）
 */
export interface ITaskQuery extends IPageQuery {
  status?: TaskStatus
}
