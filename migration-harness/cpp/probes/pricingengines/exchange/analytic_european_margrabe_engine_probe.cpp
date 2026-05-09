// migration-harness/cpp/probes/pricingengines/exchange/analytic_european_margrabe_engine_probe.cpp
//
// Probe for Phase 5i.5-MGR: AnalyticEuropeanMargrabeEngine NPV + Greeks.
//
// Exercises Margrabe's 1978 closed-form across (Q1, Q2, vol1, vol2, rho, T).
// Uses GBS with FlatForward risk-free=5%, dividend yields q1, q2, BlackConstantVol vol.
// Spot1=100, Spot2=100 base; eval=2026-01-15.

#include <ql/version.hpp>

#include <ql/exercise.hpp>
#include <ql/instruments/margrabeoption.hpp>
#include <ql/instruments/payoffs.hpp>
#include <ql/pricingengines/exotic/analyticeuropeanmargrabeengine.hpp>
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
makeGBS(const Date& eval, double S, double r, double q, double vol, const DayCounter& dc) {
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
             double S1, double S2,
             double q1, double q2,
             double vol1, double vol2,
             double rho, double T,
             int Q1, int Q2,
             const Date& eval, const DayCounter& dc) {

    const double r = 0.05;
    const auto p1 = makeGBS(eval, S1, r, q1, vol1, dc);
    const auto p2 = makeGBS(eval, S2, r, q2, vol2, dc);

    const Date exerciseDate = eval + int(T * 365 + 0.5);
    const auto exercise = ext::make_shared<EuropeanExercise>(exerciseDate);

    MargrabeOption option(Q1, Q2, exercise);
    option.setPricingEngine(
        ext::make_shared<AnalyticEuropeanMargrabeEngine>(p1, p2, rho));

    const double npv     = option.NPV();
    const double delta1  = option.delta1();
    const double delta2  = option.delta2();
    const double gamma1  = option.gamma1();
    const double gamma2  = option.gamma2();
    const double theta   = option.theta();

    json inputs = {
        {"S1", S1}, {"S2", S2},
        {"q1", q1}, {"q2", q2},
        {"vol1", vol1}, {"vol2", vol2},
        {"rho", rho}, {"T", T},
        {"Q1", Q1}, {"Q2", Q2}
    };
    json expected = {
        {"npv", npv},
        {"delta1", delta1}, {"delta2", delta2},
        {"gamma1", gamma1}, {"gamma2", gamma2},
        {"theta", theta}
    };
    out.addCase(name, inputs, expected);
}

} // anonymous namespace

int main() {
    ReferenceWriter out("pricingengines/exchange/analytic_european_margrabe_engine",
                        QL_VERSION,
                        "analytic_european_margrabe_engine_probe");

    const Date eval(15, January, 2026);
    Settings::instance().evaluationDate() = eval;
    const DayCounter dc = Actual365Fixed();

    // Symmetric base: S1=S2=100, q1=q2=0.02, vol1=vol2=0.20, rho=0.5
    addCase(out, "sym_atm_v20_rho50_T1",  100, 100, 0.02, 0.02, 0.20, 0.20, 0.5, 1.0, 1, 1, eval, dc);
    addCase(out, "sym_atm_v20_rho00_T1",  100, 100, 0.02, 0.02, 0.20, 0.20, 0.0, 1.0, 1, 1, eval, dc);
    addCase(out, "sym_atm_v20_rhom50_T1", 100, 100, 0.02, 0.02, 0.20, 0.20,-0.5, 1.0, 1, 1, eval, dc);
    addCase(out, "sym_atm_v30_rho50_T1",  100, 100, 0.02, 0.02, 0.30, 0.30, 0.5, 1.0, 1, 1, eval, dc);
    addCase(out, "sym_atm_v20_rho50_T2",  100, 100, 0.02, 0.02, 0.20, 0.20, 0.5, 2.0, 1, 1, eval, dc);
    addCase(out, "sym_atm_v20_rho50_Thalf",100,100, 0.02, 0.02, 0.20, 0.20, 0.5, 0.5, 1, 1, eval, dc);

    // Asymmetric S
    addCase(out, "asym_S110_S90_v20_rho50_T1", 110, 90, 0.02, 0.02, 0.20, 0.20, 0.5, 1.0, 1, 1, eval, dc);
    addCase(out, "asym_S90_S110_v20_rho50_T1", 90,110, 0.02, 0.02, 0.20, 0.20, 0.5, 1.0, 1, 1, eval, dc);

    // Asymmetric vol
    addCase(out, "vol_v30_v10_rho50_T1", 100, 100, 0.02, 0.02, 0.30, 0.10, 0.5, 1.0, 1, 1, eval, dc);

    // Different quantities
    addCase(out, "Q2Q1_v20_rho50_T1", 100, 100, 0.02, 0.02, 0.20, 0.20, 0.5, 1.0, 2, 1, eval, dc);
    addCase(out, "Q1Q2_v20_rho50_T1", 100, 100, 0.02, 0.02, 0.20, 0.20, 0.5, 1.0, 1, 2, eval, dc);

    // Asymmetric dividend
    addCase(out, "div_q5_q1_v20_rho50_T1", 100, 100, 0.05, 0.01, 0.20, 0.20, 0.5, 1.0, 1, 1, eval, dc);

    out.write();
    return 0;
}
