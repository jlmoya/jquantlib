// migration-harness/cpp/probes/model/shortrate/calibrationhelpers/caphelper_probe.cpp
//
// Probe for Phase 2d WI-1: CapHelper internal fairRate cross-validation.
//
// Captures the swap-implied fairRate intermediate computed inside
// CapHelper::performCalculations (caphelper.cpp:91-144 in QuantLib v1.42.1).
// The downstream modelValue() and blackPrice(volatility) values are NOT
// captured here — the Java BlackCapFloorEngine and CapFloor.NPV() pricing
// path are documented Phase 2e seams (still stubbed in jquantlib).
// Cross-validating fairRate exercises the only path of CapHelper that has
// a fully-functional Java counterpart in this commit: building the
// floating + fixed legs and a Swap priced by DiscountingSwapEngine.
//
// fairRate is private to performCalculations() in C++; rather than
// reflecting/befriending CapHelper, this probe replicates the exact
// setup verbatim and computes fairRate directly from
//   swap.NPV(), swap.legBPS(1), the dummy fixedRate=0.04
// which is what the Java CapHelper test must also do.

#include <ql/version.hpp>

#include <ql/cashflows/cashflowvectors.hpp>
#include <ql/cashflows/fixedratecoupon.hpp>
#include <ql/cashflows/iborcoupon.hpp>
#include <ql/currencies/europe.hpp>
#include <ql/indexes/iborindex.hpp>
#include <ql/instruments/swap.hpp>
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
    out.write();
    return 0;
}
