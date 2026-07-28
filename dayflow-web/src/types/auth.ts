/**
 * 登录入参
 */
export interface ILoginDTO {
  username: string
  password: string
}

/**
 * 注册入参（与登录同结构）
 */
export interface IRegisterDTO {
  username: string
  password: string
}

/**
 * 登录/注册返回视图（注册即登录，复用 LoginVO）
 * userId 为 string：雪花 ID 经 json-bigint 解析
 */
export interface ILoginVO {
  token: string
  userId: string
  username: string
  nickname: string | null
}
