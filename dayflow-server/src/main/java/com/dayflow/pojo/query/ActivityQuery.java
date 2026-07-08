package com.dayflow.pojo.query;

import com.dayflow.pojo.enums.ActivityCategory;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 活动查询条件
 *
 * @author jiaxianming
 */
@Data
public class ActivityQuery {

    /**
     * 起始时间（occurredAt >= startTime）
     */
    private LocalDateTime startTime;

    /**
     * 截止时间（occurredAt <= endTime）
     */
    private LocalDateTime endTime;

    /**
     * 活动分类
     */
    private ActivityCategory category;

    /**
     * 当前页码（从 1 开始）
     */
    private Integer page = 1;

    /**
     * 每页条数
     */
    private Integer size = 20;
}
