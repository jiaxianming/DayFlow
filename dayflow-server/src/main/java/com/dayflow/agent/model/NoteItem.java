package com.dayflow.agent.model;

/**
 * 笔记轻量视图（供 LLM，不含 id/userId）
 * <p>由 {@code ReportDataTools} 查询 note 表后转换而成，仅暴露 LLM 撰写所需的字段。</p>
 *
 * @param title   笔记标题
 * @param tags    笔记标签（逗号分隔）
 * @param content 笔记内容
 * @author jiaxianming
 */
public record NoteItem(String title, String tags, String content) {
}
