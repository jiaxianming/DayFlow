package com.dayflow.agent.model;

/**
 * 活动记录轻量视图（供 LLM，不含 id/userId）
 * <p>由 {@code ReportDataTools} 查询 activity 表后转换而成，仅暴露 LLM 撰写所需的字段；
 * 安全约束：id/userId 绝不进入 prompt 上下文。</p>
 *
 * @param content    活动内容
 * @param category   活动分类
 * @param occurredAt 发生时间
 * @author jiaxianming
 */
public record ActivityItem(String content, String category, String occurredAt) {
}
