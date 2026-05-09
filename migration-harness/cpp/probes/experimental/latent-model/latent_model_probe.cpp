// migration-harness/cpp/probes/experimental/latent-model/latent_model_probe.cpp
// Phase 4m.6.5 — emit C++ v1.42.1 reference values for the LatentModel template
// and its multidim integration backends so the Java port can cross-validate.
//
// Coverage:
//   1. multidim_quadrature_2d_unit  — 2D Gauss-Hermite quadrature of f(x,y)=1
//      against analytical = pi (= sqrt(pi)^2 from ∫∫ exp(-x^2-y^2) dx dy).
//   2. multidim_quadrature_2d_xy    — ∫∫ x*y exp(-x^2-y^2) dx dy = 0
//   3. multidim_quadrature_2d_x2y2  — ∫∫ x^2 * y^2 exp(-x^2-y^2) dx dy = pi/4
//   4. multidim_trapezoid_2d_unit   — 2D trapezoid integration of f=1 over
//      [-2,2]x[-2,2] = 16
//   5. latent_model_2var_2fact      — LatentModel<GaussianCopulaPolicy>
//      with factorWeights = [[0.3,0.4],[0.5,0.2]], compute idiosyncFctrs and
//      latentVariableCorrel.
//   6. latent_model_latentVarValue  — for above model and a known factor vector.
//   7. integratedExpectedValue_id   — integrated value of identity function
//      under Gaussian copula density (should be 0 for symmetric integrand).

#include <ql/version.hpp>
#include <ql/experimental/math/multidimquadrature.hpp>
#include <ql/experimental/math/multidimintegrator.hpp>
#include <ql/experimental/math/latentmodel.hpp>
#include <ql/experimental/math/gaussiancopulapolicy.hpp>
#include <ql/math/integrals/trapezoidintegral.hpp>
#include "../../common.hpp"

#include <cmath>
#include <vector>

using namespace QuantLib;
using namespace jqml_harness;

namespace {

double f_const_one_2d(const std::vector<Real>& x) {
    return 1.0;
}

double f_xy_2d(const std::vector<Real>& x) {
    return x[0] * x[1];
}

double f_x2y2_2d(const std::vector<Real>& x) {
    return x[0] * x[0] * x[1] * x[1];
}

double f_x2_2d(const std::vector<Real>& x) {
    return x[0] * x[0];
}

double f_x_3d(const std::vector<Real>& x) {
    return x[0]; // odd in x => integral against gaussian = 0
}

} // namespace

int main() {
    ReferenceWriter out("experimental/latent-model/latent_model", QL_VERSION,
                        "latent_model_probe");

    // --- 1. 2D Gauss-Hermite quadrature of f=1: integral = pi ---
    {
        GaussianQuadMultidimIntegrator integ(/*dim=*/2, /*order=*/16, /*mu=*/0.0);
        Real value = integ.integrate<Real>(f_const_one_2d);
        out.addCase("multidim_quadrature_2d_unit",
                    json{{"dim", 2}, {"order", 16}, {"mu", 0.0}, {"f", "1"}},
                    json{{"value", value}, {"analytical", M_PI}});
    }

    // --- 2. 2D Gauss-Hermite quadrature of x*y: integral = 0 ---
    {
        GaussianQuadMultidimIntegrator integ(2, 16, 0.0);
        Real value = integ.integrate<Real>(f_xy_2d);
        out.addCase("multidim_quadrature_2d_xy",
                    json{{"dim", 2}, {"order", 16}, {"mu", 0.0}, {"f", "x*y"}},
                    json{{"value", value}, {"analytical", 0.0}});
    }

    // --- 3. 2D Gauss-Hermite quadrature of x^2*y^2: integral = pi/4 ---
    {
        GaussianQuadMultidimIntegrator integ(2, 16, 0.0);
        Real value = integ.integrate<Real>(f_x2y2_2d);
        out.addCase("multidim_quadrature_2d_x2y2",
                    json{{"dim", 2}, {"order", 16}, {"mu", 0.0}, {"f", "x^2 y^2"}},
                    json{{"value", value}, {"analytical", M_PI / 4.0}});
    }

    // --- 4. 2D MultidimIntegral with TrapezoidIntegral over [-2,2]x[-2,2] of f=1 ---
    {
        std::vector<ext::shared_ptr<Integrator> > integrators;
        for (int i = 0; i < 2; ++i) {
            integrators.push_back(ext::make_shared<TrapezoidIntegral<Default> >(1.e-6, 200));
        }
        MultidimIntegral mdi(integrators);
        std::vector<Real> a(2, -2.0), b(2, 2.0);
        Real value = mdi(f_const_one_2d, a, b);
        out.addCase("multidim_trapezoid_2d_unit",
                    json{{"dim", 2}, {"a", -2.0}, {"b", 2.0}, {"tol", 1.e-6}},
                    json{{"value", value}, {"analytical", 16.0}});
    }

    // --- 5. LatentModel idiosyncratic factors + correlations ---
    {
        std::vector<std::vector<Real> > weights = {{0.3, 0.4}, {0.5, 0.2}};
        // Verify it's normalized: 0.3^2 + 0.4^2 = 0.25 < 1, 0.5^2 + 0.2^2 = 0.29 < 1. OK
        LatentModel<GaussianCopulaPolicy> lm(weights);
        json idiosyncFctrs = json::array();
        for (Size i = 0; i < lm.size(); ++i) {
            idiosyncFctrs.push_back(lm.idiosyncFctrs()[i]);
        }
        json correls = json::array();
        for (Size i = 0; i < lm.size(); ++i) {
            for (Size j = 0; j < lm.size(); ++j) {
                correls.push_back({{"i", i}, {"j", j},
                                   {"value", lm.latentVariableCorrel(i, j)}});
            }
        }
        out.addCase("latent_model_2var_2fact",
                    json{{"weights", weights}},
                    json{{"size", lm.size()},
                         {"numFactors", lm.numFactors()},
                         {"numTotalFactors", lm.numTotalFactors()},
                         {"idiosyncFctrs", idiosyncFctrs},
                         {"correls", correls}});
    }

    // --- 6. latentVarValue for fixed factor sample ---
    {
        std::vector<std::vector<Real> > weights = {{0.3, 0.4}, {0.5, 0.2}};
        LatentModel<GaussianCopulaPolicy> lm(weights);
        // numFactors = 2 (systemic), nVariables = 2 (idiosyncratic) → 4 total
        // M = [m0, m1], Z = [z0, z1] → allFactors = [m0, m1, z0, z1]
        std::vector<Real> allFactors = {0.5, -0.3, 1.2, -0.8};
        Real y0 = lm.latentVarValue(allFactors, 0);
        Real y1 = lm.latentVarValue(allFactors, 1);
        out.addCase("latent_model_latentVarValue",
                    json{{"weights", weights}, {"allFactors", allFactors}},
                    json{{"y0", y0}, {"y1", y1}});
    }

    // --- 7. Single-factor LatentModel (correlSqr ctor) ---
    {
        Real correlSqr = 0.6;
        Size nVariables = 3;
        LatentModel<GaussianCopulaPolicy> lm(correlSqr, nVariables);
        json idiosyncFctrs = json::array();
        for (Size i = 0; i < lm.size(); ++i) {
            idiosyncFctrs.push_back(lm.idiosyncFctrs()[i]);
        }
        // factorWeights[i] should be [0.6] for all i
        json fwShape = json::array();
        for (Size i = 0; i < lm.size(); ++i) {
            fwShape.push_back({{"i", i}, {"weights", lm.factorWeights()[i]}});
        }
        out.addCase("latent_model_correlSqr_ctor",
                    json{{"correlSqr", correlSqr}, {"nVariables", nVariables}},
                    json{{"size", lm.size()},
                         {"numFactors", lm.numFactors()},
                         {"idiosyncFctrs", idiosyncFctrs},
                         {"factorWeights", fwShape}});
    }

    out.write();
    return 0;
}
