package com.dayflow.pojo.dto;

import com.dayflow.pojo.enums.TaskStatus;
import lombok.Data;

/**
 * 任务修改入参
 * <p>所有字段可选，仅更新非 null 字段（部分更新语义）。</p>
 *
 * @author jiaxianming
 */
@Data
public class TaskUpdateDTO {

    /**
     * 任务标题
     */
    private String title;

    /**
     * 任务状态
     */
    private TaskStatus status;
}
