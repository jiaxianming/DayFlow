package com.dayflow.agent.model;

import lombok.Data;

import java.util.List;

/**
 * 规划产出的报告计划
 * <p>Planner 的结构化产出，列出报告标题与各板块；编排层将其下发给 Collector 作为采集依据。</p>
 *
 * @author jiaxianming
 */
@Data
public class ReportPlan {

    /**
     * 报告标题
     */
    private String title;

    /**
     * 报告板块清单
     */
    private List<PlanSection> sections;
}
