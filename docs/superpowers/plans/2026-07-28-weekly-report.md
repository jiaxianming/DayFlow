# 周报（WEEKLY）功能实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 打通周报全链路——后端推导自然周周期并按范围采集/统计，prompt 据类型产出周报板块与语气，前端提供"本周"入口。

**Architecture:** 新增纯函数周期推导工具 `ReportPeriods`；后端编排层把单日 `date` 全链路升级为 `[startDate, endDate]` 周期（接口 `run` 加参数、`PlanInput` 改字段、`CollectorAgent.collect` 加参数、`WriterAgent.write` 加类型参数）；4 个 Agent 的 5 个系统 prompt 去"日报"硬编码泛化为"报告"，类型与周期由 user prompt 注入；前端 `ReportView`/`HistoryView` 的 radio 从两选一扩为三选一（今日日报/本周周报/指定日期日报）。

**Tech Stack:** Java 21 · Spring Boot 4.1 · Spring AI 2.0 · MyBatis-Plus 3.5 · JUnit 5 + Mockito · Vue 3.5 + TS + Element Plus + Vitest

## Global Constraints

- **不自动提交（红线）**：每个 Task 末尾仅 `git add`（暂存），**禁止 `git commit`/`git push`**；全部 Task 完成或用户明确授权后再统一提交。提交前先 `git branch --show-current` 校验（本计划在 `feature/report-center-optimize` 分支）。
- **`@author jiaxianming`**：所有新建 Java 类的 JavaDoc `@author` 统一署名 `jiaxianming`；JavaDoc 多行格式（`/**` 与 `*/` 独占行）。
- **LLM 不接触 userId**：保持 `AgentContext` ThreadLocal 机制；本任务任何 prompt 改造**不得**把 userId 写进系统/user prompt。
- **不动数据采集工具**：`ReportDataTools` 三个 `@Tool` 已支持 `[startDate, endDate]`，本计划不改它、不改 DB schema。
- **测试命令**：后端 `cd dayflow-server && mvn test`；前端 `cd dayflow-web && npx vitest run && npx vue-tsc --noEmit`。
- **签名连锁注意**：`PlanInput` 字段、`run`/`collect`/`write` 签名变更互相牵连编排层，Task 2 必须整体完成才能编译通过——不可中途拆分提交。

---

## File Structure

**后端新建**：
- `agent/orchestration/ReportPeriods.java` — 周期推导工具（纯函数）+ 嵌套 `PeriodRange` record。

**后端修改**：
- `agent/model/PlanInput.java` — `date` → `startDate`/`endDate`。
- `agent/orchestration/ReportOrchestrationService.java` — `run` 加 `endDate` 参数。
- `agent/orchestration/ReportOrchestrationServiceImpl.java` — `generate` 推导周期、`run` 新签名、`buildPlanInput`/`countXxx` 按范围、兜底标题。
- `agent/collector/CollectorAgent.java` — `collect`/`buildPrompt` 用范围。
- `agent/planner/PlannerAgent.java` — `buildPrompt` 类型引导。
- `agent/writer/WriterAgent.java` — `write` 加 `ReportType` 参数、`buildPrompt` 类型引导。
- `agent/config/AgentChatClientConfig.java` — 5 个 prompt 常量泛化。

**后端测试新建/修改**：
- `agent/orchestration/ReportPeriodsTest.java`（新）
- `agent/orchestration/ReportOrchestrationServiceImplTest.java`（改：签名迁移 + WEEKLY 场景）
- `agent/collector/CollectorAgentTest.java`（改：`collect` 新签名）

**前端修改**：`views/report/ReportView.vue`、`views/history/HistoryView.vue`
**前端测试修改**：`views/report/__tests__/ReportView.test.ts`、`views/history/__tests__/HistoryView.test.ts`

---

### Task 1: ReportPeriods 周期推导工具

**Files:**
- Create: `dayflow-server/src/main/java/com/dayflow/agent/orchestration/ReportPeriods.java`
- Test: `dayflow-server/src/test/java/com/dayflow/agent/orchestration/ReportPeriodsTest.java`

**Interfaces:**
- Produces: `ReportPeriods.resolve(LocalDate date, ReportType type) → ReportPeriods.PeriodRange`（`PeriodRange.start()` / `PeriodRange.end()`）。Task 2 的 `generate` 依赖此方法。

- [ ] **Step 1: 写失败测试**

Create `ReportPeriodsTest.java`:

```java
package com.dayflow.agent.orchestration;

import com.dayflow.pojo.enums.ReportType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ReportPeriods 周期推导单测。
 *
 * @author jiaxianming
 */
class ReportPeriodsTest {

    @Test
    void dailyReturnsSameDay() {
        ReportPeriods.PeriodRange pr = ReportPeriods.resolve(LocalDate.of(2026, 7, 28), ReportType.DAILY);
        assertEquals(LocalDate.of(2026, 7, 28), pr.start());
        assertEquals(LocalDate.of(2026, 7, 28), pr.end());
    }

    @Test
    void weeklyFromTuesdayResolvesMondayToSunday() {
        // 2026-07-28 是周二 → 所在自然周 周一 07-27 ~ 周日 08-02
        ReportPeriods.PeriodRange pr = ReportPeriods.resolve(LocalDate.of(2026, 7, 28), ReportType.WEEKLY);
        assertEquals(LocalDate.of(2026, 7, 27), pr.start());
        assertEquals(LocalDate.of(2026, 8, 2), pr.end());
    }

    @Test
    void weeklyFromSundayKeepsSameWeek() {
        // 2026-08-02 是周日（本周最后一天）→ 仍是 07-27 ~ 08-02，不跳到下周
        ReportPeriods.PeriodRange pr = ReportPeriods.resolve(LocalDate.of(2026, 8, 2), ReportType.WEEKLY);
        assertEquals(LocalDate.of(2026, 7, 27), pr.start());
        assertEquals(LocalDate.of(2026, 8, 2), pr.end());
    }

    @Test
    void weeklyFromMondayResolvesSameWeek() {
        // 2026-07-27 是周一（本周第一天）
        ReportPeriods.PeriodRange pr = ReportPeriods.resolve(LocalDate.of(2026, 7, 27), ReportType.WEEKLY);
        assertEquals(LocalDate.of(2026, 7, 27), pr.start());
        assertEquals(LocalDate.of(2026, 8, 2), pr.end());
    }

    @Test
    void weeklyCrossesMonthBoundary() {
        // 2026-07-31 是周五 → 所在周 07-27 ~ 08-02（跨 7/8 月）
        ReportPeriods.PeriodRange pr = ReportPeriods.resolve(LocalDate.of(2026, 7, 31), ReportType.WEEKLY);
        assertEquals(LocalDate.of(2026, 7, 27), pr.start());
        assertEquals(LocalDate.of(2026, 8, 2), pr.end());
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd dayflow-server && mvn test -Dtest=ReportPeriodsTest`
Expected: FAIL（`ReportPeriods` 类不存在，编译错误）

- [ ] **Step 3: 实现 ReportPeriods**

Create `ReportPeriods.java`:

```java
package com.dayflow.agent.orchestration;

import com.dayflow.pojo.enums.ReportType;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/**
 * 报告周期推导工具：据日期 + 报告类型推导周期起止。
 * <p>DAILY → [date, date]；WEEKLY → date 所在自然周（周一~周日）。
 * 纯计算无副作用，跨月/跨年由 {@link java.time} 保证。
 * 周期推导只在后端做一次（LLM 不算日期），前端仅传 date + type。</p>
 *
 * @author jiaxianming
 */
public final class ReportPeriods {

    /**
     * 据 date 与 type 推导周期起止。
     *
     * @param date 锚点日期（日报为当日；周报为所在周内任一天）
     * @param type 报告类型
     * @return 周期起止
     */
    public static PeriodRange resolve(LocalDate date, ReportType type) {
        if (type == ReportType.WEEKLY) {
            return new PeriodRange(
                    date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                    date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)));
        }
        return new PeriodRange(date, date);
    }

    /**
     * 周期起止（不可变）。
     *
    * @param start 起始日（含）
    * @param end   结束日（含）
     */
    public record PeriodRange(LocalDate start, LocalDate end) {
    }

    private ReportPeriods() {
    }
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: `cd dayflow-server && mvn test -Dtest=ReportPeriodsTest`
Expected: PASS（5 个用例全绿）

- [ ] **Step 5: 暂存（不提交）**

```bash
git add dayflow-server/src/main/java/com/dayflow/agent/orchestration/ReportPeriods.java \
        dayflow-server/src/test/java/com/dayflow/agent/orchestration/ReportPeriodsTest.java
```

---

### Task 2: 后端周报核心整合（签名连锁，必须整体完成）

> 签名连锁说明：`PlanInput` 字段、`run`/`collect`/`write` 签名任一变更都牵连编排层编译，故本 Task 整体完成才能编译通过。先写 WEEKLY 失败测试（红），再逐步改实现（绿）。

**Files:**
- Modify: `dayflow-server/src/main/java/com/dayflow/agent/model/PlanInput.java`
- Modify: `dayflow-server/src/main/java/com/dayflow/agent/orchestration/ReportOrchestrationService.java`
- Modify: `dayflow-server/src/main/java/com/dayflow/agent/orchestration/ReportOrchestrationServiceImpl.java`
- Modify: `dayflow-server/src/main/java/com/dayflow/agent/collector/CollectorAgent.java`
- Modify: `dayflow-server/src/main/java/com/dayflow/agent/planner/PlannerAgent.java`
- Modify: `dayflow-server/src/main/java/com/dayflow/agent/writer/WriterAgent.java`
- Modify: `dayflow-server/src/main/java/com/dayflow/agent/config/AgentChatClientConfig.java`
- Test: `dayflow-server/src/test/java/com/dayflow/agent/orchestration/ReportOrchestrationServiceImplTest.java`
- Test: `dayflow-server/src/test/java/com/dayflow/agent/collector/CollectorAgentTest.java`

**Interfaces:**
- Consumes: `ReportPeriods.resolve` (Task 1)。
- Produces（新签名，供测试与未来调用方）：
  - `ReportOrchestrationService.run(Long reportId, Long userId, LocalDate startDate, LocalDate endDate, ReportType type)`
  - `CollectorAgent.collect(ReportPlan plan, LocalDate startDate, LocalDate endDate) → AgentResult<CollectedMaterial>`
  - `WriterAgent.write(ReportPlan plan, CollectedMaterial material, String suggestions, ReportType type) → AgentResult<DraftReport>`
  - `PlanInput` 字段：`startDate` / `endDate` / `reportType` / `dataHint`（去掉 `date`）。

- [ ] **Step 1: 先写 WEEKLY 失败测试（红）**

在 `ReportOrchestrationServiceImplTest.java` 顶部 import 区加：

```java
import org.mockito.ArgumentCaptor;
import com.dayflow.pojo.dto.ReportCreateDTO;
```

在类内新增两个测试方法（现有 7 个方法此时仍引用旧签名，会编译失败——这是预期，后续 step 修复）：

```java
    @Test
    void runWeeklyUsesPeriodRangeForCollectAndFallbackTitle() {
        when(planner.plan(any())).thenReturn(new AgentResult<>(plan(), 50, 100));
        when(collector.collect(any(), any(), any())).thenReturn(new AgentResult<>(new CollectedMaterial(), 60, 200));
        when(writer.write(any(), any(), any(), any())).thenReturn(new AgentResult<>(draftWithTitle("   "), 90, 300));
        ReviewResult pass = new ReviewResult();
        pass.setPassed(true);
        when(reviewer.review(any(), any())).thenReturn(new AgentResult<>(pass, 70, 150));

        // WEEKLY 周期：周一 2026-07-27 ~ 周日 2026-08-02（推导在 generate，run 直接收 start/end）
        orchestration.run(1L, 7L, LocalDate.of(2026, 7, 27), LocalDate.of(2026, 8, 2), ReportType.WEEKLY);

        // Collector 收到完整周期范围（非单日）
        verify(collector).collect(any(), eq(LocalDate.of(2026, 7, 27)), eq(LocalDate.of(2026, 8, 2)));
        // draft.title 空白 → 兜底标题含周期
        verify(reportService).markGenerated(eq(1L), eq("周报 2026-07-27~2026-08-02"), any(String.class), anyInt());
    }

    @Test
    void generateWeeklyDerivesNaturalWeekPeriod() {
        UserContext.setUserId(7L);
        when(reportService.create(any())).thenReturn(99L);
        doNothing().when(agentExecutor).execute(any());

        ReportGenerateDTO dto = new ReportGenerateDTO();
        dto.setType(ReportType.WEEKLY);
        dto.setDate(LocalDate.of(2026, 7, 28)); // 周二

        orchestration.generate(dto);

        ArgumentCaptor<ReportCreateDTO> captor = ArgumentCaptor.forClass(ReportCreateDTO.class);
        verify(reportService).create(captor.capture());
        // 周二 07-28 → 所在自然周 周一 07-27 ~ 周日 08-02
        assertEquals(LocalDate.of(2026, 7, 27), captor.getValue().getPeriodStart());
        assertEquals(LocalDate.of(2026, 8, 2), captor.getValue().getPeriodEnd());
    }
```

- [ ] **Step 2: 迁移现有 7 个测试到新签名**

对 `ReportOrchestrationServiceImplTest.java` 中**所有**现有方法做如下机械替换（共 6 处 `run` 调用、若干 `collect`/`write` mock）：

- `orchestration.run(1L, 7L, LocalDate.of(2026, 7, 9), ReportType.DAILY)` → `orchestration.run(1L, 7L, LocalDate.of(2026, 7, 9), LocalDate.of(2026, 7, 9), ReportType.DAILY)`
- `collector.collect(any(), any())` → `collector.collect(any(), any(), any())`
- `writer.write(any(), any(), any())` → `writer.write(any(), any(), any(), any())`
- `writer.write(any(), any(), isNull())` → `writer.write(any(), any(), isNull(), any())`
- `writer.write(any(), any(), eq("请精简"))` → `writer.write(any(), any(), eq("请精简"), any())`

`runReviewerRejectThenRetryPass` 的两行 `verify(writer).write(...)` 同步加末尾 `, any()`：
```java
        verify(writer).write(any(), any(), isNull(), any());       // 首次
        verify(writer).write(any(), any(), eq("请精简"), any());    // 返工一次
```

`runMarksFailedOnPlannerException` 的 `markGenerated` never 断言签名不变（仍 4 参）。

- [ ] **Step 3: 改 PlanInput 字段**

`PlanInput.java`：删 `date` 字段，改为 `startDate`/`endDate`。完整新内容：

```java
package com.dayflow.agent.model;

import com.dayflow.pojo.enums.ReportType;
import lombok.Data;

import java.time.LocalDate;

/**
 * 规划输入（编排层构造，发给 Planner）。
 * <p>注意：不含 userId —— userId 绝不进 prompt，仅经 {@code AgentContext} 供 Tool 使用，
 * 杜绝 LLM 幻觉导致越权拉取他人数据。</p>
 *
 * @author jiaxianming
 */
@Data
public class PlanInput {

    /**
     * 周期起始日（日报 == endDate；周报为所在周周一）
     */
    private LocalDate startDate;

    /**
     * 周期结束日（日报 == startDate；周报为所在周周日）
     */
    private LocalDate endDate;

    /**
     * 报告类型（日报 / 周报）
     */
    private ReportType reportType;

    /**
     * 数据提示：编排层先 count 各源条数，形如「活动 3 条 / 任务 2 条 / 笔记 1 条」，
     * 全 0 时为「当日/本周无任何记录」
     */
    private String dataHint;
}
```

- [ ] **Step 4: 改接口 run 签名**

`ReportOrchestrationService.java`：把 `run` 方法签名与 JavaDoc `@param` 改为：

```java
    /**
     * 异步线程内执行 4 Agent 编排（由专用线程池驱动，不对外暴露）
     * <p>userId 经 {@link AgentContext} ThreadLocal 传给 {@code ReportDataTools}；
     * LLM 全程不接触 userId。周期由 generate 经 {@link ReportPeriods} 推导后传入。</p>
     *
     * @param reportId  报告 id
     * @param userId    当前用户 id（经 AgentContext 传给 Tool）
     * @param startDate 周期起始日（含）
     * @param endDate   周期结束日（含）
     * @param type      报告类型
     */
    void run(Long reportId, Long userId, LocalDate startDate, LocalDate endDate, ReportType type);
```

- [ ] **Step 5: 改编排层 generate / run / buildPlanInput / count / 兜底标题**

`ReportOrchestrationServiceImpl.java`：

5a. `generate` 方法替换为（用 `ReportPeriods` 推导周期）：

```java
    @Override
    public Long generate(ReportGenerateDTO dto) {
        Long userId = UserContext.getUserId();
        ReportPeriods.PeriodRange period = ReportPeriods.resolve(dto.getDate(), dto.getType());
        ReportCreateDTO createDTO = new ReportCreateDTO();
        createDTO.setType(dto.getType());
        createDTO.setPeriodStart(period.start());
        createDTO.setPeriodEnd(period.end());
        Long reportId = reportService.create(createDTO);
        final Long uid = userId;
        agentExecutor.execute(() -> run(reportId, uid, period.start(), period.end(), dto.getType()));
        log.info("报告生成已提交 reportId={} userId={} period={}~{}", reportId, uid, period.start(), period.end());
        return reportId;
    }
```

5b. `run` 方法签名与方法体替换为：

```java
    @Override
    public void run(Long reportId, Long userId, LocalDate startDate, LocalDate endDate, ReportType type) {
        AgentContext.setUserId(userId);
        int totalTokens = 0;
        int step = 1;
        try {
            // 1. 规划
            PlanInput planInput = buildPlanInput(userId, startDate, endDate, type);
            AgentResult<ReportPlan> planResult = planner.plan(planInput);
            totalTokens += planResult.tokens();
            trace(reportId, AgentName.PLANNER, step++, planInput, planResult.payload(), planResult, 0);

            // 2. 采集（按周期范围）
            AgentResult<CollectedMaterial> materialResult = collector.collect(planResult.payload(), startDate, endDate);
            totalTokens += materialResult.tokens();
            trace(reportId, AgentName.COLLECTOR, step++, planResult.payload(), materialResult.payload(), materialResult, 0);

            // 3. 撰写 + 审校（反馈循环，最多 MAX_RETRY 次）
            AgentResult<DraftReport> draftResult = writer.write(planResult.payload(), materialResult.payload(), null, type);
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
                        reviewResult.payload().getSuggestions(), type);
                totalTokens += rewrite.tokens();
                draft = rewrite.payload();
                trace(reportId, AgentName.WRITER, step++, reviewResult.payload(), draft, rewrite, retry);
            }
            // 4. 落库（单独事务）：标题优先取 Writer 产出，缺失则按类型+周期兜底，保证 report.title 永不为 null
            String title = draft.getTitle();
            if (title == null || title.isBlank()) {
                title = (type == ReportType.WEEKLY)
                        ? "周报 " + startDate + "~" + endDate
                        : "日报 " + startDate;
            }
            reportService.markGenerated(reportId, title, toMarkdown(draft), totalTokens);
            log.info("报告生成完成 reportId={} tokens={}", reportId, totalTokens);
        } catch (Exception e) {
            log.error("报告生成失败 reportId={}", reportId, e);
            reportService.markFailed(reportId, e.getMessage());
        } finally {
            AgentContext.clear();
        }
    }
```

5c. `buildPlanInput` 与三个 `countXxx` 替换为（按范围 count + dataHint 区分本周/当日）：

```java
    private PlanInput buildPlanInput(Long userId, LocalDate startDate, LocalDate endDate, ReportType type) {
        long actCount = countActivities(userId, startDate, endDate);
        long taskCount = countCompletedTasks(userId, startDate, endDate);
        long noteCount = countNotes(userId, startDate, endDate);
        String prefix = (type == ReportType.WEEKLY) ? "本周" : "当日";
        String dataHint = (actCount + taskCount + noteCount == 0)
                ? prefix + "无任何记录"
                : prefix + "活动 " + actCount + " 条 / 任务 " + taskCount + " 条 / 笔记 " + noteCount + " 条";
        PlanInput input = new PlanInput();
        input.setStartDate(startDate);
        input.setEndDate(endDate);
        input.setReportType(type);
        input.setDataHint(dataHint);
        return input;
    }

    private long countActivities(Long userId, LocalDate startDate, LocalDate endDate) {
        return activityMapper.selectCount(new LambdaQueryWrapper<ActivityEntity>()
                .eq(ActivityEntity::getUserId, userId)
                .ge(ActivityEntity::getOccurredAt, startDate.atStartOfDay())
                .le(ActivityEntity::getOccurredAt, endDate.atTime(23, 59, 59)));
    }

    private long countCompletedTasks(Long userId, LocalDate startDate, LocalDate endDate) {
        return taskMapper.selectCount(new LambdaQueryWrapper<TaskEntity>()
                .eq(TaskEntity::getUserId, userId)
                .eq(TaskEntity::getStatus, TaskStatus.DONE)
                .ge(TaskEntity::getCompletedAt, startDate.atStartOfDay())
                .le(TaskEntity::getCompletedAt, endDate.atTime(23, 59, 59)));
    }

    private long countNotes(Long userId, LocalDate startDate, LocalDate endDate) {
        return noteMapper.selectCount(new LambdaQueryWrapper<NoteEntity>()
                .eq(NoteEntity::getUserId, userId)
                .ge(NoteEntity::getCreatedAt, startDate.atStartOfDay())
                .le(NoteEntity::getCreatedAt, endDate.atTime(23, 59, 59)));
    }
```

（删除旧的 `countActivities(userId, date)` 等单日重载，避免混淆。）

- [ ] **Step 6: 改 CollectorAgent（collect + buildPrompt 用范围）**

`CollectorAgent.java`：

`collect` 方法签名与方法体：

```java
    public AgentResult<CollectedMaterial> collect(ReportPlan plan, LocalDate startDate, LocalDate endDate) {
        String prompt = buildPrompt(plan, startDate, endDate);
        // 第一段：带 tool 采集——仅取文本（callForContent），规避 tool calling 后空 content 崩溃
        AgentResult<String> collected = invoker.callForContent(collectorChatClient, prompt);
        // 第二段：无 tool 结构化——把采集文本喂给 structChatClient，DeepSeek 无 tool 调用稳定产 content
        AgentResult<CollectedMaterial> structured =
                invoker.invoke(structChatClient, collected.payload(), CollectedMaterial.class);
        // 两段 token / latency 累加
        return new AgentResult<>(structured.payload(),
                collected.tokens() + structured.tokens(),
                collected.latencyMs() + structured.latencyMs());
    }
```

`buildPrompt` 方法：

```java
    private String buildPrompt(ReportPlan plan, LocalDate startDate, LocalDate endDate) {
        StringBuilder sb = new StringBuilder();
        sb.append("采集周期：").append(startDate).append(" ~ ").append(endDate).append("。")
          .append("开始：").append(startDate).append("，结束：").append(endDate).append("。");
        sb.append("报告标题：").append(plan.getTitle()).append("。");
        sb.append("请按以下板块结构采集数据：\n");
        if (plan.getSections() != null) {
            for (PlanSection s : plan.getSections()) {
                sb.append("- 板块「").append(s.getName()).append("」，数据源：")
                  .append(s.getDataSource()).append("，重点：").append(s.getFocus()).append("\n");
            }
        }
        sb.append("对每个板块调用对应工具拉取真实数据，按板块归类并归纳摘要。");
        return sb.toString();
    }
```

同步更新 `collect`/`buildPrompt` 的 JavaDoc（`@param date` → `@param startDate`/`@param endDate`，"采集范围 = [date, date]" → "采集范围 = [startDate, endDate]"）。

- [ ] **Step 7: 改 PlannerAgent（buildPrompt 类型引导）**

`PlannerAgent.java`：`buildPrompt` 替换为（顶部加 `import com.dayflow.pojo.enums.ReportType;`）：

```java
    private String buildPrompt(PlanInput input) {
        ReportType type = input.getReportType();
        String typeLabel = (type == ReportType.WEEKLY) ? "周报" : "日报";
        String period = input.getStartDate().equals(input.getEndDate())
                ? "日期：" + input.getStartDate()
                : "周期：" + input.getStartDate() + " ~ " + input.getEndDate();
        String sectionHint = (type == ReportType.WEEKLY)
                ? "（建议板块：本周工作总结 / 学习收获 / 问题与改进 / 下周计划）"
                : "（建议板块：今日工作 / 学习记录）";
        return period + "；报告类型：" + typeLabel + "；数据提示："
                + (input.getDataHint() == null ? "无" : input.getDataHint())
                + "。请据此规划" + typeLabel + "板块结构" + sectionHint + "。";
    }
```

- [ ] **Step 8: 改 WriterAgent（write 加 type 参数 + buildPrompt 类型引导）**

`WriterAgent.java`：

`write` 签名（加 `ReportType type` 参数，顶部加 `import com.dayflow.pojo.enums.ReportType;`）：

```java
    public AgentResult<DraftReport> write(ReportPlan plan, CollectedMaterial material, String suggestions, ReportType type) {
        String prompt = buildPrompt(plan, material, suggestions, type);
        return invoker.invoke(writerChatClient, prompt, DraftReport.class);
    }
```

`buildPrompt`：

```java
    @SneakyThrows
    private String buildPrompt(ReportPlan plan, CollectedMaterial material, String suggestions, ReportType type) {
        String typeLabel = (type == ReportType.WEEKLY) ? "周报" : "日报";
        return "报告计划：" + JSON.writeValueAsString(plan)
                + "\n采集素材：" + JSON.writeValueAsString(material)
                + "\n修改建议：" + (suggestions == null ? "无修改建议（首次撰写）" : suggestions)
                + "\n请据此撰写" + typeLabel + "草稿。";
    }
```

同步更新 `write`/`buildPrompt` 的 JavaDoc（`@param` 增加 type）。

- [ ] **Step 9: 改 5 个 prompt 常量（泛化去硬编码）**

`AgentChatClientConfig.java`，替换 5 个常量：

```java
    /** 主编：规划报告板块（日报/周报） */
    public static final String PLANNER_PROMPT = """
            你是报告主编（Planner）。根据用户提供的报告类型（日报/周报）、周期与数据提示，规划一份「工作与学习报告」的板块结构。
            规则：
            1. 板块数量 2-4 个；每个板块指定 dataSource（ACTIVITY/TASK/NOTE 之一）与 focus（该板块重点）。
            2. 标题格式据类型：日报为「<日期> 工作与学习日报」，周报为「<周期> 工作与学习周报」。
            3. 若数据提示表明无任何记录，则产出单个板块（name=暂无记录，dataSource=ACTIVITY，focus=说明无记录）。
            4. 严格输出结构化 JSON，字段：title、sections[{name,dataSource,focus}]。
            """;

    /** 撰稿：把素材写成中文段落（日报/周报） */
    public static final String WRITER_PROMPT = """
            你是报告撰稿人（Writer）。根据报告计划与采集到的素材，撰写通顺的中文 markdown 段落。
            规则：
            1. 严格按计划板块结构组织；每个板块 content 为 2-5 句中文段落。
            2. 每段必须有素材依据，不得臆造、不得夸大；某板块无素材时写「本板块无记录」。
            3. 客观专业、不啰嗦；若收到修改建议（suggestions），严格据此修改。
            4. 严格输出结构化 JSON，字段：title、sections[{name,content}]。
            """;

    /** 审校：质检草稿（四维质检与报告类型无关） */
    public static final String REVIEWER_PROMPT = """
            你是报告审校（Reviewer）。对草稿做四维质检：①素材依据（是否夸大/无依据 OVERCLAIM）
            ②去重（板块间是否重复 REDUNDANT）③板块完整（是否漏板块 MISSING）④语气（是否不当 TONE）。
            规则：
            1. 全部通过则 passed=true、issues 为空、suggestions 为空。
            2. 否则 passed=false，issues 列出具体问题，suggestions 给出给撰稿人的明确修改建议。
            3. 严格输出结构化 JSON，字段：passed(boolean)、issues[{section,type,description}]、suggestions。
            """;

    /** 记者：采集（在 Task 6 补建 collectorChatClient 时使用） */
    public static final String COLLECTOR_PROMPT = """
            你是报告记者（Collector）。根据报告计划，调用提供的工具采集真实数据，按板块归类并归纳摘要。
            规则：
            1. 必须调用工具拉取真实数据，禁止编造；按计划板块的 dataSource 调对应工具。
            2. 每条素材出 summary（简短摘要）与 ref（如时间或标题）。
            3. 某数据源为空则该板块 items 为空、保留板块名。
            4. 采集完成后用自然语言归纳各板块素材（供下一步结构化）。
            """;
```

以及 `COLLECTOR_STRUCT_PROMPT`：把"你是日报素材整理员"改为"你是报告素材整理员"（其余不变）：

```java
    public static final String COLLECTOR_STRUCT_PROMPT = """
            你是报告素材整理员。给你一段已经采集归纳好的文本，请整理成结构化的板块素材。
            规则：
            1. 按文本内容归类到对应板块，每条素材出 source(ACTIVITY/TASK/NOTE)、summary(简短摘要)、ref(时间或标题)。
            2. 文本为空或无任何素材时，输出 sections 为空数组 []。
            3. 严格输出结构化 JSON，字段：sections[{sectionName,items[{source,summary,ref}]}]。
            """;
```

- [ ] **Step 10: 迁移 CollectorAgentTest 到新签名**

`CollectorAgentTest.java`：把两处 `collector.collect(plan, LocalDate.of(2026, 7, 9))` 改为传起止两个参数（日报 start==end）：

```java
        AgentResult<CollectedMaterial> result = collector.collect(plan, LocalDate.of(2026, 7, 9), LocalDate.of(2026, 7, 9));
```

（两个 `@Test` 方法各一处，共两处。）

- [ ] **Step 11: 跑后端全量测试确认全绿**

Run: `cd dayflow-server && mvn test`
Expected: BUILD SUCCESS，`Tests run` 全 0 失败（含新增 `ReportPeriodsTest` 5 个、`runWeeklyUsesPeriodRangeForCollectAndFallbackTitle`、`generateWeeklyDerivesNaturalWeekPeriod`）。

> 若 `runWeeklyUsesPeriodRangeForCollectAndFallbackTitle` 的 `markGenerated` 标题断言失败：检查兜底标题拼接是否为 `"周报 " + startDate + "~" + endDate`（LocalDate toString 为 `2026-07-27`）。

- [ ] **Step 12: 暂存（不提交）**

```bash
git add dayflow-server/src/main/java/com/dayflow/agent/ \
        dayflow-server/src/test/java/com/dayflow/agent/
```

---

### Task 3: 前端 ReportView 三选一

**Files:**
- Modify: `dayflow-web/src/views/report/ReportView.vue`
- Test: `dayflow-web/src/views/report/__tests__/ReportView.test.ts`

**Interfaces:**
- Consumes: `reportStore.triggerGenerate({type: 'DAILY'|'WEEKLY', date})`（API dto 不变）。
- Produces: 无（纯 UI 改造）。

- [ ] **Step 1: 改 ReportView.test.ts（先让"本周"用例失败）**

`ReportView.test.ts` 现有"默认今日"用例断言 `{ type: 'DAILY', date: todayString() }`，保留。新增"本周"用例：

```typescript
  it('选「本周」生成周报：用当天日期 + WEEKLY 类型', async () => {
    const wrapper = await mountView()
    wrapper.findComponent({ name: 'ElRadioGroup' }).vm.$emit('update:modelValue', 'week')
    await nextTick()
    const genBtn = wrapper.findAll('button').find((b) => b.text().includes('生成报告'))!
    await genBtn.trigger('click')
    await nextTick()
    expect(reportApi.generateReport).toHaveBeenCalledWith({ type: 'WEEKLY', date: todayString() })
  })
```

并把"默认今日"用例里查找按钮的文案从"生成日报"改为"生成报告"（按钮文案将统一）。

- [ ] **Step 2: 跑测试确认失败**

Run: `cd dayflow-web && npx vitest run src/views/report/__tests__/ReportView.test.ts`
Expected: FAIL（找不到"生成报告"按钮 / radio 无 'week' 选项）

- [ ] **Step 3: 改 ReportView.vue**

3a. `<script setup>` 区：把 `mode` 与生成逻辑改为支持三态。替换现有 `mode`/`date`/`resolveDate`/`onGenerate` 相关片段为：

```typescript
/** 生成范围：今日(日报) / 本周(周报) / 指定日期(日报) */
const mode = ref<'today' | 'week' | 'custom'>('today')
const date = ref<string>(todayString())
const generating = ref(false)

/** 指定日期模式下禁止选择未来日期 */
function disabledFuture(d: Date): boolean {
  return d.getTime() > Date.now()
}

/** 据所选范围解析报告类型：本周 → WEEKLY，其余 → DAILY */
function resolveType(): 'WEEKLY' | 'DAILY' {
  return mode.value === 'week' ? 'WEEKLY' : 'DAILY'
}

/** 据所选范围解析目标日期：今日/本周 → 当天；指定日期 → 用户所选 */
function resolveDate(): string {
  return mode.value === 'custom' ? date.value : todayString()
}

/** 生成报告：triggerGenerate → 跳新 reportId */
async function onGenerate(): Promise<void> {
  generating.value = true
  try {
    const id = await reportStore.triggerGenerate({ type: resolveType(), date: resolveDate() })
    router.push('/reports/' + id)
  } catch {
    // 拦截器已提示
  } finally {
    generating.value = false
  }
}
```

3b. `<template>` 区：把 generate-bar 内的 radio-group/date-picker/按钮替换为：

```vue
      <el-radio-group v-model="mode">
        <el-radio-button value="today">今日</el-radio-button>
        <el-radio-button value="week">本周</el-radio-button>
        <el-radio-button value="custom">指定日期</el-radio-button>
      </el-radio-group>
      <el-date-picker
        v-if="mode === 'custom'"
        v-model="date"
        type="date"
        value-format="YYYY-MM-DD"
        placeholder="选择日期"
        :disabled-date="disabledFuture"
        style="margin-left: 12px; width: 180px"
      />
      <el-button
        type="primary"
        :loading="generating || reportStore.isGenerating"
        :disabled="mode === 'custom' && !date"
        style="margin-left: 12px"
        @click="onGenerate"
      >
        生成报告
      </el-button>
```

3c. 空状态文案改为：

```vue
          <div v-if="!report" class="report-empty">
            选择「今日 / 本周 / 指定日期」，点「生成报告」生成报告
          </div>
```

- [ ] **Step 4: 跑测试确认通过**

Run: `cd dayflow-web && npx vitest run src/views/report/__tests__/ReportView.test.ts`
Expected: PASS（3 个用例：默认今日 / 切指定日期出 picker / 本周→WEEKLY）

- [ ] **Step 5: 暂存（不提交）**

```bash
git add dayflow-web/src/views/report/ReportView.vue \
        dayflow-web/src/views/report/__tests__/ReportView.test.ts
```

---

### Task 4: 前端 HistoryView 对话框三选一

**Files:**
- Modify: `dayflow-web/src/views/history/HistoryView.vue`
- Test: `dayflow-web/src/views/history/__tests__/HistoryView.test.ts`

**Interfaces:**
- Consumes: `reportStore.triggerGenerate({type, date})`。
- Produces: 无。

- [ ] **Step 1: 改 HistoryView.test.ts 新增"本周"用例**

在现有"点生成报告打开对话框"用例之后，新增（验证选本周后确认→WEEKLY）：

```typescript
  it('对话框选「本周」确认 → 以 WEEKLY + 当天触发生成', async () => {
    vi.spyOn(reportApi, 'pageReports').mockResolvedValue({
      records: [], total: 0, size: 10, current: 1, pages: 0,
    })
    const genSpy = vi.spyOn(reportApi, 'generateReport').mockResolvedValue('w-1')
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/:rest(.*)*', component: { template: '<div/>' } }],
    })
    const wrapper = mount(HistoryView, { global: { plugins: [router, ElementPlus] } })
    await nextTick()
    await nextTick()

    // 打开对话框
    const openBtn = wrapper.findAll('button').find((b) => b.text().includes('生成报告'))!
    await openBtn.trigger('click')
    await nextTick()
    // 选「本周」
    wrapper.findComponent({ name: 'ElRadioGroup' }).vm.$emit('update:modelValue', 'week')
    await nextTick()
    // 点对话框内「生成」
    const confirmBtn = wrapper.findAll('button').find((b) => b.text().trim() === '生成')!
    await confirmBtn.trigger('click')
    await nextTick()

    expect(genSpy).toHaveBeenCalledWith({ type: 'WEEKLY', date: todayString() })
    wrapper.unmount()
  })
```

（顶部需已 `import { todayString } from '@/utils/format'`——若没有则补。）

- [ ] **Step 2: 跑测试确认失败**

Run: `cd dayflow-web && npx vitest run src/views/history/__tests__/HistoryView.test.ts`
Expected: FAIL（对话框 radio 无 'week' 选项）

- [ ] **Step 3: 改 HistoryView.vue**

3a. `<script setup>` 区：把 `mode` 与生成逻辑改为三态。替换 `mode`/`date`/`resolveDate`/`openGenerate`/`onGenerate` 相关片段为：

```typescript
/** 生成范围：今日(日报) / 本周(周报) / 指定日期(日报) */
const mode = ref<'today' | 'week' | 'custom'>('today')
const date = ref<string>(todayString())

/** 指定日期模式下禁止选择未来日期 */
function disabledFuture(d: Date): boolean {
  return d.getTime() > Date.now()
}

/** 据所选范围解析报告类型：本周 → WEEKLY，其余 → DAILY */
function resolveType(): 'WEEKLY' | 'DAILY' {
  return mode.value === 'week' ? 'WEEKLY' : 'DAILY'
}

/** 据所选范围解析目标日期：今日/本周 → 当天；指定日期 → 用户所选 */
function resolveDate(): string {
  return mode.value === 'custom' ? date.value : todayString()
}

/** 打开生成对话框：每次重置为今日，避免上次选择残留 */
function openGenerate(): void {
  mode.value = 'today'
  date.value = todayString()
  dialogVisible.value = true
}

/** 确认生成：按所选类型+日期触发，成功后关对话框并跳详情 */
async function onGenerate(): Promise<void> {
  try {
    const id = await reportStore.triggerGenerate({ type: resolveType(), date: resolveDate() })
    dialogVisible.value = false
    router.push('/reports/' + id)
  } catch {
    // 拦截器已提示；失败时保留对话框供重试
  }
}
```

3b. `<template>` 区：把对话框内的 radio-group/date-picker 替换为：

```vue
      <el-radio-group v-model="mode">
        <el-radio-button value="today">今日</el-radio-button>
        <el-radio-button value="week">本周</el-radio-button>
        <el-radio-button value="custom">指定日期</el-radio-button>
      </el-radio-group>
      <el-date-picker
        v-if="mode === 'custom'"
        v-model="date"
        type="date"
        value-format="YYYY-MM-DD"
        placeholder="选择日期"
        :disabled-date="disabledFuture"
        style="width: 100%; margin-top: 12px"
      />
```

footer 的"生成"按钮 `:disabled` 条件不变（`mode === 'custom' && !date`）。

- [ ] **Step 4: 跑前端全量测试 + 类型检查**

Run: `cd dayflow-web && npx vitest run && npx vue-tsc --noEmit`
Expected: vitest 全绿（含 HistoryView 3 用例 + ReportView 3 用例），vue-tsc exit 0。

- [ ] **Step 5: 暂存（不提交）**

```bash
git add dayflow-web/src/views/history/HistoryView.vue \
        dayflow-web/src/views/history/__tests__/HistoryView.test.ts
```

---

## 实现完成后（所有 Task）

- 后端全量：`cd dayflow-server && mvn test`
- 前端全量：`cd dayflow-web && npx vitest run && npx vue-tsc --noEmit`
- 全绿后，**等用户明确授权**再 `git commit`（提交前 `git branch --show-current` 校验为 `feature/report-center-optimize`）。建议提交粒度：Task 1 一个 commit（feat: 周期推导工具）、Task 2 一个 commit（feat: 后端周报核心）、Task 3+4 一个 commit（feat: 前端周报入口），spec+plan 文档单独一个 docs commit。
