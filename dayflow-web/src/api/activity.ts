import { http } from './index'
import type { IPage } from '@/types/api'
import type { IActivityVO, IActivityCreateDTO, IActivityUpdateDTO, IActivityQuery } from '@/types/activity'

/**
 * 分页查询活动（GET /api/activities，query 参数绑定）
 */
export function listActivities(query: IActivityQuery): Promise<IPage<IActivityVO>> {
  return http.get('/activities', { params: query }) as unknown as Promise<IPage<IActivityVO>>
}

/**
 * 查询单个活动
 */
export function getActivity(id: string): Promise<IActivityVO> {
  return http.get(`/activities/${id}`) as unknown as Promise<IActivityVO>
}

/**
 * 新增活动（返回新 id，雪花 ID 经 bigint 解析为 string）
 */
export function createActivity(dto: IActivityCreateDTO): Promise<string> {
  return http.post('/activities', dto) as unknown as Promise<string>
}

/**
 * 修改活动（PUT /api/activities/{id}）
 */
export function updateActivity(id: string, dto: IActivityUpdateDTO): Promise<void> {
  return http.put(`/activities/${id}`, dto) as unknown as Promise<void>
}

/**
 * 删除活动
 */
export function deleteActivity(id: string): Promise<void> {
  return http.delete(`/activities/${id}`) as unknown as Promise<void>
}
