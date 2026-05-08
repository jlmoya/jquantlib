// migration-harness/cpp/probes/pricingengines/vanilla/fd_sabr_vanilla_engine_probe.cpp
//
// Phase 2m Track C — FdSabrVanillaEngine NPV fingerprint.
//
// Oracle: C++ QuantLib v1.42.1 FdSabrVanillaEngine.
//
// Fixture: eval=2026-01-15, FlatForward at various rates, European exercise.
// SABR parameters: various f0, alpha, beta, nu, rho combinations.
// Grid: tGrid=50, fGrid=400, xGrid=50, dampingSteps=0, scalingFactor=1.0, eps=1e-4.
// Scheme: Hundsdorfer (default).
//
// ~24 cases: (call+put) x (3 beta values) x (4 strike/rate combos).
//
// Output: JSON with NPV values, LOOSE tier (1e-4 relative) due to FD grid
// discretisation error; the engine's own put-call parity holds to ~1e-4.

#include <ql/version.hpp>

#include <ql/exercise.hpp>
#include <ql/instruments/vanillaoption.hpp>
#include <ql/instruments/payoffs.hpp>
#include <ql/pricingengines/vanilla/fdsabrvanillaengine.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/settings.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>

#include "common.hpp"

#include <cstdio>
#include <string>
#include <vector>

using namespace QuantLib;
using namespace jqml_harness;

int main() {
    ReferenceWriter out(
        "pricingengines/vanilla/fd_sabr_vanilla_engine",
        QL_VERSION,
        "fd_sabr_vanilla_engine_probe");

    const Date eval(15, January, 2026);
    Settings::instance().evaluationDate() = eval;

    const DayCounter dc = Actual365Fixed();
    // SABR base parameters (matching fdsabr.cpp test-suite fixture)
    const Real f0    = 1.0;
    const Real alpha = 0.35;
    const Real nu    = 1.0;
    const Real rho   = 0.25;

    // maturity: 2 years from eval
    const Date maturityDate = eval + Period(2, Years);
    const auto exercise = ext::make_shared<EuropeanExercise>(maturityDate);

    // FD grid: reduced from test-suite 400/100 to 400/50/50 to keep probe fast
    const Size tGrid        = 50;
    const Size fGrid        = 400;
    const Size xGrid        = 50;
    const Size dampingSteps = 0;
    const Real scalingFactor = 1.0;
    const Real eps           = 1e-4;

    // beta values
    const std::vector<Real> betas = { 0.25, 0.5, 0.6 };

    // strikes relative to f0
    const std::vector<Real> strikes = { 0.7, 1.0, 1.5 };

    // discount rates
    const std::vector<Real> rates = { 0.0, 0.03 };

    // option types
    const Option::Type optTypes[] = { Option::Call, Option::Put };
    const std::string  optNames[] = { "call", "put" };

    int caseIdx = 0;
    for (Real rate : rates) {
        const Handle<YieldTermStructure> rTS(
            ext::make_shared<FlatForward>(eval, rate, dc, Continuous, Annual));

        for (Real beta : betas) {
            const auto engine = ext::make_shared<FdSabrVanillaEngine>(
                f0, alpha, beta, nu, rho, rTS,
                tGrid, fGrid, xGrid, dampingSteps, scalingFactor, eps);

            for (Real strike : strikes) {
                for (int oi = 0; oi < 2; ++oi) {
                    const auto payoff = ext::make_shared<PlainVanillaPayoff>(
                        optTypes[oi], strike);
                    VanillaOption opt(payoff, exercise);
                    opt.setPricingEngine(engine);

                    const Real npv = opt.NPV();

                    const std::string caseName =
                        optNames[oi] + "_beta" + std::to_string(beta)
                        + "_K" + std::to_string(strike)
                        + "_r" + std::to_string(rate);

                    out.addCase(
                        caseName,
                        {{"option_type",   optNames[oi]},
                         {"f0",            f0},
                         {"alpha",         alpha},
                         {"beta",          beta},
                         {"nu",            nu},
                         {"rho",           rho},
                         {"strike",        strike},
                         {"rate",          rate},
                         {"maturity_years", 2.0},
                         {"tGrid",         (int)tGrid},
                         {"fGrid",         (int)fGrid},
                         {"xGrid",         (int)xGrid}},
                        {{"npv", npv}});

                    ++caseIdx;
                }
            }
        }
    }

    out.write();
    std::printf("Wrote %d cases\n", caseIdx);
    return 0;
}
