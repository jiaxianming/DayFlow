package com.dayflow.agent.orchestration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;

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

    /**
     * 反馈循环最大重试次数（Writer↔Reviewer）；首次 Writer 撰写在循环外，不计入
     */
    private static final int MAX_RETRY = 2;

    /**
     * JSON 序列化器（Spring Boot 4.1 内置 Jackson 3.x，包名为 {@code tools.jackson.databind}）
     */
    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * 规划 Agent
     */
    private final PlannerAgent planner;

    /**
     * 采集 Agent
     */
    private final CollectorAgent collector;

    /**
     * 撰写 Agent
     */
    private final WriterAgent writer;

    /**
     * 审校 Agent
     */
    private final ReviewerAgent reviewer;

    /**
     * 轨迹服务（每步独立小事务落 agent_trace）
     */
    private final AgentTraceService traceService;

    /**
     * 报告 CRUD 服务（create + markGenerated/markFailed）
     */
    private final ReportService reportService;

    /**
     * 活动 Mapper（count 当日活动条数填 dataHint）
     */
    private final ActivityMapper activityMapper;

    /**
     * 任务 Mapper（count 当日已完成任务条数填 dataHint）
     */
    private final TaskMapper taskMapper;

    /**
     * 笔记 Mapper（count 当日笔记条数填 dataHint）
     */
    private final NoteMapper noteMapper;

    /**
     * 专用线程池 dayflow-agent-executor（手动提交 run，不用 @Async）
     */
    private final ThreadPoolTaskExecutor agentExecutor;

    /**
     * 构造编排服务。
     *
     * @param planner        规划 Agent
     * @param collector      采集 Agent
     * @param writer         撰写 Agent
     * @param reviewer       审校 Agent
     * @param traceService   轨迹服务
     * @param reportService  报告 CRUD 服务
     * @param activityMapper 活动 Mapper（count 用）
     * @param taskMapper     任务 Mapper（count 用）
     * @param noteMapper     笔记 Mapper（count 用）
     * @param agentExecutor  专用线程池 dayflow-agent-executor
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
            // 4. 落库（单独事务）：标题优先取 Writer 产出，缺失则按类型+日期兜底，保证 report.title 永不为 null
            String title = draft.getTitle();
            if (title == null || title.isBlank()) {
                title = (type == ReportType.WEEKLY ? "周报 " : "日报 ") + date;
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

    /**
     * 构造规划输入：先 count 各源条数填 dataHint，userId 仅用于 DB count，不进 prompt。
     *
     * @param userId 当前用户 id
     * @param date   报告日期
     * @param type   报告类型
     * @return 规划输入（date/reportType/dataHint，不含 userId）
     */
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

    /**
     * count 当日活动条数
     *
     * @param userId 用户 id
     * @param date   日期
     * @return 当日 [00:00, 23:59:59] 活动条数
     */
    private long countActivities(Long userId, LocalDate date) {
        return activityMapper.selectCount(new LambdaQueryWrapper<ActivityEntity>()
                .eq(ActivityEntity::getUserId, userId)
                .ge(ActivityEntity::getOccurredAt, date.atStartOfDay())
                .le(ActivityEntity::getOccurredAt, date.atTime(23, 59, 59)));
    }

    /**
     * count 当日已完成任务条数
     *
     * @param userId 用户 id
     * @param date   日期
     * @return 当日状态为 DONE 的任务条数
     */
    private long countCompletedTasks(Long userId, LocalDate date) {
        return taskMapper.selectCount(new LambdaQueryWrapper<TaskEntity>()
                .eq(TaskEntity::getUserId, userId)
                .eq(TaskEntity::getStatus, TaskStatus.DONE)
                .ge(TaskEntity::getCompletedAt, date.atStartOfDay())
                .le(TaskEntity::getCompletedAt, date.atTime(23, 59, 59)));
    }

    /**
     * count 当日笔记条数
     *
     * @param userId 用户 id
     * @param date   日期
     * @return 当日创建的笔记条数
     */
    private long countNotes(Long userId, LocalDate date) {
        return noteMapper.selectCount(new LambdaQueryWrapper<NoteEntity>()
                .eq(NoteEntity::getUserId, userId)
                .ge(NoteEntity::getCreatedAt, date.atStartOfDay())
                .le(NoteEntity::getCreatedAt, date.atTime(23, 59, 59)));
    }

    /**
     * 写一条 Agent 轨迹：把输入/输出序列化为 JSON 摘要后落库。
     *
     * @param reportId   报告 id
     * @param agent      Agent 名称
     * @param step       步骤序号
     * @param input      输入对象（序列化为摘要）
     * @param output     输出对象（序列化为摘要）
     * @param result     Agent 调用结果（取 tokens/latencyMs）
     * @param retryCount 重试次数（首次为 0）
     */
    private void trace(Long reportId, AgentName agent, int step, Object input, Object output,
                       AgentResult<?> result, int retryCount) {
        traceService.trace(reportId, agent, step, toJson(input), toJson(output),
                result.tokens(), result.latencyMs(), retryCount);
    }

    /**
     * 对象转 JSON（null 返回 null）
     *
     * @param obj 任意对象
     * @return JSON 字符串
     */
    @SneakyThrows
    private String toJson(Object obj) {
        return obj == null ? null : JSON.writeValueAsString(obj);
    }

    /**
     * 把草稿拼接为 markdown 正文（标题 + 各板块）
     *
     * @param draft 草稿
     * @return markdown 正文
     */
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
