# 周报（WEEKLY）功能实现设计

> 状态：待实现 · 分支：`feature/report-center-optimize`（报告中心优化延续）
> 日期：2026-07-28 · 作者：jiaxianming

## 1. 目标与背景

DayFlow 已支持日报（DAILY）生成：4 Agent 编辑部流水线（Planner→Collector→Writer↔Reviewer）+ 反馈循环。但**周报（WEEKLY）从未真正实现**——枚举、字段、兜底分支都在，周期推导却是空的。

**根因**（全链路把周报当"单日日报"）：

| 位置 | 现状 | 问题 |
|---|---|---|
| `ReportOrchestrationServiceImpl.generate` | `setPeriodStart(date); setPeriodEnd(date)` | 周期起止同一天 |
| `run(reportId, userId, date, type)` | 只收单日 `date` | 周报无周期概念 |
| `buildPlanInput` / `countXxx` | 按单日 `[00:00,23:59:59]` 统计；`dataHint` 写死"当日" | 周报只统计一天 |
| `CollectorAgent.collect(plan, date)` | 只收单日，prompt "开始/结束都=同一天" | 只采一天数据 |
| 4 个系统 prompt | 全部硬编码"日报主编/记者/撰稿人/审校" | 周报产出仍偏日报 |

**好消息**（地基已就绪，不用动）：`ReportDataTools` 三个 `@Tool`（`listActivities`/`listCompletedTasks`/`searchNotes`）**已支持 `[startDate, endDate]` 范围查询**；`ReportType` 枚举、`ReportEntity.periodStart/periodEnd`、`PlanInput.reportType`、前端 `ReportType='DAILY'|'WEEKLY'`、`IReportGenerateDTO(type,date)` 全部就绪。

**目标**：打通周报全链路——后端推导自然周周期并按范围采集/统计，prompt 据类型产出周报板块与语气，前端提供周报入口。

## 2. 决策记录（用户确认）

1. **周期定义 = 自然周（周一~周日）**。选"本周"→ 当前所在自然周；`date` 所在周的周一~周日由后端推导。符合中文"周报"语义，边界稳定。
2. **前端入口 = 简化单选三选一**：今日日报 / 本周周报 / 指定日期（仅日报）。周报以今天为锚点，不支持选日期补历史周报。
3. **prompt 策略 = 泛化 + 类型引导**：系统 prompt 去"日报"硬编码、泛化为"报告"，类型与周期由 user prompt 注入。一套 prompt 通吃，不为周报单独建 ChatClient。

## 3. 详细设计

### 3.1 周期推导（新增工具类）

新建 `com.dayflow.agent.orchestration.ReportPeriods`，含嵌套 `record PeriodRange(LocalDate start, LocalDate end)`：

```java
public static PeriodRange resolve(LocalDate date, ReportType type) {
    if (type == ReportType.WEEKLY) {
        return new PeriodRange(
            date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
            date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)));
    }
    return new PeriodRange(date, date); // DAILY
}
```

- 纯计算无副作用；跨月/跨年由 `java.time` 保证。
- 前端只传 `date + type`，**推导只在后端做一次**，LLM 不算日期。
- `PeriodRange` 仅在编排层内部流转；下游（Collector/Planner）收散参数 `start/end`，不依赖此类型，避免扩散。

### 3.2 后端数据流改造（`ReportOrchestrationServiceImpl`）

**接口签名变更**：

```
- void run(Long reportId, Long userId, LocalDate date, ReportType type);
+ void run(Long reportId, Long userId, LocalDate startDate, LocalDate endDate, ReportType type);
```

同步改 `ReportOrchestrationService` 接口 + 所有测试调用。

- `generate(dto)`：
  ```java
  PeriodRange pr = ReportPeriods.resolve(dto.getDate(), dto.getType());
  createDTO.setPeriodStart(pr.start());
  createDTO.setPeriodEnd(pr.end());
  ...
  run(reportId, uid, pr.start(), pr.end(), dto.getType());
  ```
- `run(...)`：把 `startDate/endDate` 传给 `buildPlanInput` 与 `collector.collect`。
- `buildPlanInput(userId, startDate, endDate, type)`：三个 `countXxx` 参数由单日 `date` 改为 `start/end`，范围 `.ge(start.atStartOfDay()).le(end.atTime(23,59,59))`；`dataHint` 前缀按类型：`type==WEEKLY ? "本周" : "当日"`，全 0 → "本周无任何记录" / "当日无任何记录"。
- 兜底标题：`type==WEEKLY ? "周报 " + start + "~" + end : "日报 " + start`。

### 3.3 CollectorAgent 改造

```
- AgentResult<CollectedMaterial> collect(ReportPlan plan, LocalDate date)
+ AgentResult<CollectedMaterial> collect(ReportPlan plan, LocalDate startDate, LocalDate endDate)
```

`buildPrompt`：用 `"采集周期：" + start + " ~ " + end` 替换原来"开始/结束都=同一天"。**`ReportDataTools` 不动**（已支持范围）。

### 3.4 prompt 改造（泛化 + 类型引导）

**系统 prompt 常量**（`AgentChatClientConfig`）：

| 常量 | 改动 |
|---|---|
| `PLANNER_PROMPT` | "日报主编"→"报告主编"；声明"会告知报告类型（日报/周报）与周期，据类型规划板块"；标题格式分类型：日报「`<日期>` 工作与学习日报」/周报「`<周期>` 工作与学习周报」 |
| `WRITER_PROMPT` | "日报撰稿人"→"报告撰稿人"；"本板块今日无记录"→"本板块无记录" |
| `COLLECTOR_PROMPT` / `COLLECTOR_STRUCT_PROMPT` | "日报记者"/"日报素材整理员"→"报告记者"/"报告素材整理员" |
| `REVIEWER_PROMPT` | 基本不动（四维质检与类型无关），"日报审校"→"报告审校"保持一致 |

**user prompt 按类型注入引导**：

- `PlannerAgent.buildPrompt`：
  - DAILY → `"日期：X；数据提示：…；请规划今日工作与学习日报的板块结构。"`
  - WEEKLY → `"周期：周一~周日；数据提示：…；请规划本周工作与学习周报的板块结构（建议：本周工作总结 / 学习收获 / 问题与改进 / 下周计划）。"`
- `WriterAgent.buildPrompt`：末句 `"请据此撰写日报草稿。"` → 按 type `"请据此撰写日报/周报草稿。"`

### 3.5 `PlanInput` 模型变更

```
- private LocalDate date;
+ private LocalDate startDate;
+ private LocalDate endDate;
```

`reportType` / `dataHint` 保留。`PlannerAgent.buildPrompt` 用 `startDate/endDate` 显示周期（日报时 start==end）。

### 3.6 前端改造（ReportView 顶部 + HistoryView 对话框，两处一致）

`mode` 类型扩展：`'today' | 'custom'` → `'today' | 'week' | 'custom'`，radio 三选一：

| mode | 触发 dto | date-picker |
|---|---|---|
| `today` | `{ type:'DAILY', date: todayString() }` | 不显示 |
| `week` | `{ type:'WEEKLY', date: todayString() }` | 不显示（后端推导本周） |
| `custom` | `{ type:'DAILY', date: 用户所选 }` | 显示，`disabledFuture` 保留 |

- 按钮文案统一"生成报告"（radio 已标明类型）。
- 空状态文案调整为"选择今日 / 本周 / 指定日期，点「生成报告」生成报告"。
- `IReportGenerateDTO`（type+date）无需改。

## 4. 测试策略

**后端新增**：
- `ReportPeriodsTest`：DAILY 同日；WEEKLY 周二→本周一~周日；周日边界（周日即本周最后一天）；跨月边界（如 2026-07-31 所在周横跨 7/8 月）。

**后端改造**：
- `ReportOrchestrationServiceImplTest`：`run` 调用全部改为新签名（5 个参数）；新增 WEEKLY 场景——verify `count` 按范围、`collect` 收到 start/end、`markGenerated` 兜底标题含周期（"周报 …~…"）。
- `CollectorAgentTest`：`collect(plan, start, end)` 新签名；verify prompt 含范围文本。

**前端改造**：
- `ReportView.test.ts` / `HistoryView.test.ts`：补"选本周 → triggerGenerate({type:'WEEKLY', date: today})""选指定日期 → {type:'DAILY', date: 所选}"。Element Plus jsdom 下 radio 仍用 `findComponent({name:'ElRadioGroup'}).vm.$emit('update:modelValue', val)` 驱动。

## 5. 文件清单

**后端（1 新 + 7 改）**：

| 文件 | 动作 |
|---|---|
| `agent/orchestration/ReportPeriods.java` | 新建：周期推导 + `PeriodRange` record |
| `agent/orchestration/ReportOrchestrationService.java` | 改：`run` 签名 +1 参数 |
| `agent/orchestration/ReportOrchestrationServiceImpl.java` | 改：generate 推导 / run / buildPlanInput / count / 兜底标题 |
| `agent/collector/CollectorAgent.java` | 改：collect+buildPrompt 用范围 |
| `agent/planner/PlannerAgent.java` | 改：buildPrompt 类型引导 |
| `agent/writer/WriterAgent.java` | 改：buildPrompt 类型引导 |
| `agent/config/AgentChatClientConfig.java` | 改：5 个 prompt 常量泛化（PLANNER/WRITER/REVIEWER/COLLECTOR/COLLECTOR_STRUCT，对应 4 个 Agent） |
| `agent/model/PlanInput.java` | 改：date → start/end |

**后端测试（1 新 + 2 改）**：`ReportPeriodsTest.java`（新）；`ReportOrchestrationServiceImplTest.java`、`CollectorAgentTest.java`（改）。

**前端（2 改）**：`views/report/ReportView.vue`、`views/history/HistoryView.vue`。

**前端测试（2 改）**：`views/report/__tests__/ReportView.test.ts`、`views/history/__tests__/HistoryView.test.ts`。

## 6. 不做范围（YAGNI）

- 不为周报单独建 ChatClient / 一套 prompt（方案 B，过度设计）。
- 周报不支持"指定日期补历史周报"（用户选简化单选）。
- 不动 `ReportDataTools`（已支持范围）、不动 `ReviewerAgent` 核心质检逻辑、不动 DB schema（`period_start/end` 早有）。

## 7. 验收标准

1. 前端选"本周"→ 生成 WEEKLY 报告，`period_start/period_end` 为本周一~本周日。
2. 周报正文板块为周报结构（本周总结/下周计划等），标题含周期或由 Writer 产出周报标题。
3. 周报统计与采集覆盖整个自然周（非单日）。
4. 选"今日""指定日期"仍为 DAILY，行为与改造前一致（回归）。
5. 后端 `mvn test` 全绿（含 `ReportPeriodsTest` 新增、`run` 签名回归）；前端 `vitest` + `vue-tsc` 全绿。

## 8. 分支策略

延续 `feature/report-center-optimize`（报告中心优化进行中、未合并）。周报是该分支主题（报告生成）的自然延伸，且要改的 `ReportView`/`HistoryView` 正是上一轮"先选再生成"改过的文件，连贯。整支审查通过 + 用户明确授权后再合并 `main`。
