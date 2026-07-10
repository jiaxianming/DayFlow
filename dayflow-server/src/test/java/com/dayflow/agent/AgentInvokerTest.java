package com.dayflow.agent;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AgentInvoker 单测：覆盖 token 提取纯函数。
 * <p>invoke 主流程依赖 Spring AI 2.0 CallResponseSpec API，由 live 冒烟端到端验证（T5 Planner 首次接入）。
 * 本测试聚焦 {@link AgentInvoker#extractTokens(ChatResponse)} 的 null 防御与正常路径。</p>
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
        // Spring AI 2.0 DefaultUsage 字段为 final（无 setter），通过构造函数注入；
        // 2-参构造内部由 calculateTotalTokens 算出 total = prompt + completion
        DefaultUsage usage = new DefaultUsage(60, 40);
        when(resp.getMetadata()).thenReturn(meta);
        when(meta.getUsage()).thenReturn(usage);
        // totalTokens = prompt(60) + completion(40) = 100
        assertEquals(100, AgentInvoker.extractTokens(resp));
    }

    @Test
    void extractContentReturnsEmptyWhenResponseNull() {
        assertEquals("", AgentInvoker.extractContent(null));
    }

    @Test
    void extractContentReturnsEmptyWhenNoResult() {
        ChatResponse resp = mock(ChatResponse.class);
        when(resp.getResult()).thenReturn(null);
        assertEquals("", AgentInvoker.extractContent(resp));
    }

    @Test
    void extractContentReturnsText() {
        ChatResponse resp = mock(ChatResponse.class);
        Generation gen = mock(Generation.class);
        AssistantMessage msg = mock(AssistantMessage.class);
        when(resp.getResult()).thenReturn(gen);
        when(gen.getOutput()).thenReturn(msg);
        when(msg.getText()).thenReturn("采集归纳文本");
        assertEquals("采集归纳文本", AgentInvoker.extractContent(resp));
    }
}
