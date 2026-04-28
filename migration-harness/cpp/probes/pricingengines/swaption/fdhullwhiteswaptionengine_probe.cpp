// migration-harness/cpp/probes/pricingengines/swaption/fdhullwhiteswaptionengine_probe.cpp
//
// Probe for Phase 2h WI-2: FdHullWhiteSwaptionEngine NPV fingerprint.
//
// Cross-validates a 5Y x 5Y ATM payer swaption priced under a one-factor
// Hull-White model (a = 0.1, sigma = 0.01) using the finite-difference
// engine FdHullWhiteSwaptionEngine against the Java port.
//
// Fixture mirrors the Phase 2f WI-2 JamshidianSwaptionEngine probe so the
// engines under test see the exact same swap object: eval=2026-01-15,
// FlatForward 5% Continuous Actual365Fixed on TARGET, Euribor3M float leg,
// 30/360 European fixed leg, fixed leg Annual, exercise five years from
// eval, underlying swap five years long.
//
// Engine grid parameters are kept at the Java-port defaults
// (tGrid=100, xGrid=100, dampingSteps=0, invEps=1e-5, scheme=Douglas)
// so the discretisation noise is reproducible.
//
// Captured outputs:
//   - swap0_npv             : NPV of the dummy swap (sanity check)
//   - atm_rate              : par rate of the underlying swap
//   - fd_hw_swaption_npv    : Swaption.NPV() under FdHullWhiteSwaptionEngine

#include <ql/version.hpp>

#include <ql/exercise.hpp>
#include <ql/indexes/ibor/euribor.hpp>
#include <ql/instruments/swaption.hpp>
#include <ql/instruments/vanillaswap.hpp>
#include <ql/models/shortrate/onefactormodels/hullwhite.hpp>
#include <ql/pricingengines/swap/discountingswapengine.hpp>
#include <ql/pricingengines/swaption/fdhullwhiteswaptionengine.hpp>
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
    ReferenceWriter out("pricingengines/swaption/fdhullwhiteswaptionengine",
                        QL_VERSION, "fdhullwhiteswaptionengine_probe");

    // --- Fixture -------------------------------------------------------------
    const Date eval(15, January, 2026);
    Settings::instance().evaluationDate() = eval;

    const DayCounter dc = Actual365Fixed();
    const Calendar cal = TARGET();
    const Real flatRate = 0.05;
    const Real hwA = 0.1;
    const Real hwSigma = 0.01;

    const Size tGrid = 100;
    const Size xGrid = 100;
    const Size dampingSteps = 0;
    const Real invEps = 1.0e-5;

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

    // Step 1: dummy swap to read the par rate.
    const Real nominal = 100.0;
    const Rate dummyRate = 0.04;
    auto swap0 = ext::make_shared<VanillaSwap>(
        VanillaSwap::Payer, nominal, fixedSchedule, dummyRate, fixedDc,
        floatSchedule, idx, 0.0, dc);
    swap0->setPricingEngine(ext::make_shared<DiscountingSwapEngine>(ts));
    const Rate atmRate = swap0->fairRate();
    const Real swap0NPV = swap0->NPV();

    // Step 2: ATM swap + Hull-White FD swaption.
    auto swap = ext::make_shared<VanillaSwap>(
        VanillaSwap::Payer, nominal, fixedSchedule, atmRate, fixedDc,
        floatSchedule, idx, 0.0, dc);

    auto hw = ext::make_shared<HullWhite>(ts, hwA, hwSigma);

    Swaption swaption(swap, exercise);
    swaption.setPricingEngine(
        ext::make_shared<FdHullWhiteSwaptionEngine>(
            hw, tGrid, xGrid, dampingSteps, invEps,
            FdmSchemeDesc::Douglas()));
    const Real npv = swaption.NPV();

    json inputs = {
        {"eval_date", "2026-01-15"},
        {"flat_rate", flatRate},
        {"hw_a", hwA},
        {"hw_sigma", hwSigma},
        {"exercise_years", 5},
        {"swap_years", 5},
        {"fixed_freq", "Annual"},
        {"float_tenor_months", 3},
        {"fixed_day_counter", "30/360 European"},
        {"yts_day_counter", "Actual/365 Fixed"},
        {"calendar", "TARGET"},
        {"index", "Euribor3M"},
        {"nominal", nominal},
        {"dummy_fixed_rate", dummyRate},
        {"t_grid", tGrid},
        {"x_grid", xGrid},
        {"damping_steps", dampingSteps},
        {"inv_eps", invEps},
        {"scheme", "Douglas"}
    };
    json expected = {
        {"swap0_npv", swap0NPV},
        {"atm_rate", atmRate},
        {"fd_hw_swaption_npv", npv}
    };

    out.addCase("atm_payer_5y5y", inputs, expected);
    out.write();
    return 0;
}
