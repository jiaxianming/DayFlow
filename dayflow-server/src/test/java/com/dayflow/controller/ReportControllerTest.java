package com.dayflow.controller;

import com.dayflow.agent.orchestration.ReportOrchestrationService;
import com.dayflow.common.GlobalExceptionHandler;
import com.dayflow.common.JwtUtil;
import com.dayflow.pojo.vo.AgentTraceVO;
import com.dayflow.pojo.vo.ReportVO;
import com.dayflow.service.ReportService;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ReportController 测试（@WebMvcTest 切片，不连 DB）
 * <p>沿用 NoteControllerTest 范式：排除 WebConfig 避免 JwtInterceptor 注册；
 * 用 @MockitoBean 提供 JwtUtil 满足被切片扫描到的 JwtInterceptor 构造依赖。</p>
 *
 * @author jiaxianming
 */
@WebMvcTest(controllers = ReportController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = com.dayflow.config.WebConfig.class))
@Import(GlobalExceptionHandler.class)
class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportService reportService;

    /**
     * M3 编排服务（generate 端点依赖），切片测试用 mock 隔离真实 4 Agent 流水线。
     */
    @MockitoBean
    private ReportOrchestrationService orchestrationService;

    /**
     * JwtInterceptor 被 @WebMvcTest 自动扫描，构造需要 JwtUtil；
     * 这里 mock 它只为满足上下文依赖，WebConfig 已排除故拦截器不会进入请求链。
     */
    @MockitoBean
    private JwtUtil jwtUtil;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void createReturns200() throws Exception {
        when(reportService.create(any())).thenReturn(10L);
        mockMvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DAILY\",\"periodStart\":\"2026-07-08\",\"periodEnd\":\"2026-07-08\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(10));
    }

    @Test
    void createWithInvalidBodyReturns400() throws Exception {
        // 缺 type -> @Valid 失败 -> 400
        mockMvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"periodStart\":\"2026-07-08\",\"periodEnd\":\"2026-07-08\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void getByIdReturns200() throws Exception {
        ReportVO vo = new ReportVO();
        vo.setId(1L);
        vo.setTitle("测试报告");
        when(reportService.getById(1L)).thenReturn(vo);
        mockMvc.perform(get("/api/reports/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void tracesReturns200() throws Exception {
        AgentTraceVO t = new AgentTraceVO();
        t.setId(1L);
        t.setReportId(1L);
        when(reportService.listTraces(1L)).thenReturn(List.of(t));
        mockMvc.perform(get("/api/reports/1/traces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].reportId").value(1));
        // 验证 controller 确实调用了 listTraces（断言链路打通）
        verify(reportService).listTraces(1L);
    }

    @Test
    void generateReturns200WithReportId() throws Exception {
        when(orchestrationService.generate(any())).thenReturn(42L);
        mockMvc.perform(post("/api/reports/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DAILY\",\"date\":\"2026-07-09\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(42));
        verify(orchestrationService).generate(any());
    }

    @Test
    void generateWithInvalidBodyReturns400() throws Exception {
        // 缺 date -> @Valid 失败 -> GlobalExceptionHandler 映射为 HTTP 200 + Result.code=400
        mockMvc.perform(post("/api/reports/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DAILY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }
}
