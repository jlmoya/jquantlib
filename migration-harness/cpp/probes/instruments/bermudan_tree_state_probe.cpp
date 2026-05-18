// Tree-state diagnostic probe — Phase 5e.5b-CFC-d-261.
// Captures HullWhite ShortRateTree internals (size per step, underlying x[i,j],
// discount[i,j], grid times, mandatory times, swap pay times) for the same
// BermudanSwaptionTest cached_hw fixture so a Java equivalent probe can
// compare bit-by-bit.
//
// Findings: tree state, time grid, mandatory times, swap arg pay/reset times
// all bit-match between Java and C++. Yet engine NPV diverges by +0.011
// (n=1) to +0.186 (n=5). See BermudanSwaptionTest @Ignore Javadoc and
// BermudanTreeDiagJavaProbe for the Java side.

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
    ReferenceWriter out("instruments/bermudan_tree_state",
                        QL_VERSION, "bermudan_tree_state_probe");

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

    json inputs = {{"fixture", "Phase5e.5b-CFC-d-261 tree state"}};
    json expected;
    expected["atm_rate"] = atm;

    auto hw = ext::make_shared<HullWhite>(ts, 0.048696, 0.0058904);

    // Build the DiscretizedSwaption for n_ex=1 to inspect its mandatoryTimes.
    {
        std::vector<Date> subset(exDates.begin(), exDates.begin() + 1);
        auto exer = ext::make_shared<BermudanExercise>(subset);
        Swaption sw(atmSwap, exer);
        Swaption::arguments args;
        sw.setupArguments(&args);

        DiscretizedSwaption discSwn(args, today, dc);
        auto mand = discSwn.mandatoryTimes();
        json mandJson = json::array();
        for (auto t : mand) mandJson.push_back(t);
        expected["mandatory_times_n1"] = mandJson;

        TimeGrid grid(mand.begin(), mand.end(), 50);
        json timesJson = json::array();
        for (Size i = 0; i < grid.size(); i++) timesJson.push_back(grid[i]);
        expected["grid_times_n1"] = timesJson;
        expected["grid_size_n1"] = grid.size();

        // Build the tree and dump first few sizes / state nodes
        auto latt = hw->tree(grid);
        auto srt = ext::dynamic_pointer_cast<OneFactorModel::ShortRateTree>(latt);
        json sizes = json::array();
        json firstNodes = json::array();
        json firstDiscs = json::array();
        for (Size i = 0; i < std::min(Size(10), grid.size()); i++) {
            sizes.push_back(srt->size(i));
            json nodes_i = json::array();
            json discs_i = json::array();
            for (Size j = 0; j < std::min(Size(7), srt->size(i)); j++) {
                nodes_i.push_back(srt->underlying(i, j));
                discs_i.push_back(srt->discount(i, j));
            }
            firstNodes.push_back(nodes_i);
            firstDiscs.push_back(discs_i);
        }
        expected["tree_sizes"] = sizes;
        expected["tree_x"] = firstNodes;
        expected["tree_disc"] = firstDiscs;

        // Dump underlying-swap values rolled back to exTime (direct path —
        // matches Java direct-rollback bit-exact, but engine-NPV path
        // produces slightly different values for reasons not yet pinpointed).
        Time exTime = dc.yearFraction(today, exDates[0]);
        Time lastPay = dc.yearFraction(today, atmSwap->fixedLeg().back()->date());
        Time lastFlt = dc.yearFraction(today, atmSwap->floatingLeg().back()->date());
        lastPay = std::max(lastPay, lastFlt);

        DiscretizedSwap traceUnd(args, today, dc);
        traceUnd.initialize(latt, lastPay);
        traceUnd.rollback(exTime);
        traceUnd.preAdjustValues();
        json undVals = json::array();
        for (Size j = 0; j < traceUnd.values().size(); j++) undVals.push_back(traceUnd.values()[j]);
        expected["underlying_values_at_exTime"] = undVals;
        expected["exercise_time"] = exTime;
        expected["last_payment"] = lastPay;
    }

    // Capture n=1..5 Bermudan engine NPV — the same set as bermudan_tree_diag.
    auto treeEng = ext::make_shared<TreeSwaptionEngine>(hw, 50);
    for (size_t n = 1; n <= exDates.size(); n++) {
        std::vector<Date> subset(exDates.begin(), exDates.begin() + n);
        auto exer = ext::make_shared<BermudanExercise>(subset);
        Swaption s(atmSwap, exer);
        s.setPricingEngine(treeEng);
        std::string k = "tree_npv_" + std::to_string(n) + "ex";
        expected[k] = s.NPV();
    }

    // Dump fixed/floating pay times from arguments path (what DiscretizedSwap sees).
    {
        Swaption::arguments args;
        std::vector<Date> subset(exDates.begin(), exDates.end());
        auto exer = ext::make_shared<BermudanExercise>(subset);
        Swaption sw(atmSwap, exer);
        sw.setupArguments(&args);
        json frd = json::array(), fpd = json::array();
        for (auto& d : args.fixedResetDates) frd.push_back((double) dc.yearFraction(today, d));
        for (auto& d : args.fixedPayDates) fpd.push_back((double) dc.yearFraction(today, d));
        json flrd = json::array(), flpd = json::array();
        for (auto& d : args.floatingResetDates) flrd.push_back((double) dc.yearFraction(today, d));
        for (auto& d : args.floatingPayDates) flpd.push_back((double) dc.yearFraction(today, d));
        expected["fixed_reset_times"] = frd;
        expected["fixed_pay_times"] = fpd;
        expected["floating_reset_times"] = flrd;
        expected["floating_pay_times"] = flpd;
    }

    out.addCase("scan", inputs, expected);
    out.write();
    return 0;
}
