// migration-harness/cpp/probes/model/shortrate/calibrationhelpers/caphelper_probe.cpp
//
// Probe for Phase 2d WI-1 + Phase 2e WI-2: CapHelper cross-validation.
//
// Captures:
//  * fair_rate_intermediate: swap-implied fairRate computed inside
//    CapHelper::performCalculations (caphelper.cpp:91-144 in QuantLib v1.42.1).
//  * model_value: CapHelper::modelValue() with engine_ = BlackCapFloorEngine
//    on the same termStructure / vol Handle as the helper itself.
//  * black_price_at_vol: CapHelper::blackPrice(0.20). This is independent
//    of engine_ — the helper builds a transient BlackCapFloorEngine with a
//    SimpleQuote(sigma) inside blackPrice().
//
// fairRate is private to performCalculations() in C++; rather than
// reflecting/befriending CapHelper, the fair-rate case replicates the exact
// setup verbatim and computes fairRate directly from
//   swap.NPV(), swap.legBPS(1), the dummy fixedRate=0.04.
// model_value / black_price_at_vol are read off the actual CapHelper public
// API to avoid drift between the probe and the helper internals.

#include <ql/version.hpp>

#include <ql/cashflows/cashflowvectors.hpp>
#include <ql/cashflows/fixedratecoupon.hpp>
#include <ql/cashflows/iborcoupon.hpp>
#include <ql/currencies/europe.hpp>
#include <ql/indexes/iborindex.hpp>
#include <ql/instruments/swap.hpp>
#include <ql/models/shortrate/calibrationhelpers/caphelper.hpp>
#include <ql/pricingengines/capfloor/blackcapfloorengine.hpp>
#include <ql/pricingengines/swap/discountingswapengine.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/settings.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/time/calendars/target.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>
#include <ql/time/daycounters/thirty360.hpp>
#include <ql/time/schedule.hpp>

#include "../../../common.hpp"

#include <vector>

using namespace QuantLib;
using namespace jqml_harness;

int main() {
    ReferenceWriter out("model/shortrate/calibrationhelpers/caphelper",
                        QL_VERSION, "caphelper_probe");

    // --- Fixture (must match Java CapHelperTest exactly) ---
    const Date eval(15, January, 2026);
    Settings::instance().evaluationDate() = eval;
    const DayCounter dc = Actual365Fixed();
    const Calendar cal = TARGET();
    const BusinessDayConvention bdc = ModifiedFollowing;
    const Currency ccy = EURCurrency();

    const Real flatRate = 0.05;
    const Handle<YieldTermStructure> ts(
        ext::make_shared<FlatForward>(eval, flatRate, dc, Continuous, Annual));

    // IborIndex matching what CapHelper's caller would normally pass.
    // CapHelper.performCalculations does NOT use the index's day-counter for
    // discounting; the curve carries its own. The index just supplies tenor,
    // calendar, BDC, EOM, fixingDays, and the handle used by IborCoupon.
    const Period idxTenor = 3 * Months;
    const Natural fixingDays = 0;
    const bool eom = false;
    const ext::shared_ptr<IborIndex> idx(new IborIndex(
        "TestIbor3M", idxTenor, fixingDays, ccy, cal, bdc, eom, dc, ts));

    // --- Replicate CapHelper::performCalculations setup verbatim ---
    const Period length = 5 * Years;
    const Frequency fixedLegFrequency = Annual;
    const DayCounter fixedLegDayCounter = Thirty360(Thirty360::European);
    const bool includeFirstSwaplet = true;
    const Rate fixedRate = 0.04; // dummy value, exactly as CapHelper hardcodes

    const Date startDate = includeFirstSwaplet
        ? ts->referenceDate()
        : ts->referenceDate() + idxTenor;
    const Date maturity = ts->referenceDate() + length;

    const std::vector<Real> nominals(1, 1.0);

    Schedule floatSchedule(startDate, maturity,
                           idxTenor, cal, bdc, bdc,
                           DateGeneration::Forward, false);
    Leg floatingLeg = IborLeg(floatSchedule, idx)
        .withNotionals(nominals)
        .withPaymentAdjustment(bdc)
        .withFixingDays(0);

    Schedule fixedSchedule(startDate, maturity, Period(fixedLegFrequency),
                           cal, Unadjusted, Unadjusted,
                           DateGeneration::Forward, false);
    Leg fixedLeg = FixedRateLeg(fixedSchedule)
        .withNotionals(nominals)
        .withCouponRates(fixedRate, fixedLegDayCounter)
        .withPaymentAdjustment(bdc);

    Swap swap(floatingLeg, fixedLeg);
    swap.setPricingEngine(ext::shared_ptr<PricingEngine>(
        new DiscountingSwapEngine(ts, false)));

    const Real swapNPV   = swap.NPV();
    const Real legBPS1   = swap.legBPS(1);
    const Real fairRate  = fixedRate - swapNPV / (legBPS1 / 1.0e-4);

    json inputs = {
        {"eval_date", "2026-01-15"},
        {"flat_rate", flatRate},
        {"length_years", 5},
        {"index_tenor_months", 3},
        {"fixed_freq", "Annual"},
        {"include_first_swaplet", includeFirstSwaplet},
        {"fixed_rate_dummy", fixedRate}
    };
    json expected = {
        {"swap_npv", swapNPV},
        {"leg_bps_1", legBPS1},
        {"fair_rate", fairRate},
        // Useful structural counters for the Java test to assert too.
        {"n_floating_periods", static_cast<int>(floatingLeg.size())},
        {"n_fixed_periods", static_cast<int>(fixedLeg.size())}
    };

    out.addCase("fair_rate_intermediate", inputs, expected);

    // --- Phase 2e WI-2: modelValue() + blackPrice() cases ---
    // Build the CapHelper exactly as the Java test does, set its engine_
    // to a BlackCapFloorEngine with the same vol Handle, then read
    // modelValue() and blackPrice(0.20) off the helper's public API.
    const Real helperVol = 0.20;
    const Handle<Quote> volQuote(ext::make_shared<SimpleQuote>(helperVol));
    ext::shared_ptr<CapHelper> helper(new CapHelper(
        length, volQuote, idx, fixedLegFrequency, fixedLegDayCounter,
        includeFirstSwaplet, ts));
    ext::shared_ptr<PricingEngine> capEngine(
        new BlackCapFloorEngine(ts, volQuote, Actual365Fixed()));
    helper->setPricingEngine(capEngine);

    const Real modelValue       = helper->modelValue();
    const Real blackPriceAtVol  = helper->blackPrice(helperVol);

    json mvInputs = {
        {"helper_vol", helperVol}
    };
    json mvExpected = {
        {"model_value",         modelValue},
        {"black_price_at_vol",  blackPriceAtVol}
    };
    out.addCase("model_value_and_black_price", mvInputs, mvExpected);

    out.write();
    return 0;
}
