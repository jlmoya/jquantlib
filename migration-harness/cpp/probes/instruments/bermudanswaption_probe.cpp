// migration-harness/cpp/probes/instruments/bermudanswaption_probe.cpp
//
// Probe for Phase 5e.5b-CFC-d-39 (BermudanSwaptionTest body-fill).
//
// Cross-validates the cached NPVs that QuantLib v1.42.1
// test-suite/bermudanswaption.cpp asserts in
//   - testCachedValues   (HW Bermudan, 1y x 5y, two exercise schedules)
//   - testCachedG2Values (G2 Bermudan, 1y x 5y, 5 strikes, tree + FD)
//
// The cached values hardcoded in the C++ test (e.g. itmValue=42.2402) are
// shipped under "usingAtParCoupons=false" (the modern v1.42.1 default), and
// were targeted only at 1e-4 tolerance. We re-emit them via this probe so
// the Java test can pin at TIGHT (1e-12 rel / 1e-14 abs) against a fresh
// run from the pinned v1.42.1 SHA.

#include <ql/version.hpp>

#include <ql/cashflows/coupon.hpp>
#include <ql/cashflows/iborcoupon.hpp>
#include <ql/exercise.hpp>
#include <ql/indexes/ibor/euribor.hpp>
#include <ql/instruments/swaption.hpp>
#include <ql/instruments/vanillaswap.hpp>
#include <ql/models/shortrate/onefactormodels/hullwhite.hpp>
#include <ql/models/shortrate/twofactormodels/g2.hpp>
#include <ql/pricingengines/swap/discountingswapengine.hpp>
#include <ql/pricingengines/swaption/fdg2swaptionengine.hpp>
#include <ql/pricingengines/swaption/fdhullwhiteswaptionengine.hpp>
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

namespace {

struct CommonVars {
    Date today, settlement;
    Calendar calendar;
    Integer startYears = 1;
    Integer length = 5;
    Swap::Type type = Swap::Payer;
    Real nominal = 1000.0;
    BusinessDayConvention fixedConvention = Unadjusted;
    BusinessDayConvention floatingConvention = ModifiedFollowing;
    Frequency fixedFrequency = Annual;
    Frequency floatingFrequency = Semiannual;
    DayCounter fixedDayCount = Thirty360(Thirty360::BondBasis);
    ext::shared_ptr<IborIndex> index;
    Natural settlementDays = 2;
    RelinkableHandle<YieldTermStructure> termStructure;

    CommonVars(const Date& evalDate, const Date& settle, Real flatRate) {
        index = ext::make_shared<Euribor6M>(termStructure);
        calendar = index->fixingCalendar();
        today = evalDate;
        settlement = settle;
        Settings::instance().evaluationDate() = today;
        termStructure.linkTo(
            ext::make_shared<FlatForward>(settlement, flatRate, Actual365Fixed()));
    }

    ext::shared_ptr<VanillaSwap> makeSwap(Rate fixedRate) const {
        Date start = calendar.advance(settlement, startYears, Years);
        Date maturity = calendar.advance(start, length, Years);
        Schedule fixedSchedule(start, maturity, Period(fixedFrequency),
                               calendar, fixedConvention, fixedConvention,
                               DateGeneration::Forward, false);
        Schedule floatSchedule(start, maturity, Period(floatingFrequency),
                               calendar, floatingConvention, floatingConvention,
                               DateGeneration::Forward, false);
        auto swap = ext::make_shared<VanillaSwap>(
            type, nominal, fixedSchedule, fixedRate, fixedDayCount,
            floatSchedule, index, 0.0, index->dayCounter());
        swap->setPricingEngine(
            ext::make_shared<DiscountingSwapEngine>(termStructure));
        return swap;
    }
};

} // namespace

int main() {
    ReferenceWriter out("instruments/bermudan_swaption",
                        QL_VERSION, "bermudanswaption_probe");

    // ------------------------------------------------------------------
    // Case 1: testCachedValues  — Bermudan HW (tree + FDM), 2002-02-15
    // ------------------------------------------------------------------
    {
        CommonVars vars(Date(15, February, 2002),
                        Date(19, February, 2002),
                        0.04875825);

        const Rate atmRate = vars.makeSwap(0.0)->fairRate();
        auto itmSwap = vars.makeSwap(0.8 * atmRate);
        auto atmSwap = vars.makeSwap(atmRate);
        auto otmSwap = vars.makeSwap(1.2 * atmRate);

        const Real a = 0.048696, sigma = 0.0058904;
        auto model = ext::make_shared<HullWhite>(vars.termStructure, a, sigma);

        std::vector<Date> exerciseDates;
        for (const auto& cf : atmSwap->fixedLeg()) {
            auto coupon = ext::dynamic_pointer_cast<Coupon>(cf);
            exerciseDates.push_back(coupon->accrualStartDate());
        }
        auto exercise = ext::make_shared<BermudanExercise>(exerciseDates);

        auto treeEngine = ext::make_shared<TreeSwaptionEngine>(model, 50);
        auto fdmEngine  = ext::make_shared<FdHullWhiteSwaptionEngine>(model);

        // --- aligned exercise dates (= accrual-start)
        Real itmTree, atmTree, otmTree;
        Real itmFdm,  atmFdm,  otmFdm;
        {
            Swaption s(itmSwap, exercise);
            s.setPricingEngine(treeEngine); itmTree = s.NPV();
            s.setPricingEngine(fdmEngine);  itmFdm  = s.NPV();
        }
        {
            Swaption s(atmSwap, exercise);
            s.setPricingEngine(treeEngine); atmTree = s.NPV();
            s.setPricingEngine(fdmEngine);  atmFdm  = s.NPV();
        }
        {
            Swaption s(otmSwap, exercise);
            s.setPricingEngine(treeEngine); otmTree = s.NPV();
            s.setPricingEngine(fdmEngine);  otmFdm  = s.NPV();
        }

        // --- shifted exercise dates (= accrual-start - 10 days, calendar-adjusted)
        std::vector<Date> shiftedDates = exerciseDates;
        for (auto& d : shiftedDates) {
            d = vars.calendar.adjust(d - 10);
        }
        auto exerciseShifted =
            ext::make_shared<BermudanExercise>(shiftedDates);

        Real itmTreeShifted, atmTreeShifted, otmTreeShifted;
        {
            Swaption s(itmSwap, exerciseShifted);
            s.setPricingEngine(treeEngine); itmTreeShifted = s.NPV();
        }
        {
            Swaption s(atmSwap, exerciseShifted);
            s.setPricingEngine(treeEngine); atmTreeShifted = s.NPV();
        }
        {
            Swaption s(otmSwap, exerciseShifted);
            s.setPricingEngine(treeEngine); otmTreeShifted = s.NPV();
        }

        json inputs = {
            {"eval_date", "2002-02-15"},
            {"settlement", "2002-02-19"},
            {"flat_rate", 0.04875825},
            {"index", "Euribor6M"},
            {"calendar", "TARGET"},
            {"nominal", 1000.0},
            {"start_years", 1},
            {"length_years", 5},
            {"hw_a", a},
            {"hw_sigma", sigma},
            {"time_steps", 50},
            {"fixed_freq", "Annual"},
            {"float_freq", "Semiannual"},
            {"fixed_day_counter", "30/360 BondBasis"},
            {"yts_day_counter", "Actual/365 Fixed"}
        };
        json expected = {
            {"atm_rate", atmRate},
            {"itm_tree", itmTree},
            {"atm_tree", atmTree},
            {"otm_tree", otmTree},
            {"itm_fdm",  itmFdm},
            {"atm_fdm",  atmFdm},
            {"otm_fdm",  otmFdm},
            {"itm_tree_shifted", itmTreeShifted},
            {"atm_tree_shifted", atmTreeShifted},
            {"otm_tree_shifted", otmTreeShifted}
        };
        out.addCase("cached_hw", inputs, expected);
    }

    // ------------------------------------------------------------------
    // Case 2: testCachedG2Values  — Bermudan G2 (tree + FDM), 2016-09-15
    // ------------------------------------------------------------------
    {
        CommonVars vars(Date(15, September, 2016),
                        Date(19, September, 2016),
                        0.04875825);

        const Rate atmRate = vars.makeSwap(0.0)->fairRate();

        std::vector<ext::shared_ptr<Swaption> > swaptions;
        std::vector<Real> strikeMultipliers;
        for (Real s = 0.5; s < 1.51; s += 0.25) {
            strikeMultipliers.push_back(s);
            auto swap = vars.makeSwap(s * atmRate);
            std::vector<Date> exerciseDates;
            for (const auto& cf : swap->fixedLeg()) {
                exerciseDates.push_back(
                    ext::dynamic_pointer_cast<Coupon>(cf)->accrualStartDate());
            }
            swaptions.push_back(ext::make_shared<Swaption>(
                swap, ext::make_shared<BermudanExercise>(exerciseDates)));
        }

        const Real a = 0.1, sigma = 0.01, b = 0.2, eta = 0.013, rho = -0.5;
        auto g2 = ext::make_shared<G2>(vars.termStructure, a, sigma, b, eta, rho);
        auto fdmEngine  = ext::make_shared<FdG2SwaptionEngine>(g2, 50, 75, 75, 0, 1e-3);
        auto treeEngine = ext::make_shared<TreeSwaptionEngine>(g2, 50);

        std::vector<Real> fdmVals;
        std::vector<Real> treeVals;
        for (auto& s : swaptions) {
            s->setPricingEngine(fdmEngine);
            fdmVals.push_back(s->NPV());
            s->setPricingEngine(treeEngine);
            treeVals.push_back(s->NPV());
        }

        json inputs = {
            {"eval_date", "2016-09-15"},
            {"settlement", "2016-09-19"},
            {"flat_rate", 0.04875825},
            {"index", "Euribor6M"},
            {"calendar", "TARGET"},
            {"nominal", 1000.0},
            {"start_years", 1},
            {"length_years", 5},
            {"g2_a", a},
            {"g2_sigma", sigma},
            {"g2_b", b},
            {"g2_eta", eta},
            {"g2_rho", rho},
            {"fd_t_grid", 50},
            {"fd_x_grid", 75},
            {"fd_y_grid", 75},
            {"fd_damping_steps", 0},
            {"fd_inv_eps", 1e-3},
            {"tree_time_steps", 50},
            {"strike_multipliers", strikeMultipliers}
        };
        json expected = {
            {"atm_rate", atmRate},
            {"fdm",  fdmVals},
            {"tree", treeVals}
        };
        out.addCase("cached_g2", inputs, expected);
    }

    out.write();
    return 0;
}
