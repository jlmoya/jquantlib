// migration-harness/cpp/probes/models/equity/gjrgarch_engine_probe.cpp
//
// Probe for Phase 5e.5b-CFC-d — AnalyticGJRGARCHEngine + GjrGarchModel +
// GjrGarchProcess (Duan-Gauthier-Simonato-Sasseville 2006 Edgeworth-expansion
// closed-form approximation for the GJR-GARCH(1,1) stochastic-volatility
// model). Mirrors the C++ test-suite garch.cpp testEngines parameter grid
// (omega=2e-6, alpha=0.024, beta=0.93, gamma=0.059, daysPerYear=365.0,
// strikes {35..60}, maturities {90, 180}, lambdas {0.0, 0.1, 0.2}).
//
// Cross-validates NPV against the Java port. Tolerance tier: LOOSE (1e-3)
// — the analytic engine is itself a 4-moment Edgeworth approximation
// (sigma, k3, k4 truncation) and the reference values in garch.cpp
// quote 4 significant digits anchored to the same approximation.

#include <ql/version.hpp>

#include <ql/exercise.hpp>
#include <ql/instruments/vanillaoption.hpp>
#include <ql/instruments/payoffs.hpp>
#include <ql/math/distributions/normaldistribution.hpp>
#include <ql/models/equity/gjrgarchmodel.hpp>
#include <ql/pricingengines/vanilla/analyticgjrgarchengine.hpp>
#include <ql/processes/gjrgarchprocess.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/settings.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/time/calendars/nullcalendar.hpp>
#include <ql/time/daycounters/actualactual.hpp>
#include <cmath>

#include "../../common.hpp"

using namespace QuantLib;
using namespace jqml_harness;

namespace {

void addCase(ReferenceWriter& out,
             const std::string& name,
             const Date& eval,
             const Handle<YieldTermStructure>& rTS,
             const Handle<YieldTermStructure>& qTS,
             double s0,
             double omega,
             double alpha,
             double beta,
             double gamma,
             double lambda,
             double daysPerYear,
             double strike,
             int maturityDays,
             Option::Type type) {
    // Long-run unconditional variance v0 = omega / (1 - m1).
    // m1 = beta + (alpha + gamma * N(lambda)) * (1 + lambda^2)
    //      + gamma * lambda * phi(lambda)
    // where phi is the standard normal density.
    const double Nlam = CumulativeNormalDistribution()(lambda);
    const double nlam = std::exp(-lambda * lambda / 2.0)
            / std::sqrt(2.0 * M_PI);
    const double m1 = beta + (alpha + gamma * Nlam) * (1.0 + lambda * lambda)
            + gamma * lambda * nlam;
    const double v0 = omega / (1.0 - m1);

    Handle<Quote> spot(ext::make_shared<SimpleQuote>(s0));
    auto process = ext::make_shared<GJRGARCHProcess>(
            rTS, qTS, spot, v0, omega, alpha, beta, gamma, lambda, daysPerYear);
    auto model = ext::make_shared<GJRGARCHModel>(process);
    auto engine = ext::make_shared<AnalyticGJRGARCHEngine>(model);

    const Date exDate = eval + maturityDays;
    auto exercise = ext::make_shared<EuropeanExercise>(exDate);
    auto payoff   = ext::make_shared<PlainVanillaPayoff>(type, strike);
    VanillaOption option(payoff, exercise);
    option.setPricingEngine(engine);
    const double npv = option.NPV();

    const std::string typeStr = (type == Option::Call) ? "Call" : "Put";
    json inputs = {
        {"s0",            s0},
        {"omega",         omega},
        {"alpha",         alpha},
        {"beta",          beta},
        {"gamma",         gamma},
        {"lambda",        lambda},
        {"daysPerYear",   daysPerYear},
        {"v0_derived",    v0},
        {"strike",        strike},
        {"maturity_days", maturityDays},
        {"option_type",   typeStr}
    };
    json expected = { {"npv", npv} };
    out.addCase(name, inputs, expected);
}

} // anonymous namespace

int main() {
    ReferenceWriter out("models/equity/gjrgarch_engine",
                        QL_VERSION,
                        "gjrgarch_engine_probe");

    // Mirrors C++ test-suite garch.cpp testEngines fixture exactly.
    const DayCounter dayCounter = ActualActual(ActualActual::ISDA);
    const Date eval = Date(15, January, 2026);
    Settings::instance().evaluationDate() = eval;

    Handle<YieldTermStructure> rTS(
            ext::make_shared<FlatForward>(eval, 0.05, dayCounter));
    Handle<YieldTermStructure> qTS(
            ext::make_shared<FlatForward>(eval, 0.0,  dayCounter));

    const double s0 = 50.0;
    const double omega = 2.0e-6;
    const double alpha = 0.024;
    const double beta  = 0.93;
    const double gamma = 0.059;
    const double daysPerYear = 365.0;

    const int maturity[2] = {90, 180};
    const double strike[6] = {35.0, 40.0, 45.0, 50.0, 55.0, 60.0};
    const double lambdas[3] = {0.0, 0.1, 0.2};

    for (int k = 0; k < 3; ++k) {
        for (int i = 0; i < 2; ++i) {
            for (int j = 0; j < 6; ++j) {
                std::ostringstream nm;
                nm << "call_l" << k << "_m" << maturity[i]
                   << "_k" << static_cast<int>(strike[j]);
                addCase(out, nm.str(), eval, rTS, qTS, s0,
                        omega, alpha, beta, gamma, lambdas[k], daysPerYear,
                        strike[j], maturity[i], Option::Call);
            }
        }
    }

    // A handful of put cases at lambda=0.1 to exercise the put-call branch.
    for (int j = 0; j < 6; ++j) {
        std::ostringstream nm;
        nm << "put_l1_m90_k" << static_cast<int>(strike[j]);
        addCase(out, nm.str(), eval, rTS, qTS, s0,
                omega, alpha, beta, gamma, 0.1, daysPerYear,
                strike[j], 90, Option::Put);
    }

    out.write();
    return 0;
}
