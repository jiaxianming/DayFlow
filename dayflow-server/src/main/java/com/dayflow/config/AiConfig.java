package com.dayflow.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Spring AI 接入配置
 * <p>Provider 选择与 ChatModel 创建交给 Spring AI 2.0 原生 auto-config
 * （由 spring.ai.model.chat 决定激活 DeepSeek 或 Ollama）。本类只负责：
 * 1) 自建 ChatClient bean（ChatClient.create(chatModel)），供业务层注入与单测 mock；
 * 2) 启动期 fail-fast：provider=none 或 provider=deepseek 但 DEEPSEEK_API_KEY 空白时直接报错。</p>
 *
 * @author jiaxianming
 */
@Slf4j
@Configuration
public class AiConfig {

    private final Environment environment;

    /**
     * 注入 Environment 用于启动期读取 AI provider 配置做 fail-fast 校验
     *
     * @param environment Spring 环境，用于读取 spring.ai.* 属性做 fail-fast 校验
     */
    public AiConfig(Environment environment) {
        this.environment = environment;
    }

    /**
     * 自建 ChatClient，业务层统一注入此 bean
     *
     * @param chatModel auto-config 按 spring.ai.model.chat 选定的 ChatModel
     * @return ChatClient
     */
    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.create(chatModel);
    }

    /**
     * 启动期 fail-fast：provider=none 或 deepseek 缺 key 不让带病启动
     */
    @PostConstruct
    public void validate() {
        String provider = environment.getProperty("spring.ai.model.chat", "deepseek");
        if ("none".equalsIgnoreCase(provider)) {
            throw new IllegalStateException(
                    "DayFlow 启动失败：spring.ai.model.chat=none，未启用任何对话模型。"
                            + "请设置 DAYFLOW_AI_PROVIDER=deepseek（需 DEEPSEEK_API_KEY）或 ollama。");
        }
        if ("deepseek".equals(provider)) {
            String key = environment.getProperty("spring.ai.deepseek.api-key", "");
            if (key.isBlank()) {
                throw new IllegalStateException(
                        "DayFlow 启动失败：spring.ai.model.chat=deepseek 但未配置 DEEPSEEK_API_KEY。"
                                + "请设置环境变量 DEEPSEEK_API_KEY，或改用 DAYFLOW_AI_PROVIDER=ollama。");
            }
        }
        log.info("DayFlow AI provider = {}", provider);
    }
}
