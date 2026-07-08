package com.dayflow.config;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AiConfig 测试
 * <p>① ChatClient bean 装配成功（@SpringBootTest，需本机 MySQL）；
 * ② provider=deepseek 且 key 空白时启动 fail-fast（ApplicationContextRunner，不连 DB）。</p>
 *
 * @author jiaxianming
 */
class AiConfigTest {

    /**
     * 完整上下文下 ChatClient bean 存在（provider 默认 deepseek，注入测试 key 绕过 fail-fast）
     * <p>注：需 @Nested + 非静态，否则 JUnit 5 不会发现该嵌套测试类。</p>
     */
    @Nested
    @SpringBootTest(properties = "spring.ai.deepseek.api-key=test-key")
    class ContextLoads {

        @Autowired(required = false)
        private ChatClient chatClient;

        @Test
        void chatClientBeanExists() {
            assertThat(chatClient).isNotNull();
        }
    }

    /**
     * provider=deepseek 且 api-key 空白 → 启动抛 IllegalStateException
     */
    @Test
    void failFastWhenDeepSeekKeyMissing() {
        new ApplicationContextRunner()
                .withUserConfiguration(AiConfig.class)
                .withPropertyValues(
                        "spring.ai.model.chat=deepseek",
                        "spring.ai.deepseek.api-key=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    Throwable root = context.getStartupFailure();
                    while (root.getCause() != null) {
                        root = root.getCause();
                    }
                    assertThat(root).isInstanceOf(IllegalStateException.class);
                    assertThat(root.getMessage()).contains("DEEPSEEK_API_KEY");
                });
    }
}

