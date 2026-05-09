// migration-harness/cpp/probes/experimental/variancegamma/variance_gamma_engine_probe.cpp
// Reference values for VarianceGammaEngine (analytic) for European vanilla options
// (ql/experimental/variancegamma/analyticvariancegammaengine.{hpp,cpp}).
//
// Mirror of the v1.42.1 test-suite scenarios in test-suite/variancegamma.cpp
// (testVarianceGamma): 2 process configs x 22 European-option strikes/types,
// dc=Actual360, today = Date::todaysDate().

#include <cstdio>
#include <ql/version.hpp>
#include "../../common.hpp"

#include <ql/experimental/variancegamma/analyticvariancegammaengine.hpp>
#include <ql/experimental/variancegamma/variancegammaprocess.hpp>
#include <ql/instruments/europeanoption.hpp>
#include <ql/instruments/payoffs.hpp>
#include <ql/exercise.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/time/calendars/nullcalendar.hpp>
#include <ql/time/daycounters/actual360.hpp>

using namespace jqml_harness;
using namespace QuantLib;

namespace {
    Integer timeToDays(Time t) {
        return static_cast<Integer>(t * 360 + 0.5);
    }
}

int main() {
    ReferenceWriter out("experimental/variancegamma/variance_gamma_engine",
                        QL_VERSION,
                        "variance_gamma_engine_probe");

    struct Proc {
        Real s;
        Rate q;
        Rate r;
        Real sigma;
        Real nu;
        Real theta;
    };
    std::vector<Proc> processes = {
        { 6000, 0.00, 0.05, 0.20, 0.05, -0.50 },
        { 6000, 0.02, 0.05, 0.15, 0.01, -0.50 }
    };

    struct Opt {
        Option::Type type;
        Real strike;
        Time t;
    };
    std::vector<Opt> options;
    for (Real strike = 5550; strike <= 6550; strike += 50)
        options.push_back({Option::Call, strike, 1.0});
    options.push_back({Option::Put, 5550, 1.0});

    DayCounter dc = Actual360();
    Date today = Date::todaysDate();
    Settings::instance().evaluationDate() = today;

    for (size_t i = 0; i < processes.size(); ++i) {
        const auto& p = processes[i];

        ext::shared_ptr<SimpleQuote> spot(new SimpleQuote(p.s));
        ext::shared_ptr<YieldTermStructure> qTS(new FlatForward(today, p.q, dc));
        ext::shared_ptr<YieldTermStructure> rTS(new FlatForward(today, p.r, dc));

        ext::shared_ptr<VarianceGammaProcess> proc(
            new VarianceGammaProcess(Handle<Quote>(spot),
                                     Handle<YieldTermStructure>(qTS),
                                     Handle<YieldTermStructure>(rTS),
                                     p.sigma, p.nu, p.theta));

        ext::shared_ptr<PricingEngine> engine(new VarianceGammaEngine(proc));

        for (size_t j = 0; j < options.size(); ++j) {
            const auto& o = options[j];
            Date exDate = today + timeToDays(o.t);
            ext::shared_ptr<Exercise> exercise(new EuropeanExercise(exDate));
            ext::shared_ptr<StrikedTypePayoff> payoff(
                new PlainVanillaPayoff(o.type, o.strike));
            EuropeanOption option(payoff, exercise);
            option.setPricingEngine(engine);

            Real npv = option.NPV();

            char name[64];
            std::snprintf(name, sizeof(name), "p%zu_%s_strike_%d",
                          i,
                          (o.type == Option::Call ? "call" : "put"),
                          static_cast<int>(o.strike));

            json inp = {
                {"spot", p.s},
                {"q", p.q},
                {"r", p.r},
                {"sigma", p.sigma},
                {"nu", p.nu},
                {"theta", p.theta},
                {"type", (o.type == Option::Call ? "Call" : "Put")},
                {"strike", o.strike},
                {"t", o.t},
                {"days_to_maturity", timeToDays(o.t)}
            };
            json expected = { {"npv", npv} };
            out.addCase(name, inp, expected);
        }
    }

    out.write();
    return 0;
}
