package com.dayflow.pojo.query;

import lombok.Data;

/**
 * 学习笔记查询条件
 *
 * @author jiaxianming
 */
@Data
public class NoteQuery {

    /**
     * 标签（LIKE 模糊匹配，可空）
     */
    private String tags;

    /**
     * 当前页码（从 1 开始）
     */
    private Integer page = 1;

    /**
     * 每页条数
     */
    private Integer size = 20;
}
