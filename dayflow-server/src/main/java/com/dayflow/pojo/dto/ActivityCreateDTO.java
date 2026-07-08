package com.dayflow.pojo.dto;

import com.dayflow.pojo.enums.ActivityCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 活动创建入参
 *
 * @author jiaxianming
 */
@Data
public class ActivityCreateDTO {

    /**
     * 活动内容
     */
    @NotBlank(message = "内容不能为空")
    private String content;

    /**
     * 活动分类
     */
    @NotNull(message = "分类不能为空")
    private ActivityCategory category;

    /**
     * 发生时间（可选，不传则由前端补全或留空）
     */
    private LocalDateTime occurredAt;
}
