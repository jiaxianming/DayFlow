package com.dayflow.service.impl;

import com.dayflow.common.BusinessException;
import com.dayflow.common.ResultCode;
import com.dayflow.pojo.dto.ChatRequestDTO;
import com.dayflow.pojo.vo.ChatVO;
import com.dayflow.service.AiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/**
 * AI 对话服务实现
 * <p>注入 T1 自建的 {@link ChatClient} 调用模型，并将 LLM 任意异常统一映射为
 * {@link BusinessException}(SYSTEM_ERROR=500)。回复为 null 时兜底空串。
 * provider/model 元信息从 {@link Environment} 读取，确保返回 VO 可追溯实际使用的模型。</p>
 *
 * @author jiaxianming
 */
@Slf4j
@Service
public class AiServiceImpl implements AiService {

    private final ChatClient chatClient;
    private final Environment environment;

    /**
     * 注入 ChatClient 与 Environment，构造可调用的对话服务
     *
     * @param chatClient  AiConfig 自建的 ChatClient
     * @param environment 读取 spring.ai.* 填充 provider/model 元信息
     */
    public AiServiceImpl(ChatClient chatClient, Environment environment) {
        this.chatClient = chatClient;
        this.environment = environment;
    }

    @Override
    public ChatVO chat(ChatRequestDTO dto) {
        String reply;
        try {
            reply = chatClient.prompt()
                    .user(dto.getMessage())
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("AI 调用失败: {}", e.getMessage(), e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "AI 服务调用失败，请稍后重试");
        }
        String provider = currentProvider();
        return new ChatVO(reply == null ? "" : reply, provider, currentModel(provider));
    }

    /**
     * @return 当前激活 provider（默认 deepseek）
     */
    private String currentProvider() {
        return environment.getProperty("spring.ai.model.chat", "deepseek");
    }

    /**
     * @param provider 当前激活 provider
     * @return 当前 provider 对应的模型名
     */
    private String currentModel(String provider) {
        if ("ollama".equals(provider)) {
            return environment.getProperty("spring.ai.ollama.chat.model", "qwen2.5");
        }
        return environment.getProperty("spring.ai.deepseek.chat.model", "deepseek-chat");
    }
}
