// migration-harness/cpp/probes/experimental/volatility/heston_black_vol_surface_probe.cpp
// Reference values for HestonBlackVolSurface vs QuantLib C++ v1.42.1.
//
// Black volatility surface backed by a calibrated Heston model. For each
// (t, K) query the C++ class:
//  1) prices the corresponding plain-vanilla payoff via AnalyticHestonEngine,
//  2) inverts the Black formula via Brent to recover the implied Black vol.
//
// Reference parameter set (matches several existing probes):
//   S0 = 100,  v0 = 0.04,  kappa = 2.0,  theta = 0.04,  sigma = 0.30,
//   rho = -0.5,  r = 0.05,  q = 0.02,  today = 15-Jan-2026,
//   dc = Actual365Fixed.

#include <ql/version.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/time/calendars/nullcalendar.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/processes/hestonprocess.hpp>
#include <ql/models/equity/hestonmodel.hpp>
#include <ql/termstructures/volatility/equityfx/hestonblackvolsurface.hpp>
#include "../../common.hpp"

using namespace QuantLib;
using namespace jqml_harness;

int main() {
    ReferenceWriter out("experimental/volatility/heston_black_vol_surface",
                        QL_VERSION,
                        "heston_black_vol_surface_probe");

    const Date today(15, January, 2026);
    const DayCounter dc = Actual365Fixed();

    auto spot = ext::make_shared<SimpleQuote>(100.0);
    auto rTS  = Handle<YieldTermStructure>(
                    ext::make_shared<FlatForward>(today, 0.05, dc));
    auto qTS  = Handle<YieldTermStructure>(
                    ext::make_shared<FlatForward>(today, 0.02, dc));

    const Real v0    = 0.04;
    const Real kappa = 2.0;
    const Real theta = 0.04;
    const Real sigma = 0.30;
    const Real rho   = -0.5;

    auto process = ext::make_shared<HestonProcess>(
        rTS, qTS, Handle<Quote>(spot), v0, kappa, theta, sigma, rho);
    auto model = Handle<HestonModel>(ext::make_shared<HestonModel>(process));

    // Use the simplest constructor — Gatheral default + Gauss-Laguerre 144.
    HestonBlackVolSurface surface(model);

    struct Spec { double t; double K; const char* name; };
    const Spec specs[] = {
        // ATM and skew across short/medium tenors.
        {0.25, 100.0, "atm_3m" },
        {0.25,  90.0, "K90_3m" },
        {0.25, 110.0, "K110_3m"},
        {0.50,  80.0, "K80_6m" },
        {0.50, 100.0, "atm_6m" },
        {0.50, 120.0, "K120_6m"},
        {1.00,  80.0, "K80_1y" },
        {1.00,  90.0, "K90_1y" },
        {1.00, 100.0, "atm_1y" },
        {1.00, 110.0, "K110_1y"},
        {1.00, 120.0, "K120_1y"},
        {2.00, 100.0, "atm_2y" },
        {2.00,  80.0, "K80_2y" },
        {2.00, 120.0, "K120_2y"},
        {3.00, 100.0, "atm_3y" },
    };

    for (const auto& sp : specs) {
        const double bv  = surface.blackVol(sp.t, sp.K, true);
        const double bvr = surface.blackVariance(sp.t, sp.K, true);
        out.addCase(sp.name,
            json{ {"t", sp.t}, {"K", sp.K} },
            json{ {"blackVol", bv}, {"blackVariance", bvr} });
    }

    out.write();
    return 0;
}
