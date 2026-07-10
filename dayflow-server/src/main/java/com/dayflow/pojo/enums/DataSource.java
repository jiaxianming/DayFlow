package com.dayflow.pojo.enums;

/**
 * 报告板块数据源类型
 * <p>由 Planner 在 {@code PlanSection.dataSource} 中标注，Collector 据此决定
 * 调用哪个 {@code @Tool}（ACTIVITY→活动查询、TASK→任务查询、NOTE→笔记查询）。</p>
 *
 * @author jiaxianming
 */
public enum DataSource {
    /**
     * 工作活动记录
     */
    ACTIVITY,
    /**
     * 学习笔记
     */
    NOTE,
    /**
     * 任务
     */
    TASK
}
