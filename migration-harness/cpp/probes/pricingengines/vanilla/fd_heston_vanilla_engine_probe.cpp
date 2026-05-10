// migration-harness/cpp/probes/pricingengines/vanilla/fd_heston_vanilla_engine_probe.cpp
//
// Probe for Phase 4n.5: FdHestonVanillaEngine NPV/delta/gamma fingerprint.
//
// Process: S=100, r=5% flat, q=2% flat.
// Heston:  v0=0.04 (20% vol), kappa=1.0, theta=0.04, sigma=0.3, rho=+0.3.
// Eval:    2026-01-15.
//
// Tolerance tier: LOOSE (1e-2 abs/rel) -- 2-factor FD on coarse grid
// (50 t-steps, 50 x-grid, 20 v-grid) used to keep CI runtime low.

#include <ql/version.hpp>

#include <ql/exercise.hpp>
#include <ql/instruments/vanillaoption.hpp>
#include <ql/instruments/payoffs.hpp>
#include <ql/methods/finitedifferences/solvers/fdmbackwardsolver.hpp>
#include <ql/models/equity/hestonmodel.hpp>
#include <ql/pricingengines/vanilla/fdhestonvanillaengine.hpp>
#include <ql/processes/hestonprocess.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/settings.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/time/calendars/nullcalendar.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>

#include "common.hpp"

using namespace QuantLib;
using namespace jqml_harness;

namespace {

void addCase(ReferenceWriter& out,
             const std::string& name,
             const ext::shared_ptr<HestonModel>& hestonModel,
             double strike,
             double maturityYears,
             Option::Type type,
             const Date& eval,
             const DayCounter& dc,
             int tGrid = 50,
             int xGrid = 50,
             int vGrid = 20,
             int dampingSteps = 0) {

    const Date exerciseDate = eval + int(maturityYears * 365 + 0.5);
    const auto exercise = ext::make_shared<EuropeanExercise>(exerciseDate);
    const auto payoff   = ext::make_shared<PlainVanillaPayoff>(type, strike);
    VanillaOption option(payoff, exercise);

    option.setPricingEngine(
        ext::make_shared<FdHestonVanillaEngine>(
            hestonModel,
            tGrid, xGrid, vGrid, dampingSteps,
            FdmSchemeDesc::Hundsdorfer()));

    const double npv   = option.NPV();
    const double delta = option.delta();
    const double gamma = option.gamma();

    const std::string typeStr = (type == Option::Call) ? "Call" : "Put";

    json inputs = {
        {"strike",         strike},
        {"maturity_years", maturityYears},
        {"option_type",    typeStr},
        {"t_grid",         tGrid},
        {"x_grid",         xGrid},
        {"v_grid",         vGrid},
        {"damping_steps",  dampingSteps}
    };
    json expected = {
        {"npv",   npv},
        {"delta", delta},
        {"gamma", gamma}
    };

    out.addCase(name, inputs, expected);
}

} // anonymous namespace

int main() {
    ReferenceWriter out("pricingengines/vanilla/fd_heston_vanilla_engine",
                        QL_VERSION,
                        "fd_heston_vanilla_engine_probe");

    const Date eval(15, January, 2026);
    Settings::instance().evaluationDate() = eval;
    const DayCounter dc = Actual365Fixed();

    // Market data
    const Handle<Quote> s0(ext::make_shared<SimpleQuote>(100.0));
    const Handle<YieldTermStructure> rTS(
        ext::make_shared<FlatForward>(eval, 0.05, dc, Continuous, Annual));
    const Handle<YieldTermStructure> qTS(
        ext::make_shared<FlatForward>(eval, 0.02, dc, Continuous, Annual));

    // Heston parameters
    // NOTE: matches FdHestonHullWhite probe — Java HestonModel uses
    // PositiveConstraint for rho, so positive rho is required.
    const double v0    = 0.04;
    const double kappa = 1.0;
    const double theta = 0.04;
    const double sigma = 0.3;
    const double rho   = 0.3;

    const auto hestonProcess = ext::make_shared<HestonProcess>(
        rTS, qTS, s0, v0, kappa, theta, sigma, rho);
    const auto hestonModel = ext::make_shared<HestonModel>(hestonProcess);

    // --- ATM call/put, 1y ---
    addCase(out, "eur_call_atm_1y", hestonModel, 100.0, 1.0, Option::Call, eval, dc);
    addCase(out, "eur_put_atm_1y",  hestonModel, 100.0, 1.0, Option::Put,  eval, dc);

    // --- OTM/ITM, 1y ---
    addCase(out, "eur_call_otm_1y", hestonModel, 110.0, 1.0, Option::Call, eval, dc);
    addCase(out, "eur_put_otm_1y",  hestonModel,  90.0, 1.0, Option::Put,  eval, dc);

    // --- 2y ATM ---
    addCase(out, "eur_call_atm_2y", hestonModel, 100.0, 2.0, Option::Call, eval, dc);

    out.write();
    return 0;
}
