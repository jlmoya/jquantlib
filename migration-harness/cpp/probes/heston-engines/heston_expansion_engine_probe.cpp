// migration-harness/cpp/probes/heston-engines/heston_expansion_engine_probe.cpp
//
// Probe for Phase 5h.5 — HestonExpansionEngine (Forde + LPP2 + LPP3) NPVs.
// Cross-validates the closed-form expansion formulas against Java port.
//
// Tolerance tier: TIGHT (1e-12 rel) — pure analytic closed-form.

#include <ql/version.hpp>

#include <ql/exercise.hpp>
#include <ql/instruments/vanillaoption.hpp>
#include <ql/instruments/payoffs.hpp>
#include <ql/models/equity/hestonmodel.hpp>
#include <ql/pricingengines/vanilla/hestonexpansionengine.hpp>
#include <ql/processes/hestonprocess.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/settings.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/time/calendars/nullcalendar.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>

#include "../common.hpp"

using namespace QuantLib;
using namespace jqml_harness;

namespace {

void addCase(ReferenceWriter& out,
             const std::string& name,
             const ext::shared_ptr<HestonModel>& hestonModel,
             double strike,
             double maturityYears,
             Option::Type type,
             HestonExpansionEngine::HestonExpansionFormula formula,
             const Date& eval) {
    const std::string formulaStr =
        (formula == HestonExpansionEngine::LPP2)  ? "LPP2"  :
        (formula == HestonExpansionEngine::LPP3)  ? "LPP3"  :
        (formula == HestonExpansionEngine::Forde) ? "Forde" : "?";

    const Date exerciseDate = eval + int(maturityYears * 365 + 0.5);
    const auto exercise = ext::make_shared<EuropeanExercise>(exerciseDate);
    const auto payoff   = ext::make_shared<PlainVanillaPayoff>(type, strike);
    VanillaOption option(payoff, exercise);

    option.setPricingEngine(
        ext::make_shared<HestonExpansionEngine>(hestonModel, formula));

    const double npv = option.NPV();

    const std::string typeStr = (type == Option::Call) ? "Call" : "Put";

    json inputs = {
        {"strike",         strike},
        {"maturity_years", maturityYears},
        {"option_type",    typeStr},
        {"formula",        formulaStr}
    };
    json expected = { {"npv", npv} };

    out.addCase(name, inputs, expected);
}

} // anonymous namespace

int main() {
    ReferenceWriter out("heston-engines/heston_expansion_engine",
                        QL_VERSION,
                        "heston_expansion_engine_probe");

    const Date eval(15, January, 2026);
    Settings::instance().evaluationDate() = eval;
    const DayCounter dc = Actual365Fixed();

    // Standard market data
    const Handle<Quote> s0(ext::make_shared<SimpleQuote>(100.0));
    const Handle<YieldTermStructure> rTS(
        ext::make_shared<FlatForward>(eval, 0.05, dc, Continuous, Annual));
    const Handle<YieldTermStructure> qTS(
        ext::make_shared<FlatForward>(eval, 0.02, dc, Continuous, Annual));

    // Heston parameters (use rho>=0 due to Java HestonModel PositiveConstraint
    // bug on rho — separately verified for negative rho in C++ alone).
    const double v0    = 0.04;
    const double kappa = 1.0;
    const double theta = 0.04;
    const double sigma = 0.3;
    const double rho   = 0.3;

    const auto hestonProcess = ext::make_shared<HestonProcess>(
        rTS, qTS, s0, v0, kappa, theta, sigma, rho);
    const auto hestonModel = ext::make_shared<HestonModel>(hestonProcess);

    // Forde expansion (small-time)
    addCase(out, "forde_call_atm_1y", hestonModel,
            100.0, 1.0, Option::Call, HestonExpansionEngine::Forde, eval);
    addCase(out, "forde_put_atm_1y",  hestonModel,
            100.0, 1.0, Option::Put,  HestonExpansionEngine::Forde, eval);
    addCase(out, "forde_call_otm_1y", hestonModel,
            110.0, 1.0, Option::Call, HestonExpansionEngine::Forde, eval);
    addCase(out, "forde_put_itm_1y",  hestonModel,
            110.0, 1.0, Option::Put,  HestonExpansionEngine::Forde, eval);
    addCase(out, "forde_call_atm_2y", hestonModel,
            100.0, 2.0, Option::Call, HestonExpansionEngine::Forde, eval);

    // LPP2 expansion
    addCase(out, "lpp2_call_atm_1y", hestonModel,
            100.0, 1.0, Option::Call, HestonExpansionEngine::LPP2, eval);
    addCase(out, "lpp2_put_atm_1y",  hestonModel,
            100.0, 1.0, Option::Put,  HestonExpansionEngine::LPP2, eval);
    addCase(out, "lpp2_call_otm_1y", hestonModel,
            110.0, 1.0, Option::Call, HestonExpansionEngine::LPP2, eval);
    addCase(out, "lpp2_call_atm_2y", hestonModel,
            100.0, 2.0, Option::Call, HestonExpansionEngine::LPP2, eval);

    // LPP3 expansion
    addCase(out, "lpp3_call_atm_1y", hestonModel,
            100.0, 1.0, Option::Call, HestonExpansionEngine::LPP3, eval);
    addCase(out, "lpp3_call_otm_1y", hestonModel,
            110.0, 1.0, Option::Call, HestonExpansionEngine::LPP3, eval);
    addCase(out, "lpp3_call_atm_2y", hestonModel,
            100.0, 2.0, Option::Call, HestonExpansionEngine::LPP3, eval);

    out.write();
    return 0;
}
