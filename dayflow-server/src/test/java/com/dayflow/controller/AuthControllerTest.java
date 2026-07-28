package com.dayflow.controller;

import com.dayflow.common.GlobalExceptionHandler;
import com.dayflow.common.JwtUtil;
import com.dayflow.pojo.vo.LoginVO;
import com.dayflow.service.UserAuthService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AuthController 测试（@WebMvcTest 切片，不连 DB）
 * <p>沿用 ReportControllerTest 范式：排除 WebConfig 避免 JwtInterceptor 注册到 /api/auth/** 拦截无 token 请求；
 * 用 @MockitoBean 提供 JwtUtil 满足被切片扫描到的 JwtInterceptor 构造依赖。</p>
 *
 * @author jiaxianming
 */
@WebMvcTest(controllers = AuthController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = com.dayflow.config.WebConfig.class))
@Import(GlobalExceptionHandler.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserAuthService userAuthService;

    /**
     * JwtInterceptor 被 @WebMvcTest 自动扫描，构造需要 JwtUtil；
     * 这里 mock 它只为满足上下文依赖，WebConfig 已排除故拦截器不会进入请求链。
     */
    @MockitoBean
    private JwtUtil jwtUtil;

    @Test
    void registerReturns200() throws Exception {
        LoginVO vo = LoginVO.builder().token("tok").userId(1L).username("alice").build();
        when(userAuthService.register(any())).thenReturn(vo);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"pw\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").value("tok"));
        verify(userAuthService).register(any());
    }

    @Test
    void registerWithInvalidBodyReturns400() throws Exception {
        // 缺 username -> @Valid 失败 -> GlobalExceptionHandler 映射为 HTTP 200 + Result.code=400
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"pw\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void loginReturns200() throws Exception {
        LoginVO vo = LoginVO.builder().token("tok").userId(1L).username("alice").build();
        when(userAuthService.login(any())).thenReturn(vo);
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"pw\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").value("tok"));
    }
}
