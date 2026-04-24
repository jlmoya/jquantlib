// migration-harness/cpp/probes/processes/hestonprocess_qe_probe.cpp
// Reference values for HestonProcess::evolve under the QuadraticExponential
// (Andersen 2008, ql/processes/hestonprocess.cpp case 461-516)
// discretization. Three cases exercise the two internal branches (psi < 1.5
// vs psi >= 1.5) plus a typical low-vol regime.

#include <ql/version.hpp>
#include <ql/processes/hestonprocess.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/time/calendars/nullcalendar.hpp>
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

json vec(const double* p, int n) {
    json j = json::array();
    for (int i = 0; i < n; ++i) j.push_back(p[i]);
    return j;
}

struct QECase {
    std::string name;
    Real r, q, s0;
    Real v0, kappa, theta, sigma, rho;
    Real t0, dt;
    Real x00, x01;
    Real dw0, dw1;
};

void runAndEmit(ReferenceWriter& out, const QECase& tc) {
    Handle<YieldTermStructure> rCurve = flatCurve(tc.r);
    Handle<YieldTermStructure> qCurve = flatCurve(tc.q);
    Handle<Quote> spot(ext::make_shared<SimpleQuote>(tc.s0));

    HestonProcess process(rCurve, qCurve, spot,
                          tc.v0, tc.kappa, tc.theta, tc.sigma, tc.rho,
                          HestonProcess::QuadraticExponential);

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
    ReferenceWriter out("processes/hestonprocess_qe", QL_VERSION,
                        "hestonprocess_qe_probe");

    // Case 1: typical medium-vol regime — psi should land in the
    // central part of the feasible range and exercise the psi<1.5 path
    // (quadratic-Gaussian sampling branch).
    {
        QECase tc;
        tc.name = "qe_psiLow_centralVol";
        tc.r = 0.05; tc.q = 0.02; tc.s0 = 100.0;
        tc.v0 = 0.04; tc.kappa = 2.0; tc.theta = 0.04;
        tc.sigma = 0.5; tc.rho = -0.7;
        tc.t0 = 0.5; tc.dt = 0.1;
        tc.x00 = 100.0; tc.x01 = 0.04;
        tc.dw0 = 0.3; tc.dw1 = -0.2;
        runAndEmit(out, tc);
    }

    // Case 2: high-vol-of-vol / low-initial-variance — drives psi above
    // 1.5 and exercises the exponential-sampling branch with u>p
    // (non-zero variance draw).
    {
        QECase tc;
        tc.name = "qe_psiHigh_lowInitV";
        tc.r = 0.03; tc.q = 0.0; tc.s0 = 100.0;
        tc.v0 = 0.005; tc.kappa = 0.3; tc.theta = 0.04;
        tc.sigma = 1.0; tc.rho = -0.9;
        tc.t0 = 0.0; tc.dt = 0.25;
        tc.x00 = 100.0; tc.x01 = 0.005;
        tc.dw0 = 0.5; tc.dw1 = 1.5;  // u = N(1.5) ≈ 0.933 > p
        runAndEmit(out, tc);
    }

    // Case 3: psi-high branch with u <= p — variance draw is exactly 0.
    // Same setup as case 2 but dw1 negative so u < p.
    {
        QECase tc;
        tc.name = "qe_psiHigh_zeroVarianceDraw";
        tc.r = 0.03; tc.q = 0.0; tc.s0 = 100.0;
        tc.v0 = 0.005; tc.kappa = 0.3; tc.theta = 0.04;
        tc.sigma = 1.0; tc.rho = -0.9;
        tc.t0 = 0.0; tc.dt = 0.25;
        tc.x00 = 100.0; tc.x01 = 0.005;
        tc.dw0 = -0.1; tc.dw1 = -2.0;  // u = N(-2.0) ≈ 0.023; likely u <= p
        runAndEmit(out, tc);
    }

    out.write();
    return 0;
}
