package com.dayflow.pojo.enums;

/**
 * Agent 名称（编辑部模式 4 Agent）
 *
 * @author jiaxianming
 */
public enum AgentName {
    /**
     * 规划者：分析输入、制定编排方案
     */
    PLANNER,
    /**
     * 收集者：拉取活动/任务/笔记数据
     */
    COLLECTOR,
    /**
     * 撰写者：根据数据生成报告草案
     */
    WRITER,
    /**
     * 审核者：检查报告质量并反馈
     */
    REVIEWER
}
