package com.dayflow.agent.model;

import com.dayflow.pojo.enums.ReportType;
import lombok.Data;

import java.time.LocalDate;

/**
 * 规划输入（编排层构造，发给 Planner）。
 * <p>注意：不含 userId —— userId 绝不进 prompt，仅经 {@code AgentContext} 供 Tool 使用，
 * 杜绝 LLM 幻觉导致越权拉取他人数据。</p>
 *
 * @author jiaxianming
 */
@Data
public class PlanInput {

    /**
     * 周期起始日（日报 == endDate；周报为所在周周一）
     */
    private LocalDate startDate;

    /**
     * 周期结束日（日报 == startDate；周报为所在周周日）
     */
    private LocalDate endDate;

    /**
     * 报告类型（日报 / 周报）
     */
    private ReportType reportType;

    /**
     * 数据提示：编排层先 count 各源条数，形如「活动 3 条 / 任务 2 条 / 笔记 1 条」，
     * 全 0 时为「当日/本周无任何记录」
     */
    private String dataHint;
}
