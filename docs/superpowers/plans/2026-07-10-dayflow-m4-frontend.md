# DayFlow M4 前端 Web 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 M3 后端多智能体核心之上落地 Vue3 前端 Web，打通「登录注册 → 录入 Activity/Note/Task → 触发日报生成 → 轮询查看 Markdown 报告 + Agent 协作时间线 → 历史列表」的端到端可用闭环，并补齐后端注册端点。

**Architecture:** Vue3 (`<script setup>`) + TS + Vite（dev proxy `/api` → 后端 8080）+ Pinia（仅 authStore / reportStore 两个全局 store，列表数据在 view 内管理）+ Element Plus 默认主题。axios 实例统一拦截：请求注入 JWT、响应按 `Result.code` 分流、`transformResponse` 用 json-bigint 保雪花 ID 精度。报告异步生成经 `useReportPolling` composable 每 2.5s 轮询 `status` + `traces`，三态（GENERATING/GENERATED/FAILED）切换，`onUnmounted` 清理定时器。LLM 产出的 Markdown 必经 DOMPurify 净化防 XSS。后端仅补 `POST /api/auth/register`（注册即登录返回 LoginVO），不动多智能体核心。

**Tech Stack:** Vue 3.5 / TypeScript 5.6 / Vite 5.4 / Pinia 2.3 / Vue Router 4.5 / Element Plus 2.9 / axios 1.7 / markdown-it 14 / DOMPurify 3 / json-bigint 1 / Vitest 2 + Vue Test Utils 2（前端）；Spring Boot 4.1 / Spring AI 2.0 / Java 21 / MyBatis-Plus 3.5.14 / jjwt 0.12（后端注册端点）。

## Global Constraints

- **`@author jiaxianming`** —— 后端新增 Java 类 JavaDoc 统一署名。
- **后端命名**：实体 `Entity`、入参 `DTO`、出参 `VO`、查询 `Query`；`ResultCode` 常量名为 `PARAM_ERROR(400)` / `UNAUTHORIZED(401)` / `FORBIDDEN(403)` / `NOT_FOUND(404)` / `BUSINESS_ERROR(409)` / `SYSTEM_ERROR(500)`（注意：是 `PARAM_ERROR` 非 `BAD_REQUEST`）。
- **统一响应** `Result<T>`（`code` / `msg` / `data`）；业务异常 `BusinessException(ResultCode, String)` 由 `GlobalExceptionHandler` 统一映射；Controller 统一返回 HTTP 200，业务语义体现在 `Result.code`。
- **雪花 ID 精度**：后端保留 Long（不改 M1-M3 序列化契约）；前端所有 id 类字段（`id` / `userId` / `reportId`）TS 类型为 `string`，axios `transformResponse` 用 `json-bigint({ storeAsString: true })` 解析。
- **前端命名**（遵循用户级 CLAUDE.md §3）：Vue 组件文件 PascalCase；TS 工具文件 kebab-case；目录名 kebab-case；CSS 类名 kebab-case + BEM；变量/函数 camelCase；常量 UPPER_SNAKE_CASE；TS 接口 `I` 前缀。
- **前端代码风格**：`<script setup lang="ts>`；基本类型 `ref()`、对象 `reactive()`；派生状态 `computed()`；`<style scoped>`；不用 `!important`；`v-for` 必带 `:key`，`v-if` 与 `v-for` 不同元素。
- **安全铁律**：LLM 生成 HTML 必经 `DOMPurify.sanitize` 后再注入；token 存 localStorage，请求拦截器注入 `Authorization: Bearer <token>`。
- **前端测试**：Vitest 全部 mock（不连真后端、不依赖网络），保证测试独立性；用 `vi.useFakeTimers` 测轮询。
- **后端测试**：沿用 M1 范式 —— `@WebMvcTest` 切片（排除 `WebConfig` + `@Import(GlobalExceptionHandler.class)` + `@MockitoBean` 注入 service/JwtUtil）；`UserAuthServiceImplTest` 是 `@SpringBootTest`（真 DB + schema.sql），register 用例加 `@Transactional` 回滚。
- **包管理**：npm；前端独立 `dayflow-web/package.json`，与 `dayflow-server/` 平级。
- **提交**：不自动提交；每 task 在 `feature/m4-frontend` 分支做 task 级提交（review 所需、可 reset）；**commit 前先 `git branch --show-current` 校验分支**（本环境 Bash 分支状态不持久，曾误把 M2 docs 提到 main）；整支审查通过 + 用户明确授权后才合并 `main`。

---

## File Structure

### 前端新增（`dayflow-web/`，全新模块）

| 路径 | 职责 |
|---|---|
| `dayflow-web/package.json` | 依赖与脚本（dev/build/test/preview） |
| `dayflow-web/vite.config.ts` | Vite + `@vitejs/plugin-vue` + dev proxy `/api`→`localhost:8080` + Vitest 配置（jsdom + alias `@`→`src`） |
| `dayflow-web/tsconfig.json` / `tsconfig.app.json` / `tsconfig.node.json` | TS 配置（严格模式、`@` 路径别名） |
| `dayflow-web/index.html` | 入口 HTML |
| `dayflow-web/src/main.ts` | 应用入口：创建 app + 装 Pinia/Router/ElementPlus + 挂载 |
| `dayflow-web/src/App.vue` | 根组件（仅 `<router-view/>`） |
| `dayflow-web/src/types/api.ts` | `Result<T>` / `IPage<T>` 通用响应类型 |
| `dayflow-web/src/types/enums.ts` | `ActivityCategory` / `TaskStatus` / `ReportType` / `ReportStatus` / `AgentName` 字符串字面量联合 |
| `dayflow-web/src/types/activity.ts` `note.ts` `task.ts` `report.ts` `auth.ts` | 各业务 VO/DTO/Query 类型（id 为 string） |
| `dayflow-web/src/api/index.ts` | axios 实例 + 请求拦截器（注入 JWT）+ 响应拦截器（按 code 分流）+ `transformResponse`（json-bigint） |
| `dayflow-web/src/api/auth.ts` | `login(LoginDTO)` / `register(RegisterDTO)` |
| `dayflow-web/src/api/activity.ts` | Activity CRUD（list/get/create/update/delete） |
| `dayflow-web/src/api/note.ts` | Note CRUD |
| `dayflow-web/src/api/task.ts` | Task CRUD + `complete(id)` |
| `dayflow-web/src/api/report.ts` | `generate` / `getById` / `page` / `listTraces` / `delete` |
| `dayflow-web/src/stores/auth.ts` | `useAuthStore`：token/userId/username/nickname + login/register/logout |
| `dayflow-web/src/stores/report.ts` | `useReportStore`：currentReport/traces/isGenerating + triggerGenerate |
| `dayflow-web/src/composables/useReportPolling.ts` | 轮询 status+traces、三态停止、onUnmounted 清理 |
| `dayflow-web/src/router/index.ts` | 路由表 + `beforeEach` 守卫 |
| `dayflow-web/src/layouts/AppLayout.vue` | 侧边栏 el-menu + 主区 router-view + 用户信息/登出 |
| `dayflow-web/src/components/common/` | 基础通用组件占位（按需） |
| `dayflow-web/src/components/MarkdownView.vue` | markdown-it 渲染 + DOMPurify 净化 |
| `dayflow-web/src/components/AgentTimeline.vue` | el-timeline 渲染 4 Agent 轨迹 + 返工徽章 |
| `dayflow-web/src/views/auth/LoginView.vue` | 登录页 |
| `dayflow-web/src/views/auth/RegisterView.vue` | 注册页 |
| `dayflow-web/src/views/input/InputView.vue` | 三 tab 容器 |
| `dayflow-web/src/views/input/panels/ActivityPanel.vue` | Activity CRUD 面板 |
| `dayflow-web/src/views/input/panels/NotePanel.vue` | Note CRUD 面板 |
| `dayflow-web/src/views/input/panels/TaskPanel.vue` | Task CRUD + complete 面板 |
| `dayflow-web/src/views/report/ReportView.vue` | 双栏：左 MarkdownView + 右 AgentTimeline + 生成触发区 |
| `dayflow-web/src/views/history/HistoryView.vue` | el-table 分页 + 状态 tag + 跳详情 + 删除 |
| `dayflow-web/src/views/NotFoundView.vue` | 404 |
| `dayflow-web/src/setupTests.ts` | Vitest 全局配置（如需） |

### 后端新增 / 修改（`dayflow-server/`，仅注册端点）

| 路径 | 改动 |
|---|---|
| `pojo/dto/RegisterDTO.java` | **新增**：username `@NotBlank` + password `@NotBlank` |
| `service/UserAuthService.java` | **修改**：接口新增 `LoginVO register(RegisterDTO dto)` |
| `service/impl/UserAuthServiceImpl.java` | **修改**：实现 register（查重→BCrypt→insert→签 JWT→LoginVO） |
| `controller/AuthController.java` | **修改**：新增 `POST /api/auth/register` |
| `config/WebConfig.java` | **修改**：`excludePathPatterns` 加 `/api/auth/register` |
| `src/test/.../controller/AuthControllerTest.java` | **新增**：register 端点切片测试 |
| `src/test/.../service/impl/UserAuthServiceImplTest.java` | **修改**：补 register 成功/重复/加密用例 |

---

## Task 1: 前端 Vite 脚手架 + 基建

**Files:**
- Create: `dayflow-web/package.json`
- Create: `dayflow-web/vite.config.ts`
- Create: `dayflow-web/tsconfig.json`
- Create: `dayflow-web/tsconfig.app.json`
- Create: `dayflow-web/tsconfig.node.json`
- Create: `dayflow-web/index.html`
- Create: `dayflow-web/env.d.ts`
- Create: `dayflow-web/.gitignore`
- Create: `dayflow-web/src/main.ts`
- Create: `dayflow-web/src/App.vue`
- Create: `dayflow-web/src/setupTests.ts`
- Test: `dayflow-web/src/__tests__/smoke.test.ts`

**Interfaces:**
- Consumes: 无（起点 task）
- Produces: 可运行的 Vite dev server（proxy `/api`→`localhost:8080`）、可通过的 Vitest 套件、`@`→`src` 路径别名、ElementPlus/Pinia/Router 已注册的应用入口。后续所有 task 在此骨架上扩展。

- [ ] **Step 1: 创建 `dayflow-web/package.json`（锁定版本）**

```json
{
  "name": "dayflow-web",
  "version": "0.1.0",
  "private": true,
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "vue-tsc --noEmit && vite build",
    "preview": "vite preview",
    "test": "vitest run",
    "test:watch": "vitest"
  },
  "dependencies": {
    "vue": "^3.5.13",
    "vue-router": "^4.5.0",
    "pinia": "^2.3.0",
    "element-plus": "^2.9.1",
    "@element-plus/icons-vue": "^2.3.1",
    "axios": "^1.7.9",
    "markdown-it": "^14.1.0",
    "dompurify": "^3.2.3",
    "json-bigint": "^1.0.0"
  },
  "devDependencies": {
    "@vitejs/plugin-vue": "^5.2.1",
    "@vue/test-utils": "^2.4.6",
    "@vue/tsconfig": "^0.7.0",
    "jsdom": "^25.0.1",
    "typescript": "~5.6.3",
    "vite": "^5.4.11",
    "vitest": "^2.1.8",
    "vue-tsc": "^2.1.10",
    "@types/markdown-it": "^14.1.2",
    "@types/json-bigint": "^1.0.4"
  }
}
```

> 说明：`dompurify@3.x` 自带 TypeScript 类型，无需 `@types/dompurify`。

- [ ] **Step 2: 创建 `dayflow-web/vite.config.ts`（含 proxy + Vitest）**

```typescript
/// <reference types="vitest" />
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

/**
 * Vite 构建配置
 * - dev server proxy：/api → 后端 localhost:8080（规避联调跨域）
 * - 路径别名 @ → src
 * - Vitest：jsdom 环境 + 同别名
 */
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/setupTests.ts'],
  },
})
```

- [ ] **Step 3: 创建 tsconfig 系列**

`dayflow-web/tsconfig.json`（根，仅做引用聚合）：

```json
{
  "files": [],
  "references": [
    { "path": "./tsconfig.app.json" },
    { "path": "./tsconfig.node.json" }
  ]
}
```

`dayflow-web/tsconfig.app.json`（应用代码）：

```json
{
  "extends": "@vue/tsconfig/tsconfig.dom.json",
  "compilerOptions": {
    "composite": true,
    "tsBuildInfoFile": "./node_modules/.tmp/tsconfig.app.tsbuildinfo",
    "baseUrl": ".",
    "paths": {
      "@/*": ["./src/*"]
    }
  },
  "include": ["src/**/*.ts", "src/**/*.tsx", "src/**/*.vue", "env.d.ts"]
}
```

`dayflow-web/tsconfig.node.json`（构建脚本侧）：

```json
{
  "extends": "@tsconfig/node20/tsconfig.json",
  "compilerOptions": {
    "composite": true,
    "tsBuildInfoFile": "./node_modules/.tmp/tsconfig.node.tsbuildinfo",
    "module": "ESNext",
    "moduleResolution": "Bundler",
    "types": ["node"]
  },
  "include": ["vite.config.ts"]
}
```

> 若 `@tsconfig/node20` 未装，将其替换为内联严格配置（`"strict": true, "noEmit": true`）。优先在 Step 6 `npm install` 后确认；若缺失，给 `tsconfig.node.json` 加 `"devDependencies": { "@tsconfig/node20": "^20.1.4" }` 并重装。**简化方案（推荐）**：直接用下面的自包含版本，避免额外依赖：

```json
{
  "compilerOptions": {
    "composite": true,
    "tsBuildInfoFile": "./node_modules/.tmp/tsconfig.node.tsbuildinfo",
    "target": "ES2022",
    "lib": ["ES2023"],
    "module": "ESNext",
    "moduleResolution": "Bundler",
    "strict": true,
    "noEmit": true,
    "skipLibCheck": true,
    "types": ["node"]
  },
  "include": ["vite.config.ts"]
}
```

- [ ] **Step 4: 创建 `dayflow-web/env.d.ts`（Vue 文件类型声明）**

```typescript
/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<Record<string, unknown>, Record<string, unknown>, unknown>
  export default component
}
```

- [ ] **Step 5: 创建 `dayflow-web/index.html`**

```html
<!DOCTYPE html>
<html lang="zh-CN">
  <head>
    <meta charset="UTF-8" />
    <link rel="icon" href="/favicon.ico" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>DayFlow - AI 日报生成器</title>
  </head>
  <body>
    <div id="app"></div>
    <script type="module" src="/src/main.ts"></script>
  </body>
</html>
```

- [ ] **Step 6: 创建 `dayflow-web/.gitignore`**

```gitignore
node_modules
dist
dist-ssr
*.local
.DS_Store
coverage
.vscode/*
!.vscode/extensions.json
*.log
```

- [ ] **Step 7: 创建 `dayflow-web/src/App.vue`（根组件，仅 router-view）**

```vue
<script setup lang="ts">
/**
 * 应用根组件
 * 路由视图由 router 驱动，布局在 AppLayout 内
 */
</script>

<template>
  <router-view />
</template>
```

- [ ] **Step 8: 创建 `dayflow-web/src/setupTests.ts`（Vitest 全局 setup）**

```typescript
/**
 * Vitest 全局 setup
 * 当前为空占位，后续按需补充全局 mock（如 ElMessage）
 */
```

- [ ] **Step 9: 写冒烟测试 `dayflow-web/src/__tests__/smoke.test.ts`（先红）**

```typescript
import { describe, it, expect } from 'vitest'

/**
 * 脚手架冒烟测试：验证 Vitest 配置可用
 */
describe('scaffold smoke', () => {
  it('vitest runs and asserts', () => {
    expect(1 + 1).toBe(2)
  })
})
```

- [ ] **Step 10: 创建 `dayflow-web/src/main.ts`（先简化版，待 Task 4 接 router/store）**

```typescript
/**
 * 应用入口
 * Task 1 仅装 ElementPlus 挂载根组件；
 * Task 4 接入 Pinia + Router 后会在此补 use(pinia)/use(router)。
 */
import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'

const app = createApp(App)
app.use(ElementPlus)
app.mount('#app')
```

- [ ] **Step 11: 安装依赖**

Run: `cd dayflow-web && npm install`
Expected: 依赖安装成功，生成 `node_modules/` 与 `package-lock.json`。

- [ ] **Step 12: 运行冒烟测试（绿）**

Run: `cd dayflow-web && npm test`
Expected: `smoke.test.ts` 1 passed。

- [ ] **Step 13: 验证 dev server 可启动（手动）**

Run: `cd dayflow-web && npm run dev`
Expected: Vite 启动，浏览器访问 `http://localhost:5173` 显示空白页（App.vue 无内容，控制台无报错）。proxy 配置在后端启动后联调时验证。Ctrl+C 停止。

- [ ] **Step 14: 验证类型检查通过**

Run: `cd dayflow-web && npx vue-tsc --noEmit`
Expected: 无类型错误（若报 `@tsconfig/node20` 找不到，按 Step 3 简化方案替换 `tsconfig.node.json`）。

- [ ] **Step 15: 提交**

```bash
git branch --show-current   # 必须是 feature/m4-frontend
git add dayflow-web
git commit -m "feat(m4): 前端 Vite 脚手架 + EP + Vitest 基建"
```

---

## Task 2: 类型定义 + axios 实例 + API 层

**Files:**
- Create: `dayflow-web/src/types/api.ts`
- Create: `dayflow-web/src/types/enums.ts`
- Create: `dayflow-web/src/types/auth.ts`
- Create: `dayflow-web/src/types/activity.ts`
- Create: `dayflow-web/src/types/note.ts`
- Create: `dayflow-web/src/types/task.ts`
- Create: `dayflow-web/src/types/report.ts`
- Create: `dayflow-web/src/utils/format.ts`
- Create: `dayflow-web/src/api/index.ts`
- Create: `dayflow-web/src/api/auth.ts`
- Create: `dayflow-web/src/api/activity.ts`
- Create: `dayflow-web/src/api/note.ts`
- Create: `dayflow-web/src/api/task.ts`
- Create: `dayflow-web/src/api/report.ts`
- Test: `dayflow-web/src/api/__tests__/index.test.ts`

**Interfaces:**
- Consumes: Task 1 的 `@` 别名、Vitest、axios 实例
- Produces:
  - `http`（`@/api/index`）：配置好的 axios 实例 —— 请求拦截器注入 `Authorization: Bearer <token>`（从 localStorage 读 `token`）、响应拦截器对 `Result.code=200` 解包返回 `data`、`code=401` 调用注入的 401 handler、其他 code 走 `ElMessage.error` + reject、网络错误兜底；`transformResponse` 用 `parseBigintJson`
  - `parseBigintJson(data: string): unknown`：雪花 ID 安全解析（`storeAsString: true`），超过 `MAX_SAFE_INTEGER` 的整数转 string
  - `setUnauthorizedHandler(fn: () => void): void`：供 `main.ts` 注册 401 回调（清 authStore + 跳 `/login`），解 `api ↔ router/store` 循环依赖
  - 各 api 模块函数：`login` / `register` / `listActivities` / `getActivity` / `createActivity` / `updateActivity` / `deleteActivity`（Note/Task 对称）+ `completeTask` / `generateReport` / `getReport` / `deleteReport` / `pageReports` / `listTraces`，均返回 `Promise<业务类型>`（已解包，id 为 string）
  - 类型：`IResult<T>` / `IPage<T>` / `IPageQuery` / 五大枚举字面量联合 / 各 VO/DTO/Query（**所有 id 类字段为 `string`**）

- [ ] **Step 1: 创建 `src/types/api.ts`（通用响应类型）**

```typescript
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
```

- [ ] **Step 2: 创建 `src/types/enums.ts`（枚举字面量联合，对应后端枚举名字符串序列化）**

```typescript
/**
 * 活动类别（对应后端 ActivityCategory 枚举）
 */
export type ActivityCategory = 'WORK' | 'STUDY' | 'MEETING' | 'OTHER'

/**
 * 任务状态（对应后端 TaskStatus 枚举）
 */
export type TaskStatus = 'TODO' | 'DOING' | 'DONE'

/**
 * 报告类型（对应后端 ReportType 枚举；M4 运行时只用 DAILY）
 */
export type ReportType = 'DAILY' | 'WEEKLY'

/**
 * 报告状态（对应后端 ReportStatus 枚举）
 */
export type ReportStatus = 'GENERATING' | 'GENERATED' | 'FAILED'

/**
 * Agent 名称（编辑部 4 Agent，对应后端 AgentName 枚举）
 */
export type AgentName = 'PLANNER' | 'COLLECTOR' | 'WRITER' | 'REVIEWER'
```

- [ ] **Step 3: 创建 `src/types/auth.ts`**

```typescript
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
```

- [ ] **Step 4: 创建 `src/types/activity.ts`**

```typescript
import type { ActivityCategory } from './enums'
import type { IPageQuery } from './api'

/**
 * 活动视图（id/userId 为 string：雪花 ID 经 json-bigint 解析）
 */
export interface IActivityVO {
  id: string
  userId: string
  content: string
  category: ActivityCategory
  occurredAt: string
  createdAt: string
}

/**
 * 新增活动入参（occurredAt 可选，后端缺省取当前时间）
 */
export interface IActivityCreateDTO {
  content: string
  category: ActivityCategory
  occurredAt?: string
}

/**
 * 修改活动入参（全字段可选）
 */
export interface IActivityUpdateDTO {
  content?: string
  category?: ActivityCategory
  occurredAt?: string
}

/**
 * 活动分页查询条件
 */
export interface IActivityQuery extends IPageQuery {
  startTime?: string
  endTime?: string
  category?: ActivityCategory
}
```

- [ ] **Step 5: 创建 `src/types/note.ts`**

```typescript
import type { IPageQuery } from './api'

/**
 * 学习笔记视图（tags 为后端 String，前端按需拆分显示）
 */
export interface INoteVO {
  id: string
  userId: string
  title: string
  content: string
  tags: string
  createdAt: string
}

/**
 * 新增笔记入参（title/content 必填，对应后端 @NotBlank）
 */
export interface INoteCreateDTO {
  title: string
  content: string
  tags?: string
}

/**
 * 修改笔记入参（全字段可选）
 */
export interface INoteUpdateDTO {
  title?: string
  content?: string
  tags?: string
}

/**
 * 笔记分页查询条件（tags 走后端 LIKE）
 */
export interface INoteQuery extends IPageQuery {
  tags?: string
}
```

- [ ] **Step 6: 创建 `src/types/task.ts`**

```typescript
import type { TaskStatus } from './enums'
import type { IPageQuery } from './api'

/**
 * 任务视图（completedAt 未完成时为 null）
 */
export interface ITaskVO {
  id: string
  userId: string
  title: string
  status: TaskStatus
  completedAt: string | null
  createdAt: string
}

/**
 * 新增任务入参（title 必填；status 可选，后端缺省 TODO）
 */
export interface ITaskCreateDTO {
  title: string
  status?: TaskStatus
}

/**
 * 修改任务入参（全字段可选）
 */
export interface ITaskUpdateDTO {
  title?: string
  status?: TaskStatus
}

/**
 * 任务分页查询条件（按状态过滤）
 */
export interface ITaskQuery extends IPageQuery {
  status?: TaskStatus
}
```

- [ ] **Step 7: 创建 `src/types/report.ts`**

```typescript
import type { ReportType, ReportStatus, AgentName } from './enums'
import type { IPageQuery } from './api'

/**
 * 报告视图（errorMsg 生成成功时为 null；tokenUsage 为普通整数，用 number）
 */
export interface IReportVO {
  id: string
  userId: string
  type: ReportType
  periodStart: string
  periodEnd: string
  title: string
  content: string
  status: ReportStatus
  errorMsg: string | null
  tokenUsage: number
  createdAt: string
}

/**
 * 触发生成入参（M4 运行时只用 type=DAILY；date 为 'YYYY-MM-DD'）
 */
export interface IReportGenerateDTO {
  type: ReportType
  date: string
}

/**
 * 报告分页查询条件（按类型过滤）
 */
export interface IReportQuery extends IPageQuery {
  type?: ReportType
}

/**
 * Agent 执行轨迹视图（tokens/latencyMs/step/retryCount 为普通整数）
 */
export interface IAgentTraceVO {
  id: string
  reportId: string
  agentName: AgentName
  step: number
  inputSummary: string
  outputSummary: string
  tokens: number
  latencyMs: number
  retryCount: number
  createdAt: string
}
```

- [ ] **Step 8: 创建 `src/utils/format.ts`（日期格式化，容错）**

```typescript
/**
 * 日期格式化工具
 * 后端 LocalDateTime 经 Jackson 序列化为 ISO 字符串（如 '2026-07-10T12:30:00'）；
 * Date 构造器可直接解析 ISO 字符串，解析失败则原样返回避免崩 UI。
 */

/** 格式化为 'YYYY-MM-DD HH:mm' */
export function formatDateTime(value: string | null | undefined): string {
  if (!value) return ''
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return value
  const pad = (n: number): string => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** 格式化为 'YYYY-MM-DD' */
export function formatDate(value: string | null | undefined): string {
  if (!value) return ''
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return value
  const pad = (n: number): string => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/** 返回今天的 'YYYY-MM-DD'，用于 date-picker 默认值 */
export function todayString(): string {
  return formatDate(new Date().toISOString())
}
```

- [ ] **Step 9: 写 `parseBigintJson` 失败测试（红）**

`dayflow-web/src/api/__tests__/index.test.ts`：

```typescript
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'

/**
 * ElMessage 在 jsdom 环境无 DOM 通知容器会报警告，统一 mock
 */
vi.mock('element-plus', () => ({
  ElMessage: {
    error: vi.fn(),
    warning: vi.fn(),
    success: vi.fn(),
    info: vi.fn(),
  },
}))

import { parseBigintJson } from '@/api/index'

describe('parseBigintJson', () => {
  it('雪花 ID（19 位）解析为 string，保精度', () => {
    const raw = '{"id":1901234567890123456,"name":"x"}'
    const parsed = parseBigintJson(raw) as { id: unknown; name: string }
    expect(parsed.id).toBe('1901234567890123456')
    expect(typeof parsed.id).toBe('string')
  })

  it('小整数仍为 number', () => {
    const parsed = parseBigintJson('{"age":30}') as { age: unknown }
    expect(parsed.age).toBe(30)
    expect(typeof parsed.age).toBe('number')
  })

  it('空数据原样返回', () => {
    expect(parseBigintJson('')).toBe('')
  })

  it('非法 JSON 抛错（兜底也用 JSON.parse）', () => {
    expect(() => parseBigintJson('{bad')).toThrow()
  })
})
```

- [ ] **Step 10: 运行测试确认失败**

Run: `cd dayflow-web && npm test`
Expected: FAIL —— `parseBigintJson is not a function`（`@/api/index` 尚未创建）。

- [ ] **Step 11: 创建 `src/api/index.ts`（axios 实例 + 拦截器 + 解析）**

```typescript
import axios from 'axios'
import JSONBig from 'json-bigint'
import { ElMessage } from 'element-plus'
import type { IResult } from '@/types/api'

/**
 * 雪花 ID 安全解析：超过 Number.MAX_SAFE_INTEGER 的整数解析为 string。
 * 作为 axios transformResponse，对 /api 响应体统一生效。
 *
 * @param data 原始响应字符串
 * @returns 解析后的对象（id 类字段为 string）
 */
export function parseBigintJson(data: string): unknown {
  if (!data) return data
  try {
    return JSONBig({ storeAsString: true }).parse(data)
  } catch {
    // 非标准 JSON 兜底用原生 parse（仍可能抛错，交由上层 catch）
    return JSON.parse(data)
  }
}

/** 配置好的 axios 实例（baseURL=/api，注入 JWT，按 Result.code 分流） */
export const http = axios.create({
  baseURL: '/api',
  timeout: 15000,
  transformResponse: [(data) => parseBigintJson(data)],
})

/**
 * 401 处理由外部注入，避免 api ↔ router/store 循环依赖。
 * main.ts 启动时调用 setUnauthorizedHandler 注册：清 authStore + 跳 /login。
 */
let unauthorizedHandler: (() => void) | null = null

export function setUnauthorizedHandler(fn: () => void): void {
  unauthorizedHandler = fn
}

/** 请求拦截器：从 localStorage 取 token 注入 Authorization 头 */
http.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

/**
 * 响应拦截器：按后端 Result.code 分流
 * - 200：解包返回 data
 * - 401：调用注入的 401 handler 并 reject
 * - 其他：ElMessage.error 提示并 reject
 * - 网络错误：ElMessage.error('网络异常...') 并 reject
 */
http.interceptors.response.use(
  (response) => {
    const result = response.data as IResult<unknown>
    if (result.code === 200) {
      return result.data
    }
    if (result.code === 401) {
      unauthorizedHandler?.()
      return Promise.reject(new Error(result.msg || '未认证'))
    }
    ElMessage.error(result.msg || '请求失败')
    return Promise.reject(new Error(result.msg || '请求失败'))
  },
  (error) => {
    ElMessage.error('网络异常，请稍后重试')
    return Promise.reject(error)
  },
)
```

- [ ] **Step 12: 运行测试确认 `parseBigintJson` 绿**

Run: `cd dayflow-web && npm test`
Expected: `parseBigintJson` 4 项全 PASS。

- [ ] **Step 13: 安装 axios 测试用 mock adapter**

Run: `cd dayflow-web && npm install -D axios-mock-adapter@^2.1.0`
Expected: 安装成功。

- [ ] **Step 14: 扩充拦截器测试（401/200/500/token 注入）**

在 `src/api/__tests__/index.test.ts` 末尾追加：

```typescript
import MockAdapter from 'axios-mock-adapter'
import { http, setUnauthorizedHandler } from '@/api/index'
import { ElMessage } from 'element-plus'

describe('http interceptors', () => {
  let mock: MockAdapter

  beforeEach(() => {
    mock = new MockAdapter(http)
    localStorage.clear()
  })

  afterEach(() => {
    mock.restore()
  })

  it('code=200 解包返回 data', async () => {
    mock.onGet('/x').reply(200, { code: 200, msg: 'ok', data: { id: '1' } })
    const res = (await http.get('/x')) as unknown as { id: string }
    expect(res).toEqual({ id: '1' })
  })

  it('code=401 调用 unauthorizedHandler 并 reject', async () => {
    const handler = vi.fn()
    setUnauthorizedHandler(handler)
    mock.onGet('/x').reply(200, { code: 401, msg: '未认证', data: null })
    await expect(http.get('/x')).rejects.toThrow('未认证')
    expect(handler).toHaveBeenCalledTimes(1)
  })

  it('code=500 走 ElMessage.error 并 reject', async () => {
    mock.onGet('/x').reply(200, { code: 500, msg: '系统异常', data: null })
    await expect(http.get('/x')).rejects.toThrow('系统异常')
    expect(ElMessage.error).toHaveBeenCalled()
  })

  it('请求拦截器从 localStorage 注入 Bearer token', async () => {
    localStorage.setItem('token', 'abc123')
    let captured: string | undefined
    mock.onGet('/x').reply((config) => {
      captured = config.headers?.Authorization as string | undefined
      return [200, { code: 200, msg: 'ok', data: null }]
    })
    await http.get('/x').catch(() => undefined)
    expect(captured).toBe('Bearer abc123')
  })
})
```

- [ ] **Step 15: 运行测试确认拦截器全绿**

Run: `cd dayflow-web && npm test`
Expected: `parseBigintJson` 4 + `http interceptors` 4，共 8 项 PASS。

- [ ] **Step 16: 创建 `src/api/auth.ts`**

```typescript
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
```

- [ ] **Step 17: 创建 `src/api/activity.ts`**

```typescript
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
```

- [ ] **Step 18: 创建 `src/api/note.ts`**

```typescript
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
```

- [ ] **Step 19: 创建 `src/api/task.ts`**

```typescript
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
```

- [ ] **Step 20: 创建 `src/api/report.ts`**

```typescript
import { http } from './index'
import type { IPage } from '@/types/api'
import type { IReportVO, IReportGenerateDTO, IReportQuery, IAgentTraceVO } from '@/types/report'

/**
 * 触发报告生成（POST /api/reports/generate）
 * 异步：立即返回 reportId，前端轮询状态与轨迹
 */
export function generateReport(dto: IReportGenerateDTO): Promise<string> {
  return http.post('/reports/generate', dto) as unknown as Promise<string>
}

/** 查询单个报告 */
export function getReport(id: string): Promise<IReportVO> {
  return http.get(`/reports/${id}`) as unknown as Promise<IReportVO>
}

/** 删除报告 */
export function deleteReport(id: string): Promise<void> {
  return http.delete(`/reports/${id}`) as unknown as Promise<void>
}

/** 分页查询报告（GET /api/reports） */
export function pageReports(query: IReportQuery): Promise<IPage<IReportVO>> {
  return http.get('/reports', { params: query }) as unknown as Promise<IPage<IReportVO>>
}

/** 查询某报告的 Agent 执行轨迹（GET /api/reports/{id}/traces） */
export function listTraces(reportId: string): Promise<IAgentTraceVO[]> {
  return http.get(`/reports/${reportId}/traces`) as unknown as Promise<IAgentTraceVO[]>
}
```

- [ ] **Step 21: 全量测试 + 类型检查**

Run: `cd dayflow-web && npm test && npx vue-tsc --noEmit`
Expected: 全部测试 PASS；类型检查无错（`as unknown as Promise<T>` 断言绕过拦截器改写 AxiosResponse 类型，是预期设计）。

- [ ] **Step 22: 提交**

```bash
git branch --show-current   # 必须是 feature/m4-frontend
git add dayflow-web/src dayflow-web/package.json dayflow-web/package-lock.json
git commit -m "feat(m4): types + axios 实例(拦截器/json-bigint) + API 层"
```

---

## Task 3: 后端注册端点 TDD（RegisterDTO + Service + Controller + 放行）

**Files:**
- Create: `dayflow-server/src/main/java/com/dayflow/pojo/dto/RegisterDTO.java`
- Modify: `dayflow-server/src/main/java/com/dayflow/service/UserAuthService.java`
- Modify: `dayflow-server/src/main/java/com/dayflow/service/impl/UserAuthServiceImpl.java`
- Modify: `dayflow-server/src/main/java/com/dayflow/controller/AuthController.java`
- Modify: `dayflow-server/src/main/java/com/dayflow/config/WebConfig.java`
- Test: `dayflow-server/src/test/java/com/dayflow/service/impl/UserAuthServiceImplRegisterTest.java`（新增，纯 Mockito）
- Test: `dayflow-server/src/test/java/com/dayflow/controller/AuthControllerTest.java`（新增，@WebMvcTest 切片）
- Test: `dayflow-server/src/test/java/com/dayflow/service/impl/UserAuthServiceImplTest.java`（扩展，@SpringBootTest 端到端）

**Interfaces:**
- Consumes: `UserMapper` / `JwtUtil.generate(Long, String)` / `BCryptPasswordEncoder`（实例字段，`final` 已初始化不进构造，Mockito `@InjectMocks` 时自动 new 真实实例）/ `BusinessException(ResultCode, String)` / `ResultCode.BUSINESS_ERROR(409)` / `LoginVO.builder()`
- Produces:
  - `RegisterDTO`：`{ username: String(@NotBlank), password: String(@NotBlank) }`
  - `UserAuthService.register(RegisterDTO): LoginVO` —— 查重（重名抛 `BusinessException(BUSINESS_ERROR, "用户名已存在")`→409）→ BCrypt 加密 → `userMapper.insert` → `jwtUtil.generate` → 返回 `LoginVO`（注册即登录，nickname=null）
  - `AuthController.register(@Valid @RequestBody RegisterDTO): Result<LoginVO>` —— `POST /api/auth/register`
  - `WebConfig` 放行 `/api/auth/register`（与 `/api/auth/login` 同列）

> 现状（已核实）：`UserAuthServiceImpl.passwordEncoder` 是 `private final BCryptPasswordEncoder = new BCryptPasswordEncoder()`（实例字段，非局部 new），`@RequiredArgsConstructor` 不会把它纳入构造参数，故 Mockito 单测里它保持真实 BCrypt 实例 —— 可直接验证加密。login 用 `userMapper.selectOne(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUsername, ...))` 查询，register 查重沿用此范式。

- [ ] **Step 1: 创建 `RegisterDTO`**

`dayflow-server/src/main/java/com/dayflow/pojo/dto/RegisterDTO.java`：

```java
package com.dayflow.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 注册入参
 *
 * @author jiaxianming
 */
@Data
public class RegisterDTO {

    /**
     * 用户名
     */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /**
     * 密码
     */
    @NotBlank(message = "密码不能为空")
    private String password;
}
```

- [ ] **Step 2: 接口加 `register` 方法签名**

在 `UserAuthService.java` 增加 import 与方法（保留原 login 不动）：

```java
package com.dayflow.service;

import com.dayflow.pojo.dto.LoginDTO;
import com.dayflow.pojo.dto.RegisterDTO;
import com.dayflow.pojo.vo.LoginVO;

/**
 * 用户鉴权服务
 *
 * @author jiaxianming
 */
public interface UserAuthService {

    /**
     * 登录：校验用户名密码，签发 JWT
     *
     * @param dto 登录入参
     * @return 登录出参（含 token 与用户基本信息）
     */
    LoginVO login(LoginDTO dto);

    /**
     * 注册：查重 + BCrypt 加密 + 落库 + 签发 JWT（注册即登录）
     *
     * @param dto 注册入参
     * @return 登录出参（含 token，注册即登录，无需二次登录）
     */
    LoginVO register(RegisterDTO dto);
}
```

- [ ] **Step 3: 写 register 纯 Mockito 单测（红）**

`dayflow-server/src/test/java/com/dayflow/service/impl/UserAuthServiceImplRegisterTest.java`：

```java
package com.dayflow.service.impl;

import com.dayflow.common.BusinessException;
import com.dayflow.common.JwtUtil;
import com.dayflow.mapper.UserMapper;
import com.dayflow.pojo.dto.RegisterDTO;
import com.dayflow.pojo.entity.UserEntity;
import com.dayflow.pojo.vo.LoginVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserAuthService.register 纯 Mockito 单测
 * <p>验证查重/加密/签 JWT 逻辑；passwordEncoder 为实例字段（final 已初始化），
 * @InjectMocks 时自动 new 真实 BCrypt，故可用真实 BCryptPasswordEncoder 验证加密。</p>
 *
 * @author jiaxianming
 */
@ExtendWith(MockitoExtension.class)
class UserAuthServiceImplRegisterTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private UserAuthServiceImpl userAuthService;

    /**
     * 正常注册：不重名 → insert 落库（密码已 BCrypt 加密）→ 签 JWT → 返回 LoginVO
     */
    @Test
    void registerSucceeds() {
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername("alice");
        dto.setPassword("pass123");

        when(userMapper.selectOne(any())).thenReturn(null);
        when(jwtUtil.generate(any(), eq("alice"))).thenReturn("token-alice");

        LoginVO vo = userAuthService.register(dto);

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userMapper).insert(captor.capture());
        UserEntity saved = captor.getValue();

        assertEquals("alice", saved.getUsername());
        // 密码已加密：不是明文，且可被 BCrypt 验证匹配
        assertTrue(!"pass123".equals(saved.getPasswordHash()), "落库的不能是明文密码");
        assertTrue(new BCryptPasswordEncoder().matches("pass123", saved.getPasswordHash()),
                "落库 hash 必须能被 BCrypt 验证匹配");
        // 返回的 LoginVO
        assertEquals("token-alice", vo.getToken());
        assertEquals("alice", vo.getUsername());
    }

    /**
     * 用户名已存在：抛 BusinessException(409)，且不执行 insert
     */
    @Test
    void registerThrowsWhenUsernameExists() {
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername("alice");
        dto.setPassword("pass123");

        UserEntity existing = new UserEntity();
        existing.setUsername("alice");
        when(userMapper.selectOne(any())).thenReturn(existing);

        BusinessException ex = assertThrows(BusinessException.class, () -> userAuthService.register(dto));
        assertEquals(409, ex.getCode());
        assertNotNull(ex.getMessage());
        verify(userMapper, never()).insert(any());
    }
}
```

- [ ] **Step 4: 运行确认失败**

Run: `mvn -f dayflow-server/pom.xml test -Dtest=UserAuthServiceImplRegisterTest`
Expected: 编译失败 —— `UserAuthServiceImpl` 未实现 `register`（接口新增了抽象方法，impl 未 override）。

- [ ] **Step 5: 实现 `register`**

在 `UserAuthServiceImpl.java` 增加 `@Override register` 方法（login 不动）：

```java
    /**
     * 注册：查重 → BCrypt 加密 → 落库 → 签发 JWT（注册即登录）
     *
     * @param dto 注册入参
     * @return 登录出参（含 token）
     */
    @Override
    public LoginVO register(RegisterDTO dto) {
        UserEntity existing = userMapper.selectOne(
                new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUsername, dto.getUsername()));
        if (existing != null) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "用户名已存在");
        }
        UserEntity user = new UserEntity();
        user.setUsername(dto.getUsername());
        user.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        userMapper.insert(user);
        String token = jwtUtil.generate(user.getId(), user.getUsername());
        return LoginVO.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .build();
    }
```

> 顶部 import 需补 `import com.dayflow.pojo.dto.RegisterDTO;`（`LambdaQueryWrapper` / `UserEntity` / `BusinessException` / `ResultCode` 已在 login 用到，已 import）。

- [ ] **Step 6: 运行确认绿**

Run: `mvn -f dayflow-server/pom.xml test -Dtest=UserAuthServiceImplRegisterTest`
Expected: 2 项 PASS。

- [ ] **Step 7: 写 AuthController 切片测试（红）**

`dayflow-server/src/test/java/com/dayflow/controller/AuthControllerTest.java`：

```java
package com.dayflow.controller;

import com.dayflow.common.GlobalExceptionHandler;
import com.dayflow.common.JwtUtil;
import com.dayflow.pojo.vo.LoginVO;
import com.dayflow.service.UserAuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AuthController 测试（@WebMvcTest 切片，不连 DB）
 * <p>沿用 ReportControllerTest 范式：排除 WebConfig 避免 JwtInterceptor 注册到 /api/auth/** 拦截无 token 请求；
 * 用 @MockitoBean 提供 JwtUtil 满足被切片扫描到的 JwtInterceptor 构造依赖。</p>
 *
 * @author jiaxianming
 */
@WebMvcTest(controllers = AuthController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = com.dayflow.config.WebConfig.class))
@Import(GlobalExceptionHandler.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserAuthService userAuthService;

    /**
     * JwtInterceptor 被 @WebMvcTest 自动扫描，构造需要 JwtUtil；
     * 这里 mock 它只为满足上下文依赖，WebConfig 已排除故拦截器不会进入请求链。
     */
    @MockitoBean
    private JwtUtil jwtUtil;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void registerReturns200() throws Exception {
        LoginVO vo = LoginVO.builder().token("tok").userId(1L).username("alice").build();
        when(userAuthService.register(any())).thenReturn(vo);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"pw\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").value("tok"));
        verify(userAuthService).register(any());
    }

    @Test
    void registerWithInvalidBodyReturns400() throws Exception {
        // 缺 username -> @Valid 失败 -> GlobalExceptionHandler 映射为 HTTP 200 + Result.code=400
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"pw\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void loginReturns200() throws Exception {
        LoginVO vo = LoginVO.builder().token("tok").userId(1L).username("alice").build();
        when(userAuthService.login(any())).thenReturn(vo);
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"pw\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").value("tok"));
    }
}
```

- [ ] **Step 8: 运行确认失败**

Run: `mvn -f dayflow-server/pom.xml test -Dtest=AuthControllerTest`
Expected: `registerReturns200` FAIL —— 404（`/api/auth/register` 端点不存在）。

- [ ] **Step 9: 加 Controller 端点**

在 `AuthController.java` 增加 register 方法（login 不动）：

```java
    /**
     * 注册：查重 + 落库 + 签发 JWT（注册即登录）
     *
     * @param dto 注册入参
     * @return 登录出参（含 token，注册即登录）
     */
    @PostMapping("/register")
    public Result<LoginVO> register(@Valid @RequestBody RegisterDTO dto) {
        return Result.success(userAuthService.register(dto));
    }
```

> 顶部 import 需补 `import com.dayflow.pojo.dto.RegisterDTO;`（`Result` / `LoginVO` / `@Valid` / `@RequestBody` / `@PostMapping` 已在 login 用到，已 import）。

- [ ] **Step 10: WebConfig 放行 `/api/auth/register`**

在 `WebConfig.java` 的拦截器注册处，`excludePathPatterns` 追加 `"/api/auth/register"`：

修改前：
```java
            registry.addInterceptor(jwtInterceptor)
                    .addPathPatterns("/api/**")
                    .excludePathPatterns("/api/auth/login", "/api/health/**");
```

修改后：
```java
            registry.addInterceptor(jwtInterceptor)
                    .addPathPatterns("/api/**")
                    .excludePathPatterns("/api/auth/login", "/api/auth/register", "/api/health/**");
```

- [ ] **Step 11: 运行 Controller 测试确认绿**

Run: `mvn -f dayflow-server/pom.xml test -Dtest=AuthControllerTest`
Expected: 3 项 PASS（register 200 / register 400 / login 200）。

- [ ] **Step 12: 扩展集成测试（register→login 端到端 + 重复 409）**

在 `UserAuthServiceImplTest.java` 增加 import `RegisterDTO` 与 `@Transactional`，并追加两个测试方法：

顶部 import 补：
```java
import com.dayflow.pojo.dto.RegisterDTO;
import org.springframework.transaction.annotation.Transactional;
```

在类内追加（原有 login 测试保留不动）：

```java
    /**
     * 端到端：注册新用户 → 返回非空 token；同密码可登录（验证 BCrypt 加密落库可登录）
     * 用 nanoTime 保证用户名唯一，避免与预置 admin 或多次运行冲突
     */
    @Test
    @Transactional
    void registerSucceedsThenCanLogin() {
        String username = "newuser_" + System.nanoTime();
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername(username);
        dto.setPassword("secret123");

        LoginVO vo = userAuthService.register(dto);
        assertNotNull(vo);
        assertTrue(vo.getToken() != null && !vo.getToken().isBlank(), "注册后 token 不应为空");
        assertEquals(username, vo.getUsername());

        // 注册即登录：同账号密码可登录
        LoginDTO loginDto = new LoginDTO();
        loginDto.setUsername(username);
        loginDto.setPassword("secret123");
        LoginVO loginVo = userAuthService.login(loginDto);
        assertNotNull(loginVo);
        assertTrue(loginVo.getToken() != null && !loginVo.getToken().isBlank(), "登录 token 不应为空");
    }

    /**
     * 用户名重复：抛 BusinessException(409)
     */
    @Test
    @Transactional
    void registerDuplicateThrows409() {
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername("admin");  // 预置用户已存在
        dto.setPassword("whatever");
        BusinessException ex = assertThrows(BusinessException.class, () -> userAuthService.register(dto));
        assertEquals(409, ex.getCode());
    }
```

- [ ] **Step 13: 运行 register 相关全部后端测试**

Run: `mvn -f dayflow-server/pom.xml test -Dtest=UserAuthServiceImplRegisterTest,AuthControllerTest,UserAuthServiceImplTest`
Expected: 全部 PASS（含原有 login + 新增 register）。

- [ ] **Step 14: 全量后端测试回归**

Run: `mvn -f dayflow-server/pom.xml test`
Expected: 全绿（确保 register 改动未破坏 M1-M3 测试）。

- [ ] **Step 15: 提交**

```bash
git branch --show-current   # 必须是 feature/m4-frontend
git add dayflow-server/src
git commit -m "feat(m4): 后端注册端点 TDD（RegisterDTO + Service + Controller + WebConfig 放行）"
```

---

## Task 4: 前端鉴权全链路（authStore + 路由守卫 + 登录/注册页 + JWT 拦截接线）

**Files:**
- Create: `dayflow-web/src/stores/auth.ts`
- Create: `dayflow-web/src/router/index.ts`
- Create: `dayflow-web/src/views/auth/LoginView.vue`
- Create: `dayflow-web/src/views/auth/RegisterView.vue`
- Create: `dayflow-web/src/views/NotFoundView.vue`
- Create: `dayflow-web/src/views/input/InputView.vue`（占位，Task 6 覆盖为真实实现）
- Modify: `dayflow-web/src/main.ts`
- Test: `dayflow-web/src/stores/__tests__/auth.test.ts`
- Test: `dayflow-web/src/router/__tests__/guard.test.ts`

**Interfaces:**
- Consumes: Task 2 的 `api/auth`（`login`/`register`）、`setUnauthorizedHandler`、`http`；localStorage key `'token'`（与 `api/index.ts` 请求拦截器约定一致）
- Produces:
  - `useAuthStore`（`@/stores/auth`）：`token` / `userId` / `username` / `nickname`（均从 localStorage 初始化）+ `isAuthed`（computed）+ `login(dto)` / `register(dto)`（成功后持久化 token+userInfo）/ `logout()`（清空）
  - `resolveRoute(to, isAuthed): string | boolean`（`@/router`）：守卫判定纯函数 —— 未登录访问受保护页返回 `/login?redirect=...`、已登录访问 `/login` `/register` 返回 `/input`、其余放行
  - `router`（`@/router`）：vue-router 实例，`beforeEach` 调用 `resolveRoute`
  - `main.ts`：装 Pinia + Router + ElementPlus，并注册 401 handler（`authStore.logout()` + `router.push('/login')`）

> 测试策略：核心逻辑（authStore 状态变更 / 守卫判定）严格 TDD 红绿纯单测；LoginView/RegisterView/NotFoundView 给完整代码 + dev 联调验证（EP 表单 + vue-router 在 jsdom 下组件测试脆弱，投入产出低；spec §11 亦未要求其组件测试）。

- [ ] **Step 1: 写 authStore 失败测试（红）**

`dayflow-web/src/stores/__tests__/auth.test.ts`：

```typescript
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import * as authApi from '@/api/auth'
import { useAuthStore } from '../auth'

describe('useAuthStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  it('login 成功写入 token/userInfo 并持久化 localStorage', async () => {
    vi.spyOn(authApi, 'login').mockResolvedValue({
      token: 'tok', userId: '1', username: 'alice', nickname: '小A',
    })
    const store = useAuthStore()
    await store.login({ username: 'alice', password: 'pw' })
    expect(store.token).toBe('tok')
    expect(store.userId).toBe('1')
    expect(store.username).toBe('alice')
    expect(store.nickname).toBe('小A')
    expect(store.isAuthed).toBe(true)
    expect(localStorage.getItem('token')).toBe('tok')
  })

  it('register 成功即登录（nickname 为 null 时存空串）', async () => {
    vi.spyOn(authApi, 'register').mockResolvedValue({
      token: 't2', userId: '2', username: 'bob', nickname: null,
    })
    const store = useAuthStore()
    await store.register({ username: 'bob', password: 'pw' })
    expect(store.isAuthed).toBe(true)
    expect(store.username).toBe('bob')
    expect(store.nickname).toBe('')
  })

  it('logout 清空状态与 localStorage', () => {
    const store = useAuthStore()
    store.token = 'x'
    localStorage.setItem('token', 'x')
    store.logout()
    expect(store.token).toBe('')
    expect(store.isAuthed).toBe(false)
    expect(localStorage.getItem('token')).toBeNull()
  })

  it('初始 token 从 localStorage 读取（刷新保登录态）', () => {
    localStorage.setItem('token', 'restored')
    const store = useAuthStore()
    expect(store.token).toBe('restored')
    expect(store.isAuthed).toBe(true)
  })
})
```

- [ ] **Step 2: 运行确认失败**

Run: `cd dayflow-web && npm test`
Expected: FAIL —— `Cannot find module '../auth'`（`stores/auth.ts` 未创建）。

- [ ] **Step 3: 实现 `stores/auth.ts`（绿）**

```typescript
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as authApi from '@/api/auth'
import type { ILoginDTO, IRegisterDTO } from '@/types/auth'

/**
 * 鉴权 Store
 * token + userInfo 持久化到 localStorage；
 * token key 为 'token'，与 api/index.ts 请求拦截器约定一致。
 */
export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(localStorage.getItem('token') || '')
  const userId = ref<string>(localStorage.getItem('userId') || '')
  const username = ref<string>(localStorage.getItem('username') || '')
  const nickname = ref<string>(localStorage.getItem('nickname') || '')

  /** 是否已登录（仅依赖 token） */
  const isAuthed = computed(() => !!token.value)

  /** 写入登录态并持久化 */
  function applyLogin(t: string, id: string, name: string, nick: string | null): void {
    token.value = t
    userId.value = id
    username.value = name
    nickname.value = nick || ''
    localStorage.setItem('token', t)
    localStorage.setItem('userId', id)
    localStorage.setItem('username', name)
    localStorage.setItem('nickname', nick || '')
  }

  /** 清空登录态 */
  function clear(): void {
    token.value = ''
    userId.value = ''
    username.value = ''
    nickname.value = ''
    localStorage.removeItem('token')
    localStorage.removeItem('userId')
    localStorage.removeItem('username')
    localStorage.removeItem('nickname')
  }

  /** 登录：调 api → 持久化 */
  async function login(dto: ILoginDTO): Promise<void> {
    const vo = await authApi.login(dto)
    applyLogin(vo.token, vo.userId, vo.username, vo.nickname)
  }

  /** 注册：调 api（注册即登录返回 LoginVO）→ 持久化 */
  async function register(dto: IRegisterDTO): Promise<void> {
    const vo = await authApi.register(dto)
    applyLogin(vo.token, vo.userId, vo.username, vo.nickname)
  }

  /** 登出：清登录态（跳转由调用方决定） */
  function logout(): void {
    clear()
  }

  return { token, userId, username, nickname, isAuthed, login, register, logout }
})
```

- [ ] **Step 4: 运行确认绿**

Run: `cd dayflow-web && npm test`
Expected: `useAuthStore` 4 项 PASS。

- [ ] **Step 5: 写守卫判定失败测试（红）**

`dayflow-web/src/router/__tests__/guard.test.ts`：

```typescript
import { describe, expect, it } from 'vitest'
import { resolveRoute } from '../index'

describe('resolveRoute', () => {
  it('未登录访问受保护页 → 跳 /login 带 redirect', () => {
    expect(resolveRoute({ path: '/input' }, false)).toBe(
      '/login?redirect=' + encodeURIComponent('/input'),
    )
  })

  it('未登录访问公开页 → 放行', () => {
    expect(resolveRoute({ path: '/login', meta: { public: true } }, false)).toBe(true)
  })

  it('已登录访问 /login → 跳 /input', () => {
    expect(resolveRoute({ path: '/login', meta: { public: true } }, true)).toBe('/input')
  })

  it('已登录访问 /register → 跳 /input', () => {
    expect(resolveRoute({ path: '/register', meta: { public: true } }, true)).toBe('/input')
  })

  it('已登录访问受保护页 → 放行', () => {
    expect(resolveRoute({ path: '/input' }, true)).toBe(true)
  })
})
```

- [ ] **Step 6: 运行确认失败**

Run: `cd dayflow-web && npm test`
Expected: FAIL —— `Cannot find module '../index'`。

- [ ] **Step 7: 实现 `router/index.ts`（绿）**

```typescript
import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

/**
 * 守卫判定纯函数（独立于 vue-router，可单测）
 * - 未登录访问受保护页 → '/login?redirect=<原路径>'
 * - 已登录访问 /login 或 /register → '/input'
 * - 其余放行（true）
 *
 * @param to 目标路由（path + meta.public）
 * @param isAuthed 当前是否已登录
 * @returns 跳转路径字符串或 true（放行）
 */
export function resolveRoute(
  to: { path: string; meta?: { public?: boolean } },
  isAuthed: boolean,
): string | boolean {
  const isPublic = to.meta?.public === true
  if (!isPublic && !isAuthed) {
    return `/login?redirect=${encodeURIComponent(to.path)}`
  }
  if (isAuthed && (to.path === '/login' || to.path === '/register')) {
    return '/input'
  }
  return true
}

/**
 * 路由表
 * Task 4 仅注册 login/register/input(占位)/404；
 * Task 5 起将受保护路由重构进 AppLayout children，并逐步替换占位为真实组件。
 */
const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/auth/LoginView.vue'),
    meta: { public: true },
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/auth/RegisterView.vue'),
    meta: { public: true },
  },
  { path: '/', redirect: '/input' },
  {
    path: '/input',
    name: 'input',
    component: () => import('@/views/input/InputView.vue'),
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/NotFoundView.vue'),
    meta: { public: true },
  },
]

export const router = createRouter({
  history: createWebHistory(),
  routes,
})

/** 全局前置守卫：基于 resolveRoute 判定 */
router.beforeEach((to) => resolveRoute(to, useAuthStore().isAuthed))
```

- [ ] **Step 8: 运行确认绿**

Run: `cd dayflow-web && npm test`
Expected: `resolveRoute` 5 项 PASS（注：`router/index.ts` import 的 view 文件尚未创建，vue-tsc 会报错，但 Vitest 只跑测试文件不 import router 整体，测试可通过；view 文件在后续 Step 创建）。

> 若 Vitest 因 `router/index.ts` 顶部 import 链解析 `@/views/...` 失败，先创建 Step 9-12 的占位文件再跑测试。

- [ ] **Step 9: 创建 `views/auth/LoginView.vue`**

```vue
<script setup lang="ts">
/**
 * 登录页：用户名 + 密码 → authStore.login → 成功跳 redirect 或 /input
 */
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const formRef = ref<FormInstance>()
const form = reactive({ username: '', password: '' })
const loading = ref(false)

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function onSubmit(): Promise<void> {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      await authStore.login({ username: form.username, password: form.password })
      const redirect = (route.query.redirect as string) || '/input'
      router.push(redirect)
    } catch {
      // 响应拦截器已 ElMessage 提示错误
    } finally {
      loading.value = false
    }
  })
}
</script>

<template>
  <div class="auth-view">
    <el-card class="auth-card">
      <template #header>
        <h2 class="auth-title">登录 DayFlow</h2>
      </template>
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="0"
        @keyup.enter="onSubmit"
      >
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" class="auth-submit" @click="onSubmit">
            登录
          </el-button>
        </el-form-item>
      </el-form>
      <div class="auth-link">
        还没账号？<router-link to="/register">去注册</router-link>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.auth-view {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: var(--dayflow-bg, #f5f7fa);
}
.auth-card {
  width: 360px;
}
.auth-title {
  margin: 0;
  font-size: 18px;
  text-align: center;
}
.auth-submit {
  width: 100%;
}
.auth-link {
  text-align: center;
  margin-top: 8px;
  font-size: 14px;
}
</style>
```

- [ ] **Step 10: 创建 `views/auth/RegisterView.vue`**

```vue
<script setup lang="ts">
/**
 * 注册页：用户名 + 密码 + 确认密码 → authStore.register → 成功即登录跳 /input
 */
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { type FormInstance, type FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const formRef = ref<FormInstance>()
const form = reactive({ username: '', password: '', confirm: '' })
const loading = ref(false)

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' },
  ],
  confirm: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback: (err?: Error) => void) => {
        if (value !== form.password) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}

async function onSubmit(): Promise<void> {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      await authStore.register({ username: form.username, password: form.password })
      router.push('/input')
    } catch {
      // 响应拦截器已 ElMessage 提示错误
    } finally {
      loading.value = false
    }
  })
}
</script>

<template>
  <div class="auth-view">
    <el-card class="auth-card">
      <template #header>
        <h2 class="auth-title">注册 DayFlow 账号</h2>
      </template>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="0">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码（至少 6 位）" show-password />
        </el-form-item>
        <el-form-item prop="confirm">
          <el-input v-model="form.confirm" type="password" placeholder="确认密码" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" class="auth-submit" @click="onSubmit">
            注册
          </el-button>
        </el-form-item>
      </el-form>
      <div class="auth-link">
        已有账号？<router-link to="/login">去登录</router-link>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.auth-view {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: var(--dayflow-bg, #f5f7fa);
}
.auth-card {
  width: 360px;
}
.auth-title {
  margin: 0;
  font-size: 18px;
  text-align: center;
}
.auth-submit {
  width: 100%;
}
.auth-link {
  text-align: center;
  margin-top: 8px;
  font-size: 14px;
}
</style>
```

- [ ] **Step 11: 创建 `views/NotFoundView.vue`**

```vue
<script setup lang="ts">
/**
 * 404 页
 */
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <el-result icon="warning" title="404" sub-title="您访问的页面不存在">
    <template #extra>
      <el-button type="primary" @click="router.push('/input')">返回首页</el-button>
    </template>
  </el-result>
</template>
```

- [ ] **Step 12: 创建占位 `views/input/InputView.vue`（Task 6 覆盖）**

```vue
<script setup lang="ts">
/**
 * 数据录入页（占位，Task 6 实现三 tab CRUD）
 */
</script>

<template>
  <div style="padding: 24px">数据录入页（Task 6 实现）</div>
</template>
```

- [ ] **Step 13: 修改 `main.ts` 接 Pinia + Router + 401 handler**

替换 Task 1 的简化版 `main.ts` 为：

```typescript
/**
 * 应用入口
 * 装 Pinia + Router + ElementPlus；注册 401 handler（清 authStore + 跳 /login）
 */
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import { router } from './router'
import { setUnauthorizedHandler } from './api/index'
import { useAuthStore } from './stores/auth'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)
app.use(ElementPlus)

/**
 * 401 处理：清登录态 + 跳 /login
 * 运行时（HTTP 401）触发，此时 pinia 已 active
 */
setUnauthorizedHandler(() => {
  useAuthStore().logout()
  router.push('/login')
})

app.mount('#app')
```

- [ ] **Step 14: 全量测试 + 类型检查**

Run: `cd dayflow-web && npm test && npx vue-tsc --noEmit`
Expected: 全部测试 PASS（含 Task 2 的 api 测试 + Task 4 的 authStore/guard 测试）；类型检查无错。

- [ ] **Step 15: dev 冒烟（手动，需后端运行）**

前置：后端 `mvn -f dayflow-server/pom.xml spring-boot:run`（或 IDE 启动）。

Run: `cd dayflow-web && npm run dev`
验证：
1. 访问 `http://localhost:5173/input` → 未登录被守卫重定向到 `/login?redirect=%2Finput`
2. 注册新用户（如 testuser/test123）→ 成功即登录，跳 `/input`（占位）
3. 登出后用同账号登录 → 跳 `/input`
4. 重复注册同名用户 → 后端返回 409，前端 `ElMessage.error('用户名已存在')`
5. 伪造 token（localStorage 改 `token` 为 'bad'）后访问受保护接口 → 后端 401 → 拦截器清 token + 跳 `/login`

- [ ] **Step 16: 提交**

```bash
git branch --show-current   # 必须是 feature/m4-frontend
git add dayflow-web/src
git commit -m "feat(m4): 前端鉴权全链路（authStore + 路由守卫 + 登录注册页 + 401 接线）"
```

---

## Task 5: AppLayout 侧边栏导航 + 路由骨架重构

**Files:**
- Create: `dayflow-web/src/layouts/AppLayout.vue`
- Create: `dayflow-web/src/views/history/HistoryView.vue`（占位，Task 9 覆盖）
- Create: `dayflow-web/src/views/report/ReportView.vue`（占位，Task 7 覆盖）
- Modify: `dayflow-web/src/router/index.ts`（受保护路由重构进 AppLayout children）
- Test: `dayflow-web/src/layouts/__tests__/AppLayout.test.ts`

**Interfaces:**
- Consumes: `useAuthStore`（昵称/用户名 + `logout`）、vue-router
- Produces:
  - `AppLayout`（`@/layouts/AppLayout`）：`el-container` 布局 —— 左 `el-aside`（logo + `el-menu` 导航，router 模式，`default-active` 跟随当前路径）+ 右 `el-header`（用户名 + 登出按钮）+ `el-main`（`<router-view/>`）
  - 路由重构：`/login` `/register`（公开，顶层）+ `/`（AppLayout，children：`''`→redirect `/input`、`input`、`reports`、`reports/:id`）+ `/:pathMatch(.*)*`（404，公开）。守卫 `resolveRoute` 与 `beforeEach` 不变。

> 设计说明：spec §6.1 提"录入/报告/历史三项导航"，但报告详情 `/reports/:id` 需 id 无法作为固定导航项，故将"报告中心"(`/reports`，HistoryView，Task 9 顶部含"生成新日报"按钮) 作为统一入口 —— 偏离 spec 措辞但更合理（self-review 记录）。

- [ ] **Step 1: 创建 `layouts/AppLayout.vue`**

```vue
<script setup lang="ts">
/**
 * 应用主布局：左侧边栏导航 + 顶部用户信息/登出 + 主区路由视图
 */
import { useRoute, useRouter } from 'vue-router'
import { Edit, Document } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

/** 登出：清登录态并跳登录页 */
function onLogout(): void {
  authStore.logout()
  router.push('/login')
}
</script>

<template>
  <el-container class="app-layout">
    <el-aside width="200px" class="app-aside">
      <div class="app-logo">DayFlow</div>
      <el-menu :default-active="route.path" router>
        <el-menu-item index="/input">
          <el-icon><Edit /></el-icon>
          <span>数据录入</span>
        </el-menu-item>
        <el-menu-item index="/reports">
          <el-icon><Document /></el-icon>
          <span>报告中心</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="app-header">
        <span class="app-user">{{ authStore.nickname || authStore.username || '用户' }}</span>
        <el-button data-test="logout" link type="primary" @click="onLogout">登出</el-button>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.app-layout {
  height: 100vh;
}
.app-aside {
  background: #fff;
  border-right: 1px solid #e6e8eb;
}
.app-logo {
  height: 56px;
  line-height: 56px;
  text-align: center;
  font-size: 18px;
  font-weight: 600;
  color: var(--dayflow-primary, #409eff);
}
.app-header {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 12px;
  background: #fff;
  border-bottom: 1px solid #e6e8eb;
}
.app-user {
  font-size: 14px;
  color: #606266;
}
</style>
```

- [ ] **Step 2: 创建占位 `views/history/HistoryView.vue`**

```vue
<script setup lang="ts">
/**
 * 历史报告列表页（占位，Task 9 实现）
 */
</script>

<template>
  <div style="padding: 24px">历史报告页（Task 9 实现）</div>
</template>
```

- [ ] **Step 3: 创建占位 `views/report/ReportView.vue`**

```vue
<script setup lang="ts">
/**
 * 报告查看页（占位，Task 7 实现）
 */
</script>

<template>
  <div style="padding: 24px">报告查看页（Task 7 实现）</div>
</template>
```

- [ ] **Step 4: 重构 `router/index.ts`（受保护路由进 AppLayout children）**

替换 Task 4 版 `router/index.ts` 为（`resolveRoute` 与 `beforeEach` 不变，仅 `routes` 重构）：

```typescript
import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

/**
 * 守卫判定纯函数（独立于 vue-router，可单测）
 * - 未登录访问受保护页 → '/login?redirect=<原路径>'
 * - 已登录访问 /login 或 /register → '/input'
 * - 其余放行（true）
 *
 * @param to 目标路由（path + meta.public）
 * @param isAuthed 当前是否已登录
 * @returns 跳转路径字符串或 true（放行）
 */
export function resolveRoute(
  to: { path: string; meta?: { public?: boolean } },
  isAuthed: boolean,
): string | boolean {
  const isPublic = to.meta?.public === true
  if (!isPublic && !isAuthed) {
    return `/login?redirect=${encodeURIComponent(to.path)}`
  }
  if (isAuthed && (to.path === '/login' || to.path === '/register')) {
    return '/input'
  }
  return true
}

/**
 * 路由表
 * - /login /register：公开页（顶层）
 * - /：AppLayout 布局，children 为受保护业务页
 * - /:pathMatch(.*)*：404（公开）
 */
const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/auth/LoginView.vue'),
    meta: { public: true },
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/auth/RegisterView.vue'),
    meta: { public: true },
  },
  {
    path: '/',
    component: () => import('@/layouts/AppLayout.vue'),
    children: [
      { path: '', redirect: '/input' },
      { path: 'input', name: 'input', component: () => import('@/views/input/InputView.vue') },
      { path: 'reports', name: 'history', component: () => import('@/views/history/HistoryView.vue') },
      { path: 'reports/:id', name: 'report', component: () => import('@/views/report/ReportView.vue') },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/NotFoundView.vue'),
    meta: { public: true },
  },
]

export const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => resolveRoute(to, useAuthStore().isAuthed))
```

- [ ] **Step 5: 写 AppLayout 组件测试**

`dayflow-web/src/layouts/__tests__/AppLayout.test.ts`：

```typescript
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory, type RouteRecordRaw } from 'vue-router'
import ElementPlus from 'element-plus'
import { beforeEach, describe, expect, it } from 'vitest'
import AppLayout from '../AppLayout.vue'
import { useAuthStore } from '@/stores/auth'

describe('AppLayout', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('显示当前用户昵称 + 导航项，登出清登录态', async () => {
    const store = useAuthStore()
    store.nickname = '小A'
    store.token = 'x'

    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/input', component: { template: '<div/>' } }] as RouteRecordRaw[],
    })
    await router.push('/input')
    await router.isReady()

    const wrapper = mount(AppLayout, {
      global: {
        plugins: [router, ElementPlus],
        stubs: ['router-view', 'Edit', 'Document'],
      },
    })

    expect(wrapper.text()).toContain('小A')
    expect(wrapper.text()).toContain('数据录入')
    expect(wrapper.text()).toContain('报告中心')

    await wrapper.find('[data-test="logout"]').trigger('click')
    expect(store.token).toBe('')
    expect(store.isAuthed).toBe(false)
  })
})
```

- [ ] **Step 6: 全量测试 + 类型检查**

Run: `cd dayflow-web && npm test && npx vue-tsc --noEmit`
Expected: 全部测试 PASS（含 Task 4 的 authStore/guard 测试 + 本任务 AppLayout 测试）；类型检查无错。

- [ ] **Step 7: dev 冒烟（手动，需后端运行）**

Run: `cd dayflow-web && npm run dev`（后端已起）
验证：
1. 登录后进入 `/input` → 看到左侧边栏（数据录入 / 报告中心）+ 顶部用户昵称 + 登出
2. 点击"报告中心" → 跳 `/reports`（占位）
3. 浏览器地址栏手动改 `/reports/123` → 进 ReportView（占位）
4. 点"登出" → 清登录态 + 跳 `/login`

- [ ] **Step 8: 提交**

```bash
git branch --show-current   # 必须是 feature/m4-frontend
git add dayflow-web/src
git commit -m "feat(m4): AppLayout 侧边栏导航 + 路由骨架重构（受保护路由进 layout children）"
```

---

## Task 6: 数据录入页 InputView（Activity/Note/Task 三 tab CRUD + Task complete）

**Files:**
- Modify: `dayflow-web/src/views/input/InputView.vue`（覆盖 Task 4 占位）
- Create: `dayflow-web/src/views/input/panels/ActivityPanel.vue`
- Create: `dayflow-web/src/views/input/panels/NotePanel.vue`
- Create: `dayflow-web/src/views/input/panels/TaskPanel.vue`
- Test: `dayflow-web/src/views/input/__tests__/InputView.test.ts`

**Interfaces:**
- Consumes: Task 2 的 `api/activity`（`listActivities`/`createActivity`/`updateActivity`/`deleteActivity`）、`api/note`、`api/task`（含 `completeTask`）；`utils/format`；types
- Produces:
  - `InputView`：`el-tabs` 容器，三 pane（`activity`/`note`/`task`），默认 `activity`
  - `ActivityPanel`：活动 CRUD —— `el-table`（内容/类别/发生时间/创建时间/操作）+ 新增/编辑 `el-dialog`（content 必填、category 必填、occurredAt 可选 datetime）+ 删除二次确认 + 分页
  - `NotePanel`：笔记 CRUD —— title/content 必填、tags 可选；列：标题/标签/创建时间/操作
  - `TaskPanel`：任务 CRUD + 状态筛选（TODO/DOING/DONE）+ `complete`（`PATCH /api/tasks/{id}/complete`）；列：标题/状态/完成时间/创建时间/操作（编辑/删除/完成，已完成不显示完成按钮）

> 测试策略：`InputView` tab 切换组件测试（mount 三 panel，mock 各 list api 返回空页避免网络）；三 panel 的 CRUD 交互由 dev 联调验证（EP table/dialog/pagination 在 jsdom 下组件交互测试脆弱）。panel 内 `<script>` 块（模块级）定义选项常量与 helper，`<script setup>` 直接复用（同 SFC 模块作用域，无需 import）。

- [ ] **Step 1: 写 InputView tab 切换测试（红）**

`dayflow-web/src/views/input/__tests__/InputView.test.ts`：

```typescript
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import InputView from '../InputView.vue'
import * as activityApi from '@/api/activity'
import * as noteApi from '@/api/note'
import * as taskApi from '@/api/task'

const EMPTY_PAGE = { records: [], total: 0, size: 10, current: 1, pages: 0 }

describe('InputView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.spyOn(activityApi, 'listActivities').mockResolvedValue(EMPTY_PAGE)
    vi.spyOn(noteApi, 'listNotes').mockResolvedValue(EMPTY_PAGE)
    vi.spyOn(taskApi, 'listTasks').mockResolvedValue(EMPTY_PAGE)
  })

  it('渲染三个 tab（活动 / 笔记 / 任务）', () => {
    const wrapper = mount(InputView, { global: { plugins: [ElementPlus] } })
    const text = wrapper.text()
    expect(text).toContain('活动')
    expect(text).toContain('笔记')
    expect(text).toContain('任务')
  })
})
```

- [ ] **Step 2: 运行确认失败**

Run: `cd dayflow-web && npm test`
Expected: FAIL —— `InputView` 仍是占位（不含三 tab 文本 / 子 panel 未创建）。

- [ ] **Step 3: 实现 `panels/ActivityPanel.vue`**

```vue
<script setup lang="ts">
/**
 * 活动 CRUD 面板：列表 + 新增/编辑弹窗 + 删除
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  createActivity,
  deleteActivity,
  listActivities,
  updateActivity,
} from '@/api/activity'
import type {
  IActivityCreateDTO,
  IActivityQuery,
  IActivityUpdateDTO,
  IActivityVO,
} from '@/types/activity'
import type { ActivityCategory } from '@/types/enums'
import { formatDateTime } from '@/utils/format'

/** 活动类别选项（setup 顶层声明，template 可直接用） */
const CATEGORY_OPTIONS: { label: string; value: ActivityCategory }[] = [
  { label: '工作', value: 'WORK' },
  { label: '学习', value: 'STUDY' },
  { label: '会议', value: 'MEETING' },
  { label: '其他', value: 'OTHER' },
]

/** 类别值 → 中文标签 */
function categoryLabel(v: ActivityCategory): string {
  return CATEGORY_OPTIONS.find((o) => o.value === v)?.label || v
}

const list = ref<IActivityVO[]>([])
const total = ref(0)
const loading = ref(false)
const query = reactive<IActivityQuery>({ page: 1, size: 10 })

async function load(): Promise<void> {
  loading.value = true
  try {
    const page = await listActivities(query)
    list.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}

const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<{
  id?: string
  content: string
  category: ActivityCategory | ''
  occurredAt?: string
}>({ content: '', category: '' })

const rules: FormRules = {
  content: [{ required: true, message: '请输入活动内容', trigger: 'blur' }],
  category: [{ required: true, message: '请选择类别', trigger: 'change' }],
}

function openCreate(): void {
  isEdit.value = false
  form.id = undefined
  form.content = ''
  form.category = ''
  form.occurredAt = undefined
  dialogVisible.value = true
}

function openEdit(row: IActivityVO): void {
  isEdit.value = true
  form.id = row.id
  form.content = row.content
  form.category = row.category
  form.occurredAt = row.occurredAt
  dialogVisible.value = true
}

async function onSubmit(): Promise<void> {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    try {
      const category = form.category as ActivityCategory
      if (isEdit.value && form.id) {
        const dto: IActivityUpdateDTO = {
          content: form.content,
          category,
          ...(form.occurredAt ? { occurredAt: form.occurredAt } : {}),
        }
        await updateActivity(form.id, dto)
        ElMessage.success('已更新')
      } else {
        const dto: IActivityCreateDTO = {
          content: form.content,
          category,
          ...(form.occurredAt ? { occurredAt: form.occurredAt } : {}),
        }
        await createActivity(dto)
        ElMessage.success('已新增')
      }
      dialogVisible.value = false
      await load()
    } catch {
      // 拦截器已提示
    }
  })
}

async function onDelete(row: IActivityVO): Promise<void> {
  await ElMessageBox.confirm('确认删除该活动？', '提示', { type: 'warning' })
  await deleteActivity(row.id)
  ElMessage.success('已删除')
  await load()
}

function onPageChange(p: number): void {
  query.page = p
  load()
}

onMounted(load)
</script>

<template>
  <div>
    <div class="panel-toolbar">
      <el-button type="primary" @click="openCreate">新增活动</el-button>
    </div>
    <el-table v-loading="loading" :data="list" border>
      <el-table-column prop="content" label="内容" min-width="200" />
      <el-table-column label="类别" width="100">
        <template #default="{ row }">{{ categoryLabel(row.category as ActivityCategory) }}</template>
      </el-table-column>
      <el-table-column label="发生时间" width="160">
        <template #default="{ row }">{{ formatDateTime(row.occurredAt) }}</template>
      </el-table-column>
      <el-table-column label="创建时间" width="160">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="140">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      class="panel-pagination"
      background
      layout="total, prev, pager, next"
      :total="total"
      :current-page="query.page"
      :page-size="query.size"
      @current-change="onPageChange"
    />

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑活动' : '新增活动'" width="480px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="内容" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="类别" prop="category">
          <el-select v-model="form.category" placeholder="选择类别">
            <el-option v-for="opt in CATEGORY_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="发生时间" prop="occurredAt">
          <el-date-picker v-model="form.occurredAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="留空取当前时间" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="onSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.panel-toolbar {
  margin-bottom: 12px;
}
.panel-pagination {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
```

- [ ] **Step 4: 实现 `panels/NotePanel.vue`**

```vue
<script setup lang="ts">
/**
 * 学习笔记 CRUD 面板：列表 + 新增/编辑弹窗 + 删除
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { createNote, deleteNote, listNotes, updateNote } from '@/api/note'
import type { INoteCreateDTO, INoteQuery, INoteUpdateDTO, INoteVO } from '@/types/note'
import { formatDateTime } from '@/utils/format'

const list = ref<INoteVO[]>([])
const total = ref(0)
const loading = ref(false)
const query = reactive<INoteQuery>({ page: 1, size: 10 })

async function load(): Promise<void> {
  loading.value = true
  try {
    const page = await listNotes(query)
    list.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}

const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<{ id?: string; title: string; content: string; tags: string }>({
  title: '',
  content: '',
  tags: '',
})

const rules: FormRules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入内容', trigger: 'blur' }],
}

function openCreate(): void {
  isEdit.value = false
  form.id = undefined
  form.title = ''
  form.content = ''
  form.tags = ''
  dialogVisible.value = true
}

function openEdit(row: INoteVO): void {
  isEdit.value = true
  form.id = row.id
  form.title = row.title
  form.content = row.content
  form.tags = row.tags || ''
  dialogVisible.value = true
}

async function onSubmit(): Promise<void> {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    try {
      if (isEdit.value && form.id) {
        const dto: INoteUpdateDTO = { title: form.title, content: form.content, tags: form.tags }
        await updateNote(form.id, dto)
        ElMessage.success('已更新')
      } else {
        const dto: INoteCreateDTO = { title: form.title, content: form.content, tags: form.tags }
        await createNote(dto)
        ElMessage.success('已新增')
      }
      dialogVisible.value = false
      await load()
    } catch {
      // 拦截器已提示
    }
  })
}

async function onDelete(row: INoteVO): Promise<void> {
  await ElMessageBox.confirm('确认删除该笔记？', '提示', { type: 'warning' })
  await deleteNote(row.id)
  ElMessage.success('已删除')
  await load()
}

function onPageChange(p: number): void {
  query.page = p
  load()
}

onMounted(load)
</script>

<template>
  <div>
    <div class="panel-toolbar">
      <el-button type="primary" @click="openCreate">新增笔记</el-button>
    </div>
    <el-table v-loading="loading" :data="list" border>
      <el-table-column prop="title" label="标题" min-width="180" />
      <el-table-column prop="tags" label="标签" width="160" />
      <el-table-column label="创建时间" width="160">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="140">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      class="panel-pagination"
      background
      layout="total, prev, pager, next"
      :total="total"
      :current-page="query.page"
      :page-size="query.size"
      @current-change="onPageChange"
    />

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑笔记' : '新增笔记'" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" />
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="5" />
        </el-form-item>
        <el-form-item label="标签" prop="tags">
          <el-input v-model="form.tags" placeholder="多个标签用逗号分隔" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="onSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.panel-toolbar {
  margin-bottom: 12px;
}
.panel-pagination {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
```

- [ ] **Step 5: 实现 `panels/TaskPanel.vue`（含状态筛选 + complete）**

```vue
<script setup lang="ts">
/**
 * 任务 CRUD 面板：列表 + 状态筛选 + 新增/编辑弹窗 + 删除 + 完成
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { completeTask, createTask, deleteTask, listTasks, updateTask } from '@/api/task'
import type { ITaskCreateDTO, ITaskQuery, ITaskUpdateDTO, ITaskVO } from '@/types/task'
import type { TaskStatus } from '@/types/enums'
import { formatDateTime } from '@/utils/format'

/** 任务状态选项（setup 顶层声明，template 可直接用） */
const STATUS_OPTIONS: { label: string; value: TaskStatus; tagType: 'info' | 'success' | 'warning' }[] = [
  { label: '待办', value: 'TODO', tagType: 'info' },
  { label: '进行中', value: 'DOING', tagType: 'warning' },
  { label: '已完成', value: 'DONE', tagType: 'success' },
]

/** 状态值 → 中文标签 */
function statusLabel(v: TaskStatus): string {
  return STATUS_OPTIONS.find((o) => o.value === v)?.label || v
}

/** 状态值 → el-tag type */
function statusTagType(v: TaskStatus): 'info' | 'success' | 'warning' {
  return STATUS_OPTIONS.find((o) => o.value === v)?.tagType ?? 'info'
}

const list = ref<ITaskVO[]>([])
const total = ref(0)
const loading = ref(false)
const query = reactive<ITaskQuery>({ page: 1, size: 10 })
const statusFilter = ref<TaskStatus | ''>('')

async function load(): Promise<void> {
  loading.value = true
  try {
    const q: ITaskQuery = { page: query.page, size: query.size }
    if (statusFilter.value) {
      q.status = statusFilter.value
    }
    const page = await listTasks(q)
    list.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}

function onFilter(): void {
  query.page = 1
  load()
}

const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<{ id?: string; title: string; status: TaskStatus }>({
  title: '',
  status: 'TODO',
})

const rules: FormRules = {
  title: [{ required: true, message: '请输入任务标题', trigger: 'blur' }],
}

function openCreate(): void {
  isEdit.value = false
  form.id = undefined
  form.title = ''
  form.status = 'TODO'
  dialogVisible.value = true
}

function openEdit(row: ITaskVO): void {
  isEdit.value = true
  form.id = row.id
  form.title = row.title
  form.status = row.status
  dialogVisible.value = true
}

async function onSubmit(): Promise<void> {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    try {
      if (isEdit.value && form.id) {
        const dto: ITaskUpdateDTO = { title: form.title, status: form.status }
        await updateTask(form.id, dto)
        ElMessage.success('已更新')
      } else {
        const dto: ITaskCreateDTO = { title: form.title, status: form.status }
        await createTask(dto)
        ElMessage.success('已新增')
      }
      dialogVisible.value = false
      await load()
    } catch {
      // 拦截器已提示
    }
  })
}

async function onDelete(row: ITaskVO): Promise<void> {
  await ElMessageBox.confirm('确认删除该任务？', '提示', { type: 'warning' })
  await deleteTask(row.id)
  ElMessage.success('已删除')
  await load()
}

async function onComplete(row: ITaskVO): Promise<void> {
  await completeTask(row.id)
  ElMessage.success('已标记完成')
  await load()
}

function onPageChange(p: number): void {
  query.page = p
  load()
}

onMounted(load)
</script>

<template>
  <div>
    <div class="panel-toolbar">
      <el-button type="primary" @click="openCreate">新增任务</el-button>
      <el-select
        v-model="statusFilter"
        placeholder="按状态筛选"
        clearable
        style="margin-left: 12px; width: 140px"
        @change="onFilter"
      >
        <el-option v-for="opt in STATUS_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
      </el-select>
    </div>
    <el-table v-loading="loading" :data="list" border>
      <el-table-column prop="title" label="标题" min-width="200" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status as TaskStatus)">
            {{ statusLabel(row.status as TaskStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="完成时间" width="160">
        <template #default="{ row }">{{ formatDateTime(row.completedAt) }}</template>
      </el-table-column>
      <el-table-column label="创建时间" width="160">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button
            v-if="row.status !== 'DONE'"
            link
            type="success"
            @click="onComplete(row)"
          >
            完成
          </el-button>
          <el-button link type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      class="panel-pagination"
      background
      layout="total, prev, pager, next"
      :total="total"
      :current-page="query.page"
      :page-size="query.size"
      @current-change="onPageChange"
    />

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑任务' : '新增任务'" width="440px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="form.status">
            <el-option v-for="opt in STATUS_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="onSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.panel-toolbar {
  margin-bottom: 12px;
}
.panel-pagination {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
```

> 注：`STATUS_OPTIONS` 的 `tagType` 直接用 `'info' | 'success' | 'warning'` 字面量联合，`statusTagType` 返回同类型，确保传给 el-tag `:type` 类型合法（el-tag 接受 `'info'|'success'|'warning'|'danger'|''`，本组件用前三者）。

- [ ] **Step 6: 实现 `InputView.vue`（覆盖占位）**

```vue
<script setup lang="ts">
/**
 * 数据录入页：Activity / Note / Task 三 tab
 */
import { ref } from 'vue'
import ActivityPanel from './panels/ActivityPanel.vue'
import NotePanel from './panels/NotePanel.vue'
import TaskPanel from './panels/TaskPanel.vue'

const activeTab = ref<'activity' | 'note' | 'task'>('activity')
</script>

<template>
  <el-tabs v-model="activeTab">
    <el-tab-pane label="活动" name="activity">
      <ActivityPanel />
    </el-tab-pane>
    <el-tab-pane label="笔记" name="note">
      <NotePanel />
    </el-tab-pane>
    <el-tab-pane label="任务" name="task">
      <TaskPanel />
    </el-tab-pane>
  </el-tabs>
</template>
```

- [ ] **Step 7: 运行确认绿 + 类型检查**

Run: `cd dayflow-web && npm test && npx vue-tsc --noEmit`
Expected: `InputView` tab 测试 PASS；全部既有测试 PASS；类型检查无错。

- [ ] **Step 8: dev 冒烟（手动，需后端运行）**

Run: `cd dayflow-web && npm run dev`
验证（每个 tab）：
1. 活动：新增（内容+类别，发生时间留空）→ 列表出现 → 编辑 → 删除（二次确认）
2. 笔记：新增（标题+内容+标签）→ 编辑 → 删除
3. 任务：新增 → 切状态筛选 → 点"完成"（status 变 DONE + 完成时间填充，"完成"按钮消失）→ 编辑/删除
4. 分页：单页 >10 条时翻页生效

- [ ] **Step 9: 提交**

```bash
git branch --show-current   # 必须是 feature/m4-frontend
git add dayflow-web/src
git commit -m "feat(m4): 数据录入页 InputView（Activity/Note/Task 三 tab CRUD + Task complete）"
```

---

## Task 7: 报告页 ReportView（生成触发 + 轮询 + 三态 + MarkdownView）

**Files:**
- Create: `dayflow-web/src/stores/report.ts`
- Create: `dayflow-web/src/composables/useReportPolling.ts`
- Create: `dayflow-web/src/components/MarkdownView.vue`
- Create: `dayflow-web/src/components/AgentTimeline.vue`（占位，Task 8 覆盖）
- Modify: `dayflow-web/src/views/report/ReportView.vue`（覆盖 Task 5 占位）
- Test: `dayflow-web/src/stores/__tests__/report.test.ts`
- Test: `dayflow-web/src/composables/__tests__/useReportPolling.test.ts`
- Test: `dayflow-web/src/components/__tests__/MarkdownView.test.ts`

**Interfaces:**
- Consumes: `api/report`（`generateReport`/`getReport`/`listTraces`）、`markdown-it` + `dompurify`、AgentTimeline（Task 8）
- Produces:
  - `useReportStore`（`@/stores/report`）：`isGenerating` + `triggerGenerate(dto): Promise<string>`（POST /generate → reportId）
  - `useReportPolling(reportId: Ref<string|undefined>)`（`@/composables/useReportPolling`）：返回 `{ report: Ref<IReportVO|null>, traces: Ref<IAgentTraceVO[]>, isRunning: Ref<boolean>, start(), stop() }` —— `start` 立即拉一次 + 每 2500ms 轮询，`status` 为 `GENERATED`/`FAILED` 时 `stop`；不自动 `onUnmounted`，由 ReportView 调 `stop`
  - `MarkdownView`（`@/components/MarkdownView`）：props `content: string`；markdown-it 渲染后 DOMPurify 净化再 `v-html`
  - `ReportView`：顶部生成触发区（date-picker + 「生成日报」→ `triggerGenerate` → `router.push('/reports/'+id)`）+ 双栏（左按 status 切换：GENERATING 进度骨架/GENERATED MarkdownView/FAILED el-result+重试；右 AgentTimeline）

> 设计说明：`reportStore` 仅保留 `triggerGenerate`（生成动作，跨 HistoryView/ReportView 复用）；报告详情态（currentReport/traces）由 `useReportPolling` 局部管理 —— 详情页内状态非全局共享（YAGNI），是对 spec §5.2 的简化（self-review 记录）。

- [ ] **Step 1: 写 reportStore 失败测试（红）**

`dayflow-web/src/stores/__tests__/report.test.ts`：

```typescript
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as reportApi from '@/api/report'
import { useReportStore } from '../report'

describe('useReportStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('triggerGenerate 调 generateReport 返回 reportId', async () => {
    const spy = vi.spyOn(reportApi, 'generateReport').mockResolvedValue('99')
    const store = useReportStore()
    const id = await store.triggerGenerate({ type: 'DAILY', date: '2026-07-10' })
    expect(id).toBe('99')
    expect(spy).toHaveBeenCalledWith({ type: 'DAILY', date: '2026-07-10' })
  })

  it('triggerGenerate 期间 isGenerating 为 true，结束恢复 false', async () => {
    vi.spyOn(reportApi, 'generateReport').mockResolvedValue('99')
    const store = useReportStore()
    expect(store.isGenerating).toBe(false)
    const p = store.triggerGenerate({ type: 'DAILY', date: '2026-07-10' })
    expect(store.isGenerating).toBe(true)
    await p
    expect(store.isGenerating).toBe(false)
  })
})
```

- [ ] **Step 2: 运行确认失败**

Run: `cd dayflow-web && npm test`
Expected: FAIL —— `Cannot find module '../report'`。

- [ ] **Step 3: 实现 `stores/report.ts`（绿）**

```typescript
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { generateReport } from '@/api/report'
import type { IReportGenerateDTO } from '@/types/report'

/**
 * 报告 Store
 * 仅管「触发生成」动作（跨 HistoryView/ReportView 复用）；
 * 报告详情态（report/traces）由 useReportPolling composable 局部管理。
 */
export const useReportStore = defineStore('report', () => {
  /** 生成请求进行中（控制按钮 loading） */
  const isGenerating = ref(false)

  /**
   * 触发日报生成：POST /api/reports/generate → 返回 reportId
   */
  async function triggerGenerate(dto: IReportGenerateDTO): Promise<string> {
    isGenerating.value = true
    try {
      return await generateReport(dto)
    } finally {
      isGenerating.value = false
    }
  }

  return { isGenerating, triggerGenerate }
})
```

- [ ] **Step 4: 运行确认绿**

Run: `cd dayflow-web && npm test`
Expected: `useReportStore` 2 项 PASS。

- [ ] **Step 5: 写 useReportPolling 失败测试（红）**

`dayflow-web/src/composables/__tests__/useReportPolling.test.ts`：

```typescript
import { ref } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as reportApi from '@/api/report'
import { useReportPolling } from '../useReportPolling'
import type { IReportVO } from '@/types/report'

describe('useReportPolling', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  it('start 立即拉一次并周期轮询，GENERATED 时停止', async () => {
    const reportId = ref('1')
    const generating: IReportVO = { id: '1', userId: 'u', type: 'DAILY', periodStart: '', periodEnd: '', title: '', content: '', status: 'GENERATING', errorMsg: null, tokenUsage: 0, createdAt: '' }
    const generated: IReportVO = { ...generating, status: 'GENERATED', content: '# done' }
    const getReport = vi.spyOn(reportApi, 'getReport')
      .mockResolvedValueOnce(generating)
      .mockResolvedValueOnce(generated)
    const listTraces = vi.spyOn(reportApi, 'listTraces').mockResolvedValue([])

    const { report, isRunning, start } = useReportPolling(reportId)
    start()

    await vi.advanceTimersByTimeAsync(0)
    expect(getReport).toHaveBeenCalledTimes(1)
    expect(report.value?.status).toBe('GENERATING')
    expect(isRunning.value).toBe(true)

    await vi.advanceTimersByTimeAsync(2500)
    expect(getReport).toHaveBeenCalledTimes(2)
    expect(listTraces).toHaveBeenCalledTimes(2)
    expect(report.value?.status).toBe('GENERATED')
    expect(isRunning.value).toBe(false)
  })

  it('FAILED 时也停止', async () => {
    const reportId = ref('1')
    const failed: IReportVO = { id: '1', userId: 'u', type: 'DAILY', periodStart: '', periodEnd: '', title: '', content: '', status: 'FAILED', errorMsg: '出错了', tokenUsage: 0, createdAt: '' }
    vi.spyOn(reportApi, 'getReport').mockResolvedValue(failed)
    vi.spyOn(reportApi, 'listTraces').mockResolvedValue([])

    const { report, isRunning, start } = useReportPolling(reportId)
    start()

    await vi.advanceTimersByTimeAsync(0)
    expect(report.value?.status).toBe('FAILED')
    expect(isRunning.value).toBe(false)
  })

  it('stop 清定时器，不再轮询', async () => {
    const reportId = ref('1')
    const generating: IReportVO = { id: '1', userId: 'u', type: 'DAILY', periodStart: '', periodEnd: '', title: '', content: '', status: 'GENERATING', errorMsg: null, tokenUsage: 0, createdAt: '' }
    const getReport = vi.spyOn(reportApi, 'getReport').mockResolvedValue(generating)
    vi.spyOn(reportApi, 'listTraces').mockResolvedValue([])

    const { start, stop } = useReportPolling(reportId)
    start()
    await vi.advanceTimersByTimeAsync(0)
    expect(getReport).toHaveBeenCalledTimes(1)

    stop()
    await vi.advanceTimersByTimeAsync(5000)
    expect(getReport).toHaveBeenCalledTimes(1) // stop 后不再增加
  })
})
```

- [ ] **Step 6: 运行确认失败**

Run: `cd dayflow-web && npm test`
Expected: FAIL —— `Cannot find module '../useReportPolling'`。

- [ ] **Step 7: 实现 `composables/useReportPolling.ts`（绿）**

```typescript
import { ref, type Ref } from 'vue'
import { getReport, listTraces } from '@/api/report'
import type { IAgentTraceVO, IReportVO } from '@/types/report'

/** 轮询间隔（ms） */
const POLL_INTERVAL = 2500

/**
 * 报告轮询 composable
 * - start：立即拉一次 + 每 POLL_INTERVAL 轮询 report 与 traces
 * - status 为 GENERATED/FAILED 时自动 stop
 * - 不自动 onUnmounted；调用方需在 onUnmounted 调 stop，防定时器泄漏
 *
 * @param reportId 响应式 reportId（Ref）；为 undefined 时 tick 直接返回
 */
export function useReportPolling(reportId: Ref<string | undefined>) {
  const report = ref<IReportVO | null>(null)
  const traces = ref<IAgentTraceVO[]>([])
  const isRunning = ref(false)
  let timer: ReturnType<typeof setInterval> | null = null

  /** 单次轮询：拉 report + traces，三态停止 */
  async function tick(): Promise<void> {
    if (!reportId.value) return
    try {
      report.value = await getReport(reportId.value)
      traces.value = await listTraces(reportId.value)
      if (report.value.status === 'GENERATED' || report.value.status === 'FAILED') {
        stop()
      }
    } catch {
      // 单次轮询失败容忍（网络抖动），不中断整体轮询
    }
  }

  /** 启动轮询 */
  function start(): void {
    if (isRunning.value) return
    isRunning.value = true
    void tick()
    timer = setInterval(() => void tick(), POLL_INTERVAL)
  }

  /** 停止轮询并清定时器 */
  function stop(): void {
    isRunning.value = false
    if (timer !== null) {
      clearInterval(timer)
      timer = null
    }
  }

  return { report, traces, isRunning, start, stop }
}
```

- [ ] **Step 8: 运行确认绿**

Run: `cd dayflow-web && npm test`
Expected: `useReportPolling` 3 项 PASS。

- [ ] **Step 9: 写 MarkdownView 失败测试（红）**

`dayflow-web/src/components/__tests__/MarkdownView.test.ts`：

```typescript
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import MarkdownView from '../MarkdownView.vue'

describe('MarkdownView', () => {
  it('把 markdown 渲染为 HTML', () => {
    const wrapper = mount(MarkdownView, { props: { content: '# 标题\n\n正文段落' } })
    const html = wrapper.html()
    expect(html).toContain('<h1>标题</h1>')
    expect(html).toContain('<p>正文段落</p>')
  })

  it('净化潜在 XSS（不含 script / onerror）', () => {
    const wrapper = mount(MarkdownView, {
      props: { content: '<script>alert(1)</script>\n\n<img src=x onerror=alert(1)>' },
    })
    const html = wrapper.html()
    expect(html).not.toContain('<script>')
    expect(html).not.toContain('onerror')
  })

  it('空内容安全渲染', () => {
    const wrapper = mount(MarkdownView, { props: { content: '' } })
    expect(wrapper.exists()).toBe(true)
  })
})
```

- [ ] **Step 10: 运行确认失败**

Run: `cd dayflow-web && npm test`
Expected: FAIL —— `Cannot find module '../MarkdownView.vue'`。

- [ ] **Step 11: 实现 `components/MarkdownView.vue`（绿）**

```vue
<script setup lang="ts">
/**
 * Markdown 渲染组件
 * markdown-it 转 HTML 后必经 DOMPurify.sanitize 净化（防 LLM 产出恶意 HTML）
 */
import { computed } from 'vue'
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'

const props = defineProps<{
  /** markdown 原文（后端 report.content） */
  content: string
}>()

/** markdown-it 实例：禁原始 HTML（双保险，DOMPurify 仍会再净化） */
const md = new MarkdownIt({ html: false, linkify: true, breaks: false })

/** 渲染 + 净化后的安全 HTML */
const html = computed<string>(() => {
  const raw = md.render(props.content || '')
  return DOMPurify.sanitize(raw)
})
</script>

<template>
  <div class="markdown-view" v-html="html" />
</template>

<style scoped>
.markdown-view {
  line-height: 1.7;
  font-size: 14px;
}
.markdown-view :deep(h1),
.markdown-view :deep(h2),
.markdown-view :deep(h3) {
  margin: 16px 0 8px;
  font-weight: 600;
}
.markdown-view :deep(p) {
  margin: 8px 0;
}
.markdown-view :deep(ul),
.markdown-view :deep(ol) {
  padding-left: 24px;
}
.markdown-view :deep(code) {
  background: #f5f7fa;
  padding: 2px 4px;
  border-radius: 3px;
  font-family: monospace;
}
</style>
```

- [ ] **Step 12: 运行确认绿**

Run: `cd dayflow-web && npm test`
Expected: `MarkdownView` 3 项 PASS。

- [ ] **Step 13: 创建占位 `components/AgentTimeline.vue`（Task 8 覆盖）**

```vue
<script setup lang="ts">
/**
 * Agent 协作时间线（占位，Task 8 实现）
 */
import type { IAgentTraceVO } from '@/types/report'

defineProps<{
  traces: IAgentTraceVO[]
  active?: boolean
}>()
</script>

<template>
  <div class="agent-timeline-placeholder">Agent 时间线（Task 8 实现）</div>
</template>

<style scoped>
.agent-timeline-placeholder {
  padding: 24px;
  color: #909399;
  text-align: center;
}
</style>
```

- [ ] **Step 14: 实现 `views/report/ReportView.vue`（覆盖占位）**

```vue
<script setup lang="ts">
/**
 * 报告查看页：顶部生成触发区 + 双栏（左 MarkdownView 按状态切换 / 右 AgentTimeline）
 */
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useReportStore } from '@/stores/report'
import { useReportPolling } from '@/composables/useReportPolling'
import MarkdownView from '@/components/MarkdownView.vue'
import AgentTimeline from '@/components/AgentTimeline.vue'
import { todayString } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const reportStore = useReportStore()

/** 当前报告 id（来自路由 /reports/:id） */
const reportId = computed<string | undefined>(() => route.params.id as string | undefined)

const date = ref<string>(todayString())
const generating = ref(false)

const { report, traces, isRunning, start, stop } = useReportPolling(reportId)

/** 生成日报：triggerGenerate → 跳新 reportId */
async function onGenerate(): Promise<void> {
  generating.value = true
  try {
    const id = await reportStore.triggerGenerate({ type: 'DAILY', date: date.value })
    router.push('/reports/' + id)
  } catch {
    // 拦截器已提示
  } finally {
    generating.value = false
  }
}

onMounted(() => {
  if (reportId.value) {
    start()
  }
})

watch(reportId, (id) => {
  if (id) {
    start()
  } else {
    stop()
  }
})

onUnmounted(() => stop())
</script>

<template>
  <div class="report-view">
    <el-card class="generate-bar" shadow="never">
      <el-date-picker
        v-model="date"
        type="date"
        value-format="YYYY-MM-DD"
        placeholder="选择日期"
        style="width: 180px"
      />
      <el-button
        type="primary"
        :loading="generating || reportStore.isGenerating"
        style="margin-left: 12px"
        @click="onGenerate"
      >
        生成日报
      </el-button>
    </el-card>

    <el-row :gutter="16" class="report-body">
      <el-col :span="15">
        <el-card shadow="never">
          <!-- 未加载/未生成 -->
          <div v-if="!report" class="report-empty">
            选择日期点「生成日报」，或正在加载报告…
          </div>
          <!-- 生成中 -->
          <div v-else-if="report.status === 'GENERATING'">
            <el-skeleton :rows="6" animated />
            <p class="report-hint">4 Agent 协作中… 已产出 {{ traces.length }} 条轨迹</p>
          </div>
          <!-- 生成成功 -->
          <MarkdownView
            v-else-if="report.status === 'GENERATED'"
            :content="report.content"
          />
          <!-- 生成失败 -->
          <el-result
            v-else-if="report.status === 'FAILED'"
            icon="error"
            title="报告生成失败"
            :sub-title="report.errorMsg || '请稍后重试'"
          >
            <template #extra>
              <el-button type="primary" @click="onGenerate">重新生成</el-button>
            </template>
          </el-result>
        </el-card>
      </el-col>
      <el-col :span="9">
        <el-card shadow="never">
          <template #header>Agent 协作时间线</template>
          <AgentTimeline :traces="traces" :active="isRunning" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.report-view {
  padding: 0 0 16px;
}
.generate-bar {
  margin-bottom: 16px;
}
.report-body {
  align-items: stretch;
}
.report-empty {
  padding: 48px 0;
  text-align: center;
  color: #909399;
}
.report-hint {
  margin-top: 12px;
  color: #606266;
}
</style>
```

- [ ] **Step 15: 全量测试 + 类型检查**

Run: `cd dayflow-web && npm test && npx vue-tsc --noEmit`
Expected: 全部测试 PASS（reportStore + useReportPolling + MarkdownView + 既有）；类型检查无错。

- [ ] **Step 16: dev 冒烟（手动，需后端运行 + DEEPSEEK_API_KEY）**

前置：后端已配 DeepSeek key 启动；先在录入页录入若干 Activity/Note/Task。

Run: `cd dayflow-web && npm run dev`
验证：
1. 进入 `/reports`（HistoryView 占位，Task 9 实现）—— 暂在地址栏直接访问 `/reports` 后改 `/reports/<某id>`，或从 ReportView 顶部触发：
2. 访问 `/reports/<任一已有id>` 或在任意页地址栏输入 `/reports/1`，再点顶部「生成日报」→ 跳新 `/reports/<id>`
3. 左栏：先显示 GENERATING 进度骨架 + 右栏轨迹渐进出现（Planner→Collector→Writer→Reviewer）
4. 最终左栏渲染 Markdown 报告内容 + 右栏 ≥4 条完整轨迹
5. 若 LLM 失败：左栏 el-result 显示 errorMsg + 「重新生成」
6. 切换/离开页面：定时器清理（无控制台持续请求）

- [ ] **Step 17: 提交**

```bash
git branch --show-current   # 必须是 feature/m4-frontend
git add dayflow-web/src
git commit -m "feat(m4): 报告页 ReportView（生成触发 + useReportPolling 轮询 + 三态 + MarkdownView）"
```

---

## Task 8: AgentTimeline 组件（垂直时间轴 + 返工徽章 + 渐进高亮）

**Files:**
- Modify: `dayflow-web/src/components/AgentTimeline.vue`（覆盖 Task 7 占位）
- Test: `dayflow-web/src/components/__tests__/AgentTimeline.test.ts`

**Interfaces:**
- Consumes: `IAgentTraceVO[]`、`AgentName` 枚举
- Produces: `AgentTimeline`（`@/components/AgentTimeline`）—— props `{ traces: IAgentTraceVO[]; active?: boolean }`：`el-timeline` 按 `step` 升序渲染；每项按 `agentName` 区分四色 + 「Step N」+ `retryCount>0` 显示「返工 #N」徽章；`el-collapse` 折叠 input/output 摘要 + timestamp 显示耗时/token；空轨迹（`active=true`）显示「Agent 即将开始协作…」占位。

- [ ] **Step 1: 写 AgentTimeline 失败测试（红）**

`dayflow-web/src/components/__tests__/AgentTimeline.test.ts`：

```typescript
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { describe, expect, it } from 'vitest'
import AgentTimeline from '../AgentTimeline.vue'
import type { IAgentTraceVO } from '@/types/report'

/** 构造一条轨迹，覆盖默认值 */
function makeTrace(over: Partial<IAgentTraceVO>): IAgentTraceVO {
  return {
    id: '1',
    reportId: 'r',
    agentName: 'PLANNER',
    step: 1,
    inputSummary: '输入摘要',
    outputSummary: '输出摘要',
    tokens: 100,
    latencyMs: 50,
    retryCount: 0,
    createdAt: '',
    ...over,
  }
}

describe('AgentTimeline', () => {
  it('按 step 升序渲染', () => {
    const wrapper = mount(AgentTimeline, {
      props: {
        traces: [
          makeTrace({ id: '3', step: 3 }),
          makeTrace({ id: '1', step: 1 }),
          makeTrace({ id: '2', step: 2 }),
        ],
      },
      global: { plugins: [ElementPlus] },
    })
    const steps = wrapper.findAll('.trace-step').map((el) => el.text())
    expect(steps).toEqual(['Step 1', 'Step 2', 'Step 3'])
  })

  it('retryCount>0 显示返工徽章', () => {
    const wrapper = mount(AgentTimeline, {
      props: { traces: [makeTrace({ id: '1', step: 1, retryCount: 2 })] },
      global: { plugins: [ElementPlus] },
    })
    expect(wrapper.text()).toContain('返工 #2')
  })

  it('按 agentName 渲染中文角色名', () => {
    const wrapper = mount(AgentTimeline, {
      props: { traces: [makeTrace({ id: '1', agentName: 'WRITER', step: 1 })] },
      global: { plugins: [ElementPlus] },
    })
    expect(wrapper.text()).toContain('撰写员')
  })

  it('空轨迹 + active 显示协作占位', () => {
    const wrapper = mount(AgentTimeline, {
      props: { traces: [], active: true },
      global: { plugins: [ElementPlus] },
    })
    expect(wrapper.text()).toContain('Agent 即将开始协作')
  })
})
```

- [ ] **Step 2: 运行确认失败**

Run: `cd dayflow-web && npm test`
Expected: FAIL —— AgentTimeline 仍是占位（不含 `.trace-step` 等）。

- [ ] **Step 3: 实现 `components/AgentTimeline.vue`（绿，覆盖占位）**

```vue
<script setup lang="ts">
/**
 * Agent 协作时间线（核心卖点）
 * el-timeline 按 step 升序渲染 4 Agent 轨迹：四色区分、返工徽章、折叠摘要
 */
import { computed } from 'vue'
import type { IAgentTraceVO } from '@/types/report'
import type { AgentName } from '@/types/enums'

const props = defineProps<{
  /** 轨迹列表（将按 step 升序渲染） */
  traces: IAgentTraceVO[]
  /** 是否生成进行中（影响空轨迹占位文案） */
  active?: boolean
}>()

/** 各 Agent 角色元信息：中文标签 + 主题色 */
const AGENT_META: Record<AgentName, { label: string; color: string }> = {
  PLANNER: { label: '规划师', color: '#409eff' },
  COLLECTOR: { label: '采集员', color: '#67c23a' },
  WRITER: { label: '撰写员', color: '#e6a23c' },
  REVIEWER: { label: '评审员', color: '#f56c6c' },
}

/** 按 step 升序排序（不改原数组） */
const sortedTraces = computed<IAgentTraceVO[]>(() =>
  [...props.traces].sort((a, b) => a.step - b.step),
)
</script>

<template>
  <div class="agent-timeline">
    <div v-if="sortedTraces.length === 0" class="timeline-empty">
      {{ active ? 'Agent 即将开始协作…' : '暂无协作轨迹' }}
    </div>
    <el-timeline v-else>
      <el-timeline-item
        v-for="(t, idx) in sortedTraces"
        :key="t.id"
        :color="AGENT_META[t.agentName]?.color || '#909399'"
        :timestamp="`耗时 ${t.latencyMs}ms · ${t.tokens} tokens`"
        placement="top"
        :hollow="active && idx === sortedTraces.length - 1"
      >
        <div class="trace-head">
          <span class="trace-agent" :style="{ color: AGENT_META[t.agentName]?.color }">
            {{ AGENT_META[t.agentName]?.label || t.agentName }}
          </span>
          <span class="trace-step">Step {{ t.step }}</span>
          <el-tag v-if="t.retryCount > 0" type="warning" size="small">
            返工 #{{ t.retryCount }}
          </el-tag>
        </div>
        <el-collapse>
          <el-collapse-item title="输入摘要">
            <div class="trace-summary">{{ t.inputSummary }}</div>
          </el-collapse-item>
          <el-collapse-item title="输出摘要">
            <div class="trace-summary">{{ t.outputSummary }}</div>
          </el-collapse-item>
        </el-collapse>
      </el-timeline-item>
    </el-timeline>
  </div>
</template>

<style scoped>
.agent-timeline {
  min-height: 120px;
}
.timeline-empty {
  padding: 32px 0;
  text-align: center;
  color: #909399;
}
.trace-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}
.trace-agent {
  font-weight: 600;
}
.trace-step {
  color: #606266;
  font-size: 13px;
}
.trace-summary {
  white-space: pre-wrap;
  word-break: break-word;
  color: #606266;
  font-size: 13px;
}
</style>
```

> `el-timeline-item` 的 `:hollow` 让生成中最新一步（`idx === length-1`）空心高亮，区分已完成的实心节点。

- [ ] **Step 4: 运行确认绿 + 类型检查**

Run: `cd dayflow-web && npm test && npx vue-tsc --noEmit`
Expected: `AgentTimeline` 4 项 PASS；类型检查无错。

- [ ] **Step 5: dev 冒烟（手动）**

Run: `cd dayflow-web && npm run dev`
验证：触发日报生成后，右栏 AgentTimeline 按 step 渐进出现 4 条轨迹（规划师蓝/采集员绿/撰写员橙/评审员红），最新步骤空心高亮；展开折叠看输入/输出摘要；若 Reviewer 返工，显示「返工 #1」徽章。

- [ ] **Step 6: 提交**

```bash
git branch --show-current   # 必须是 feature/m4-frontend
git add dayflow-web/src
git commit -m "feat(m4): AgentTimeline 协作时间线（四色 + 返工徽章 + 折叠摘要 + 渐进高亮）"
```

---

## Task 9: 历史报告列表 HistoryView（分页 + 状态 tag + 跳详情 + 删除）

**Files:**
- Modify: `dayflow-web/src/views/history/HistoryView.vue`（覆盖 Task 5 占位）
- Modify: `dayflow-web/src/setupTests.ts`（补 `ResizeObserver` stub，EP 表格在 jsdom 渲染依赖）
- Test: `dayflow-web/src/views/history/__tests__/HistoryView.test.ts`

**Interfaces:**
- Consumes: `api/report`（`pageReports`/`deleteReport`）、`useReportStore`（`triggerGenerate`）、`utils/format`
- Produces: `HistoryView` —— 顶部「生成今日日报」按钮（`triggerGenerate({type:'DAILY', date: today})` → 跳 `/reports/:id`）+ `el-table`（标题/类型/周期/状态 tag/Token/创建时间/操作）+ `el-pagination`；操作：查看（`router.push('/reports/:id')`）、删除（二次确认 + `DELETE`）。状态 tag：GENERATING=info/GENERATED=success/FAILED=danger。

- [ ] **Step 1: 更新 `setupTests.ts`（补 ResizeObserver stub）**

替换 Task 1 占位版 `src/setupTests.ts` 为：

```typescript
import { vi } from 'vitest'

/**
 * Vitest 全局 setup
 * 补 ResizeObserver stub：Element Plus 的 el-table / el-select 等组件在 jsdom 下
 * 依赖 ResizeObserver 做布局计算，无 stub 时渲染异常/行不出现。
 */
class ResizeObserverStub {
  observe(): void {}
  unobserve(): void {}
  disconnect(): void {}
}
vi.stubGlobal('ResizeObserver', ResizeObserverStub)
```

- [ ] **Step 2: 写 HistoryView 失败测试（红）**

`dayflow-web/src/views/history/__tests__/HistoryView.test.ts`：

```typescript
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import HistoryView from '../HistoryView.vue'
import * as reportApi from '@/api/report'
import type { IReportVO } from '@/types/report'

describe('HistoryView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('加载并渲染报告列表 + 状态文案', async () => {
    const report: IReportVO = {
      id: '1',
      userId: 'u',
      type: 'DAILY',
      periodStart: '2026-07-10',
      periodEnd: '2026-07-10',
      title: '7月10日日报',
      status: 'GENERATED',
      errorMsg: null,
      tokenUsage: 500,
      createdAt: '2026-07-10T10:00:00',
    }
    vi.spyOn(reportApi, 'pageReports').mockResolvedValue({
      records: [report],
      total: 1,
      size: 10,
      current: 1,
      pages: 1,
    })

    const wrapper = mount(HistoryView, { global: { plugins: [ElementPlus] } })
    await nextTick()
    await nextTick()

    expect(reportApi.pageReports).toHaveBeenCalled()
    expect(wrapper.text()).toContain('7月10日日报')
    expect(wrapper.text()).toContain('已完成')
  })
})
```

- [ ] **Step 3: 运行确认失败**

Run: `cd dayflow-web && npm test`
Expected: FAIL —— HistoryView 仍是占位（不含「7月10日日报」等）。

- [ ] **Step 4: 实现 `views/history/HistoryView.vue`（绿，覆盖占位）**

```vue
<script setup lang="ts">
/**
 * 历史报告列表：分页 + 状态 tag + 跳详情 + 删除 + 生成今日日报
 */
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteReport, pageReports } from '@/api/report'
import { useReportStore } from '@/stores/report'
import type { IReportQuery, IReportVO } from '@/types/report'
import type { ReportStatus } from '@/types/enums'
import { formatDateTime, todayString } from '@/utils/format'

const router = useRouter()
const reportStore = useReportStore()

const list = ref<IReportVO[]>([])
const total = ref(0)
const loading = ref(false)
const query = reactive<IReportQuery>({ page: 1, size: 10 })

/** 状态 → tag 文案与颜色 */
const STATUS_TAG: Record<ReportStatus, { label: string; type: 'info' | 'success' | 'danger' }> = {
  GENERATING: { label: '生成中', type: 'info' },
  GENERATED: { label: '已完成', type: 'success' },
  FAILED: { label: '失败', type: 'danger' },
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const p = await pageReports(query)
    list.value = p.records
    total.value = p.total
  } finally {
    loading.value = false
  }
}

function onView(row: IReportVO): void {
  router.push('/reports/' + row.id)
}

async function onDelete(row: IReportVO): Promise<void> {
  await ElMessageBox.confirm('确认删除该报告？', '提示', { type: 'warning' })
  await deleteReport(row.id)
  ElMessage.success('已删除')
  await load()
}

async function onGenerate(): Promise<void> {
  const id = await reportStore.triggerGenerate({ type: 'DAILY', date: todayString() })
  router.push('/reports/' + id)
}

function onPageChange(p: number): void {
  query.page = p
  load()
}

onMounted(load)
</script>

<template>
  <div>
    <div class="history-toolbar">
      <el-button type="primary" :loading="reportStore.isGenerating" @click="onGenerate">
        生成今日日报
      </el-button>
    </div>
    <el-table v-loading="loading" :data="list" border>
      <el-table-column prop="title" label="标题" min-width="180" />
      <el-table-column label="类型" width="90">
        <template #default="{ row }">{{ row.type === 'DAILY' ? '日报' : '周报' }}</template>
      </el-table-column>
      <el-table-column label="周期" width="200">
        <template #default="{ row }">{{ row.periodStart }} ~ {{ row.periodEnd }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="STATUS_TAG[row.status as ReportStatus].type">
            {{ STATUS_TAG[row.status as ReportStatus].label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="tokenUsage" label="Token" width="100" />
      <el-table-column label="创建时间" width="160">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="140">
        <template #default="{ row }">
          <el-button link type="primary" @click="onView(row)">查看</el-button>
          <el-button link type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      class="history-pagination"
      background
      layout="total, prev, pager, next"
      :total="total"
      :current-page="query.page"
      :page-size="query.size"
      @current-change="onPageChange"
    />
  </div>
</template>

<style scoped>
.history-toolbar {
  margin-bottom: 12px;
}
.history-pagination {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
```

- [ ] **Step 5: 运行确认绿 + 类型检查**

Run: `cd dayflow-web && npm test && npx vue-tsc --noEmit`
Expected: `HistoryView` 测试 PASS；全部既有测试 PASS（setupTests 的 ResizeObserver stub 对 InputView/AgentTimeline 等含 EP 组件的测试更稳）；类型检查无错。

- [ ] **Step 6: dev 冒烟（手动）**

Run: `cd dayflow-web && npm run dev`
验证：
1. 进入 `/reports` → 列表展示历史报告，状态 tag 颜色正确（生成中灰/已完成绿/失败红）
2. 「查看」→ 跳 `/reports/:id` 进入 ReportView 轮询
3. 「删除」→ 二次确认 → 列表刷新
4. 「生成今日日报」→ 生成 → 跳新报告详情页
5. 分页：报告 >10 条时翻页

- [ ] **Step 7: 提交**

```bash
git branch --show-current   # 必须是 feature/m4-frontend
git add dayflow-web/src
git commit -m "feat(m4): 历史报告列表 HistoryView（分页 + 状态 tag + 跳详情 + 删除 + 生成入口）"
```

---

## Task 10: 收尾（全量测试 + dev 联调验收）

**Files:**
- 无新增/修改（验收 task）

**Interfaces:**
- Consumes: Task 1-9 全部产出
- Produces: M4 里程碑验收通过（spec §12 八项验收标准）；可选打 tag `m4-complete`（**需用户明确授权**）

- [ ] **Step 1: 前端全量测试 + 构建**

Run: `cd dayflow-web && npm test && npm run build`
Expected:
- Vitest 全绿（authStore / guard / api index / report store / useReportPolling / MarkdownView / AgentTimeline / AppLayout / InputView / HistoryView / smoke）
- `vue-tsc --noEmit` 无类型错误
- `vite build` 产物生成（`dist/`）

- [ ] **Step 2: 后端全量测试**

Run: `mvn -f dayflow-server/pom.xml test`
Expected: 全绿（含 Task 3 新增的 `UserAuthServiceImplRegisterTest` / `AuthControllerTest` / 扩展的 `UserAuthServiceImplTest`，以及 M1-M3 既有测试无回归）。

- [ ] **Step 3: dev 联调验收（对照 spec §12 八项）**

前置：后端配 `DEEPSEEK_API_KEY` 启动（`mvn -f dayflow-server/pom.xml spring-boot:run`）；前端 `cd dayflow-web && npm run dev`。

逐项验收：
1. ☑ `npm run build` 成功 + `npm test` 全绿（Step 1）
2. ☑ 后端 `mvn test` 全绿（Step 2）
3. ☑ 端到端：注册 → 登录 → 录入 Activity/Note/Task → 生成日报 → 轮询到 `GENERATED` + content 可读 + 时间线 ≥4 条轨迹（Planner/Collector/Writer/Reviewer）
4. ☑ 三态：`GENERATING`（进度骨架 + 轨迹渐进）/ `GENERATED`（Markdown 渲染 + 完整轨迹）/ `FAILED`（errorMsg + 重试）
5. ☑ 越权：localStorage 改 `token` 为伪造值 → 任意接口 → 401 → 拦截器清 token + 跳 `/login`
6. ☑ 雪花 ID：报告 id（19 位）在历史列表 / 详情路径 `/reports/<19位id>` 正确，无精度丢失（控制台无 `1.9e18` 之类）
7. ☑ 历史报告列表分页 + 「查看」跳详情正常
8. ☑ 注册：用户名重复 → `ElMessage.error('用户名已存在')`（409）；成功即登录跳 `/input`

- [ ] **Step 4: （可选，需用户明确授权）打里程碑 tag**

> ⚠️ 依项目规范「不自动提交」，打 tag 属 git 写操作，须用户明确授权后执行。

授权后执行：
```bash
git branch --show-current   # feature/m4-frontend
git tag m4-complete
```

- [ ] **Step 5: 收尾提交（如有联调微调）**

若联调发现并修复了小问题，按既有规范 task 级提交：
```bash
git branch --show-current
git add -A
git commit -m "fix(m4): 联调验收修复（描述具体问题）"
```

---

## Self-Review

### 1. Spec coverage（spec 各节 → 实现任务对照）

| spec 章节 | 内容 | 实现任务 |
|---|---|---|
| §1.1 | 登录 + 注册（前端 + 后端注册端点） | Task 3（后端 register）+ Task 4（前端鉴权） |
| §1.1 | 数据录入页（Activity/Note/Task 三 tab CRUD + Task complete） | Task 6 |
| §1.1 | 报告生成与查看页（异步轮询 + 三态 + 双栏） | Task 7 |
| §1.1 | Agent 协作时间线可视化 | Task 8 |
| §1.1 | 历史报告列表页 | Task 9 |
| §1.1 | 侧边栏导航布局 | Task 5 |
| §2 | 技术基线（版本锁定） | Task 1（package.json） |
| §3 | 目录结构 | File Structure 章节 |
| §4 | 路由与鉴权（路由表 + 守卫 + JWT 拦截） | Task 4（守卫+拦截）+ Task 5（路由重构） |
| §5 | 状态管理（authStore / reportStore） | Task 4（authStore）+ Task 7（reportStore） |
| §6.1–6.6 | AppLayout / InputView / ReportView / AgentTimeline / HistoryView / Login/Register | Task 5 / 6 / 7 / 8 / 9 / 4 |
| §7 | 报告生成异步闭环（轮询 2.5s + 三态停止 + onUnmounted 清理） | Task 7（useReportPolling） |
| §8 | 雪花 ID 精度（json-bigint transformResponse） | Task 2 |
| §9 | 后端注册端点（RegisterDTO + Service + Controller + WebConfig） | Task 3 |
| §10 | 错误处理与三态覆盖（拦截器按 code 分流 + 报告三态） | Task 2（拦截器）+ Task 7（三态） |
| §11 | 测试策略（Vitest 各层 + 后端 TDD） | 各 task 测试 step |
| §12 | 验收标准 8 项 | Task 10 |
| §13 | 任务预览 T1–T9 | Task 1–9（+ Task 10 收尾） |

**对 spec 的三处合理偏离（均已在对应 task 注明理由）**：
- **§5.2 reportStore**：`currentReport/traces` 由 spec 的"放 store"改为放 `useReportPolling` composable 局部 —— 报告详情是页面内状态、无跨页共享需求（YAGNI）；store 仅保留 `triggerGenerate`（Task 7）。
- **§6.1 侧边栏三项**：报告详情 `/reports/:id` 需 id 无法作固定导航项，故合并为两项（数据录入 + 报告中心），生成入口并入报告中心 HistoryView 顶部（Task 5）。
- **§9.3 注册 status 字段**：spec 提"status 默认正常"，但 `UserEntity` 实际无 status 字段，故注册时 `nickname=null`、不设 status（Task 3，与现状代码对齐）。

### 2. Placeholder scan

无反模式占位（TBD / TODO / "fill in details" / "add error handling" / "similar to Task N"）。每个 step 含完整代码或精确命令 + 预期输出。

**关于"占位组件"**：Task 4 的 `InputView`、Task 5 的 `HistoryView`/`ReportView`、Task 7 的 `AgentTimeline` 在创建时是简化占位、后续 task 覆盖为真实实现 —— 这是保证"每个 task 独立可构建/可跑测试"的渐进中间产物（路径 import 链不断裂），并非"未实现细节"占位。每处占位都明确标注「Task X 覆盖」。

### 3. Type consistency

- **types 字段**：所有 id 类字段为 `string`（雪花 ID 经 json-bigint），Task 2 定义 → Task 6/7/8/9 使用一致 ✓
- **api 函数名**：Task 2 定义的 `login`/`register`/`listActivities`/`createActivity`/`updateActivity`/`deleteActivity`/`listNotes`/.../`completeTask`/`generateReport`/`getReport`/`pageReports`/`listTraces`/`deleteReport` → 各 view 使用一致 ✓
- **composable 契约**：`useReportPolling` 返回 `{ report, traces, isRunning, start, stop }` → ReportView 解构使用一致 ✓
- **组件 props**：`AgentTimeline { traces, active }` / `MarkdownView { content }` → ReportView 传入一致 ✓
- **已修正（本轮 self-review inline fix）**：
  - Task 6 `ActivityPanel` / `TaskPanel` 原用独立 `<script lang="ts">` 块 + `export` 暴露选项常量 —— 但 Vue SFC 的 template 只能访问 `<script setup>` 顶层绑定，模块级 `<script>` 块的常量不会自动暴露给 template（真实 bug）。已改为：选项常量与 helper 移入 `<script setup>` 顶层声明。
  - Task 6 `TaskPanel` 原 `tagType: 'info' as never` 取巧写法 —— 已改为 `tagType: 'info' | 'success' | 'warning'` 字面量联合，`statusTagType` 同类型返回，去除 `as never` 与冗余断言。

---

## Execution Handoff

计划已完成并保存至 `docs/superpowers/plans/2026-07-10-dayflow-m4-frontend.md`。两种执行方式：

**1. Subagent-Driven（推荐）** —— 每个 task 派发独立子智能体实现，task 间两阶段审查，快速迭代。

**2. Inline Execution** —— 在当前会话内用 executing-plans 逐 task 批量执行，带检查点审查。

**选择哪种方式？**

- 若选 Subagent-Driven：REQUIRED SUB-SKILL `superpowers:subagent-driven-development`（每 task 新子智能体 + 两阶段审查）。
- 若选 Inline Execution：REQUIRED SUB-SKILL `superpowers:executing-plans`（批量执行 + 检查点）。
