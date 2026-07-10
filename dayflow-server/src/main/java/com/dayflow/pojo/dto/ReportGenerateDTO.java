package com.dayflow.pojo.dto;

import com.dayflow.pojo.enums.ReportType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 报告生成入参（M3 编排层入口）
 * <p>仅携带报告类型与日期，userId 经 {@code UserContext} 在请求线程内读取，
 * 不在此 DTO 中传递；{@code @Valid} 校验注解由 Task 11 端点层补齐。</p>
 *
 * @author jiaxianming
 */
@Data
public class ReportGenerateDTO {

    /**
     * 报告类型（DAILY / WEEKLY）
     */
    @NotNull(message = "报告类型不能为空")
    private ReportType type;

    /**
     * 报告日期（日报为当日；周报由端点层据此推导周期起止）
     */
    @NotNull(message = "报告日期不能为空")
    private LocalDate date;
}
