package com.dayflow.service;

import com.dayflow.pojo.enums.AgentName;

/**
 * Agent 轨迹服务：把每步 Agent 的输入/输出摘要、token、耗时、重试次数落 agent_trace 表。
 *
 * @author jiaxianming
 */
public interface AgentTraceService {

    /**
     * 记录一条 Agent 轨迹（每步 Agent 调用后调用）
     *
     * @param reportId      报告 id
     * @param agent         Agent 名称
     * @param step          执行步骤序号（从 1 开始递增）
     * @param inputSummary  输入摘要（已截断）
     * @param outputSummary 输出摘要（已截断）
     * @param tokens        本次调用 token
     * @param latencyMs     本次调用耗时毫秒
     * @param retryCount    重试次数（首次为 0）
     */
    void trace(Long reportId, AgentName agent, int step, String inputSummary,
               String outputSummary, int tokens, long latencyMs, int retryCount);
}
