// migration-harness/cpp/probes/experimental/variancegamma/variance_gamma_singularity_probe.cpp
// Reference value for VarianceGammaEngine on the testSingularityAtZero
// scenario (v1.42.1 test-suite/variancegamma.cpp).
//
// The C++ test only asserts non-hang; we emit the actual NPV so that the
// Java equivalent can both verify finite-time termination AND match the
// reference price.

#include <cstdio>
#include <ql/version.hpp>
#include "../../common.hpp"

#include <ql/experimental/variancegamma/analyticvariancegammaengine.hpp>
#include <ql/experimental/variancegamma/variancegammaprocess.hpp>
#include <ql/instruments/vanillaoption.hpp>
#include <ql/instruments/payoffs.hpp>
#include <ql/exercise.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/time/daycounters/thirty360.hpp>

using namespace jqml_harness;
using namespace QuantLib;

int main() {
    ReferenceWriter out("experimental/variancegamma/variance_gamma_singularity",
                        QL_VERSION,
                        "variance_gamma_singularity_probe");

    Real stock = 100;
    Real strike = 98;
    Volatility sigma = 0.12;
    Real mu = -0.14;
    Real kappa = 0.2;

    Date valuation(1, Jan, 2017);
    Date maturity(10, Jan, 2017);
    DayCounter dc = Thirty360(Thirty360::BondBasis);

    Settings::instance().evaluationDate() = valuation;

    ext::shared_ptr<Exercise> exercise(new EuropeanExercise(maturity));
    ext::shared_ptr<StrikedTypePayoff> payoff(new PlainVanillaPayoff(Option::Call, strike));
    VanillaOption option(payoff, exercise);

    Handle<YieldTermStructure> div(ext::shared_ptr<YieldTermStructure>(
        new FlatForward(valuation, 0.0, dc)));
    Handle<YieldTermStructure> disc(ext::shared_ptr<YieldTermStructure>(
        new FlatForward(valuation, 0.05, dc)));
    Handle<Quote> S0(ext::shared_ptr<Quote>(new SimpleQuote(stock)));

    ext::shared_ptr<VarianceGammaProcess> process(
        new VarianceGammaProcess(S0, div, disc, sigma, kappa, mu));

    option.setPricingEngine(ext::shared_ptr<PricingEngine>(
        new VarianceGammaEngine(process)));

    Real npv = option.NPV();

    json inp = {
        {"spot", stock},
        {"strike", strike},
        {"sigma", sigma},
        {"mu", mu},
        {"kappa", kappa},
        {"valuation", "2017-01-01"},
        {"maturity", "2017-01-10"},
        {"r", 0.05},
        {"q", 0.0}
    };
    json expected = { {"npv", npv} };
    out.addCase("singularity_call_98", inp, expected);

    out.write();
    return 0;
}
