// migration-harness/cpp/probes/experimental/volatility/zabr_model_probe.cpp
//
// Reference values for QuantLib v1.42.1 ZabrModel (ql/termstructures/volatility/zabr.{hpp,cpp}).
// Covers:
//   - lognormalVolatility(strike) for both gamma == 1 (closed-form x) and gamma != 1 (RK ODE x)
//   - normalVolatility(strike)
//   - localVolatility(strike) [needs y(), F(), x()]
//   - fdPrice(strike) — Dupire 1-D FD
//
// Phase 4f.5b validates the Java port at TIGHT (analytic, gamma=1) and LOOSE
// (RK ODE / FD, gamma != 1) tolerance tiers.

#include <ql/version.hpp>
#include <ql/termstructures/volatility/zabr.hpp>
#include "../../common.hpp"

#include <iostream>
#include <vector>

using namespace QuantLib;
using namespace jqml_harness;

namespace {

void addLognormalCase(ReferenceWriter& w, const std::string& name,
                      Real expiry, Real fwd, Real alpha, Real beta,
                      Real nu, Real rho, Real gamma,
                      const std::vector<Real>& strikes) {
    ZabrModel m(expiry, fwd, alpha, beta, nu, rho, gamma);
    json results = json::array();
    for (Real k : strikes) {
        results.push_back(m.lognormalVolatility(k));
    }
    w.addCase(name,
              {{"expiry", expiry}, {"forward", fwd}, {"alpha", alpha}, {"beta", beta},
               {"nu", nu}, {"rho", rho}, {"gamma", gamma}, {"strikes", strikes}},
              {{"lognormal_vol", results}});
}

void addNormalCase(ReferenceWriter& w, const std::string& name,
                   Real expiry, Real fwd, Real alpha, Real beta,
                   Real nu, Real rho, Real gamma,
                   const std::vector<Real>& strikes) {
    ZabrModel m(expiry, fwd, alpha, beta, nu, rho, gamma);
    json results = json::array();
    for (Real k : strikes) {
        results.push_back(m.normalVolatility(k));
    }
    w.addCase(name,
              {{"expiry", expiry}, {"forward", fwd}, {"alpha", alpha}, {"beta", beta},
               {"nu", nu}, {"rho", rho}, {"gamma", gamma}, {"strikes", strikes}},
              {{"normal_vol", results}});
}

void addLocalCase(ReferenceWriter& w, const std::string& name,
                  Real expiry, Real fwd, Real alpha, Real beta,
                  Real nu, Real rho, Real gamma,
                  const std::vector<Real>& strikes) {
    ZabrModel m(expiry, fwd, alpha, beta, nu, rho, gamma);
    json results = json::array();
    for (Real k : strikes) {
        results.push_back(m.localVolatility(k));
    }
    w.addCase(name,
              {{"expiry", expiry}, {"forward", fwd}, {"alpha", alpha}, {"beta", beta},
               {"nu", nu}, {"rho", rho}, {"gamma", gamma}, {"strikes", strikes}},
              {{"local_vol", results}});
}

void addFdPriceCase(ReferenceWriter& w, const std::string& name,
                    Real expiry, Real fwd, Real alpha, Real beta,
                    Real nu, Real rho, Real gamma,
                    const std::vector<Real>& strikes) {
    ZabrModel m(expiry, fwd, alpha, beta, nu, rho, gamma);
    json results = json::array();
    for (Real k : strikes) {
        results.push_back(m.fdPrice(k));
    }
    w.addCase(name,
              {{"expiry", expiry}, {"forward", fwd}, {"alpha", alpha}, {"beta", beta},
               {"nu", nu}, {"rho", rho}, {"gamma", gamma}, {"strikes", strikes}},
              {{"fd_price", results}});
}

void addFullFdPriceCase(ReferenceWriter& w, const std::string& name,
                        Real expiry, Real fwd, Real alpha, Real beta,
                        Real nu, Real rho, Real gamma,
                        const std::vector<Real>& strikes) {
    ZabrModel m(expiry, fwd, alpha, beta, nu, rho, gamma);
    json results = json::array();
    for (Real k : strikes) {
        results.push_back(m.fullFdPrice(k));
    }
    w.addCase(name,
              {{"expiry", expiry}, {"forward", fwd}, {"alpha", alpha}, {"beta", beta},
               {"nu", nu}, {"rho", rho}, {"gamma", gamma}, {"strikes", strikes}},
              {{"full_fd_price", results}});
}

} // namespace

int main() {
    ReferenceWriter writer("experimental/volatility/zabr_model",
                           QL_VERSION,
                           "experimental/volatility/zabr_model_probe");

    // === gamma == 1 (closed-form x) ===
    // Standard SABR-like fixture from test-suite/zabr.cpp testConsistency
    Real alpha = 0.08, beta = 0.70, nu = 0.20, rho = -0.30;
    Real expiry = 5.0, fwd = 0.03;
    std::vector<Real> strikes_g1 = {0.005, 0.01, 0.015, 0.02, 0.025, 0.03,
                                    0.035, 0.04, 0.05, 0.07};

    addLognormalCase(writer, "lognormal_gamma1",
                     expiry, fwd, alpha, beta, nu, rho, 1.0, strikes_g1);
    addNormalCase(writer, "normal_gamma1",
                  expiry, fwd, alpha, beta, nu, rho, 1.0, strikes_g1);

    // === gamma != 1 (uses adaptive Runge-Kutta) ===
    addLognormalCase(writer, "lognormal_gamma075",
                     expiry, fwd, alpha, beta, nu, rho, 0.75, strikes_g1);
    addLognormalCase(writer, "lognormal_gamma125",
                     expiry, fwd, alpha, beta, nu, rho, 1.25, strikes_g1);
    addNormalCase(writer, "normal_gamma075",
                  expiry, fwd, alpha, beta, nu, rho, 0.75, strikes_g1);
    addNormalCase(writer, "normal_gamma05",
                  expiry, fwd, alpha, beta, nu, rho, 0.5, strikes_g1);

    // === localVolatility ===
    addLocalCase(writer, "local_gamma1",
                 expiry, fwd, alpha, beta, nu, rho, 1.0, strikes_g1);
    addLocalCase(writer, "local_gamma075",
                 expiry, fwd, alpha, beta, nu, rho, 0.75, strikes_g1);

    // === fdPrice (Dupire 1d) ===
    // Use a smaller strike grid — fdPrice constructs a ~500-point mesh.
    std::vector<Real> strikes_fd = {0.015, 0.025, 0.03, 0.035, 0.045};
    addFdPriceCase(writer, "fd_price_gamma1",
                   expiry, fwd, alpha, beta, nu, rho, 1.0, strikes_fd);

    // === fullFdPrice (2-D Glued1dMesher × Concentrating1dMesher) ===
    // Phase 4f.5c: each call builds a 100x100 mesh with steps=24*5+1=121
    // and Hundsdorfer scheme. Use a small strike list to bound runtime.
    std::vector<Real> strikes_full_fd = {0.025, 0.03, 0.035};
    addFullFdPriceCase(writer, "full_fd_price_gamma1",
                       expiry, fwd, alpha, beta, nu, rho, 1.0, strikes_full_fd);
    addFullFdPriceCase(writer, "full_fd_price_gamma075",
                       expiry, fwd, alpha, beta, nu, rho, 0.75, strikes_full_fd);

    // === beta = 1 case (degenerate y formula) ===
    std::vector<Real> strikes_b1 = {0.01, 0.02, 0.03, 0.04, 0.05};
    addLognormalCase(writer, "lognormal_beta1_gamma1",
                     1.0, 0.03, 0.08, 1.0, 0.20, -0.30, 1.0, strikes_b1);
    addLognormalCase(writer, "lognormal_beta1_gamma05",
                     1.0, 0.03, 0.08, 1.0, 0.20, -0.30, 0.5, strikes_b1);

    writer.write();
    std::cerr << "wrote: experimental/volatility/zabr_model" << std::endl;
    return 0;
}
