// migration-harness/cpp/probes/pricingengines/vanilla/fd_black_scholes_vanilla_engine_probe.cpp
//
// Probe for Phase 2m Track A: FdBlackScholesVanillaEngine NPV fingerprint.
//
// Exercises FdBlackScholesVanillaEngine across a grid of (strike, maturity,
// vol, type, exercise) combinations using the Spot cash-dividend model and
// no explicit discrete dividends. No local-vol, no quanto-helper.
//
// Scheme: Douglas (default), tGrid=100, xGrid=100, dampingSteps=0.
// Process: GBS with FlatForward risk-free=5%, q=2%, BlackConstantVol vol.
// Spot = 100.0, eval = 2026-01-15.
//
// Tolerance tier: LOOSE (1e-8 rel) — FD engine accumulates numerical noise
// across 100 time steps and 100 mesh points.

#include <ql/version.hpp>

#include <ql/exercise.hpp>
#include <ql/instruments/vanillaoption.hpp>
#include <ql/instruments/payoffs.hpp>
#include <ql/methods/finitedifferences/solvers/fdmbackwardsolver.hpp>
#include <ql/pricingengines/vanilla/fdblackscholesvanillaengine.hpp>
#include <ql/processes/blackscholesprocess.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/settings.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/termstructures/volatility/equityfx/blackconstantvol.hpp>
#include <ql/time/calendars/nullcalendar.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>

#include "common.hpp"

using namespace QuantLib;
using namespace jqml_harness;

namespace {

ext::shared_ptr<GeneralizedBlackScholesProcess>
makeGBS(const Date& eval, double S, double r, double q, double vol,
        const DayCounter& dc) {
    const Handle<Quote> spot(ext::make_shared<SimpleQuote>(S));
    const Handle<YieldTermStructure> rTS(
        ext::make_shared<FlatForward>(eval, r, dc, Continuous, Annual));
    const Handle<YieldTermStructure> qTS(
        ext::make_shared<FlatForward>(eval, q, dc, Continuous, Annual));
    const Handle<BlackVolTermStructure> volTS(
        ext::make_shared<BlackConstantVol>(eval, NullCalendar(), vol, dc));
    return ext::make_shared<GeneralizedBlackScholesProcess>(spot, qTS, rTS, volTS);
}

void addCase(ReferenceWriter& out,
             const std::string& name,
             const ext::shared_ptr<GeneralizedBlackScholesProcess>& process,
             double strike, double maturityYears,
             Option::Type type, Exercise::Type exType,
             const FdmSchemeDesc& scheme,
             int tGrid, int xGrid, int dampingSteps,
             const Date& eval, const DayCounter& dc) {

    const Date exerciseDate = eval + int(maturityYears * 365 + 0.5);

    ext::shared_ptr<Exercise> exercise;
    std::string exTypeStr;
    if (exType == Exercise::European) {
        exercise = ext::make_shared<EuropeanExercise>(exerciseDate);
        exTypeStr = "European";
    } else {
        exercise = ext::make_shared<AmericanExercise>(eval, exerciseDate);
        exTypeStr = "American";
    }

    const auto payoff = ext::make_shared<PlainVanillaPayoff>(type, strike);
    VanillaOption option(payoff, exercise);

    option.setPricingEngine(
        ext::make_shared<FdBlackScholesVanillaEngine>(
            process, tGrid, xGrid, dampingSteps, scheme));

    const double npv = option.NPV();
    const double delta = option.delta();
    const double gamma = option.gamma();

    const std::string typeStr = (type == Option::Call) ? "Call" : "Put";

    json inputs = {
        {"strike", strike},
        {"maturity_years", maturityYears},
        {"option_type", typeStr},
        {"exercise_type", exTypeStr},
        {"t_grid", tGrid},
        {"x_grid", xGrid},
        {"damping_steps", dampingSteps}
    };
    json expected = {
        {"npv", npv},
        {"delta", delta},
        {"gamma", gamma}
    };

    out.addCase(name, inputs, expected);
}

} // anonymous namespace

int main() {
    ReferenceWriter out("pricingengines/vanilla/fd_black_scholes_vanilla_engine",
                        QL_VERSION,
                        "fd_black_scholes_vanilla_engine_probe");

    const Date eval(15, January, 2026);
    Settings::instance().evaluationDate() = eval;
    const DayCounter dc = Actual365Fixed();

    // Base process: S=100, r=5%, q=2%, vol=20%
    const double S    = 100.0;
    const double r    = 0.05;
    const double q    = 0.02;
    const double vol  = 0.20;

    const auto p20 = makeGBS(eval, S, r, q, 0.20, dc);
    const auto p30 = makeGBS(eval, S, r, q, 0.30, dc);
    const auto p10 = makeGBS(eval, S, r, q, 0.10, dc);

    const FdmSchemeDesc douglas = FdmSchemeDesc::Douglas();

    // --- European calls ---
    addCase(out, "eur_call_atm_1y_v20",      p20, 100.0, 1.0, Option::Call, Exercise::European, douglas, 100, 100, 0, eval, dc);
    addCase(out, "eur_call_otm_1y_v20",      p20, 110.0, 1.0, Option::Call, Exercise::European, douglas, 100, 100, 0, eval, dc);
    addCase(out, "eur_call_itm_1y_v20",      p20,  90.0, 1.0, Option::Call, Exercise::European, douglas, 100, 100, 0, eval, dc);
    addCase(out, "eur_call_atm_2y_v20",      p20, 100.0, 2.0, Option::Call, Exercise::European, douglas, 100, 100, 0, eval, dc);
    addCase(out, "eur_call_atm_half_y_v20",  p20, 100.0, 0.5, Option::Call, Exercise::European, douglas, 100, 100, 0, eval, dc);
    addCase(out, "eur_call_atm_1y_v30",      p30, 100.0, 1.0, Option::Call, Exercise::European, douglas, 100, 100, 0, eval, dc);
    addCase(out, "eur_call_atm_1y_v10",      p10, 100.0, 1.0, Option::Call, Exercise::European, douglas, 100, 100, 0, eval, dc);

    // --- European puts ---
    addCase(out, "eur_put_atm_1y_v20",       p20, 100.0, 1.0, Option::Put,  Exercise::European, douglas, 100, 100, 0, eval, dc);
    addCase(out, "eur_put_otm_1y_v20",       p20,  90.0, 1.0, Option::Put,  Exercise::European, douglas, 100, 100, 0, eval, dc);
    addCase(out, "eur_put_itm_1y_v20",       p20, 110.0, 1.0, Option::Put,  Exercise::European, douglas, 100, 100, 0, eval, dc);
    addCase(out, "eur_put_atm_2y_v20",       p20, 100.0, 2.0, Option::Put,  Exercise::European, douglas, 100, 100, 0, eval, dc);
    addCase(out, "eur_put_atm_1y_v30",       p30, 100.0, 1.0, Option::Put,  Exercise::European, douglas, 100, 100, 0, eval, dc);

    // --- American calls ---
    addCase(out, "amer_call_atm_1y_v20",     p20, 100.0, 1.0, Option::Call, Exercise::American, douglas, 100, 100, 0, eval, dc);
    addCase(out, "amer_call_otm_1y_v20",     p20, 110.0, 1.0, Option::Call, Exercise::American, douglas, 100, 100, 0, eval, dc);
    addCase(out, "amer_call_itm_1y_v20",     p20,  90.0, 1.0, Option::Call, Exercise::American, douglas, 100, 100, 0, eval, dc);
    addCase(out, "amer_call_atm_2y_v20",     p20, 100.0, 2.0, Option::Call, Exercise::American, douglas, 100, 100, 0, eval, dc);
    addCase(out, "amer_call_atm_1y_v30",     p30, 100.0, 1.0, Option::Call, Exercise::American, douglas, 100, 100, 0, eval, dc);

    // --- American puts ---
    addCase(out, "amer_put_atm_1y_v20",      p20, 100.0, 1.0, Option::Put,  Exercise::American, douglas, 100, 100, 0, eval, dc);
    addCase(out, "amer_put_otm_1y_v20",      p20,  90.0, 1.0, Option::Put,  Exercise::American, douglas, 100, 100, 0, eval, dc);
    addCase(out, "amer_put_itm_1y_v20",      p20, 110.0, 1.0, Option::Put,  Exercise::American, douglas, 100, 100, 0, eval, dc);
    addCase(out, "amer_put_atm_2y_v20",      p20, 100.0, 2.0, Option::Put,  Exercise::American, douglas, 100, 100, 0, eval, dc);
    addCase(out, "amer_put_atm_1y_v30",      p30, 100.0, 1.0, Option::Put,  Exercise::American, douglas, 100, 100, 0, eval, dc);

    // --- Damping steps ---
    addCase(out, "eur_call_atm_1y_damping2", p20, 100.0, 1.0, Option::Call, Exercise::European, douglas, 100, 100, 2, eval, dc);
    addCase(out, "amer_put_atm_1y_damping2", p20, 100.0, 1.0, Option::Put,  Exercise::American, douglas, 100, 100, 2, eval, dc);

    // --- Different grid sizes ---
    addCase(out, "eur_call_atm_1y_g50",      p20, 100.0, 1.0, Option::Call, Exercise::European, douglas, 50, 50, 0, eval, dc);
    addCase(out, "eur_call_atm_1y_g200",     p20, 100.0, 1.0, Option::Call, Exercise::European, douglas, 200, 200, 0, eval, dc);

    // --- ImplicitEuler scheme ---
    {
        const FdmSchemeDesc ie = FdmSchemeDesc::ImplicitEuler();
        addCase(out, "eur_call_atm_1y_ie",   p20, 100.0, 1.0, Option::Call, Exercise::European, ie, 100, 100, 0, eval, dc);
        addCase(out, "amer_put_atm_1y_ie",   p20, 100.0, 1.0, Option::Put,  Exercise::American, ie, 100, 100, 0, eval, dc);
    }

    // --- High-vol deep-ITM/OTM (stress cases) ---
    const auto pHigh = makeGBS(eval, S, r, q, 0.50, dc);
    addCase(out, "eur_call_deepotm_1y_v50",  pHigh, 150.0, 1.0, Option::Call, Exercise::European, douglas, 100, 100, 0, eval, dc);
    addCase(out, "eur_put_deepitm_1y_v50",   pHigh, 150.0, 1.0, Option::Put,  Exercise::European, douglas, 100, 100, 0, eval, dc);
    addCase(out, "amer_put_deepitm_1y_v50",  pHigh, 150.0, 1.0, Option::Put,  Exercise::American, douglas, 100, 100, 0, eval, dc);

    out.write();
    return 0;
}
