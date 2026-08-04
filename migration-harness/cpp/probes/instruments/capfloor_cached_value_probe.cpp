// migration-harness/cpp/probes/instruments/capfloor_cached_value_probe.cpp
//
// Phase 5e.5b-CFC-d-222 — reproduce C++ test-suite/capfloor.cpp:testCachedValue
// fixture and emit the actual C++ v1.42.1 NPV (plus per-coupon fingerprints)
// so the Java port can pin a tight-tier expected value.
//
// Fixture (verbatim from capfloor.cpp lines 536-578):
//   today          = 14-March-2002
//   evaluationDate = today
//   settlementDays = 2
//   index          = Euribor6M(termStructure)
//   calendar       = index.fixingCalendar()
//   convention     = ModifiedFollowing
//   fixingDays     = 2
//   settlement     = calendar.advance(today, settlementDays, Days)  // 18-Mar-2002
//                    NB: the test hard-codes cachedSettlement(18,March,2002)
//                    and uses it directly as the curve reference date.
//   termStructure  = flatRate(cachedSettlement, 0.05, Actual360())
//   length         = 20 years
//   cap strike     = 0.07, floor strike = 0.03, vol = 0.20 (lognormal)
//
// Hard-coded cached values in the C++ source (for reference / sanity):
//   index-fixing branch:   cap = 6.87630307745, floor = 2.65796764715
//   par-coupon  branch:    cap = 6.87570026732, floor = 2.65812927959
// The C++ v1.42.1 build default is usingAtParCoupons=false (the index-fixing
// branch); the probe captures the actual computed NPV regardless of branch.

#include <ql/version.hpp>
#include <ql/cashflows/cashflows.hpp>
#include <ql/cashflows/coupon.hpp>
#include <ql/cashflows/floatingratecoupon.hpp>
#include <ql/cashflows/iborcoupon.hpp>
#include <ql/indexes/ibor/euribor.hpp>
#include <ql/instruments/capfloor.hpp>
#include <ql/pricingengines/capfloor/blackcapfloorengine.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/settings.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/time/daycounters/actual360.hpp>
#include <ql/time/schedule.hpp>

#include "../common.hpp"

#include <sstream>
#include <vector>

using namespace QuantLib;
using namespace jqml_harness;

namespace {

json dateToJson(const Date& d) {
    std::ostringstream oss;
    oss << d.year() << "-"
        << std::setw(2) << std::setfill('0') << static_cast<int>(d.month()) << "-"
        << std::setw(2) << std::setfill('0') << d.dayOfMonth();
    return oss.str();
}

json scheduleToJson(const Schedule& s) {
    json arr = json::array();
    for (Size i = 0; i < s.size(); ++i) {
        arr.push_back(dateToJson(s.date(i)));
    }
    return arr;
}

json legToJson(const Leg& leg, const Handle<YieldTermStructure>& ts) {
    json arr = json::array();
    for (const auto& cf : leg) {
        json entry;
        entry["paymentDate"] = dateToJson(cf->date());
        entry["amount"]      = cf->amount();
        entry["discount"]    = ts->discount(cf->date());

        if (auto coupon = ext::dynamic_pointer_cast<Coupon>(cf)) {
            entry["accrualStart"] = dateToJson(coupon->accrualStartDate());
            entry["accrualEnd"]   = dateToJson(coupon->accrualEndDate());
            entry["accrualDays"]  = static_cast<int>(coupon->accrualDays());
            entry["accrualTime"]  = coupon->accrualPeriod();
            entry["nominal"]      = coupon->nominal();
            entry["rate"]         = coupon->rate();
        }
        if (auto fcp = ext::dynamic_pointer_cast<FloatingRateCoupon>(cf)) {
            entry["fixingDate"]  = dateToJson(fcp->fixingDate());
            entry["indexFixing"] = fcp->indexFixing();
            entry["spread"]      = fcp->spread();
            entry["gearing"]     = fcp->gearing();
        }
        arr.push_back(entry);
    }
    return arr;
}

}  // namespace

int main() {
    // --- common-vars fixture, sliced to the testCachedValue inputs ---
    const Date today(14, March, 2002);
    Settings::instance().evaluationDate() = today;

    const Natural settlementDays = 2;
    const Natural fixingDays = 2;
    const std::vector<Real> nominals(1, 100.0);
    const Frequency frequency = Semiannual;
    const auto convention = ModifiedFollowing;

    RelinkableHandle<YieldTermStructure> termStructure;
    auto index = ext::make_shared<Euribor6M>(termStructure);
    const Calendar calendar = index->fixingCalendar();

    // Probe-side check: confirm that the C++ test's hard-coded
    // cachedSettlement matches calendar.advance(today, settlementDays, Days).
    const Date computedSettlement =
        calendar.advance(today, settlementDays, Days);
    const Date cachedSettlement(18, March, 2002);

    // The C++ test links the curve at cachedSettlement directly (not
    // computedSettlement). Mirror that.
    termStructure.linkTo(
        ext::make_shared<FlatForward>(cachedSettlement, 0.05, Actual360()));

    const Date startDate = termStructure->referenceDate();  // = cachedSettlement

    // Build the leg (capfloor.cpp:78-88 makeLeg, length=20)
    const Integer length = 20;
    const Date endDate =
        calendar.advance(startDate, length * Years, convention);
    Schedule schedule(startDate, endDate, Period(frequency), calendar,
                      convention, convention,
                      DateGeneration::Forward, false);
    Leg leg = IborLeg(schedule, index)
                  .withNotionals(nominals)
                  .withPaymentDayCounter(index->dayCounter())
                  .withPaymentAdjustment(convention)
                  .withFixingDays(fixingDays);

    // Engines (BlackCapFloorEngine, lognormal vol = 0.20)
    const Volatility vol = 0.20;
    auto engine = ext::make_shared<BlackCapFloorEngine>(
        termStructure,
        Handle<Quote>(ext::make_shared<SimpleQuote>(vol)));

    // Cap, strike=0.07
    auto cap = ext::make_shared<Cap>(leg, std::vector<Rate>(1, 0.07));
    cap->setPricingEngine(engine);

    // Floor, strike=0.03
    auto floor = ext::make_shared<Floor>(leg, std::vector<Rate>(1, 0.03));
    floor->setPricingEngine(engine);

    const Real capNPV = cap->NPV();
    const Real floorNPV = floor->NPV();
    const bool usingAtParCoupons =
        IborCoupon::Settings::instance().usingAtParCoupons();

    // The two hard-coded branches in the C++ source.
    const Real hardcodedCapAtPar = 6.87570026732;
    const Real hardcodedFloorAtPar = 2.65812927959;
    const Real hardcodedCapIndexFix = 6.87630307745;
    const Real hardcodedFloorIndexFix = 2.65796764715;

    // --- emit fingerprint ---
    ReferenceWriter w("instruments/capfloor_cached_value",
                      QL_VERSION,
                      "capfloor_cached_value_probe.cpp");

    json inputs = {
        {"today",                  dateToJson(today)},
        {"settlementDays",         settlementDays},
        {"computedSettlement",     dateToJson(computedSettlement)},
        {"cachedSettlement",       dateToJson(cachedSettlement)},
        {"curveReferenceDate",     dateToJson(termStructure->referenceDate())},
        {"calendar",               calendar.name()},
        {"indexName",              index->name()},
        {"indexDayCounter",        index->dayCounter().name()},
        {"indexFixingDays",        static_cast<int>(index->fixingDays())},
        {"indexFamily",            index->familyName()},
        {"convention",             "ModifiedFollowing"},
        {"frequency",              "Semiannual"},
        {"length",                 length},
        {"endDate",                dateToJson(endDate)},
        {"nominal",                nominals[0]},
        {"capStrike",              0.07},
        {"floorStrike",            0.03},
        {"volatility",             vol},
        {"flatRate",               0.05},
        {"curveDayCounter",        "Actual/360"},
        {"discountAtSettlement",   termStructure->discount(cachedSettlement)},
        {"discountAtMaturity",     termStructure->discount(endDate)},
        {"schedule",               scheduleToJson(schedule)}
    };

    json expectedJson = {
        {"capNPV",                  capNPV},
        {"floorNPV",                floorNPV},
        {"usingAtParCoupons",       usingAtParCoupons},
        {"hardcodedCapAtPar",       hardcodedCapAtPar},
        {"hardcodedFloorAtPar",     hardcodedFloorAtPar},
        {"hardcodedCapIndexFix",    hardcodedCapIndexFix},
        {"hardcodedFloorIndexFix",  hardcodedFloorIndexFix},
        {"floatingLeg",             legToJson(leg, termStructure)}
    };

    w.addCase("black_cap20y_strike7_floor3_vol20", inputs, expectedJson);
    w.write();

    return 0;
}
