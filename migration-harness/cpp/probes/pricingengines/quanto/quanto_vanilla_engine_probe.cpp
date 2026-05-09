// migration-harness/cpp/probes/pricingengines/quanto/quanto_vanilla_engine_probe.cpp
//
// Probe for Phase 5i.5-MGR: QuantoEngine<VanillaOption, AnalyticEuropeanEngine>
// NPV + quanto Greeks (qvega, qrho, qlambda) and standard Greeks.

#include <ql/version.hpp>

#include <ql/exercise.hpp>
#include <ql/instruments/quantovanillaoption.hpp>
#include <ql/instruments/payoffs.hpp>
#include <ql/instruments/vanillaoption.hpp>
#include <ql/pricingengines/quanto/quantoengine.hpp>
#include <ql/pricingengines/vanilla/analyticeuropeanengine.hpp>
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
             double S, double r, double q, double vol, double strike, double T,
             Option::Type type,
             double rf, double exVol, double rho,
             const Date& eval, const DayCounter& dc) {

    const auto process = makeGBS(eval, S, r, q, vol, dc);

    const Handle<YieldTermStructure> foreignR(
        ext::make_shared<FlatForward>(eval, rf, dc, Continuous, Annual));
    const Handle<BlackVolTermStructure> exchangeVol(
        ext::make_shared<BlackConstantVol>(eval, NullCalendar(), exVol, dc));
    const Handle<Quote> corrQ(ext::make_shared<SimpleQuote>(rho));

    const Date exerciseDate = eval + int(T * 365 + 0.5);
    const auto exercise = ext::make_shared<EuropeanExercise>(exerciseDate);

    const auto payoff = ext::make_shared<PlainVanillaPayoff>(type, strike);
    QuantoVanillaOption option(payoff, exercise);
    option.setPricingEngine(
        ext::make_shared<QuantoEngine<VanillaOption, AnalyticEuropeanEngine> >(
            process, foreignR, exchangeVol, corrQ));

    json inputs = {
        {"S", S}, {"r", r}, {"q", q}, {"vol", vol},
        {"strike", strike}, {"T", T},
        {"option_type", type == Option::Call ? "Call" : "Put"},
        {"foreign_r", rf}, {"exchange_vol", exVol}, {"correlation", rho}
    };

    json expected = {
        {"npv", option.NPV()},
        {"delta", option.delta()},
        {"gamma", option.gamma()},
        {"theta", option.theta()},
        {"rho", option.rho()},
        {"dividendRho", option.dividendRho()},
        {"vega", option.vega()},
        {"qvega", option.qvega()},
        {"qrho", option.qrho()},
        {"qlambda", option.qlambda()}
    };
    out.addCase(name, inputs, expected);
}

} // anonymous namespace

int main() {
    ReferenceWriter out("pricingengines/quanto/quanto_vanilla_engine",
                        QL_VERSION,
                        "quanto_vanilla_engine_probe");

    const Date eval(15, January, 2026);
    Settings::instance().evaluationDate() = eval;
    const DayCounter dc = Actual365Fixed();

    // Base: S=100, r=5%, q=2%, vol=20%, foreign-r=4%, exchange-vol=10%, rho=0.3
    addCase(out, "atm_call_v20_rho30_T1",
            100, 0.05, 0.02, 0.20, 100, 1.0, Option::Call, 0.04, 0.10, 0.3, eval, dc);
    addCase(out, "atm_put_v20_rho30_T1",
            100, 0.05, 0.02, 0.20, 100, 1.0, Option::Put,  0.04, 0.10, 0.3, eval, dc);
    addCase(out, "otm_call_v20_rho30_T1",
            100, 0.05, 0.02, 0.20, 110, 1.0, Option::Call, 0.04, 0.10, 0.3, eval, dc);
    addCase(out, "itm_call_v20_rho30_T1",
            100, 0.05, 0.02, 0.20, 90,  1.0, Option::Call, 0.04, 0.10, 0.3, eval, dc);
    addCase(out, "atm_call_v30_rho30_T1",
            100, 0.05, 0.02, 0.30, 100, 1.0, Option::Call, 0.04, 0.10, 0.3, eval, dc);
    addCase(out, "atm_call_v20_rhom30_T1",
            100, 0.05, 0.02, 0.20, 100, 1.0, Option::Call, 0.04, 0.10,-0.3, eval, dc);
    addCase(out, "atm_call_v20_rho00_T1",
            100, 0.05, 0.02, 0.20, 100, 1.0, Option::Call, 0.04, 0.10, 0.0, eval, dc);
    addCase(out, "atm_call_v20_rho30_T2",
            100, 0.05, 0.02, 0.20, 100, 2.0, Option::Call, 0.04, 0.10, 0.3, eval, dc);
    addCase(out, "atm_call_v20_rho30_Thalf",
            100, 0.05, 0.02, 0.20, 100, 0.5, Option::Call, 0.04, 0.10, 0.3, eval, dc);
    // Different exchange-rate vol
    addCase(out, "atm_call_v20_eVol20_rho30_T1",
            100, 0.05, 0.02, 0.20, 100, 1.0, Option::Call, 0.04, 0.20, 0.3, eval, dc);
    addCase(out, "atm_call_v20_eVol05_rho30_T1",
            100, 0.05, 0.02, 0.20, 100, 1.0, Option::Call, 0.04, 0.05, 0.3, eval, dc);

    out.write();
    return 0;
}
