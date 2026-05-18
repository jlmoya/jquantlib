// Diagnostic probe to investigate Java vs C++ Bermudan tree divergence.
// Phase 5e.5b-CFC-d-207.

#include <ql/version.hpp>
#include <ql/cashflows/coupon.hpp>
#include <ql/exercise.hpp>
#include <ql/indexes/ibor/euribor.hpp>
#include <ql/instruments/swaption.hpp>
#include <ql/instruments/vanillaswap.hpp>
#include <ql/models/shortrate/onefactormodels/hullwhite.hpp>
#include <ql/pricingengines/swap/discountingswapengine.hpp>
#include <ql/pricingengines/swaption/treeswaptionengine.hpp>
#include <ql/settings.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>
#include <ql/time/daycounters/thirty360.hpp>
#include <ql/time/schedule.hpp>

#include "common.hpp"

using namespace QuantLib;
using namespace jqml_harness;

int main() {
    ReferenceWriter out("instruments/bermudan_tree_diag",
                        QL_VERSION, "bermudan_tree_diag_probe");

    Settings::instance().evaluationDate() = Date(15, February, 2002);
    RelinkableHandle<YieldTermStructure> ts;
    auto idx = ext::make_shared<Euribor6M>(ts);
    Calendar cal = idx->fixingCalendar();
    Date settle(19, February, 2002);
    ts.linkTo(ext::make_shared<FlatForward>(settle, 0.04875825, Actual365Fixed()));

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

    auto hw = ext::make_shared<HullWhite>(ts, 0.048696, 0.0058904);
    auto eng = ext::make_shared<TreeSwaptionEngine>(hw, 50);

    json inputs = {{"fixture", "Phase5e.5b-CFC-d-207 diag"}};
    json expected;

    for (size_t n = 1; n <= exDates.size(); n++) {
        std::vector<Date> subset(exDates.begin(), exDates.begin() + n);
        auto exer = ext::make_shared<BermudanExercise>(subset);
        Swaption s(atmSwap, exer);
        s.setPricingEngine(eng);
        std::string k = "berm_" + std::to_string(n) + "ex";
        expected[k] = s.NPV();
    }

    out.addCase("scan", inputs, expected);
    out.write();
    return 0;
}
