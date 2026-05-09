// migration-harness/cpp/probes/lookback/analytic_continuous_floating_lookback_probe.cpp
//
// Probe for Phase 5i.5: AnalyticContinuousFloatingLookbackEngine NPV.
//
// Tolerance tier: TIGHT (1e-9 abs).

#include <ql/version.hpp>

#include <ql/exercise.hpp>
#include <ql/instruments/lookbackoption.hpp>
#include <ql/instruments/payoffs.hpp>
#include <ql/pricingengines/lookback/analyticcontinuousfloatinglookback.hpp>
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
             double S, double r, double q, double vol,
             double minmax, double maturityYears,
             Option::Type type, const Date& eval, const DayCounter& dc) {

    auto process = makeGBS(eval, S, r, q, vol, dc);
    const Date exerciseDate = eval + int(maturityYears * 365 + 0.5);
    auto exercise = ext::make_shared<EuropeanExercise>(exerciseDate);
    auto payoff = ext::make_shared<FloatingTypePayoff>(type);

    ContinuousFloatingLookbackOption option(minmax, payoff, exercise);
    option.setPricingEngine(
        ext::make_shared<AnalyticContinuousFloatingLookbackEngine>(process));

    const double npv = option.NPV();

    const std::string typeStr = (type == Option::Call) ? "Call" : "Put";

    json inputs = {
        {"S", S}, {"r", r}, {"q", q}, {"vol", vol},
        {"minmax", minmax},
        {"maturity_years", maturityYears},
        {"option_type", typeStr}
    };
    json expected = { {"npv", npv} };

    out.addCase(name, inputs, expected);
}

} // anonymous namespace

int main() {
    ReferenceWriter out("lookback/analytic_continuous_floating_lookback",
                        QL_VERSION,
                        "analytic_continuous_floating_lookback_probe");

    const Date eval(15, January, 2026);
    Settings::instance().evaluationDate() = eval;
    const DayCounter dc = Actual365Fixed();

    // Hand-curated cases:
    //   Spot, r, q, vol, minmax, maturity, type
    // Floating-strike: call uses min, put uses max.
    addCase(out, "call_atm_min100_1y_v20",   100.0, 0.05, 0.02, 0.20, 100.0, 1.0, Option::Call, eval, dc);
    addCase(out, "call_min90_1y_v20",        100.0, 0.05, 0.02, 0.20,  90.0, 1.0, Option::Call, eval, dc);
    addCase(out, "call_min100_2y_v30",       100.0, 0.05, 0.02, 0.30, 100.0, 2.0, Option::Call, eval, dc);
    addCase(out, "call_min80_half_y_v25",    100.0, 0.04, 0.01, 0.25,  80.0, 0.5, Option::Call, eval, dc);
    addCase(out, "put_atm_max100_1y_v20",    100.0, 0.05, 0.02, 0.20, 100.0, 1.0, Option::Put,  eval, dc);
    addCase(out, "put_max110_1y_v20",        100.0, 0.05, 0.02, 0.20, 110.0, 1.0, Option::Put,  eval, dc);
    addCase(out, "put_max120_2y_v30",        100.0, 0.05, 0.02, 0.30, 120.0, 2.0, Option::Put,  eval, dc);
    addCase(out, "call_high_vol_min100_1y",  100.0, 0.06, 0.03, 0.40, 100.0, 1.0, Option::Call, eval, dc);
    addCase(out, "put_high_vol_max100_1y",   100.0, 0.06, 0.03, 0.40, 100.0, 1.0, Option::Put,  eval, dc);
    addCase(out, "call_negcarry_min100_1y",  100.0, 0.02, 0.05, 0.20, 100.0, 1.0, Option::Call, eval, dc);

    out.write();
    return 0;
}
