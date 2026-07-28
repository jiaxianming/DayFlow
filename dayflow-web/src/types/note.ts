import type { IPageQuery } from './api'

/**
 * 学习笔记视图（tags 为后端 String，前端按需拆分显示）
 */
export interface INoteVO {
  id: string
  userId: string
  title: string
  content: string
  tags: string
  createdAt: string
}

/**
 * 新增笔记入参（title/content 必填，对应后端 @NotBlank）
 */
export interface INoteCreateDTO {
  title: string
  content: string
  tags?: string
}

/**
 * 修改笔记入参（全字段可选）
 */
export interface INoteUpdateDTO {
  title?: string
  content?: string
  tags?: string
}

/**
 * 笔记分页查询条件（tags 走后端 LIKE）
 */
export interface INoteQuery extends IPageQuery {
  tags?: string
}
