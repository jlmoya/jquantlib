// migration-harness/cpp/probes/lookback/analytic_continuous_partial_fixed_lookback_probe.cpp
//
// Probe for Phase 5i.5: AnalyticContinuousPartialFixedLookbackEngine NPV.
//
// Tolerance tier: TIGHT (1e-9 abs).

#include <ql/version.hpp>

#include <ql/exercise.hpp>
#include <ql/instruments/lookbackoption.hpp>
#include <ql/instruments/payoffs.hpp>
#include <ql/pricingengines/lookback/analyticcontinuouspartialfixedlookback.hpp>
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
             double strike, double lookbackStartYears, double maturityYears,
             Option::Type type, const Date& eval, const DayCounter& dc) {

    auto process = makeGBS(eval, S, r, q, vol, dc);
    const Date exerciseDate = eval + int(maturityYears * 365 + 0.5);
    const Date lookbackStartDate = eval + int(lookbackStartYears * 365 + 0.5);
    auto exercise = ext::make_shared<EuropeanExercise>(exerciseDate);
    auto payoff = ext::make_shared<PlainVanillaPayoff>(type, strike);

    ContinuousPartialFixedLookbackOption option(
        lookbackStartDate, payoff, exercise);
    option.setPricingEngine(
        ext::make_shared<AnalyticContinuousPartialFixedLookbackEngine>(process));

    const double npv = option.NPV();

    const std::string typeStr = (type == Option::Call) ? "Call" : "Put";

    json inputs = {
        {"S", S}, {"r", r}, {"q", q}, {"vol", vol},
        {"strike", strike},
        {"lookback_start_years", lookbackStartYears},
        {"maturity_years", maturityYears},
        {"option_type", typeStr}
    };
    json expected = { {"npv", npv} };

    out.addCase(name, inputs, expected);
}

} // anonymous namespace

int main() {
    ReferenceWriter out("lookback/analytic_continuous_partial_fixed_lookback",
                        QL_VERSION,
                        "analytic_continuous_partial_fixed_lookback_probe");

    const Date eval(15, January, 2026);
    Settings::instance().evaluationDate() = eval;
    const DayCounter dc = Actual365Fixed();

    // Partial period (lookbackStart > 0) and lookback over full residual.
    addCase(out, "call_strike90_lstart0.3_T1_v20",  100.0, 0.05, 0.02, 0.20,  90.0, 0.3, 1.0, Option::Call, eval, dc);
    addCase(out, "call_strike100_lstart0.3_T1_v20", 100.0, 0.05, 0.02, 0.20, 100.0, 0.3, 1.0, Option::Call, eval, dc);
    addCase(out, "call_strike110_lstart0.5_T1_v25", 100.0, 0.04, 0.01, 0.25, 110.0, 0.5, 1.0, Option::Call, eval, dc);
    addCase(out, "call_strike90_lstart1.0_T2_v30",  100.0, 0.05, 0.02, 0.30,  90.0, 1.0, 2.0, Option::Call, eval, dc);
    addCase(out, "call_strike100_lstart_eq_T1_v20", 100.0, 0.05, 0.02, 0.20, 100.0, 1.0, 1.0, Option::Call, eval, dc);

    addCase(out, "put_strike110_lstart0.3_T1_v20",  100.0, 0.05, 0.02, 0.20, 110.0, 0.3, 1.0, Option::Put,  eval, dc);
    addCase(out, "put_strike100_lstart0.3_T1_v20",  100.0, 0.05, 0.02, 0.20, 100.0, 0.3, 1.0, Option::Put,  eval, dc);
    addCase(out, "put_strike90_lstart0.5_T1_v25",   100.0, 0.04, 0.01, 0.25,  90.0, 0.5, 1.0, Option::Put,  eval, dc);
    addCase(out, "put_strike110_lstart1.0_T2_v30",  100.0, 0.05, 0.02, 0.30, 110.0, 1.0, 2.0, Option::Put,  eval, dc);
    addCase(out, "put_strike100_lstart_eq_T1_v20",  100.0, 0.05, 0.02, 0.20, 100.0, 1.0, 1.0, Option::Put,  eval, dc);

    out.write();
    return 0;
}
