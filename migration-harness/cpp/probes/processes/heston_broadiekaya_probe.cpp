// migration-harness/cpp/probes/processes/heston_broadiekaya_probe.cpp
// Phase 2f WI-3 C.6 — emit C++ v1.42.1 reference values for
// HestonProcess::evolve under each of the three BroadieKaya exact
// schemes (Lobatto, Laguerre, Trapezoidal). The Java port
// (HestonProcess + HestonHelpers) cross-validates against these at
// the loose tier — see HestonProcessTest.

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

struct BKCase {
    std::string name;
    HestonProcess::Discretization disc;
    Real r, q, s0;
    Real v0, kappa, theta, sigma, rho;
    Real t0, dt;
    Real x00, x01;
    Real dw0, dw1, dw2;
};

void runAndEmit(ReferenceWriter& out, const BKCase& tc) {
    Handle<YieldTermStructure> rCurve = flatCurve(tc.r);
    Handle<YieldTermStructure> qCurve = flatCurve(tc.q);
    Handle<Quote> spot(ext::make_shared<SimpleQuote>(tc.s0));

    HestonProcess process(rCurve, qCurve, spot,
                          tc.v0, tc.kappa, tc.theta, tc.sigma, tc.rho,
                          tc.disc);

    Array x0(2);
    x0[0] = tc.x00;
    x0[1] = tc.x01;
    Array dw(3);
    dw[0] = tc.dw0;
    dw[1] = tc.dw1;
    dw[2] = tc.dw2;

    Array result = process.evolve(tc.t0, x0, tc.dt, dw);

    json inputs = {
        {"r", tc.r}, {"q", tc.q}, {"s0", tc.s0},
        {"v0", tc.v0}, {"kappa", tc.kappa}, {"theta", tc.theta},
        {"sigma", tc.sigma}, {"rho", tc.rho},
        {"t0", tc.t0}, {"dt", tc.dt},
        {"x0", {tc.x00, tc.x01}},
        {"dw", {tc.dw0, tc.dw1, tc.dw2}}
    };
    json expected = {{"evolved", {result[0], result[1]}}};
    out.addCase(tc.name, inputs, expected);
}

} // namespace

int main() {
    ReferenceWriter out("processes/heston_broadiekaya", QL_VERSION,
                        "heston_broadiekaya_probe");

    const Real KAPPA = 2.0, THETA = 0.04, SIGMA = 0.3, RHO = -0.5;
    const Real R = 0.05, Q = 0.02, S0 = 100.0;
    const Real T0 = 0.0, DT = 0.1;

    const HestonProcess::Discretization schemes[] = {
        HestonProcess::BroadieKayaExactSchemeLobatto,
        HestonProcess::BroadieKayaExactSchemeLaguerre,
        HestonProcess::BroadieKayaExactSchemeTrapezoidal
    };
    const std::string schemeNames[] = {"lobatto", "laguerre", "trapezoidal"};

    for (size_t s = 0; s < 3; ++s) {
        const std::string& sn = schemeNames[s];
        // mid v0/dw fixture
        {
            BKCase tc;
            tc.name = "bk_" + sn + "_midV0";
            tc.disc = schemes[s];
            tc.r = R; tc.q = Q; tc.s0 = S0;
            tc.v0 = 0.04; tc.kappa = KAPPA; tc.theta = THETA;
            tc.sigma = SIGMA; tc.rho = RHO;
            tc.t0 = T0; tc.dt = DT;
            tc.x00 = S0; tc.x01 = 0.04;
            tc.dw0 = 0.5; tc.dw1 = 0.3; tc.dw2 = -0.2;
            runAndEmit(out, tc);
        }
        // low v0 fixture
        {
            BKCase tc;
            tc.name = "bk_" + sn + "_lowV0";
            tc.disc = schemes[s];
            tc.r = R; tc.q = Q; tc.s0 = S0;
            tc.v0 = 0.01; tc.kappa = KAPPA; tc.theta = THETA;
            tc.sigma = SIGMA; tc.rho = RHO;
            tc.t0 = T0; tc.dt = DT;
            tc.x00 = S0; tc.x01 = 0.01;
            tc.dw0 = -0.4; tc.dw1 = 0.6; tc.dw2 = 0.1;
            runAndEmit(out, tc);
        }
        // high v0 fixture
        {
            BKCase tc;
            tc.name = "bk_" + sn + "_highV0";
            tc.disc = schemes[s];
            tc.r = R; tc.q = Q; tc.s0 = S0;
            tc.v0 = 0.10; tc.kappa = KAPPA; tc.theta = THETA;
            tc.sigma = SIGMA; tc.rho = RHO;
            tc.t0 = T0; tc.dt = DT;
            tc.x00 = S0; tc.x01 = 0.10;
            tc.dw0 = 1.0; tc.dw1 = 0.5; tc.dw2 = -1.2;
            runAndEmit(out, tc);
        }
    }

    out.write();
    return 0;
}
