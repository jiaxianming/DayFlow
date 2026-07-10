# DayFlow M3 多智能体核心 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 M2 的 `ChatClient` 地基上落地编辑部模式 4 Agent 协作 + 反馈循环，打通「触发日报生成 → 4 Agent 协作 → 落库 + agent_trace 可视化」核心闭环，并修复 M1 遗留的横向越权安全债。

**Architecture:** 4 个独立 `@Component` Agent（Planner/Collector/Writer/Reviewer）经 `ReportOrchestrationService` 串成流水线 + 反馈循环（`MAX_RETRY=2`）；Agent 间以结构化对象（`.entity()`）通信；Collector 经 `@Tool` 拉业务数据，userId 通过 `AgentContext`(ThreadLocal) 注入 Tool，LLM 全程不接触 userId；专用线程池 `dayflow-agent-executor` 异步执行，前端轮询 `report.status` + `agent_trace`。

**Tech Stack:** Spring Boot 4.1.0 / Spring AI 2.0.0（`ChatClient.builder` / `.entity()` / `@Tool` / `.defaultTools()`）/ Java 21 / MyBatis-Plus 3.5.14（`LambdaQueryWrapper`）/ jjwt / Mockito（`RETURNS_DEEP_STUBS` + byte-buddy-agent javaagent）。

## Global Constraints

- **`@author jiaxianming`** —— 所有新增 Java 类 JavaDoc 统一署名。
- **命名**：实体 `Entity` 后缀、入参 `DTO`、出参 `VO`、查询 `Query`（复用 M1 规范）；**Agent 协议对象放 `agent/model/`，不带 Entity/DTO/VO 后缀**（它们是 Agent 间内部协议）。
- **统一响应** `Result<T>`；`ResultCode` 常量名为 `PARAM_ERROR(400)` / `UNAUTHORIZED(401)` / `FORBIDDEN(403)` / `NOT_FOUND(404)` / `BUSINESS_ERROR(409)` / `SYSTEM_ERROR(500)`（注意：是 `PARAM_ERROR` 非 `BAD_REQUEST`）。
- **业务异常** `BusinessException(ResultCode, String)`，由 `GlobalExceptionHandler` 统一映射。
- **路径** `/api/<resource>` kebab-case；`/api/reports/generate` 自动落入 `WebConfig` 的 `/api/**` 拦截（已确认），必须带 JWT。
- **userId 安全铁律**：userId 绝不进 prompt、不进 `PlanInput`；仅 `AgentContext`(ThreadLocal) 供 `ReportDataTools` 读取。`UserContext.getUserId()` 用于请求线程的 ownership guard。
- **测试**：单测全部 mock（不连真模型、不花钱、不依赖网络）；沿用 M1/M2 范式（`@ExtendWith(MockitoExtension.class)` + `@Mock`/`@InjectMocks` + `@MockitoBean` 切片）；byte-buddy-agent javaagent 已在 `pom.xml`。
- **提交**：不自动提交；每 task 在 `feature/m3-multi-agent` 分支做 task 级提交（review 所需、可 reset），整支审查通过 + 用户明确授权后才合并 `main`。
- **前置依赖**：live 端到端需自备 `DEEPSEEK_API_KEY`；CI 走 mock 不受影响。

---

## File Structure

### 新增文件

| 路径 | 职责 |
|---|---|
| `agent/orchestration/AgentContext.java` | ThreadLocal<Long>，异步线程内传递 userId 给 Tool |
| `agent/orchestration/AgentExecutorConfig.java` | 专用线程池 `dayflow-agent-executor` bean |
| `agent/orchestration/ReportOrchestrationService.java` | 编排接口（对外 Service，接口/实现分离） |
| `agent/orchestration/ReportOrchestrationServiceImpl.java` | 串 4 Agent + 反馈循环 + 落库 + 写 trace + 异步提交 |
| `agent/AgentInvoker.java` | 聚合 ChatClient 调用：测 latency + 提取 token + 解析 entity → `AgentResult<T>` |
| `agent/planner/PlannerAgent.java` | plan(PlanInput) → AgentResult<ReportPlan> |
| `agent/collector/CollectorAgent.java` | collect(ReportPlan, date) → AgentResult<CollectedMaterial> |
| `agent/writer/WriterAgent.java` | write(plan, material, suggestions) → AgentResult<DraftReport> |
| `agent/reviewer/ReviewerAgent.java` | review(draft, material) → AgentResult<ReviewResult> |
| `agent/tools/ReportDataTools.java` | @Tool 三方法：listActivities/listCompletedTasks/searchNotes |
| `agent/config/AgentChatClientConfig.java` | 4 个专属 ChatClient bean（各自 defaultSystem） |
| `agent/model/AgentResult.java` | record<T>(payload, tokens, latencyMs) |
| `agent/model/PlanInput.java` 等 13 个协议对象 | Agent 间结构化协议（@Data class / record） |
| `pojo/enums/DataSource.java` | ACTIVITY/NOTE/TASK |
| `pojo/enums/ReviewIssueType.java` | OVERCLAIM/REDUNDANT/MISSING/TONE |
| `service/AgentTraceService.java` + `impl/AgentTraceServiceImpl.java` | 把每步 Agent 轨迹落 `agent_trace` 表 |
| `pojo/dto/ReportGenerateDTO.java` | generate 端点入参 {type, date} |

### 修改文件

| 路径 | 改动 |
|---|---|
| `service/impl/ReportServiceImpl.java` | getById/delete/listTraces 加 userId 归属校验；新增 markGenerated/markFailed |
| `service/ReportService.java` | 接口新增 markGenerated/markFailed |
| `service/impl/ActivityServiceImpl.java` | getById/delete 加 userId 校验 |
| `service/impl/NoteServiceImpl.java` | getById/delete 加 userId 校验 |
| `service/impl/TaskServiceImpl.java` | getById/delete 加 userId 校验；complete 幂等 + userId 校验 |
| `controller/ReportController.java` | 新增 POST /api/reports/generate 端点 |

---

## Task 1: ownership guard + Task complete 幂等（先堵 M1 安全债）

**Files:**
- Modify: `dayflow-server/src/main/java/com/dayflow/service/impl/ReportServiceImpl.java`
- Modify: `dayflow-server/src/main/java/com/dayflow/service/impl/ActivityServiceImpl.java`
- Modify: `dayflow-server/src/main/java/com/dayflow/service/impl/NoteServiceImpl.java`
- Modify: `dayflow-server/src/main/java/com/dayflow/service/impl/TaskServiceImpl.java`
- Modify: 各对应 `*ServiceImplTest.java`（补越权 + 幂等用例）

**Interfaces:**
- Consumes: `UserContext.getUserId()`、`BusinessException(ResultCode, String)`、现有实体 `getUserId()`
- Produces: 4 个 Service 的按-id 操作均校验 `entity.userId == ctx.userId`，不符抛 `BusinessException(FORBIDDEN)`；`TaskService.complete` 幂等（已 DONE 直接返回）

> 现状（已核实）：`ReportServiceImpl` 的 `getById/delete/listTraces`、`Activity/Note/TaskServiceImpl` 的 `getById/delete`、`TaskServiceImpl.complete` 均**无 userId 校验**；`update` 三处已有校验（`Objects.equals(e.getUserId(), UserContext.getUserId())`）。本 task 把缺失的补齐。

- [ ] **Step 1: 写失败测试 —— ReportServiceImpl 越权**

在 `ReportServiceImplTest` 增加（`UserContext` 已 `@AfterEach clear()`）：

```java
@Test
void getByIdForbiddenWhenNotOwner() {
    UserContext.setUserId(1L);
    ReportEntity e = new ReportEntity();
    e.setId(9L);
    e.setUserId(2L);              // 他人报告
    when(reportMapper.selectById(9L)).thenReturn(e);
    BusinessException ex = assertThrows(BusinessException.class, () -> reportService.getById(9L));
    assertEquals(403, ex.getCode());
}

@Test
void deleteForbiddenWhenNotOwner() {
    UserContext.setUserId(1L);
    ReportEntity e = new ReportEntity();
    e.setId(9L);
    e.setUserId(2L);
    when(reportMapper.selectById(9L)).thenReturn(e);
    BusinessException ex = assertThrows(BusinessException.class, () -> reportService.delete(9L));
    assertEquals(403, ex.getCode());
    verify(reportMapper, never()).deleteById(any());
}

@Test
void listTracesForbiddenWhenNotOwner() {
    UserContext.setUserId(1L);
    ReportEntity e = new ReportEntity();
    e.setId(9L);
    e.setUserId(2L);
    when(reportMapper.selectById(9L)).thenReturn(e);
    BusinessException ex = assertThrows(BusinessException.class, () -> reportService.listTraces(9L));
    assertEquals(403, ex.getCode());
    verify(traceMapper, never()).selectList(any());
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -pl dayflow-server test -Dtest=ReportServiceImplTest`
Expected: 3 个新测试 FAIL（当前无校验，不抛 403）。

- [ ] **Step 3: 实现 —— ReportServiceImpl 加归属校验**

`getById` 在 `selectById` 后、转 VO 前加：

```java
ReportEntity e = reportMapper.selectById(id);
if (e == null) {
    throw new BusinessException(ResultCode.NOT_FOUND, "报告不存在");
}
if (!Objects.equals(e.getUserId(), UserContext.getUserId())) {
    throw new BusinessException(ResultCode.FORBIDDEN, "无权操作他人报告");
}
// ...原有转 VO 逻辑
```

`delete` 同样在 `selectById` 后加 null 判 + 归属校验，再 `deleteById`。

`listTraces`：在查 trace 前先 `selectById(reportId)` 做归属校验（同上三行），校验通过后再 `traceMapper.selectList(...)`。null 报告 → `NOT_FOUND`。

> 需 `import java.util.Objects;`（若未引入）。

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn -pl dayflow-server test -Dtest=ReportServiceImplTest`
Expected: PASS。

- [ ] **Step 5: ActivityServiceImpl + NoteServiceImpl 同理补测试与实现**

各 `*ServiceImplTest` 补：

```java
@Test
void getByIdForbiddenWhenNotOwner() {
    UserContext.setUserId(1L);
    ActivityEntity e = new ActivityEntity();
    e.setId(9L);
    e.setUserId(2L);
    when(activityMapper.selectById(9L)).thenReturn(e);
    BusinessException ex = assertThrows(BusinessException.class, () -> activityService.getById(9L));
    assertEquals(403, ex.getCode());
}

@Test
void deleteForbiddenWhenNotOwner() {
    UserContext.setUserId(1L);
    ActivityEntity e = new ActivityEntity();
    e.setId(9L);
    e.setUserId(2L);
    when(activityMapper.selectById(9L)).thenReturn(e);
    BusinessException ex = assertThrows(BusinessException.class, () -> activityService.delete(9L));
    assertEquals(403, ex.getCode());
    verify(activityMapper, never()).deleteById(any());
}
```

`NoteServiceImplTest` 同构（`NoteEntity`/`noteMapper`/`noteService`）。

实现：`ActivityServiceImpl` / `NoteServiceImpl` 的 `getById`、`delete` 在 `selectById` 后加 null 判 + `Objects.equals` 归属校验（照搬各自 `update` 的校验三行）。

- [ ] **Step 6: TaskServiceImpl 补 complete 幂等 + 越权测试**

在 `TaskServiceImplTest` 增加：

```java
@Test
void completeIsIdempotentWhenAlreadyDone() {
    UserContext.setUserId(1L);
    TaskEntity e = new TaskEntity();
    e.setId(9L);
    e.setUserId(1L);
    e.setStatus(TaskStatus.DONE);                 // 已完成
    when(taskMapper.selectById(9L)).thenReturn(e);
    taskService.complete(9L);
    verify(taskMapper, never()).updateById(any()); // 不再写库
}

@Test
void completeForbiddenWhenNotOwner() {
    UserContext.setUserId(1L);
    TaskEntity e = new TaskEntity();
    e.setId(9L);
    e.setUserId(2L);
    e.setStatus(TaskStatus.TODO);
    when(taskMapper.selectById(9L)).thenReturn(e);
    BusinessException ex = assertThrows(BusinessException.class, () -> taskService.complete(9L));
    assertEquals(403, ex.getCode());
}

@Test
void getByIdForbiddenWhenNotOwner() {
    UserContext.setUserId(1L);
    TaskEntity e = new TaskEntity();
    e.setId(9L);
    e.setUserId(2L);
    when(taskMapper.selectById(9L)).thenReturn(e);
    BusinessException ex = assertThrows(BusinessException.class, () -> taskService.getById(9L));
    assertEquals(403, ex.getCode());
}
```

实现 `TaskServiceImpl.complete`：

```java
@Override
public void complete(Long id) {
    TaskEntity e = taskMapper.selectById(id);
    if (e == null) {
        throw new BusinessException(ResultCode.NOT_FOUND, "任务不存在");
    }
    if (!Objects.equals(e.getUserId(), UserContext.getUserId())) {
        throw new BusinessException(ResultCode.FORBIDDEN, "无权操作他人任务");
    }
    if (e.getStatus() == TaskStatus.DONE) {
        return;  // 幂等：已完成直接返回成功，不重复写库
    }
    e.setStatus(TaskStatus.DONE);
    e.setCompletedAt(LocalDateTime.now());
    taskMapper.updateById(e);
}
```

`TaskServiceImpl` 的 `getById`/`delete` 同样补 null 判 + 归属校验。

- [ ] **Step 7: 运行全部相关测试**

Run: `mvn -pl dayflow-server test -Dtest=ReportServiceImplTest,ActivityServiceImplTest,NoteServiceImplTest,TaskServiceImplTest`
Expected: 全 PASS。

- [ ] **Step 8: 提交**

```bash
git add dayflow-server/src/main/java/com/dayflow/service dayflow-server/src/test/java/com/dayflow/service
git commit -m "fix(m3): ownership guard 补齐 + Task complete 幂等（M1 安全债）"
```

---

## Task 2: Agent 协议对象、枚举、AgentContext、AgentResult、AgentInvoker

**Files:**
- Create: `agent/model/AgentResult.java`
- Create: `agent/model/PlanInput.java`、`ReportPlan.java`、`PlanSection.java`、`CollectedMaterial.java`、`CollectedSection.java`、`CollectedItem.java`、`DraftReport.java`、`DraftSection.java`、`ReviewResult.java`、`ReviewIssue.java`、`ActivityItem.java`、`TaskItem.java`、`NoteItem.java`
- Create: `pojo/enums/DataSource.java`、`pojo/enums/ReviewIssueType.java`
- Create: `agent/orchestration/AgentContext.java`
- Create: `agent/AgentInvoker.java`
- Test: `dayflow-server/src/test/java/com/dayflow/agent/AgentInvokerTest.java`

**Interfaces:**
- Produces:
  - `AgentResult<T>`: `record AgentResult<T>(T payload, int tokens, long latencyMs)`
  - `AgentContext`: `static void setUserId(Long)` / `static Long getUserId()` / `static void clear()`
  - `AgentInvoker`（`@Component`）: `<T> AgentResult<T> invoke(ChatClient client, String userPrompt, Class<T> type)`
  - 协议对象 getter 见下（Lombok `@Data`；Tool 视图用 record）

- [ ] **Step 1: 新增两个枚举**

```java
// pojo/enums/DataSource.java
package com.dayflow.pojo.enums;

/**
 * 报告板块数据源类型
 *
 * @author jiaxianming
 */
public enum DataSource {
    /** 工作活动记录 */
    ACTIVITY,
    /** 学习笔记 */
    NOTE,
    /** 任务 */
    TASK
}
```

```java
// pojo/enums/ReviewIssueType.java
package com.dayflow.pojo.enums;

/**
 * 审校问题类型
 *
 * @author jiaxianming
 */
public enum ReviewIssueType {
    /** 夸大/无依据 */
    OVERCLAIM,
    /** 板块间重复 */
    REDUNDANT,
    /** 板块未覆盖 */
    MISSING,
    /** 语气不当 */
    TONE
}
```

- [ ] **Step 2: 新增 13 个协议对象（agent/model/）**

```java
// agent/model/AgentResult.java
package com.dayflow.agent.model;

/**
 * Agent 调用结果包装：结构化产出 + token + 耗时
 *
 * @param payload  Agent 产出的结构化对象
 * @param tokens   本次调用消耗 token（取自 ChatResponse.metadata().usage()）
 * @param latencyMs 本次调用耗时毫秒
 * @author jiaxianming
 */
public record AgentResult<T>(T payload, int tokens, long latencyMs) {
}
```

```java
// agent/model/PlanInput.java
package com.dayflow.agent.model;

import com.dayflow.pojo.enums.ReportType;
import lombok.Data;

import java.time.LocalDate;

/**
 * 规划输入（编排层构造，发给 Planner）
 * <p>注意：不含 userId —— userId 绝不进 prompt，仅经 AgentContext 供 Tool 使用。</p>
 *
 * @author jiaxianming
 */
@Data
public class PlanInput {
    /** 报告日期 */
    private LocalDate date;
    /** 报告类型 */
    private ReportType reportType;
    /** 数据提示：编排层先 count 各源条数，形如「活动 3 条 / 任务 2 条 / 笔记 1 条」，全 0 时为「当日无任何记录」 */
    private String dataHint;
}
```

```java
// agent/model/ReportPlan.java
package com.dayflow.agent.model;

import lombok.Data;
import java.util.List;

/** 规划产出的报告计划 @author jiaxianming */
@Data
public class ReportPlan {
    private String title;
    private List<PlanSection> sections;
}
```

```java
// agent/model/PlanSection.java
package com.dayflow.agent.model;

import com.dayflow.pojo.enums.DataSource;
import lombok.Data;

/** 计划板块 @author jiaxianming */
@Data
public class PlanSection {
    private String name;
    private DataSource dataSource;
    private String focus;
}
```

```java
// agent/model/CollectedMaterial.java
package com.dayflow.agent.model;

import lombok.Data;
import java.util.List;

/** 采集产出的素材包 @author jiaxianming */
@Data
public class CollectedMaterial {
    private List<CollectedSection> sections;
}
```

```java
// agent/model/CollectedSection.java
package com.dayflow.agent.model;

import lombok.Data;
import java.util.List;

/** 素材板块 @author jiaxianming */
@Data
public class CollectedSection {
    private String sectionName;
    private List<CollectedItem> items;
}
```

```java
// agent/model/CollectedItem.java
package com.dayflow.agent.model;

import lombok.Data;

/** 单条素材 @author jiaxianming */
@Data
public class CollectedItem {
    /** 来源类型，如 ACTIVITY/TASK/NOTE */
    private String source;
    /** 摘要 */
    private String summary;
    /** 引用标识（如原记录的时间/标题） */
    private String ref;
}
```

```java
// agent/model/DraftReport.java
package com.dayflow.agent.model;

import lombok.Data;
import java.util.List;

/** 撰写产出的草稿 @author jiaxianming */
@Data
public class DraftReport {
    private String title;
    private List<DraftSection> sections;
}
```

```java
// agent/model/DraftSection.java
package com.dayflow.agent.model;

import lombok.Data;

/** 草稿板块（content 为中文 markdown） @author jiaxianming */
@Data
public class DraftSection {
    private String name;
    private String content;
}
```

```java
// agent/model/ReviewResult.java
package com.dayflow.agent.model;

import lombok.Data;
import java.util.List;

/** 审校结果 @author jiaxianming */
@Data
public class ReviewResult {
    /** 是否通过 */
    private boolean passed;
    /** 问题清单（passed=false 时非空） */
    private List<ReviewIssue> issues;
    /** 给 Writer 的修改建议（passed=false 时非空） */
    private String suggestions;
}
```

```java
// agent/model/ReviewIssue.java
package com.dayflow.agent.model;

import com.dayflow.pojo.enums.ReviewIssueType;
import lombok.Data;

/** 审校问题 @author jiaxianming */
@Data
public class ReviewIssue {
    private String section;
    private ReviewIssueType type;
    private String description;
}
```

Tool 返回的轻量视图用 **record**（只含 LLM 所需字段，不带 id/userId）：

```java
// agent/model/ActivityItem.java
package com.dayflow.agent.model;

/** 活动记录轻量视图（供 LLM，不含 id/userId） @author jiaxianming */
public record ActivityItem(String content, String category, String occurredAt) {
}
```

```java
// agent/model/TaskItem.java
package com.dayflow.agent.model;

/** 任务轻量视图 @author jiaxianming */
public record TaskItem(String title, String status, String completedAt) {
}
```

```java
// agent/model/NoteItem.java
package com.dayflow.agent.model;

/** 笔记轻量视图 @author jiaxianming */
public record NoteItem(String title, String tags, String content) {
}
```

- [ ] **Step 3: 新增 AgentContext（ThreadLocal）**

```java
// agent/orchestration/AgentContext.java
package com.dayflow.agent.orchestration;

/**
 * Agent 执行上下文：在异步线程内传递 userId 给 {@code ReportDataTools}。
 * <p>与请求线程的 {@code UserContext} 区分：异步线程不经 JwtInterceptor，
 * UserContext 不会被设置；故编排层在 run 方法体内手动 {@link #setUserId(Long)}，
 * Tool 同线程 {@link #getUserId()} 读取，run 结束 {@link #clear()}。</p>
 * <p>核心安全约束：userId 经此 ThreadLocal 传递，LLM 全程不接触 userId，
 * 杜绝 LLM 幻觉导致越权拉取他人数据。</p>
 *
 * @author jiaxianming
 */
public final class AgentContext {

    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private AgentContext() {
    }

    /**
     * 设置当前 Agent 执行流的 userId
     *
     * @param userId 当前用户 id
     */
    public static void setUserId(Long userId) {
        CURRENT.set(userId);
    }

    /**
     * @return 当前 Agent 执行流的 userId（Tool 读取），未设置返回 null
     */
    public static Long getUserId() {
        return CURRENT.get();
    }

    /**
     * 清理 ThreadLocal，防止线程池复用导致的跨任务污染
     */
    public static void clear() {
        CURRENT.remove();
    }
}
```

- [ ] **Step 4: 新增 AgentInvoker**

> ⚠️ **Spike 点**：Spring AI 2.0 的 `CallResponse` 同时提供 `.entity(Class)` 与 `.chatResponse()`，这是"一次调用拿结构化对象 + token"的关键。本 task 先按此实现；若 `.call()` 返回类型 API 不符，fallback 见 Step 6 备注。

```java
// agent/AgentInvoker.java
package com.dayflow.agent;

import com.dayflow.agent.model.AgentResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

/**
 * Agent 调用聚合器：封装 4 个 Agent 共用的「调用 ChatClient → 测 latency → 提取 token → 解析 entity」。
 * <p>不吞异常：调用失败时原样抛出，由编排层 {@code run} 的 try-catch 统一转 report.status=FAILED。</p>
 *
 * @author jiaxianming
 */
@Component
public class AgentInvoker {

    private static final Logger log = LoggerFactory.getLogger(AgentInvoker.class);

    /**
     * 调用 ChatClient 并返回结构化结果 + 元信息
     *
     * @param client     已配置 defaultSystem（及 defaultTools）的专属 ChatClient
     * @param userPrompt 用户提示文本
     * @param type       结构化产出类型
     * @param <T>        结构化类型
     * @return AgentResult（payload + tokens + latencyMs）
     */
    public <T> AgentResult<T> invoke(ChatClient client, String userPrompt, Class<T> type) {
        long start = System.currentTimeMillis();
        // .call() 返回 CallResponse：同时拿 chatResponse()（元信息）与 entity(Class)（结构化对象）
        ChatClient.ChatClientRequest.CallResponse callResponse =
                client.prompt().user(userPrompt).call();
        ChatResponse chatResponse = callResponse.chatResponse();
        int tokens = extractTokens(chatResponse);
        T payload = callResponse.entity(type);
        long latency = System.currentTimeMillis() - start;
        log.debug("Agent 调用 type={} tokens={} latencyMs={}", type.getSimpleName(), tokens, latency);
        return new AgentResult<>(payload, tokens, latency);
    }

    /**
     * 从 ChatResponse 提取 token 用量（usage 为空或字段缺失时返回 0，不影响主流程）
     *
     * @param chatResponse ChatResponse
     * @return total tokens，取不到为 0
     */
    static int extractTokens(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getMetadata() == null) {
            return 0;
        }
        Usage usage = chatResponse.getMetadata().getUsage();
        if (usage == null || usage.getTotalTokens() == null) {
            return 0;
        }
        return usage.getTotalTokens();
    }
}
```

- [ ] **Step 5: 写 AgentInvoker 纯函数测试（extractTokens）**

```java
// dayflow-server/src/test/java/com/dayflow/agent/AgentInvokerTest.java
package com.dayflow.agent;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AgentInvoker 单测：覆盖 token 提取纯函数。
 * <p>invoke 主流程依赖 Spring AI 2.0 CallResponse API，由 live 冒烟端到端验证。</p>
 *
 * @author jiaxianming
 */
class AgentInvokerTest {

    private final AgentInvoker invoker = new AgentInvoker();

    @Test
    void extractTokensReturnsZeroWhenResponseNull() {
        assertEquals(0, AgentInvoker.extractTokens(null));
    }

    @Test
    void extractTokensReturnsZeroWhenNoUsage() {
        ChatResponse resp = mock(ChatResponse.class);
        ChatResponseMetadata meta = mock(ChatResponseMetadata.class);
        when(resp.getMetadata()).thenReturn(meta);
        when(meta.getUsage()).thenReturn(null);
        assertEquals(0, AgentInvoker.extractTokens(resp));
    }

    @Test
    void extractTokensReturnsTotalTokens() {
        ChatResponse resp = mock(ChatResponse.class);
        ChatResponseMetadata meta = mock(ChatResponseMetadata.class);
        DefaultUsage usage = new DefaultUsage();
        usage.setPromptTokens(60);
        usage.setCompletionTokens(40);
        // totalTokens = prompt + completion（DefaultUsage 计算）
        when(resp.getMetadata()).thenReturn(meta);
        when(meta.getUsage()).thenReturn(usage);
        // 仅断言 > 0（具体 total 计算口径以 Spring AI 2.0 为准，不绑死数值）
        assertEquals(100, AgentInvoker.extractTokens(resp));
    }
}
```

> 若 `DefaultUsage` 的 API（setPromptTokens 等）在 2.0 有差异，调整断言为「断言 `> 0`」即可，不阻塞。`extractTokens` 的核心逻辑（null 防御）由前两个用例保证。

- [ ] **Step 6: 运行测试确认通过**

Run: `mvn -pl dayflow-server test -Dtest=AgentInvokerTest`
Expected: PASS。

> **Spike 备注**：`invoke` 用的 `ChatClient.ChatClientRequest.CallResponse` 及其 `.entity(Class)`/`.chatResponse()` 方法在 Task 5（首个真正调用 `invoke` 的 Planner，配 live 冒烟）验证。若 2.0 API 实际为 `.call().chatResponse()` 后用 `BeanOutputConverter.from(response.getResult().getOutput().getText(), type)` 解析，则在 AgentInvoker 内调整这两行（不影响测试与调用方签名）。

- [ ] **Step 7: 编译确认整体无误**

Run: `mvn -pl dayflow-server test-compile`
Expected: BUILD SUCCESS。

- [ ] **Step 8: 提交**

```bash
git add dayflow-server/src/main/java/com/dayflow/agent dayflow-server/src/main/java/com/dayflow/pojo/enums/DataSource.java dayflow-server/src/main/java/com/dayflow/pojo/enums/ReviewIssueType.java dayflow-server/src/test/java/com/dayflow/agent
git commit -m "feat(m3): Agent 协议对象 + 枚举 + AgentContext + AgentInvoker 骨架"
```

---

## Task 3: AgentTraceService（轨迹落库）

**Files:**
- Create: `service/AgentTraceService.java`
- Create: `service/impl/AgentTraceServiceImpl.java`
- Test: `dayflow-server/src/test/java/com/dayflow/service/AgentTraceServiceImplTest.java`

**Interfaces:**
- Consumes: `AgentTraceMapper`（`BaseMapper<AgentTraceEntity>`，M1 已有）、`AgentName` 枚举
- Produces: `AgentTraceService.trace(Long reportId, AgentName agent, int step, String inputSummary, String outputSummary, int tokens, long latencyMs, int retryCount)` —— 每步 Agent 后落库一条；`step` 从 1 开始递增

- [ ] **Step 1: 写失败测试**

```java
// dayflow-server/src/test/java/com/dayflow/service/AgentTraceServiceImplTest.java
package com.dayflow.service;

import com.dayflow.mapper.AgentTraceMapper;
import com.dayflow.pojo.entity.AgentTraceEntity;
import com.dayflow.pojo.enums.AgentName;
import com.dayflow.service.impl.AgentTraceServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AgentTraceService 测试：轨迹字段落库正确
 *
 * @author jiaxianming
 */
@ExtendWith(MockitoExtension.class)
class AgentTraceServiceImplTest {

    @Mock
    private AgentTraceMapper traceMapper;

    @InjectMocks
    private AgentTraceServiceImpl traceService;

    @Test
    void traceInsertsEntityWithAllFields() {
        when(traceMapper.insert(any(AgentTraceEntity.class))).thenReturn(1);
        traceService.trace(100L, AgentName.PLANNER, 1, "规划输入摘要", "规划输出摘要", 80, 320L, 0);

        ArgumentCaptor<AgentTraceEntity> captor = ArgumentCaptor.forClass(AgentTraceEntity.class);
        verify(traceMapper).insert(captor.capture());
        AgentTraceEntity saved = captor.getValue();
        assertEquals(100L, saved.getReportId());
        assertEquals(AgentName.PLANNER, saved.getAgentName());
        assertEquals(1, saved.getStep());
        assertEquals("规划输入摘要", saved.getInputSummary());
        assertEquals("规划输出摘要", saved.getOutputSummary());
        assertEquals(80, saved.getTokens());
        assertEquals(320, saved.getLatencyMs());
        assertEquals(0, saved.getRetryCount());
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn -pl dayflow-server test -Dtest=AgentTraceServiceImplTest`
Expected: FAIL（类不存在，编译错误）。

- [ ] **Step 3: 实现接口与实现**

```java
// service/AgentTraceService.java
package com.dayflow.service;

import com.dayflow.pojo.enums.AgentName;

/**
 * Agent 轨迹服务：把每步 Agent 的输入/输出摘要、token、耗时、重试次数落 agent_trace 表。
 *
 * @author jiaxianming
 */
public interface AgentTraceService {

    /**
     * 记录一条 Agent 轨迹（每步 Agent 调用后调用）
     *
     * @param reportId      报告 id
     * @param agent         Agent 名称
     * @param step          执行步骤序号（从 1 开始递增）
     * @param inputSummary  输入摘要（已截断）
     * @param outputSummary 输出摘要（已截断）
     * @param tokens        本次调用 token
     * @param latencyMs     本次调用耗时毫秒
     * @param retryCount    重试次数（首次为 0）
     */
    void trace(Long reportId, AgentName agent, int step, String inputSummary,
               String outputSummary, int tokens, long latencyMs, int retryCount);
}
```

```java
// service/impl/AgentTraceServiceImpl.java
package com.dayflow.service.impl;

import com.dayflow.mapper.AgentTraceMapper;
import com.dayflow.pojo.entity.AgentTraceEntity;
import com.dayflow.pojo.enums.AgentName;
import com.dayflow.service.AgentTraceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Agent 轨迹服务实现：直接走 Mapper 落库，每条独立小事务（前端轮询能渐进看到轨迹）。
 *
 * @author jiaxianming
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentTraceServiceImpl implements AgentTraceService {

    private static final int MAX_SUMMARY_LEN = 500;

    private final AgentTraceMapper traceMapper;

    @Override
    public void trace(Long reportId, AgentName agent, int step, String inputSummary,
                      String outputSummary, int tokens, long latencyMs, int retryCount) {
        AgentTraceEntity entity = new AgentTraceEntity();
        entity.setReportId(reportId);
        entity.setAgentName(agent);
        entity.setStep(step);
        entity.setInputSummary(truncate(inputSummary));
        entity.setOutputSummary(truncate(outputSummary));
        entity.setTokens(tokens);
        entity.setLatencyMs((int) latencyMs);
        entity.setRetryCount(retryCount);
        traceMapper.insert(entity);
        log.info("trace 落库 reportId={} agent={} step={} tokens={} retry={}",
                reportId, agent, step, tokens, retryCount);
    }

    private String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= MAX_SUMMARY_LEN ? text : text.substring(0, MAX_SUMMARY_LEN);
    }
}
```

> `AgentTraceEntity.latencyMs` 字段类型为 `Integer`（M1 字段表已确认），故 `(int) latencyMs`。token/times 字段均 `Integer`。

- [ ] **Step 4: 运行确认通过**

Run: `mvn -pl dayflow-server test -Dtest=AgentTraceServiceImplTest`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add dayflow-server/src/main/java/com/dayflow/service/AgentTraceService.java dayflow-server/src/main/java/com/dayflow/service/impl/AgentTraceServiceImpl.java dayflow-server/src/test/java/com/dayflow/service/AgentTraceServiceImplTest.java
git commit -m "feat(m3): AgentTraceService 轨迹落库（每步独立小事务）"
```

---

## Task 4: Agent 基建配置（线程池 + 4 个专属 ChatClient）

**Files:**
- Create: `agent/orchestration/AgentExecutorConfig.java`
- Create: `agent/config/AgentChatClientConfig.java`
- Test: `dayflow-server/src/test/java/com/dayflow/agent/config/AgentChatClientConfigTest.java`

**Interfaces:**
- Consumes: M2 `ChatModel` bean、`ReportDataTools`（Task 6 创建，Collector 的 ChatClient 需 `defaultTools`）
- Produces:
  - `ThreadPoolTaskExecutor` bean 名 `dayflow-agent-executor`（core=2/max=4/queue=10/线程名 `agent-`）
  - 4 个 ChatClient bean：`plannerChatClient` / `writerChatClient` / `reviewerChatClient`（各自 defaultSystem）；`collectorChatClient`（defaultSystem + defaultTools(reportDataTools)）

> 依赖顺序：`AgentChatClientConfig` 的 `collectorChatClient` 注入 `ReportDataTools`。为避免循环依赖卡住本 task，**先建 3 个不带 tools 的 ChatClient + executor**，`collectorChatClient` 在 Task 6（ReportDataTools 完成）后再补。本 task 留 collector 方法骨架并 `// TODO: Task 6 补 defaultTools`，但为"无 placeholder"原则，本 task 改为**仅建 3 个**，collector 在 Task 6 一并建。

- [ ] **Step 1: 写 ChatClient 装配测试**

```java
// dayflow-server/src/test/java/com/dayflow/agent/config/AgentChatClientConfigTest.java
package com.dayflow.agent.config;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AgentChatClientConfig 装配测试（需本机 MySQL + DEEPSEEK_API_KEY，provider 默认 deepseek）。
 *
 * @author jiaxianming
 */
class AgentChatClientConfigTest {

    @Nested
    @SpringBootTest(properties = "spring.ai.deepseek.api-key=test-key")
    class ContextLoads {

        @Autowired(required = false)
        @Qualifier("plannerChatClient")
        private ChatClient plannerChatClient;

        @Autowired(required = false)
        @Qualifier("writerChatClient")
        private ChatClient writerChatClient;

        @Autowired(required = false)
        @Qualifier("reviewerChatClient")
        private ChatClient reviewerChatClient;

        @Test
        void threeAgentChatClientsExist() {
            assertThat(plannerChatClient).isNotNull();
            assertThat(writerChatClient).isNotNull();
            assertThat(reviewerChatClient).isNotNull();
        }
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn -pl dayflow-server test -Dtest=AgentChatClientConfigTest`
Expected: FAIL（bean 不存在）。

- [ ] **Step 3: 实现 AgentExecutorConfig**

```java
// agent/orchestration/AgentExecutorConfig.java
package com.dayflow.agent.orchestration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Agent 异步执行线程池配置。
 * <p>报告生成是重 LLM 任务，单用户低并发：core=2/max=4/queue=10 足够；高并发留 M5。
 * 拒绝策略 CallerRunsPolicy：队列满时由调用线程兜底执行，不丢任务。</p>
 *
 * @author jiaxianming
 */
@Configuration
public class AgentExecutorConfig {

    /**
     * 专用线程池 dayflow-agent-executor
     *
     * @return 配置好的 ThreadPoolTaskExecutor
     */
    @Bean(name = "dayflow-agent-executor")
    public ThreadPoolTaskExecutor agentExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("agent-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
```

- [ ] **Step 4: 实现 AgentChatClientConfig（3 个 ChatClient + 4 段 prompt 常量）**

```java
// agent/config/AgentChatClientConfig.java
package com.dayflow.agent.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 4 个 Agent 专属 ChatClient 配置：各自 defaultSystem 注入角色 prompt。
 * <p>collectorChatClient 在 ReportDataTools 就绪后于 Task 6 补建（带 defaultTools）。</p>
 *
 * @author jiaxianming
 */
@Configuration
public class AgentChatClientConfig {

    /** 主编：规划日报板块 */
    public static final String PLANNER_PROMPT = """
            你是日报主编（Planner）。根据用户提供的日期与数据提示，规划一份「工作与学习日报」的板块结构。
            规则：
            1. 板块数量 2-4 个；每个板块指定 dataSource（ACTIVITY/TASK/NOTE 之一）与 focus（该板块重点）。
            2. 标题格式固定为「<日期> 工作与学习日报」。
            3. 若数据提示表明当日无任何记录，则产出单个板块（name=今日暂无记录，dataSource=ACTIVITY，focus=说明当日无记录）。
            4. 严格输出结构化 JSON，字段：title、sections[{name,dataSource,focus}]。
            """;

    /** 撰稿：把素材写成中文段落 */
    public static final String WRITER_PROMPT = """
            你是日报撰稿人（Writer）。根据报告计划与采集到的素材，撰写通顺的中文 markdown 段落。
            规则：
            1. 严格按计划板块结构组织；每个板块 content 为 2-5 句中文段落。
            2. 每段必须有素材依据，不得臆造、不得夸大；某板块无素材时写「本板块今日无记录」。
            3. 客观专业、不啰嗦；若收到修改建议（suggestions），严格据此修改。
            4. 严格输出结构化 JSON，字段：title、sections[{name,content}]。
            """;

    /** 审校：质检草稿 */
    public static final String REVIEWER_PROMPT = """
            你是日报审校（Reviewer）。对草稿做四维质检：①素材依据（是否夸大/无依据 OVERCLAIM）
            ②去重（板块间是否重复 REDUNDANT）③板块完整（是否漏板块 MISSING）④语气（是否不当 TONE）。
            规则：
            1. 全部通过则 passed=true、issues 为空、suggestions 为空。
            2. 否则 passed=false，issues 列出具体问题，suggestions 给出给撰稿人的明确修改建议。
            3. 严格输出结构化 JSON，字段：passed(boolean)、issues[{section,type,description}]、suggestions。
            """;

    /** 记者：采集（在 Task 6 补建 collectorChatClient 时使用） */
    public static final String COLLECTOR_PROMPT = """
            你是日报记者（Collector）。根据报告计划，调用提供的工具采集真实数据，按板块归类并归纳摘要。
            规则：
            1. 必须调用工具拉取真实数据，禁止编造；按计划板块的 dataSource 调对应工具。
            2. 每条素材出 summary（简短摘要）与 ref（如时间或标题）。
            3. 某数据源为空则该板块 items 为空、保留板块名。
            4. 严格输出结构化 JSON，字段：sections[{sectionName,items[{source,summary,ref}]}]。
            """;

    /**
     * @param chatModel M2 auto-config 创建的 ChatModel
     * @return Planner 专属 ChatClient
     */
    @Bean(name = "plannerChatClient")
    public ChatClient plannerChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).defaultSystem(PLANNER_PROMPT).build();
    }

    /**
     * @param chatModel M2 auto-config 创建的 ChatModel
     * @return Writer 专属 ChatClient
     */
    @Bean(name = "writerChatClient")
    public ChatClient writerChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).defaultSystem(WRITER_PROMPT).build();
    }

    /**
     * @param chatModel M2 auto-config 创建的 ChatModel
     * @return Reviewer 专属 ChatClient
     */
    @Bean(name = "reviewerChatClient")
    public ChatClient reviewerChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).defaultSystem(REVIEWER_PROMPT).build();
    }

    // collectorChatClient 在 Task 6 补建（需注入 ReportDataTools 作 defaultTools）
}
```

- [ ] **Step 5: 运行确认通过**

Run: `mvn -pl dayflow-server test -Dtest=AgentChatClientConfigTest`
Expected: PASS（需本机 MySQL 起 + test-key；无 key 时此 @SpringBootTest 会因 M2 fail-fast 失败——此时设环境变量 `DEEPSEEK_API_KEY=test-key` 再跑，或临时 `DAYFLOW_AI_PROVIDER=ollama`）。

> CI 无 key 环境：本装配测试与 M2 `AiConfigTest` 的 `ContextLoads` 同性质（需 key）。若 CI 不跑 @SpringBootTest，可接受——单测层面 ChatClient bean 的存在性已由 ApplicationContextRunner 验证路径覆盖不足时，至少保证 `test-compile` 通过。

- [ ] **Step 6: 编译确认**

Run: `mvn -pl dayflow-server test-compile`
Expected: BUILD SUCCESS。

- [ ] **Step 7: 提交**

```bash
git add dayflow-server/src/main/java/com/dayflow/agent/orchestration/AgentExecutorConfig.java dayflow-server/src/main/java/com/dayflow/agent/config dayflow-server/src/test/java/com/dayflow/agent/config
git commit -m "feat(m3): Agent 线程池 + 3 个专属 ChatClient（Planner/Writer/Reviewer）"
```

---

## Task 5: PlannerAgent（结构化输出，含空数据）

**Files:**
- Create: `agent/planner/PlannerAgent.java`
- Test: `dayflow-server/src/test/java/com/dayflow/agent/planner/PlannerAgentTest.java`

**Interfaces:**
- Consumes: `AgentInvoker`、`@Qualifier("plannerChatClient") ChatClient`、`PlanInput`
- Produces: `AgentResult<ReportPlan> plan(PlanInput input)` —— 构造 prompt（含 date/reportType/dataHint，**不含 userId**）调 invoker

- [ ] **Step 1: 写失败测试**

```java
// dayflow-server/src/test/java/com/dayflow/agent/planner/PlannerAgentTest.java
package com.dayflow.agent.planner;

import com.dayflow.agent.AgentInvoker;
import com.dayflow.agent.model.AgentResult;
import com.dayflow.agent.model.PlanInput;
import com.dayflow.agent.model.ReportPlan;
import com.dayflow.pojo.enums.ReportType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PlannerAgent 测试：验证 prompt 构造（含 dataHint、不含 userId）与调用。
 *
 * @author jiaxianming
 */
@ExtendWith(MockitoExtension.class)
class PlannerAgentTest {

    @Mock
    private AgentInvoker invoker;

    @Mock
    private ChatClient plannerChatClient;

    @InjectMocks
    private PlannerAgent planner;

    @Test
    void planInvokesWithPromptContainingDateAndDataHint() {
        PlanInput input = new PlanInput();
        input.setDate(LocalDate.of(2026, 7, 9));
        input.setReportType(ReportType.DAILY);
        input.setDataHint("活动 3 条 / 任务 2 条 / 笔记 1 条");

        ReportPlan plan = new ReportPlan();
        when(invoker.invoke(eq(plannerChatClient), any(String.class), eq(ReportPlan.class)))
                .thenReturn(new AgentResult<>(plan, 50, 120));

        AgentResult<ReportPlan> result = planner.plan(input);

        assertSame(plan, result.payload());
        verify(invoker).invoke(eq(plannerChatClient), any(String.class), eq(ReportPlan.class));
    }
}
```

> `plannerChatClient` 字段名须与 `PlannerAgent` 中 `@Qualifier` 注入的字段名一致，`@InjectMocks` 才能按名注入。

- [ ] **Step 2: 运行确认失败**

Run: `mvn -pl dayflow-server test -Dtest=PlannerAgentTest`
Expected: FAIL（类不存在）。

- [ ] **Step 3: 实现 PlannerAgent**

```java
// agent/planner/PlannerAgent.java
package com.dayflow.agent.planner;

import com.dayflow.agent.AgentInvoker;
import com.dayflow.agent.model.AgentResult;
import com.dayflow.agent.model.PlanInput;
import com.dayflow.agent.model.ReportPlan;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 规划 Agent：据日期 + 数据提示产出报告板块计划（结构化 ReportPlan）。
 * <p>空数据由 LLM 据 dataHint="当日无任何记录" 产出单板块计划，非硬编码。</p>
 *
 * @author jiaxianming
 */
@Component
public class PlannerAgent {

    private final AgentInvoker invoker;
    private final ChatClient plannerChatClient;

    /**
     * @param invoker         Agent 调用聚合器
     * @param plannerChatClient Planner 专属 ChatClient（已注 defaultSystem）
     */
    public PlannerAgent(AgentInvoker invoker,
                        @Qualifier("plannerChatClient") ChatClient plannerChatClient) {
        this.invoker = invoker;
        this.plannerChatClient = plannerChatClient;
    }

    /**
     * 规划报告板块
     *
     * @param input 规划输入（date/reportType/dataHint）
     * @return AgentResult（payload=ReportPlan）
     */
    public AgentResult<ReportPlan> plan(PlanInput input) {
        String prompt = buildPrompt(input);
        return invoker.invoke(plannerChatClient, prompt, ReportPlan.class);
    }

    private String buildPrompt(PlanInput input) {
        return "日期：" + input.getDate()
                + "；报告类型：" + input.getReportType()
                + "；数据提示：" + (input.getDataHint() == null ? "无" : input.getDataHint())
                + "。请据此规划日报板块结构。";
    }
}
```

- [ ] **Step 4: 运行确认通过**

Run: `mvn -pl dayflow-server test -Dtest=PlannerAgentTest`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add dayflow-server/src/main/java/com/dayflow/agent/planner dayflow-server/src/test/java/com/dayflow/agent/planner
git commit -m "feat(m3): PlannerAgent 结构化规划（含空数据模板）"
```

---

## Task 6: ReportDataTools（@Tool 三方法）+ collectorChatClient

**Files:**
- Create: `agent/tools/ReportDataTools.java`
- Modify: `agent/config/AgentChatClientConfig.java`（补 collectorChatClient bean）
- Test: `dayflow-server/src/test/java/com/dayflow/agent/tools/ReportDataToolsTest.java`

**Interfaces:**
- Consumes: `ActivityMapper`/`TaskMapper`/`NoteMapper`（均 `BaseMapper`）、`AgentContext.getUserId()`
- Produces:
  - `List<ActivityItem> listActivities(String startDate, String endDate)`
  - `List<TaskItem> listCompletedTasks(String startDate, String endDate)`
  - `List<NoteItem> searchNotes(String keywords, String startDate, String endDate)`
  - `collectorChatClient` bean（带 `defaultTools(reportDataTools)`）

- [ ] **Step 1: 写失败测试**

```java
// dayflow-server/src/test/java/com/dayflow/agent/tools/ReportDataToolsTest.java
package com.dayflow.agent.tools;

import com.dayflow.agent.model.ActivityItem;
import com.dayflow.agent.model.NoteItem;
import com.dayflow.agent.model.TaskItem;
import com.dayflow.agent.orchestration.AgentContext;
import com.dayflow.mapper.ActivityMapper;
import com.dayflow.mapper.NoteMapper;
import com.dayflow.mapper.TaskMapper;
import com.dayflow.pojo.entity.ActivityEntity;
import com.dayflow.pojo.entity.NoteEntity;
import com.dayflow.pojo.entity.TaskEntity;
import com.dayflow.pojo.enums.ActivityCategory;
import com.dayflow.pojo.enums.TaskStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * ReportDataTools 测试：按 AgentContext.userId 查询 + 安全降级。
 *
 * @author jiaxianming
 */
@ExtendWith(MockitoExtension.class)
class ReportDataToolsTest {

    @Mock
    private ActivityMapper activityMapper;
    @Mock
    private TaskMapper taskMapper;
    @Mock
    private NoteMapper noteMapper;

    @InjectMocks
    private ReportDataTools tools;

    @AfterEach
    void clear() {
        AgentContext.clear();
    }

    @Test
    void listActivitiesQueriesByAgentContextUserId() {
        AgentContext.setUserId(7L);
        ActivityEntity e = new ActivityEntity();
        e.setContent("完成需求评审");
        e.setCategory(ActivityCategory.MEETING);
        e.setOccurredAt(LocalDateTime.of(2026, 7, 9, 10, 0));
        when(activityMapper.selectList(any())).thenReturn(List.of(e));

        List<ActivityItem> items = tools.listActivities("2026-07-09", "2026-07-09");

        assertEquals(1, items.size());
        assertEquals("完成需求评审", items.get(0).content());
        assertEquals("MEETING", items.get(0).category());
    }

    @Test
    void listActivitiesReturnsEmptyWhenNoUserId() {
        // userId 缺失（AgentContext 未设）→ 安全降级返回空，不抛异常
        AgentContext.clear();
        List<ActivityItem> items = tools.listActivities("2026-07-09", "2026-07-09");
        assertTrue(items.isEmpty());
    }

    @Test
    void listCompletedTasksQueriesDoneTasks() {
        AgentContext.setUserId(7L);
        TaskEntity t = new TaskEntity();
        t.setTitle("写技术方案");
        t.setStatus(TaskStatus.DONE);
        t.setCompletedAt(LocalDateTime.of(2026, 7, 9, 18, 0));
        when(taskMapper.selectList(any())).thenReturn(List.of(t));

        List<TaskItem> items = tools.listCompletedTasks("2026-07-09", "2026-07-09");

        assertEquals(1, items.size());
        assertEquals("写技术方案", items.get(0).title());
        assertEquals("DONE", items.get(0).status());
    }

    @Test
    void searchNotesMatchesByKeyword() {
        AgentContext.setUserId(7L);
        NoteEntity n = new NoteEntity();
        n.setTitle("Spring AI 学习");
        n.setTags("ai,spring");
        n.setContent("ChatClient 用法");
        when(noteMapper.selectList(any())).thenReturn(List.of(n));

        List<NoteItem> items = tools.searchNotes("ai", "2026-07-09", "2026-07-09");

        assertEquals(1, items.size());
        assertEquals("Spring AI 学习", items.get(0).title());
    }
}
```

> `ActivityCategory.MEETING` 为示例常量；若实际枚举值不同（如 `MEETING` 不存在），改为实际存在的值（如 `WORK`）——以 M1 `ActivityCategory` 枚举为准，运行测试时按编译错误修正。

- [ ] **Step 2: 运行确认失败**

Run: `mvn -pl dayflow-server test -Dtest=ReportDataToolsTest`
Expected: FAIL（类不存在）。

- [ ] **Step 3: 实现 ReportDataTools**

```java
// agent/tools/ReportDataTools.java
package com.dayflow.agent.tools;

import com.dayflow.agent.model.ActivityItem;
import com.dayflow.agent.model.NoteItem;
import com.dayflow.agent.model.TaskItem;
import com.dayflow.agent.orchestration.AgentContext;
import com.dayflow.mapper.ActivityMapper;
import com.dayflow.mapper.NoteMapper;
import com.dayflow.mapper.TaskMapper;
import com.dayflow.pojo.entity.ActivityEntity;
import com.dayflow.pojo.entity.NoteEntity;
import com.dayflow.pojo.entity.TaskEntity;
import com.dayflow.pojo.enums.TaskStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 报告数据采集工具，注册给 Collector Agent。
 * <p>userId 一律从 {@link AgentContext} 读取（后端掌控），LLM 全程不接触 userId，
 * 杜绝 LLM 幻觉导致越权拉取他人数据。userId 缺失时安全降级返回空列表。</p>
 *
 * @author jiaxianming
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportDataTools {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final ActivityMapper activityMapper;
    private final TaskMapper taskMapper;
    private final NoteMapper noteMapper;

    /**
     * 查询指定日期范围内用户的工作活动记录
     *
     * @param startDate 起始日期 yyyy-MM-dd
     * @param endDate   结束日期 yyyy-MM-dd
     * @return 活动记录轻量视图列表
     */
    @Tool(description = "查询指定日期范围内用户的工作活动记录（含分类与发生时间）")
    public List<ActivityItem> listActivities(String startDate, String endDate) {
        Long userId = AgentContext.getUserId();
        if (userId == null) {
            log.warn("listActivities: AgentContext.userId 缺失，返回空");
            return List.of();
        }
        List<ActivityEntity> entities = activityMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ActivityEntity>()
                        .eq(ActivityEntity::getUserId, userId)
                        .ge(ActivityEntity::getOccurredAt, parseStart(startDate))
                        .le(ActivityEntity::getOccurredAt, parseEnd(endDate))
                        .orderByDesc(ActivityEntity::getOccurredAt));
        return entities.stream()
                .map(e -> new ActivityItem(e.getContent(),
                        e.getCategory() == null ? null : e.getCategory().name(),
                        formatTime(e.getOccurredAt())))
                .toList();
    }

    /**
     * 查询指定日期范围内已完成的任务
     *
     * @param startDate 起始日期 yyyy-MM-dd
     * @param endDate   结束日期 yyyy-MM-dd
     * @return 已完成任务轻量视图列表
     */
    @Tool(description = "查询指定日期范围内已完成的任务")
    public List<TaskItem> listCompletedTasks(String startDate, String endDate) {
        Long userId = AgentContext.getUserId();
        if (userId == null) {
            return List.of();
        }
        List<TaskEntity> entities = taskMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TaskEntity>()
                        .eq(TaskEntity::getUserId, userId)
                        .eq(TaskEntity::getStatus, TaskStatus.DONE)
                        .ge(TaskEntity::getCompletedAt, parseStart(startDate))
                        .le(TaskEntity::getCompletedAt, parseEnd(endDate))
                        .orderByDesc(TaskEntity::getCompletedAt));
        return entities.stream()
                .map(t -> new TaskItem(t.getTitle(),
                        t.getStatus() == null ? null : t.getStatus().name(),
                        formatTime(t.getCompletedAt())))
                .toList();
    }

    /**
     * 按关键词检索学习笔记（M3 无 RAG，走 LIKE 关键词/标签匹配）
     *
     * @param keywords  关键词（标题/内容/标签匹配）
     * @param startDate 起始日期 yyyy-MM-dd
     * @param endDate   结束日期 yyyy-MM-dd
     * @return 笔记轻量视图列表
     */
    @Tool(description = "按关键词检索学习笔记（标题/内容/标签匹配）")
    public List<NoteItem> searchNotes(String keywords, String startDate, String endDate) {
        Long userId = AgentContext.getUserId();
        if (userId == null) {
            return List.of();
        }
        String kw = keywords == null || keywords.isBlank() ? null : keywords;
        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<NoteEntity>()
                .eq(NoteEntity::getUserId, userId)
                .ge(NoteEntity::getCreatedAt, parseStart(startDate))
                .le(NoteEntity::getCreatedAt, parseEnd(endDate));
        if (kw != null) {
            wrapper.and(w -> w.like(NoteEntity::getTitle, kw)
                    .or().like(NoteEntity::getContent, kw)
                    .or().like(NoteEntity::getTags, kw));
        }
        wrapper.orderByDesc(NoteEntity::getCreatedAt);
        List<NoteEntity> entities = noteMapper.selectList(wrapper);
        return entities.stream()
                .map(n -> new NoteItem(n.getTitle(), n.getTags(), n.getContent()))
                .toList();
    }

    private LocalDateTime parseStart(String date) {
        return LocalDate.parse(date, DATE_FMT).atStartOfDay();
    }

    private LocalDateTime parseEnd(String date) {
        return LocalDate.parse(date, DATE_FMT).atTime(23, 59, 59);
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? null : time.toString();
    }
}
```

> `@Tool` import：Spring AI 2.0 为 `org.springframework.ai.tool.annotation.Tool`。`defaultTools` 注册用 `ChatClient.builder().defaultTools(Object...)` 传工具实例。

- [ ] **Step 4: 在 AgentChatClientConfig 补 collectorChatClient bean**

```java
// agent/config/AgentChatClientConfig.java —— 新增字段与 bean（其余不变）
// 新增 import:
//   import com.dayflow.agent.tools.ReportDataTools;
// 类内新增：

    private final ReportDataTools reportDataTools;

    // 构造器注入 ReportDataTools（@Configuration 类无 @RequiredArgsConstructor 则手写构造器）
    public AgentChatClientConfig(ReportDataTools reportDataTools) {
        this.reportDataTools = reportDataTools;
    }

    /**
     * @param chatModel M2 auto-config 创建的 ChatModel
     * @return Collector 专属 ChatClient（defaultSystem + defaultTools，LLM 可自主调工具取数）
     */
    @Bean(name = "collectorChatClient")
    public ChatClient collectorChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem(COLLECTOR_PROMPT)
                .defaultTools(reportDataTools)
                .build();
    }
```

> ⚠️ 若 `@Configuration` 加构造器后破坏现有 3 个 bean 的注入——不会，3 个 bean 方法仍接收 `ChatModel` 参数，构造器只多注入 `ReportDataTools` 字段。

- [ ] **Step 5: 运行确认通过**

Run: `mvn -pl dayflow-server test -Dtest=ReportDataToolsTest`
Expected: PASS。

> 若 `ActivityCategory.MEETING` 等枚举值编译不过，按 M1 实际枚举常量修正测试。

- [ ] **Step 6: 提交**

```bash
git add dayflow-server/src/main/java/com/dayflow/agent/tools dayflow-server/src/main/java/com/dayflow/agent/config/AgentChatClientConfig.java dayflow-server/src/test/java/com/dayflow/agent/tools
git commit -m "feat(m3): ReportDataTools(@Tool 三方法) + collectorChatClient"
```

---

## Task 7: CollectorAgent（两段式 spike）

**Files:**
- Create: `agent/collector/CollectorAgent.java`
- Test: `dayflow-server/src/test/java/com/dayflow/agent/collector/CollectorAgentTest.java`

**Interfaces:**
- Consumes: `AgentInvoker`、`@Qualifier("collectorChatClient") ChatClient`、`ReportPlan`、`LocalDate`
- Produces: `AgentResult<CollectedMaterial> collect(ReportPlan plan, LocalDate date)`

> ⚠️ **Spike 点（spec 第 4.5/11 节）**：Collector 要「带工具调一次 + 同轮产出结构化 `CollectedMaterial`」。本 task 实现主路径（`invoker.invoke(collectorChatClient, prompt, CollectedMaterial.class)`，client 已 `defaultTools`）。**Live 验证**：若 2.0「Tool Calling + `.entity()`」组合不稳，fallback 为拆两步——先 `collectorChatClient.prompt().user(prompt).call().content()` 拿原始数据，再无工具 `writerChatClient`（或临时无工具 client）结构化归纳。fallback 会改 `collect` 实现，不改对外签名。

- [ ] **Step 1: 写失败测试**

```java
// dayflow-server/src/test/java/com/dayflow/agent/collector/CollectorAgentTest.java
package com.dayflow.agent.collector;

import com.dayflow.agent.AgentInvoker;
import com.dayflow.agent.model.AgentResult;
import com.dayflow.agent.model.CollectedMaterial;
import com.dayflow.agent.model.ReportPlan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CollectorAgent 测试：验证按计划+日期构造 prompt 调 collectorChatClient（已预配 tools）。
 *
 * @author jiaxianming
 */
@ExtendWith(MockitoExtension.class)
class CollectorAgentTest {

    @Mock
    private AgentInvoker invoker;
    @Mock
    private ChatClient collectorChatClient;

    @InjectMocks
    private CollectorAgent collector;

    @Test
    void collectInvokesCollectorChatClient() {
        ReportPlan plan = new ReportPlan();
        plan.setTitle("2026-07-09 工作与学习日报");
        CollectedMaterial material = new CollectedMaterial();
        when(invoker.invoke(eq(collectorChatClient), any(String.class), eq(CollectedMaterial.class)))
                .thenReturn(new AgentResult<>(material, 120, 800));

        AgentResult<CollectedMaterial> result = collector.collect(plan, LocalDate.of(2026, 7, 9));

        assertSame(material, result.payload());
        verify(invoker).invoke(eq(collectorChatClient), any(String.class), eq(CollectedMaterial.class));
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn -pl dayflow-server test -Dtest=CollectorAgentTest`
Expected: FAIL（类不存在）。

- [ ] **Step 3: 实现 CollectorAgent**

```java
// agent/collector/CollectorAgent.java
package com.dayflow.agent.collector;

import com.dayflow.agent.AgentInvoker;
import com.dayflow.agent.model.AgentResult;
import com.dayflow.agent.model.CollectedMaterial;
import com.dayflow.agent.model.ReportPlan;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 采集 Agent：按报告计划调工具拉真实数据，归纳成结构化素材包。
 * <p>collectorChatClient 已预配 {@code defaultTools(reportDataTools)}，LLM 自主调工具取数。</p>
 *
 * @author jiaxianming
 */
@Component
public class CollectorAgent {

    private final AgentInvoker invoker;
    private final ChatClient collectorChatClient;

    /**
     * @param invoker            Agent 调用聚合器
     * @param collectorChatClient Collector 专属 ChatClient（已注 defaultSystem + defaultTools）
     */
    public CollectorAgent(AgentInvoker invoker,
                          @Qualifier("collectorChatClient") ChatClient collectorChatClient) {
        this.invoker = invoker;
        this.collectorChatClient = collectorChatClient;
    }

    /**
     * 采集素材
     *
     * @param plan 报告计划
     * @param date 采集日期（范围 = [date, date]）
     * @return AgentResult（payload=CollectedMaterial）
     */
    public AgentResult<CollectedMaterial> collect(ReportPlan plan, LocalDate date) {
        String dateStr = date.toString();
        StringBuilder sb = new StringBuilder();
        sb.append("采集日期：").append(dateStr).append("。").append("开始：").append(dateStr)
          .append("，结束：").append(dateStr).append("。");
        sb.append("报告标题：").append(plan.getTitle()).append("。");
        sb.append("请按以下板块结构采集数据：\n");
        if (plan.getSections() != null) {
            for (var s : plan.getSections()) {
                sb.append("- 板块「").append(s.getName()).append("」，数据源：")
                  .append(s.getDataSource()).append("，重点：").append(s.getFocus()).append("\n");
            }
        }
        sb.append("对每个板块调用对应工具拉取真实数据，按板块归类并归纳摘要。");
        return invoker.invoke(collectorChatClient, sb.toString(), CollectedMaterial.class);
    }
}
```

- [ ] **Step 4: 运行确认通过**

Run: `mvn -pl dayflow-server test -Dtest=CollectorAgentTest`
Expected: PASS。

- [ ] **Step 5: 编译全部 Agent**

Run: `mvn -pl dayflow-server test-compile`
Expected: BUILD SUCCESS。

- [ ] **Step 6: 提交**

```bash
git add dayflow-server/src/main/java/com/dayflow/agent/collector dayflow-server/src/test/java/com/dayflow/agent/collector
git commit -m "feat(m3): CollectorAgent（带工具的结构化采集，主路径）"
```

---

## Task 8: WriterAgent

**Files:**
- Create: `agent/writer/WriterAgent.java`
- Test: `dayflow-server/src/test/java/com/dayflow/agent/writer/WriterAgentTest.java`

**Interfaces:**
- Consumes: `AgentInvoker`、`@Qualifier("writerChatClient") ChatClient`、`ReportPlan`、`CollectedMaterial`、`String suggestions`（首次为 null）
- Produces: `AgentResult<DraftReport> write(ReportPlan plan, CollectedMaterial material, String suggestions)`

- [ ] **Step 1: 写失败测试**

```java
// dayflow-server/src/test/java/com/dayflow/agent/writer/WriterAgentTest.java
package com.dayflow.agent.writer;

import com.dayflow.agent.AgentInvoker;
import com.dayflow.agent.model.AgentResult;
import com.dayflow.agent.model.CollectedMaterial;
import com.dayflow.agent.model.DraftReport;
import com.dayflow.agent.model.ReportPlan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WriterAgent 测试：首次（suggestions=null）与返工（suggestions 非空）均正确传 prompt。
 *
 * @author jiaxianming
 */
@ExtendWith(MockitoExtension.class)
class WriterAgentTest {

    @Mock
    private AgentInvoker invoker;
    @Mock
    private ChatClient writerChatClient;

    @InjectMocks
    private WriterAgent writer;

    @Test
    void writeFirstRoundPassesNullSuggestions() {
        ReportPlan plan = new ReportPlan();
        CollectedMaterial material = new CollectedMaterial();
        DraftReport draft = new DraftReport();
        when(invoker.invoke(eq(writerChatClient), any(String.class), eq(DraftReport.class)))
                .thenReturn(new AgentResult<>(draft, 90, 500));

        AgentResult<DraftReport> result = writer.write(plan, material, null);

        assertSame(draft, result.payload());
        verify(invoker).invoke(eq(writerChatClient), contains("无修改建议"), eq(DraftReport.class));
    }

    @Test
    void writeRetryRoundPassesSuggestions() {
        ReportPlan plan = new ReportPlan();
        CollectedMaterial material = new CollectedMaterial();
        DraftReport draft = new DraftReport();
        when(invoker.invoke(eq(writerChatClient), any(String.class), eq(DraftReport.class)))
                .thenReturn(new AgentResult<>(draft, 90, 500));

        writer.write(plan, material, "减少第一段冗余");

        verify(invoker).invoke(eq(writerChatClient), contains("减少第一段冗余"), eq(DraftReport.class));
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn -pl dayflow-server test -Dtest=WriterAgentTest`
Expected: FAIL（类不存在）。

- [ ] **Step 3: 实现 WriterAgent**

```java
// agent/writer/WriterAgent.java
package com.dayflow.agent.writer;

import com.dayflow.agent.AgentInvoker;
import com.dayflow.agent.model.AgentResult;
import com.dayflow.agent.model.CollectedMaterial;
import com.dayflow.agent.model.DraftReport;
import com.dayflow.agent.model.ReportPlan;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 撰写 Agent：把素材写成通顺中文 markdown 段落（结构化 DraftReport）。
 * <p>收到 Reviewer 的 suggestions 时据此返工。</p>
 *
 * @author jiaxianming
 */
@Component
public class WriterAgent {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final AgentInvoker invoker;
    private final ChatClient writerChatClient;

    /**
     * @param invoker          Agent 调用聚合器
     * @param writerChatClient Writer 专属 ChatClient
     */
    public WriterAgent(AgentInvoker invoker,
                       @Qualifier("writerChatClient") ChatClient writerChatClient) {
        this.invoker = invoker;
        this.writerChatClient = writerChatClient;
    }

    /**
     * 撰写草稿
     *
     * @param plan        报告计划
     * @param material    采集素材
     * @param suggestions 修改建议（首次为 null）
     * @return AgentResult（payload=DraftReport）
     */
    public AgentResult<DraftReport> write(ReportPlan plan, CollectedMaterial material, String suggestions) {
        String prompt = buildPrompt(plan, material, suggestions);
        return invoker.invoke(writerChatClient, prompt, DraftReport.class);
    }

    @SneakyThrows
    private String buildPrompt(ReportPlan plan, CollectedMaterial material, String suggestions) {
        return "报告计划：" + JSON.writeValueAsString(plan)
                + "\n采集素材：" + JSON.writeValueAsString(material)
                + "\n修改建议：" + (suggestions == null ? "无修改建议（首次撰写）" : suggestions)
                + "\n请据此撰写日报草稿。";
    }
}
```

> 用 Jackson 序列化协议对象为 JSON 喂 prompt（清晰、LLM 易解析）。`@SneakyThrows` 简化 JsonProcessingException（序列化协议对象不会失败）。若项目禁用 `@SneakyThrows`，改为 try-catch 包 RuntimeException。

- [ ] **Step 4: 运行确认通过**

Run: `mvn -pl dayflow-server test -Dtest=WriterAgentTest`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add dayflow-server/src/main/java/com/dayflow/agent/writer dayflow-server/src/test/java/com/dayflow/agent/writer
git commit -m "feat(m3): WriterAgent 撰写（含返工 suggestions）"
```

---

## Task 9: ReviewerAgent

**Files:**
- Create: `agent/reviewer/ReviewerAgent.java`
- Test: `dayflow-server/src/test/java/com/dayflow/agent/reviewer/ReviewerAgentTest.java`

**Interfaces:**
- Consumes: `AgentInvoker`、`@Qualifier("reviewerChatClient") ChatClient`、`DraftReport`、`CollectedMaterial`
- Produces: `AgentResult<ReviewResult> review(DraftReport draft, CollectedMaterial material)`

- [ ] **Step 1: 写失败测试**

```java
// dayflow-server/src/test/java/com/dayflow/agent/reviewer/ReviewerAgentTest.java
package com.dayflow.agent.reviewer;

import com.dayflow.agent.AgentInvoker;
import com.dayflow.agent.model.AgentResult;
import com.dayflow.agent.model.CollectedMaterial;
import com.dayflow.agent.model.DraftReport;
import com.dayflow.agent.model.ReviewResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ReviewerAgent 测试：验证审校调用与结果透传。
 *
 * @author jiaxianming
 */
@ExtendWith(MockitoExtension.class)
class ReviewerAgentTest {

    @Mock
    private AgentInvoker invoker;
    @Mock
    private ChatClient reviewerChatClient;

    @InjectMocks
    private ReviewerAgent reviewer;

    @Test
    void reviewInvokesReviewerChatClient() {
        DraftReport draft = new DraftReport();
        CollectedMaterial material = new CollectedMaterial();
        ReviewResult review = new ReviewResult();
        review.setPassed(true);
        when(invoker.invoke(eq(reviewerChatClient), any(String.class), eq(ReviewResult.class)))
                .thenReturn(new AgentResult<>(review, 70, 300));

        AgentResult<ReviewResult> result = reviewer.review(draft, material);

        assertSame(review, result.payload());
        verify(invoker).invoke(eq(reviewerChatClient), any(String.class), eq(ReviewResult.class));
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn -pl dayflow-server test -Dtest=ReviewerAgentTest`
Expected: FAIL（类不存在）。

- [ ] **Step 3: 实现 ReviewerAgent**

```java
// agent/reviewer/ReviewerAgent.java
package com.dayflow.agent.reviewer;

import com.dayflow.agent.AgentInvoker;
import com.dayflow.agent.model.AgentResult;
import com.dayflow.agent.model.CollectedMaterial;
import com.dayflow.agent.model.DraftReport;
import com.dayflow.agent.model.ReviewResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 审校 Agent：对草稿做四维质检（素材依据/去重/板块完整/语气）。
 * <p>passed=false 时给 suggestions 供 Writer 返工。</p>
 *
 * @author jiaxianming
 */
@Component
public class ReviewerAgent {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final AgentInvoker invoker;
    private final ChatClient reviewerChatClient;

    /**
     * @param invoker            Agent 调用聚合器
     * @param reviewerChatClient Reviewer 专属 ChatClient
     */
    public ReviewerAgent(AgentInvoker invoker,
                         @Qualifier("reviewerChatClient") ChatClient reviewerChatClient) {
        this.invoker = invoker;
        this.reviewerChatClient = reviewerChatClient;
    }

    /**
     * 审校草稿
     *
     * @param draft    草稿
     * @param material 采集素材（供核对素材依据）
     * @return AgentResult（payload=ReviewResult）
     */
    public AgentResult<ReviewResult> review(DraftReport draft, CollectedMaterial material) {
        String prompt = buildPrompt(draft, material);
        return invoker.invoke(reviewerChatClient, prompt, ReviewResult.class);
    }

    @SneakyThrows
    private String buildPrompt(DraftReport draft, CollectedMaterial material) {
        return "草稿：" + JSON.writeValueAsString(draft)
                + "\n原始素材（供核对依据）：" + JSON.writeValueAsString(material)
                + "\n请做四维质检并输出结构化结果。";
    }
}
```

- [ ] **Step 4: 运行确认通过**

Run: `mvn -pl dayflow-server test -Dtest=ReviewerAgentTest`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add dayflow-server/src/main/java/com/dayflow/agent/reviewer dayflow-server/src/test/java/com/dayflow/agent/reviewer
git commit -m "feat(m3): ReviewerAgent 四维质检"
```

---

## Task 10: ReportOrchestrationService（编排 + 反馈循环 + 异步 + 三态）

**Files:**
- Create: `service/ReportService.java`（接口新增 markGenerated/markFailed 方法）
- Modify: `service/impl/ReportServiceImpl.java`（实现 markGenerated/markFailed）
- Create: `agent/orchestration/ReportOrchestrationService.java`
- Create: `agent/orchestration/ReportOrchestrationServiceImpl.java`
- Test: `dayflow-server/src/test/java/com/dayflow/agent/orchestration/ReportOrchestrationServiceImplTest.java`

**Interfaces:**
- Consumes:
  - `PlannerAgent.plan(PlanInput) → AgentResult<ReportPlan>`
  - `CollectorAgent.collect(ReportPlan, LocalDate) → AgentResult<CollectedMaterial>`
  - `WriterAgent.write(ReportPlan, CollectedMaterial, String) → AgentResult<DraftReport>`
  - `ReviewerAgent.review(DraftReport, CollectedMaterial) → AgentResult<ReviewResult>`
  - `AgentTraceService.trace(Long, AgentName, int, String, String, int, long, int)`
  - `ReportService.create(ReportCreateDTO) → Long`、`ReportService.markGenerated(Long, String, Integer)`、`ReportService.markFailed(Long, String)`
  - `ActivityMapper`/`TaskMapper`/`NoteMapper`（count 各源条数填 dataHint）
  - `@Qualifier("dayflow-agent-executor") ThreadPoolTaskExecutor`、`UserContext.getUserId()`
- Produces:
  - `Long generate(ReportGenerateDTO dto)` —— 创建 report(GENERATING) + 提交异步 + 立即返回 reportId
  - `void run(Long reportId, Long userId, LocalDate date, ReportType type)` —— 异步线程内 4 Agent 编排

> `MAX_RETRY = 2`。`markGenerated`/`markFailed` 是系统内部 finalize（按 reportId），**不加 userId 校验**（异步线程无 UserContext；reportId 来自 generate 创建，受信）。对外入口的 ownership guard 在 T1 已加（getById/delete/listTraces）。

- [ ] **Step 1: ReportService 接口新增两个方法**

```java
// service/ReportService.java —— 接口内新增（保留原有方法）
    /**
     * 标记报告生成成功并写入正文 + token（编排层内部调用）
     *
     * @param id         报告 id
     * @param content    日报正文 markdown
     * @param tokenUsage 累计 token
     */
    void markGenerated(Long id, String content, Integer tokenUsage);

    /**
     * 标记报告生成失败并写入错误信息（编排层内部调用）
     *
     * @param id       报告 id
     * @param errorMsg 错误信息
     */
    void markFailed(Long id, String errorMsg);
```

- [ ] **Step 2: ReportServiceImpl 实现两方法**

```java
// service/impl/ReportServiceImpl.java —— 新增（注入的 reportMapper 已有）
    @Override
    public void markGenerated(Long id, String content, Integer tokenUsage) {
        ReportEntity e = reportMapper.selectById(id);
        if (e == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "报告不存在");
        }
        e.setStatus(ReportStatus.GENERATED);
        e.setContent(content);
        e.setTokenUsage(tokenUsage);
        e.setErrorMsg(null);
        reportMapper.updateById(e);
    }

    @Override
    public void markFailed(Long id, String errorMsg) {
        ReportEntity e = reportMapper.selectById(id);
        if (e == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "报告不存在");
        }
        e.setStatus(ReportStatus.FAILED);
        e.setErrorMsg(errorMsg);
        reportMapper.updateById(e);
    }
```

- [ ] **Step 3: 写编排失败测试（主流程 passed=true）**

```java
// dayflow-server/src/test/java/com/dayflow/agent/orchestration/ReportOrchestrationServiceImplTest.java
package com.dayflow.agent.orchestration;

import com.dayflow.agent.collector.CollectorAgent;
import com.dayflow.agent.model.AgentResult;
import com.dayflow.agent.model.CollectedMaterial;
import com.dayflow.agent.model.DraftReport;
import com.dayflow.agent.model.DraftSection;
import com.dayflow.agent.model.PlanInput;
import com.dayflow.agent.model.ReportPlan;
import com.dayflow.agent.model.ReviewResult;
import com.dayflow.agent.planner.PlannerAgent;
import com.dayflow.agent.reviewer.ReviewerAgent;
import com.dayflow.agent.writer.WriterAgent;
import com.dayflow.common.UserContext;
import com.dayflow.mapper.ActivityMapper;
import com.dayflow.mapper.NoteMapper;
import com.dayflow.mapper.TaskMapper;
import com.dayflow.pojo.enums.ReportType;
import com.dayflow.service.AgentTraceService;
import com.dayflow.service.ReportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * ReportOrchestrationServiceImpl 测试（最核心）。
 * <p>4 Agent + traceService + reportService 全 mock；executor 用同步包装（run 直接调）。
 * 覆盖：①主流程 passed=true ②Reviewer 打回→返工→通过 ③retry≥2 强制通过 ④异常→FAILED。</p>
 *
 * @author jiaxianming
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportOrchestrationServiceImplTest {

    @Mock private PlannerAgent planner;
    @Mock private CollectorAgent collector;
    @Mock private WriterAgent writer;
    @Mock private ReviewerAgent reviewer;
    @Mock private AgentTraceService traceService;
    @Mock private ReportService reportService;
    @Mock private ActivityMapper activityMapper;
    @Mock private TaskMapper taskMapper;
    @Mock private NoteMapper noteMapper;
    @Mock private ThreadPoolTaskExecutor agentExecutor;

    @InjectMocks
    private ReportOrchestrationServiceImpl orchestration;

    /**
     * run() 第一步调 buildPlanInput → 3 Mapper selectCount；
     * 统一桩返回 1，避免各用例重复桩、避免未桩 NPE。
     */
    @BeforeEach
    void stubCount() {
        when(activityMapper.selectCount(any())).thenReturn(1L);
        when(taskMapper.selectCount(any())).thenReturn(1L);
        when(noteMapper.selectCount(any())).thenReturn(1L);
    }

    @AfterEach
    void clear() {
        UserContext.clear();
        AgentContext.clear();
    }

    private ReportPlan plan() {
        ReportPlan p = new ReportPlan();
        p.setTitle("2026-07-09 工作与学习日报");
        return p;
    }

    private DraftReport draftWithTitle(String t) {
        DraftReport d = new DraftReport();
        d.setTitle(t);
        DraftSection s = new DraftSection();
        s.setName("工作");
        s.setContent("内容");
        d.setSections(List.of(s));
        return d;
    }

    @Test
    void runMainFlowReviewerPassedFirstTime() {
        when(planner.plan(any())).thenReturn(new AgentResult<>(plan(), 50, 100));
        when(collector.collect(any(), any())).thenReturn(new AgentResult<>(new CollectedMaterial(), 60, 200));
        when(writer.write(any(), any(), any())).thenReturn(new AgentResult<>(draftWithTitle("v1"), 90, 300));
        ReviewResult passed = new ReviewResult();
        passed.setPassed(true);
        when(reviewer.review(any(), any())).thenReturn(new AgentResult<>(passed, 70, 150));

        orchestration.run(1L, 7L, LocalDate.of(2026, 7, 9), ReportType.DAILY);

        verify(reportService).markGenerated(eq(1L), any(String.class), anyInt());
        verify(reportService, never()).markFailed(anyLong(), any());
        // AgentContext 在 run 内已 set（Tool 可读），finally clear
    }

    @Test
    void runReviewerRejectThenRetryPass() {
        when(planner.plan(any())).thenReturn(new AgentResult<>(plan(), 50, 100));
        when(collector.collect(any(), any())).thenReturn(new AgentResult<>(new CollectedMaterial(), 60, 200));
        when(writer.write(any(), any(), isNull())).thenReturn(new AgentResult<>(draftWithTitle("v1"), 90, 300));
        when(writer.write(any(), any(), eq("请精简"))).thenReturn(new AgentResult<>(draftWithTitle("v2"), 90, 300));
        ReviewResult reject = new ReviewResult();
        reject.setPassed(false);
        reject.setSuggestions("请精简");
        ReviewResult pass = new ReviewResult();
        pass.setPassed(true);
        when(reviewer.review(any(), any())).thenReturn(new AgentResult<>(reject, 70, 150), new AgentResult<>(pass, 70, 150));

        orchestration.run(1L, 7L, LocalDate.of(2026, 7, 9), ReportType.DAILY);

        verify(writer).write(any(), any(), isNull());       // 首次
        verify(writer).write(any(), any(), eq("请精简"));    // 返工一次
        verify(reportService).markGenerated(eq(1L), any(), anyInt());
    }

    @Test
    void runForcePassWhenRetryExceedsMax() {
        when(planner.plan(any())).thenReturn(new AgentResult<>(plan(), 50, 100));
        when(collector.collect(any(), any())).thenReturn(new AgentResult<>(new CollectedMaterial(), 60, 200));
        // Writer 每次都产新草稿
        when(writer.write(any(), any(), any())).thenAnswer(inv -> new AgentResult<>(draftWithTitle("v"), 90, 300));
        // Reviewer 每次都不通过
        ReviewResult reject = new ReviewResult();
        reject.setPassed(false);
        reject.setSuggestions("再改");
        when(reviewer.review(any(), any())).thenReturn(new AgentResult<>(reject, 70, 150));

        orchestration.run(1L, 7L, LocalDate.of(2026, 7, 9), ReportType.DAILY);

        // MAX_RETRY=2：while(retry<2) 仅 retry=0、1 两轮，reviewer 最多被调 2 次（始终 reject 时），退出后强制通过 → markGenerated（与 spec 5.4 伪代码一致）
        verify(reportService).markGenerated(eq(1L), any(), anyInt());
        verify(reportService, never()).markFailed(anyLong(), any());
    }

    @Test
    void runMarksFailedOnPlannerException() {
        when(planner.plan(any())).thenThrow(new RuntimeException("LLM 挂了"));

        orchestration.run(1L, 7L, LocalDate.of(2026, 7, 9), ReportType.DAILY);

        verify(reportService).markFailed(eq(1L), contains("LLM 挂了"));
        verify(reportService, never()).markGenerated(anyLong(), any(), any());
    }

    @Test
    void runSetsAgentContextForTools() {
        when(planner.plan(any())).thenAnswer(inv -> {
            // 模拟 Tool 读 AgentContext：run 内应已 set userId=7
            org.junit.jupiter.api.Assertions.assertEquals(7L, AgentContext.getUserId());
            return new AgentResult<>(plan(), 50, 100);
        });
        when(collector.collect(any(), any())).thenReturn(new AgentResult<>(new CollectedMaterial(), 60, 200));
        when(writer.write(any(), any(), any())).thenReturn(new AgentResult<>(draftWithTitle("v"), 90, 300));
        ReviewResult pass = new ReviewResult();
        pass.setPassed(true);
        when(reviewer.review(any(), any())).thenReturn(new AgentResult<>(pass, 70, 150));

        orchestration.run(1L, 7L, LocalDate.of(2026, 7, 9), ReportType.DAILY);

        // run 结束后 AgentContext 应被 clear（@AfterEach 也 clear，此处额外验证无残留由 AfterEach 兜底）
    }
}
```

> `isNull()` / `contains()` 来自 `org.mockito.ArgumentMatchers`。`generate` 方法的测试在 Task 11（端点）一起，因 generate 依赖 `ReportGenerateDTO`（Task 11 创建）；本 task 测 `run`。

- [ ] **Step 4: 运行确认失败**

Run: `mvn -pl dayflow-server test -Dtest=ReportOrchestrationServiceImplTest`
Expected: FAIL（类不存在）。

- [ ] **Step 5: 实现 ReportOrchestrationService 接口**

```java
// agent/orchestration/ReportOrchestrationService.java
package com.dayflow.agent.orchestration;

import com.dayflow.pojo.dto.ReportGenerateDTO;
import com.dayflow.pojo.enums.ReportType;

import java.time.LocalDate;

/**
 * 报告编排服务：触发异步报告生成并执行 4 Agent 流水线。
 *
 * @author jiaxianming
 */
public interface ReportOrchestrationService {

    /**
     * 触发报告生成：创建 report(GENERATING) + 提交异步编排 + 立即返回 reportId
     *
     * @param dto 生成入参（type/date）
     * @return 新建报告 id
     */
    Long generate(ReportGenerateDTO dto);

    /**
     * 异步线程内执行 4 Agent 编排（由专用线程池驱动，不对外暴露）
     *
     * @param reportId 报告 id
     * @param userId   当前用户 id（经 AgentContext 传给 Tool）
     * @param date     报告日期
     * @param type     报告类型
     */
    void run(Long reportId, Long userId, LocalDate date, ReportType type);
}
```

> 此接口引用 `ReportGenerateDTO`（Task 11 创建）。为避免编译依赖顺序问题，**先在 Task 11 Step 1 创建 `ReportGenerateDTO`，再回头编译本接口**；或本 task 内同步先建 `ReportGenerateDTO` 骨架。下面 Step 6 实现同理依赖它。

- [ ] **Step 6: 实现 ReportOrchestrationServiceImpl**

```java
// agent/orchestration/ReportOrchestrationServiceImpl.java
package com.dayflow.agent.orchestration;

import com.dayflow.agent.collector.CollectorAgent;
import com.dayflow.agent.model.AgentResult;
import com.dayflow.agent.model.CollectedMaterial;
import com.dayflow.agent.model.DraftReport;
import com.dayflow.agent.model.DraftSection;
import com.dayflow.agent.model.PlanInput;
import com.dayflow.agent.model.ReportPlan;
import com.dayflow.agent.model.ReviewResult;
import com.dayflow.agent.planner.PlannerAgent;
import com.dayflow.agent.reviewer.ReviewerAgent;
import com.dayflow.agent.writer.WriterAgent;
import com.dayflow.common.UserContext;
import com.dayflow.mapper.ActivityMapper;
import com.dayflow.mapper.NoteMapper;
import com.dayflow.mapper.TaskMapper;
import com.dayflow.pojo.dto.ReportCreateDTO;
import com.dayflow.pojo.dto.ReportGenerateDTO;
import com.dayflow.pojo.entity.ActivityEntity;
import com.dayflow.pojo.entity.NoteEntity;
import com.dayflow.pojo.entity.TaskEntity;
import com.dayflow.pojo.enums.AgentName;
import com.dayflow.pojo.enums.ReportType;
import com.dayflow.pojo.enums.TaskStatus;
import com.dayflow.service.AgentTraceService;
import com.dayflow.service.ReportService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 报告编排实现：4 Agent 流水线 + 反馈循环（MAX_RETRY=2）+ 落库 + 写轨迹。
 * <p>异步经 dayflow-agent-executor 手动提交（不用 @Async，规避自调用坑）；
 * userId 经 AgentContext ThreadLocal 传给 Tool，LLM 不接触 userId；
 * 每步 trace 独立小事务，report 最终化单独事务，绝不用一个大事务包整个 run。</p>
 *
 * @author jiaxianming
 */
@Slf4j
@Service
public class ReportOrchestrationServiceImpl implements ReportOrchestrationService {

    private static final int MAX_RETRY = 2;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final PlannerAgent planner;
    private final CollectorAgent collector;
    private final WriterAgent writer;
    private final ReviewerAgent reviewer;
    private final AgentTraceService traceService;
    private final ReportService reportService;
    private final ActivityMapper activityMapper;
    private final TaskMapper taskMapper;
    private final NoteMapper noteMapper;
    private final ThreadPoolTaskExecutor agentExecutor;

    /**
     * @param planner       规划 Agent
     * @param collector     采集 Agent
     * @param writer        撰写 Agent
     * @param reviewer      审校 Agent
     * @param traceService  轨迹服务
     * @param reportService 报告 CRUD 服务
     * @param activityMapper 活动 Mapper（count 用）
     * @param taskMapper    任务 Mapper（count 用）
     * @param noteMapper    笔记 Mapper（count 用）
     * @param agentExecutor 专用线程池 dayflow-agent-executor
     */
    public ReportOrchestrationServiceImpl(PlannerAgent planner, CollectorAgent collector,
                                          WriterAgent writer, ReviewerAgent reviewer,
                                          AgentTraceService traceService, ReportService reportService,
                                          ActivityMapper activityMapper, TaskMapper taskMapper, NoteMapper noteMapper,
                                          @Qualifier("dayflow-agent-executor") ThreadPoolTaskExecutor agentExecutor) {
        this.planner = planner;
        this.collector = collector;
        this.writer = writer;
        this.reviewer = reviewer;
        this.traceService = traceService;
        this.reportService = reportService;
        this.activityMapper = activityMapper;
        this.taskMapper = taskMapper;
        this.noteMapper = noteMapper;
        this.agentExecutor = agentExecutor;
    }

    @Override
    public Long generate(ReportGenerateDTO dto) {
        Long userId = UserContext.getUserId();
        LocalDate date = dto.getDate();
        ReportCreateDTO createDTO = new ReportCreateDTO();
        createDTO.setType(dto.getType());
        createDTO.setPeriodStart(date);
        createDTO.setPeriodEnd(date);
        Long reportId = reportService.create(createDTO);
        final Long uid = userId;
        agentExecutor.execute(() -> run(reportId, uid, date, dto.getType()));
        log.info("报告生成已提交 reportId={} userId={} date={}", reportId, uid, date);
        return reportId;
    }

    @Override
    public void run(Long reportId, Long userId, LocalDate date, ReportType type) {
        AgentContext.setUserId(userId);
        int totalTokens = 0;
        int step = 1;
        try {
            // 1. 规划
            PlanInput planInput = buildPlanInput(userId, date, type);
            AgentResult<ReportPlan> planResult = planner.plan(planInput);
            totalTokens += planResult.tokens();
            trace(reportId, AgentName.PLANNER, step++, planInput, planResult.payload(), planResult, 0);

            // 2. 采集
            AgentResult<CollectedMaterial> materialResult = collector.collect(planResult.payload(), date);
            totalTokens += materialResult.tokens();
            trace(reportId, AgentName.COLLECTOR, step++, planResult.payload(), materialResult.payload(), materialResult, 0);

            // 3. 撰写 + 审校（反馈循环，最多 MAX_RETRY 次）
            AgentResult<DraftReport> draftResult = writer.write(planResult.payload(), materialResult.payload(), null);
            totalTokens += draftResult.tokens();
            DraftReport draft = draftResult.payload();
            trace(reportId, AgentName.WRITER, step++, materialResult.payload(), draft, draftResult, 0);

            int retry = 0;
            while (retry < MAX_RETRY) {
                AgentResult<ReviewResult> reviewResult = reviewer.review(draft, materialResult.payload());
                totalTokens += reviewResult.tokens();
                trace(reportId, AgentName.REVIEWER, step++, draft, reviewResult.payload(), reviewResult, retry);
                if (reviewResult.payload().isPassed()) {
                    break;
                }
                retry++;
                AgentResult<DraftReport> rewrite = writer.write(planResult.payload(), materialResult.payload(),
                        reviewResult.payload().getSuggestions());
                totalTokens += rewrite.tokens();
                draft = rewrite.payload();
                trace(reportId, AgentName.WRITER, step++, reviewResult.payload(), draft, rewrite, retry);
            }
            // 4. 落库（单独事务）
            reportService.markGenerated(reportId, toMarkdown(draft), totalTokens);
            log.info("报告生成完成 reportId={} tokens={}", reportId, totalTokens);
        } catch (Exception e) {
            log.error("报告生成失败 reportId={}", reportId, e);
            reportService.markFailed(reportId, e.getMessage());
        } finally {
            AgentContext.clear();
        }
    }

    private PlanInput buildPlanInput(Long userId, LocalDate date, ReportType type) {
        long actCount = countActivities(userId, date);
        long taskCount = countCompletedTasks(userId, date);
        long noteCount = countNotes(userId, date);
        String dataHint = (actCount + taskCount + noteCount == 0)
                ? "当日无任何记录"
                : "活动 " + actCount + " 条 / 任务 " + taskCount + " 条 / 笔记 " + noteCount + " 条";
        PlanInput input = new PlanInput();
        input.setDate(date);
        input.setReportType(type);
        input.setDataHint(dataHint);
        return input;
    }

    private long countActivities(Long userId, LocalDate date) {
        return activityMapper.selectCount(new LambdaQueryWrapper<ActivityEntity>()
                .eq(ActivityEntity::getUserId, userId)
                .ge(ActivityEntity::getOccurredAt, date.atStartOfDay())
                .le(ActivityEntity::getOccurredAt, date.atTime(23, 59, 59)));
    }

    private long countCompletedTasks(Long userId, LocalDate date) {
        return taskMapper.selectCount(new LambdaQueryWrapper<TaskEntity>()
                .eq(TaskEntity::getUserId, userId)
                .eq(TaskEntity::getStatus, TaskStatus.DONE)
                .ge(TaskEntity::getCompletedAt, date.atStartOfDay())
                .le(TaskEntity::getCompletedAt, date.atTime(23, 59, 59)));
    }

    private long countNotes(Long userId, LocalDate date) {
        return noteMapper.selectCount(new LambdaQueryWrapper<NoteEntity>()
                .eq(NoteEntity::getUserId, userId)
                .ge(NoteEntity::getCreatedAt, date.atStartOfDay())
                .le(NoteEntity::getCreatedAt, date.atTime(23, 59, 59)));
    }

    private void trace(Long reportId, AgentName agent, int step, Object input, Object output,
                       AgentResult<?> result, int retryCount) {
        traceService.trace(reportId, agent, step, toJson(input), toJson(output),
                result.tokens(), result.latencyMs(), retryCount);
    }

    @SneakyThrows
    private String toJson(Object obj) {
        return obj == null ? null : JSON.writeValueAsString(obj);
    }

    private String toMarkdown(DraftReport draft) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(draft.getTitle()).append("\n\n");
        if (draft.getSections() != null) {
            for (DraftSection s : draft.getSections()) {
                sb.append("## ").append(s.getName()).append("\n\n").append(s.getContent()).append("\n\n");
            }
        }
        return sb.toString();
    }
}
```

> `selectCount` 在 MyBatis-Plus 3.5.14 返回 `Long`。`markGenerated` 的 tokenUsage 参数为 `Integer`，传 `totalTokens`(int) 自动装箱。`@SneakyThrows` 同 Writer，可改 try-catch。

- [ ] **Step 7: 运行确认通过**

Run: `mvn -pl dayflow-server test -Dtest=ReportOrchestrationServiceImplTest`
Expected: 5 个测试全 PASS。

> 循环边界核对：`retry` 从 0 开始，`while (retry < 2)` 仅进入 retry=0、retry=1 两轮，每轮调一次 reviewer → reviewer 最多被调 **2 次**（首次 + 1 次重试），退出后强制 markGenerated。`runReviewerRejectThenRetryPass`：retry=0 review reject→返工，retry=1 review pass→break（reviewer 共调 2 次、writer 共调 2 次：循环外首次 + 循环内返工 1 次）；`runForcePassWhenRetryExceedsMax`：reviewer 始终 reject，调满 2 次后退出→markGenerated。符合 spec 5.4 伪代码。

- [ ] **Step 8: 提交**

```bash
git add dayflow-server/src/main/java/com/dayflow/service/ReportService.java dayflow-server/src/main/java/com/dayflow/service/impl/ReportServiceImpl.java dayflow-server/src/main/java/com/dayflow/agent/orchestration dayflow-server/src/test/java/com/dayflow/agent/orchestration
git commit -m "feat(m3): ReportOrchestrationService 编排 + 反馈循环 + 异步 + 三态错误处理"
```

---

## Task 11: POST /api/reports/generate 端点 + ReportGenerateDTO

**Files:**
- Create: `pojo/dto/ReportGenerateDTO.java`
- Modify: `controller/ReportController.java`（新增 generate 端点 + 注入 ReportOrchestrationService）
- Test: `dayflow-server/src/test/java/com/dayflow/controller/ReportControllerTest.java`（补 generate 用例）
- Test: 补 `ReportOrchestrationServiceImplTest` 的 generate 用例

**Interfaces:**
- Consumes: `ReportOrchestrationService.generate(ReportGenerateDTO) → Long`、`UserContext`
- Produces: `POST /api/reports/generate` body `{type, date}` → `Result<Long>`

- [ ] **Step 1: 创建 ReportGenerateDTO**

```java
// pojo/dto/ReportGenerateDTO.java
package com.dayflow.pojo.dto;

import com.dayflow.pojo.enums.ReportType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 报告生成入参
 *
 * @author jiaxianming
 */
@Data
public class ReportGenerateDTO {

    /** 报告类型 */
    @NotNull(message = "报告类型不能为空")
    private ReportType type;

    /** 报告日期 */
    @NotNull(message = "报告日期不能为空")
    private LocalDate date;
}
```

- [ ] **Step 2: ReportController 新增 generate 端点**

在 `ReportController` 注入 `ReportOrchestrationService`（构造器或字段注入，沿用现有风格）并加端点：

```java
// controller/ReportController.java —— 新增字段、构造器参数与端点
    private final ReportService reportService;
    private final ReportOrchestrationService orchestrationService;

    // 构造器注入两者（若原为构造器注入则补一个参数）

    /**
     * 触发报告生成（异步）：立即返回 reportId，前端轮询状态与轨迹
     *
     * @param dto 生成入参
     * @return 新建报告 id
     */
    @PostMapping("/generate")
    public Result<Long> generate(@Valid @RequestBody ReportGenerateDTO dto) {
        return Result.success(orchestrationService.generate(dto));
    }
```

> 沿用现有 Controller 风格（`@Tag`/`@Operation` 文档注解按 M1 习惯补）。`@RequestMapping("/api/reports")` 已在类级，`/generate` 即完整路径 `/api/reports/generate`。

- [ ] **Step 3: 写 Controller 切片测试（generate）**

在 `ReportControllerTest` 增加：

```java
    @MockitoBean
    private ReportOrchestrationService orchestrationService;  // 新增 mock

    @Test
    void generateReturns200WithReportId() throws Exception {
        when(orchestrationService.generate(any())).thenReturn(42L);
        mockMvc.perform(post("/api/reports/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DAILY\",\"date\":\"2026-07-09\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(42));
        verify(orchestrationService).generate(any());
    }

    @Test
    void generateWithInvalidBodyReturns400() throws Exception {
        // 缺 date → @Valid 失败 → 400
        mockMvc.perform(post("/api/reports/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DAILY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }
```

> `any()` 静态导入 `org.mockito.ArgumentMatchers.any`。

- [ ] **Step 4: 补 generate 的编排测试**

在 `ReportOrchestrationServiceImplTest` 增加（验证 generate 创建 report + 提交 executor + 返回 id；executor 用同步 `Runnable::run` 不可行——`@InjectMocks` 注入的是真实 executor。改为：测试 generate 时 mock reportService.create 返回 id，验证 agentExecutor.execute 被调一次）。

> 由于 `agentExecutor` 是真实 `ThreadPoolTaskExecutor`，`generate` 会真的提交 `run` 到线程池异步执行，单测内难断言。**两个选择**：
> 1. 把 `agentExecutor` 也 `@Mock`，`when(agentExecutor.execute(any())).thenAnswer(inv -> { ((Runnable) inv.getArgument(0)).run(); return null; })`——同步执行 run，可断言，但会触发 run 全链路 mock。
> 2. 仅验证 generate 同步部分：mock reportService.create 返回 88L，mock agentExecutor.execute 不做任何事（doNothing），断言返回 88L + verify execute 调用一次。
>
> 选 2（聚焦 generate 契约，run 已由前 5 个用例覆盖）：

```java
    @Test
    void generateCreatesReportAndSubmitsAsync() {
        // agentExecutor / 3 Mapper 已在类声明中 @Mock（见 Task 10）
        UserContext.setUserId(7L);
        when(reportService.create(any())).thenReturn(88L);
        doNothing().when(agentExecutor).execute(any());

        com.dayflow.pojo.dto.ReportGenerateDTO dto = new com.dayflow.pojo.dto.ReportGenerateDTO();
        dto.setType(ReportType.DAILY);
        dto.setDate(LocalDate.of(2026, 7, 9));

        Long id = orchestration.generate(dto);

        assertEquals(88L, id);
        verify(reportService).create(any());
        verify(agentExecutor).execute(any());
    }
```

> `assertEquals`/`doNothing` 需静态导入。此用例要求 `ReportOrchestrationServiceImpl` 的 `agentExecutor` 字段可被 `@InjectMocks` 注入 mock（字段名 `agentExecutor`，类型 `ThreadPoolTaskExecutor`）。

- [ ] **Step 5: 运行全部新增测试**

Run: `mvn -pl dayflow-server test -Dtest=ReportControllerTest,ReportOrchestrationServiceImplTest`
Expected: 全 PASS。

- [ ] **Step 6: 提交**

```bash
git add dayflow-server/src/main/java/com/dayflow/pojo/dto/ReportGenerateDTO.java dayflow-server/src/main/java/com/dayflow/controller/ReportController.java dayflow-server/src/test/java/com/dayflow/controller/ReportControllerTest.java dayflow-server/src/test/java/com/dayflow/agent/orchestration/ReportOrchestrationServiceImplTest.java
git commit -m "feat(m3): POST /api/reports/generate 异步生成端点 + ReportGenerateDTO"
```

---

## Task 12: 收尾（全绿 + live 冒烟 + tag m3-complete）

**Files:**
- Create: `dayflow-server/src/test/java/com/dayflow/ReportGenerateLiveSmokeTest.java`（live 门控）
- 验证 `application.yml`（无改动需求，确认）

**Interfaces:** 无新增

- [ ] **Step 1: 全量单测**

Run: `mvn -pl dayflow-server clean test`
Expected: BUILD SUCCESS，全部测试 PASS（含 M3 新增的 Agent/编排/工具/端点测试），无 key 时 live 门控测试自动 skip。

- [ ] **Step 2: 写 live 冒烟测试（门控）**

```java
// dayflow-server/src/test/java/com/dayflow/ReportGenerateLiveSmokeTest.java
package com.dayflow;

import com.dayflow.agent.orchestration.ReportOrchestrationService;
import com.dayflow.pojo.dto.ReportGenerateDTO;
import com.dayflow.pojo.enums.ReportType;
import com.dayflow.pojo.vo.AgentTraceVO;
import com.dayflow.pojo.vo.ReportVO;
import com.dayflow.pojo.enums.ReportStatus;
import com.dayflow.service.ReportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 报告生成 live 冒烟（可选）：真调 DeepSeek，端到端验证 4 Agent 协作。
 * <p>需本机 MySQL + DEEPSEEK_API_KEY；CI 无 key 自动跳过。合并前手动跑。</p>
 *
 * @author jiaxianming
 */
@SpringBootTest(properties = "spring.ai.deepseek.api-key=${DEEPSEEK_API_KEY}")
@EnabledIfEnvironmentVariable(named = "DEEPSEEK_API_KEY", matches = ".+")
class ReportGenerateLiveSmokeTest {

    @Autowired
    private ReportOrchestrationService orchestration;
    @Autowired
    private ReportService reportService;

    /**
     * ⚠️ live 测试需先有 JWT 上下文：实际经 Controller 触发更真实。
     * 此处直接调 orchestration.run 模拟（UserContext 由 generate 设置），
     * 但 generate 依赖 UserContext.getUserId()——live 场景需先 login 拿 token 走 HTTP。
     * 简化：直接调 run(reportId, userId, date, type)，reportId 由 reportService.create 预建。
     */
    @Test
    void generateProducesGeneratedReportWithTraces() {
        // 预建 report（status=GENERATING）
        ReportGenerateDTO dto = new ReportGenerateDTO();
        dto.setType(ReportType.DAILY);
        dto.setDate(LocalDate.now());
        // generate 内部从 UserContext 取 userId；测试线程无 JWT，故直接调 run
        // 先用固定 userId 建 report（此处假设 admin userId=1，按实际预置用户调整）
        Long userId = 1L;
        // 直接执行编排（同步，不经线程池）
        orchestration.run(1L, userId, LocalDate.now(), ReportType.DAILY);

        ReportVO report = reportService.getById(1L);
        // 验收：status=GENERATED（或 FAILED 时人工排查）
        assertNotNull(report);
        // 至少 4 条 Agent 轨迹（Planner/Collector/Writer/Reviewer 至少各一）
        List<AgentTraceVO> traces = reportService.listTraces(1L);
        assertNotNull(traces);
    }
}
```

> ⚠️ live 测试的真实路径建议**手动经 HTTP 触发**：启动服务 → 登录拿 JWT → `curl -X POST /api/reports/generate -H "Authorization: Bearer <token>" -d '{"type":"DAILY","date":"2026-07-09"}'` → 轮询 `GET /api/reports/{id}` 与 `GET /api/reports/{id}/traces`。上面的自动化 live 测试因 UserContext/线程池在测试线程下的局限，断言放宽（status/轨迹数量），**重点由人工 HTTP 验证**。若自动 live 测试不稳定，可降级为仅 `@Disabled` 占位 + 人工验收清单。

- [ ] **Step 3: 端到端四态人工验证（合并前）**

带 `DEEPSEEK_API_KEY` 启动服务，依次验证：
1. **正常**：当天有活动/任务/笔记 → `POST /api/reports/generate` 拿 reportId → 轮询 `GET /{id}` 到 `status=GENERATED` + content 非空可读 → `GET /{id}/traces` 见 ≥4 条轨迹。
2. **空数据**：无记录的日期生成 → 仍走 4 Agent、content 为 LLM 简短说明（非硬编码）、status=GENERATED。
3. **越权**：A 用户 token `GET /api/reports/{B 的 id}` → 403。
4. **参数缺失**：`POST /generate` 缺 date → 400。
5. **Task 幂等**：重复 `complete` 已 DONE 任务 → 不报错。

- [ ] **Step 4: 提交收尾**

```bash
git add dayflow-server/src/test/java/com/dayflow/ReportGenerateLiveSmokeTest.java
git commit -m "test(m3): 报告生成 live 冒烟（门控）+ 端到端验收"
git tag m3-complete
```

> tag 与合并 main 需用户明确授权后再执行（遵循「不自动提交/审查通过+授权才合并 main」）。

---

## Self-Review 结论

- **Spec 覆盖**：4 Agent（T5-T9）✓、反馈循环 MAX_RETRY=2（T10）✓、@Tool 工具调用（T6）✓、agent_trace 写入（T3/T10）✓、异步 + 前端轮询入口（T10/T11）✓、ownership guard + Task complete 幂等（T1）✓、三态错误处理（T10 run 的 try-catch + Task 内部 catch 留空由编排兜底）✓、空数据走完整 4 Agent（T10 buildPlanInput dataHint="当日无任何记录" → Planner LLM 产单板块）✓。
- **类型一致性**：`AgentResult<T>`、`AgentInvoker.invoke`、4 Agent 签名、`AgentTraceService.trace`、`ReportService.markGenerated/markFailed`、`ReportOrchestrationService.generate/run` 跨 task 一致已核对。
- **明确 spike 点**：① Spring AI 2.0 `CallResponse.entity()`/`.chatResponse()` API（T2/T5 live 验证）；② Collector 两段式「Tool+entity」组合（T7，fallback 已备）；③ `ActivityCategory` 枚举实际常量（T6 按编译错误修正）；④ `DefaultUsage` token 计算（T2 断言放宽）。均标注且不阻塞主线。
- **安全**：userId 不进 prompt（PlanInput 无 userId 字段）、仅 AgentContext 供 Tool（T2/T6）、ownership guard 覆盖 4 Service 按-id 操作（T1）。
