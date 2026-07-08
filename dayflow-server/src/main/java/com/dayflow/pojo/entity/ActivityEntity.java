package com.dayflow.pojo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dayflow.pojo.enums.ActivityCategory;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工作活动实体
 *
 * @author jiaxianming
 */
@Data
@TableName("activity")
public class ActivityEntity implements Serializable {

    /**
     * 主键 ID（雪花 ID）
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 所属用户 ID
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 活动内容
     */
    @TableField("content")
    private String content;

    /**
     * 活动分类
     */
    @TableField("category")
    private ActivityCategory category;

    /**
     * 发生时间
     */
    @TableField("occurred_at")
    private LocalDateTime occurredAt;

    /**
     * 创建时间（新增时自动填充）
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间（新增/更新时自动填充）
     */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
