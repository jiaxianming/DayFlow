# DayFlow 项目开发规范

> 本文件为项目级补充规范，通用开发规范请参见用户级配置 `${USER_HOME}/.claude/CLAUDE.md`。
> **当本文件与用户级规范冲突时，以本文件为准。**

---

## 1. 项目概况

DayFlow 是基于 **Spring AI 多智能体**的个人日报/周报生成器（工作汇报 + 学习日报混合型），开源项目。采用编辑部模式 4 Agent（Planner / Collector / Writer / Reviewer）+ 反馈循环（最多重试 2 次），用 `agent_trace` 表可视化 Agent 协作过程。

- **包根**：`com.dayflow`
- **模块**：`dayflow-server`（Spring Boot 后端）、`dayflow-web`（Vue3 前端，M4）
- **路线图**：M0 骨架基建 → M1 数据层 CRUD → M2 Spring AI 接入 → M3 多智能体核心 → M4 前端 → M5 开源工程化
- **设计文档**：`docs/superpowers/specs/`、`docs/superpowers/plans/`

---

## 2. 技术栈

| 组件 | 版本 | 说明 |
|------|------|------|
| Spring Boot | **4.1.x** | 当前主线，Java 21 |
| Spring Framework | 7.0.x | 随 Boot 4 |
| Spring AI | **2.0.x** | M2 引入、M3 多智能体核心；要求 Boot 4.x，**不兼容 Boot 3.x** |
| Java | **21 (LTS)** | |
| 数据持久层 | **MyBatis-Plus 3.5.14+** | 使用 `mybatis-plus-spring-boot4-starter` |
| 数据库 | **MySQL 8.0** | `utf8mb4` / `InnoDB` |
| 向量库 | Redis Stack | M2+，学习笔记 RAG |
| 鉴权 | **jjwt 0.12.x** | JWT，M1 |
| API 文档 | springdoc-openapi（OpenAPI 3） | |
| 前端 | Vue3 + TS + Vite + Pinia + Element Plus | M4 |
| 构建 | Maven 3.9.8（无 Gradle） | |
| LLM Provider | 默认 DeepSeek（OpenAI 兼容），可切 Ollama 本地 | M2，可插拔 |

> 遵循《阿里巴巴 Java 开发手册》；日志用 SLF4J + Logback，业务关键节点 INFO、异常 ERROR，禁止 `System.out`。

---

## 3. 项目结构

```
dayflow-server/src/main/java/com/dayflow/
├── controller/        # 薄层：参数校验 + 调 Service + Result 包装
├── service/           # 厚层：业务编排、实体转换、事务
│   └── impl/          # 接口在 service/，实现在 service/impl/
├── agent/             # 多智能体（M2/M3）
│   ├── orchestration/ # ReportOrchestrationService
│   ├── planner/ collector/ writer/ reviewer/
│   └── tools/         # ReportDataTools（Spring AI @Tool）
├── mapper/            # Mapper 接口（BaseMapper）
├── pojo/              # 数据模型层
│   ├── entity/        # 实体类（与表对应，必须加 Entity 后缀）
│   ├── dto/           # 创建/修改入参（DTO 后缀）
│   ├── query/         # 查询条件对象（Query 后缀）
│   └── vo/            # 出参视图对象（VO 后缀）
├── config/            # MyBatis-Plus / 安全 / OpenAI / Spring AI 配置
└── common/            # Result / ResultCode / 异常 / 常量
```

---

## 4. 项目特定约定

1. **实体类必须加 `Entity` 后缀** —— 遵循全局 §2.3。示例：`UserEntity`、`ActivityEntity`、`ReportEntity`。
2. **主键与字段映射** —— 所有主键 `@TableId(type = IdType.ASSIGN_ID)`（雪花 ID）；所有字段显式 `@TableField("列名")`，不依赖隐式驼峰转换。
3. **`@author` 署名** —— 所有 Java 类 JavaDoc 的 `@author` 统一用 `jiaxianming`。
4. **统一响应** —— `Result<T>`，字段为 `code` / `msg` / `data`；成功 `code = 200`。详见 `common/Result.java`、`common/ResultCode.java`。
5. **状态码语义** —— `ResultCode` 按 HTTP 语义细化：`200` 成功 / `400` 参数错误 / `401` 未认证 / `403` 无权限 / `404` 资源不存在 / `409` 业务规则冲突 / `500` 系统异常。业务异常用 `BusinessException` 包装，由 `GlobalExceptionHandler` 统一处理。
6. **API 路径** —— 统一前缀 `/api/<resource>`（如 `/api/activities`、`/api/auth/login`）；路径用小写 kebab-case，JSON 字段用 camelCase。

---

## 5. 协作约定

- 通用协作流程见用户级规范。
- **分支与提交**：在 `feature/<milestone>-<topic>` 分支上做 task 级提交（review 所需、可 reset）；整支审查通过 + 用户明确授权后才合并 `main`。遵循全局"不自动提交"。
- **回复语言**：所有开发沟通使用中文。
