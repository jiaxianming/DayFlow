package com.dayflow.pojo.query;

import com.dayflow.pojo.enums.TaskStatus;
import lombok.Data;

/**
 * 任务查询条件
 *
 * @author jiaxianming
 */
@Data
public class TaskQuery {

    /**
     * 任务状态
     */
    private TaskStatus status;

    /**
     * 当前页码（从 1 开始）
     */
    private Integer page = 1;

    /**
     * 每页条数
     */
    private Integer size = 20;
}
