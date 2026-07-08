package com.dayflow.pojo.dto;

import lombok.Data;

/**
 * 学习笔记修改入参
 * <p>所有字段可选，仅更新非 null 字段（部分更新语义）。</p>
 *
 * @author jiaxianming
 */
@Data
public class NoteUpdateDTO {

    /**
     * 笔记标题
     */
    private String title;

    /**
     * 笔记正文
     */
    private String content;

    /**
     * 标签（逗号分隔）
     */
    private String tags;
}
