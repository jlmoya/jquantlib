// migration-harness/cpp/probes/model/shortrate/twofactormodels/g2_probe.cpp
// Reference values for G2++ closed-form discount, discountBondOption, and
// the 2D tree fingerprint. Phase 2e WI-1: cross-validates the freshly
// ported G2 body (closed-form analytics + Dynamics + FittingParameter)
// against C++ v1.42.1.

#include <ql/version.hpp>
#include <ql/exercise.hpp>
#include <ql/indexes/ibor/euribor.hpp>
#include <ql/instruments/swaption.hpp>
#include <ql/instruments/vanillaswap.hpp>
#include <ql/models/shortrate/twofactormodels/g2.hpp>
#include <ql/pricingengines/swap/discountingswapengine.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/time/calendars/nullcalendar.hpp>
#include <ql/time/calendars/target.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>
#include <ql/time/daycounters/thirty360.hpp>
#include <ql/time/schedule.hpp>
#include <ql/timegrid.hpp>
#include "../../../common.hpp"

#include <vector>

using namespace QuantLib;
using namespace jqml_harness;

int main() {
    ReferenceWriter out("model/shortrate/twofactormodels/g2", QL_VERSION,
                        "g2_probe");

    Settings::instance().evaluationDate() = Date(15, January, 2026);
    Handle<YieldTermStructure> ts(
        ext::make_shared<FlatForward>(Date(15, January, 2026), 0.05, Actual365Fixed()));

    const Real a = 0.1, sigma = 0.01, b = 0.1, eta = 0.005, rho = -0.5;
    G2 model(ts, a, sigma, b, eta, rho);

    // ----- Closed-form discount fingerprint (model.discount(t)) -----
    json discArr = json::array();
    for (Time t : {0.5, 1.0, 2.0, 5.0, 10.0}) {
        discArr.push_back({{"t", t}, {"discount", model.discount(t)}});
    }
    out.addCase("g2_discount_fingerprint",
        json{{"r_curve", 0.05}, {"a", a}, {"sigma", sigma},
             {"b", b}, {"eta", eta}, {"rho", rho}},
        json{{"samples", discArr}});

    // ----- discountBondOption(Call, k, 5.0, 10.0) -----
    json optArr = json::array();
    for (Real k : {0.95, 1.0, 1.05}) {
        optArr.push_back({{"strike", k}, {"maturity", 5.0}, {"bondMaturity", 10.0},
            {"call", model.discountBondOption(Option::Call, k, 5.0, 10.0)},
            {"put",  model.discountBondOption(Option::Put,  k, 5.0, 10.0)}});
    }
    out.addCase("g2_discountBondOption_fingerprint",
        json{{"r_curve", 0.05}, {"a", a}, {"sigma", sigma},
             {"b", b}, {"eta", eta}, {"rho", rho}},
        json{{"samples", optArr}});

    // ----- 2D tree fingerprint -----
    // TimeGrid(end=10.0, steps=5). Capture tree.discount(i, index) over
    // the full 2D state (index spans tree1.size(i) * tree2.size(i)).
    // Cast to TwoFactorModel::ShortRateTree to access the discount(i, j)
    // member (Lattice base only exposes timeGrid()).
    {
        TimeGrid grid(/*end*/10.0, /*steps*/5);
        auto lattice = model.tree(grid);
        auto tree = ext::dynamic_pointer_cast<TwoFactorModel::ShortRateTree>(lattice);

        // Walk i = 0 .. grid.size()-2: the terminal grid node has
        // no dt(i) defined (TimeGrid stores size()-1 dt values), so
        // discount(size-1, ...) is UB on the C++ side. The Java
        // ShortRateTree.discount mirrors the same dt(i) read and
        // throws out-of-bounds. Match the BK / HullWhite tree-probe
        // convention: skip the terminal cell.
        json treeArr = json::array();
        for (Size i = 0; i + 1 < grid.size(); ++i) {
            const Size sz = tree->size(i);
            for (Size index = 0; index < sz; ++index) {
                treeArr.push_back({{"i", i}, {"index", index},
                                   {"discount", tree->discount(i, index)}});
            }
        }
        out.addCase("g2_tree_fingerprint",
            json{{"r_curve", 0.05}, {"a", a}, {"sigma", sigma},
                 {"b", b}, {"eta", eta}, {"rho", rho},
                 {"grid_end", 10.0}, {"grid_steps", 5}},
            json{{"samples", treeArr}});
    }

    // ----- G2.swaption(arguments, fixedRate, range, intervals) -----
    // Phase 2f WI-2 carve from Phase 2e A11. Fixture: 5Y x 5Y ATM payer
    // swaption (mirrors the BlackSwaptionEngine probe schedule shape but
    // priced via the G2 integral path on the same flat 5% curve).
    {
        const Date eval = Date(15, January, 2026);
        Settings::instance().evaluationDate() = eval;

        const DayCounter dc = Actual365Fixed();
        const Calendar cal = TARGET();

        const Handle<YieldTermStructure> tsSwap(
            ext::make_shared<FlatForward>(eval, 0.05, dc, Continuous, Annual));

        const auto idx = ext::make_shared<Euribor3M>(tsSwap);

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
        swap0->setPricingEngine(ext::make_shared<DiscountingSwapEngine>(tsSwap));
        const Rate atmRate = swap0->fairRate();

        auto swap = ext::make_shared<VanillaSwap>(
            VanillaSwap::Payer, nominal, fixedSchedule, atmRate, fixedDc,
            floatSchedule, idx, 0.0, dc);

        Swaption swaption(swap, exercise);
        Swaption::arguments swaptionArgs;
        swaption.setupArguments(&swaptionArgs);

        // Build a fresh G2 against the same curve so fitting parameter and
        // integration both see the same yields the Java probe consumes.
        G2 modelSw(tsSwap, a, sigma, b, eta, rho);

        const Real range = 5.0;
        const Size intervals = 50;
        const Real swaptionPrice = modelSw.swaption(swaptionArgs, atmRate,
                                                    range, intervals);

        out.addCase("g2_swaption_integral_fingerprint",
            json{{"r_curve", 0.05}, {"a", a}, {"sigma", sigma},
                 {"b", b}, {"eta", eta}, {"rho", rho},
                 {"nominal", nominal}, {"dummy_fixed_rate", dummyRate},
                 {"exercise_years", 5}, {"swap_years", 5},
                 {"fixed_freq", "Annual"}, {"float_tenor_months", 3},
                 {"fixed_day_counter", "30/360 European"},
                 {"yts_day_counter", "Actual/365 Fixed"},
                 {"calendar", "TARGET"}, {"index", "Euribor3M"},
                 {"range", range}, {"intervals", intervals}},
            json{{"atm_rate", atmRate},
                 {"swaption_integral", swaptionPrice}});
    }

    out.write();
    return 0;
}
