package com.dayflow.service.impl;

import com.dayflow.mapper.AgentTraceMapper;
import com.dayflow.pojo.entity.AgentTraceEntity;
import com.dayflow.pojo.enums.AgentName;
import com.dayflow.service.AgentTraceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Agent 轨迹服务实现：直接走 Mapper 落库，每条独立小事务（前端轮询能渐进看到轨迹）。
 *
 * @author jiaxianming
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentTraceServiceImpl implements AgentTraceService {

    private static final int MAX_SUMMARY_LEN = 500;

    private final AgentTraceMapper traceMapper;

    @Override
    public void trace(Long reportId, AgentName agent, int step, String inputSummary,
                      String outputSummary, int tokens, long latencyMs, int retryCount) {
        AgentTraceEntity entity = new AgentTraceEntity();
        entity.setReportId(reportId);
        entity.setAgentName(agent);
        entity.setStep(step);
        entity.setInputSummary(truncate(inputSummary));
        entity.setOutputSummary(truncate(outputSummary));
        entity.setTokens(tokens);
        entity.setLatencyMs((int) latencyMs);
        entity.setRetryCount(retryCount);
        traceMapper.insert(entity);
        log.info("trace 落库 reportId={} agent={} step={} tokens={} retry={}",
                reportId, agent, step, tokens, retryCount);
    }

    /**
     * 摘要截断：超过 500 字符截断到 500，null 原样返回。
     *
     * @param text 原始摘要
     * @return 截断后的摘要
     */
    private String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= MAX_SUMMARY_LEN ? text : text.substring(0, MAX_SUMMARY_LEN);
    }
}
