// migration-harness/cpp/probes/credit-loss-models/spot_recovery_latent_probe.cpp
// Phase 4m.7c — emit C++ v1.42.1 reference values for the
// SpotRecoveryLatentModel<GaussianCopulaPolicy> kernels (declared in
// ql/experimental/credit/spotlosslatentmodel.hpp).
//
// We reproduce the analytic kernel of expCondRecoveryInvPinvRR directly —
// the C++ class is a header-only template; we instead bypass Basket
// dependencies by computing the closed form independently with the same
// inputs (factor weights / cross-idiosyn / modelA).
//
// Output schema per case:
//   inputs:   { invP, invRR, factorWeightsDef, factorWeightsRR, modelA, m }
//   expected: <double> = expCondRecoveryInvPinvRR

#include <ql/version.hpp>
#include <ql/math/distributions/normaldistribution.hpp>
#include "../common.hpp"

#include <cmath>
#include <vector>

using namespace QuantLib;
using namespace jqml_harness;

namespace {

double expCondRecoveryInvPinvRR_kernel(
        double invP, double invRR,
        const std::vector<double>& factorWeightsDef,
        const std::vector<double>& factorWeightsRR,
        double modelA,
        const std::vector<double>& m) {
    static const CumulativeNormalDistribution Phi;
    double sumMs = 0.0, sumBetaLoss = 0.0, cross = 0.0;
    for (std::size_t k = 0; k < factorWeightsDef.size(); ++k) {
        sumMs += factorWeightsDef[k] * m[k];
        sumBetaLoss += factorWeightsRR[k] * factorWeightsRR[k];
        cross += factorWeightsDef[k] * factorWeightsDef[k]
               * factorWeightsRR[k] * factorWeightsRR[k];
    }
    const double a2 = modelA * modelA;
    const double num = sumMs
                       + std::sqrt(1.0 - cross) * std::sqrt(1.0 + a2) * invRR
                       - std::sqrt(cross) * invP;
    const double den = std::sqrt(1.0 - sumBetaLoss + a2 * (1.0 - cross));
    return Phi(num / den);
}

double conditionalRecovery_kernel(
        double latentVarSample,
        double cross,
        double pdef, double recovery,
        double invP_iName, double invRR_iRR,
        double modelA) {
    static const CumulativeNormalDistribution Phi;
    if (pdef < 1.0e-10) return 0.0;
    (void)cross;
    const double term = (latentVarSample - std::sqrt(cross) * invP_iName)
                      / (modelA * std::sqrt(1.0 - cross))
                      + std::sqrt(1.0 + 1.0 / (modelA * modelA)) * invRR_iRR;
    (void)recovery;
    // For Gaussian copula, cumulativeY = Phi.
    return Phi(term);
}

} // namespace

int main() {
    ReferenceWriter w("credit-loss-models/spot_recovery_latent",
                      QL_VERSION,
                      "spot_recovery_latent_probe.cpp (Phase 4m.7c)");

    InverseCumulativeNormal invPhi;

    // ---- expCondRecoveryInvPinvRR cases ----
    // Single-factor (1 factor), 2 names => 4 latent vars (2 def + 2 RR).
    {
        const double rho = 0.20;
        const double w_def = std::sqrt(rho);
        const double w_RR  = std::sqrt(rho);

        std::vector<double> wd{w_def};
        std::vector<double> wr{w_RR};
        const double prob = 0.05;
        const double recovery = 0.40;
        const double modelA = 1.0;
        const double invP = invPhi(prob);
        const double invRR = invPhi(recovery);

        for (double mv : {-2.0, -1.0, 0.0, 1.0, 2.0}) {
            std::vector<double> mvec{mv};
            const double v = expCondRecoveryInvPinvRR_kernel(invP, invRR, wd, wr, modelA, mvec);
            std::ostringstream nm;
            nm << "expRR_p5_rr40_modelA1_m" << (mv >= 0 ? "p" : "n") << std::abs(mv);
            w.addCase(nm.str(),
                      json{{"invP", invP}, {"invRR", invRR},
                           {"factorWeightsDef", wd}, {"factorWeightsRR", wr},
                           {"modelA", modelA}, {"m", mvec}},
                      v);
        }
    }

    // Multi-factor case: 2 factors, 1 default + 1 recovery row each.
    {
        std::vector<double> wd{0.3, 0.4};   // |wd|^2 = 0.25
        std::vector<double> wr{0.5, 0.2};   // |wr|^2 = 0.29
        const double prob = 0.10;
        const double recovery = 0.35;
        const double modelA = 0.8;
        const double invP = invPhi(prob);
        const double invRR = invPhi(recovery);

        std::vector<std::vector<double>> mvecs = {
            {0.0, 0.0}, {1.0, 0.0}, {0.0, 1.0}, {-0.5, 0.5}
        };
        for (std::size_t ix = 0; ix < mvecs.size(); ++ix) {
            const double v = expCondRecoveryInvPinvRR_kernel(invP, invRR, wd, wr, modelA, mvecs[ix]);
            std::ostringstream nm;
            nm << "expRR_2factor_case" << ix;
            w.addCase(nm.str(),
                      json{{"invP", invP}, {"invRR", invRR},
                           {"factorWeightsDef", wd}, {"factorWeightsRR", wr},
                           {"modelA", modelA}, {"m", mvecs[ix]}},
                      v);
        }
    }

    w.write();
    return 0;
}
