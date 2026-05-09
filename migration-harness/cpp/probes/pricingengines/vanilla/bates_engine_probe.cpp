// migration-harness/cpp/probes/pricingengines/vanilla/bates_engine_probe.cpp
// Phase 5h.5-Bates — emit C++ v1.42.1 reference NPVs for the four Bates
// engines: BatesEngine (log-normal jump), BatesDetJumpEngine (deterministic
// jump intensity log-normal), BatesDoubleExpEngine (double-exponential
// jump), BatesDoubleExpDetJumpEngine (deterministic intensity double-exp).
// Java ports cross-validate against these references at the loose tier.

#include <ql/version.hpp>
#include <ql/processes/batesprocess.hpp>
#include <ql/instruments/europeanoption.hpp>
#include <ql/instruments/payoffs.hpp>
#include <ql/exercise.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>
#include <ql/pricingengines/vanilla/batesengine.hpp>
#include <ql/models/equity/batesmodel.hpp>
#include "../../common.hpp"

using namespace QuantLib;
using namespace jqml_harness;

namespace {

const Date TODAY(22, April, 2026);

Handle<YieldTermStructure> flatCurve(Real rate) {
    Settings::instance().evaluationDate() = TODAY;
    return Handle<YieldTermStructure>(
        ext::make_shared<FlatForward>(TODAY, rate, Actual365Fixed()));
}

struct EngineCase {
    std::string name;
    std::string engine;          // "bates", "batesDetJump", "batesDoubleExp", "batesDoubleExpDetJump"
    Option::Type type;
    Real strike, spot;
    Real r, q;
    int days;                    // maturity in days from TODAY
    Real v0, kappa, theta, sigma, rho;
    Real lambda, nu, delta;      // Bates / BatesDetJump
    Real nuUp, nuDown, p;        // BatesDoubleExp / DoubleExpDetJump
    Real kappaLambda, thetaLambda; // Det-jump variants
};

void runCase(ReferenceWriter& out, const EngineCase& tc) {
    Settings::instance().evaluationDate() = TODAY;
    Handle<YieldTermStructure> rCurve = flatCurve(tc.r);
    Handle<YieldTermStructure> qCurve = flatCurve(tc.q);
    Handle<Quote> spot(ext::make_shared<SimpleQuote>(tc.spot));

    Date exerciseDate = TODAY + tc.days;
    ext::shared_ptr<StrikedTypePayoff> payoff(
        new PlainVanillaPayoff(tc.type, tc.strike));
    ext::shared_ptr<Exercise> exercise(new EuropeanExercise(exerciseDate));
    VanillaOption option(payoff, exercise);

    ext::shared_ptr<BatesProcess> process(new BatesProcess(
        rCurve, qCurve, spot,
        tc.v0, tc.kappa, tc.theta, tc.sigma, tc.rho,
        tc.lambda, tc.nu, tc.delta));

    ext::shared_ptr<PricingEngine> engine;
    if (tc.engine == "bates") {
        engine = ext::shared_ptr<PricingEngine>(new BatesEngine(
            ext::make_shared<BatesModel>(process), 128));
    } else if (tc.engine == "batesDetJump") {
        engine = ext::shared_ptr<PricingEngine>(new BatesDetJumpEngine(
            ext::make_shared<BatesDetJumpModel>(process,
                                                tc.kappaLambda, tc.thetaLambda),
            128));
    } else if (tc.engine == "batesDoubleExp") {
        engine = ext::shared_ptr<PricingEngine>(new BatesDoubleExpEngine(
            ext::make_shared<BatesDoubleExpModel>(process,
                                                  tc.lambda, tc.nuUp, tc.nuDown, tc.p),
            128));
    } else if (tc.engine == "batesDoubleExpDetJump") {
        engine = ext::shared_ptr<PricingEngine>(new BatesDoubleExpDetJumpEngine(
            ext::make_shared<BatesDoubleExpDetJumpModel>(process,
                tc.lambda, tc.nuUp, tc.nuDown, tc.p,
                tc.kappaLambda, tc.thetaLambda),
            128));
    } else {
        throw std::runtime_error("unknown engine: " + tc.engine);
    }

    option.setPricingEngine(engine);
    Real npv = option.NPV();

    json inputs = {
        {"engine", tc.engine},
        {"type", tc.type == Option::Call ? "call" : "put"},
        {"strike", tc.strike}, {"spot", tc.spot},
        {"r", tc.r}, {"q", tc.q}, {"days", tc.days},
        {"v0", tc.v0}, {"kappa", tc.kappa}, {"theta", tc.theta},
        {"sigma", tc.sigma}, {"rho", tc.rho},
        {"lambda", tc.lambda}, {"nu", tc.nu}, {"delta", tc.delta},
        {"nuUp", tc.nuUp}, {"nuDown", tc.nuDown}, {"p", tc.p},
        {"kappaLambda", tc.kappaLambda}, {"thetaLambda", tc.thetaLambda},
        {"integrationOrder", 128}
    };
    json expected = {{"npv", npv}};
    out.addCase(tc.name, inputs, expected);
}

} // namespace

int main() {
    ReferenceWriter out("pricingengines/vanilla/bates_engine", QL_VERSION,
                        "bates_engine_probe");

    // Black-degenerate parameters — sigma~0, lambda~0 → BatesEngine should
    // reproduce Black formula. Mirrors testAnalyticVsBlack in
    // QuantLib's batesmodel.cpp test-suite.
    const Real R = 0.1, Q = 0.04, S0 = 32.0, K = 30.0;
    const int DAYS_6M = 183;  // ~6 months
    const Real V0 = 0.05, KAPPA = 5.0, THETA = 0.05;
    const Real TINY = 1.0e-4;

    // Case A: BatesEngine, near-Black (Put @ K=30, S0=32)
    runCase(out, {"bates_black_degenerate", "bates",
                  Option::Put, K, S0, R, Q, DAYS_6M,
                  V0, KAPPA, THETA, TINY, 0.0,
                  TINY, 0.0, TINY,
                  0.0, 0.0, 0.0,   // unused for "bates"
                  0.0, 0.0});

    // Case B: BatesEngine — jump-heavy parameters
    runCase(out, {"bates_jump_heavy", "bates",
                  Option::Call, 100.0, 100.0, 0.05, 0.02, 365,
                  0.04, 2.0, 0.04, 0.3, -0.5,
                  0.5, 0.05, 0.2,
                  0.0, 0.0, 0.0, 0.0, 0.0});

    // Case C: BatesEngine — Put OTM, longer maturity
    runCase(out, {"bates_put_otm", "bates",
                  Option::Put, 80.0, 100.0, 0.04, 0.01, 730,
                  0.05, 1.5, 0.05, 0.4, -0.6,
                  0.3, -0.05, 0.18,
                  0.0, 0.0, 0.0, 0.0, 0.0});

    // Case D: BatesDetJumpEngine — deterministic jump intensity
    runCase(out, {"batesDetJump_atm", "batesDetJump",
                  Option::Call, 100.0, 100.0, 0.05, 0.02, 365,
                  0.04, 2.0, 0.04, 0.3, -0.5,
                  0.5, 0.05, 0.2,
                  0.0, 0.0, 0.0,
                  1.0, 0.1});

    // Case E: BatesDoubleExpEngine — double-exponential jumps
    runCase(out, {"batesDoubleExp_atm", "batesDoubleExp",
                  Option::Call, 100.0, 100.0, 0.05, 0.02, 365,
                  0.04, 2.0, 0.04, 0.3, -0.5,
                  0.5, 0.0, 0.0,        // lambda only used; nu/delta unused
                  0.1, 0.1, 0.5,
                  0.0, 0.0});

    // Case F: BatesDoubleExpEngine — Put OTM
    runCase(out, {"batesDoubleExp_put_otm", "batesDoubleExp",
                  Option::Put, 80.0, 100.0, 0.04, 0.01, 730,
                  0.05, 1.5, 0.05, 0.4, -0.6,
                  0.3, 0.0, 0.0,
                  0.15, 0.1, 0.4,
                  0.0, 0.0});

    // Case G: BatesDoubleExpDetJumpEngine
    runCase(out, {"batesDoubleExpDetJump_atm", "batesDoubleExpDetJump",
                  Option::Call, 100.0, 100.0, 0.05, 0.02, 365,
                  0.04, 2.0, 0.04, 0.3, -0.5,
                  0.5, 0.0, 0.0,
                  0.1, 0.1, 0.5,
                  1.0, 0.1});

    out.write();
    return 0;
}
