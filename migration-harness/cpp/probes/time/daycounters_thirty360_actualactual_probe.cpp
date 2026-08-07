// migration-harness/cpp/probes/time/daycounters_thirty360_actualactual_probe.cpp
//
// Emits dayCount() and yearFraction() for EVERY Thirty360 and ActualActual
// convention over a date grid built to hit the exact inputs on which the
// conventions differ from each other.
//
// WHY THIS EXISTS: JQuantLib realises each C++ `DayCounter::Impl` subclass
// (Thirty360::{US,ISMA,EU,IT,ISDA,NASD}_Impl, ActualActual::{ISMA,Old_ISMA,
// ISDA,AFB}_Impl) as a private inner class selected by the same `Convention`
// enum. Those inner classes are only legitimately absent from the coverage
// audit if the behaviour they carry is pinned to C++. Before this probe the
// Italian and NASD conventions appeared in exactly one Java test — a
// yearFraction/yearFractionToDate ROUND-TRIP, which is self-consistent for any
// monotone day count and therefore proves nothing about the day count itself.
//
// The grid is every ordered pair (d1 <= d2) from a curated date list. The list
// is chosen so that each convention's distinguishing branch is reached:
//   * 31sts of long months            -> US/ISMA/EU/NASD 31->30 clamps
//   * 28/29 February in leap and
//     non-leap years                  -> US and 30/360-ISDA last-of-February
//                                        rules, IT's "February day > 27" rule,
//                                        Act/Act AFB's leap-denominator rule
//   * a d2 == 31st with d1 < 30       -> NASD's roll-to-1st-of-next-month
//   * spans crossing 1 January        -> Act/Act ISDA's per-year split
//   * spans of several years          -> Act/Act AFB's whole-year loop
//
// Thirty360 ISDA is emitted twice: once with a null termination date and once
// with a termination date that IS one of the grid dates, because the
// termination date suppresses the last-of-February adjustment on d2 only for
// that one date.
//
// ActualActual ISMA is emitted both without a schedule (which selects C++
// Old_ISMA_Impl) and with semiannual/annual schedules (which select ISMA_Impl)
// — the two are different implementations behind one enumerator.

#include <ql/version.hpp>

#include <ql/time/date.hpp>
#include <ql/time/schedule.hpp>
#include <ql/time/calendars/nullcalendar.hpp>
#include <ql/time/daycounters/actualactual.hpp>
#include <ql/time/daycounters/thirty360.hpp>

#include <cstdio>
#include <string>
#include <vector>

#include "common.hpp"

using namespace QuantLib;
using namespace jqml_harness;

namespace {

// Curated grid; see the header comment for why each date is here.
std::vector<Date> gridDates() {
    return {
        Date(1, January, 2020),   // year boundary
        Date(31, January, 2020),  // 31st
        Date(28, February, 2020), // penultimate day of a leap February
        Date(29, February, 2020), // last of February, leap
        Date(1, March, 2020),
        Date(30, March, 2020),    // 30th, so d1 >= 30 for the NASD branch
        Date(31, March, 2020),    // 31st
        Date(30, April, 2020),
        Date(31, May, 2020),
        Date(15, June, 2020),     // mid-month, no clamp anywhere
        Date(30, June, 2020),
        Date(31, July, 2020),
        Date(31, August, 2020),
        Date(30, September, 2020),
        Date(31, October, 2020),
        Date(30, November, 2020),
        Date(31, December, 2020), // used as the ISDA termination date below
        Date(1, January, 2021),
        Date(27, February, 2021), // just below IT's "> 27" threshold
        Date(28, February, 2021), // last of February, non-leap
        Date(1, March, 2021),
        Date(31, January, 2022),
        Date(28, February, 2022),
        Date(31, March, 2023),
        Date(29, February, 2024), // leap again, several years out
        Date(31, December, 2024)
    };
}

// Dates are emitted as "YYYY-MM-DD"; the grid is large enough that the object
// form {y,m,d} would triple the reference file for no added information.
json dateJson(const Date& d) {
    char buf[16];
    std::snprintf(buf, sizeof(buf), "%04d-%02d-%02d",
                  static_cast<int>(d.year()),
                  static_cast<int>(d.month()),
                  static_cast<int>(d.dayOfMonth()));
    return json(std::string(buf));
}

// dayCount + yearFraction over the whole grid, no reference period.
void addDayCounter(ReferenceWriter& out, const char* key, const DayCounter& dc) {
    const auto dates = gridDates();
    json rows = json::array();
    for (std::size_t i = 0; i < dates.size(); ++i) {
        for (std::size_t j = i; j < dates.size(); ++j) {
            rows.push_back(json{{"d1", dateJson(dates[i])},
                                {"d2", dateJson(dates[j])},
                                {"dayCount", dc.dayCount(dates[i], dates[j])},
                                {"yearFraction", dc.yearFraction(dates[i], dates[j])}});
        }
    }
    out.addCase(key, json{{"name", dc.name()}, {"grid", "curated"}},
                json{{"name", dc.name()}, {"rows", rows}});
}

// An alias enumerator routes to the SAME C++ Impl object as its canonical
// sibling (see the switch in thirty360.cpp / actualactual.cpp), so re-emitting
// the whole grid for it would only duplicate bytes. Recording the name plus
// which case it aliases lets the Java test assert both that the name matches
// and that the alias produces values identical to the canonical convention —
// which is exactly the property the C++ switch guarantees.
void addAlias(ReferenceWriter& out, const char* key, const char* aliasOf, const DayCounter& dc) {
    out.addCase(key, json{{"name", dc.name()}, {"aliasOf", aliasOf}},
                json{{"name", dc.name()}, {"aliasOf", aliasOf}});
}

// Act/Act ISMA without a schedule (C++ Old_ISMA_Impl) is driven by the
// reference period, so the grid alone would leave its interesting branches
// (short first coupon, long first coupon, span past the reference end)
// unreached. These cases carry explicit reference periods.
void addOldIsmaWithReferencePeriods(ReferenceWriter& out) {
    const ActualActual dc(ActualActual::ISMA);
    struct Row { Date d1, d2, refStart, refEnd; };
    const std::vector<Row> rows = {
        // regular semiannual coupon: refStart <= d1 <= d2 <= refEnd
        {Date(1, November, 2003), Date(1, May, 2004), Date(1, November, 2003), Date(1, May, 2004)},
        {Date(1, November, 2003), Date(1, February, 2004), Date(1, November, 2003), Date(1, May, 2004)},
        // short first coupon
        {Date(1, February, 1999), Date(1, July, 1999), Date(1, July, 1998), Date(1, July, 1999)},
        // long first coupon: d1 < refStart
        {Date(15, August, 2002), Date(15, July, 2003), Date(15, January, 2003), Date(15, July, 2003)},
        // short final coupon
        {Date(31, July, 1999), Date(1, July, 2000), Date(1, July, 1999), Date(1, July, 2000)},
        // span running past refEnd (the multi-period accumulation branch)
        {Date(1, January, 2020), Date(1, January, 2023), Date(1, January, 2020), Date(1, July, 2020)},
        // annual reference period
        {Date(1, January, 2020), Date(1, January, 2021), Date(1, January, 2020), Date(1, January, 2021)},
        // no reference period at all -> falls back to (d1, d2)
        {Date(1, January, 2020), Date(1, January, 2021), Date(), Date()},
        {Date(1, February, 2020), Date(15, March, 2020), Date(), Date()}
    };
    json arr = json::array();
    for (const auto& r : rows) {
        arr.push_back(json{{"d1", dateJson(r.d1)},
                           {"d2", dateJson(r.d2)},
                           {"refStart", r.refStart == Date() ? json(nullptr) : dateJson(r.refStart)},
                           {"refEnd", r.refEnd == Date() ? json(nullptr) : dateJson(r.refEnd)},
                           {"yearFraction",
                            dc.yearFraction(r.d1, r.d2, r.refStart, r.refEnd)}});
    }
    out.addCase("actualactual_isma_reference_periods",
                json{{"name", dc.name()}},
                json{{"name", dc.name()}, {"rows", arr}});
}

// Act/Act ISMA WITH a schedule selects C++ ISMA_Impl, a different class from
// Old_ISMA_Impl above. Emitted for a semiannual and an annual schedule.
void addSchedISMA(ReferenceWriter& out,
                  const char* key,
                  const Date& start,
                  const Date& end,
                  const Period& tenor) {
    const Schedule schedule = MakeSchedule()
                                  .from(start)
                                  .to(end)
                                  .withTenor(tenor)
                                  .withCalendar(NullCalendar())
                                  .withConvention(Unadjusted)
                                  .backwards();
    const ActualActual dc(ActualActual::ISMA, schedule);

    json scheduleDates = json::array();
    for (Size i = 0; i < schedule.size(); ++i)
        scheduleDates.push_back(dateJson(schedule.date(i)));

    json rows = json::array();
    // every ordered pair of schedule dates, plus a mid-period pair per period
    for (Size i = 0; i < schedule.size(); ++i) {
        for (Size j = i; j < schedule.size(); ++j) {
            rows.push_back(json{{"d1", dateJson(schedule.date(i))},
                                {"d2", dateJson(schedule.date(j))},
                                {"yearFraction", dc.yearFraction(schedule.date(i), schedule.date(j))}});
        }
    }
    for (Size i = 0; i + 1 < schedule.size(); ++i) {
        const Date mid = schedule.date(i) + ((schedule.date(i + 1) - schedule.date(i)) / 2);
        rows.push_back(json{{"d1", dateJson(schedule.date(i))},
                            {"d2", dateJson(mid)},
                            {"yearFraction", dc.yearFraction(schedule.date(i), mid)}});
        rows.push_back(json{{"d1", dateJson(mid)},
                            {"d2", dateJson(schedule.date(i + 1))},
                            {"yearFraction", dc.yearFraction(mid, schedule.date(i + 1))}});
    }

    out.addCase(key,
                json{{"name", dc.name()},
                     {"scheduleStart", dateJson(start)},
                     {"scheduleEnd", dateJson(end)},
                     {"tenorMonths", static_cast<int>(tenor.length()) *
                                         (tenor.units() == Years ? 12 : 1)}},
                json{{"name", dc.name()},
                     {"scheduleDates", scheduleDates},
                     {"rows", rows}});
}

} // namespace

int main() {
    ReferenceWriter out("time/daycounters/thirty360_actualactual", QL_VERSION,
                        "daycounters_thirty360_actualactual_probe");

    // --- Thirty360: one full grid per distinct Impl --------------------------
    addDayCounter(out, "thirty360_usa", Thirty360(Thirty360::USA));          // US_Impl
    addDayCounter(out, "thirty360_bond_basis", Thirty360(Thirty360::BondBasis)); // ISMA_Impl
    addDayCounter(out, "thirty360_european", Thirty360(Thirty360::European));    // EU_Impl
    addDayCounter(out, "thirty360_italian", Thirty360(Thirty360::Italian));      // IT_Impl
    addDayCounter(out, "thirty360_isda_no_termination", Thirty360(Thirty360::ISDA)); // ISDA_Impl
    // 31-Dec-2020 is in the grid, so the termination-date branch is reached.
    addDayCounter(out, "thirty360_isda_termination_20201231",
                  Thirty360(Thirty360::ISDA, Date(31, December, 2020)));
    addDayCounter(out, "thirty360_nasd", Thirty360(Thirty360::NASD));        // NASD_Impl
    // aliases: thirty360.cpp:35-55 routes these to the Impl named in aliasOf
    addAlias(out, "thirty360_isma", "thirty360_bond_basis", Thirty360(Thirty360::ISMA));
    addAlias(out, "thirty360_eurobond_basis", "thirty360_european",
             Thirty360(Thirty360::EurobondBasis));
    addAlias(out, "thirty360_german_no_termination", "thirty360_isda_no_termination",
             Thirty360(Thirty360::German));

    // --- ActualActual: one full grid per distinct Impl -----------------------
    addDayCounter(out, "actualactual_isda", ActualActual(ActualActual::ISDA)); // ISDA_Impl
    addDayCounter(out, "actualactual_afb", ActualActual(ActualActual::AFB));   // AFB_Impl
    // ISMA with no schedule -> Old_ISMA_Impl; over the plain grid the reference
    // period defaults to (d1, d2), which still exercises the short-period path.
    addDayCounter(out, "actualactual_isma_no_schedule", ActualActual(ActualActual::ISMA));
    // aliases: actualactual.cpp routes these to the Impl named in aliasOf
    addAlias(out, "actualactual_historical", "actualactual_isda",
             ActualActual(ActualActual::Historical));
    addAlias(out, "actualactual_actual365", "actualactual_isda",
             ActualActual(ActualActual::Actual365));
    addAlias(out, "actualactual_euro", "actualactual_afb", ActualActual(ActualActual::Euro));
    addAlias(out, "actualactual_bond_no_schedule", "actualactual_isma_no_schedule",
             ActualActual(ActualActual::Bond));
    addOldIsmaWithReferencePeriods(out);

    // ISMA with a schedule -> ISMA_Impl
    addSchedISMA(out, "actualactual_isma_semiannual_schedule",
                 Date(1, January, 2020), Date(1, January, 2023), Period(6, Months));
    addSchedISMA(out, "actualactual_isma_annual_schedule",
                 Date(15, March, 2019), Date(15, March, 2024), Period(1, Years));

    out.write();
    return 0;
}
