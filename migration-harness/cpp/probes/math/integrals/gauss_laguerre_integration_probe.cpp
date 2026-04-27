// migration-harness/cpp/probes/math/integrals/gauss_laguerre_integration_probe.cpp
// Phase 2f WI-3 C.2 — emit the C++ v1.42.1 GaussLaguerreIntegration(128)
// nodes and weights as JSON so the Java port can embed them as static
// final double[] arrays and reproduce bit-exactly the C++ quadrature
// sum for any integrand. Also runs three reference integrals at the
// 128-node order so the Java side can fingerprint the dot-product loop.
//
// The C++ build path of the nodes/weights is via TqrEigenDecomposition
// over a Jacobi tridiagonal system; reproducing that in Java would add
// ~500 LOC of eigensolver infrastructure. Embedding the precomputed
// table is a deliberate trade-off documented in phase2f-design C.2.

#include <ql/version.hpp>
#include <ql/math/integrals/gaussianquadratures.hpp>
#include <ql/math/array.hpp>
#include "../../common.hpp"

#include <cmath>

using namespace QuantLib;
using namespace jqml_harness;

namespace {

double f_const_one(double /*x*/)        { return 1.0; }            // ∫₀^∞ e^{-x} dx = 1
double f_x(double x)                    { return x; }              // ∫₀^∞ x·e^{-x} dx = 1
double f_xsquared(double x)             { return x*x; }            // ∫₀^∞ x²·e^{-x} dx = 2
double f_cos(double x)                  { return std::cos(x); }    // ∫₀^∞ cos(x)·e^{-x} dx = 0.5

template <class Fn>
double integrate(const GaussLaguerreIntegration& q, Fn fn) {
    return q(fn);
}

} // namespace

int main() {
    ReferenceWriter out("math/integrals/gauss_laguerre_integration", QL_VERSION,
                        "gauss_laguerre_integration_probe");

    GaussLaguerreIntegration q(128);

    // --- table of nodes/weights ---
    json nodes = json::array();
    json weights = json::array();
    for (Size i = 0; i < q.order(); ++i) {
        nodes.push_back(q.x()[i]);
        weights.push_back(q.weights()[i]);
    }
    out.addCase("nodes_weights_n128",
                json{{"order", q.order()}},
                json{{"nodes", nodes}, {"weights", weights}});

    // --- reference integrals at n=128 ---
    json integrals = json::array();
    integrals.push_back({{"name", "const_one"},   {"value", integrate(q, f_const_one)}});
    integrals.push_back({{"name", "x"},           {"value", integrate(q, f_x)}});
    integrals.push_back({{"name", "x_squared"},   {"value", integrate(q, f_xsquared)}});
    integrals.push_back({{"name", "cos"},         {"value", integrate(q, f_cos)}});
    out.addCase("reference_integrals_n128",
                json{{"order", q.order()}},
                json{{"integrals", integrals}});

    out.write();
    return 0;
}
