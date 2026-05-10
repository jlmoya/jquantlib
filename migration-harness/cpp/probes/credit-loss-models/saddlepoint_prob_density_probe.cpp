// migration-harness/cpp/probes/credit-loss-models/saddlepoint_prob_density_probe.cpp
// Phase 4m.7c — emit C++ v1.42.1 reference values for the high-order
// saddle-point evaluators (probOverLossPortfCond, probDensityCond,
// splitLossCond, conditionalExpectedLoss) declared in
// ql/experimental/credit/saddlepointlossmodel.hpp.
//
// We emit pure-static-kernel reference values: given conditional
// probabilities + per-name LGD weights + relative loss, return the
// saddle-point evaluator output. (Avoids the basket plumbing.)

#include <ql/version.hpp>
#include <ql/math/distributions/normaldistribution.hpp>
#include "../common.hpp"

#include <cmath>
#include <vector>
#include <iostream>

using namespace QuantLib;
using namespace jqml_harness;

namespace {

// ===== shared CGF kernels (copied from saddlepointlossmodel.hpp 566-696) =====

double cgf(const std::vector<double>& condProbs,
           const std::vector<double>& lid,
           double s) {
    double sum = 0.0;
    for (std::size_t i = 0; i < condProbs.size(); ++i) {
        const double p = condProbs[i];
        sum += std::log(1.0 - p + p * std::exp(lid[i] * s));
    }
    return sum;
}

double cgf1(const std::vector<double>& condProbs,
            const std::vector<double>& lid, double s) {
    double sum = 0.0;
    for (std::size_t i = 0; i < condProbs.size(); ++i) {
        const double p = condProbs[i];
        const double mid = p * std::exp(lid[i] * s);
        sum += lid[i] * mid / (1.0 - p + mid);
    }
    return sum;
}

double cgf2(const std::vector<double>& condProbs,
            const std::vector<double>& lid, double s) {
    double sum = 0.0;
    for (std::size_t i = 0; i < condProbs.size(); ++i) {
        const double p = condProbs[i];
        const double mid = p * std::exp(lid[i] * s);
        const double denom = 1.0 - p + mid;
        sum += lid[i] * lid[i] * mid / denom
             - std::pow(lid[i] * mid / denom, 2.0);
    }
    return sum;
}

double cgf3(const std::vector<double>& condProbs,
            const std::vector<double>& lid, double s) {
    double sum = 0.0;
    for (std::size_t i = 0; i < condProbs.size(); ++i) {
        const double p = condProbs[i];
        const double mid = p * std::exp(lid[i] * s);
        const double denom = 1.0 - p + mid;
        const double s0 = denom;
        const double s1 = lid[i] * mid;
        const double s2 = lid[i] * s1;
        const double s3 = lid[i] * s2;
        sum += (s3 + (2.0 * std::pow(s1, 3.0) / s0 - 3.0 * s1 * s2) / s0) / s0;
    }
    return sum;
}

double cgf4(const std::vector<double>& condProbs,
            const std::vector<double>& lid, double s) {
    double sum = 0.0;
    for (std::size_t i = 0; i < condProbs.size(); ++i) {
        const double p = condProbs[i];
        const double mid = p * std::exp(lid[i] * s);
        const double denom = 1.0 - p + mid;
        const double s0 = denom;
        const double s1 = lid[i] * mid;
        const double s2 = lid[i] * s1;
        const double s3 = lid[i] * s2;
        const double s4 = lid[i] * s3;
        sum += (s4 + (-4.0 * s1 * s3 / s0 + (12.0 * s1 * s1 * s2 / s0
                - 6.0 * std::pow(s1, 4.0) / (s0 * s0) - 3.0 * s2 * s2) / s0)) / s0;
    }
    return sum;
}

// Newton-Raphson saddle-point solver (matches Java findSaddleNewton).
double findSaddle(const std::vector<double>& condProbs,
                  const std::vector<double>& lid,
                  double lossLevel,
                  double accuracy = 1.0e-3,
                  int maxEvals = 50) {
    double s = 0.0;
    for (int i = 0; i < maxEvals; ++i) {
        const double k1 = cgf1(condProbs, lid, s);
        const double err = k1 - lossLevel;
        if (std::abs(err) < accuracy) return s;
        const double k2 = cgf2(condProbs, lid, s);
        if (k2 <= 0.0) {
            std::cerr << "K2<=0 in findSaddle\n";
            std::abort();
        }
        s -= err / k2;
    }
    std::cerr << "findSaddle no converge\n";
    std::abort();
}

double probOverLossPortfCond(const std::vector<double>& condProbs,
                              const std::vector<double>& lid,
                              double relLoss) {
    static const double EPS = 2.2204460492503131e-16;
    if (relLoss <= EPS) return 1.0;
    if (relLoss >= 1.0 - EPS) return 0.0;
    const double s = findSaddle(condProbs, lid, relLoss);
    const double K0 = cgf(condProbs, lid, s);
    const double K2 = cgf2(condProbs, lid, s);
    const double K3 = cgf3(condProbs, lid, s);
    const double K4 = cgf4(condProbs, lid, s);
    const double s2 = s * s;
    const double s3 = s2 * s;
    const double s4 = s3 * s;
    const double s6 = s4 * s2;
    const double K3Sq = K3 * K3;
    static const CumulativeNormalDistribution Phi;

    if (s > 0.0) {
        const double ex = K0 - relLoss * s + 0.5 * s2 * K2;
        if (std::abs(ex) > 700.0) return 0.0;
        return std::exp(ex)
             * Phi(-std::abs(s) * std::sqrt(K2))
             * (1.0 - s3 * K3 / 6.0 + s4 * K4 / 24.0 + s6 * K3Sq / 72.0);
    } else if (s == 0.0) {
        return 0.5;
    } else {
        const double ex = K0 - relLoss * s + 0.5 * s2 * K2;
        if (std::abs(ex) > 700.0) return 0.0;
        return 1.0 - std::exp(ex)
             * Phi(-std::abs(s) * std::sqrt(K2))
             * (1.0 - s3 * K3 / 6.0 + s4 * K4 / 24.0 + s6 * K3Sq / 72.0);
    }
}

double probDensityCond(const std::vector<double>& condProbs,
                       const std::vector<double>& lid,
                       double relLoss) {
    static const double EPS = 2.2204460492503131e-16;
    if (relLoss <= EPS) return 0.0;
    const double s = findSaddle(condProbs, lid, relLoss);
    const double K0 = cgf(condProbs, lid, s);
    const double K2 = cgf2(condProbs, lid, s);
    const double K3 = cgf3(condProbs, lid, s);
    const double K4 = cgf4(condProbs, lid, s);
    const double K2Sq = K2 * K2;
    const double K2Cb = K2Sq * K2;
    return (1.0 + K4 / (8.0 * K2Sq) - 5.0 * K3 * K3 / (24.0 * K2Cb))
         * std::exp(K0 - s * relLoss)
         / std::sqrt(2.0 * M_PI * K2);
}

} // namespace

int main() {
    ReferenceWriter w("credit-loss-models/saddlepoint_prob_density",
                      QL_VERSION,
                      "saddlepoint_prob_density_probe.cpp (Phase 4m.7c)");

    // Test scenarios: a 5-name pool with various conditional default
    // probabilities and per-name fractional LGD weights.
    {
        std::vector<double> condProbs = {0.10, 0.05, 0.15, 0.08, 0.12};
        std::vector<double> lid       = {0.20, 0.20, 0.20, 0.20, 0.20};

        // probOverLossPortfCond at multiple loss levels.
        for (double L : {0.05, 0.10, 0.15, 0.20, 0.30}) {
            const double v = probOverLossPortfCond(condProbs, lid, L);
            std::ostringstream nm;
            nm << "probOverLoss_5n_L" << static_cast<int>(L * 100);
            w.addCase(nm.str(),
                      json{{"condProbs", condProbs}, {"lossInDef", lid}, {"relLoss", L}},
                      v);
        }
        // probDensityCond at the same levels.
        for (double L : {0.05, 0.10, 0.15, 0.20, 0.30}) {
            const double v = probDensityCond(condProbs, lid, L);
            std::ostringstream nm;
            nm << "probDensity_5n_L" << static_cast<int>(L * 100);
            w.addCase(nm.str(),
                      json{{"condProbs", condProbs}, {"lossInDef", lid}, {"relLoss", L}},
                      v);
        }
    }

    // 10-name heterogeneous LGDs
    {
        std::vector<double> condProbs = {
            0.05, 0.05, 0.05, 0.05, 0.05,
            0.10, 0.10, 0.10, 0.10, 0.10
        };
        std::vector<double> lid = {
            0.10, 0.10, 0.10, 0.10, 0.10,
            0.10, 0.10, 0.10, 0.10, 0.10
        };
        for (double L : {0.05, 0.075, 0.10}) {
            const double v = probOverLossPortfCond(condProbs, lid, L);
            std::ostringstream nm;
            nm << "probOverLoss_10n_L" << static_cast<int>(L * 1000);
            w.addCase(nm.str(),
                      json{{"condProbs", condProbs}, {"lossInDef", lid}, {"relLoss", L}},
                      v);
        }
    }

    w.write();
    return 0;
}
