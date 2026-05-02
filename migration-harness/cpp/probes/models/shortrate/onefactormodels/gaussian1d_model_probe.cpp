// migration-harness/cpp/probes/models/shortrate/onefactormodels/gaussian1d_model_probe.cpp
// Phase 2j WI-1.1 — emit Gaussian1dModel base behaviors via a Gsr instance.
// Oracle: C++ QuantLib v1.42.1 Gsr (which inherits Gaussian1dModel).

#include <ql/version.hpp>
#include "../../../common.hpp"

#include <ql/models/shortrate/onefactormodels/gsr.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/time/calendars/target.hpp>
#include <ql/time/daycounters/actual360.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/indexes/swap/euriborswap.hpp>

#include <vector>
#include <cstdio>

using namespace jqml_harness;
using namespace QuantLib;

int main() {
    ReferenceWriter out("models/shortrate/onefactormodels/gaussian1d_model",
                        QL_VERSION, "gaussian1d_model_probe");

    const Date today(15, May, 2026);
    Settings::instance().evaluationDate() = today;
    Handle<YieldTermStructure> yts(ext::shared_ptr<YieldTermStructure>(
        new FlatForward(today, 0.03, Actual360())));

    std::vector<Date> volStepDates;
    volStepDates.push_back(today + Period(1, Years));
    volStepDates.push_back(today + Period(2, Years));
    std::vector<Real> volatilities;
    volatilities.push_back(0.01);
    volatilities.push_back(0.012);
    volatilities.push_back(0.015);
    Real reversion = 0.01;

    ext::shared_ptr<Gsr> gsr(new Gsr(yts, volStepDates, volatilities, reversion));

    // Forward-measure conversion fingerprints
    int idx = 0;
    Real ts[] = {0.5, 1.0, 1.5, 2.0, 3.0};
    Real xs[] = {-0.02, -0.01, 0.0, 0.01, 0.02, 0.03};
    Real T_offsets[] = {0.5, 1.0, 2.0, 5.0};
    for (Real t : ts) {
        for (Real x : xs) {
            for (Real T_off : T_offsets) {
                Real T = t + T_off;
                Real n = gsr->numeraire(t, x);
                Real z = gsr->zerobond(T, t, x);
                char nm[48]; std::snprintf(nm, sizeof nm, "fm_%03d", idx++);
                out.addCase(nm,
                    json{{"t", t}, {"x", x}, {"T", T}},
                    json{{"numeraire", n}, {"zerobond", z}});
            }
        }
    }

    // Standard swap rate at (fixingDate, swapTenor, x). The C++ swapRate signature
    // is swapRate(fixing, tenor, referenceDate, y, swapIdx). We pass referenceDate
    // = Date() (default), so the model curve is used.
    ext::shared_ptr<SwapIndex> swapIdx(new EuriborSwapIsdaFixA(2 * Years, yts));
    int sridx = 0;
    Real sr_xs[] = {-0.01, 0.0, 0.01};
    Period sr_expiries[] = {Period(1, Years), Period(3, Years), Period(5, Years)};
    Period sr_tenors[]  = {Period(1, Years), Period(2, Years), Period(5, Years)};
    for (Real x : sr_xs) {
        for (Period exp_p : sr_expiries) {
            // Adjust fixing date to a valid business day on the swap index calendar
            Date fixing = swapIdx->fixingCalendar().adjust(today + exp_p);
            for (Period ten : sr_tenors) {
                Real sr = gsr->swapRate(fixing, ten, Date(), x, swapIdx);
                Real sa = gsr->swapAnnuity(fixing, ten, Date(), x, swapIdx);
                char nm[48]; std::snprintf(nm, sizeof nm, "sr_%02d", sridx++);
                out.addCase(nm,
                    json{{"x", x},
                         {"expiry_serial", static_cast<long>(fixing.serialNumber())},
                         {"tenor_units", static_cast<int>(ten.units())},
                         {"tenor_length", ten.length()}},
                    json{{"swap_rate", sr}, {"swap_annuity", sa}});
            }
        }
    }

    // Time-discretization mesh: numeraire(t) at uniformly-spaced t in [0, 5]
    Size n_steps = 20;
    Real T_max = 5.0;
    for (Size i = 0; i <= n_steps; ++i) {
        Real t_i = (T_max * i) / n_steps;
        Real n_i = gsr->numeraire(t_i, 0.0);
        char nm[32]; std::snprintf(nm, sizeof nm, "mesh_%02zu", i);
        out.addCase(nm, json{{"t", t_i}, {"x", 0.0}}, json{{"numeraire", n_i}});
    }

    // gaussianPolynomialIntegral fingerprints (static, pure-math base helper)
    {
        int gpi = 0;
        struct GP { Real a,b,c,d,e,x0,x1; };
        GP cases[] = {
            {0,0,0,0,1, -1.0, 1.0},        // constant=1: erf-like
            {0,0,0,1,0, 0.0, 2.0},         // linear
            {0,0,1,0,0, -2.0, 2.0},        // quadratic
            {1,0,0,0,0, 0.0, 1.0},         // quartic
            {0.5,-0.3,0.2,-0.1,0.05, -1.5, 2.5}, // mixed
            {1,1,1,1,1, -3.0, 3.0},        // all-ones
        };
        for (const auto& c : cases) {
            Real v = Gaussian1dModel::gaussianPolynomialIntegral(
                c.a, c.b, c.c, c.d, c.e, c.x0, c.x1);
            char nm[32]; std::snprintf(nm, sizeof nm, "gpi_%02d", gpi++);
            out.addCase(nm,
                json{{"a", c.a}, {"b", c.b}, {"c", c.c}, {"d", c.d}, {"e", c.e},
                     {"x0", c.x0}, {"x1", c.x1}},
                json{{"value", v}});
        }
    }

    // gaussianShiftedPolynomialIntegral fingerprints
    {
        int gspi = 0;
        struct GSP { Real a,b,c,d,e,h,x0,x1; };
        GSP cases[] = {
            {0,0,0,0,1, 0.5, -1.0, 1.0},
            {0,0,1,0,0, -0.5, -2.0, 2.0},
            {0.5,-0.3,0.2,-0.1,0.05, 0.7, -1.5, 2.5},
            {1,1,1,1,1, -1.0, -3.0, 3.0},
        };
        for (const auto& c : cases) {
            Real v = Gaussian1dModel::gaussianShiftedPolynomialIntegral(
                c.a, c.b, c.c, c.d, c.e, c.h, c.x0, c.x1);
            char nm[32]; std::snprintf(nm, sizeof nm, "gspi_%02d", gspi++);
            out.addCase(nm,
                json{{"a", c.a}, {"b", c.b}, {"c", c.c}, {"d", c.d}, {"e", c.e},
                     {"h", c.h}, {"x0", c.x0}, {"x1", c.x1}},
                json{{"value", v}});
        }
    }

    out.write();
    return 0;
}
