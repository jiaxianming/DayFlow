package com.dayflow.pojo.dto;

import com.dayflow.pojo.enums.ReportType;
import lombok.Data;

import java.time.LocalDate;

/**
 * 报告生成入参（M3 编排层入口）
 * <p>仅携带报告类型与日期，userId 经 {@code UserContext} 在请求线程内读取，
 * 不在此 DTO 中传递；本骨架在 Task 10 先建立以解编译依赖，
 * {@code @Valid} 校验注解留待 Task 11（端点层）补齐。</p>
 *
 * @author jiaxianming
 */
@Data
public class ReportGenerateDTO {

    /**
     * 报告类型（DAILY / WEEKLY）
     */
    private ReportType type;

    /**
     * 报告日期（日报为当日；周报由端点层据此推导周期起止）
     */
    private LocalDate date;
}
