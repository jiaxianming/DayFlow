package com.dayflow.agent.orchestration;

/**
 * Agent 执行上下文：在异步线程内传递 userId 给 {@code ReportDataTools}。
 * <p>与请求线程的 {@code UserContext} 区分：异步线程不经 JwtInterceptor，
 * UserContext 不会被设置；故编排层在 run 方法体内手动 {@link #setUserId(Long)}，
 * Tool 同线程 {@link #getUserId()} 读取，run 结束 {@link #clear()}。</p>
 * <p>核心安全约束：userId 经此 ThreadLocal 传递，LLM 全程不接触 userId，
 * 杜绝 LLM 幻觉导致越权拉取他人数据。</p>
 *
 * @author jiaxianming
 */
public final class AgentContext {

    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private AgentContext() {
    }

    /**
     * 设置当前 Agent 执行流的 userId
     *
     * @param userId 当前用户 id
     */
    public static void setUserId(Long userId) {
        CURRENT.set(userId);
    }

    /**
     * @return 当前 Agent 执行流的 userId（Tool 读取），未设置返回 null
     */
    public static Long getUserId() {
        return CURRENT.get();
    }

    /**
     * 清理 ThreadLocal，防止线程池复用导致的跨任务污染
     */
    public static void clear() {
        CURRENT.remove();
    }
}
