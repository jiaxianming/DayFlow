package com.dayflow.agent.model;

/**
 * Agent 调用结果包装：结构化产出 + token + 耗时
 * <p>由 {@code AgentInvoker.invoke} 统一返回，4 个 Agent 通用；
 * {@code tokens} 取自 {@code ChatResponse.metadata().usage().totalTokens()}，
 * {@code latencyMs} 为本次 ChatClient 调用端到端耗时，两者供
 * {@code AgentTraceService} 写入 {@code agent_trace} 表用于可视化与成本核算。</p>
 *
 * @param payload   Agent 产出的结构化对象（ReportPlan / CollectedMaterial / DraftReport / ReviewResult）
 * @param tokens    本次调用消耗 token（取自 ChatResponse.metadata().usage()）
 * @param latencyMs 本次调用耗时毫秒
 * @author jiaxianming
 */
public record AgentResult<T>(T payload, int tokens, long latencyMs) {
}
