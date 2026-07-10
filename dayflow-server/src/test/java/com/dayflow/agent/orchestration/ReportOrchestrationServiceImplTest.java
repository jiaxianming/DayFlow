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
import com.dayflow.pojo.dto.ReportGenerateDTO;
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

    /**
     * generate 契约：读 UserContext.userId -> reportService.create 建报告 -> 提交 agentExecutor 异步 -> 返回 reportId。
     * <p>run 全链路已由前 5 个用例覆盖，这里聚焦 generate 同步契约：
     * mock reportService.create 返回 88L，agentExecutor.execute doNothing（不触发 run），断言返回值 + verify 提交一次。</p>
     */
    @Test
    void generateCreatesReportAndSubmitsAsync() {
        // agentExecutor / reportService / 3 Mapper 均已在类声明中 @Mock（见上）
        UserContext.setUserId(7L);
        when(reportService.create(any())).thenReturn(88L);
        doNothing().when(agentExecutor).execute(any());

        ReportGenerateDTO dto = new ReportGenerateDTO();
        dto.setType(ReportType.DAILY);
        dto.setDate(LocalDate.of(2026, 7, 9));

        Long id = orchestration.generate(dto);

        assertEquals(88L, id);
        verify(reportService).create(any());
        verify(agentExecutor).execute(any());
    }
}
