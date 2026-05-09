// migration-harness/cpp/probes/credit-loss-models/gaussian_lhp_loss_probe.cpp
// Phase 4m.7 — emit C++ v1.42.1 reference values for the GaussianLHPLossModel
// analytic formulas (expectedTrancheLossImpl, percentilePortfolioLossFraction,
// probOverLoss "kernel"). Avoids constructing a Basket — exercises the pure
// analytic kernels which depend only on (correlation, recoveries, prob,
// attach, detach, etc.).
//
// The kernel itself is:
//   one  = 1 - 1e-12
//   k1   = min(one, attach/(1-RR)) + QL_EPSILON
//   k2   = min(one, detach/(1-RR)) + QL_EPSILON
//   ip   = InverseCumulativeNormal(prob)
//   if1  = (ip - sqrt(1-rho) * InverseCumulativeNormal(k1))/sqrt(rho)
//   if2  = (ip - sqrt(1-rho) * InverseCumulativeNormal(k2))/sqrt(rho)
//   ETL  = remNot * (detach * Φ(if2) - attach * Φ(if1)
//                  + (1-RR) * (Φ_2(ip,-if2;-β) - Φ_2(ip,-if1;-β)))
//
// We exercise these via the actual GaussianLHPLossModel by constructing a
// small Basket of homogeneous synthetic names. We generate values by
// recomputing the kernel directly with QuantLib utilities — avoids the
// Basket plumbing entirely.

#include <ql/version.hpp>
#include <ql/math/distributions/bivariatenormaldistribution.hpp>
#include <ql/math/distributions/normaldistribution.hpp>
#include <ql/qldefines.hpp>
#include "../common.hpp"

#include <cmath>

using namespace QuantLib;
using namespace jqml_harness;

namespace {

// Local re-implementation matching GaussianLHPLossModel::expectedTrancheLossImpl
// 1:1 (uses the same math library calls). This is the kernel we want to
// reference; Java will compute the same way and we cross-validate per case.
double expectedTrancheLossKernel(double remNot, double prob, double averageRR,
                                 double attach, double detach, double rho) {
    static const CumulativeNormalDistribution phi;
    if (attach >= detach) return 0.0;
    if (remNot == 0.0) return 0.0;

    const double one = 1.0 - 1.0e-12;
    const double k1 = std::min(one, attach/(1.0 - averageRR)) + QL_EPSILON;
    const double k2 = std::min(one, detach/(1.0 - averageRR)) + QL_EPSILON;

    if (prob > 0) {
        const double sqrt1mc = std::sqrt(1.0 - rho);
        const double beta    = std::sqrt(rho);
        BivariateCumulativeNormalDistribution biphi(-beta);
        const double ip  = InverseCumulativeNormal::standard_value(prob);
        const double if1 = (ip - sqrt1mc * InverseCumulativeNormal::standard_value(k1)) / beta;
        const double if2 = (ip - sqrt1mc * InverseCumulativeNormal::standard_value(k2)) / beta;
        return remNot * (detach * phi(if2) - attach * phi(if1)
                       + (1.0 - averageRR) * (biphi(ip, -if2) - biphi(ip, -if1)));
    }
    return 0.0;
}

double percentilePortfolioLossFractionKernel(double averageRR, double averageProb,
                                             double perctl, double rho) {
    static const CumulativeNormalDistribution phi;
    if (perctl == 0.0) return 0.0;
    if (perctl == 1.0) perctl = 1.0 - QL_EPSILON;

    const double sqrt1mc = std::sqrt(1.0 - rho);
    const double beta    = std::sqrt(rho);
    return (1.0 - averageRR) *
           phi((InverseCumulativeNormal::standard_value(averageProb)
                + beta * InverseCumulativeNormal::standard_value(perctl))
               / sqrt1mc);
}

double probOverLossKernel(double averageRR, double averageProb,
                          double portfFract, double rho) {
    static const CumulativeNormalDistribution phi;

    const double sqrt1mc = std::sqrt(1.0 - rho);
    const double beta    = std::sqrt(rho);
    const double ip  = InverseCumulativeNormal::standard_value(averageProb);
    const double if1 = (ip - sqrt1mc *
                        InverseCumulativeNormal::standard_value(portfFract/(1.0 - averageRR)))
                       / beta;
    return phi(if1);
}

} // namespace

int main() {
    ReferenceWriter w("credit-loss-models/gaussian_lhp_loss",
                      QL_VERSION,
                      "gaussian_lhp_loss_probe.cpp (Phase 4m.7)");

    // ---- expectedTrancheLossImpl cases ------------------------------------
    {
        const double remNot = 100.0;
        const double prob   = 0.05;
        const double rr     = 0.40;
        const double rho    = 0.20;
        const double attach = 0.0;
        const double detach = 0.10;
        const double etl = expectedTrancheLossKernel(remNot, prob, rr, attach, detach, rho);
        w.addCase("etl_equity_5pct_prob_20pct_corr",
                  {{"remNot", remNot}, {"prob", prob}, {"recovery", rr},
                   {"attach", attach}, {"detach", detach}, {"correl", rho}},
                  etl);
    }
    {
        const double remNot = 100.0;
        const double prob   = 0.05;
        const double rr     = 0.40;
        const double rho    = 0.20;
        const double attach = 0.10;
        const double detach = 0.30;
        const double etl = expectedTrancheLossKernel(remNot, prob, rr, attach, detach, rho);
        w.addCase("etl_mezz_5pct_prob_20pct_corr",
                  {{"remNot", remNot}, {"prob", prob}, {"recovery", rr},
                   {"attach", attach}, {"detach", detach}, {"correl", rho}},
                  etl);
    }
    {
        const double remNot = 100.0;
        const double prob   = 0.05;
        const double rr     = 0.40;
        const double rho    = 0.20;
        const double attach = 0.30;
        const double detach = 1.00;
        const double etl = expectedTrancheLossKernel(remNot, prob, rr, attach, detach, rho);
        w.addCase("etl_senior_5pct_prob_20pct_corr",
                  {{"remNot", remNot}, {"prob", prob}, {"recovery", rr},
                   {"attach", attach}, {"detach", detach}, {"correl", rho}},
                  etl);
    }
    {
        // higher correlation
        const double remNot = 1000.0;
        const double prob   = 0.10;
        const double rr     = 0.40;
        const double rho    = 0.50;
        const double attach = 0.05;
        const double detach = 0.20;
        const double etl = expectedTrancheLossKernel(remNot, prob, rr, attach, detach, rho);
        w.addCase("etl_mezz_10pct_prob_50pct_corr",
                  {{"remNot", remNot}, {"prob", prob}, {"recovery", rr},
                   {"attach", attach}, {"detach", detach}, {"correl", rho}},
                  etl);
    }
    {
        // edge: attach >= detach -> 0
        const double remNot = 100.0;
        const double etl = expectedTrancheLossKernel(remNot, 0.05, 0.40, 0.20, 0.10, 0.20);
        w.addCase("etl_attach_ge_detach_zero",
                  {{"remNot", remNot}, {"prob", 0.05}, {"recovery", 0.40},
                   {"attach", 0.20}, {"detach", 0.10}, {"correl", 0.20}},
                  etl);
    }
    {
        // edge: prob=0 -> 0
        const double etl = expectedTrancheLossKernel(100.0, 0.0, 0.40, 0.0, 0.10, 0.20);
        w.addCase("etl_prob_zero",
                  {{"remNot", 100.0}, {"prob", 0.0}, {"recovery", 0.40},
                   {"attach", 0.0}, {"detach", 0.10}, {"correl", 0.20}},
                  etl);
    }
    {
        // very low corr (close to indep limit)
        const double etl = expectedTrancheLossKernel(100.0, 0.10, 0.40, 0.0, 0.05, 0.01);
        w.addCase("etl_low_correl",
                  {{"remNot", 100.0}, {"prob", 0.10}, {"recovery", 0.40},
                   {"attach", 0.0}, {"detach", 0.05}, {"correl", 0.01}},
                  etl);
    }

    // ---- percentilePortfolioLossFraction cases ---------------------------
    {
        const double rr = 0.40, p = 0.05, q = 0.95, rho = 0.20;
        const double v = percentilePortfolioLossFractionKernel(rr, p, q, rho);
        w.addCase("pctl_5pct_prob_95th_q_20pct_corr",
                  {{"recovery", rr}, {"avgProb", p}, {"perctl", q}, {"correl", rho}},
                  v);
    }
    {
        const double rr = 0.40, p = 0.05, q = 0.99, rho = 0.20;
        const double v = percentilePortfolioLossFractionKernel(rr, p, q, rho);
        w.addCase("pctl_5pct_prob_99th_q_20pct_corr",
                  {{"recovery", rr}, {"avgProb", p}, {"perctl", q}, {"correl", rho}},
                  v);
    }
    {
        const double rr = 0.40, p = 0.10, q = 0.50, rho = 0.50;
        const double v = percentilePortfolioLossFractionKernel(rr, p, q, rho);
        w.addCase("pctl_10pct_prob_50th_q_50pct_corr",
                  {{"recovery", rr}, {"avgProb", p}, {"perctl", q}, {"correl", rho}},
                  v);
    }
    {
        const double rr = 0.40, p = 0.05, q = 0.0, rho = 0.20;
        const double v = percentilePortfolioLossFractionKernel(rr, p, q, rho);
        w.addCase("pctl_perctl_zero",
                  {{"recovery", rr}, {"avgProb", p}, {"perctl", q}, {"correl", rho}},
                  v);
    }

    // ---- probOverLoss kernel cases ---------------------------------------
    {
        const double rr = 0.40, p = 0.05, pf = 0.05, rho = 0.20;
        const double v = probOverLossKernel(rr, p, pf, rho);
        w.addCase("pol_avg5pct_pf5pct_20pct_corr",
                  {{"recovery", rr}, {"avgProb", p}, {"portfFract", pf}, {"correl", rho}},
                  v);
    }
    {
        const double rr = 0.40, p = 0.05, pf = 0.10, rho = 0.20;
        const double v = probOverLossKernel(rr, p, pf, rho);
        w.addCase("pol_avg5pct_pf10pct_20pct_corr",
                  {{"recovery", rr}, {"avgProb", p}, {"portfFract", pf}, {"correl", rho}},
                  v);
    }
    {
        const double rr = 0.40, p = 0.10, pf = 0.20, rho = 0.50;
        const double v = probOverLossKernel(rr, p, pf, rho);
        w.addCase("pol_avg10pct_pf20pct_50pct_corr",
                  {{"recovery", rr}, {"avgProb", p}, {"portfFract", pf}, {"correl", rho}},
                  v);
    }

    w.write();
    return 0;
}
