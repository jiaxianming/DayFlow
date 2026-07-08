package com.dayflow.pojo.dto;

import com.dayflow.pojo.enums.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 任务创建入参
 *
 * @author jiaxianming
 */
@Data
public class TaskCreateDTO {

    /**
     * 任务标题
     */
    @NotBlank(message = "标题不能为空")
    private String title;

    /**
     * 任务状态（可空，未传时由数据库列 DEFAULT 'TODO' 兜底，实际入库为 TODO 而非 null）
     */
    private TaskStatus status;
}
