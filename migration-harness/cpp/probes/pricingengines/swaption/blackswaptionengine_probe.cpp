// migration-harness/cpp/probes/pricingengines/swaption/blackswaptionengine_probe.cpp
//
// Probe for Phase 2e WI-3: BlackSwaptionEngine NPV fingerprint.
//
// Cross-validates a 5Y x 5Y ATM payer swaption priced under Black76
// (shifted-lognormal, displacement = 0.0) against the upcoming Java
// BlackSwaptionEngine port.
//
// Captured outputs:
//   - swap_npv               : par-rate of the underlying swap (atm strike)
//   - atm_rate               : same as swap.fairRate() before re-pricing the ATM swap
//   - swaption_npv           : Swaption.NPV() under BlackSwaptionEngine(ts, vol)
//   - bachelier_swaption_npv : Swaption.NPV() under BachelierSwaptionEngine
//                              with normal_vol = 0.01 (Phase 2f WI-2 extension)
//
// The Java port covers both Black76 and Bachelier (Phase 2f WI-2). The fixture
// mirrors the standard probe convention: eval=2026-01-15,
// FlatForward 5% Continuous Actual365Fixed on TARGET, Euribor3M float leg,
// 30/360 European fixed leg, fixed leg Annual, exercise five years from eval,
// underlying swap five years long, vol = 20%.

#include <ql/version.hpp>

#include <ql/exercise.hpp>
#include <ql/indexes/ibor/euribor.hpp>
#include <ql/instruments/makevanillaswap.hpp>
#include <ql/instruments/swaption.hpp>
#include <ql/instruments/vanillaswap.hpp>
#include <ql/pricingengines/swap/discountingswapengine.hpp>
#include <ql/pricingengines/swaption/blackswaptionengine.hpp>
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
    ReferenceWriter out("pricingengines/swaption/blackswaptionengine",
                        QL_VERSION, "blackswaptionengine_probe");

    // --- Fixture -------------------------------------------------------------
    const Date eval(15, January, 2026);
    Settings::instance().evaluationDate() = eval;

    const DayCounter dc = Actual365Fixed();
    const Calendar cal = TARGET();
    const Real flatRate = 0.05;
    const Volatility vol = 0.20;

    const Handle<YieldTermStructure> ts(
        ext::make_shared<FlatForward>(eval, flatRate, dc, Continuous, Annual));

    const auto idx = ext::make_shared<Euribor3M>(ts);

    // 5Y x 5Y: exercise = eval + 5Y, underlying swap starts at exercise + 2BD
    // (TARGET spot lag), maturity = start + 5Y.
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

    // --- Step 1: build a dummy swap to read the par (ATM) fixed rate. --------
    const Real nominal = 100.0;
    const Rate dummyRate = 0.04;
    auto swap0 = ext::make_shared<VanillaSwap>(
        VanillaSwap::Payer, nominal, fixedSchedule, dummyRate, fixedDc,
        floatSchedule, idx, 0.0, dc);
    swap0->setPricingEngine(ext::make_shared<DiscountingSwapEngine>(ts));
    const Rate atmRate = swap0->fairRate();
    const Real swap0NPV = swap0->NPV();

    // --- Step 2: rebuild the ATM swap and price the swaption. ----------------
    auto swap = ext::make_shared<VanillaSwap>(
        VanillaSwap::Payer, nominal, fixedSchedule, atmRate, fixedDc,
        floatSchedule, idx, 0.0, dc);
    // (No engine on swap; BlackSwaptionEngine sets its own DiscountingSwapEngine.)

    Swaption swaption(swap, exercise);
    swaption.setPricingEngine(
        ext::make_shared<BlackSwaptionEngine>(ts, vol));
    const Real npv = swaption.NPV();

    // Bachelier branch (Phase 2f WI-2). Use a typical normal vol level
    // (1% absolute) for an ATM-quoted forward of ~5%, so the Bachelier price
    // is in the same general magnitude as the lognormal one.
    const Volatility normalVol = 0.01;
    Swaption swaptionBach(swap, exercise);
    swaptionBach.setPricingEngine(
        ext::make_shared<BachelierSwaptionEngine>(ts, normalVol));
    const Real bachelierNPV = swaptionBach.NPV();

    json inputs = {
        {"eval_date", "2026-01-15"},
        {"flat_rate", flatRate},
        {"vol", vol},
        {"normal_vol", normalVol},
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
        {"swap0_npv", swap0NPV},
        {"atm_rate", atmRate},
        {"swaption_npv", npv},
        {"bachelier_swaption_npv", bachelierNPV}
    };

    out.addCase("atm_payer_5y5y", inputs, expected);
    out.write();
    return 0;
}
