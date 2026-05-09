// migration-harness/cpp/probes/time/cds_schedule_probe.cpp
//
// Probe for Phase 3c L0 A.1: DateGeneration.CDS / CDS2015 / OldCDS schedule
// generation + previousTwentieth / nextTwentieth helpers.
//
// Captures the schedule date list produced by Schedule(...) under the new
// CDS-family rules, plus the previousTwentieth / nextTwentieth output for a
// few sample dates. The Java port re-reproduces the same schedule and asserts
// equality.

#include <ql/version.hpp>

#include <ql/time/calendars/weekendsonly.hpp>
#include <ql/time/calendars/target.hpp>
#include <ql/time/period.hpp>
#include <ql/time/schedule.hpp>
#include <ql/instruments/creditdefaultswap.hpp>

#include "common.hpp"

#include <sstream>

using namespace QuantLib;
using namespace jqml_harness;

namespace {

std::string isoDate(const Date& d) {
    std::ostringstream os;
    os.fill('0');
    os << d.year() << "-";
    os.width(2); os << static_cast<int>(d.month()) << "-";
    os.width(2); os << d.dayOfMonth();
    return os.str();
}

json scheduleDates(const Schedule& s) {
    json arr = json::array();
    for (Size i = 0; i < s.size(); ++i) {
        arr.push_back(isoDate(s[i]));
    }
    return arr;
}

void runScheduleCase(ReferenceWriter& out,
                     const std::string& name,
                     const Date& effective,
                     const Date& termination,
                     Period tenor,
                     DateGeneration::Rule rule) {
    Calendar cal = WeekendsOnly();
    Schedule schedule(effective, termination, tenor, cal,
                      Following, Unadjusted, rule, false);

    json inputs = {
        {"effective", isoDate(effective)},
        {"termination", isoDate(termination)},
        {"tenor_months", tenor.length() * (tenor.units() == Years ? 12 : 1)},
        {"rule", static_cast<int>(rule)}
    };
    json expected = {
        {"dates", scheduleDates(schedule)}
    };
    out.addCase(name, inputs, expected);
}

void runTwentiethCase(ReferenceWriter& out,
                      const std::string& name,
                      const Date& d,
                      DateGeneration::Rule rule) {
    Date prev = previousTwentieth(d, rule);
    json inputs = {
        {"date", isoDate(d)},
        {"rule", static_cast<int>(rule)}
    };
    json expected = {
        {"previous_twentieth", isoDate(prev)}
    };
    out.addCase(name, inputs, expected);
}

void runCdsMaturityCase(ReferenceWriter& out,
                        const std::string& name,
                        const Date& tradeDate,
                        Period tenor,
                        DateGeneration::Rule rule) {
    Date m = cdsMaturity(tradeDate, tenor, rule);
    json inputs = {
        {"trade_date", isoDate(tradeDate)},
        {"tenor_months", tenor.length() * (tenor.units() == Years ? 12 : 1)},
        {"rule", static_cast<int>(rule)}
    };
    json expected = {
        {"cds_maturity", isoDate(m)}
    };
    out.addCase(name, inputs, expected);
}

} // anonymous

int main() {
    ReferenceWriter out("time/cds_schedule",
                        QL_VERSION, "cds_schedule_probe");

    // Schedule cases — CDS and CDS2015 rules. Trade date Friday 6-Mar-2026
    // (matches testDefaultConventions C++ scenario).
    runScheduleCase(out, "schedule_cds_5y_quarterly_2026",
                    Date(6, March, 2026),
                    Date(20, June, 2031),
                    Period(3, Months),
                    DateGeneration::CDS);

    runScheduleCase(out, "schedule_cds2015_5y_quarterly_2026",
                    Date(6, March, 2026),
                    Date(20, June, 2031),
                    Period(3, Months),
                    DateGeneration::CDS2015);

    runScheduleCase(out, "schedule_oldcds_5y_quarterly",
                    Date(15, June, 2008),
                    Date(20, June, 2013),
                    Period(3, Months),
                    DateGeneration::OldCDS);

    // previousTwentieth cases.
    runTwentiethCase(out, "prev20_cds_jan_15",
                     Date(15, January, 2026), DateGeneration::CDS);
    runTwentiethCase(out, "prev20_cds_dec_31",
                     Date(31, December, 2025), DateGeneration::CDS);
    runTwentiethCase(out, "prev20_cds2015_dec_25",
                     Date(25, December, 2025), DateGeneration::CDS2015);
    runTwentiethCase(out, "prev20_oldcds_aug_5",
                     Date(5, August, 2025), DateGeneration::OldCDS);

    // cdsMaturity cases.
    runCdsMaturityCase(out, "cdsmat_5y_cds",
                       Date(6, March, 2026), Period(5, Years),
                       DateGeneration::CDS);
    runCdsMaturityCase(out, "cdsmat_3y_cds2015",
                       Date(6, March, 2026), Period(3, Years),
                       DateGeneration::CDS2015);

    out.write();
    return 0;
}
