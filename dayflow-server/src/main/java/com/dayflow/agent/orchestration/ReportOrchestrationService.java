package com.dayflow.agent.orchestration;

import com.dayflow.pojo.dto.ReportGenerateDTO;
import com.dayflow.pojo.enums.ReportType;

import java.time.LocalDate;

/**
 * 报告编排服务：触发异步报告生成并执行 4 Agent 流水线。
 * <p>编辑部模式：Planner → Collector → Writer ↔ Reviewer（反馈循环，MAX_RETRY=2）。
 * 入口 {@link #generate(ReportGenerateDTO)} 在请求线程内创建 report(GENERATING) + 提交异步编排；
 * 真正的流水线在 {@link #run(Long, Long, LocalDate, ReportType)} 内由专用线程池驱动。</p>
 *
 * @author jiaxianming
 */
public interface ReportOrchestrationService {

    /**
     * 触发报告生成：创建 report(GENERATING) + 提交异步编排 + 立即返回 reportId
     *
     * @param dto 生成入参（type/date）
     * @return 新建报告 id
     */
    Long generate(ReportGenerateDTO dto);

    /**
     * 异步线程内执行 4 Agent 编排（由专用线程池驱动，不对外暴露）
     * <p>userId 经 {@link AgentContext} ThreadLocal 传给 {@code ReportDataTools}；
     * LLM 全程不接触 userId。周期由 generate 经 {@link ReportPeriods} 推导后传入。</p>
     *
     * @param reportId  报告 id
     * @param userId    当前用户 id（经 AgentContext 传给 Tool）
     * @param startDate 周期起始日（含）
     * @param endDate   周期结束日（含）
     * @param type      报告类型
     */
    void run(Long reportId, Long userId, LocalDate startDate, LocalDate endDate, ReportType type);
}
