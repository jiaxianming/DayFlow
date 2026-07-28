import { http } from './index'
import type { IPage } from '@/types/api'
import type { ITaskVO, ITaskCreateDTO, ITaskUpdateDTO, ITaskQuery } from '@/types/task'

/** 分页查询任务（GET /api/tasks） */
export function listTasks(query: ITaskQuery): Promise<IPage<ITaskVO>> {
  return http.get('/tasks', { params: query }) as unknown as Promise<IPage<ITaskVO>>
}

/** 查询单个任务 */
export function getTask(id: string): Promise<ITaskVO> {
  return http.get(`/tasks/${id}`) as unknown as Promise<ITaskVO>
}

/** 新增任务 */
export function createTask(dto: ITaskCreateDTO): Promise<string> {
  return http.post('/tasks', dto) as unknown as Promise<string>
}

/** 修改任务（PUT /api/tasks/{id}） */
export function updateTask(id: string, dto: ITaskUpdateDTO): Promise<void> {
  return http.put(`/tasks/${id}`, dto) as unknown as Promise<void>
}

/** 删除任务 */
export function deleteTask(id: string): Promise<void> {
  return http.delete(`/tasks/${id}`) as unknown as Promise<void>
}

/**
 * 标记任务完成（PATCH /api/tasks/{id}/complete，status→DONE + completedAt=now）
 */
export function completeTask(id: string): Promise<void> {
  return http.patch(`/tasks/${id}/complete`) as unknown as Promise<void>
}
