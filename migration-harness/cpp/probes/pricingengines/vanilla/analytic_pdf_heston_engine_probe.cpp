// migration-harness/cpp/probes/pricingengines/vanilla/analytic_pdf_heston_engine_probe.cpp
// Reference values for AnalyticPDFHestonEngine vs QuantLib C++ v1.42.1.

#include <ql/version.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/time/calendars/nullcalendar.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/processes/hestonprocess.hpp>
#include <ql/models/equity/hestonmodel.hpp>
#include <ql/instruments/payoffs.hpp>
#include <ql/exercise.hpp>
#include <ql/instruments/vanillaoption.hpp>
#include <ql/pricingengines/vanilla/analyticpdfhestonengine.hpp>
#include "../../common.hpp"

using namespace QuantLib;
using namespace jqml_harness;

int main() {
    ReferenceWriter out("pricingengines/vanilla/analytic_pdf_heston_engine",
                        QL_VERSION,
                        "analytic_pdf_heston_engine_probe");

    // Setup mirrors test-suite/hestonmodel.cpp testAnalyticPDFHestonEngine.
    const Date settlement(5, January, 2014);
    Settings::instance().evaluationDate() = settlement;

    const DayCounter dc = Actual365Fixed();
    auto rTS = Handle<YieldTermStructure>(
                    ext::make_shared<FlatForward>(settlement, 0.07, dc));
    auto qTS = Handle<YieldTermStructure>(
                    ext::make_shared<FlatForward>(settlement, 0.185, dc));

    auto spot = ext::make_shared<SimpleQuote>(100.0);

    auto process = ext::make_shared<HestonProcess>(
        rTS, qTS, Handle<Quote>(spot),
        0.1, 4.0, 0.05, 1.0, -0.5);

    auto model = ext::make_shared<HestonModel>(process);

    auto pdfEngine = ext::make_shared<AnalyticPDFHestonEngine>(model, 1.0e-6);

    const Date maturity(5, July, 2014);
    auto exercise = ext::make_shared<EuropeanExercise>(maturity);

    // 7 strikes from 40..160 step 20 (matches the C++ test loop).
    for (Real strike = 40.0; strike < 170.0; strike += 20.0) {
        auto payoff = ext::make_shared<PlainVanillaPayoff>(Option::Call, strike);
        VanillaOption opt(payoff, exercise);
        opt.setPricingEngine(pdfEngine);
        char name[40];
        std::snprintf(name, sizeof(name), "call_strike_%03d", int(strike));
        out.addCase(name,
            json{ {"strike", strike}, {"type", "call"},
                  {"maturityYears", dc.yearFraction(settlement, maturity)} },
            json{ {"npv", opt.NPV()} });
    }

    // pdf cdf scalar accessors at log-spot grid x = ln(100) + k*sqrt(0.05*0.5)
    const Time t = dc.yearFraction(settlement, maturity);
    for (int k = -2; k <= 2; ++k) {
        const Real stdDev = std::sqrt(0.05 * t);
        const Real x = std::log(100.0) + k * stdDev;
        char name[40];
        std::snprintf(name, sizeof(name), "Pv_k%+d", k);
        out.addCase(name,
            json{ {"x", x}, {"t", t} },
            json{ {"pdf", pdfEngine->Pv(x, t)} });
    }
    for (int k = -2; k <= 2; ++k) {
        const Real stdDev = std::sqrt(0.05 * t);
        const Real S = std::exp(std::log(100.0) + k * stdDev);
        char name[40];
        std::snprintf(name, sizeof(name), "cdf_k%+d", k);
        out.addCase(name,
            json{ {"S", S}, {"t", t} },
            json{ {"cdf", pdfEngine->cdf(S, t)} });
    }

    out.write();
    return 0;
}
