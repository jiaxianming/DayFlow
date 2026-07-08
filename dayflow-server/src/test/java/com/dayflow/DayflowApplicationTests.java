package com.dayflow;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Spring 上下文加载测试
 * <p>注入测试用 DeepSeek key 绕过 AiConfig fail-fast（M2 T1 引入），
 * 让上下文正常装载以验证 bean 装配完整性。</p>
 */
@SpringBootTest(properties = "spring.ai.deepseek.api-key=test-key")
class DayflowApplicationTests {

    /**
     * 上下文可正常加载
     */
    @Test
    void contextLoads() {
    }
}
