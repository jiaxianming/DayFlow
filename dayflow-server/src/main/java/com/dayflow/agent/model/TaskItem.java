package com.dayflow.agent.model;

/**
 * 任务轻量视图（供 LLM，不含 id/userId）
 * <p>由 {@code ReportDataTools} 查询 task 表后转换而成，仅暴露 LLM 撰写所需的字段。</p>
 *
 * @param title       任务标题
 * @param status      任务状态
 * @param completedAt 完成时间
 * @author jiaxianming
 */
public record TaskItem(String title, String status, String completedAt) {
}
