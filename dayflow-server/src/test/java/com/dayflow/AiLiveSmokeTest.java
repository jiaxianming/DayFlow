package com.dayflow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DeepSeek live 冒烟测试（可选）
 * <p>仅当环境变量 DEEPSEEK_API_KEY 非空时运行；CI 无 key 自动跳过。
 * 需本机 MySQL（@SpringBootTest）。合并前手动跑一次确认真实链路。</p>
 *
 * @author jiaxianming
 */
@SpringBootTest(properties = "spring.ai.deepseek.api-key=${DEEPSEEK_API_KEY}")
@EnabledIfEnvironmentVariable(named = "DEEPSEEK_API_KEY", matches = ".+")
class AiLiveSmokeTest {

    @Autowired
    private ChatClient chatClient;

    /**
     * 真调 DeepSeek，断言非空回复
     */
    @Test
    void deepSeekReplies() {
        String reply = chatClient.prompt().user("用一个字回答：你好").call().content();
        assertTrue(reply != null && !reply.isBlank(), "DeepSeek 回复不应为空，实际: " + reply);
    }
}
