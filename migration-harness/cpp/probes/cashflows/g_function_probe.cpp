// migration-harness/cpp/probes/cashflows/g_function_probe.cpp
//
// Reference values for GFunctionFactory variants + AnalyticHaganPricer +
// NumericHaganPricer in ql/cashflows/conundrumpricer.{hpp,cpp} against
// QuantLib v1.42.1. Phase 5e.6.
//
// Three variants of CMS-coupon G(x):
//   - GFunctionStandard(q, delta, swapLength)  (Hagan eq. 3.5b, pure analytic)
//   - GFunctionExactYield(coupon)              (built from a CMS coupon)
//   - GFunctionWithShifts(coupon, meanReversion)
//
// For each variant, the probe samples G, G', G'' at a handful of x values
// and writes them as the reference for the corresponding Java unit test.
//
// Plus AnalyticHaganPricer.swapletPrice / capletPrice / floorletPrice
// against a constant-vol shifted-lognormal swaption surface, and the
// same triple via NumericHaganPricer (fixed evaluation date, fixed
// curves -> deterministic outputs).
//
// The Java side rebuilds the same CmsCoupon under identical conventions
// and asserts at TIGHT tolerance (1e-12 rel) for analytic and LOOSE
// (1e-6) for numeric.

#include <cstdio>
#include <ql/version.hpp>
#include "../common.hpp"

#include <ql/cashflows/cmscoupon.hpp>
#include <ql/cashflows/conundrumpricer.hpp>
#include <ql/indexes/swap/euriborswap.hpp>
#include <ql/indexes/ibor/euribor.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/time/calendars/target.hpp>
#include <ql/time/daycounters/actual360.hpp>

using namespace jqml_harness;
using namespace QuantLib;

int main() {
    ReferenceWriter out("cashflows/g_function",
                        QL_VERSION, "g_function_probe");

    // ====================================================================
    // 1. GFunctionStandard — pure analytic, no curves required.
    // ====================================================================
    // Hagan eq. 3.5b at q=2 (semi-annual), delta=0.5 (mid-period), swap
    // length 5y, sampled at x={0.01, 0.025, 0.05, 0.10}.
    {
        const Size q = 2;
        const Real delta = 0.5;
        const Size swapLength = 5;
        ext::shared_ptr<GFunction> g =
            GFunctionFactory::newGFunctionStandard(q, delta, swapLength);

        const std::vector<Real> xs = {0.01, 0.025, 0.05, 0.10};
        for (Real x : xs) {
            json inp{
                {"variant",    "Standard"},
                {"q",          q},
                {"delta",      delta},
                {"swapLength", swapLength},
                {"x",          x}
            };
            json exp{
                {"G",  (*g)(x)},
                {"G1", g->firstDerivative(x)},
                {"G2", g->secondDerivative(x)}
            };
            char name[64];
            std::snprintf(name, sizeof(name),
                "standard_q%zu_d%.1f_n%zu_x%g",
                q, delta, swapLength, x);
            out.addCase(name, inp, exp);
        }
    }

    // ====================================================================
    // Build a CMS coupon for the ExactYield + WithShifts probes.
    // ====================================================================
    // Single-curve flat-forward setup at 4% Act/360 continuous.
    Date evalDate(28, January, 2010);
    Settings::instance().evaluationDate() = evalDate;

    Calendar calendar = TARGET();
    DayCounter dc = Actual360();

    Handle<YieldTermStructure> rateCurve(
        ext::make_shared<FlatForward>(evalDate, 0.04, dc, Continuous));

    ext::shared_ptr<IborIndex> euribor6m =
        ext::make_shared<Euribor6M>(rateCurve);
    ext::shared_ptr<SwapIndex> swapIndex =
        ext::make_shared<EuriborSwapIsdaFixA>(Period(10, Years), rateCurve);

    Date startDate = calendar.advance(evalDate, Period(2, Years), Following);
    Date endDate = calendar.advance(startDate, Period(6, Months), Following);
    Date paymentDate = endDate;

    ext::shared_ptr<CmsCoupon> cmsCoupon = ext::make_shared<CmsCoupon>(
        paymentDate, 100000.0,
        startDate, endDate,
        2,                       // fixingDays
        swapIndex,
        1.0,                     // gearing
        0.0,                     // spread
        Date(), Date(),          // refPeriod (unused)
        dc,
        false                    // isInArrears
    );

    // ====================================================================
    // 2. GFunctionExactYield(cmsCoupon)
    // ====================================================================
    {
        ext::shared_ptr<GFunction> g =
            GFunctionFactory::newGFunctionExactYield(*cmsCoupon);

        const std::vector<Real> xs = {0.01, 0.025, 0.05, 0.10};
        for (Real x : xs) {
            json inp{
                {"variant",    "ExactYield"},
                {"x",          x}
            };
            json exp{
                {"G",  (*g)(x)},
                {"G1", g->firstDerivative(x)},
                {"G2", g->secondDerivative(x)}
            };
            char name[64];
            std::snprintf(name, sizeof(name),
                "exact_yield_x%g", x);
            out.addCase(name, inp, exp);
        }
    }

    // ====================================================================
    // 3. GFunctionWithShifts(cmsCoupon, meanReversion)
    // ====================================================================
    // Two mean-reversion levels: zero (parallel-shift case) and 0.03
    // (typical Hull-White MR).
    {
        const std::vector<Real> meanReversions = {0.0, 0.03};
        const std::vector<Real> xs = {0.01, 0.025, 0.05, 0.10};
        for (Real mr : meanReversions) {
            Handle<Quote> meanReversion(ext::make_shared<SimpleQuote>(mr));
            ext::shared_ptr<GFunction> g =
                GFunctionFactory::newGFunctionWithShifts(*cmsCoupon, meanReversion);

            for (Real x : xs) {
                json inp{
                    {"variant",       "WithShifts"},
                    {"meanReversion", mr},
                    {"x",             x}
                };
                json exp{
                    {"G",  (*g)(x)},
                    {"G1", g->firstDerivative(x)},
                    {"G2", g->secondDerivative(x)}
                };
                char name[64];
                std::snprintf(name, sizeof(name),
                    "with_shifts_mr%g_x%g", mr, x);
                out.addCase(name, inp, exp);
            }
        }
    }

    // ====================================================================
    // 4. AnalyticHaganPricer + NumericHaganPricer integration smoke.
    //
    // C++ NumericHaganPricer is heavily curve-dependent and the Hagan
    // initialize() path is brittle to setup mismatches between probe and
    // Java replicated rig. Phase 5e.6 ports the pricer machinery; the
    // swapletPrice/capletPrice/floorletPrice cross-validation is deferred
    // to Phase 5e.6b once CmsTest is un-ignored against EuriborSwapIsdaFixA.
    //
    // The Java test side does an analytic-vs-numeric self-validation
    // (ATM swaplet rate / capletPrice agreement) which is the strongest
    // self-test we can make at this point.
    // ====================================================================

    out.write();
    return 0;
}
