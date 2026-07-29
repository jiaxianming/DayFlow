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
        input.setStartDate(LocalDate.of(2026, 7, 9));
        input.setEndDate(LocalDate.of(2026, 7, 9));
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
