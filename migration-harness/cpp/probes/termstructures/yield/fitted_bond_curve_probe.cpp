// Reference values for FittedBondDiscountCurve fitting-method discount
// functions (parametric / no-fit mode) against QuantLib v1.42.1.
// Phase 5d.5-ZCS+FB.
//
// Exercises the discountFunction(x, t) of:
//   - NelsonSiegelFitting (4 params)
//   - SvenssonFitting     (6 params)
//   - SimplePolynomialFitting (degree N)
// at a small grid of t values. We use the parametric (no-fit) constructor
// of FittedBondDiscountCurve so that the optimizer does not run; the curve
// becomes a pure evaluator of the chosen fitting function.

#include <cstdio>
#include <ql/version.hpp>
#include "../../common.hpp"

#include <ql/math/array.hpp>
#include <ql/termstructures/yield/fittedbonddiscountcurve.hpp>
#include <ql/termstructures/yield/nonlinearfittingmethods.hpp>
#include <ql/time/calendars/target.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>

using namespace jqml_harness;
using namespace QuantLib;

namespace {

json arrayOf(const std::vector<double>& v) {
    json a = json::array();
    for (double x : v) a.push_back(x);
    return a;
}

} // namespace

int main() {
    ReferenceWriter out("termstructures/yield/fitted_bond_curve",
                        QL_VERSION, "fitted_bond_curve_probe");

    Date today(15, July, 2019);
    Settings::instance().evaluationDate() = today;
    DayCounter dc = Actual365Fixed();
    Date maxDate = today + Period(20, Years);

    const std::vector<double> grid = {0.5, 1.0, 2.0, 3.0, 5.0, 7.0, 10.0, 15.0, 19.0};

    // ---------- Nelson-Siegel ----------
    {
        // Typical NS params: c0=long rate, c1=slope, c2=curvature, kappa=decay
        Array params(4);
        params[0] = 0.04;
        params[1] = -0.02;
        params[2] = 0.01;
        params[3] = 0.5;

        NelsonSiegelFitting fit;
        FittedBondDiscountCurve curve(today, fit, params, maxDate, dc);

        std::vector<double> dfs;
        for (double t : grid) dfs.push_back(curve.discount(t));

        json inputs = {
            {"description", "NelsonSiegel parametric: c0=0.04, c1=-0.02, c2=0.01, kappa=0.5"},
            {"params", arrayOf({0.04, -0.02, 0.01, 0.5})},
            {"grid", arrayOf(grid)}
        };
        out.addCase("nelson_siegel_basic", inputs, json{{"discounts", arrayOf(dfs)}});
    }

    // ---------- Svensson ----------
    {
        Array params(6);
        params[0] = 0.04;
        params[1] = -0.02;
        params[2] = 0.01;
        params[3] = 0.005;
        params[4] = 0.5;
        params[5] = 0.2;

        SvenssonFitting fit;
        FittedBondDiscountCurve curve(today, fit, params, maxDate, dc);

        std::vector<double> dfs;
        for (double t : grid) dfs.push_back(curve.discount(t));

        json inputs = {
            {"description", "Svensson parametric: c0=0.04, c1=-0.02, c2=0.01, c3=0.005, k=0.5, k1=0.2"},
            {"params", arrayOf({0.04, -0.02, 0.01, 0.005, 0.5, 0.2})},
            {"grid", arrayOf(grid)}
        };
        out.addCase("svensson_basic", inputs, json{{"discounts", arrayOf(dfs)}});
    }

    // ---------- SimplePolynomial degree=3 (constrainAtZero=true → 3 free coeffs, d(0)=1) ----------
    {
        // d(t) = 1 + c0*B(1,1,t) + c1*B(2,2,t) + c2*B(3,3,t)
        Array params(3);
        params[0] = -0.05;
        params[1] = 0.01;
        params[2] = -0.001;

        SimplePolynomialFitting fit(3, true);
        FittedBondDiscountCurve curve(today, fit, params, maxDate, dc);

        std::vector<double> dfs;
        for (double t : grid) dfs.push_back(curve.discount(t));

        json inputs = {
            {"description", "SimplePolynomial degree=3 constrainAtZero=true"},
            {"degree", 3},
            {"constrainAtZero", true},
            {"params", arrayOf({-0.05, 0.01, -0.001})},
            {"grid", arrayOf(grid)}
        };
        out.addCase("simple_polynomial_d3_constrained", inputs, json{{"discounts", arrayOf(dfs)}});
    }

    // ---------- SimplePolynomial degree=2 (constrainAtZero=false → 3 coeffs, no constraint) ----------
    {
        // d(t) = c0*B(0,0,t) + c1*B(1,1,t) + c2*B(2,2,t)
        Array params(3);
        params[0] = 1.0;
        params[1] = 0.95;
        params[2] = 0.85;

        SimplePolynomialFitting fit(2, false);
        FittedBondDiscountCurve curve(today, fit, params, maxDate, dc);

        std::vector<double> dfs;
        for (double t : grid) dfs.push_back(curve.discount(t));

        json inputs = {
            {"description", "SimplePolynomial degree=2 constrainAtZero=false"},
            {"degree", 2},
            {"constrainAtZero", false},
            {"params", arrayOf({1.0, 0.95, 0.85})},
            {"grid", arrayOf(grid)}
        };
        out.addCase("simple_polynomial_d2_unconstrained", inputs, json{{"discounts", arrayOf(dfs)}});
    }

    out.write();
    return 0;
}
