package com.dayflow.agent.config;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AgentChatClientConfig 装配测试（需本机 MySQL + DEEPSEEK_API_KEY，provider 默认 deepseek）。
 *
 * @author jiaxianming
 */
class AgentChatClientConfigTest {

    @Nested
    @SpringBootTest(properties = "spring.ai.deepseek.api-key=test-key")
    class ContextLoads {

        @Autowired(required = false)
        @Qualifier("plannerChatClient")
        private ChatClient plannerChatClient;

        @Autowired(required = false)
        @Qualifier("writerChatClient")
        private ChatClient writerChatClient;

        @Autowired(required = false)
        @Qualifier("reviewerChatClient")
        private ChatClient reviewerChatClient;

        @Test
        void threeAgentChatClientsExist() {
            assertThat(plannerChatClient).isNotNull();
            assertThat(writerChatClient).isNotNull();
            assertThat(reviewerChatClient).isNotNull();
        }
    }
}
