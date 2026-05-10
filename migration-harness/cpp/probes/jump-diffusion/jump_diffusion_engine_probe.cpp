// migration-harness/cpp/probes/jump-diffusion/jump_diffusion_engine_probe.cpp
//
// Phase 5h.5-Bates-c — emit C++ v1.42.1 reference NPVs for the
// JumpDiffusionEngine (Merton 1976 analytic) using the parameter
// sets from C++ test-suite/batesmodel.cpp::testAnalyticAndMcVsJumpDiffusion.
//
// Java JumpDiffusionEngine cross-validates against these references
// at the TIGHT tier (1e-9 abs); the Bates(λ→0) cross-check (Bates
// engine should reproduce Merton when stochastic-vol degenerates)
// is separately covered by the un-ignored
// BatesModelTest.testAnalyticAndMcVsJumpDiffusion in the testsuite.

#include <ql/version.hpp>
#include <ql/processes/merton76process.hpp>
#include <ql/instruments/europeanoption.hpp>
#include <ql/instruments/payoffs.hpp>
#include <ql/exercise.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/termstructures/volatility/equityfx/blackconstantvol.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/time/calendars/nullcalendar.hpp>
#include <ql/time/daycounters/actualactual.hpp>
#include <ql/pricingengines/vanilla/jumpdiffusionengine.hpp>
#include "../common.hpp"

using namespace QuantLib;
using namespace jqml_harness;

namespace {

const Date TODAY(22, April, 2026);

Handle<YieldTermStructure> flatCurve(Real rate, const DayCounter& dc) {
    return Handle<YieldTermStructure>(
        ext::make_shared<FlatForward>(TODAY, rate, dc));
}

Handle<BlackVolTermStructure> flatVolCurve(Real vol, const DayCounter& dc) {
    return Handle<BlackVolTermStructure>(
        ext::make_shared<BlackConstantVol>(TODAY, NullCalendar(), vol, dc));
}

struct MertonCase {
    std::string name;
    Option::Type type;
    Real strike;
    Real spot;
    Real r;
    Real q;
    Real vol;            // diffusive volatility
    Real lambda;         // jump intensity
    Real meanLogJump;    // mu (log-jump mean)
    Real jumpVol;        // delta (log-jump vol)
    int days;
    Real relativeAccuracy;
    int maxIterations;
};

void runCase(ReferenceWriter& out, const MertonCase& tc) {
    Settings::instance().evaluationDate() = TODAY;
    DayCounter dc = ActualActual(ActualActual::ISDA);

    Handle<YieldTermStructure> rTS = flatCurve(tc.r, dc);
    Handle<YieldTermStructure> qTS = flatCurve(tc.q, dc);
    Handle<Quote> spot(ext::make_shared<SimpleQuote>(tc.spot));
    Handle<BlackVolTermStructure> volTS = flatVolCurve(tc.vol, dc);

    ext::shared_ptr<SimpleQuote> jumpIntensity(new SimpleQuote(tc.lambda));
    ext::shared_ptr<SimpleQuote> meanLogJump(new SimpleQuote(tc.meanLogJump));
    ext::shared_ptr<SimpleQuote> jumpVol(new SimpleQuote(tc.jumpVol));

    ext::shared_ptr<Merton76Process> mertonProcess(new Merton76Process(
        spot, qTS, rTS, volTS,
        Handle<Quote>(jumpIntensity),
        Handle<Quote>(meanLogJump),
        Handle<Quote>(jumpVol)));

    ext::shared_ptr<PricingEngine> engine(new JumpDiffusionEngine(
        mertonProcess, tc.relativeAccuracy, tc.maxIterations));

    Date exerciseDate = TODAY + tc.days;
    ext::shared_ptr<StrikedTypePayoff> payoff(
        new PlainVanillaPayoff(tc.type, tc.strike));
    ext::shared_ptr<Exercise> exercise(new EuropeanExercise(exerciseDate));
    EuropeanOption option(payoff, exercise);
    option.setPricingEngine(engine);

    Real npv = option.NPV();

    json inputs = {
        {"type", tc.type == Option::Call ? "call" : "put"},
        {"strike", tc.strike},
        {"spot", tc.spot},
        {"r", tc.r},
        {"q", tc.q},
        {"vol", tc.vol},
        {"lambda", tc.lambda},
        {"meanLogJump", tc.meanLogJump},
        {"jumpVol", tc.jumpVol},
        {"days", tc.days},
        {"relativeAccuracy", tc.relativeAccuracy},
        {"maxIterations", tc.maxIterations}
    };
    json expected = {{"npv", npv}};
    out.addCase(tc.name, inputs, expected);
}

} // namespace

int main() {
    ReferenceWriter out("jump-diffusion/jump_diffusion_engine",
                        QL_VERSION,
                        "jump_diffusion_engine_probe");

    // ----------------------------------------------------------------
    // Block A — testAnalyticAndMcVsJumpDiffusion fixtures (Bates-c).
    // Mirrors C++ batesmodel.cpp::testAnalyticAndMcVsJumpDiffusion
    // parameters: Put @ K=95, S0=100, r=0.10, q=0.04, v0=0.0433
    // (vol=sqrt(v0)), lambda=2, meanLogJump=-0.2, jumpVol=0.2,
    // maturities 1y, 3y, 5y, JumpDiffusion accuracy=1e-10 / 1000 iters.
    // ----------------------------------------------------------------
    const Real V0    = 0.0433;
    const Real VOL   = std::sqrt(V0);
    const Real R     = 0.10;
    const Real Q     = 0.04;
    const Real S0    = 100.0;
    const Real K     = 95.0;
    const Real LAM   = 2.0;
    const Real MLJ   = -0.2;
    const Real JVOL  = 0.2;
    const Real ACC   = 1.0e-10;
    const int  MAXIT = 1000;

    runCase(out, {"merton_put_1y", Option::Put, K, S0, R, Q, VOL,
                  LAM, MLJ, JVOL, 365, ACC, MAXIT});
    runCase(out, {"merton_put_3y", Option::Put, K, S0, R, Q, VOL,
                  LAM, MLJ, JVOL, 3 * 365, ACC, MAXIT});
    runCase(out, {"merton_put_5y", Option::Put, K, S0, R, Q, VOL,
                  LAM, MLJ, JVOL, 5 * 365, ACC, MAXIT});

    // ----------------------------------------------------------------
    // Block B — Haug Merton-76 fixtures (subset of the 135 used by
    // JumpDiffusionEngineTest.testMerton76). Verifies that our analytic
    // engine matches both Haug 1998 published prices and our own port.
    // Per-case Haug parameters: jumpIntensity=lambda, gamma=fraction
    // of variance from jumps, vol=total stdev. Convert to (diffVol,
    // jumpVol, meanLogJump=ln(1+0)-0.5*jVol^2) per Haug §1.3.6.
    // ----------------------------------------------------------------
    auto haug = [&](const std::string& name, Option::Type t, Real strike,
                    Real spot, Real q, Real r, Real tYears, Real haugVol,
                    Real intensity, Real gamma) {
        const Real jVol = haugVol * std::sqrt(gamma / intensity);
        const Real dVol = haugVol * std::sqrt(1.0 - gamma);
        const Real meanLog = std::log(1.0) - 0.5 * jVol * jVol;
        const int days = static_cast<int>(tYears * 360 + 0.5);
        runCase(out, {name, t, strike, spot, r, q, dVol,
                      intensity, meanLog, jVol, days,
                      1.0e-4, 100});
    };

    // Sample the Haug grid corners (gamma=0.25/0.5/0.75 × strike=80/100/120
    // × t=0.10/0.25/0.50). Tests the Poisson-sum convergence under all
    // three jump-intensity regimes.
    haug("haug_g25_k80_t01_l1",  Option::Call,  80.0, 100.0, 0.0, 0.08, 0.10, 0.25, 1.0,  0.25);
    haug("haug_g25_k80_t05_l5",  Option::Call,  80.0, 100.0, 0.0, 0.08, 0.50, 0.25, 5.0,  0.25);
    haug("haug_g25_k100_t01_l1", Option::Call, 100.0, 100.0, 0.0, 0.08, 0.10, 0.25, 1.0,  0.25);
    haug("haug_g25_k100_t05_l10",Option::Call, 100.0, 100.0, 0.0, 0.08, 0.50, 0.25, 10.0, 0.25);
    haug("haug_g25_k120_t05_l5", Option::Call, 120.0, 100.0, 0.0, 0.08, 0.50, 0.25, 5.0,  0.25);
    haug("haug_g50_k100_t05_l5", Option::Call, 100.0, 100.0, 0.0, 0.08, 0.50, 0.25, 5.0,  0.50);
    haug("haug_g75_k100_t05_l5", Option::Call, 100.0, 100.0, 0.0, 0.08, 0.50, 0.25, 5.0,  0.75);

    out.write();
    return 0;
}
