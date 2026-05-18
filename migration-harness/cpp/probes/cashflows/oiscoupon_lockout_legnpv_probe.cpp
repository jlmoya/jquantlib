// migration-harness/cpp/probes/cashflows/oiscoupon_lockout_legnpv_probe.cpp
//
// Reference probe for the C++ test
// overnightindexedcoupon.cpp:1018-1036 testOvernightLegNPV
// (Phase 5e.5b-CFC-d follow-up: un-ignore the lockout=3 + telescopic=true
// leg-NPV case in the Java mirror).
//
// Replicates the exact CommonVarsONLeg fixture + setupForecastCurve():
//   eval date         = 1-Jun-2025
//   notional          = 1,000,000
//   schedule          = quarterly, 1-Jul-2025 -> 1-Jul-2026, US-GovBond, ModFollowing
//   past fixings      = 43 SOFR fixings 2-Jun-2025..1-Aug-2025
//   forecast curve    = InterpolatedZeroCurve<Cubic> over 7 (date,zeroRate) pairs,
//                       Actual/360, US-SOFR calendar, extrapolation enabled
//   discount curve    = flatRate(0.0015, Actual/360)
//   leg config        = makeLeg(Null, lockoutDays=3, observationShift=false,
//                               telescopicValueDates=true, RateAveraging::Compound)
//   pricer            = default (no Black caps/floors here — plain compounding)
//
// Dumps:
//   * total NPV                 (= sum coupon.amount * discount(coupon.date))
//   * per-coupon: amount, paymentDate, discount, contribution, accrualPeriod,
//                 rate, fixingDates_size, valueDate_first, valueDate_last
//   * legSize
//
// Used by Java test
// org.jquantlib.testsuite.cashflows.OvernightIndexedCouponTest#testOvernightLegNPV

#include <cstdio>
#include <ql/version.hpp>
#include "../common.hpp"

#include <ql/cashflows/overnightindexedcoupon.hpp>
#include <ql/cashflows/couponpricer.hpp>
#include <ql/indexes/ibor/sofr.hpp>
#include <ql/math/interpolations/cubicinterpolation.hpp>
#include <ql/termstructures/yield/zerocurve.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
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
    ReferenceWriter out("cashflows/oiscoupon_lockout_legnpv",
                        QL_VERSION, "oiscoupon_lockout_legnpv_probe");

    // -------- CommonVarsONLeg fixture (cpp:184-319) --------
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

    // -------- Build leg:
    // makeLeg(Null<Natural>(), 3, false, true, RateAveraging::Compound)
    // i.e. no lookback override, 3 lockout days, no observationShift,
    // telescopicValueDates=true, compound averaging, no gearings/spreads/caps/floors.
    OvernightLeg leg(legSchedule, sofr);
    leg.withNotionals(notional)
       .withPaymentDayCounter(dc)
       .withAveragingMethod(RateAveraging::Compound)
       .withLockoutDays(3)
       .withObservationShift(false)
       .withTelescopicValueDates(true);

    Leg cflows = leg;

    // -------- Per-coupon assertions + NPV accumulation --------
    Real npv = 0.0;
    json perCoupon = json::array();
    for (Size i = 0; i < cflows.size(); ++i) {
        auto cf = ext::dynamic_pointer_cast<OvernightIndexedCoupon>(cflows[i]);
        if (!cf) {
            std::fprintf(stderr,
                "FATAL: leg[%zu] is NOT an OvernightIndexedCoupon\n", i);
            return 1;
        }
        Real amt = cf->amount();
        Real df  = discountCurve->discount(cf->date());
        Real contrib = amt * df;
        npv += contrib;

        const auto& fxd = cf->fixingDates();
        const auto& vd  = cf->valueDates();

        perCoupon.push_back(json{
            {"index",           static_cast<int>(i)},
            {"paymentDate",     dateToJson(cf->date())},
            {"accrualStart",    dateToJson(cf->accrualStartDate())},
            {"accrualEnd",      dateToJson(cf->accrualEndDate())},
            {"accrualPeriod",   cf->accrualPeriod()},
            {"rate",            cf->rate()},
            {"amount",          amt},
            {"discount",        df},
            {"contribution",    contrib},
            {"fixingDates_size", static_cast<int>(fxd.size())},
            {"fixingDates_first", dateToJson(fxd.front())},
            {"fixingDates_last",  dateToJson(fxd.back())},
            {"valueDate_first", dateToJson(vd.front())},
            {"valueDate_last",  dateToJson(vd.back())}
        });
    }

    json inp{
        {"today",        dateToJson(today)},
        {"notional",     notional},
        {"lookbackDays", "Null<Natural>"},
        {"lockoutDays",  3},
        {"observationShift", false},
        {"telescopicValueDates", true},
        {"averaging",    "Compound"},
        {"discountFlat", 0.0015}
    };
    json exp{
        {"legSize",            static_cast<int>(cflows.size())},
        {"npv",                npv},
        {"npv_cpp_hardcoded",  34883.949669756257},
        {"diff_vs_cpp",        npv - 34883.949669756257},
        {"coupons",            perCoupon}
    };
    out.addCase("compound_lockout3_telescopic", inp, exp);

    out.write();
    std::printf("OvernightLeg NPV probe (lockout=3, telescopic=true): legSize=%zu npv=%.15f (cpp ref 34883.949669756257, diff=%.3e)\n",
                cflows.size(), npv, npv - 34883.949669756257);
    return 0;
}
