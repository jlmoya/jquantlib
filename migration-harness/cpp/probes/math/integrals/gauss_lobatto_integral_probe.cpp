// migration-harness/cpp/probes/math/integrals/gauss_lobatto_integral_probe.cpp
// Phase 2f WI-3 C.3 — emit C++ v1.42.1 GaussLobattoIntegral reference
// values for several test functions across smooth, oscillating, and
// edge-case regimes. The Java port (org.jquantlib.math.integrals
// .GaussLobattoIntegral) cross-validates against these.

#include <ql/version.hpp>
#include <ql/math/integrals/gausslobattointegral.hpp>
#include <ql/utilities/null.hpp>
#include "../../common.hpp"

#include <cmath>

using namespace QuantLib;
using namespace jqml_harness;

namespace {

double poly(double x)        { return x*x*x - 2.0*x*x + x + 1.0; }
double sin_x(double x)       { return std::sin(x); }
double exp_neg_xsq(double x) { return std::exp(-x*x); }
double oneOver(double x)     { return 1.0 / (1.0 + 25.0 * x * x); } // Runge function
double sqrtFn(double x)      { return std::sqrt(x); }                // mild endpoint

} // namespace

int main() {
    ReferenceWriter out("math/integrals/gauss_lobatto_integral", QL_VERSION,
                        "gauss_lobatto_integral_probe");

    // Default ctor variant used by HestonProcess BroadieKayaLobatto:
    //   GaussLobattoIntegral(Null<Size>(), eps)
    // i.e. no iteration cap and absAccuracy=eps. We reproduce that
    // exactly so the Heston integration shares its tolerance.
    GaussLobattoIntegral hestonStyle(/*maxIters=*/Null<Size>(), /*absAcc=*/1e-4);

    // Tighter "general purpose" instance for the smooth-function checks.
    GaussLobattoIntegral tight(100000, 1e-12);

    json arr = json::array();
    arr.push_back({{"name","poly_0_2"},        {"value", tight(poly,        0.0,  2.0)}});
    arr.push_back({{"name","sin_0_pi"},        {"value", tight(sin_x,       0.0,  M_PI)}});
    arr.push_back({{"name","exp_neg_xsq_-2_2"},{"value", tight(exp_neg_xsq, -2.0, 2.0)}});
    arr.push_back({{"name","runge_-1_1"},      {"value", tight(oneOver,    -1.0,  1.0)}});
    arr.push_back({{"name","sqrt_0_1"},        {"value", tight(sqrtFn,      0.0,  1.0)}});
    // Heston-style call (loose abs accuracy, no iteration cap).
    arr.push_back({{"name","heston_runge_eps1e-4"},
                   {"value", hestonStyle(oneOver, -1.0, 1.0)}});

    out.addCase("reference_integrals",
                json{{"count", arr.size()}},
                json{{"integrals", arr}});

    out.write();
    return 0;
}
