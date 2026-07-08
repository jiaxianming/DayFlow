package com.dayflow.pojo.vo;

import com.dayflow.pojo.enums.ActivityCategory;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 活动视图对象
 *
 * @author jiaxianming
 */
@Data
public class ActivityVO {

    /**
     * 主键 ID
     */
    private Long id;

    /**
     * 所属用户 ID
     */
    private Long userId;

    /**
     * 活动内容
     */
    private String content;

    /**
     * 活动分类
     */
    private ActivityCategory category;

    /**
     * 发生时间
     */
    private LocalDateTime occurredAt;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
