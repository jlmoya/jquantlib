package org.jquantlib.showcase.dto;

import java.util.List;

/**
 * Business-day analysis for a chosen market calendar over a horizon: holiday
 * list, business/weekend/holiday counts, a monthly business-day histogram, a
 * generated coupon schedule, and business-day-convention adjustment examples.
 */
public record CalendarResponse(
        String calendar,
        String summary,
        int businessDays,
        int weekendDays,
        int holidays,
        List<HolidayInfo> holidayList,
        List<MonthCount> monthlyBusinessDays,
        List<String> schedule,
        List<Adjustment> adjustments) {

    public record HolidayInfo(String date, String weekday) {
    }

    public record MonthCount(String month, int businessDays) {
    }

    public record Adjustment(String convention, String result) {
    }
}
