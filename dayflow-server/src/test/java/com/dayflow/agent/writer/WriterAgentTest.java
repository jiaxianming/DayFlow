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
