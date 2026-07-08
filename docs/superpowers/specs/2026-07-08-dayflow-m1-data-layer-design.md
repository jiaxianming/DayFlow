# DayFlow M1 数据层 CRUD 设计

> 日期：2026-07-08
> 里程碑：M1（数据层 CRUD）
> 上游 spec：`docs/superpowers/specs/2026-07-07-dayflow-ai-report-design.md`（整体架构与数据模型）
> 下游：`writing-plans` 据此细化为 TDD task

---

## 1. 目标与范围

M1 交付**纯数据层**：6 张表的 CRUD + JWT 登录 + 数据层基建。不碰 AI（M2/M3）。

**本里程碑交付**：
- Spring Boot 4.1 + MyBatis-Plus（boot4 starter）+ MySQL 8 数据层
- 6 表 entity/mapper + Activity / Note / Task / Report CRUD 接口
- JWT 登录（预置单用户）+ 拦截器鉴权
- ResultCode 码段细化 + GlobalExceptionHandler 扩展（含 M-3 validation/兜底分支补测）
- `init.sql`（DDL + 索引 + 预置 BCrypt 用户）

**明确不做（留后续里程碑）**：
- Report AI 生成（`generateDailyReport`）→ M3
- `agent_trace` 写入 → M3（M1 只建表 + 只读接口）
- Redis 向量库 + 笔记切块 embedding → M2
- 用户注册接口 → 后续版本

---

## 2. 技术基线（M-9 决策落地）

"用最新 Spring AI" ⇒ 基线整体跳一代。M1 本身不引入 Spring AI，但 Boot 版本为 M2 铺路。

| 组件 | 版本 | 说明 |
|------|------|------|
| Spring Boot | **4.1.x** | 当前主线（4.1.0 GA 2026-06-10），Java 21 |
| Spring Framework | 7.0.x | 随 Boot 4 |
| Spring AI | 2.0.x（M2 引入） | 2.0 GA 2026-06-12，要求 Boot 4.x，**不兼容 Boot 3.x** |
| Java | **21 (LTS)** | |
| MyBatis-Plus | **3.5.14+ `mybatis-plus-spring-boot4-starter`** | 专用 Boot4 starter |
| MySQL | 8.0（已装） | `utf8mb4` / `InnoDB` |
| JWT | jjwt 0.12.x | 签发/校验 |
| API 文档 | springdoc-openapi（OpenAPI 3） | |
| 构建 | Maven 3.9.8 | |

**风险与 fallback**：
- ⚠️ Spring AI 2.0 GA 仅约 1 个月，API 较上游 spec（基于 1.0）有破坏性变更 —— **M2 brainstorming 时重写 Agent 代码示例**，M1 不受影响
- ⚠️ `mybatis-plus-spring-boot4-starter` 有已知 issue（[#7009](https://github.com/baomidou/mybatis-plus/issues/7009)，`mybatis-spring` 版本过低致启动报错）。**Task 1 优先验证启动**；若卡死，fallback 降级为 `mybatis-plus-spring-boot3-starter` + Boot 3.4 + Spring AI 1.0（成熟稳定线）

---

## 3. 数据库设计

### 3.1 工程约定

| 项 | 约定 |
|----|------|
| 字符集 / 排序 | `utf8mb4` / `utf8mb4_0900_ai_ci` |
| 引擎 | InnoDB |
| 主键 | `bigint`，`@TableId(type = IdType.ASSIGN_ID)`（雪花 ID） |
| 时间字段 | `created_at` + `updated_at`（MyBatis-Plus 自动填充） |
| 软删除 | **不加**（YAGNI，单用户工具硬删足够） |
| Schema 管理 | `src/main/resources/schema.sql`（dev profile 经 `spring.sql.init` 自动执行）；项目根 `init.sql` 同内容供手动初始化 / 开源展示。不引入 Flyway，M5 再评估 |
| status / category | `varchar` + Java `enum` 映射 |

### 3.2 DDL（`init.sql`）

```sql
CREATE DATABASE IF NOT EXISTS dayflow DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE dayflow;

-- 用户
CREATE TABLE `user` (
  `id`            BIGINT       NOT NULL COMMENT '雪花ID',
  `username`      VARCHAR(64)  NOT NULL COMMENT '登录名',
  `nickname`      VARCHAR(64)  NULL     COMMENT '昵称',
  `password_hash` VARCHAR(128) NOT NULL COMMENT 'BCrypt',
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB COMMENT='用户';

-- 工作活动
CREATE TABLE `activity` (
  `id`          BIGINT      NOT NULL,
  `user_id`     BIGINT      NOT NULL,
  `content`     TEXT        NOT NULL COMMENT '活动描述',
  `category`    VARCHAR(16) NOT NULL DEFAULT 'OTHER' COMMENT 'WORK/STUDY/MEETING/OTHER',
  `occurred_at` DATETIME    NULL     COMMENT '发生时间',
  `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB COMMENT='工作活动';

-- 内置轻量待办
CREATE TABLE `task` (
  `id`           BIGINT      NOT NULL,
  `user_id`      BIGINT      NOT NULL,
  `title`        VARCHAR(200) NOT NULL,
  `status`       VARCHAR(16) NOT NULL DEFAULT 'TODO' COMMENT 'TODO/DOING/DONE',
  `completed_at` DATETIME    NULL     COMMENT '完成时间（供周报统计）',
  `created_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB COMMENT='内置轻量待办';

-- 学习笔记（M1 只存原文，切块 embedding 留 M2）
CREATE TABLE `note` (
  `id`         BIGINT        NOT NULL,
  `user_id`    BIGINT        NOT NULL,
  `title`      VARCHAR(200)  NOT NULL,
  `content`    MEDIUMTEXT    NOT NULL COMMENT '原文',
  `tags`       VARCHAR(200)  NULL     COMMENT '逗号分隔',
  `created_at` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB COMMENT='学习笔记';

-- 报告（M1 只存元信息与最终稿字段，AI 生成留 M3）
CREATE TABLE `report` (
  `id`           BIGINT        NOT NULL,
  `user_id`      BIGINT        NOT NULL,
  `type`         VARCHAR(8)    NOT NULL COMMENT 'DAILY/WEEKLY',
  `period_start` DATE          NOT NULL,
  `period_end`   DATE          NOT NULL,
  `title`        VARCHAR(200)  NULL,
  `content`      MEDIUMTEXT    NULL     COMMENT '最终 Markdown',
  `status`       VARCHAR(16)   NOT NULL DEFAULT 'GENERATING' COMMENT 'GENERATING/GENERATED/FAILED',
  `error_msg`    VARCHAR(500)  NULL,
  `token_usage`  INT           NULL     DEFAULT 0,
  `created_at`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_type_period` (`user_id`, `type`, `period_start`)
) ENGINE=InnoDB COMMENT='报告';

-- Agent 执行轨迹（M1 只建表 + 只读；写入留 M3）
CREATE TABLE `agent_trace` (
  `id`             BIGINT   NOT NULL,
  `report_id`      BIGINT   NOT NULL,
  `agent_name`     VARCHAR(16) NOT NULL COMMENT 'PLANNER/COLLECTOR/WRITER/REVIEWER',
  `step`           INT      NOT NULL,
  `input_summary`  TEXT     NULL,
  `output_summary` TEXT     NULL,
  `tokens`         INT      NULL DEFAULT 0,
  `latency_ms`     INT      NULL DEFAULT 0,
  `retry_count`    INT      NOT NULL DEFAULT 0,
  `created_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_report_id` (`report_id`)
) ENGINE=InnoDB COMMENT='Agent 执行轨迹';

-- 预置单用户（明文 dayflow123，BCrypt hash）
INSERT INTO `user` (`id`, `username`, `nickname`, `password_hash`)
VALUES (1, 'admin', '管理员', '$2a$10$<bcrypt_hash_of_dayflow123>');
```

> 预置用户的 BCrypt hash 在 Task 2 生成真实值替换占位。

---

## 4. 包结构

```
dayflow-server/src/main/java/com/dayflow/
├── controller/        # 薄层：参数校验 + 调 Service + Result 包装
├── service/           # 厚层：业务编排、实体转换、事务
│   └── impl/
├── mapper/            # MyBatis-Plus Mapper（BaseMapper）
├── pojo/
│   ├── entity/        # 实体（Entity 后缀）：UserEntity / ActivityEntity ...
│   ├── dto/           # 创建/修改入参（DTO 后缀）
│   ├── query/         # 查询条件（Query 后缀）
│   └── vo/            # 出参视图对象（VO 后缀）
├── config/            # MyBatis-Plus / 安全 / OpenAPI 配置
├── common/            # Result / ResultCode / 异常 / UserContext / 常量
└── (agent/ 等 M2/M3 再加)
```

**命名规范**（遵循全局 CLAUDE.md §2.3）：
- 实体必须 `Entity` 后缀：`UserEntity`、`ActivityEntity`、`TaskEntity`、`NoteEntity`、`ReportEntity`、`AgentTraceEntity`
- 入参：`ActivityCreateDTO` / `ActivityUpdateDTO`；查询：`ActivityQuery`
- 出参：`ActivityVO`
- 主键 `@TableId(type = IdType.ASSIGN_ID)`；字段显式 `@TableField("列名")`

---

## 5. 统一响应与码段（M-5）

### 5.1 Result 字段

`Result<T>`：`code` / `msg` / `data`（已实现，字段 `msg`，成功 `code=200`）。

### 5.2 ResultCode 细化（HTTP 语义码，去重 + 补全）

| 枚举 | code | 用途 |
|------|------|------|
| SUCCESS | 200 | 成功 |
| PARAM_ERROR | 400 | 参数校验失败（@Valid） |
| UNAUTHORIZED | 401 | 未登录 / token 无效 |
| FORBIDDEN | **403** | 无权限（新增） |
| NOT_FOUND | **404** | 资源不存在（新增） |
| BUSINESS_ERROR | **409** | 业务规则冲突（原 500，改） |
| SYSTEM_ERROR | 500 | 系统异常 |

### 5.3 GlobalExceptionHandler 扩展（含 M-3 补测）

| 异常 | 映射码 |
|------|--------|
| `BusinessException` | 自带 code |
| `MethodArgumentNotValidException` | 400（M-3 补测） |
| `HttpMessageNotReadableException` | 400 |
| `NoHandlerFoundException` | 404 |
| `AccessDeniedException` | 403 |
| `Exception`（兜底） | 500 |

---

## 6. JWT 登录与鉴权

### 6.1 登录流程

`POST /api/auth/login`（`LoginDTO`: username + password）
→ UserAuthService 按 username 查 `UserEntity`
→ BCrypt 校验密码
→ JwtUtil 签发 token（claims: userId + username，HS256，有效期 7 天）
→ 返回 `LoginVO`: `{ token, userId, username, nickname }`

### 6.2 组件

- **JwtUtil**：`generate(userId, username)` / `parse(token)` / `verify(token)`，配置 `dayflow.jwt.secret`、`dayflow.jwt.expiration`
- **UserContext**：`ThreadLocal<Long>` 持有当前 userId，`set/get/clear`
- **JwtInterceptor**：拦截 `/api/**`，放行 `/api/auth/login`、`/api/health/**`；解析 Authorization Bearer token → 注入 UserContext；无效 → `BusinessException(UNAUTHORIZED)`
- **WebConfig**：`addInterceptors` 注册 JwtInterceptor

### 6.3 配置（application.yml + .env.example）

```yaml
dayflow:
  jwt:
    secret: ${DAYFLOW_JWT_SECRET:change-me-in-prod}
    expiration: 7d
```

---

## 7. CRUD 接口清单（M1 范围）

| 资源 | 接口 | 说明 |
|------|------|------|
| Auth | `POST /api/auth/login` | 预置用户登录 |
| Activity | `GET /api/activities`（Query: 日期范围 + category） / `POST` / `PUT /{id}` / `DELETE /{id}` / `GET /{id}` | 工作活动 |
| Task | `GET /api/tasks`（Query）/ `POST` / `PUT /{id}` / `DELETE /{id}` / `GET /{id}` / `PATCH /{id}/complete` | status: TODO/DOING/DONE |
| Note | `GET /api/notes`（Query）/ `POST` / `PUT /{id}` / `DELETE /{id}` / `GET /{id}` | M1 只存原文 |
| Report | `GET /api/reports`（Query）/ `POST`（仅元信息）/ `GET /{id}` / `DELETE /{id}` | **不做 AI 生成** |
| agent_trace | `GET /api/reports/{id}/traces` | 只读，M3 才写 |

> 所有查询用 MyBatis-Plus `LambdaQueryWrapper`，不写 XML；分页用 `IPage`（`records/total/current/size`）。

---

## 8. 测试策略（含 M-3）

| 层 | 方式 | 覆盖 |
|----|------|------|
| Controller | `@WebMvcTest` + MockMvc + jsonPath，`@MockBean` Service | 正常 200、@Valid 失败 400（M-3）、404、409、401、兜底 500 |
| Service | Mockito 单测（mock Mapper） | CRUD 逻辑、entity↔VO/DTO 转换、空数据、不存在、状态流转 |
| JwtUtil | 单测 | 签发 / 解析 / 过期 / 伪造 |
| GlobalExceptionHandler | `@WebMvcTest` 内 | 5 个异常分支（M-3 兜底补测） |

**DB 集成测试**：Docker 未装 ⇒ Testcontainers 不可用；H2 与 MySQL 方言失真 ⇒ **M1 不写真 DB 集成测试**。Mapper 只继承 `BaseMapper` + `LambdaQueryWrapper`，CRUD 由 MyBatis-Plus 保证。真 DB 端到端靠手动跑 `init.sql` + 接口冒烟；**集成测试留 M5**（Testcontainers 入 CI）。

---

## 9. Task 拆分（writing-plans 输入）

按依赖与风险序：

1. **T1 基线验证** — Boot 3.3.5→4.1 升级 + `mybatis-plus-spring-boot4-starter` + MySQL 驱动 + jjwt，验证启动（先排 #7009 坑，踩坑走 fallback）
2. **T2 数据层基建** — MySQL 数据源 + MyBatis-Plus 配置（自动填充 created/updated、分页插件）+ `schema.sql`（6 表 + 索引 + 预置 BCrypt 用户，dev profile 自动执行）+ 项目根 `init.sql`（同内容）
3. **T3 码段与异常扩展**（M-5 + M-3）— ResultCode 细化(403/404/409) + GlobalExceptionHandler 5 分支 + 补测
4. **T4 6 表 entity + mapper** — `UserEntity`/`ActivityEntity`/`TaskEntity`/`NoteEntity`/`ReportEntity`/`AgentTraceEntity` + BaseMapper
5. **T5 JWT 鉴权** — JwtUtil + UserContext + JwtInterceptor + WebConfig + AuthController(`/api/auth/login`) + LoginDTO/VO + UserAuthService
6. **T6 Activity CRUD** — Query/DTO/VO + Service(接口+impl) + Controller，TDD
7. **T7 Task CRUD** — 含 `complete` 状态流转
8. **T8 Note CRUD**
9. **T9 Report CRUD + agent_trace 只读**
10. **T10 全量验证收尾** — `mvn test` 全绿 + 启动冒烟 + tag `m1-complete`

依赖链：T1 → T2 → T4 → T5 → {T6, T7, T8, T9} → T10；T6–T9 互相独立可并行。**T1 先行排雷**（最大不确定性优先）。

---

## 10. 顺带修正的规范文档（本里程碑已落地）

- `.claude/CLAUDE.md`：从 linbi 遗留模板重写为 DayFlow 规范（包名 `com.dayflow`、技术栈 Boot 4.1 + Spring AI 2.0、`pojo/` 结构、实体加 Entity 后缀、`@author jiaxianming`、Result `code/msg/data`）
- `.claude/rules/api-design.md`：响应 `code:0`→`200`、`0 表示成功`→`200 表示成功`（`msg` 本就对）；待清理：`PageUtils.java`/`Query.java` 引用、真 HTTP 状态码风格（201/204）、`/api/v1` 前缀
- `common/Result.java` + 测试：字段 `message`→`msg`（已验证 7 测试全绿）
- 待处理：工作区暂存的 `common/PageUtils.java.bak`（renren-fast 遗留，去留待定）
