// migration-harness/cpp/probes/instruments/excoupon_bonds_probe.cpp
// Reference values for ex-coupon bond tests against QuantLib v1.42.1.
// Phase 5e.5b-CFC-d-93.
//
// Reproduces the testExCouponGilt / testExCouponAustralianBond /
// testBondFromScheduleWithDateVector fixtures from test-suite/bonds.cpp
// and emits accruedAmount + dirtyPrice values that drive the Java
// FixedRateBond + FixedRateCoupon.exCouponDate validation in
// BondAdditionalTest.

#include <cstdio>
#include <ql/version.hpp>
#include "../common.hpp"

#include <ql/instruments/bonds/fixedratebond.hpp>
#include <ql/pricingengines/bond/bondfunctions.hpp>
#include <ql/cashflows/cashflows.hpp>
#include <ql/time/calendars/unitedkingdom.hpp>
#include <ql/time/calendars/australia.hpp>
#include <ql/time/calendars/nullcalendar.hpp>
#include <ql/time/daycounters/actualactual.hpp>
#include <ql/time/schedule.hpp>
#include <ql/settings.hpp>

using namespace jqml_harness;
using namespace QuantLib;

namespace {

// --- testExCouponGilt fixture ---
// test-suite/bonds.cpp:1155-1281
void emitGilt(ReferenceWriter& out) {
    Calendar calendar = UnitedKingdom();
    Natural settlementDays = 3;
    Date issueDate(29, February, 1996);
    Date startDate(29, February, 1996);
    Date firstCouponDate(7, June, 1996);
    Date maturityDate(7, June, 2021);
    Rate coupon = 0.08;
    Period tenor = 6 * Months;
    Period exCouponPeriod = 6 * Days;
    Compounding comp = Compounded;
    Frequency freq = Semiannual;

    Schedule schedule(startDate, maturityDate, tenor,
                      NullCalendar(), Unadjusted, Unadjusted,
                      DateGeneration::Forward, true, firstCouponDate);
    DayCounter dc = ActualActual(ActualActual::ISMA, schedule);

    FixedRateBond bond(settlementDays, 100.0,
                       schedule,
                       std::vector<Rate>(1, coupon),
                       dc, Unadjusted, 100.0,
                       issueDate, calendar, exCouponPeriod, calendar);

    const Leg& leg = bond.cashflows();

    struct Tc { Date d; Real testPrice; };
    Tc tcs[] = {
        {Date(29, May, 2013), 103.0},
        {Date(30, May, 2013), 103.0},
        {Date(31, May, 2013), 103.0},
    };

    for (auto& t : tcs) {
        Real accrued = bond.accruedAmount(t.d);
        Real npv = t.testPrice + accrued;
        Rate y = CashFlows::yield(leg, npv, dc, comp, freq, false, t.d);
        Time dur = CashFlows::duration(leg, y, dc, comp, freq,
                                       Duration::Modified, false, t.d);
        Real conv = CashFlows::convexity(leg, y, dc, comp, freq, false, t.d);
        Real calcnpv = CashFlows::npv(leg, y, dc, comp, freq, false, t.d);

        char name[64];
        std::snprintf(name, sizeof(name), "gilt_%d",
                      (int)t.d.serialNumber());
        json inp{
            {"settlement_serial", t.d.serialNumber()},
            {"testPrice", t.testPrice},
        };
        json exp{
            {"accruedAmount", accrued},
            {"npv", npv},
            {"yield", y},
            {"duration", dur},
            {"convexity", conv},
            {"npvFromYield", calcnpv},
            {"priceFromYield", calcnpv - accrued},
        };
        out.addCase(name, inp, exp);
    }
}

// --- testExCouponAustralianBond fixture ---
// test-suite/bonds.cpp:1283-1409
void emitAGB(ReferenceWriter& out) {
    Calendar calendar = Australia();
    Natural settlementDays = 3;
    Date issueDate(10, June, 2004);
    Date startDate(15, February, 2004);
    Date firstCouponDate(15, August, 2004);
    Date maturityDate(15, February, 2017);
    Rate coupon = 0.06;
    Period tenor = 6 * Months;
    Period exCouponPeriod = 7 * Days;
    Compounding comp = Compounded;
    Frequency freq = Semiannual;

    Schedule schedule(startDate, maturityDate, tenor,
                      NullCalendar(), Unadjusted, Unadjusted,
                      DateGeneration::Forward, true, firstCouponDate);
    DayCounter dc = ActualActual(ActualActual::ISMA, schedule);

    FixedRateBond bond(settlementDays, 100.0,
                       schedule,
                       std::vector<Rate>(1, coupon),
                       dc, Unadjusted, 100.0,
                       issueDate, calendar, exCouponPeriod, NullCalendar());

    const Leg& leg = bond.cashflows();

    struct Tc { Date d; Real testPrice; };
    Tc tcs[] = {
        {Date(7, August, 2014), 103.0},
        {Date(8, August, 2014), 103.0},
        {Date(11, August, 2014), 103.0},
    };

    for (auto& t : tcs) {
        Real accrued = bond.accruedAmount(t.d);
        Real npv = t.testPrice + accrued;
        Rate y = CashFlows::yield(leg, npv, dc, comp, freq, false, t.d);
        Time dur = CashFlows::duration(leg, y, dc, comp, freq,
                                       Duration::Modified, false, t.d);
        Real conv = CashFlows::convexity(leg, y, dc, comp, freq, false, t.d);
        Real calcnpv = CashFlows::npv(leg, y, dc, comp, freq, false, t.d);

        char name[64];
        std::snprintf(name, sizeof(name), "agb_%d",
                      (int)t.d.serialNumber());
        json inp{
            {"settlement_serial", t.d.serialNumber()},
            {"testPrice", t.testPrice},
        };
        json exp{
            {"accruedAmount", accrued},
            {"npv", npv},
            {"yield", y},
            {"duration", dur},
            {"convexity", conv},
            {"npvFromYield", calcnpv},
            {"priceFromYield", calcnpv - accrued},
        };
        out.addCase(name, inp, exp);
    }
}

// --- testBondFromScheduleWithDateVector fixture ---
// test-suite/bonds.cpp:1416-1491
void emitR2048(ReferenceWriter& out) {
    Calendar calendar = NullCalendar();
    Natural settlementDays = 3;
    Date issueDate(29, June, 2012);
    Date today(7, September, 2015);
    Date evaluationDate = calendar.adjust(today);
    Date settlementDate = calendar.advance(evaluationDate, settlementDays * Days);
    Settings::instance().evaluationDate() = evaluationDate;
    Date maturityDate(29, February, 2048);
    Rate coupon = 0.0875;
    Compounding comp = Compounded;
    Frequency freq = Semiannual;
    Period tenor = 6 * Months;
    Period exCouponPeriod = 10 * Days;

    Schedule schedule(issueDate, maturityDate, tenor,
                      NullCalendar(), Unadjusted, Unadjusted,
                      DateGeneration::Backward, true);

    // Adjust the 29 Feb's to 28 Feb (bond pays on 28 Feb regardless).
    std::vector<Date> dates;
    for (Size i = 0; i < schedule.size(); ++i) {
        Date d = schedule.date(i);
        if (d.month() == February && d.dayOfMonth() == 29)
            dates.emplace_back(28, February, d.year());
        else
            dates.push_back(d);
    }
    schedule = Schedule(dates,
                        schedule.calendar(),
                        schedule.businessDayConvention(),
                        schedule.terminationDateBusinessDayConvention(),
                        schedule.tenor(),
                        schedule.rule(),
                        schedule.endOfMonth(),
                        schedule.isRegular());

    DayCounter dc = ActualActual(ActualActual::Bond, schedule);
    FixedRateBond bond(0, 100.0, schedule,
                       std::vector<Rate>(1, coupon),
                       dc, Following, 100.0,
                       issueDate, calendar,
                       exCouponPeriod, calendar, Unadjusted, false);

    InterestRate yield(0.09185, dc, comp, freq);
    Real dirty = BondFunctions::dirtyPrice(bond, yield, settlementDate);
    Real accrued = bond.accruedAmount(settlementDate);

    json inp{
        {"evaluation_serial", evaluationDate.serialNumber()},
        {"settlement_serial", settlementDate.serialNumber()},
        {"coupon", coupon},
        {"yield", 0.09185},
    };
    json exp{
        {"dirtyPrice", dirty},
        {"accruedAmount", accrued},
    };
    out.addCase("r2048_2015_09_07", inp, exp);
}

} // anonymous namespace

int main() {
    ReferenceWriter out("instruments/excoupon_bonds",
                        QL_VERSION,
                        "excoupon_bonds_probe");
    emitGilt(out);
    emitAGB(out);
    emitR2048(out);
    out.write();
    return 0;
}
