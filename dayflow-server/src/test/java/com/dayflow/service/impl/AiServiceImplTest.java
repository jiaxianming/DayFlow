package com.dayflow.service.impl;

import com.dayflow.common.BusinessException;
import com.dayflow.pojo.dto.ChatRequestDTO;
import com.dayflow.pojo.vo.ChatVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * AiServiceImpl 单元测试
 * <p>用 RETURNS_DEEP_STUBS 桩 ChatClient 流式链（.prompt().user().call().content()），
 * 验证回复映射 + provider/model 元信息 + LLM 异常映射 500。</p>
 *
 * @author jiaxianming
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiServiceImplTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    @Mock
    private Environment environment;

    @InjectMocks
    private AiServiceImpl aiService;

    /**
     * 正常调用：返回回复并附 provider/model
     */
    @Test
    void chatReturnsReplyWithMeta() {
        when(chatClient.prompt().user(anyString()).call().content()).thenReturn("你好");
        when(environment.getProperty("spring.ai.model.chat", "deepseek")).thenReturn("deepseek");
        when(environment.getProperty("spring.ai.deepseek.chat.model", "deepseek-chat"))
                .thenReturn("deepseek-chat");

        ChatRequestDTO dto = new ChatRequestDTO();
        dto.setMessage("在吗");

        ChatVO vo = aiService.chat(dto);

        assertEquals("你好", vo.getReply());
        assertEquals("deepseek", vo.getProvider());
        assertEquals("deepseek-chat", vo.getModel());
    }

    /**
     * LLM 调用异常 → BusinessException(code=500)
     */
    @Test
    void chatThrowsBusinessExceptionOnLlmFailure() {
        when(chatClient.prompt().user(anyString()).call().content())
                .thenThrow(new RuntimeException("boom"));

        ChatRequestDTO dto = new ChatRequestDTO();
        dto.setMessage("在吗");

        BusinessException ex = assertThrows(BusinessException.class, () -> aiService.chat(dto));
        assertEquals(500, ex.getCode());
    }

    /**
     * provider=ollama 分支：返回回复并附 ollama 元信息
     */
    @Test
    void chatReturnsOllamaMeta() {
        when(chatClient.prompt().user(anyString()).call().content()).thenReturn("hi");
        when(environment.getProperty("spring.ai.model.chat", "deepseek")).thenReturn("ollama");
        when(environment.getProperty("spring.ai.ollama.chat.model", "qwen2.5"))
                .thenReturn("qwen2.5");

        ChatRequestDTO dto = new ChatRequestDTO();
        dto.setMessage("在吗");

        ChatVO vo = aiService.chat(dto);

        assertEquals("hi", vo.getReply());
        assertEquals("ollama", vo.getProvider());
        assertEquals("qwen2.5", vo.getModel());
    }

    /**
     * LLM 返回 null → reply 兜底空串（其余元信息仍填充）
     */
    @Test
    void chatReturnsEmptyReplyWhenModelReturnsNull() {
        when(chatClient.prompt().user(anyString()).call().content()).thenReturn(null);
        when(environment.getProperty("spring.ai.model.chat", "deepseek")).thenReturn("deepseek");
        when(environment.getProperty("spring.ai.deepseek.chat.model", "deepseek-chat"))
                .thenReturn("deepseek-chat");

        ChatRequestDTO dto = new ChatRequestDTO();
        dto.setMessage("在吗");

        ChatVO vo = aiService.chat(dto);

        assertEquals("", vo.getReply());
        assertEquals("deepseek", vo.getProvider());
        assertEquals("deepseek-chat", vo.getModel());
    }
}
