package com.dayflow.common;

/**
 * 当前登录用户上下文（ThreadLocal）
 * <p>JwtInterceptor 校验通过后写入 userId；请求结束后清理。供 T6+ 所有 Service 取当前用户。</p>
 *
 * @author jiaxianming
 */
public class UserContext {

    /**
     * 当前请求线程的用户 ID
     */
    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    /**
     * 设置当前用户 ID
     *
     * @param userId 用户 ID
     */
    public static void setUserId(Long userId) {
        CURRENT.set(userId);
    }

    /**
     * 获取当前用户 ID
     *
     * @return 用户 ID；未登录返回 null
     */
    public static Long getUserId() {
        return CURRENT.get();
    }

    /**
     * 清理当前线程的用户 ID（防内存泄漏）
     */
    public static void clear() {
        CURRENT.remove();
    }
}
