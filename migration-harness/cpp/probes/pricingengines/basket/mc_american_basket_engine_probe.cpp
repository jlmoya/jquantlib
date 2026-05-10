// migration-harness/cpp/probes/pricingengines/basket/mc_american_basket_engine_probe.cpp
//
// Phase 4i.5b — emit reference values for MCAmericanBasketEngine (Java port:
// org.jquantlib.pricingengines.basket.MCAmericanBasketEngine).
//
// Two case families:
//   1. testGlassermanMaxOption2D — 2-asset American max-of-N call,
//      Glasserman 2004 p.462: S0 in {90,100,110}, K=100, r=0.05, q=0.10,
//      T=3.0 (Actual/365 from 17-May-1998 to 16-May-2001), sigma=0.20,
//      uncorrelated. Expected: {8.08, 13.90, 21.34}.
//   2. testTavellaValues3D — 3-asset American max-call (Tavella 2002):
//      S=100x3, K=100, r=0.05, q=0.10, T=3.0 (Actual/360 from
//      Date::todaysDate() + 1080 days), sigma=0.20, rho_{12}=-0.25,
//      rho_{13}=0.25, rho_{23}=0.30. Expected: 18.082.
//
// Both cases use Mersenne-Twister + InverseCumulativeNormal, antithetic
// variate, seed=42, 4096 pricing samples, 1024 calibration samples for
// Glasserman; 10000/2500 for Tavella (matches C++ test-suite settings).

#include <ql/version.hpp>
#include <ql/exercise.hpp>
#include <ql/instruments/basketoption.hpp>
#include <ql/instruments/payoffs.hpp>
#include <ql/math/matrix.hpp>
#include <ql/methods/montecarlo/lsmbasissystem.hpp>
#include <ql/pricingengines/basket/mcamericanbasketengine.hpp>
#include <ql/processes/blackscholesprocess.hpp>
#include <ql/processes/stochasticprocessarray.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/termstructures/volatility/equityfx/blackconstantvol.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/time/calendars/nullcalendar.hpp>
#include <ql/time/daycounters/actual360.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>

#include "../../common.hpp"

#include <vector>

using namespace QuantLib;
using namespace jqml_harness;

namespace {

// Convert C++ Time-in-years to days for Actual/360 (used in Tavella case).
Integer timeToDays(Time t) {
    return Integer(t * 360 + 0.5);
}

void emitGlasserman2D(ReferenceWriter& out) {
    const Date today(15, May, 1998);
    Settings::instance().evaluationDate() = today;
    const Date settlement(17, May, 1998);

    const DayCounter dc = Actual365Fixed();
    const Calendar cal = NullCalendar();
    const Date maturity(16, May, 2001);

    auto exercise = ext::make_shared<AmericanExercise>(settlement, maturity);

    const Real strike = 100.0;
    const Real r = 0.05;
    const Real q = 0.10;
    const Real vol = 0.20;

    Handle<YieldTermStructure> rTS(
        ext::make_shared<FlatForward>(settlement, r, dc));
    Handle<YieldTermStructure> qTS(
        ext::make_shared<FlatForward>(settlement, q, dc));
    Handle<BlackVolTermStructure> volTS(
        ext::make_shared<BlackConstantVol>(settlement, cal, vol, dc));

    auto payoff = ext::make_shared<PlainVanillaPayoff>(Option::Call, strike);
    auto basket = ext::make_shared<MaxBasketPayoff>(payoff);

    const Real S0_arr[] = { 90.0, 100.0, 110.0 };

    for (Real S0 : S0_arr) {
        auto spot = ext::make_shared<SimpleQuote>(S0);
        Handle<Quote> spotH(spot);
        auto process = ext::make_shared<GeneralizedBlackScholesProcess>(
            spotH, qTS, rTS, volTS);

        std::vector<ext::shared_ptr<StochasticProcess1D>> procs = {process, process};
        Matrix corr(2, 2, 0.0);
        corr[0][0] = corr[1][1] = 1.0;

        auto procArray = ext::make_shared<StochasticProcessArray>(procs, corr);

        ext::shared_ptr<PricingEngine> engine =
            MakeMCAmericanBasketEngine<>(procArray)
            .withSteps(25)
            .withAntitheticVariate()
            .withSamples(4096)
            .withCalibrationSamples(1024)
            .withSeed(42);

        BasketOption opt(basket, exercise);
        opt.setPricingEngine(engine);
        Real npv = opt.NPV();
        Real err = opt.errorEstimate();

        json inputs = json::object();
        inputs["S0"] = S0;
        inputs["K"] = strike;
        inputs["r"] = r;
        inputs["q"] = q;
        inputs["vol"] = vol;
        inputs["T_years"] = dc.yearFraction(settlement, maturity);
        inputs["seed"] = 42;
        inputs["timeSteps"] = 25;
        inputs["samples"] = 4096;
        inputs["calibrationSamples"] = 1024;
        inputs["antithetic"] = true;
        inputs["polynomialOrder"] = 2;
        inputs["polynomialType"] = "Monomial";
        inputs["numAssets"] = 2;
        inputs["correlation"] = "identity";

        json expected = json::object();
        expected["npv"] = npv;
        expected["errorEstimate"] = err;

        std::string name = "glasserman_2d_S0_" + std::to_string(int(S0));
        out.addCase(name, inputs, expected);
    }
}

void emitTavella3D(ReferenceWriter& out) {
    const Date today = Date::todaysDate();
    Settings::instance().evaluationDate() = today;
    const DayCounter dc = Actual360();
    const Calendar cal = NullCalendar();

    const Real strike = 100.0;
    const Real r = 0.05;
    const Real q = 0.10;
    const Real vol = 0.20;
    const Real T = 3.0;
    const Date maturity = today + timeToDays(T);

    auto exercise = ext::make_shared<AmericanExercise>(today, maturity);

    Handle<YieldTermStructure> rTS(
        ext::make_shared<FlatForward>(today, r, dc));
    Handle<YieldTermStructure> qTS(
        ext::make_shared<FlatForward>(today, q, dc));
    Handle<BlackVolTermStructure> volTS(
        ext::make_shared<BlackConstantVol>(today, cal, vol, dc));

    auto spot = ext::make_shared<SimpleQuote>(100.0);
    Handle<Quote> spotH(spot);
    auto process = ext::make_shared<GeneralizedBlackScholesProcess>(
        spotH, qTS, rTS, volTS);

    std::vector<ext::shared_ptr<StochasticProcess1D>> procs = {process, process, process};
    Matrix corr(3, 3, 0.0);
    for (int i = 0; i < 3; ++i) corr[i][i] = 1.0;
    corr[1][0] = corr[0][1] = -0.25;
    corr[2][0] = corr[0][2] =  0.25;
    corr[2][1] = corr[1][2] =  0.30;

    auto procArray = ext::make_shared<StochasticProcessArray>(procs, corr);

    auto payoff = ext::make_shared<PlainVanillaPayoff>(Option::Call, strike);
    auto basket = ext::make_shared<MaxBasketPayoff>(payoff);

    ext::shared_ptr<PricingEngine> engine =
        MakeMCAmericanBasketEngine<>(procArray)
        .withSteps(20)
        .withAntitheticVariate()
        .withSamples(10000)
        .withCalibrationSamples(2500)
        .withSeed(42);

    BasketOption opt(basket, exercise);
    opt.setPricingEngine(engine);
    Real npv = opt.NPV();
    Real err = opt.errorEstimate();

    json inputs = json::object();
    inputs["S1"] = 100.0;
    inputs["S2"] = 100.0;
    inputs["S3"] = 100.0;
    inputs["K"] = strike;
    inputs["r"] = r;
    inputs["q"] = q;
    inputs["vol"] = vol;
    inputs["T_years"] = T;
    inputs["seed"] = 42;
    inputs["timeSteps"] = 20;
    inputs["samples"] = 10000;
    inputs["calibrationSamples"] = 2500;
    inputs["antithetic"] = true;
    inputs["rho_12"] = -0.25;
    inputs["rho_13"] = 0.25;
    inputs["rho_23"] = 0.30;
    inputs["polynomialOrder"] = 2;
    inputs["polynomialType"] = "Monomial";
    inputs["numAssets"] = 3;

    json expected = json::object();
    expected["npv"] = npv;
    expected["errorEstimate"] = err;
    expected["tavellaReference"] = 18.082;

    out.addCase("tavella_3d_max_call", inputs, expected);
}

} // namespace

int main() {
    ReferenceWriter out("pricingengines/basket/mc_american_basket_engine",
                        QL_VERSION,
                        "mc_american_basket_engine_probe");

    emitGlasserman2D(out);
    emitTavella3D(out);

    out.write();
    return 0;
}
