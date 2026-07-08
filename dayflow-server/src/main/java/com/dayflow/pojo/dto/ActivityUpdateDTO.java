package com.dayflow.pojo.dto;

import com.dayflow.pojo.enums.ActivityCategory;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 活动修改入参
 * <p>所有字段可选，仅更新非 null 字段（部分更新语义）。</p>
 *
 * @author jiaxianming
 */
@Data
public class ActivityUpdateDTO {

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
}
