// migration-harness/cpp/probes/heston-slv/heston_slv_process_probe.cpp
//
// Reference values for HestonSLVProcess (Phase 5h.5-SLV WI-3).
//
// drift, diffusion: standalone analytic. evolve: tested against a single
// MersenneTwister seed.

#include <ql/version.hpp>
#include <ql/processes/hestonslvprocess.hpp>
#include <ql/processes/hestonprocess.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/termstructures/volatility/equityfx/localconstantvol.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>
#include <ql/math/array.hpp>
#include <ql/math/matrix.hpp>
#include <vector>
#include "../common.hpp"

using namespace QuantLib;
using namespace jqml_harness;

namespace {

json arrayToJson(const Array& a) {
    json j = json::array();
    for (Size i = 0; i < a.size(); ++i) j.push_back(a[i]);
    return j;
}

json matrixToJson(const Matrix& m) {
    json j = json::array();
    for (Size i = 0; i < m.rows(); ++i) {
        json row = json::array();
        for (Size k = 0; k < m.columns(); ++k) row.push_back(m[i][k]);
        j.push_back(row);
    }
    return j;
}

} // namespace

int main() {
    ReferenceWriter out("heston-slv/heston_slv_process",
                        QL_VERSION,
                        "heston_slv_process_probe");

    Date today(15, May, 2026);
    DayCounter dc = Actual365Fixed();
    const double r = 0.03, q = 0.01, s0 = 100.0;
    const double v0 = 0.04, kappa = 2.5, theta = 0.04, sigma = 0.2, rho = -0.5;

    Handle<YieldTermStructure> rTS(ext::make_shared<FlatForward>(today, r, dc));
    Handle<YieldTermStructure> qTS(ext::make_shared<FlatForward>(today, q, dc));
    Handle<Quote> spot(ext::make_shared<SimpleQuote>(s0));
    auto hp = ext::make_shared<HestonProcess>(rTS, qTS, spot, v0, kappa, theta, sigma, rho);

    // Constant leverage L = 1.2
    auto leverage = ext::make_shared<LocalConstantVol>(today, 1.2, dc);

    HestonSLVProcess slv(hp, leverage, 1.0);

    // 1) drift, diffusion at (t=0.5, x=[100, 0.04])
    {
        Array x(2);
        x[0] = 100.0; x[1] = 0.04;
        const Array d = slv.drift(0.5, x);
        const Matrix s = slv.diffusion(0.5, x);
        json inputs = {{"r", r}, {"q", q}, {"s0", s0}, {"v0", v0},
                       {"kappa", kappa}, {"theta", theta}, {"sigma", sigma},
                       {"rho", rho}, {"L", 1.2}, {"t", 0.5},
                       {"x", json::array({100.0, 0.04})}};
        json expected = {{"drift", arrayToJson(d)}, {"diffusion", matrixToJson(s)}};
        out.addCase("drift_diffusion_at_atm", inputs, expected);
    }

    // 2) drift, diffusion at (t=1.0, x=[120, 0.06])
    {
        Array x(2);
        x[0] = 120.0; x[1] = 0.06;
        const Array d = slv.drift(1.0, x);
        const Matrix s = slv.diffusion(1.0, x);
        json inputs = {{"r", r}, {"q", q}, {"s0", s0}, {"v0", v0},
                       {"kappa", kappa}, {"theta", theta}, {"sigma", sigma},
                       {"rho", rho}, {"L", 1.2}, {"t", 1.0},
                       {"x", json::array({120.0, 0.06})}};
        json expected = {{"drift", arrayToJson(d)}, {"diffusion", matrixToJson(s)}};
        out.addCase("drift_diffusion_otm_high_v", inputs, expected);
    }

    // 3) evolve at (t0=0.0, dt=0.1, x0=[100, 0.04], dw=[0.5, -0.3]) — psi<1.5 path
    {
        Array x0(2); x0[0] = 100.0; x0[1] = 0.04;
        Array dw(2); dw[0] = 0.5;   dw[1] = -0.3;
        const Array r1 = slv.evolve(0.0, x0, 0.1, dw);
        json inputs = {{"r", r}, {"q", q}, {"s0", s0}, {"v0", v0},
                       {"kappa", kappa}, {"theta", theta}, {"sigma", sigma},
                       {"rho", rho}, {"L", 1.2},
                       {"t0", 0.0}, {"dt", 0.1},
                       {"x0", json::array({100.0, 0.04})},
                       {"dw", json::array({0.5, -0.3})}};
        out.addCase("evolve_small_dt", inputs, arrayToJson(r1));
    }

    // 4) evolve at (t0=0.5, dt=2.0, x0=[100, 0.5], dw=[1.0, 1.5]) — psi>=1.5 path
    {
        Array x0(2); x0[0] = 100.0; x0[1] = 0.5;
        Array dw(2); dw[0] = 1.0;   dw[1] = 1.5;
        const Array r1 = slv.evolve(0.5, x0, 2.0, dw);
        json inputs = {{"r", r}, {"q", q}, {"s0", s0}, {"v0", v0},
                       {"kappa", kappa}, {"theta", theta}, {"sigma", sigma},
                       {"rho", rho}, {"L", 1.2},
                       {"t0", 0.5}, {"dt", 2.0},
                       {"x0", json::array({100.0, 0.5})},
                       {"dw", json::array({1.0, 1.5})}};
        out.addCase("evolve_long_dt", inputs, arrayToJson(r1));
    }

    out.write();
    return 0;
}
