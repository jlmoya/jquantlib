// Engine-path diagnostic probe — Phase 5e.5b-CFC-d-284.
// Replays the TreeSwaptionEngine pricing manually so each intermediate
// rollback step's values can be dumped and compared with Java step-by-step.
//
// The existing tree_state probe captures DIRECT rollback through a
// DiscretizedSwap; this probe captures the ENGINE path through
// DiscretizedSwaption (initialize at last exercise, rollback to first
// non-negative exercise, presentValue) for n_ex=1.

#include <ql/version.hpp>
#include <ql/cashflows/coupon.hpp>
#include <ql/exercise.hpp>
#include <ql/indexes/ibor/euribor.hpp>
#include <ql/instruments/swaption.hpp>
#include <ql/instruments/vanillaswap.hpp>
#include <ql/methods/lattices/trinomialtree.hpp>
#include <ql/models/shortrate/onefactormodels/hullwhite.hpp>
#include <ql/pricingengines/swap/discountingswapengine.hpp>
#include <ql/pricingengines/swap/discretizedswap.hpp>
#include <ql/pricingengines/swaption/discretizedswaption.hpp>
#include <ql/pricingengines/swaption/treeswaptionengine.hpp>
#include <ql/settings.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>
#include <ql/time/daycounters/thirty360.hpp>
#include <ql/time/schedule.hpp>
#include <ql/timegrid.hpp>

#include "common.hpp"

using namespace QuantLib;
using namespace jqml_harness;

int main() {
    ReferenceWriter out("instruments/bermudan_engine_path",
                        QL_VERSION, "bermudan_engine_path_probe");

    Settings::instance().evaluationDate() = Date(15, February, 2002);
    RelinkableHandle<YieldTermStructure> ts;
    auto idx = ext::make_shared<Euribor6M>(ts);
    Calendar cal = idx->fixingCalendar();
    Date settle(19, February, 2002);
    Date today(15, February, 2002);
    DayCounter dc = Actual365Fixed();
    ts.linkTo(ext::make_shared<FlatForward>(settle, 0.04875825, dc));

    Date start = cal.advance(settle, 1, Years);
    Date maturity = cal.advance(start, 5, Years);
    Schedule fixSched(start, maturity, Period(Annual), cal,
                       Unadjusted, Unadjusted, DateGeneration::Forward, false);
    Schedule fltSched(start, maturity, Period(Semiannual), cal,
                       ModifiedFollowing, ModifiedFollowing, DateGeneration::Forward, false);
    DayCounter fixDc = Thirty360(Thirty360::BondBasis);

    auto swap0 = ext::make_shared<VanillaSwap>(
        Swap::Payer, 1000.0, fixSched, 0.0, fixDc, fltSched, idx, 0.0, idx->dayCounter());
    swap0->setPricingEngine(ext::make_shared<DiscountingSwapEngine>(ts));
    Rate atm = swap0->fairRate();

    auto atmSwap = ext::make_shared<VanillaSwap>(
        Swap::Payer, 1000.0, fixSched, atm, fixDc, fltSched, idx, 0.0, idx->dayCounter());
    atmSwap->setPricingEngine(ext::make_shared<DiscountingSwapEngine>(ts));

    std::vector<Date> exDates;
    for (auto& cf : atmSwap->fixedLeg()) {
        exDates.push_back(ext::dynamic_pointer_cast<Coupon>(cf)->accrualStartDate());
    }

    json inputs = {{"fixture", "Phase5e.5b-CFC-d-284 engine path"}};
    json expected;
    expected["atm_rate"] = atm;

    auto hw = ext::make_shared<HullWhite>(ts, 0.048696, 0.0058904);

    // n_ex=1 engine path replay.
    std::vector<Date> subset(exDates.begin(), exDates.begin() + 1);
    auto exer = ext::make_shared<BermudanExercise>(subset);
    Swaption sw(atmSwap, exer);
    Swaption::arguments args;
    sw.setupArguments(&args);

    DiscretizedSwaption swaption(args, today, dc);
    auto mand = swaption.mandatoryTimes();
    TimeGrid grid(mand.begin(), mand.end(), 50);
    auto latt = hw->tree(grid);

    Time exTime = dc.yearFraction(today, exDates[0]);
    expected["exercise_time"] = exTime;

    // Replay the TreeSwaptionEngine::calculate path for n_ex=1:
    //   stoppingTimes.back() = exTime (only one exercise)
    //   nextExercise         = exTime (first non-negative)
    // So swaption.initialize(latt, exTime); swaption.rollback(exTime);
    // For n=1 the rollback is a no-op (close(from,to)==true), but
    // swaption.rollback ALSO calls swaption.adjustValues() unconditionally
    // (rollback => partialRollback => no-op for equal times; then adjustValues).
    //
    // That means the full engine path is:
    //   1) swaption.initialize(latt, exTime)
    //        → swaption.setTime(exTime)
    //        → swaption.reset(size_at_exTime)
    //              → underlying.initialize(method, lastPayment)
    //                   → underlying.setTime(lastPayment)
    //                   → underlying.reset(size) → fill zero + adjustValues
    //              → DiscretizedOption::reset
    //                   → values_ = zero
    //                   → adjustValues:
    //                        - preAdjustValues (no-op)
    //                        - postAdjustValues:
    //                              underlying.partialRollback(exTime)
    //                              underlying.preAdjustValues  (sets latestPre to exTime)
    //                              applyExerciseCondition (max(under, 0))
    //                              underlying.postAdjustValues (sets latestPost to exTime)
    //                              ⇒ swaption.latestPostAdjustment = exTime
    //   2) swaption.rollback(exTime) → close(from,to)==true → no-op.
    //      Then adjustValues runs:
    //        - preAdjustValues: latestPre==MAX != exTime → calls impl (no-op for swaption)
    //          But wait - the inherited DiscretizedOption doesn't override
    //          preAdjustValuesImpl, so it's the empty default; sets latestPre=exTime.
    //        - postAdjustValues: latestPost==exTime → skipped (close_enough)
    //   3) swaption.presentValue() at exTime
    //
    // So the divergence — if any — must be inside one of these steps.
    // We capture each step's values.

    // Cross-check FIRST so that any side-effects don't influence the
    // manual replay.
    {
        auto eng = ext::make_shared<TreeSwaptionEngine>(hw, 50);
        Swaption sw2(atmSwap, exer);
        sw2.setPricingEngine(eng);
        expected["engine_npv_n1"] = sw2.NPV();
    }

    // Manual engine-path trace of the UNDERLYING (compare with Java).
    // Also: try constructing a *fresh* DiscretizedSwaption and replaying via
    // its underlying, exactly as the engine does.
    {
        // First, replicate the C++ engine path 1:1 by INSPECTING the
        // DiscretizedSwaption's underlying (which is constructed inside its
        // ctor via prepareSwaptionWithSnappedDates → DiscretizedSwap on
        // snapped args).
        // But DiscretizedSwaption::underlying_ is protected; we can't access
        // it directly. Instead we mirror the snapping logic: in our fixture
        // snapping is a no-op (exDates ≡ fixedResetDates).
    }
    {
        Time lastPay = dc.yearFraction(today, atmSwap->fixedLeg().back()->date());
        Time lastFlt = dc.yearFraction(today, atmSwap->floatingLeg().back()->date());
        lastPay = std::max(lastPay, lastFlt);

        DiscretizedSwap und(args, today, dc);
        und.initialize(latt, lastPay);

        und.partialRollback(exTime);
        json u1 = json::array();
        for (Size j = 0; j < und.values().size(); j++) u1.push_back(und.values()[j]);
        expected["und_after_partialRollback"] = u1;

        und.preAdjustValues();
        json u2 = json::array();
        for (Size j = 0; j < und.values().size(); j++) u2.push_back(und.values()[j]);
        expected["und_after_preAdjust"] = u2;

        // Direct path comparison:
        DiscretizedSwap und2(args, today, dc);
        und2.initialize(latt, lastPay);
        und2.rollback(exTime);
        und2.preAdjustValues();
        json u3 = json::array();
        for (Size j = 0; j < und2.values().size(); j++) u3.push_back(und2.values()[j]);
        expected["und_direct_after_rollback_preAdjust"] = u3;
    }

    json snapshots = json::array();

    swaption.initialize(latt, exTime);
    {
        json snap = {{"step", "after_initialize"},
                     {"time", swaption.time()}};
        json vals = json::array();
        for (Size j = 0; j < swaption.values().size(); j++)
            vals.push_back(swaption.values()[j]);
        snap["swaption_values"] = vals;
        snapshots.push_back(snap);
    }

    swaption.rollback(exTime);
    {
        json snap = {{"step", "after_rollback"},
                     {"time", swaption.time()}};
        json vals = json::array();
        for (Size j = 0; j < swaption.values().size(); j++)
            vals.push_back(swaption.values()[j]);
        snap["swaption_values"] = vals;
        snapshots.push_back(snap);
    }

    expected["snapshots"] = snapshots;
    expected["present_value"] = swaption.presentValue();
    expected["swaption_time_at_pv"] = swaption.time();
    expected["grid_index_at_exTime"] = grid.index(exTime);
    {
        // dump state prices at the index used by presentValue.
        json sps = json::array();
        auto srt = ext::dynamic_pointer_cast<OneFactorModel::ShortRateTree>(latt);
        const Array& spArr = srt->statePrices(grid.index(exTime));
        for (Size j = 0; j < spArr.size(); j++) sps.push_back(spArr[j]);
        expected["state_prices_at_exTime"] = sps;
    }

    out.addCase("scan", inputs, expected);
    out.write();
    return 0;
}
