// migration-harness/cpp/probes/pricingengines/barrier/fd_heston_rebate_engine_probe.cpp
//
// Probe for Phase 4n.5b: FdHestonRebateEngine NPV/delta/gamma/theta fingerprint.
//
// Process: S=100, r=5% flat, q=2% flat.
// Heston:  v0=0.04 (20% vol), kappa=1.0, theta=0.04, sigma=0.3, rho=+0.3.
// Eval:    2026-01-15.
//
// Tolerance tier: LOOSE (1e-2 abs/rel) -- 2-factor FD on coarse grid.
//
// FdHestonRebateEngine prices the rebate component for in-barriers
// (DownIn/UpIn) and out-barriers (DownOut/UpOut) — value of the cash rebate
// paid when the barrier is hit (in) or NOT hit (out). All four barrier
// types exercised here.

#include <ql/version.hpp>

#include <ql/exercise.hpp>
#include <ql/instruments/barrieroption.hpp>
#include <ql/instruments/payoffs.hpp>
#include <ql/methods/finitedifferences/solvers/fdmbackwardsolver.hpp>
#include <ql/models/equity/hestonmodel.hpp>
#include <ql/pricingengines/barrier/fdhestonrebateengine.hpp>
#include <ql/processes/hestonprocess.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/settings.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/time/calendars/nullcalendar.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>

#include "common.hpp"

using namespace QuantLib;
using namespace jqml_harness;

namespace {

void addCase(ReferenceWriter& out,
             const std::string& name,
             const ext::shared_ptr<HestonModel>& hestonModel,
             Barrier::Type barrierType,
             double barrier,
             double rebate,
             double strike,
             double maturityYears,
             Option::Type type,
             const Date& eval,
             int tGrid = 50,
             int xGrid = 50,
             int vGrid = 20,
             int dampingSteps = 0) {

    const Date exerciseDate = eval + int(maturityYears * 365 + 0.5);
    const auto exercise = ext::make_shared<EuropeanExercise>(exerciseDate);
    const auto payoff   = ext::make_shared<PlainVanillaPayoff>(type, strike);
    BarrierOption option(barrierType, barrier, rebate, payoff, exercise);

    option.setPricingEngine(
        ext::make_shared<FdHestonRebateEngine>(
            hestonModel,
            tGrid, xGrid, vGrid, dampingSteps,
            FdmSchemeDesc::Hundsdorfer()));

    const double npv   = option.NPV();
    const double delta = option.delta();
    const double gamma = option.gamma();

    const std::string typeStr = (type == Option::Call) ? "Call" : "Put";
    std::string barTypeStr;
    switch (barrierType) {
        case Barrier::DownOut: barTypeStr = "DownOut"; break;
        case Barrier::UpOut:   barTypeStr = "UpOut";   break;
        case Barrier::DownIn:  barTypeStr = "DownIn";  break;
        case Barrier::UpIn:    barTypeStr = "UpIn";    break;
        default:               barTypeStr = "Unknown";
    }

    json inputs = {
        {"strike",         strike},
        {"barrier",        barrier},
        {"rebate",         rebate},
        {"barrier_type",   barTypeStr},
        {"maturity_years", maturityYears},
        {"option_type",    typeStr},
        {"t_grid",         tGrid},
        {"x_grid",         xGrid},
        {"v_grid",         vGrid},
        {"damping_steps",  dampingSteps}
    };
    json expected = {
        {"npv",   npv},
        {"delta", delta},
        {"gamma", gamma}
    };

    out.addCase(name, inputs, expected);
}

} // anonymous namespace

int main() {
    ReferenceWriter out("pricingengines/barrier/fd_heston_rebate_engine",
                        QL_VERSION,
                        "fd_heston_rebate_engine_probe");

    const Date eval(15, January, 2026);
    Settings::instance().evaluationDate() = eval;
    const DayCounter dc = Actual365Fixed();

    const Handle<Quote> s0(ext::make_shared<SimpleQuote>(100.0));
    const Handle<YieldTermStructure> rTS(
        ext::make_shared<FlatForward>(eval, 0.05, dc, Continuous, Annual));
    const Handle<YieldTermStructure> qTS(
        ext::make_shared<FlatForward>(eval, 0.02, dc, Continuous, Annual));

    const double v0 = 0.04, kappa = 1.0, theta = 0.04, sigma = 0.3, rho = 0.3;
    const auto hestonProcess = ext::make_shared<HestonProcess>(
        rTS, qTS, s0, v0, kappa, theta, sigma, rho);
    const auto hestonModel = ext::make_shared<HestonModel>(hestonProcess);

    // --- All four barrier types, 1y, rebate=10 (so values are non-trivial) ---
    // Down-and-out call (rebate paid if barrier NOT hit)
    addCase(out, "down_out_call_K100_B80_rebate10_1y", hestonModel,
            Barrier::DownOut, 80.0, 10.0, 100.0, 1.0, Option::Call, eval);
    // Up-and-out call
    addCase(out, "up_out_call_K100_B120_rebate10_1y", hestonModel,
            Barrier::UpOut, 120.0, 10.0, 100.0, 1.0, Option::Call, eval);
    // Down-and-in call (rebate paid if barrier hit)
    addCase(out, "down_in_call_K100_B80_rebate10_1y", hestonModel,
            Barrier::DownIn, 80.0, 10.0, 100.0, 1.0, Option::Call, eval);
    // Up-and-in call
    addCase(out, "up_in_call_K100_B120_rebate10_1y", hestonModel,
            Barrier::UpIn, 120.0, 10.0, 100.0, 1.0, Option::Call, eval);

    // Down-and-out put + rebate
    addCase(out, "down_out_put_K100_B80_rebate10_1y", hestonModel,
            Barrier::DownOut, 80.0, 10.0, 100.0, 1.0, Option::Put, eval);
    // Down-and-in put + rebate
    addCase(out, "down_in_put_K100_B80_rebate10_1y", hestonModel,
            Barrier::DownIn, 80.0, 10.0, 100.0, 1.0, Option::Put, eval);

    out.write();
    return 0;
}
