// migration-harness/cpp/probes/experimental/varianceoption/integral_heston_variance_option_engine_probe.cpp
// Reference values for IntegralHestonVarianceOptionEngine
// (ql/experimental/varianceoption/integralhestonvarianceoptionengine.{hpp,cpp}).
//
// Mirror of the smoke scenarios used in the Java port (Phase 4a A.2):
// fixed (kappa, theta, sigma, v0, rho), zero dividends, flat continuous risk-free
// rate, varying maturity and realised-variance strike, plain-vanilla call payoff.

#include <cstdio>
#include <ql/version.hpp>
#include "../../common.hpp"

#include <ql/experimental/varianceoption/varianceoption.hpp>
#include <ql/experimental/varianceoption/integralhestonvarianceoptionengine.hpp>
#include <ql/instruments/payoffs.hpp>
#include <ql/processes/hestonprocess.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/time/calendars/nullcalendar.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>

using namespace jqml_harness;
using namespace QuantLib;

int main() {
    ReferenceWriter out("experimental/varianceoption/integral_heston_variance_option_engine",
                        QL_VERSION,
                        "integral_heston_variance_option_engine_probe");

    Date eval(15, June, 2026);
    Settings::instance().evaluationDate() = eval;

    DayCounter dc = Actual365Fixed();
    Calendar cal = NullCalendar();

    Handle<YieldTermStructure> rTS(ext::shared_ptr<YieldTermStructure>(
        new FlatForward(eval, 0.05, dc)));
    Handle<YieldTermStructure> qTS(ext::shared_ptr<YieldTermStructure>(
        new FlatForward(eval, 0.0, dc)));

    Real spot = 100.0;
    Handle<Quote> s0(ext::shared_ptr<Quote>(new SimpleQuote(spot)));

    // Heston params satisfying Feller (s = 2*chi*theta/eps^2 - 1 > 0)
    Real v0 = 0.04;
    Real kappa = 2.0;
    Real theta = 0.04;
    Real sigma = 0.2;
    Real rho = -0.5;

    ext::shared_ptr<HestonProcess> proc(
        new HestonProcess(rTS, qTS, s0, v0, kappa, theta, sigma, rho));

    ext::shared_ptr<PricingEngine> engine(
        new IntegralHestonVarianceOptionEngine(proc));

    struct Scenario {
        const char* name;
        Integer days;
        Real strike;
        Real notional;
    };
    std::vector<Scenario> scenarios = {
        {"call_3m_strike_0.04_n100k", 90,  0.04, 100000.0},
        {"call_6m_strike_0.04_n100k", 180, 0.04, 100000.0},
        {"call_1y_strike_0.04_n100k", 365, 0.04, 100000.0},
        {"call_1y_strike_0.05_n100k", 365, 0.05, 100000.0},
        {"call_1y_strike_0.03_n100k", 365, 0.03, 100000.0},
        {"call_1y_strike_0.04_n50k",  365, 0.04, 50000.0},
    };

    for (auto const& sc : scenarios) {
        Date maturity = eval + sc.days;
        ext::shared_ptr<Payoff> payoff(new PlainVanillaPayoff(Option::Call, sc.strike));
        VarianceOption opt(payoff, sc.notional, eval, maturity);
        opt.setPricingEngine(engine);
        Real npv = opt.NPV();

        json inp = {
            {"days_to_maturity", sc.days},
            {"strike", sc.strike},
            {"notional", sc.notional},
            {"v0", v0},
            {"kappa", kappa},
            {"theta", theta},
            {"sigma", sigma},
            {"rho", rho},
            {"r", 0.05},
            {"q", 0.0}
        };
        json expected = { {"npv", npv} };
        out.addCase(sc.name, inp, expected);
    }

    out.write();
    return 0;
}
