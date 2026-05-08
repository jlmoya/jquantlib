// migration-harness/cpp/probes/cashflows/capped_floored_yoy_inflation_coupon_probe.cpp
// Reference values for CappedFlooredYoYInflationCoupon against QuantLib v1.42.1.
// Phase 2q L1 Track D — cross-track close-out (D.1).
//
// Builds a YYUKRPI YoY index seeded with monthly fixings and bound to a
// 6-pillar Linear-interpolated YoY curve, then constructs CappedFlooredYoY
// coupons in several scenarios:
//   PASS_*  — no cap and no floor — pure pass-through (rate == underlying yoy
//             coupon rate). This is the only scenario the Java test can
//             actually drive without a vol-dependent pricer; cap()/floor()
//             accessors return Null<Rate>(), isCapped()/isFloored() are false.
//   META_*  — cap- or floor-only constructions, no rate() probe (rate would
//             require a YoY optionlet pricer not yet ported). We probe the
//             metadata accessors: cap(), floor(), effectiveCap(),
//             effectiveFloor(), isCapped(), isFloored(), and the date_serial
//             of the underlying coupon.
//
// Tier: TIGHT for rate() pass-through (closed-form) and metadata. effectiveCap
// /effectiveFloor are TIGHT (pure (cap - spread) / gearing arithmetic).

#include <cstdio>
#include <ql/version.hpp>
#include "../common.hpp"

#include <ql/cashflows/capflooredinflationcoupon.hpp>
#include <ql/cashflows/yoyinflationcoupon.hpp>
#include <ql/cashflows/inflationcouponpricer.hpp>
#include <ql/indexes/inflation/ukrpi.hpp>
#include <ql/termstructures/inflation/interpolatedyoyinflationcurve.hpp>
#include <ql/time/calendars/unitedkingdom.hpp>
#include <ql/time/daycounters/actualactual.hpp>
#include <ql/utilities/null.hpp>

using namespace jqml_harness;
using namespace QuantLib;

namespace {
const char* interpName(CPI::InterpolationType t) {
    switch (t) {
        case CPI::AsIndex: return "AsIndex";
        case CPI::Flat:    return "Flat";
        case CPI::Linear:  return "Linear";
        default:           return "?";
    }
}
} // namespace

int main() {
    ReferenceWriter out("cashflows/capped_floored_yoy_inflation_coupon",
                        QL_VERSION, "capped_floored_yoy_inflation_coupon_probe");

    // ---------- Common setup ----------
    Date evalDate(13, August, 2007);
    Settings::instance().evaluationDate() = evalDate;

    Calendar calendar = UnitedKingdom();
    BusinessDayConvention bdc = ModifiedFollowing;
    DayCounter dc = ActualActual(ActualActual::ISDA);
    Frequency freq = Monthly;
    Period swapObsLag = Period(3, Months);

    // Build a YoY curve.
    Date refDate = calendar.adjust(evalDate, bdc);
    std::vector<Date> nodeDates = {
        Date(1, May, 2007),
        Date(13, August, 2008),
        Date(13, August, 2009),
        Date(13, August, 2010),
        Date(13, August, 2012),
        Date(13, August, 2017)
    };
    std::vector<Rate> nodeRates = { 0.025, 0.027, 0.029, 0.031, 0.034, 0.036 };
    auto yoyCurve = ext::make_shared<InterpolatedYoYInflationCurve<Linear>>(
        refDate, nodeDates, nodeRates, freq, dc);
    yoyCurve->enableExtrapolation();

    // YoY index bound to the YoY curve. Use the non-deprecated overload
    // (no `interpolated` parameter — that flag was removed in QL v1.38).
    auto yyIndex = ext::make_shared<YoYInflationIndex>(
        std::string("YY_UKRPI"),
        UKRegion(),
        false,                  // revised
        Monthly,
        Period(2, Months),
        GBPCurrency(),
        Handle<YoYInflationTermStructure>(yoyCurve));

    // Seed monthly YoY fixings 2005-01..2007-07 (constant 2.5%).
    Date fixDates[] = {
        Date(1, January,   2005), Date(1, February,  2005),
        Date(1, March,     2005), Date(1, April,     2005),
        Date(1, May,       2005), Date(1, June,      2005),
        Date(1, July,      2005), Date(1, August,    2005),
        Date(1, September, 2005), Date(1, October,   2005),
        Date(1, November,  2005), Date(1, December,  2005),
        Date(1, January,   2006), Date(1, February,  2006),
        Date(1, March,     2006), Date(1, April,     2006),
        Date(1, May,       2006), Date(1, June,      2006),
        Date(1, July,      2006), Date(1, August,    2006),
        Date(1, September, 2006), Date(1, October,   2006),
        Date(1, November,  2006), Date(1, December,  2006),
        Date(1, January,   2007), Date(1, February,  2007),
        Date(1, March,     2007), Date(1, April,     2007),
        Date(1, May,       2007), Date(1, June,      2007),
        Date(1, July,      2007),
    };
    for (size_t i = 0; i < sizeof(fixDates)/sizeof(Date); ++i) {
        yyIndex->addFixing(fixDates[i], 0.025);
    }

    // Standard YoY swaplet pricer (no nominal TS, since we need rate() not price()).
    auto pricer = ext::make_shared<YoYInflationCouponPricer>();

    // ---------------------------------------------------------------
    // Scenario PASS — pass-through (no cap, no floor): rate equals the
    // underlying YoYInflationCoupon's rate(). Probe drives the Java test
    // since this exercises the pricer-less / vol-independent path.
    // ---------------------------------------------------------------
    {
        struct Spec {
            const char* name;
            Real notional;
            Date startDate;
            Date endDate;
            Date paymentDate;
            CPI::InterpolationType obsInterp;
            Real gearing;
            Spread spread;
        };

        // All scenarios use future accrual periods (endDate > evalDate +
        // availabilityLag) so the forecast path is exercised. The Java
        // YoYInflationIndex.fixing() past-fixing branch (ratio formula) is a
        // pre-existing divergence from the v1.42.1 C++ behavior for
        // ratio_=false indices and is out of scope for Phase 2q D.1.
        std::vector<Spec> specs = {
            // PASS_1: future period, gearing=1, spread=0
            {"PASS_1_future_g1_s0",
                1000000.0,
                Date(13, August, 2008), Date(13, August, 2009),
                Date(15, August, 2009),
                CPI::AsIndex, 1.0, 0.0},
            // PASS_2: future period, gearing=1, spread=0.005
            {"PASS_2_future_g1_s50bp",
                1000000.0,
                Date(13, August, 2008), Date(13, August, 2009),
                Date(15, August, 2009),
                CPI::AsIndex, 1.0, 0.005},
            // PASS_3: future period, gearing=2, spread=0
            {"PASS_3_future_g2_s0",
                500000.0,
                Date(13, August, 2008), Date(13, August, 2009),
                Date(15, August, 2009),
                CPI::AsIndex, 2.0, 0.0},
            // PASS_4: future period, gearing=-1 (negative), spread=0.01
            {"PASS_4_future_gNeg1_s100bp",
                1000000.0,
                Date(13, August, 2008), Date(13, August, 2009),
                Date(15, August, 2009),
                CPI::AsIndex, -1.0, 0.01},
            // PASS_5: longer future period, gearing=1, spread=0
            {"PASS_5_future_long_g1_s0",
                1000000.0,
                Date(13, August, 2009), Date(13, August, 2010),
                Date(15, August, 2010),
                CPI::AsIndex, 1.0, 0.0},
        };

        for (const auto& s : specs) {
            // Build the underlying YoYInflationCoupon.
            auto underlying = ext::make_shared<YoYInflationCoupon>(
                s.paymentDate, s.notional,
                s.startDate, s.endDate,
                /* fixingDays */ 0u,
                yyIndex, swapObsLag, s.obsInterp, dc,
                s.gearing, s.spread);
            underlying->setPricer(pricer);

            // Wrap in CappedFlooredYoYInflationCoupon with no cap, no floor
            // → strict pass-through.
            CappedFlooredYoYInflationCoupon cf(underlying,
                                               Null<Rate>(), Null<Rate>());
            cf.setPricer(pricer);

            json inp{
                {"notional", s.notional},
                {"startDate_serial", (Integer)s.startDate.serialNumber()},
                {"endDate_serial", (Integer)s.endDate.serialNumber()},
                {"paymentDate_serial", (Integer)s.paymentDate.serialNumber()},
                {"observationLag_months", swapObsLag.length()},
                {"observationInterpolation", interpName(s.obsInterp)},
                {"gearing", s.gearing},
                {"spread", s.spread},
                {"cap", "null"},
                {"floor", "null"}
            };

            json exp{
                {"date_serial", (Integer)cf.date().serialNumber()},
                {"isCapped", cf.isCapped()},
                {"isFloored", cf.isFloored()},
                {"underlyingRate", cf.underlyingRate()},
                {"rate", cf.rate()},
                {"amount", cf.amount()},
                {"gearing", cf.gearing()},
                {"spread", cf.spread()}
            };

            out.addCase(s.name, inp, exp);
        }
    }

    // ---------------------------------------------------------------
    // Scenario META — capped/floored configurations: probe metadata accessors
    // only (no rate() since YoY optionlet pricer not yet ported).
    // ---------------------------------------------------------------
    {
        struct Spec {
            const char* name;
            Real notional;
            Date startDate;
            Date endDate;
            Date paymentDate;
            Real gearing;
            Spread spread;
            Real cap;     // Null<Rate>() if not capped
            Real floor;   // Null<Rate>() if not floored
        };

        std::vector<Spec> specs = {
            // META_1: cap-only, gearing=1, spread=0
            {"META_1_capOnly_g1",
                1000000.0,
                Date(13, August, 2008), Date(13, August, 2009),
                Date(15, August, 2009),
                1.0, 0.0,
                /* cap */ 0.05, /* floor */ Null<Rate>()},
            // META_2: floor-only, gearing=1, spread=0.005
            {"META_2_floorOnly_g1_s50bp",
                1000000.0,
                Date(13, August, 2008), Date(13, August, 2009),
                Date(15, August, 2009),
                1.0, 0.005,
                /* cap */ Null<Rate>(), /* floor */ 0.005},
            // META_3: collar (cap + floor), gearing=1
            {"META_3_collar_g1",
                1000000.0,
                Date(13, August, 2008), Date(13, August, 2009),
                Date(15, August, 2009),
                1.0, 0.0,
                /* cap */ 0.04, /* floor */ 0.005},
            // META_4: collar with negative gearing — caps become floors.
            {"META_4_collar_gNeg1",
                1000000.0,
                Date(13, August, 2008), Date(13, August, 2009),
                Date(15, August, 2009),
                -1.0, 0.0,
                /* cap */ 0.04, /* floor */ 0.005},
            // META_5: cap-only, gearing=2, non-zero spread (effectiveCap math)
            {"META_5_capOnly_g2_s100bp",
                500000.0,
                Date(13, August, 2008), Date(13, August, 2009),
                Date(15, August, 2009),
                2.0, 0.01,
                /* cap */ 0.07, /* floor */ Null<Rate>()},
        };

        for (const auto& s : specs) {
            auto underlying = ext::make_shared<YoYInflationCoupon>(
                s.paymentDate, s.notional,
                s.startDate, s.endDate,
                /* fixingDays */ 0u,
                yyIndex, swapObsLag, CPI::AsIndex, dc,
                s.gearing, s.spread);

            CappedFlooredYoYInflationCoupon cf(underlying, s.cap, s.floor);

            json inp{
                {"notional", s.notional},
                {"startDate_serial", (Integer)s.startDate.serialNumber()},
                {"endDate_serial", (Integer)s.endDate.serialNumber()},
                {"paymentDate_serial", (Integer)s.paymentDate.serialNumber()},
                {"observationLag_months", swapObsLag.length()},
                {"gearing", s.gearing},
                {"spread", s.spread},
                {"cap", s.cap == Null<Rate>() ? "null" : std::to_string(s.cap)},
                {"floor", s.floor == Null<Rate>() ? "null" : std::to_string(s.floor)}
            };

            json exp{
                {"date_serial", (Integer)cf.date().serialNumber()},
                {"isCapped", cf.isCapped()},
                {"isFloored", cf.isFloored()},
                {"cap", cf.cap() == Null<Rate>() ? json("null") : json(cf.cap())},
                {"floor", cf.floor() == Null<Rate>() ? json("null") : json(cf.floor())}
            };
            // effectiveCap / effectiveFloor are only meaningful when capped/floored.
            if (cf.isCapped()) {
                exp["effectiveCap"] = cf.effectiveCap();
            }
            if (cf.isFloored()) {
                exp["effectiveFloor"] = cf.effectiveFloor();
            }

            out.addCase(s.name, inp, exp);
        }
    }

    out.write();
    return 0;
}
