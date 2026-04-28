// migration-harness/cpp/probes/pricingengines/swaption/fdg2swaptionengine_probe.cpp
//
// Probe for Phase 2h WI-3: FdG2SwaptionEngine NPV fingerprint.
//
// Cross-validates a 5Y x 5Y ATM payer swaption priced under the C++
// FdG2SwaptionEngine with a G2 (a=0.1, sigma=0.01, b=0.1, eta=0.005,
// rho=-0.5) on a 50x50x50 grid, no damping steps, against the upcoming
// Java FdG2SwaptionEngine port.
//
// The fixture mirrors treeswaptionengine_probe.cpp / blackswaptionengine_probe.cpp
// (same schedules, nominal, day counters, calendar) so the only difference vs
// the existing references is the model (G2 vs HullWhite) and the engine.

#include <ql/version.hpp>

#include <ql/exercise.hpp>
#include <ql/indexes/ibor/euribor.hpp>
#include <ql/instruments/swaption.hpp>
#include <ql/instruments/vanillaswap.hpp>
#include <ql/models/shortrate/twofactormodels/g2.hpp>
#include <ql/pricingengines/swap/discountingswapengine.hpp>
#include <ql/pricingengines/swaption/fdg2swaptionengine.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/settings.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/time/calendars/target.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>
#include <ql/time/daycounters/thirty360.hpp>
#include <ql/time/schedule.hpp>

#include "common.hpp"

using namespace QuantLib;
using namespace jqml_harness;

int main() {
    ReferenceWriter out("pricingengines/swaption/fdg2swaptionengine",
                        QL_VERSION, "fdg2swaptionengine_probe");

    // --- Fixture (mirrors treeswaptionengine_probe schedules) ---------------
    const Date eval(15, January, 2026);
    Settings::instance().evaluationDate() = eval;

    const DayCounter dc = Actual365Fixed();
    const Calendar cal = TARGET();
    const Real flatRate = 0.05;
    // G2 parameters
    const Real g2A = 0.1;
    const Real g2Sigma = 0.01;
    const Real g2B = 0.1;
    const Real g2Eta = 0.005;
    const Real g2Rho = -0.5;
    // FD grid
    const Size tGrid = 50;
    const Size xGrid = 50;
    const Size yGrid = 50;
    const Size dampingSteps = 0;

    const Handle<YieldTermStructure> ts(
        ext::make_shared<FlatForward>(eval, flatRate, dc, Continuous, Annual));

    const auto idx = ext::make_shared<Euribor3M>(ts);

    const Date exerciseDate = cal.advance(eval, Period(5, Years));
    const auto exercise = ext::make_shared<EuropeanExercise>(exerciseDate);
    const Date startDate = cal.advance(exerciseDate, 2, Days);
    const Date maturity = cal.advance(startDate, Period(5, Years));

    const DayCounter fixedDc = Thirty360(Thirty360::European);

    Schedule fixedSchedule(startDate, maturity, Period(1, Years), cal,
                           ModifiedFollowing, ModifiedFollowing,
                           DateGeneration::Forward, false);
    Schedule floatSchedule(startDate, maturity, Period(3, Months), cal,
                           ModifiedFollowing, ModifiedFollowing,
                           DateGeneration::Forward, false);

    const Real nominal = 100.0;
    const Rate dummyRate = 0.04;
    auto swap0 = ext::make_shared<VanillaSwap>(
        VanillaSwap::Payer, nominal, fixedSchedule, dummyRate, fixedDc,
        floatSchedule, idx, 0.0, dc);
    swap0->setPricingEngine(ext::make_shared<DiscountingSwapEngine>(ts));
    const Rate atmRate = swap0->fairRate();

    auto swap = ext::make_shared<VanillaSwap>(
        VanillaSwap::Payer, nominal, fixedSchedule, atmRate, fixedDc,
        floatSchedule, idx, 0.0, dc);

    Swaption swaption(swap, exercise);
    auto g2 = ext::make_shared<G2>(ts, g2A, g2Sigma, g2B, g2Eta, g2Rho);
    swaption.setPricingEngine(
        ext::make_shared<FdG2SwaptionEngine>(
            g2, tGrid, xGrid, yGrid, dampingSteps));
    const Real npvFdG2 = swaption.NPV();

    json inputs = {
        {"eval_date", "2026-01-15"},
        {"flat_rate", flatRate},
        {"g2_a", g2A},
        {"g2_sigma", g2Sigma},
        {"g2_b", g2B},
        {"g2_eta", g2Eta},
        {"g2_rho", g2Rho},
        {"t_grid", tGrid},
        {"x_grid", xGrid},
        {"y_grid", yGrid},
        {"damping_steps", dampingSteps},
        {"exercise_years", 5},
        {"swap_years", 5},
        {"fixed_freq", "Annual"},
        {"float_tenor_months", 3},
        {"fixed_day_counter", "30/360 European"},
        {"yts_day_counter", "Actual/365 Fixed"},
        {"calendar", "TARGET"},
        {"index", "Euribor3M"},
        {"nominal", nominal},
        {"dummy_fixed_rate", dummyRate}
    };
    json expected = {
        {"atm_rate", atmRate},
        {"swaption_npv_fd_g2", npvFdG2}
    };

    out.addCase("atm_payer_5y5y_fd_g2", inputs, expected);
    out.write();
    return 0;
}
