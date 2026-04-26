// migration-harness/cpp/probes/processes/hestonprocess_nccv_probe.cpp
// Reference values for HestonProcess::evolve under the
// NonCentralChiSquareVariance discretization (Alan Lewis decorrelation
// trick, ql/processes/hestonprocess.cpp lines 444-460). Five tuples cover
// the typical, low-ncp, high-v0, pure-mean and very-high-v0 (P2D-6 Ding
// region) regimes of the underlying ncchisq inverse-CDF sampler.

#include <ql/version.hpp>
#include <ql/processes/hestonprocess.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>
#include "../common.hpp"

using namespace QuantLib;
using namespace jqml_harness;

namespace {

Handle<YieldTermStructure> flatCurve(Real rate) {
    const Date today(22, April, 2026);
    Settings::instance().evaluationDate() = today;
    return Handle<YieldTermStructure>(
        ext::make_shared<FlatForward>(today, rate, Actual365Fixed()));
}

struct NCCVCase {
    std::string name;
    Real r, q, s0;
    Real v0, kappa, theta, sigma, rho;
    Real t0, dt;
    Real x00, x01;
    Real dw0, dw1;
};

void runAndEmit(ReferenceWriter& out, const NCCVCase& tc) {
    Handle<YieldTermStructure> rCurve = flatCurve(tc.r);
    Handle<YieldTermStructure> qCurve = flatCurve(tc.q);
    Handle<Quote> spot(ext::make_shared<SimpleQuote>(tc.s0));

    HestonProcess process(rCurve, qCurve, spot,
                          tc.v0, tc.kappa, tc.theta, tc.sigma, tc.rho,
                          HestonProcess::NonCentralChiSquareVariance);

    Array x0(2);
    x0[0] = tc.x00;
    x0[1] = tc.x01;
    Array dw(2);
    dw[0] = tc.dw0;
    dw[1] = tc.dw1;

    Array result = process.evolve(tc.t0, x0, tc.dt, dw);

    json inputs = {
        {"r", tc.r}, {"q", tc.q}, {"s0", tc.s0},
        {"v0", tc.v0}, {"kappa", tc.kappa}, {"theta", tc.theta},
        {"sigma", tc.sigma}, {"rho", tc.rho},
        {"t0", tc.t0}, {"dt", tc.dt},
        {"x0", {tc.x00, tc.x01}},
        {"dw", {tc.dw0, tc.dw1}}
    };
    json expected = {
        {"evolved", {result[0], result[1]}}
    };
    out.addCase(tc.name, inputs, expected);
}

} // namespace

int main() {
    ReferenceWriter out("processes/hestonprocess_nccv", QL_VERSION,
                        "hestonprocess_nccv_probe");

    // Common Heston coefficients across all five tuples; vary v0/dw to
    // exercise distinct ncchisq regimes.
    const Real KAPPA = 2.0, THETA = 0.04, SIGMA = 0.3, RHO = -0.5;
    const Real R = 0.05, Q = 0.02, S0 = 100.0;
    const Real T0 = 0.0, DT = 0.1;

    // Case 1: mid-ncp, central regime.
    {
        NCCVCase tc;
        tc.name = "nccv_midNcp";
        tc.r = R; tc.q = Q; tc.s0 = S0;
        tc.v0 = 0.04; tc.kappa = KAPPA; tc.theta = THETA;
        tc.sigma = SIGMA; tc.rho = RHO;
        tc.t0 = T0; tc.dt = DT;
        tc.x00 = S0; tc.x01 = 0.04;
        tc.dw0 = 0.5; tc.dw1 = 0.3;
        runAndEmit(out, tc);
    }

    // Case 2: low-ncp / low-v0 — exercises the small-noncentrality branch
    // of ncchisq sampling.
    {
        NCCVCase tc;
        tc.name = "nccv_lowNcp_lowV0";
        tc.r = R; tc.q = Q; tc.s0 = S0;
        tc.v0 = 0.001; tc.kappa = KAPPA; tc.theta = THETA;
        tc.sigma = SIGMA; tc.rho = RHO;
        tc.t0 = T0; tc.dt = DT;
        tc.x00 = S0; tc.x01 = 0.001;
        tc.dw0 = 0.0; tc.dw1 = -1.0;
        runAndEmit(out, tc);
    }

    // Case 3: high-v0 — variance well above theta; tail draw.
    {
        NCCVCase tc;
        tc.name = "nccv_highV0";
        tc.r = R; tc.q = Q; tc.s0 = S0;
        tc.v0 = 0.25; tc.kappa = KAPPA; tc.theta = THETA;
        tc.sigma = SIGMA; tc.rho = RHO;
        tc.t0 = T0; tc.dt = DT;
        tc.x00 = S0; tc.x01 = 0.25;
        tc.dw0 = -1.5; tc.dw1 = 2.0;
        runAndEmit(out, tc);
    }

    // Case 4: pure-mean — dw1=0 exercises the Φ(0)=0.5 inverse-CDF path.
    {
        NCCVCase tc;
        tc.name = "nccv_pureMean";
        tc.r = R; tc.q = Q; tc.s0 = S0;
        tc.v0 = 0.04; tc.kappa = KAPPA; tc.theta = THETA;
        tc.sigma = SIGMA; tc.rho = RHO;
        tc.t0 = T0; tc.dt = DT;
        tc.x00 = S0; tc.x01 = 0.04;
        tc.dw0 = 0.0; tc.dw1 = 0.0;
        runAndEmit(out, tc);
    }

    // Case 5: high v0 → elevated noncentrality regime targeting the
    // P2D-6 Ding approximation territory of the ncchisq inverse CDF.
    // Note: v0 dialed to 1.0 because larger values (e.g. 12.0) overflow
    // the ncchisq Brent bracket in C++ v1.42.1 ("root not bracketed"),
    // i.e. the underlying ql distribution itself loses applicability
    // there, not a Java port concern.
    {
        NCCVCase tc;
        tc.name = "nccv_highV0_dingRegion";
        tc.r = R; tc.q = Q; tc.s0 = S0;
        tc.v0 = 1.0; tc.kappa = KAPPA; tc.theta = THETA;
        tc.sigma = SIGMA; tc.rho = RHO;
        tc.t0 = T0; tc.dt = DT;
        tc.x00 = S0; tc.x01 = 1.0;
        tc.dw0 = 1.0; tc.dw1 = 1.5;
        runAndEmit(out, tc);
    }

    out.write();
    return 0;
}
