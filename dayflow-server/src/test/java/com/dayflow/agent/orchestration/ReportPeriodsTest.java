package com.dayflow.agent.orchestration;

import com.dayflow.pojo.enums.ReportType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ReportPeriods 周期推导单测。
 *
 * @author jiaxianming
 */
class ReportPeriodsTest {

    @Test
    void dailyReturnsSameDay() {
        ReportPeriods.PeriodRange pr = ReportPeriods.resolve(LocalDate.of(2026, 7, 28), ReportType.DAILY);
        assertEquals(LocalDate.of(2026, 7, 28), pr.start());
        assertEquals(LocalDate.of(2026, 7, 28), pr.end());
    }

    @Test
    void weeklyFromTuesdayResolvesMondayToSunday() {
        // 2026-07-28 是周二 → 所在自然周 周一 07-27 ~ 周日 08-02
        ReportPeriods.PeriodRange pr = ReportPeriods.resolve(LocalDate.of(2026, 7, 28), ReportType.WEEKLY);
        assertEquals(LocalDate.of(2026, 7, 27), pr.start());
        assertEquals(LocalDate.of(2026, 8, 2), pr.end());
    }

    @Test
    void weeklyFromSundayKeepsSameWeek() {
        // 2026-08-02 是周日（本周最后一天）→ 仍是 07-27 ~ 08-02，不跳到下周
        ReportPeriods.PeriodRange pr = ReportPeriods.resolve(LocalDate.of(2026, 8, 2), ReportType.WEEKLY);
        assertEquals(LocalDate.of(2026, 7, 27), pr.start());
        assertEquals(LocalDate.of(2026, 8, 2), pr.end());
    }

    @Test
    void weeklyFromMondayResolvesSameWeek() {
        // 2026-07-27 是周一（本周第一天）
        ReportPeriods.PeriodRange pr = ReportPeriods.resolve(LocalDate.of(2026, 7, 27), ReportType.WEEKLY);
        assertEquals(LocalDate.of(2026, 7, 27), pr.start());
        assertEquals(LocalDate.of(2026, 8, 2), pr.end());
    }

    @Test
    void weeklyCrossesMonthBoundary() {
        // 2026-07-31 是周五 → 所在周 07-27 ~ 08-02（跨 7/8 月）
        ReportPeriods.PeriodRange pr = ReportPeriods.resolve(LocalDate.of(2026, 7, 31), ReportType.WEEKLY);
        assertEquals(LocalDate.of(2026, 7, 27), pr.start());
        assertEquals(LocalDate.of(2026, 8, 2), pr.end());
    }
}
