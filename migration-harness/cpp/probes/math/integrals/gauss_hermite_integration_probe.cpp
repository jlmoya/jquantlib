// migration-harness/cpp/probes/math/integrals/gauss_hermite_integration_probe.cpp
// Phase 2j.5 Track C.1 — emit C++ v1.42.1 GaussHermiteIntegration nodes
// and weights together with reference integrals so the Java port can
// cross-validate its runtime port of:
//   * GaussianOrthogonalPolynomial (3-term recurrence base)
//   * GaussHermitePolynomial       (Hermite weight w(x)=|x|^{2mu} e^{-x^2})
//   * GaussianQuadrature           (Golub-Welsch via TqrEigenDecomposition)
//   * GaussHermiteIntegration      (mu=0 wrapper)
//
// The Java port reproduces the eigendecomposition at runtime (via a Java
// port of TqrEigenDecomposition), so this probe emits node/weight tables
// for several orders, plus reference integrals against analytical values
// where available.
//
// Coverage:
//   1. nodes_weights_n{4,8,16,32} for mu=0 — orders relevant for MarkovFunctional
//      (default 32). n=64 is intentionally omitted: the outermost weights of
//      Gauss-Hermite at n=64 lie below double-precision noise floor (since
//      w(x)=exp(-x^2) ≈ 1e-49 at |x|≈10.5, and the Golub-Welsch formula
//      w_i = mu_0 * v[0,i]^2 / w(x_i) divides ≈1e-32 by ≈1e-49 — both factors
//      at the IEEE-754 cliff). At that order Java vs C++ rounding paths
//      legitimately produce different small-magnitude eigenvector
//      components, so the cross-validation would be checking noise.
//   2. nodes_weights_mu05_n16 for mu=0.5 (non-default mu path)
//   3. reference_integrals at n=16 against:
//        const_one  ∫ e^{-x^2} dx          = sqrt(pi)
//        x_squared  ∫ x^2 e^{-x^2} dx      = sqrt(pi)/2
//        cos        ∫ cos(x) e^{-x^2} dx   = sqrt(pi) * exp(-1/4)
//        polyDeg7   ∫ p(x) e^{-x^2} dx (n=4 must be exact)

#include <ql/version.hpp>
#include <ql/math/integrals/gaussianquadratures.hpp>
#include <ql/math/array.hpp>
#include "../../common.hpp"

#include <cmath>

using namespace QuantLib;
using namespace jqml_harness;

namespace {

void emitNodesWeights(ReferenceWriter& out,
                      const std::string& caseName,
                      Size n,
                      Real mu) {
    GaussHermiteIntegration q(n, mu);
    json nodes = json::array();
    json weights = json::array();
    for (Size i = 0; i < q.order(); ++i) {
        nodes.push_back(q.x()[i]);
        weights.push_back(q.weights()[i]);
    }
    out.addCase(caseName,
                json{{"order", q.order()}, {"mu", mu}},
                json{{"nodes", nodes}, {"weights", weights}});
}

double f_const_one(double /*x*/) { return 1.0; }
double f_x(double x)             { return x; }
double f_x_squared(double x)     { return x * x; }
double f_cos(double x)           { return std::cos(x); }
// degree-7 polynomial — exact for n=4 Gauss-Hermite (2n-1 = 7)
double f_poly7(double x) {
    return 1.0 + 2.0*x + 3.0*x*x - 0.5*x*x*x + 0.25*x*x*x*x
           - 0.1*x*x*x*x*x + 0.05*x*x*x*x*x*x + 0.02*x*x*x*x*x*x*x;
}

template <class F>
double integrate(const GaussHermiteIntegration& q, F fn) {
    return q(fn);
}

} // namespace

int main() {
    ReferenceWriter out("math/integrals/gauss_hermite_integration", QL_VERSION,
                        "gauss_hermite_integration_probe");

    // --- node/weight tables, mu = 0 ---
    emitNodesWeights(out, "nodes_weights_n4",  4,  0.0);
    emitNodesWeights(out, "nodes_weights_n8",  8,  0.0);
    emitNodesWeights(out, "nodes_weights_n16", 16, 0.0);
    emitNodesWeights(out, "nodes_weights_n32", 32, 0.0);

    // --- node/weight tables, mu = 0.5 (exercises GaussHermitePolynomial::mu_) ---
    emitNodesWeights(out, "nodes_weights_mu05_n16", 16, 0.5);

    // --- reference integrals at n=16, mu=0 ---
    {
        GaussHermiteIntegration q(16, 0.0);
        json integrals = json::array();
        integrals.push_back({{"name", "const_one"}, {"value", integrate(q, f_const_one)}});
        integrals.push_back({{"name", "x"},          {"value", integrate(q, f_x)}});
        integrals.push_back({{"name", "x_squared"},  {"value", integrate(q, f_x_squared)}});
        integrals.push_back({{"name", "cos"},        {"value", integrate(q, f_cos)}});
        out.addCase("reference_integrals_n16",
                    json{{"order", q.order()}, {"mu", 0.0}},
                    json{{"integrals", integrals}});
    }

    // --- exact-polynomial test at n=4 (degree 7, so it must be exact) ---
    {
        GaussHermiteIntegration q(4, 0.0);
        out.addCase("poly7_n4",
                    json{{"order", q.order()}, {"mu", 0.0}},
                    json{{"value", integrate(q, f_poly7)}});
    }

    out.write();
    return 0;
}
