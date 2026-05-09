// migration-harness/cpp/probes/processes/bates_process_probe.cpp
// Phase 5h.5-Bates — emit C++ v1.42.1 reference values for
// BatesProcess::drift / evolve / accessors. Java port
// (org.jquantlib.processes.BatesProcess) cross-validates against these.

#include <ql/version.hpp>
#include <ql/processes/batesprocess.hpp>
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

struct DriftCase {
    std::string name;
    Real r, q, s0;
    Real v0, kappa, theta, sigma, rho;
    Real lambda, nu, delta;
    Real t, x0, x1;
};

struct EvolveCase {
    std::string name;
    Real r, q, s0;
    Real v0, kappa, theta, sigma, rho;
    Real lambda, nu, delta;
    Real t0, dt;
    Real x00, x01;
    Real dw0, dw1, dw2, dw3;
};

void runDriftCase(ReferenceWriter& out, const DriftCase& tc) {
    Handle<YieldTermStructure> rCurve = flatCurve(tc.r);
    Handle<YieldTermStructure> qCurve = flatCurve(tc.q);
    Handle<Quote> spot(ext::make_shared<SimpleQuote>(tc.s0));

    BatesProcess process(rCurve, qCurve, spot,
                         tc.v0, tc.kappa, tc.theta, tc.sigma, tc.rho,
                         tc.lambda, tc.nu, tc.delta);

    Array x(2);
    x[0] = tc.x0;
    x[1] = tc.x1;
    Array d = process.drift(tc.t, x);

    json inputs = {
        {"r", tc.r}, {"q", tc.q}, {"s0", tc.s0},
        {"v0", tc.v0}, {"kappa", tc.kappa}, {"theta", tc.theta},
        {"sigma", tc.sigma}, {"rho", tc.rho},
        {"lambda", tc.lambda}, {"nu", tc.nu}, {"delta", tc.delta},
        {"t", tc.t}, {"x", {tc.x0, tc.x1}}
    };
    json expected = {
        {"drift", {d[0], d[1]}},
        {"factors", static_cast<long long>(process.factors())},
        {"lambda_acc", process.lambda()},
        {"nu_acc", process.nu()},
        {"delta_acc", process.delta()}
    };
    out.addCase(tc.name, inputs, expected);
}

void runEvolveCase(ReferenceWriter& out, const EvolveCase& tc) {
    Handle<YieldTermStructure> rCurve = flatCurve(tc.r);
    Handle<YieldTermStructure> qCurve = flatCurve(tc.q);
    Handle<Quote> spot(ext::make_shared<SimpleQuote>(tc.s0));

    BatesProcess process(rCurve, qCurve, spot,
                         tc.v0, tc.kappa, tc.theta, tc.sigma, tc.rho,
                         tc.lambda, tc.nu, tc.delta);

    Array x0(2);
    x0[0] = tc.x00;
    x0[1] = tc.x01;
    // BatesProcess factors = HestonProcess::factors() + 2 = 4 (FullTruncation)
    Array dw(4);
    dw[0] = tc.dw0;
    dw[1] = tc.dw1;
    dw[2] = tc.dw2;
    dw[3] = tc.dw3;

    Array result = process.evolve(tc.t0, x0, tc.dt, dw);

    json inputs = {
        {"r", tc.r}, {"q", tc.q}, {"s0", tc.s0},
        {"v0", tc.v0}, {"kappa", tc.kappa}, {"theta", tc.theta},
        {"sigma", tc.sigma}, {"rho", tc.rho},
        {"lambda", tc.lambda}, {"nu", tc.nu}, {"delta", tc.delta},
        {"t0", tc.t0}, {"dt", tc.dt},
        {"x0", {tc.x00, tc.x01}},
        {"dw", {tc.dw0, tc.dw1, tc.dw2, tc.dw3}}
    };
    json expected = {{"evolved", {result[0], result[1]}}};
    out.addCase(tc.name, inputs, expected);
}

} // namespace

int main() {
    ReferenceWriter out("processes/bates_process", QL_VERSION,
                        "bates_process_probe");

    const Real R = 0.05, Q = 0.02, S0 = 100.0;
    const Real KAPPA = 2.0, THETA = 0.04, SIGMA = 0.3, RHO = -0.5;
    const Real LAMBDA = 0.2, NU = -0.1, DELTA = 0.15;

    // Drift cases
    {
        DriftCase tc{"drift_midV0", R, Q, S0,
                     0.04, KAPPA, THETA, SIGMA, RHO,
                     LAMBDA, NU, DELTA, 0.5, S0, 0.04};
        runDriftCase(out, tc);
    }
    {
        DriftCase tc{"drift_lowV0", R, Q, S0,
                     0.01, KAPPA, THETA, SIGMA, RHO,
                     LAMBDA, NU, DELTA, 0.5, S0, 0.01};
        runDriftCase(out, tc);
    }
    {
        DriftCase tc{"drift_zeroLambda", R, Q, S0,
                     0.04, KAPPA, THETA, SIGMA, RHO,
                     0.0, NU, DELTA, 0.5, S0, 0.04};
        runDriftCase(out, tc);
    }

    // Evolve cases
    {
        EvolveCase tc{"evolve_midV0", R, Q, S0,
                      0.04, KAPPA, THETA, SIGMA, RHO,
                      LAMBDA, NU, DELTA,
                      0.0, 0.1, S0, 0.04,
                      0.5, 0.3, -0.2, 0.7};
        runEvolveCase(out, tc);
    }
    {
        // tiny lambda — InverseCumulativePoisson rejects zero, so use 1e-6
        EvolveCase tc{"evolve_tinyJump", R, Q, S0,
                      0.04, KAPPA, THETA, SIGMA, RHO,
                      1.0e-6, 0.0, 1.0e-6,
                      0.0, 0.1, S0, 0.04,
                      0.5, 0.3, -0.2, 0.7};
        runEvolveCase(out, tc);
    }
    {
        EvolveCase tc{"evolve_strongJump", R, Q, S0,
                      0.04, KAPPA, THETA, SIGMA, RHO,
                      0.5, 0.05, 0.2,
                      0.0, 0.5, S0, 0.04,
                      -0.3, 0.4, 1.5, -0.8};
        runEvolveCase(out, tc);
    }

    out.write();
    return 0;
}
