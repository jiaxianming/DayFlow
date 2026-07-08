package com.dayflow.pojo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dayflow.pojo.enums.ReportStatus;
import com.dayflow.pojo.enums.ReportType;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 报告实体（M1 只存元信息与最终稿字段；生成逻辑在 M3）
 *
 * @author jiaxianming
 */
@Data
@TableName("report")
public class ReportEntity implements Serializable {

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
     * 报告类型（DAILY / WEEKLY）
     */
    @TableField("type")
    private ReportType type;

    /**
     * 报告周期起始日
     */
    @TableField("period_start")
    private LocalDate periodStart;

    /**
     * 报告周期结束日
     */
    @TableField("period_end")
    private LocalDate periodEnd;

    /**
     * 报告标题
     */
    @TableField("title")
    private String title;

    /**
     * 报告正文（最终稿）
     */
    @TableField("content")
    private String content;

    /**
     * 报告状态
     */
    @TableField("status")
    private ReportStatus status;

    /**
     * 失败时的错误信息
     */
    @TableField("error_msg")
    private String errorMsg;

    /**
     * Token 消耗量
     */
    @TableField("token_usage")
    private Integer tokenUsage;

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
