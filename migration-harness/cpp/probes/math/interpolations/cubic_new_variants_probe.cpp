// Reference probe for 4 missing CubicInterpolation derivative variants (v1.42.1).
// Generates reference values for:
//   1. CubicInterpolation::Harmonic           -> HarmonicCubic
//   2. CubicInterpolation::SplineOM1          -> CubicSplineOvershootingMinimization1
//   3. CubicInterpolation::SplineOM2          -> CubicSplineOvershootingMinimization2
//   4. CubicInterpolation::Harmonic via log   -> HarmonicLogCubic
//
// Uses three test grids:
//   - Grid A: 5-point monotonic increasing
//   - Grid B: 7-point non-monotonic (sign changes)
//   - Grid C: 5-point monotonic decreasing positive (for log)
//
// BUILD (standalone, NOT via harness common.hpp framework):
//   cd /tmp
//   c++ -std=c++17 -O2 \
//     -I/opt/homebrew/Cellar/quantlib/1.42.1/include \
//     -I/opt/homebrew/opt/boost/include \
//     -L/opt/homebrew/Cellar/quantlib/1.42.1/lib \
//     cubic_new_variants_probe.cpp -lQuantLib -o cubic_new_variants_probe
//   ./cubic_new_variants_probe > cubic_new_variants_probe.out
//
// Output captured in migration-harness/references/math/interpolations/
// cubic_new_variants.txt — those values are hard-coded into the Java test
// CubicInterpolationNewVariantsTest at TIGHT (1e-12) tolerance.

#include <cstdio>
#include <vector>
#include <iomanip>
#include <iostream>
#include <ql/math/interpolations/cubicinterpolation.hpp>
#include <ql/math/interpolations/loginterpolation.hpp>

using namespace QuantLib;

static void dump(const char* label,
                 const std::vector<Real>& x,
                 const std::vector<Real>& y,
                 CubicInterpolation::DerivativeApprox da,
                 const std::vector<Real>& probes) {
    CubicInterpolation interp(x.begin(), x.end(), y.begin(),
        da, false,
        CubicInterpolation::SecondDerivative, 0.0,
        CubicInterpolation::SecondDerivative, 0.0);

    std::cout << "=== " << label << " ===\n";

    // a/b/c coefficients
    const std::vector<Real>& a = interp.aCoefficients();
    const std::vector<Real>& b = interp.bCoefficients();
    const std::vector<Real>& c = interp.cCoefficients();
    std::cout << "n_segments=" << a.size() << "\n";
    for (Size i = 0; i < a.size(); ++i) {
        std::cout << "  seg[" << i << "] a=" << std::setprecision(17) << a[i]
                  << " b=" << std::setprecision(17) << b[i]
                  << " c=" << std::setprecision(17) << c[i] << "\n";
    }
    for (Real p : probes) {
        Real v  = interp(p, true);
        Real dv = interp.derivative(p, true);
        Real ddv = interp.secondDerivative(p, true);
        Real prim = interp.primitive(p, true);
        std::cout << "  probe x=" << std::setprecision(17) << p
                  << " value=" << std::setprecision(17) << v
                  << " deriv=" << std::setprecision(17) << dv
                  << " sderiv=" << std::setprecision(17) << ddv
                  << " prim=" << std::setprecision(17) << prim
                  << "\n";
    }
}

static void dumpHarmonicLog(const char* label,
                            const std::vector<Real>& x,
                            const std::vector<Real>& y,
                            const std::vector<Real>& probes) {
    LogCubicInterpolation interp(x.begin(), x.end(), y.begin(),
        CubicInterpolation::Harmonic, false,
        CubicInterpolation::SecondDerivative, 0.0,
        CubicInterpolation::SecondDerivative, 0.0);

    std::cout << "=== " << label << " ===\n";
    for (Real p : probes) {
        Real v  = interp(p, true);
        Real dv = interp.derivative(p, true);
        Real ddv = interp.secondDerivative(p, true);
        std::cout << "  probe x=" << std::setprecision(17) << p
                  << " value=" << std::setprecision(17) << v
                  << " deriv=" << std::setprecision(17) << dv
                  << " sderiv=" << std::setprecision(17) << ddv
                  << "\n";
    }
}

int main() {
    // ---- Grid A: 5-point monotonic increasing ----
    std::vector<Real> xa = {0.0, 1.0, 2.0, 3.0, 4.0};
    std::vector<Real> ya = {1.0, 1.5, 2.5, 4.0, 6.0};
    std::vector<Real> pa = {0.25, 0.5, 1.5, 2.5, 3.5};

    // ---- Grid B: 7-point non-monotonic (sign changes for Harmonic edge) ----
    std::vector<Real> xb = {0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0};
    std::vector<Real> yb = {0.5, 0.9, 1.3, 1.2, 1.0, 1.4, 1.7};
    std::vector<Real> pb = {0.5, 1.5, 2.5, 3.5, 4.5, 5.5};

    // ---- Grid C: positive y (for LogCubic) ----
    std::vector<Real> xc = {0.0, 1.0, 2.0, 3.0, 4.0};
    std::vector<Real> yc = {1.0, 0.95, 0.90, 0.80, 0.78};
    std::vector<Real> pc = {0.25, 0.5, 1.5, 2.5, 3.5};

    // Grid A: all 3 algorithms
    dump("Harmonic / Grid A", xa, ya, CubicInterpolation::Harmonic, pa);
    dump("SplineOM1 / Grid A", xa, ya, CubicInterpolation::SplineOM1, pa);
    dump("SplineOM2 / Grid A", xa, ya, CubicInterpolation::SplineOM2, pa);

    // Grid B: all 3 algorithms
    dump("Harmonic / Grid B", xb, yb, CubicInterpolation::Harmonic, pb);
    dump("SplineOM1 / Grid B", xb, yb, CubicInterpolation::SplineOM1, pb);
    dump("SplineOM2 / Grid B", xb, yb, CubicInterpolation::SplineOM2, pb);

    // Grid C: HarmonicLogCubic
    dumpHarmonicLog("HarmonicLogCubic / Grid C", xc, yc, pc);

    return 0;
}
