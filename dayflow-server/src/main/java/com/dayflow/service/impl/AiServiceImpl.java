package com.dayflow.service.impl;

import com.dayflow.common.BusinessException;
import com.dayflow.common.ResultCode;
import com.dayflow.pojo.dto.ChatRequestDTO;
import com.dayflow.pojo.vo.ChatVO;
import com.dayflow.service.AiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
@Service
public class AiServiceImpl implements AiService {

    private static final Logger log = LoggerFactory.getLogger(AiServiceImpl.class);

    private final ChatClient chatClient;
    private final Environment environment;

    /**
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
        return new ChatVO(reply == null ? "" : reply, currentProvider(), currentModel());
    }

    /**
     * @return 当前激活 provider（默认 deepseek）
     */
    private String currentProvider() {
        return environment.getProperty("spring.ai.model.chat", "deepseek");
    }

    /**
     * @return 当前 provider 对应的模型名
     */
    private String currentModel() {
        if ("ollama".equals(currentProvider())) {
            return environment.getProperty("spring.ai.ollama.chat.model", "qwen2.5");
        }
        return environment.getProperty("spring.ai.deepseek.chat.model", "deepseek-chat");
    }
}
