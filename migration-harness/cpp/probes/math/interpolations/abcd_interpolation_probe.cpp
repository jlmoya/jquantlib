// migration-harness/cpp/probes/math/interpolations/abcd_interpolation_probe.cpp
// Reference values for AbcdInterpolation construction + calibration.
// Cross-validates the Java port of ql/math/interpolations/abcdinterpolation.hpp:
//
//   * AbcdCoeffHolder ctor — default-fill for Null<Real> guesses;
//     calibrated post-update() (a, b, c, d, error, maxError, k_).
//   * AbcdInterpolation::operator() — evaluation at knot + interior points.
//   * Abcd factory — same outputs via Interpolation::Interpolator route.
//
// Grids chosen to match canonical cap-vol calibration use cases (a small
// monotone-decreasing term structure of black vols vs. time).

#include <ql/version.hpp>
#include <ql/math/interpolations/abcdinterpolation.hpp>
#include <ql/utilities/null.hpp>
#include "../../common.hpp"

#include <vector>

using namespace QuantLib;
using namespace jqml_harness;

int main() {
    ReferenceWriter out("math/interpolations/abcd_interpolation", QL_VERSION,
                        "abcd_interpolation_probe");

    // ------------------------------------------------------------------
    // Case 1: free-fit on a 7-point cap-vol-shaped term structure.
    // All guesses default (Null<Real> -> -0.06/0.17/0.54/0.17 sentinel),
    // no fixes, no vega weighting.
    // ------------------------------------------------------------------
    {
        std::vector<Real> times = {0.5, 1.0, 2.0, 3.0, 5.0, 7.0, 10.0};
        std::vector<Real> vols  = {0.20, 0.22, 0.24, 0.23, 0.21, 0.19, 0.17};

        AbcdInterpolation abcd(times.begin(), times.end(), vols.begin(),
                               Null<Real>(), Null<Real>(),
                               Null<Real>(), Null<Real>(),
                               false, false, false, false,  // *IsFixed
                               false,                       // vegaWeighted
                               ext::shared_ptr<EndCriteria>(),
                               ext::shared_ptr<OptimizationMethod>());

        // Evaluate at knot times and at two interior points.
        std::vector<Real> eval_t = {0.5, 1.0, 1.5, 4.0, 8.0};
        std::vector<Real> eval_v;
        for (auto t : eval_t) eval_v.push_back(abcd(t));

        std::vector<Real> k_out = abcd.k();

        json inputs = {
            {"times", times},
            {"vols",  vols},
            {"aGuess", "Null<Real>"},
            {"bGuess", "Null<Real>"},
            {"cGuess", "Null<Real>"},
            {"dGuess", "Null<Real>"},
            {"aIsFixed", false},
            {"bIsFixed", false},
            {"cIsFixed", false},
            {"dIsFixed", false},
            {"vegaWeighted", false}
        };
        json expected = {
            {"a", abcd.a()},
            {"b", abcd.b()},
            {"c", abcd.c()},
            {"d", abcd.d()},
            {"rmsError", abcd.rmsError()},
            {"maxError", abcd.maxError()},
            {"eval_t", eval_t},
            {"eval_v", eval_v},
            {"k", k_out}
        };
        out.addCase("free_fit_capvol_grid", inputs, expected);
    }

    // ------------------------------------------------------------------
    // Case 2: fix d to 0.10, free a/b/c. Exercises the *IsFixed path.
    // ------------------------------------------------------------------
    {
        std::vector<Real> times = {0.25, 0.5, 1.0, 2.0, 5.0, 10.0};
        std::vector<Real> vols  = {0.30, 0.28, 0.26, 0.22, 0.18, 0.15};

        AbcdInterpolation abcd(times.begin(), times.end(), vols.begin(),
                               Null<Real>(), Null<Real>(),
                               Null<Real>(), 0.10,
                               false, false, false, true,   // dIsFixed
                               false,
                               ext::shared_ptr<EndCriteria>(),
                               ext::shared_ptr<OptimizationMethod>());

        std::vector<Real> eval_t = {0.5, 1.5, 7.0};
        std::vector<Real> eval_v;
        for (auto t : eval_t) eval_v.push_back(abcd(t));

        json inputs = {
            {"times", times},
            {"vols",  vols},
            {"aGuess", "Null<Real>"},
            {"bGuess", "Null<Real>"},
            {"cGuess", "Null<Real>"},
            {"dGuess", 0.10},
            {"aIsFixed", false},
            {"bIsFixed", false},
            {"cIsFixed", false},
            {"dIsFixed", true},
            {"vegaWeighted", false}
        };
        json expected = {
            {"a", abcd.a()},
            {"b", abcd.b()},
            {"c", abcd.c()},
            {"d", abcd.d()},
            {"rmsError", abcd.rmsError()},
            {"maxError", abcd.maxError()},
            {"eval_t", eval_t},
            {"eval_v", eval_v}
        };
        out.addCase("dfixed_at_010", inputs, expected);
    }

    // ------------------------------------------------------------------
    // Case 3: Abcd factory path -- verify Interpolator::interpolate yields
    // identical outputs to direct AbcdInterpolation construction.
    // ------------------------------------------------------------------
    {
        std::vector<Real> times = {0.5, 1.0, 2.0, 3.0, 5.0};
        std::vector<Real> vols  = {0.25, 0.24, 0.22, 0.21, 0.19};

        Abcd factory(-0.06, 0.17, 0.54, 0.17,
                     false, false, false, false,
                     false,
                     ext::shared_ptr<EndCriteria>(),
                     ext::shared_ptr<OptimizationMethod>());
        Interpolation interp = factory.interpolate(times.begin(), times.end(), vols.begin());

        // Cast to AbcdInterpolation to read a/b/c/d. C++ uses static cast of
        // the impl_; the public AbcdInterpolation API exposes the inspectors.
        // For the probe we sidestep that by constructing an AbcdInterpolation
        // directly with the same params and asserting op() agreement at the
        // same evaluation grid.
        AbcdInterpolation abcd(times.begin(), times.end(), vols.begin(),
                               -0.06, 0.17, 0.54, 0.17,
                               false, false, false, false,
                               false,
                               ext::shared_ptr<EndCriteria>(),
                               ext::shared_ptr<OptimizationMethod>());

        std::vector<Real> eval_t = {0.5, 1.5, 2.5, 4.0};
        std::vector<Real> factory_v, direct_v;
        for (auto t : eval_t) {
            factory_v.push_back(interp(t));
            direct_v.push_back(abcd(t));
        }

        json inputs = {
            {"times", times},
            {"vols",  vols},
            {"aGuess", -0.06},
            {"bGuess",  0.17},
            {"cGuess",  0.54},
            {"dGuess",  0.17}
        };
        json expected = {
            {"a", abcd.a()},
            {"b", abcd.b()},
            {"c", abcd.c()},
            {"d", abcd.d()},
            {"eval_t",     eval_t},
            {"factory_v",  factory_v},
            {"direct_v",   direct_v}
        };
        out.addCase("factory_vs_direct", inputs, expected);
    }

    out.write();
    return 0;
}
