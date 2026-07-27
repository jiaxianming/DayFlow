import { http } from './index'
import type { ILoginDTO, IRegisterDTO, ILoginVO } from '@/types/auth'

/**
 * 登录（POST /api/auth/login）
 */
export function login(dto: ILoginDTO): Promise<ILoginVO> {
  return http.post('/auth/login', dto) as unknown as Promise<ILoginVO>
}

/**
 * 注册（POST /api/auth/register，注册即登录返回 LoginVO）
 */
export function register(dto: IRegisterDTO): Promise<ILoginVO> {
  return http.post('/auth/register', dto) as unknown as Promise<ILoginVO>
}
