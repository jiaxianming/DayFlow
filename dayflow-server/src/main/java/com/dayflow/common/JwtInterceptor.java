package com.dayflow.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 鉴权拦截器
 * <p>校验 Authorization: Bearer &lt;token&gt;，通过则把 userId 注入 {@link UserContext}，否则抛 401。</p>
 *
 * @author jiaxianming
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    /**
     * 预处理：校验 token 并注入 UserContext
     *
     * @param req     请求
     * @param resp    响应
     * @param handler 处理器
     * @return true 放行；false 则由抛出的异常阻断（GlobalExceptionHandler 转 401）
     */
    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse resp, Object handler) {
        String header = req.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        String token = header.substring(7);
        Long userId = jwtUtil.parseUserId(token);
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        UserContext.setUserId(userId);
        return true;
    }

    /**
     * 请求结束：清理 ThreadLocal，防止线程复用导致的用户串号
     *
     * @param req    请求
     * @param resp   响应
     * @param handler 处理器
     * @param ex     异常（如有）
     */
    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse resp, Object handler, Exception ex) {
        UserContext.clear();
    }
}
