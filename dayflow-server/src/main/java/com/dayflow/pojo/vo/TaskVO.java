package com.dayflow.pojo.vo;

import com.dayflow.pojo.enums.TaskStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务视图对象
 *
 * @author jiaxianming
 */
@Data
public class TaskVO {

    /**
     * 主键 ID
     */
    private Long id;

    /**
     * 所属用户 ID
     */
    private Long userId;

    /**
     * 任务标题
     */
    private String title;

    /**
     * 任务状态
     */
    private TaskStatus status;

    /**
     * 完成时间
     */
    private LocalDateTime completedAt;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
