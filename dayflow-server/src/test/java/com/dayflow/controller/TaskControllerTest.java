package com.dayflow.controller;

import com.dayflow.common.GlobalExceptionHandler;
import com.dayflow.common.JwtUtil;
import com.dayflow.pojo.vo.TaskVO;
import com.dayflow.service.TaskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TaskController 测试（@WebMvcTest 切片，不连 DB）
 * <p>排除 WebConfig 以避免 JwtInterceptor 注册到 /api/tasks/** 拦截无 token 请求；
 * 用 @MockitoBean 提供 JwtUtil 满足被切片扫描到的 JwtInterceptor 构造依赖。</p>
 *
 * @author jiaxianming
 */
@WebMvcTest(controllers = TaskController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = com.dayflow.config.WebConfig.class))
@Import(GlobalExceptionHandler.class)
class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TaskService taskService;

    /**
     * JwtInterceptor 被 @WebMvcTest 自动扫描，构造需要 JwtUtil；
     * 这里 mock 它只为满足上下文依赖，WebConfig 已排除故拦截器不会进入请求链。
     */
    @MockitoBean
    private JwtUtil jwtUtil;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void createReturns200() throws Exception {
        when(taskService.create(any())).thenReturn(10L);
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"写周报\",\"status\":\"TODO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(10));
    }

    @Test
    void createWithInvalidBodyReturns400() throws Exception {
        // 缺 title -> @Valid 失败 -> 400
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"TODO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void getByIdReturns200() throws Exception {
        TaskVO vo = new TaskVO();
        vo.setId(1L);
        vo.setTitle("测试任务");
        when(taskService.getById(1L)).thenReturn(vo);
        mockMvc.perform(get("/api/tasks/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void completeReturns200() throws Exception {
        mockMvc.perform(patch("/api/tasks/1/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(taskService).complete(1L);
    }
}
