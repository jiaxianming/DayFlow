package com.dayflow.common;

import com.dayflow.controller.HealthController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 全局异常处理测试
 * <p>依赖 HealthController 的 /api/health/error 测试端点触发业务异常</p>
 *
 * @author jiaxianming
 */
@WebMvcTest(HealthController.class)
@Import(GlobalExceptionHandler.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * 业务异常被全局处理器捕获后，Result.code 必须映射为 409（业务规则冲突）
     */
    @Test
    void businessExceptionReturns409() throws Exception {
        mockMvc.perform(get("/api/health/error"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.msg").value("业务规则冲突"));
    }
}
