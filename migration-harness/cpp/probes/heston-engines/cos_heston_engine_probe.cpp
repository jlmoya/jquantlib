// migration-harness/cpp/probes/heston-engines/cos_heston_engine_probe.cpp
//
// Probe for Phase 5h.5 — COSHestonEngine (Fang-Oosterlee Fourier-Cosine).
// Cross-validates NPV plus c1/c2/c3/c4 cumulants against Java port.
//
// Tolerance tier: TIGHT (1e-9 abs) — pure analytic Fang-Oosterlee
// (slight noise from N=200 truncation series; tighter than 1e-12).

#include <ql/version.hpp>

#include <ql/exercise.hpp>
#include <ql/instruments/vanillaoption.hpp>
#include <ql/instruments/payoffs.hpp>
#include <ql/models/equity/hestonmodel.hpp>
#include <ql/pricingengines/vanilla/coshestonengine.hpp>
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

void addPriceCase(ReferenceWriter& out,
                  const std::string& name,
                  const ext::shared_ptr<HestonModel>& hestonModel,
                  double strike,
                  double maturityYears,
                  Option::Type type,
                  const Date& eval,
                  double L = 16.0,
                  std::size_t N = 200) {

    const Date exerciseDate = eval + int(maturityYears * 365 + 0.5);
    const auto exercise = ext::make_shared<EuropeanExercise>(exerciseDate);
    const auto payoff   = ext::make_shared<PlainVanillaPayoff>(type, strike);
    VanillaOption option(payoff, exercise);

    option.setPricingEngine(
        ext::make_shared<COSHestonEngine>(hestonModel, L, N));

    const double npv = option.NPV();
    const std::string typeStr = (type == Option::Call) ? "Call" : "Put";

    json inputs = {
        {"strike",         strike},
        {"maturity_years", maturityYears},
        {"option_type",    typeStr},
        {"L",              L},
        {"N",              N}
    };
    json expected = { {"npv", npv} };

    out.addCase(name, inputs, expected);
}

void addCumulantCase(ReferenceWriter& out,
                     const std::string& name,
                     const ext::shared_ptr<HestonModel>& hestonModel,
                     double t) {
    COSHestonEngine eng(hestonModel, 16, 200);
    json inputs = { {"t", t} };
    json expected = {
        {"c1", eng.c1(t)},
        {"c2", eng.c2(t)},
        {"c3", eng.c3(t)},
        {"c4", eng.c4(t)},
        {"mu",       eng.mu(t)},
        {"var",      eng.var(t)},
        {"skew",     eng.skew(t)},
        {"kurtosis", eng.kurtosis(t)}
    };
    out.addCase(name, inputs, expected);
}

} // anonymous namespace

int main() {
    ReferenceWriter out("heston-engines/cos_heston_engine",
                        QL_VERSION,
                        "cos_heston_engine_probe");

    const Date eval(15, January, 2026);
    Settings::instance().evaluationDate() = eval;
    const DayCounter dc = Actual365Fixed();

    const Handle<Quote> s0(ext::make_shared<SimpleQuote>(100.0));
    const Handle<YieldTermStructure> rTS(
        ext::make_shared<FlatForward>(eval, 0.05, dc, Continuous, Annual));
    const Handle<YieldTermStructure> qTS(
        ext::make_shared<FlatForward>(eval, 0.02, dc, Continuous, Annual));

    const double v0    = 0.04;
    const double kappa = 1.0;
    const double theta = 0.04;
    const double sigma = 0.3;
    const double rho   = 0.3;

    const auto hestonProcess = ext::make_shared<HestonProcess>(
        rTS, qTS, s0, v0, kappa, theta, sigma, rho);
    const auto hestonModel = ext::make_shared<HestonModel>(hestonProcess);

    // NPV cases
    addPriceCase(out, "call_atm_1y",  hestonModel, 100.0, 1.0, Option::Call, eval);
    addPriceCase(out, "put_atm_1y",   hestonModel, 100.0, 1.0, Option::Put,  eval);
    addPriceCase(out, "call_otm_1y",  hestonModel, 110.0, 1.0, Option::Call, eval);
    addPriceCase(out, "put_otm_1y",   hestonModel,  90.0, 1.0, Option::Put,  eval);
    addPriceCase(out, "call_atm_2y",  hestonModel, 100.0, 2.0, Option::Call, eval);
    addPriceCase(out, "put_atm_2y",   hestonModel, 100.0, 2.0, Option::Put,  eval);
    addPriceCase(out, "call_atm_05y", hestonModel, 100.0, 0.5, Option::Call, eval);

    // Cumulant fingerprints
    addCumulantCase(out, "cumulants_t_1",   hestonModel, 1.0);
    addCumulantCase(out, "cumulants_t_2",   hestonModel, 2.0);
    addCumulantCase(out, "cumulants_t_05",  hestonModel, 0.5);
    addCumulantCase(out, "cumulants_t_025", hestonModel, 0.25);

    out.write();
    return 0;
}
