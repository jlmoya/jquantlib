// migration-harness/cpp/probes/heston-engines/piecewise_time_dependent_heston_model_probe.cpp
//
// Probe for Phase 5h.5 — PiecewiseTimeDependentHestonModel parameter accessors.
// Verifies the time-grid-piecewise-constant parameter evaluation is bit-exact
// vs Java port.
//
// Tolerance tier: TIGHT (1e-15 abs) — pure parameter lookup, no math.

#include <ql/version.hpp>

#include <iomanip>
#include <sstream>
#include <vector>
#include <ql/quotes/simplequote.hpp>
#include <ql/settings.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/time/calendars/nullcalendar.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>
#include <ql/timegrid.hpp>
#include <ql/models/equity/piecewisetimedependenthestonmodel.hpp>
#include <ql/models/parameter.hpp>

#include "../common.hpp"

using namespace QuantLib;
using namespace jqml_harness;

int main() {
    ReferenceWriter out("heston-engines/piecewise_time_dependent_heston_model",
                        QL_VERSION,
                        "piecewise_time_dependent_heston_model_probe");

    const Date eval(15, January, 2026);
    Settings::instance().evaluationDate() = eval;
    const DayCounter dc = Actual365Fixed();

    const Handle<Quote> s0(ext::make_shared<SimpleQuote>(100.0));
    const Handle<YieldTermStructure> rTS(
        ext::make_shared<FlatForward>(eval, 0.05, dc, Continuous, Annual));
    const Handle<YieldTermStructure> qTS(
        ext::make_shared<FlatForward>(eval, 0.02, dc, Continuous, Annual));

    // 4-step time grid covering [0, 2y]
    std::vector<Time> grid = {0.0, 0.5, 1.0, 1.5, 2.0};
    TimeGrid tg(grid.begin(), grid.end());

    // Piecewise parameters: evaluate to constant at each segment.
    // For PiecewiseConstantParameter the value is the i-th segment value at time t in [grid[i], grid[i+1]).
    PiecewiseConstantParameter theta(grid, PositiveConstraint());
    theta.setParam(0, 0.04);
    theta.setParam(1, 0.05);
    theta.setParam(2, 0.06);
    theta.setParam(3, 0.07);
    theta.setParam(4, 0.08);  // last segment beyond grid

    PiecewiseConstantParameter kappa(grid, PositiveConstraint());
    kappa.setParam(0, 1.0);
    kappa.setParam(1, 1.1);
    kappa.setParam(2, 1.2);
    kappa.setParam(3, 1.3);
    kappa.setParam(4, 1.4);

    PiecewiseConstantParameter sigma(grid, PositiveConstraint());
    sigma.setParam(0, 0.30);
    sigma.setParam(1, 0.32);
    sigma.setParam(2, 0.34);
    sigma.setParam(3, 0.36);
    sigma.setParam(4, 0.38);

    PiecewiseConstantParameter rho(grid, BoundaryConstraint(-1.0, 1.0));
    rho.setParam(0, 0.20);
    rho.setParam(1, 0.22);
    rho.setParam(2, 0.24);
    rho.setParam(3, 0.26);
    rho.setParam(4, 0.28);

    const double v0 = 0.04;

    PiecewiseTimeDependentHestonModel model(
        rTS, qTS, s0, v0,
        theta, kappa, sigma, rho, tg);

    // Sample at distinct times within each segment + at exact boundaries
    const std::vector<double> sample_t = {
        0.0, 0.25, 0.49, 0.5, 0.75, 0.99, 1.0, 1.25, 1.49, 1.5, 1.75, 1.99, 2.0, 2.5
    };

    for (double t : sample_t) {
        json inputs = { {"t", t} };
        json expected = {
            {"theta", model.theta(t)},
            {"kappa", model.kappa(t)},
            {"sigma", model.sigma(t)},
            {"rho",   model.rho(t)}
        };
        std::ostringstream name;
        name << "sample_t_" << std::fixed << std::setprecision(2) << t;
        out.addCase(name.str(), inputs, expected);
    }

    // v0 should be constant
    json v0Inputs = json::object();
    json v0Expected = { {"v0", model.v0()}, {"s0", model.s0()} };
    out.addCase("constants", v0Inputs, v0Expected);

    out.write();
    return 0;
}
