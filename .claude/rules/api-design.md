# API 设计规范

## 1. RESTful 原则

*   使用名词复数表示资源：`/users`、`/orders`
*   使用HTTP方法表达操作：
    *   `GET`    - 获取资源
    *   `POST`   - 创建资源
    *   `PUT`    - 完整更新资源
    *   `PATCH`  - 部分更新资源
    *   `DELETE` - 删除资源
*   嵌套资源表示关系：`/users/{userId}/orders`
*   查询请求，超过3个参数，封装为对象，如：XxxRequestDTO

## 2. 请求与响应格式

*   统一使用JSON（Content-Type: application/json）
*   请求体示例（POST /users）：

```json
{
  "username": "john_doe",
  "email": "john@example.com"
}
```

*   响应体封装统一结构（基于 `Result.java`）

```json
{
  "code": 200,
  "msg": "success",
  "data": { ... }
}
```

*   字段说明：
    *   `code` - 状态码，200 表示成功，非 200 表示失败
    *   `msg` - 提示信息
    *   `data` - 响应数据，可为任意类型

*   成功响应示例：

```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "id": 1,
    "username": "john_doe"
  }
}
```

*   失败响应示例：

```json
{
  "code": 400,
  "msg": "参数校验失败"
}
```

*   列表响应直接用 MyBatis-Plus `IPage` 序列化（Controller 返回 `Result<IPage<XxxVO>>`）

```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "records": [...],
    "total": 100,
    "size": 20,
    "current": 1,
    "pages": 5
  }
}
```

*   分页响应字段说明（`IPage` 标准字段）：
    *   `records` - 当前页数据列表
    *   `total` - 总记录数
    *   `size` - 每页条数
    *   `current` - 当前页码，从 1 开始
    *   `pages` - 总页数（由 total/size 计算）

*   分页查询参数（各 `XxxQuery` 类，如 `ActivityQuery`/`TaskQuery`/`NoteQuery`/`ReportQuery`）：

| 参数名 | 类型 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- |
| `page` | Integer | 1 | 当前页码（Service 内绑定到 `IPage.current`） |
| `size` | Integer | 20 | 每页条数（Service 内绑定到 `IPage.size`） |

## 3.  命名约定

• 路径使用小写字母、连字符（kebab-case）：/user-profiles
• 查询参数使用驼峰（camelCase）
• JSON字段使用驼峰（camelCase）

## 4. HTTP 状态码与业务语义

*   DayFlow Controller **统一返回 HTTP 200**，外层用 `Result` 包装；业务语义体现在 `Result.code` 字段（详见 `common/ResultCode.java`）。
*   `Result.code` 语义：

| code | 含义 | 触发场景 |
| :--- | :--- | :--- |
| `200` | 成功 | 正常返回 |
| `400` | 参数错误 | `@Valid` 校验失败、请求体格式错误、请求方法不支持 |
| `401` | 未认证 | 缺失/无效 JWT、登录失败（不区分用户是否存在） |
| `403` | 无权限 | 越权操作他人资源 |
| `404` | 资源不存在 | 按 id 查不到记录、无匹配路由 |
| `409` | 业务规则冲突 | `BusinessException` 默认码 |
| `500` | 系统异常 | 未捕获异常兜底 |

*   业务异常用 `BusinessException(ResultCode, String)` 包装，由 `GlobalExceptionHandler` 统一映射为上表 code。

## 5. 版本控制

*   路径前缀 `/api/<resource>`（如 `/api/activities`、`/api/auth/login`），**暂不版本化**。
*   后续确有破坏性变更时再引入版本段（如 `/api/v2/...`），保持向后兼容。

## 6. 文档

*   使用OpenAPI 3.0（SpringDoc）自动生成文档，并部署为Swagger UI
*   所有接口需包含`@Operation`描述和`@ApiResponse`说明
*   示例：

```JAVA
@Operation(summary = "获取用户列表")
@ApiResponses(value = {
    @ApiResponse(responseCode = "200", description = "成功"),
    @ApiResponse(responseCode = "401", description = "未授权")
})
```

## 7. 安全

• 避免在URL中暴露敏感信息（如密码、Token）
• 对输入进行校验（如@Valid + @NotNull等）
• 防止SQL注入，使用参数化查询或ORM
• 限制请求大小，防止DoS攻击
