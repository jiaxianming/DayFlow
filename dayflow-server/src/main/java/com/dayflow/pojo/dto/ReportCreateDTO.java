package com.dayflow.pojo.dto;

import com.dayflow.pojo.enums.ReportType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 报告创建入参（M1 仅写元信息；content 留空，M3 多智能体才填）
 *
 * @author jiaxianming
 */
@Data
public class ReportCreateDTO {

    /**
     * 报告类型（DAILY / WEEKLY）
     */
    @NotNull(message = "报告类型不能为空")
    private ReportType type;

    /**
     * 报告周期起始日
     */
    @NotNull(message = "周期起始日不能为空")
    private LocalDate periodStart;

    /**
     * 报告周期结束日
     */
    @NotNull(message = "周期结束日不能为空")
    private LocalDate periodEnd;

    /**
     * 报告标题（可空，M3 可由智能体生成）
     */
    private String title;
}
