package com.dayflow.agent.orchestration;

import com.dayflow.pojo.enums.ReportType;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/**
 * 报告周期推导工具：据日期 + 报告类型推导周期起止。
 * <p>DAILY → [date, date]；WEEKLY → date 所在自然周（周一~周日）。
 * 纯计算无副作用，跨月/跨年由 {@link java.time} 保证。
 * 周期推导只在后端做一次（LLM 不算日期），前端仅传 date + type。</p>
 *
 * @author jiaxianming
 */
public final class ReportPeriods {

    /**
     * 据 date 与 type 推导周期起止。
     *
     * @param date 锚点日期（日报为当日；周报为所在周内任一天）
     * @param type 报告类型
     * @return 周期起止
     */
    public static PeriodRange resolve(LocalDate date, ReportType type) {
        if (type == ReportType.WEEKLY) {
            return new PeriodRange(
                    date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                    date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)));
        }
        return new PeriodRange(date, date);
    }

    /**
     * 周期起止（不可变）。
     *
     * @param start 起始日（含）
     * @param end   结束日（含）
     */
    public record PeriodRange(LocalDate start, LocalDate end) {
    }

    private ReportPeriods() {
    }
}
