// migration-harness/cpp/probes/credit-loss-models/binomial_loss_probe.cpp
// Phase 4m.7 — emit C++ v1.42.1 reference values for the BinomialLossModel
// lossProbability kernel (the adjusted-binomial distribution given conditional
// default probabilities and LGDs).
//
// Re-implements the kernel from binomiallossmodel.hpp::lossProbability()
// directly — we feed in (condDefProb[], lgdsLeft[]) (already conditional on the
// market factor) and emit the resulting binomial-approximation pmf.

#include <ql/version.hpp>
#include <ql/qldefines.hpp>
#include "../common.hpp"

#include <algorithm>
#include <cmath>
#include <numeric>
#include <vector>

using namespace QuantLib;
using namespace jqml_harness;

namespace {

std::vector<double> lossProbabilityKernel(const std::vector<double>& condDefProbIn,
                                          const std::vector<double>& lgdsLeftIn) {
    // mirror binomiallossmodel.hpp::lossProbability
    std::vector<double> condDefProb = condDefProbIn;
    std::vector<double> lgdsLeft    = lgdsLeftIn;
    const std::size_t bsktSize = condDefProb.size();

    const double avgLgd =
        std::accumulate(lgdsLeft.begin(), lgdsLeft.end(), 0.0) / static_cast<double>(bsktSize);

    const double avgProb = avgLgd <= QL_EPSILON ? 0.0 :
        std::inner_product(condDefProb.begin(), condDefProb.end(), lgdsLeft.begin(), 0.0)
        / (avgLgd * static_cast<double>(bsktSize));

    const double m            = avgProb * static_cast<double>(bsktSize);
    const double floorAveProb = std::min(static_cast<double>(bsktSize - 1), std::floor(m));
    const double ceilAveProb  = floorAveProb + 1.0;

    const double varianceBinom = avgProb * (1.0 - avgProb) / static_cast<double>(bsktSize);

    std::vector<double> oneMinusDef(bsktSize);
    std::transform(condDefProb.begin(), condDefProb.end(), oneMinusDef.begin(),
                   [](double x) { return 1.0 - x; });
    // condDefProb := condDefProb * oneMinusDef
    std::vector<double> condDefProbVar(bsktSize);
    std::transform(condDefProb.begin(), condDefProb.end(), oneMinusDef.begin(),
                   condDefProbVar.begin(), std::multiplies<>());
    // lgdsLeft^2
    std::vector<double> lgdsSq(bsktSize);
    std::transform(lgdsLeft.begin(), lgdsLeft.end(), lgdsLeft.begin(), lgdsSq.begin(),
                   std::multiplies<>());

    double variance = std::inner_product(condDefProbVar.begin(), condDefProbVar.end(),
                                         lgdsSq.begin(), 0.0);
    variance = avgLgd <= QL_EPSILON ? 0.0 :
               variance / (static_cast<double>(bsktSize * bsktSize) * avgLgd * avgLgd);

    const double sumAves = -std::pow(ceilAveProb - m, 2)
                           - (std::pow(floorAveProb - m, 2) - std::pow(ceilAveProb, 2.0))
                             * (ceilAveProb - m);

    const double alpha = (variance * static_cast<double>(bsktSize) + sumAves)
                       / (varianceBinom * static_cast<double>(bsktSize) + sumAves);

    std::vector<double> lossProbDensity(bsktSize + 1, 0.0);
    if (avgProb >= 1.0 - QL_EPSILON) {
        lossProbDensity[bsktSize] = 1.0;
    } else if (avgProb <= QL_EPSILON) {
        lossProbDensity[0] = 1.0;
    } else {
        const double probsRatio = avgProb / (1.0 - avgProb);
        lossProbDensity[0] = std::pow(1.0 - avgProb, static_cast<double>(bsktSize));
        for (std::size_t i = 1; i < bsktSize + 1; ++i) {
            lossProbDensity[i] = lossProbDensity[i - 1] * probsRatio
                               * (static_cast<double>(bsktSize) - static_cast<double>(i) + 1.0)
                               / static_cast<double>(i);
        }
        for (std::size_t i = 0; i < bsktSize + 1; ++i) {
            lossProbDensity[i] *= alpha;
        }
        const double epsilon     = (1.0 - alpha) * (ceilAveProb - m);
        const double epsilonPlus = 1.0 - alpha - epsilon;
        lossProbDensity[static_cast<std::size_t>(floorAveProb)] += epsilon;
        lossProbDensity[static_cast<std::size_t>(ceilAveProb)]  += epsilonPlus;
    }
    return lossProbDensity;
}

} // namespace

int main() {
    ReferenceWriter w("credit-loss-models/binomial_loss",
                      QL_VERSION,
                      "binomial_loss_probe.cpp (Phase 4m.7)");

    // ---- 5-name homogeneous pool, all p=0.05, LGD=60 each ---------------
    {
        std::vector<double> p(5, 0.05);
        std::vector<double> lgd(5, 60.0);
        auto pmf = lossProbabilityKernel(p, lgd);
        w.addCase("binom_5_homog_p05_lgd60",
                  {{"condProbs", p}, {"lgds", lgd}},
                  pmf);
    }
    // ---- 5-name homogeneous pool, all p=0.20, LGD=40 each ---------------
    {
        std::vector<double> p(5, 0.20);
        std::vector<double> lgd(5, 40.0);
        auto pmf = lossProbabilityKernel(p, lgd);
        w.addCase("binom_5_homog_p20_lgd40",
                  {{"condProbs", p}, {"lgds", lgd}},
                  pmf);
    }
    // ---- 10-name inhomogeneous mix --------------------------------------
    {
        std::vector<double> p{0.01, 0.02, 0.05, 0.05, 0.10, 0.10, 0.15, 0.20, 0.20, 0.25};
        std::vector<double> lgd{40, 50, 60, 70, 80, 60, 50, 40, 80, 100};
        auto pmf = lossProbabilityKernel(p, lgd);
        w.addCase("binom_10_inhomog_mixed",
                  {{"condProbs", p}, {"lgds", lgd}},
                  pmf);
    }
    // ---- 5-name with very low avgProb (essentially atom at 0) -----------
    {
        std::vector<double> p(5, 1.0e-15);  // <= QL_EPSILON
        std::vector<double> lgd(5, 50.0);
        auto pmf = lossProbabilityKernel(p, lgd);
        w.addCase("binom_5_low_prob_atom_at_0",
                  {{"condProbs", p}, {"lgds", lgd}},
                  pmf);
    }
    // ---- 5-name with very high avgProb (essentially atom at N) ----------
    {
        std::vector<double> p(5, 1.0 - 1.0e-15);  // >= 1 - QL_EPSILON
        std::vector<double> lgd(5, 50.0);
        auto pmf = lossProbabilityKernel(p, lgd);
        w.addCase("binom_5_high_prob_atom_at_N",
                  {{"condProbs", p}, {"lgds", lgd}},
                  pmf);
    }
    // ---- 3-name simple p=0.5, LGD=100 each ------------------------------
    // exact independent binomial:
    //   pmf[k] = C(3,k) * 0.5^3 = {0.125, 0.375, 0.375, 0.125}
    {
        std::vector<double> p{0.5, 0.5, 0.5};
        std::vector<double> lgd{100.0, 100.0, 100.0};
        auto pmf = lossProbabilityKernel(p, lgd);
        w.addCase("binom_3_homog_p50_lgd100",
                  {{"condProbs", p}, {"lgds", lgd}},
                  pmf);
    }

    w.write();
    return 0;
}
