/**
 * 后端统一响应包装 Result<T>（对应 com.dayflow.common.Result）
 */
export interface IResult<T> {
  code: number
  msg: string
  data: T
}

/**
 * 后端 MyBatis-Plus 分页结构 IPage<T>
 */
export interface IPage<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

/**
 * 分页查询基础参数（page 默认 1，size 默认 20，由后端绑定）
 */
export interface IPageQuery {
  page?: number
  size?: number
}
