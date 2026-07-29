package com.dayflow.agent.collector;

import com.dayflow.agent.AgentInvoker;
import com.dayflow.agent.model.AgentResult;
import com.dayflow.agent.model.CollectedMaterial;
import com.dayflow.agent.model.ReportPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CollectorAgent 测试：验证两段式采集（带 tool 取文本 → 无 tool 结构化）。
 *
 * <p>核心防御契约：DeepSeek tool calling 后最终 content 间歇为空，
 * 第一段经 {@code callForContent} 取文本（空也安全返回 ""，不抛），
 * 第二段用无 tool 的 {@code structChatClient} 结构化，杜绝 {@code .entity()}
 * 反序列化空串导致的 {@code MismatchedInputException} 崩溃。</p>
 *
 * @author jiaxianming
 */
@ExtendWith(MockitoExtension.class)
class CollectorAgentTest {

    @Mock
    private AgentInvoker invoker;

    @Mock
    private ChatClient collectorChatClient;

    /**
     * 第二段结构化专用 ChatClient（无 tool），由 CollectorAgent 注入
     */
    @Mock
    private ChatClient structChatClient;

    private CollectorAgent collector;

    /**
     * 手动构造：两个同类型 ChatClient mock 下 {@code @InjectMocks} 按名匹配不可靠，
     * 显式按构造器参数顺序注入确保 collectorChatClient / structChatClient 各归其位。
     */
    @BeforeEach
    void setUp() {
        collector = new CollectorAgent(invoker, collectorChatClient, structChatClient);
    }

    /**
     * 两段式正常路径：第一段带 tool 采集文本，第二段无 tool 结构化；token/latency 两段累加。
     */
    @Test
    void collectTwoPhaseStructuresCollectedText() {
        ReportPlan plan = new ReportPlan();
        plan.setTitle("2026-07-09 工作与学习日报");
        CollectedMaterial material = new CollectedMaterial();
        when(invoker.callForContent(eq(collectorChatClient), any(String.class)))
                .thenReturn(new AgentResult<>("采集归纳文本", 100, 500));
        when(invoker.invoke(eq(structChatClient), any(String.class), eq(CollectedMaterial.class)))
                .thenReturn(new AgentResult<>(material, 50, 300));

        AgentResult<CollectedMaterial> result = collector.collect(plan, LocalDate.of(2026, 7, 9), LocalDate.of(2026, 7, 9));

        assertSame(material, result.payload());
        // 两段 token 累加：100 + 50 = 150
        assertEquals(150, result.tokens());
        // 两段 latency 累加：500 + 300 = 800
        assertEquals(800, result.latencyMs());
        verify(invoker).callForContent(eq(collectorChatClient), any(String.class));
        verify(invoker).invoke(eq(structChatClient), any(String.class), eq(CollectedMaterial.class));
    }

    /**
     * 间歇崩溃防御：第一段 tool 调用后 content 为空（复现 DeepSeek 间歇空），
     * collect 不得抛异常，仍把空文本喂给第二段结构化降级（产空素材）。
     */
    @Test
    void collectSurvivesEmptyContentFromToolCall() {
        ReportPlan plan = new ReportPlan();
        plan.setTitle("2026-07-09 工作与学习日报");
        when(invoker.callForContent(eq(collectorChatClient), any(String.class)))
                .thenReturn(new AgentResult<>("", 80, 400));
        when(invoker.invoke(eq(structChatClient), eq(""), eq(CollectedMaterial.class)))
                .thenReturn(new AgentResult<>(new CollectedMaterial(), 30, 200));

        AgentResult<CollectedMaterial> result = collector.collect(plan, LocalDate.of(2026, 7, 9), LocalDate.of(2026, 7, 9));

        // 空 content 仍返回非 null payload（降级空素材），不崩
        assertNotNull(result.payload());
        verify(invoker).invoke(eq(structChatClient), eq(""), eq(CollectedMaterial.class));
    }
}
