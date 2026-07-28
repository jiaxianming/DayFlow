import { http } from './index'
import type { IPage } from '@/types/api'
import type { INoteVO, INoteCreateDTO, INoteUpdateDTO, INoteQuery } from '@/types/note'

/** 分页查询笔记（GET /api/notes） */
export function listNotes(query: INoteQuery): Promise<IPage<INoteVO>> {
  return http.get('/notes', { params: query }) as unknown as Promise<IPage<INoteVO>>
}

/** 查询单个笔记 */
export function getNote(id: string): Promise<INoteVO> {
  return http.get(`/notes/${id}`) as unknown as Promise<INoteVO>
}

/** 新增笔记 */
export function createNote(dto: INoteCreateDTO): Promise<string> {
  return http.post('/notes', dto) as unknown as Promise<string>
}

/** 修改笔记（PUT /api/notes/{id}） */
export function updateNote(id: string, dto: INoteUpdateDTO): Promise<void> {
  return http.put(`/notes/${id}`, dto) as unknown as Promise<void>
}

/** 删除笔记 */
export function deleteNote(id: string): Promise<void> {
  return http.delete(`/notes/${id}`) as unknown as Promise<void>
}
