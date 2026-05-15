// migration-harness/cpp/probes/cashflows/overnight_leg_caps_floors_probe.cpp
//
// Reference probe for the C++ test
// overnightindexedcoupon.cpp:1038-1071 testOvernightLegWithCapsAndFloors
// (Phase 5e.5b-CFC-d body-fill cross-validation).
//
// Replicates the exact CommonVarsONLeg fixture + setupForecastCurve():
//   eval date         = 1-Jun-2025
//   notional          = 1,000,000
//   schedule          = quarterly, 1-Jul-2025 -> 1-Jul-2026, US-GovBond, ModFollowing
//   past fixings      = 43 SOFR fixings 2-Jun-2025..1-Aug-2025
//   forecast curve    = InterpolatedZeroCurve<Cubic> over 7 (date,zeroRate) pairs,
//                       Actual/360, US-SOFR calendar, extrapolation enabled
//   discount curve    = flatRate(0.0015, Actual/360)
//   caps              = {0.0435, 0.0435, 0.04, 0.04}
//   floors            = {0.025,  0.025,  0.025, 0.025}
//   pricer            = BlackCompoundingOvernightIndexedCouponPricer with
//                       ConstantOptionletVolatility(today, TARGET, Following, 0.05, Act360)
//
// Dumps:
//   * total NPV                 (= sum coupon.amount * discount(coupon.date))
//   * per-coupon: cap, floor, isCapped, isFloored, amount, paymentDate,
//                 discountFactor
//   * legSize
//
// Used by Java test
// org.jquantlib.testsuite.cashflows.OvernightIndexedCouponTest
//   #testOvernightLegWithCapsAndFloors

#include <cstdio>
#include <ql/version.hpp>
#include "../common.hpp"

#include <ql/cashflows/overnightindexedcoupon.hpp>
#include <ql/cashflows/blackovernightindexedcouponpricer.hpp>
#include <ql/cashflows/couponpricer.hpp>
#include <ql/indexes/ibor/sofr.hpp>
#include <ql/math/interpolations/cubicinterpolation.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/termstructures/yield/zerocurve.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/termstructures/volatility/optionlet/constantoptionletvol.hpp>
#include <ql/time/calendars/target.hpp>
#include <ql/time/calendars/unitedstates.hpp>
#include <ql/time/daycounters/actual360.hpp>
#include <ql/time/schedule.hpp>
#include <ql/utilities/null.hpp>

using namespace jqml_harness;
using namespace QuantLib;

namespace {

json dateToJson(const Date& d) {
    char buf[16];
    std::snprintf(buf, sizeof(buf), "%04d-%02d-%02d",
                  static_cast<int>(d.year()),
                  static_cast<int>(d.month()),
                  static_cast<int>(d.dayOfMonth()));
    return json{
        {"date",   buf},
        {"serial", static_cast<long>(d.serialNumber())}
    };
}

} // namespace

int main() {
    ReferenceWriter out("cashflows/overnight_leg_caps_floors",
                        QL_VERSION, "overnight_leg_caps_floors_probe");

    // -------- CommonVarsONLeg fixture --------
    Date today = Date(1, June, 2025);
    Settings::instance().evaluationDate() = today;
    DayCounter dc = Actual360();
    Real notional = 1000000.0;

    RelinkableHandle<YieldTermStructure> forecastCurve;
    auto sofr = ext::make_shared<Sofr>(forecastCurve);

    Schedule legSchedule(
        Date(1, July, 2025), Date(1, July, 2026),
        Period(3, Months),
        UnitedStates(UnitedStates::GovernmentBond),
        ModifiedFollowing, ModifiedFollowing,
        DateGeneration::Forward, false);

    // 43-row past fixings (cpp:262-282).
    std::vector<Date> pastDates = {
        Date( 2, June, 2025), Date( 3, June, 2025), Date( 4, June, 2025),
        Date( 5, June, 2025), Date( 6, June, 2025), Date( 9, June, 2025),
        Date(10, June, 2025), Date(11, June, 2025), Date(12, June, 2025),
        Date(13, June, 2025), Date(16, June, 2025), Date(17, June, 2025),
        Date(18, June, 2025), Date(20, June, 2025), Date(23, June, 2025),
        Date(24, June, 2025), Date(25, June, 2025), Date(26, June, 2025),
        Date(27, June, 2025), Date(30, June, 2025), Date( 1, July, 2025),
        Date( 2, July, 2025), Date( 3, July, 2025), Date( 7, July, 2025),
        Date( 8, July, 2025), Date( 9, July, 2025), Date(10, July, 2025),
        Date(11, July, 2025), Date(14, July, 2025), Date(15, July, 2025),
        Date(16, July, 2025), Date(17, July, 2025), Date(18, July, 2025),
        Date(21, July, 2025), Date(22, July, 2025), Date(23, July, 2025),
        Date(24, July, 2025), Date(25, July, 2025), Date(28, July, 2025),
        Date(29, July, 2025), Date(30, July, 2025), Date(31, July, 2025),
        Date( 1, August, 2025)
    };
    std::vector<Rate> pastRates = {
        0.0435, 0.0432, 0.0428, 0.0429, 0.0429, 0.0429, 0.0428, 0.0428,
        0.0428, 0.0428, 0.0432, 0.0431, 0.0428, 0.0429, 0.0429, 0.0430,
        0.0436, 0.0440, 0.0439, 0.0445, 0.0444, 0.0440, 0.0435, 0.0433,
        0.0434, 0.0432, 0.0431, 0.0431, 0.0433, 0.0437, 0.0434, 0.0434,
        0.0430, 0.0428, 0.0428, 0.0428, 0.0430, 0.0436, 0.0436, 0.0436,
        0.0432, 0.0439, 0.0434
    };
    sofr->addFixings(pastDates.begin(), pastDates.end(), pastRates.begin());

    // -------- setupForecastCurve (cpp:287-316) --------
    std::vector<Date> curveDates = {
        today,
        Date(30, July, 2025),
        Date(29, August, 2025),
        Date(30, September, 2025),
        Date(30, December, 2025),
        Date(30, March, 2026),
        Date(30, June, 2026)
    };
    std::vector<Rate> zeroRates = {
        0.0434, 0.0436, 0.0431, 0.0413, 0.0390, 0.0370, 0.0348
    };
    auto zeroCurve = ext::make_shared<InterpolatedZeroCurve<Cubic>>(
        curveDates, zeroRates, dc, UnitedStates(UnitedStates::SOFR));
    zeroCurve->enableExtrapolation();
    forecastCurve.linkTo(zeroCurve);

    // -------- Discount curve: flatRate(0.0015, Actual/360) --------
    Handle<YieldTermStructure> discountCurve(
        ext::make_shared<FlatForward>(today, 0.0015, dc));

    // -------- Optionlet vol for Black pricer (cpp makeLeg:236-242) --------
    RelinkableHandle<OptionletVolatilityStructure> rateVolTS;
    rateVolTS.linkTo(ext::make_shared<ConstantOptionletVolatility>(
        today, TARGET(), Following, 0.05, dc));

    // -------- Test inputs --------
    std::vector<Rate> caps   = {0.0435, 0.0435, 0.04, 0.04};
    std::vector<Rate> floors = {0.025,  0.025,  0.025, 0.025};

    // -------- Build leg: makeLeg(Null, 0, false, false, Compound, _, _, caps, floors) --------
    OvernightLeg leg(legSchedule, sofr);
    leg.withNotionals(notional)
       .withPaymentDayCounter(dc)
       .withAveragingMethod(RateAveraging::Compound)
       .withLockoutDays(0)
       .withObservationShift(false)
       .withTelescopicValueDates(false)
       .withCaps(caps)
       .withFloors(floors)
       .withCouponPricer(
            ext::make_shared<BlackCompoundingOvernightIndexedCouponPricer>(rateVolTS));

    Leg cflows = leg;

    // -------- Per-coupon assertions + NPV accumulation --------
    Real npv = 0.0;
    json perCoupon = json::array();
    for (Size i = 0; i < cflows.size(); ++i) {
        auto cf = ext::dynamic_pointer_cast<CappedFlooredOvernightIndexedCoupon>(cflows[i]);
        if (!cf) {
            std::fprintf(stderr,
                "FATAL: leg[%zu] is NOT a CappedFlooredOvernightIndexedCoupon\n", i);
            return 1;
        }
        Real amt = cf->amount();
        Real df  = discountCurve->discount(cf->date());
        Real contrib = amt * df;
        npv += contrib;

        perCoupon.push_back(json{
            {"index",       static_cast<int>(i)},
            {"paymentDate", dateToJson(cf->date())},
            {"cap",         cf->cap()},
            {"floor",       cf->floor()},
            {"isCapped",    cf->isCapped()},
            {"isFloored",   cf->isFloored()},
            {"amount",      amt},
            {"discount",    df},
            {"contribution", contrib}
        });
    }

    json inp{
        {"today",      dateToJson(today)},
        {"notional",   notional},
        {"caps",       caps},
        {"floors",     floors},
        {"averaging",  "Compound"},
        {"vol",        0.05},
        {"discountFlat", 0.0015}
    };
    json exp{
        {"legSize",         static_cast<int>(cflows.size())},
        {"npv",             npv},
        {"npv_test_hardcoded", 34648.328606210489},
        {"diff_vs_test",    npv - 34648.328606210489},
        {"coupons",         perCoupon}
    };
    out.addCase("compound_caps_and_floors", inp, exp);

    out.write();
    std::printf("OvernightLeg caps/floors probe: legSize=%zu npv=%.15f (test ref 34648.328606210489, diff=%.3e)\n",
                cflows.size(), npv, npv - 34648.328606210489);
    return 0;
}
