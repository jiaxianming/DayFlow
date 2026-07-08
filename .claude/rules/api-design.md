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

*   列表响应应包含分页信息（基于 `PageUtils.java`）

```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "list": [...],
    "currPage": 1,
    "pageSize": 20,
    "totalPage": 5,
    "totalCount": 100
  }
}
```

*   分页字段说明：
    *   `list` - 当前页数据列表
    *   `currPage` - 当前页码，从 1 开始
    *   `pageSize` - 每页条数
    *   `totalPage` - 总页数
    *   `totalCount` - 总记录数

*   分页查询参数（基于 `Query.java`）：

| 参数名 | 类型 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- |
| `page` | Long | 1 | 当前页码 |
| `limit` | Long | 10 | 每页条数 |
| `orderField` | String | - | 排序字段（防止SQL注入） |
| `order` | String | - | 排序方向（ASC/DESC） |

## 3.  命名约定

• 路径使用小写字母、连字符（kebab-case）：/user-profiles
• 查询参数使用驼峰（camelCase）
• JSON字段使用驼峰（camelCase）

## 4. HTTP状态码

• 200 OK - 成功
• 201 Created - 创建成功
• 204 No Content - 删除成功（无返回体）
• 400 Bad Request - 请求参数错误
• 401 Unauthorized - 未认证
• 403 Forbidden - 无权限
• 404 Not Found - 资源不存在
• 409 Conflict - 资源冲突（如唯一约束）
• 422 Unprocessable Entity - 业务校验失败
• 500 Internal Server Error - 服务器内部错误

## 5. 版本控制

*   在URL中嵌入版本号：`/api/v1/users`
*   保持向后兼容，重大变更时提升主版本号

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
