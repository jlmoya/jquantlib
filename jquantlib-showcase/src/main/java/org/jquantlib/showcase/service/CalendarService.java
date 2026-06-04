package org.jquantlib.showcase.service;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.DateGeneration;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.Weekday;
import org.springframework.stereotype.Service;

import org.jquantlib.showcase.dto.CalendarResponse;
import org.jquantlib.showcase.dto.CalendarResponse.Adjustment;
import org.jquantlib.showcase.dto.CalendarResponse.HolidayInfo;
import org.jquantlib.showcase.dto.CalendarResponse.MonthCount;

/**
 * Explores a market calendar with JQuantLib: classifies each day over a horizon
 * into business / weekend / holiday, lists the holidays, counts business days per
 * month, generates a coupon {@link Schedule}, and shows how each business-day
 * convention adjusts a non-business date.
 */
@Service
public class CalendarService {

    public CalendarResponse explore(final String calendarName, final int horizonMonths,
                                    final int scheduleTenorMonths, final int scheduleYears) {
        final Date today = Date.todaysDate();
        return Quant.withEvaluationDate(today, () -> {
            final Calendar cal = ShowcaseCalendars.byName(calendarName);
            final Date end = today.add(new Period(horizonMonths, TimeUnit.Months));

            int business = 0;
            int weekend = 0;
            int holiday = 0;
            final List<HolidayInfo> holidays = new ArrayList<>();
            final List<MonthCount> monthly = new ArrayList<>();

            String currentMonth = null;
            int monthBusiness = 0;

            final Date d = today.clone();
            while (d.le(end)) {
                final boolean isWeekend = d.weekday() == Weekday.Saturday || d.weekday() == Weekday.Sunday;
                if (isWeekend) {
                    weekend++;
                } else if (cal.isHoliday(d)) {
                    holiday++;
                    holidays.add(new HolidayInfo(d.isoDate().toString(), d.weekday().toString()));
                } else {
                    business++;
                }

                final String mk = monthKey(d);
                if (currentMonth == null) {
                    currentMonth = mk;
                }
                if (!mk.equals(currentMonth)) {
                    monthly.add(new MonthCount(currentMonth, monthBusiness));
                    currentMonth = mk;
                    monthBusiness = 0;
                }
                if (!isWeekend && cal.isBusinessDay(d)) {
                    monthBusiness++;
                }
                d.inc();
            }
            if (currentMonth != null) {
                monthly.add(new MonthCount(currentMonth, monthBusiness));
            }

            // Coupon schedule generation.
            final Date maturity = today.add(new Period(scheduleYears, TimeUnit.Years));
            final Schedule schedule = new Schedule(today, maturity,
                    new Period(scheduleTenorMonths, TimeUnit.Months), cal,
                    BusinessDayConvention.ModifiedFollowing, BusinessDayConvention.ModifiedFollowing,
                    DateGeneration.Rule.Forward, false);
            final List<String> scheduleDates = new ArrayList<>();
            for (final Date sd : schedule.dates()) {
                scheduleDates.add(sd.isoDate().toString());
            }

            // Business-day-convention adjustments on the first holiday we found
            // (falls back to the horizon end if the calendar had none).
            final Date sample = holidays.isEmpty()
                    ? end
                    : firstHolidayDate(cal, today, end);
            final List<Adjustment> adjustments = List.of(
                    new Adjustment("Unadjusted", sample.isoDate().toString()),
                    new Adjustment("Following", cal.adjust(sample, BusinessDayConvention.Following).isoDate().toString()),
                    new Adjustment("ModifiedFollowing", cal.adjust(sample, BusinessDayConvention.ModifiedFollowing).isoDate().toString()),
                    new Adjustment("Preceding", cal.adjust(sample, BusinessDayConvention.Preceding).isoDate().toString()),
                    new Adjustment("ModifiedPreceding", cal.adjust(sample, BusinessDayConvention.ModifiedPreceding).isoDate().toString()));

            final String summary = ("%s over the next %d months: %d business days, %d weekend days, %d holidays. "
                    + "Adjustments shown for %s.")
                    .formatted(cal.name(), horizonMonths, business, weekend, holiday, sample.isoDate());
            return new CalendarResponse(cal.name(), summary, business, weekend, holiday,
                    holidays, monthly, scheduleDates, adjustments);
        });
    }

    private static Date firstHolidayDate(final Calendar cal, final Date from, final Date to) {
        final Date d = from.clone();
        while (d.le(to)) {
            final boolean isWeekend = d.weekday() == Weekday.Saturday || d.weekday() == Weekday.Sunday;
            if (!isWeekend && cal.isHoliday(d)) {
                return d.clone();
            }
            d.inc();
        }
        return to;
    }

    private static String monthKey(final Date d) {
        return d.year() + "-" + String.format("%02d", d.month().value());
    }
}
