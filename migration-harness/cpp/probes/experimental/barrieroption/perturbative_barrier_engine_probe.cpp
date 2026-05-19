// migration-harness/cpp/probes/experimental/barrieroption/perturbative_barrier_engine_probe.cpp
//
// Phase 5e.5b-CFC-d-312 — capture C++ PerturbativeBarrierOptionEngine
// reference values for the testPerturbative fixture in test-suite/barrieroption.cpp.
//
// Test fixture (from v1.42.1 BOOST_AUTO_TEST_CASE(testPerturbative)):
//   S=100, q=0.02, r=0.03, today=Date::todaysDate(), Actual360
//   BlackVarianceCurve at today+90 vol=0.105 and today+180 vol=0.11
//   payoff=PlainVanillaPayoff(Put, strike=101), barrier=101, UpOut, rebate=0
//   exDate=today+180
//   Order 0 expected: 0.897365
//   Order 1 expected: 0.894374
//   Tolerance: 1e-6
//
// Note: today=Date::todaysDate() means the test is calendar-dependent in C++.
// We pin today to a fixed date to make values reproducible across runs and
// platforms; the engine reads only times-from-reference (Actual360 day
// fractions), so the exact today is irrelevant when both engine and probe
// share the same evaluationDate.

#include <cstdio>
#include <ql/version.hpp>
#include "../../common.hpp"

#include <ql/exercise.hpp>
#include <ql/experimental/barrieroption/perturbativebarrieroptionengine.hpp>
#include <ql/instruments/barrieroption.hpp>
#include <ql/instruments/payoffs.hpp>
#include <ql/processes/blackscholesprocess.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/settings.hpp>
#include <ql/termstructures/volatility/equityfx/blackvariancecurve.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/time/calendars/nullcalendar.hpp>
#include <ql/time/daycounters/actual360.hpp>

using namespace jqml_harness;
using namespace QuantLib;

namespace {

double priceOrder(unsigned order, bool zeroGamma, const Date& evalDate) {
    Settings::instance().evaluationDate() = evalDate;

    DayCounter dc = Actual360();
    Real S = 100.0;
    Real q = 0.02;
    Real r = 0.03;

    auto underlying = ext::make_shared<SimpleQuote>(S);
    auto qTS = ext::make_shared<FlatForward>(evalDate, q, dc);
    auto rTS = ext::make_shared<FlatForward>(evalDate, r, dc);

    std::vector<Date> dates;
    std::vector<Volatility> vols;
    dates.push_back(evalDate + 90);  vols.push_back(0.105);
    dates.push_back(evalDate + 180); vols.push_back(0.11);

    auto volTS = ext::make_shared<BlackVarianceCurve>(evalDate, dates, vols, dc);

    auto process = ext::make_shared<BlackScholesMertonProcess>(
        Handle<Quote>(underlying),
        Handle<YieldTermStructure>(qTS),
        Handle<YieldTermStructure>(rTS),
        Handle<BlackVolTermStructure>(volTS));

    Real strike = 101.0;
    Real barrier = 101.0;
    Real rebate = 0.0;
    Date exDate = evalDate + 180;

    auto exercise = ext::make_shared<EuropeanExercise>(exDate);
    auto payoff = ext::make_shared<PlainVanillaPayoff>(Option::Put, strike);

    BarrierOption option(Barrier::UpOut, barrier, rebate, payoff, exercise);
    auto engine = ext::make_shared<PerturbativeBarrierOptionEngine>(
        process, order, zeroGamma);
    option.setPricingEngine(engine);
    return option.NPV();
}

} // namespace

int main() {
    ReferenceWriter out("experimental/perturbative_barrier_engine",
                        QL_VERSION,
                        "perturbative_barrier_engine_probe");

    // Pin today so the reference is reproducible.
    Date today(15, September, 2024);

    // Order 0: zeroth-order term P_0 only (Black-Scholes-like)
    double p0 = priceOrder(0, false, today);
    out.addCase("up_out_put_strike101_barrier101_order0",
                {{"S", 100.0}, {"q", 0.02}, {"r", 0.03},
                 {"strike", 101.0}, {"barrier", 101.0}, {"rebate", 0.0},
                 {"vol_90", 0.105}, {"vol_180", 0.11},
                 {"order", 0}, {"zeroGamma", false},
                 {"barrierType", "UpOut"}, {"optionType", "Put"},
                 {"daysToExercise", 180},
                 {"today", "2024-09-15"}},
                p0);

    // Order 1: P_0 + P_1
    double p1 = priceOrder(1, false, today);
    out.addCase("up_out_put_strike101_barrier101_order1",
                {{"S", 100.0}, {"q", 0.02}, {"r", 0.03},
                 {"strike", 101.0}, {"barrier", 101.0}, {"rebate", 0.0},
                 {"vol_90", 0.105}, {"vol_180", 0.11},
                 {"order", 1}, {"zeroGamma", false},
                 {"barrierType", "UpOut"}, {"optionType", "Put"},
                 {"daysToExercise", 180},
                 {"today", "2024-09-15"}},
                p1);

    std::printf("Order 0 NPV = %.10f (C++ test expects 0.897365)\n", p0);
    std::printf("Order 1 NPV = %.10f (C++ test expects 0.894374)\n", p1);

    out.write();
    return 0;
}
