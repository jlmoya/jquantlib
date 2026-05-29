// migration-harness/cpp/probes/methods/finitedifferences/meshers/fdm_heston_localvol_variance_mesher_probe.cpp
//
// Reference values for the gap-fdm port of
// FdmHestonLocalVolatilityVarianceMesher (Heston-SLV variance mesher).
//
// Deterministic for fixed Heston params + a fixed leverage function — TIGHT.
//
// Three cases:
//   1. leverageFct = nullptr  -> volaEstimate == plain FdmHestonVarianceMesher
//   2. leverageFct = LocalConstantVol(L=2.0) -> running mean stays L; the
//      Gauss-Lobatto integral of a constant integrand is exact, so the final
//      volaEstimate == plainVolaEstimate * 2.0  (structurally validates the
//      whole averaging loop: forward computation, sampling, integration).
//   3. leverageFct = LocalConstantVol(L=0.5) -> volaEstimate == plain * 0.5.
//
// The mesh locations / dplus / dminus are inherited verbatim from
// FdmHestonVarianceMesher (already cross-validated elsewhere) and are emitted
// for case 1 to confirm the copy is faithful.

#include <ql/version.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/termstructures/volatility/equityfx/localconstantvol.hpp>
#include <ql/processes/hestonprocess.hpp>
#include <ql/methods/finitedifferences/meshers/fdmhestonvariancemesher.hpp>
#include "../../../common.hpp"

#include <cmath>
#include <vector>

using namespace QuantLib;
using namespace jqml_harness;

namespace {

ext::shared_ptr<HestonProcess> makeHestonProcess() {
    const Date today(15, January, 2026);
    Settings::instance().evaluationDate() = today;
    const DayCounter dc = Actual365Fixed();
    Handle<Quote> s0(ext::make_shared<SimpleQuote>(100.0));
    Handle<YieldTermStructure> rTS(ext::make_shared<FlatForward>(today, 0.05, dc));
    Handle<YieldTermStructure> qTS(ext::make_shared<FlatForward>(today, 0.02, dc));
    // v0=0.04, kappa=2.0, theta=0.04, sigma=0.30, rho=-0.5
    return ext::make_shared<HestonProcess>(rTS, qTS, s0, 0.04, 2.0, 0.04, 0.30, -0.5);
}

json meshToJson(const FdmHestonLocalVolatilityVarianceMesher& m, std::size_t size) {
    json locs = json::array(), dplus = json::array(), dminus = json::array();
    for (std::size_t i = 0; i < size; ++i) {
        locs.push_back(m.location(i));
        dplus.push_back(std::isnan(m.dplus(i)) ? json(nullptr) : json(m.dplus(i)));
        dminus.push_back(std::isnan(m.dminus(i)) ? json(nullptr) : json(m.dminus(i)));
    }
    return json{ {"locations", locs}, {"dplus", dplus}, {"dminus", dminus},
                 {"volaEstimate", m.volaEstimate()} };
}

} // namespace

int main() {
    ReferenceWriter out("methods/finitedifferences/meshers/fdm_heston_localvol_variance_mesher",
                        QL_VERSION,
                        "fdm_heston_localvol_variance_mesher_probe");

    const std::size_t size = 10;
    const double maturity = 1.0;
    const int tAvgSteps = 10;
    const double epsilon = 1e-4;
    const double mixingFactor = 1.0;
    const Date today(15, January, 2026);
    const DayCounter dc = Actual365Fixed();

    // Case 1: no leverage (nullptr)
    {
        auto hp = makeHestonProcess();
        FdmHestonLocalVolatilityVarianceMesher mesh(
            size, hp, ext::shared_ptr<LocalVolTermStructure>(),
            maturity, tAvgSteps, epsilon, mixingFactor);
        out.addCase("no_leverage_size10_T1",
            json{ {"size", (long long)size}, {"maturity", maturity},
                  {"tAvgSteps", tAvgSteps}, {"epsilon", epsilon},
                  {"mixingFactor", mixingFactor}, {"leverage", nullptr} },
            meshToJson(mesh, size));
    }

    // Case 2: constant leverage L = 2.0
    {
        auto hp = makeHestonProcess();
        auto lev = ext::shared_ptr<LocalVolTermStructure>(
            new LocalConstantVol(today, 2.0, dc));
        FdmHestonLocalVolatilityVarianceMesher mesh(
            size, hp, lev, maturity, tAvgSteps, epsilon, mixingFactor);
        out.addCase("const_leverage_2_size10_T1",
            json{ {"size", (long long)size}, {"maturity", maturity},
                  {"tAvgSteps", tAvgSteps}, {"epsilon", epsilon},
                  {"mixingFactor", mixingFactor}, {"leverage", 2.0} },
            meshToJson(mesh, size));
    }

    // Case 3: constant leverage L = 0.5
    {
        auto hp = makeHestonProcess();
        auto lev = ext::shared_ptr<LocalVolTermStructure>(
            new LocalConstantVol(today, 0.5, dc));
        FdmHestonLocalVolatilityVarianceMesher mesh(
            size, hp, lev, maturity, tAvgSteps, epsilon, mixingFactor);
        out.addCase("const_leverage_0_5_size10_T1",
            json{ {"size", (long long)size}, {"maturity", maturity},
                  {"tAvgSteps", tAvgSteps}, {"epsilon", epsilon},
                  {"mixingFactor", mixingFactor}, {"leverage", 0.5} },
            meshToJson(mesh, size));
    }

    out.write();
    return 0;
}
