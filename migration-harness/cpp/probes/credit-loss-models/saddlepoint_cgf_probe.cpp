// migration-harness/cpp/probes/credit-loss-models/saddlepoint_cgf_probe.cpp
// Phase 4m.7 — emit C++ v1.42.1 reference values for the SaddlePointLossModel
// CGF (Cumulant Generating Function) and its first 4 derivatives, evaluated
// directly from the kernel formulae (saddlepointlossmodel.hpp lines 566-696).
//
// We pass in already-conditional probabilities (the C++ method calls
// copula_->conditionalDefaultProbabilityInvP per name; here we hand-pass the
// already-conditional values to test the pure CGF arithmetic).

#include <ql/version.hpp>
#include <ql/qldefines.hpp>
#include "../common.hpp"

#include <cmath>
#include <vector>

using namespace QuantLib;
using namespace jqml_harness;

namespace {

// CGF (zero-th derivative): K = sum log(1 - p_j + p_j exp(N_j * lgd_j * s / Ntot))
// where lgd_j = (1 - rr_j); N_j = name notional. Here we feed condProbs and
// pre-divided lossInDef[i] = N_i*(1-rr_i)/Ntot directly to test the kernel.
double cgf(const std::vector<double>& condProbs,
           const std::vector<double>& lossInDef,
           double saddle) {
    double sum = 0.0;
    for (std::size_t i = 0; i < condProbs.size(); ++i) {
        const double p = condProbs[i];
        sum += std::log(1.0 - p + p * std::exp(lossInDef[i] * saddle));
    }
    return sum;
}

// 1st derivative: K1 = sum lossInDef[i] * pBuffer * exp(...) / (1-p + pBuffer*exp(...))
double cgf1(const std::vector<double>& condProbs,
            const std::vector<double>& lossInDef,
            double saddle) {
    double sum = 0.0;
    for (std::size_t i = 0; i < condProbs.size(); ++i) {
        const double p = condProbs[i];
        const double mid = p * std::exp(lossInDef[i] * saddle);
        sum += lossInDef[i] * mid / (1.0 - p + mid);
    }
    return sum;
}

// 2nd derivative: K2 = sum lossInDef^2 * mid / denom - (lossInDef * mid / denom)^2
double cgf2(const std::vector<double>& condProbs,
            const std::vector<double>& lossInDef,
            double saddle) {
    double sum = 0.0;
    for (std::size_t i = 0; i < condProbs.size(); ++i) {
        const double p = condProbs[i];
        const double mid = p * std::exp(lossInDef[i] * saddle);
        const double denom = 1.0 - p + mid;
        sum += lossInDef[i] * lossInDef[i] * mid / denom
             - std::pow(lossInDef[i] * mid / denom, 2.0);
    }
    return sum;
}

// 3rd derivative
double cgf3(const std::vector<double>& condProbs,
            const std::vector<double>& lossInDef,
            double saddle) {
    double sum = 0.0;
    for (std::size_t i = 0; i < condProbs.size(); ++i) {
        const double p = condProbs[i];
        const double mid = p * std::exp(lossInDef[i] * saddle);
        const double denom = 1.0 - p + mid;
        const double s0 = denom;
        const double s1 = lossInDef[i] * mid;
        const double s2 = lossInDef[i] * s1;
        const double s3 = lossInDef[i] * s2;
        sum += (s3 + (2.0 * std::pow(s1, 3.0) / s0 - 3.0 * s1 * s2) / s0) / s0;
    }
    return sum;
}

// 4th derivative
double cgf4(const std::vector<double>& condProbs,
            const std::vector<double>& lossInDef,
            double saddle) {
    double sum = 0.0;
    for (std::size_t i = 0; i < condProbs.size(); ++i) {
        const double p = condProbs[i];
        const double mid = p * std::exp(lossInDef[i] * saddle);
        const double denom = 1.0 - p + mid;
        const double s0 = denom;
        const double s1 = lossInDef[i] * mid;
        const double s2 = lossInDef[i] * s1;
        const double s3 = lossInDef[i] * s2;
        const double s4 = lossInDef[i] * s3;
        sum += (s4 + (-4.0 * s1 * s3 - 3.0 * s2 * s2
                + (12.0 * s1 * s1 * s2 - 6.0 * std::pow(s1, 4.0) / s0) / s0) / s0) / s0;
    }
    return sum;
}

} // namespace

int main() {
    ReferenceWriter w("credit-loss-models/saddlepoint_cgf",
                      QL_VERSION,
                      "saddlepoint_cgf_probe.cpp (Phase 4m.7)");

    // ---- 5-name homogeneous, p=0.05, lossInDef = 0.12 each ----
    // (e.g., 5 names of equal $100 notional with 40% recovery: $60 LGD over $500 total = 0.12)
    {
        std::vector<double> p(5, 0.05);
        std::vector<double> l(5, 0.12);

        json out = json::object();
        for (double s : {0.0, 1.0, 5.0, 10.0, -1.0}) {
            json sub = json::object();
            sub["cgf0"] = cgf(p, l, s);
            sub["cgf1"] = cgf1(p, l, s);
            sub["cgf2"] = cgf2(p, l, s);
            sub["cgf3"] = cgf3(p, l, s);
            sub["cgf4"] = cgf4(p, l, s);
            out["s_" + std::to_string(s)] = sub;
        }
        w.addCase("cgf_5_homog_p05_lid12",
                  {{"condProbs", p}, {"lossInDef", l}},
                  out);
    }

    // ---- 5-name homogeneous, p=0.20, lossInDef = 0.12 each ----
    {
        std::vector<double> p(5, 0.20);
        std::vector<double> l(5, 0.12);
        json out = json::object();
        for (double s : {0.0, 1.0, 5.0, -1.0}) {
            json sub = json::object();
            sub["cgf0"] = cgf(p, l, s);
            sub["cgf1"] = cgf1(p, l, s);
            sub["cgf2"] = cgf2(p, l, s);
            sub["cgf3"] = cgf3(p, l, s);
            sub["cgf4"] = cgf4(p, l, s);
            out["s_" + std::to_string(s)] = sub;
        }
        w.addCase("cgf_5_homog_p20_lid12",
                  {{"condProbs", p}, {"lossInDef", l}},
                  out);
    }

    // ---- 8-name inhomogeneous ----
    {
        std::vector<double> p{0.01, 0.02, 0.05, 0.05, 0.10, 0.10, 0.15, 0.20};
        std::vector<double> l{0.05, 0.06, 0.075, 0.08, 0.10, 0.12, 0.15, 0.18};
        json out = json::object();
        for (double s : {0.0, 1.0, 3.0, -2.0}) {
            json sub = json::object();
            sub["cgf0"] = cgf(p, l, s);
            sub["cgf1"] = cgf1(p, l, s);
            sub["cgf2"] = cgf2(p, l, s);
            sub["cgf3"] = cgf3(p, l, s);
            sub["cgf4"] = cgf4(p, l, s);
            out["s_" + std::to_string(s)] = sub;
        }
        w.addCase("cgf_8_inhomog",
                  {{"condProbs", p}, {"lossInDef", l}},
                  out);
    }

    w.write();
    return 0;
}
