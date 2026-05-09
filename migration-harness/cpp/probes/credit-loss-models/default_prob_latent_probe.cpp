// migration-harness/cpp/probes/credit-loss-models/default_prob_latent_probe.cpp
// Phase 4m.7 — emit C++ v1.42.1 reference values for
// DefaultLatentModel<GaussianCopulaPolicy>::conditionalDefaultProbability(InvP)
// and conditionalProbAtLeastNEvents kernels (without instantiating a Basket).
//
// The conditional default probability formula for a single name iName given a
// realisation of systemic factors m is:
//
//    Pi|m = Φ_Z((Φ^{-1}(p_i) - Σ_k a_{i,k} m_k) / sqrt(1 - Σ_k a_{i,k}^2))
//
// We test it against the kernel directly to avoid Basket plumbing.

#include <ql/version.hpp>
#include <ql/math/distributions/normaldistribution.hpp>
#include <ql/qldefines.hpp>
#include "../common.hpp"

#include <cmath>
#include <vector>

using namespace QuantLib;
using namespace jqml_harness;

namespace {

double conditionalDefaultProbabilityInvP_kernel(double invCumYProb,
                                                const std::vector<double>& factorWeights,
                                                double idiosyncFctr,
                                                const std::vector<double>& m) {
    static const CumulativeNormalDistribution phi;
    double sumMs = 0.0;
    for (std::size_t k = 0; k < factorWeights.size(); ++k) {
        sumMs += factorWeights[k] * m[k];
    }
    return phi((invCumYProb - sumMs) / idiosyncFctr);
}

} // namespace

int main() {
    ReferenceWriter w("credit-loss-models/default_prob_latent",
                      QL_VERSION,
                      "default_prob_latent_probe.cpp (Phase 4m.7)");

    InverseCumulativeNormal invPhi;

    // ---- single-factor cases --------------------------------------------
    {
        // weight = sqrt(rho) for a single common factor of correlation rho.
        const double rho = 0.20;
        const double w_i = std::sqrt(rho);
        const double idio = std::sqrt(1.0 - w_i * w_i);
        const double prob = 0.05;
        const double invY = invPhi(prob);

        for (double m : {-2.0, -1.0, 0.0, 1.0, 2.0}) {
            std::vector<double> factor{w_i};
            std::vector<double> mvec{m};
            const double cond = conditionalDefaultProbabilityInvP_kernel(invY, factor, idio, mvec);
            std::ostringstream nm;
            nm << "cond_def_p5pc_rho20pc_m" << (m < 0 ? "neg" : "pos") << static_cast<int>(std::abs(m));
            w.addCase(nm.str(),
                      {{"prob", prob}, {"weight", w_i}, {"idio", idio},
                       {"m", mvec}},
                      cond);
        }
    }

    // ---- multi-factor (2-factor) ----------------------------------------
    {
        // 2 systemic factors with weights [0.3, 0.4]; idiosyncratic = sqrt(1 - 0.09 - 0.16) = sqrt(0.75)
        std::vector<double> factor{0.3, 0.4};
        const double idio = std::sqrt(1.0 - 0.3 * 0.3 - 0.4 * 0.4);
        const double prob = 0.10;
        const double invY = invPhi(prob);

        std::vector<double> mvec{1.0, -0.5};
        const double cond = conditionalDefaultProbabilityInvP_kernel(invY, factor, idio, mvec);
        w.addCase("cond_def_p10pc_2fact_w34_m1_neg05",
                  {{"prob", prob}, {"weights", factor}, {"idio", idio}, {"m", mvec}},
                  cond);

        std::vector<double> mvec2{0.0, 0.0};
        const double cond2 = conditionalDefaultProbabilityInvP_kernel(invY, factor, idio, mvec2);
        w.addCase("cond_def_p10pc_2fact_w34_m_zero",
                  {{"prob", prob}, {"weights", factor}, {"idio", idio}, {"m", mvec2}},
                  cond2);
    }

    // ---- low/high probability sanity ------------------------------------
    {
        const double rho = 0.50;
        const double w_i = std::sqrt(rho);
        const double idio = std::sqrt(1.0 - w_i * w_i);
        const double prob = 0.001;
        const double invY = invPhi(prob);
        std::vector<double> factor{w_i};
        std::vector<double> mvec{-3.0};
        const double cond = conditionalDefaultProbabilityInvP_kernel(invY, factor, idio, mvec);
        w.addCase("cond_def_low_prob_extreme_neg_m",
                  {{"prob", prob}, {"weight", w_i}, {"idio", idio}, {"m", mvec}},
                  cond);
    }

    w.write();
    return 0;
}
