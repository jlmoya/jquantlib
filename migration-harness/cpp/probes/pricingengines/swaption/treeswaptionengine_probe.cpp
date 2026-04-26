// migration-harness/cpp/probes/pricingengines/swaption/treeswaptionengine_probe.cpp
//
// Probe for Phase 2e WI-3: TreeSwaptionEngine NPV fingerprint on HullWhite.
//
// Cross-validates a 5Y x 5Y ATM payer swaption priced under the C++
// TreeSwaptionEngine with a HullWhite (a=0.1, sigma=0.01) tree (100 steps)
// against the upcoming Java TreeSwaptionEngine + DiscretizedSwaption +
// DiscretizedSwap port.
//
// The fixture mirrors blackswaptionengine_probe.cpp exactly (same schedules,
// nominal, fixed rate, day counters, calendar) — only the engine differs.
// This way Java's TreeSwaptionEngine result can be compared to C++'s on the
// same swap, and the difference vs. BlackSwaptionEngine is purely the
// pricing model.

#include <ql/version.hpp>

#include <ql/exercise.hpp>
#include <ql/indexes/ibor/euribor.hpp>
#include <ql/instruments/swaption.hpp>
#include <ql/instruments/vanillaswap.hpp>
#include <ql/models/shortrate/onefactormodels/hullwhite.hpp>
#include <ql/pricingengines/swap/discountingswapengine.hpp>
#include <ql/pricingengines/swaption/treeswaptionengine.hpp>
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
    ReferenceWriter out("pricingengines/swaption/treeswaptionengine",
                        QL_VERSION, "treeswaptionengine_probe");

    // --- Fixture (identical to blackswaptionengine_probe) --------------------
    const Date eval(15, January, 2026);
    Settings::instance().evaluationDate() = eval;

    const DayCounter dc = Actual365Fixed();
    const Calendar cal = TARGET();
    const Real flatRate = 0.05;
    const Real hwA = 0.1;
    const Real hwSigma = 0.01;
    const Size timeSteps = 100;

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

    // ATM rate from a dummy swap.
    const Real nominal = 100.0;
    const Rate dummyRate = 0.04;
    auto swap0 = ext::make_shared<VanillaSwap>(
        VanillaSwap::Payer, nominal, fixedSchedule, dummyRate, fixedDc,
        floatSchedule, idx, 0.0, dc);
    swap0->setPricingEngine(ext::make_shared<DiscountingSwapEngine>(ts));
    const Rate atmRate = swap0->fairRate();

    // ATM swap + swaption priced via HullWhite tree.
    auto swap = ext::make_shared<VanillaSwap>(
        VanillaSwap::Payer, nominal, fixedSchedule, atmRate, fixedDc,
        floatSchedule, idx, 0.0, dc);

    Swaption swaption(swap, exercise);
    auto hw = ext::make_shared<HullWhite>(ts, hwA, hwSigma);
    swaption.setPricingEngine(
        ext::make_shared<TreeSwaptionEngine>(hw, timeSteps, ts));
    const Real npvHwTree = swaption.NPV();

    json inputs = {
        {"eval_date", "2026-01-15"},
        {"flat_rate", flatRate},
        {"hw_a", hwA},
        {"hw_sigma", hwSigma},
        {"time_steps", timeSteps},
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
        {"swaption_npv_hw_tree", npvHwTree}
    };

    out.addCase("atm_payer_5y5y_hw_tree", inputs, expected);
    out.write();
    return 0;
}
