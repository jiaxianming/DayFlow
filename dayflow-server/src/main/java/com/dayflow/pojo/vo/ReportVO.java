package com.dayflow.pojo.vo;

import com.dayflow.pojo.enums.ReportStatus;
import com.dayflow.pojo.enums.ReportType;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 报告视图对象
 *
 * @author jiaxianming
 */
@Data
public class ReportVO {

    /**
     * 主键 ID
     */
    private Long id;

    /**
     * 所属用户 ID
     */
    private Long userId;

    /**
     * 报告类型（DAILY / WEEKLY）
     */
    private ReportType type;

    /**
     * 报告周期起始日
     */
    private LocalDate periodStart;

    /**
     * 报告周期结束日
     */
    private LocalDate periodEnd;

    /**
     * 报告标题
     */
    private String title;

    /**
     * 报告正文（最终稿，M1 阶段可能为空）
     */
    private String content;

    /**
     * 报告状态
     */
    private ReportStatus status;

    /**
     * 失败时的错误信息
     */
    private String errorMsg;

    /**
     * Token 消耗量
     */
    private Integer tokenUsage;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
