// migration-harness/cpp/probes/math/interpolations/cubic_extrapolation_tail_probe.cpp
//
// Reference probe for Cubic interpolation EXTRAPOLATION past the last
// pillar (Phase 5e.5b-CFC-d-4 follow-up).
//
// Reproduces the exact 7-knot Cubic-zero-curve setup from C++ test
// overnightindexedcoupon.cpp::CommonVarsONLeg::setupForecastCurve
// (cpp:287-316), with eval=2025-06-01 and Actual/360 day count, and
// dumps interpolation.value/derivative/secondDerivative at several
// extrapolation points 0.5, 1, 2, 3, 7, 30 days past the last pillar
// (2026-06-30 → t = 394/360).
//
// The Java port produces a 6.5e-08 absolute drift for the discount
// factor at 2026-07-01 (1 day past the last pillar) which propagates
// through the OvernightLeg-with-caps-and-floors compound rate to a
// 0.067 NPV diff.  This probe ground-truths the extrapolation tail
// values so the bug can be localized in the Java CubicInterpolation.

#include <cstdio>
#include <ql/version.hpp>
#include <ql/math/interpolations/cubicinterpolation.hpp>

#include "../../common.hpp"

#include <vector>

using namespace jqml_harness;
using namespace QuantLib;

int main() {
    ReferenceWriter out("math/interpolations/cubic_extrapolation_tail",
                        QL_VERSION, "cubic_extrapolation_tail_probe");

    // Times = day-count(eval=2025-06-01 → date) / 360.0
    // (Actual/360 day count, eval=2025-06-01)
    //   2025-06-01:    0
    //   2025-07-30:   59
    //   2025-08-29:   89
    //   2025-09-30:  121
    //   2025-12-30:  212
    //   2026-03-30:  302
    //   2026-06-30:  394
    std::vector<Real> times = {
        0.0  / 360.0,
         59.0 / 360.0,
         89.0 / 360.0,
        121.0 / 360.0,
        212.0 / 360.0,
        302.0 / 360.0,
        394.0 / 360.0
    };
    std::vector<Real> zeroRates = {
        0.0434, 0.0436, 0.0431, 0.0413, 0.0390, 0.0370, 0.0348
    };

    // Match InterpolatedZeroCurve<Cubic> default construction (uses
    // Cubic() default ctor which is Kruger / monotonic=false /
    // SecondDerivative=0 / SecondDerivative=0).
    CubicInterpolation interp(
        times.begin(), times.end(), zeroRates.begin(),
        CubicInterpolation::Kruger,
        false,
        CubicInterpolation::SecondDerivative, 0.0,
        CubicInterpolation::SecondDerivative, 0.0);
    interp.enableExtrapolation();

    // Capture the cubic coefficients for transparency.
    const std::vector<Real>& a = interp.aCoefficients();
    const std::vector<Real>& b = interp.bCoefficients();
    const std::vector<Real>& c = interp.cCoefficients();
    json coefs = json::array();
    for (Size i = 0; i < a.size(); ++i) {
        coefs.push_back(json{
            {"i", static_cast<int>(i)},
            {"x_left", times[i]},
            {"x_right", times[i+1]},
            {"y_left", zeroRates[i]},
            {"y_right", zeroRates[i+1]},
            {"a", a[i]},
            {"b", b[i]},
            {"c", c[i]}
        });
    }

    // Probe extrapolation at last-pillar t and at +Δ days for several Δ.
    std::vector<int> deltaDays = {0, 1, 2, 3, 7, 30};
    json extrapPoints = json::array();
    Real tLast = 394.0 / 360.0;
    for (int d : deltaDays) {
        Real t = (394.0 + d) / 360.0;
        Real v = interp(t, true);
        Real dv = interp.derivative(t, true);
        Real ddv = interp.secondDerivative(t, true);
        Real disc = std::exp(-v * t);
        extrapPoints.push_back(json{
            {"deltaDays", d},
            {"t", t},
            {"t_minus_tLast", t - tLast},
            {"value_zero", v},
            {"derivative", dv},
            {"secondDerivative", ddv},
            {"discountFactor", disc}
        });
    }

    // Half-day point (1-day off the end is exactly the OvernightLeg
    // pain point but we also probe inside-the-last-segment values for
    // sanity).
    {
        Real t = (394.0 - 0.5) / 360.0; // half-day BEFORE last pillar
        Real v = interp(t, true);
        Real dv = interp.derivative(t, true);
        Real ddv = interp.secondDerivative(t, true);
        Real disc = std::exp(-v * t);
        extrapPoints.push_back(json{
            {"deltaDays", -0.5},
            {"t", t},
            {"t_minus_tLast", t - tLast},
            {"value_zero", v},
            {"derivative", dv},
            {"secondDerivative", ddv},
            {"discountFactor", disc}
        });
    }

    json inputs = {
        {"times", times},
        {"zeroRates", zeroRates},
        {"da", "Kruger"},
        {"monotonic", false},
        {"leftCondition", "SecondDerivative=0"},
        {"rightCondition", "SecondDerivative=0"}
    };
    json expected = {
        {"coefficients", coefs},
        {"extrapolationPoints", extrapPoints}
    };
    out.addCase("kruger_7knot_zero_curve_tail", inputs, expected);

    out.write();
    std::printf("Cubic-extrapolation-tail probe written.\n");
    std::printf("  Last segment a/b/c = %.17g  %.17g  %.17g\n",
                a.back(), b.back(), c.back());
    Real t395 = 395.0 / 360.0;
    std::printf("  At t=395/360 (1 day past last pillar):\n");
    std::printf("    zero = %.17g\n", interp(t395, true));
    std::printf("    disc = %.17g\n", std::exp(-interp(t395, true) * t395));
    return 0;
}
