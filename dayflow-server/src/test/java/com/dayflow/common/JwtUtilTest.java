package com.dayflow.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * JwtUtil 测试
 * <p>不启动 Spring 容器，直接 new + 反射注入配置</p>
 *
 * @author jiaxianming
 */
class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        // 反射注入配置（跳过 Spring 容器）
        reflectSet(jwtUtil, "secret", "test-secret-test-secret-test-secret-32+");
        reflectSet(jwtUtil, "expiration", 60L);
    }

    /**
     * 签发 → 解析往返：userId / username 一致
     */
    @Test
    void generateAndParseRoundTrip() {
        String token = jwtUtil.generate(1L, "admin");
        assertEquals(1L, jwtUtil.parseUserId(token));
        assertEquals("admin", jwtUtil.parseUsername(token));
    }

    /**
     * 非法 token 解析返回 null（不抛异常）
     */
    @Test
    void invalidTokenReturnsNull() {
        assertNull(jwtUtil.parseUserId("not.a.jwt"));
    }

    /**
     * 过期 token 解析返回 null
     */
    @Test
    void expiredTokenReturnsNull() throws InterruptedException {
        reflectSet(jwtUtil, "expiration", 1L);
        String token = jwtUtil.generate(1L, "admin");
        Thread.sleep(1500);
        assertNull(jwtUtil.parseUserId(token));
    }

    /**
     * 反射设置私有字段
     */
    private void reflectSet(Object target, String field, Object value) {
        try {
            var f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
