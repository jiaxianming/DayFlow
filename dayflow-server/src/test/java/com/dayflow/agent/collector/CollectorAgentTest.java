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
