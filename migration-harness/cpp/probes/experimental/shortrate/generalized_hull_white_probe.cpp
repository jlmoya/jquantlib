// migration-harness/cpp/probes/experimental/shortrate/generalized_hull_white_probe.cpp
// Reference values for GeneralizedHullWhite analytical formulas
// (ql/experimental/shortrate/generalizedhullwhite.{hpp,cpp}).
//
// QuantLib has no dedicated test for GeneralizedHullWhite. We exercise:
//   - the classical-mode (constant a, sigma) constructor
//   - B(t,T), V(0,t), discount-bond price and discount-bond-option price
//   - parity with the standard HullWhite at the same a, sigma (sanity)
//   - the piecewise-linear constructor in a known-flat configuration

#include <cstdio>
#include <ql/version.hpp>
#include "../../common.hpp"

#include <ql/experimental/shortrate/generalizedhullwhite.hpp>
#include <ql/models/shortrate/onefactormodels/hullwhite.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/time/calendars/nullcalendar.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>

using namespace jqml_harness;
using namespace QuantLib;

int main() {
    ReferenceWriter out("experimental/shortrate/generalized_hull_white",
                        QL_VERSION,
                        "generalized_hull_white_probe");

    Date today(15, February, 2002);
    Settings::instance().evaluationDate() = today;
    DayCounter dc = Actual365Fixed();

    Handle<YieldTermStructure> ts(ext::shared_ptr<YieldTermStructure>(
        new FlatForward(today, 0.05, dc)));

    // Scenario A: classical-mode GeneralizedHullWhite vs standard HullWhite.
    {
        Real a = 0.05, sigma = 0.01;
        ext::shared_ptr<GeneralizedHullWhite> ghw(new GeneralizedHullWhite(ts, a, sigma));
        ext::shared_ptr<HullWhite> hw(new HullWhite(ts, a, sigma));

        struct Pt { Time t; Time T; };
        std::vector<Pt> pts = {{0.0, 1.0}, {0.0, 5.0}, {1.0, 5.0}, {2.0, 10.0}};
        for (auto const& p : pts) {
            Real ghw_disc = ghw->discountBond(p.t, p.T, 0.04);
            Real hw_disc  = hw->discountBond(p.t, p.T, 0.04);

            char name[64];
            std::snprintf(name, sizeof(name), "classical_disc_t%.1f_T%.1f", p.t, p.T);
            json inp = {
                {"a", a}, {"sigma", sigma},
                {"t", p.t}, {"T", p.T}, {"r", 0.04}
            };
            json expected = {
                {"ghw_discount_bond", ghw_disc},
                {"hw_discount_bond", hw_disc}
            };
            out.addCase(name, inp, expected);
        }

        // Discount-bond-option prices: maturity 1y, bond_maturity 5y, strike 0.6.
        Real opt_call = ghw->discountBondOption(Option::Call, 0.6, 1.0, 5.0);
        Real opt_put  = ghw->discountBondOption(Option::Put,  0.6, 1.0, 5.0);
        out.addCase("classical_dbo_strike_0.6_T_1_to_5",
            { {"a", a}, {"sigma", sigma}, {"strike", 0.6},
              {"maturity", 1.0}, {"bondMaturity", 5.0} },
            { {"call", opt_call}, {"put", opt_put} });
    }

    // Scenario B: piecewise constructor with a single segment (degenerates to
    // a constant-coefficient model). Sanity check that it agrees with the
    // classical mode.
    {
        Real a = 0.07, sigma = 0.012;
        std::vector<Date> ds = {today};
        std::vector<Real> aVec = {a};
        std::vector<Real> volVec = {sigma};
        ext::shared_ptr<GeneralizedHullWhite> ghw(
            new GeneralizedHullWhite(ts, ds, ds, aVec, volVec));

        Real disc = ghw->discountBond(0.0, 3.0, 0.05);
        Real opt_call = ghw->discountBondOption(Option::Call, 0.7, 1.0, 4.0);
        out.addCase("piecewise_single_segment",
            { {"a", a}, {"sigma", sigma},
              {"r", 0.05}, {"t", 0.0}, {"T", 3.0},
              {"strike", 0.7}, {"maturity", 1.0}, {"bondMaturity", 4.0} },
            { {"discount_bond", disc}, {"call", opt_call} });
    }

    // Scenario C: piecewise with two distinct segments, just emit
    // discount-bond-option price (no oracle, only consistency).
    {
        std::vector<Date> ds = {today, today + 365 * 5};
        std::vector<Real> aVec = {0.05, 0.10};
        std::vector<Real> volVec = {0.01, 0.02};
        ext::shared_ptr<GeneralizedHullWhite> ghw(
            new GeneralizedHullWhite(ts, ds, ds, aVec, volVec));

        Real opt = ghw->discountBondOption(Option::Call, 0.7, 2.0, 6.0);
        out.addCase("piecewise_two_segments",
            { {"speed_t0", 0.05}, {"speed_t5", 0.10},
              {"vol_t0", 0.01}, {"vol_t5", 0.02},
              {"strike", 0.7}, {"maturity", 2.0}, {"bondMaturity", 6.0} },
            { {"call", opt} });
    }

    out.write();
    return 0;
}
