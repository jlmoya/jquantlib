// migration-harness/cpp/probes/instruments/swap_cached_value_probe.cpp
//
// WI-5e.5-SWAP-3 — reproduce C++ test-suite/swap.cpp:testCachedValue
// fixture and emit fingerprint for Java parity diagnosis.
//
// Fixture (mirrors swap.cpp lines 284-313):
//   today           = 17-June-2002
//   evaluationDate  = today
//   settlementDays  = 2
//   calendar        = Euribor(Period(Semiannual), termStructure).fixingCalendar()
//   settlement      = calendar.advance(today, 2, Days)
//   termStructure   = flatRate(settlement, 0.05, Actual365Fixed())
//   index           = Euribor(Period(Semiannual), termStructure)
//   length          = 10 years, fixed=0.06, spread=0.001
//
// Expected (usingAtParCoupons=true, the C++ build default):
//   swap.NPV()  = -5.872863313209
//   numLegs     = 2
//
// Probe emits both the headline NPV and per-cashflow fingerprints
// (date, amount, plus floating-leg fixing/accrual/discount) so the
// Java port can compare cashflow-by-cashflow.

#include <ql/version.hpp>
#include <ql/cashflows/cashflows.hpp>
#include <ql/cashflows/floatingratecoupon.hpp>
#include <ql/cashflows/iborcoupon.hpp>
#include <ql/cashflows/coupon.hpp>
#include <ql/indexes/ibor/euribor.hpp>
#include <ql/instruments/vanillaswap.hpp>
#include <ql/pricingengines/swap/discountingswapengine.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/settings.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/time/calendars/target.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>
#include <ql/time/daycounters/thirty360.hpp>
#include <ql/time/schedule.hpp>

#include "common.hpp"

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
            entry["nominal"]      = coupon->nominal();
            entry["rate"]         = coupon->rate();
        }
        if (auto fcp = ext::dynamic_pointer_cast<FloatingRateCoupon>(cf)) {
            entry["fixingDate"] = dateToJson(fcp->fixingDate());
            entry["indexFixing"] = fcp->indexFixing();
            entry["spread"]     = fcp->spread();
            entry["gearing"]    = fcp->gearing();
        }
        arr.push_back(entry);
    }
    return arr;
}

}  // namespace

int main() {
    // === fixture (verbatim from C++ swap.cpp:284-298) ===
    const Date today(17, June, 2002);
    Settings::instance().evaluationDate() = today;

    const Natural settlementDays = 2;
    const Real nominal = 100.0;
    const Frequency floatingFrequency = Semiannual;
    const Frequency fixedFrequency = Annual;
    const auto fixedConvention = Unadjusted;
    const auto floatingConvention = ModifiedFollowing;
    const DayCounter fixedDayCount = Thirty360(Thirty360::BondBasis);

    RelinkableHandle<YieldTermStructure> termStructure;
    auto index = ext::make_shared<Euribor>(Period(floatingFrequency), termStructure);
    const Calendar calendar = index->fixingCalendar();
    const Date settlement = calendar.advance(today, settlementDays, Days);
    termStructure.linkTo(
        ext::make_shared<FlatForward>(settlement, 0.05, Actual365Fixed()));

    const Integer length = 10;
    const Rate fixedRate = 0.06;
    const Spread floatingSpread = 0.001;

    const Date maturity = calendar.advance(settlement, length, Years, floatingConvention);
    Schedule fixedSchedule(settlement, maturity, Period(fixedFrequency),
                           calendar, fixedConvention, fixedConvention,
                           DateGeneration::Forward, false);
    Schedule floatSchedule(settlement, maturity, Period(floatingFrequency),
                           calendar, floatingConvention, floatingConvention,
                           DateGeneration::Forward, false);

    auto swap = ext::make_shared<VanillaSwap>(
        Swap::Payer, nominal,
        fixedSchedule, fixedRate, fixedDayCount,
        floatSchedule, index, floatingSpread,
        index->dayCounter());
    swap->setPricingEngine(
        ext::make_shared<DiscountingSwapEngine>(termStructure));

    const Real npv = swap->NPV();
    const bool usingAtParCoupons =
        IborCoupon::Settings::instance().usingAtParCoupons();
    const Real expected =
        usingAtParCoupons ? -5.872863313209 : -5.872342992212;

    // === emit fingerprint ===
    ReferenceWriter w("instruments/swap_cached_value",
                      QL_VERSION,
                      "swap_cached_value_probe.cpp");

    json inputs = {
        {"today",            dateToJson(today)},
        {"settlement",       dateToJson(settlement)},
        {"settlementDays",   settlementDays},
        {"maturity",         dateToJson(maturity)},
        {"calendar",         calendar.name()},
        {"indexName",        index->name()},
        {"indexDayCounter",  index->dayCounter().name()},
        {"indexFixingDays",  static_cast<int>(index->fixingDays())},
        {"indexFamily",      index->familyName()},
        {"indexTenor",       index->tenor().length()},
        {"fixedConvention",  "Unadjusted"},
        {"floatingConvention", "ModifiedFollowing"},
        {"fixedDayCount",    fixedDayCount.name()},
        {"length",           length},
        {"fixedRate",        fixedRate},
        {"floatingSpread",   floatingSpread},
        {"nominal",          nominal},
        {"discountAtSettlement", termStructure->discount(settlement)},
        {"discountAtMaturity",   termStructure->discount(maturity)},
        {"fixedSchedule",    scheduleToJson(fixedSchedule)},
        {"floatSchedule",    scheduleToJson(floatSchedule)}
    };

    json expectedJson = {
        {"npv",               npv},
        {"expectedAtPar",     expected},
        {"usingAtParCoupons", usingAtParCoupons},
        {"numberOfLegs",      static_cast<int>(swap->numberOfLegs())},
        {"fixedLegNPV",       swap->fixedLegNPV()},
        {"floatingLegNPV",    swap->floatingLegNPV()},
        {"fairRate",          swap->fairRate()},
        {"fairSpread",        swap->fairSpread()},
        {"fixedLeg",          legToJson(swap->fixedLeg(),    termStructure)},
        {"floatingLeg",       legToJson(swap->floatingLeg(), termStructure)}
    };

    w.addCase("vanilla_swap_10y_fixed6pct_spread10bp", inputs, expectedJson);
    w.write();

    return 0;
}
