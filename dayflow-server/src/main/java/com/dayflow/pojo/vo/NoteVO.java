package com.dayflow.pojo.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学习笔记视图对象
 *
 * @author jiaxianming
 */
@Data
public class NoteVO {

    /**
     * 主键 ID
     */
    private Long id;

    /**
     * 所属用户 ID
     */
    private Long userId;

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

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
