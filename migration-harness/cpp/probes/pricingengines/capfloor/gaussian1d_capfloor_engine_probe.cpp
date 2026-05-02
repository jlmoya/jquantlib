// migration-harness/cpp/probes/pricingengines/capfloor/gaussian1d_capfloor_engine_probe.cpp
//
// Phase 2j WI-2.2 — Gaussian1dCapFloorEngine NPV fingerprint.
// Oracle: C++ QuantLib v1.42.1.
//
// Builds a Gsr (vol-step) Gaussian1d model + Gaussian1dCapFloorEngine and
// prices cap/floor NPVs across a grid of:
//   - 3 engine variants (baseline, flat-extrapolation, no-extrapolation)
//   - 3 expiries (2Y, 4Y, 6Y)
//   - 2 types (Cap, Floor)
//   - 3 strikes (2%, 3%, 4%)
// Total: 3 × 3 × 2 × 3 = 54 cases.
//
// Fixture: eval=2026-01-15, FlatForward 3% Continuous Actual365Fixed on TARGET,
// Euribor3M index; leg starts 3M after eval (avoids past-fixing issues).
//
// Captured output: npv = CapFloor::NPV() under Gaussian1dCapFloorEngine.

#include <ql/version.hpp>

#include <ql/cashflows/iborcoupon.hpp>
#include <ql/indexes/ibor/euribor.hpp>
#include <ql/instruments/capfloor.hpp>
#include <ql/models/shortrate/onefactormodels/gsr.hpp>
#include <ql/pricingengines/capfloor/gaussian1dcapfloorengine.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/settings.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/time/calendars/target.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>
#include <ql/time/schedule.hpp>

#include "common.hpp"

#include <cstdio>
#include <vector>

using namespace QuantLib;
using namespace jqml_harness;

namespace {

// Build an IborLeg (Euribor3M) running from schedStart to eval+tenorYears.
ext::shared_ptr<CapFloor> makeCap(
    const Date& eval,
    const Calendar& cal,
    const ext::shared_ptr<IborIndex>& idx,
    const Date& schedStart,
    int tenorYears,
    Rate strike,
    Real nominal) {

    const Date end = cal.advance(schedStart, Period(tenorYears, Years));
    Schedule sched(schedStart, end, Period(3, Months), cal,
                   ModifiedFollowing, ModifiedFollowing,
                   DateGeneration::Forward, false);
    Leg leg = IborLeg(sched, idx)
        .withNotionals(std::vector<Real>(1, nominal))
        .withPaymentAdjustment(idx->businessDayConvention());
    return ext::make_shared<Cap>(leg, std::vector<Rate>(1, strike));
}

ext::shared_ptr<CapFloor> makeFloor(
    const Date& eval,
    const Calendar& cal,
    const ext::shared_ptr<IborIndex>& idx,
    const Date& schedStart,
    int tenorYears,
    Rate strike,
    Real nominal) {

    const Date end = cal.advance(schedStart, Period(tenorYears, Years));
    Schedule sched(schedStart, end, Period(3, Months), cal,
                   ModifiedFollowing, ModifiedFollowing,
                   DateGeneration::Forward, false);
    Leg leg = IborLeg(sched, idx)
        .withNotionals(std::vector<Real>(1, nominal))
        .withPaymentAdjustment(idx->businessDayConvention());
    return ext::make_shared<Floor>(leg, std::vector<Rate>(1, strike));
}

} // namespace

int main() {
    ReferenceWriter out("pricingengines/capfloor/gaussian1d_capfloor_engine",
                        QL_VERSION, "gaussian1d_capfloor_engine_probe");

    // --- Fixture -----------------------------------------------------------
    const Date eval(15, January, 2026);
    Settings::instance().evaluationDate() = eval;

    const DayCounter dc = Actual365Fixed();
    const Calendar cal = TARGET();
    const Real flatRate = 0.03;
    const Real reversion = 0.01;
    const Real nominal = 100.0;

    const Handle<YieldTermStructure> ts(
        ext::make_shared<FlatForward>(eval, flatRate, dc, Continuous, Annual));
    auto idx = ext::make_shared<Euribor3M>(ts);

    // Schedule starts 3M after eval so all fixing dates are in the future.
    const Date schedStart = cal.advance(eval, Period(3, Months));

    // Vol-step dates: 1Y, 2Y, 5Y after eval — gives 4 vol levels.
    std::vector<Date> volStepDates;
    volStepDates.push_back(eval + Period(1, Years));
    volStepDates.push_back(eval + Period(2, Years));
    volStepDates.push_back(eval + Period(5, Years));
    std::vector<Real> vols = {0.01, 0.012, 0.014, 0.016};

    auto gsr = ext::make_shared<Gsr>(ts, volStepDates, vols, reversion);

    // --- Engine variants ---------------------------------------------------
    struct EngDef {
        const char* tag;
        int integrationPoints;
        Real stddevs;
        bool extrapolatePayoff;
        bool flatPayoffExtrapolation;
    };
    EngDef engines[] = {
        // baseline (defaults: 64 pts, 7.0 sd, extrapolate=true, flat=false)
        {"e0",  64, 7.0, true,  false},
        // flat-extrapolation variant
        {"e1",  64, 7.0, true,  true},
        // no-extrapolation, narrower stddevs
        {"e2",  64, 5.0, false, false},
    };

    // --- Cases ------------------------------------------------------------
    // tenors (years from schedStart), types, strikes
    int tenors[] = {2, 4, 6};
    Rate strikes[] = {0.02, 0.03, 0.04};

    int caseIdx = 0;
    for (const auto& eng : engines) {
        auto engine = ext::make_shared<Gaussian1dCapFloorEngine>(
            gsr,
            eng.integrationPoints, eng.stddevs,
            eng.extrapolatePayoff, eng.flatPayoffExtrapolation,
            Handle<YieldTermStructure>());

        for (int tenor : tenors) {
            for (Rate strike : strikes) {
                // Cap
                {
                    auto cap = makeCap(eval, cal, idx, schedStart,
                                       tenor, strike, nominal);
                    cap->setPricingEngine(engine);
                    const Real npv = cap->NPV();

                    char nm[64];
                    std::snprintf(nm, sizeof nm, "%s_%dy_cap_k%.0f_%03d",
                                  eng.tag, tenor, strike * 10000.0, caseIdx++);
                    out.addCase(nm,
                        json{
                            {"engine_tag", eng.tag},
                            {"integration_points", eng.integrationPoints},
                            {"stddevs", eng.stddevs},
                            {"extrapolate_payoff", eng.extrapolatePayoff},
                            {"flat_payoff_extrapolation", eng.flatPayoffExtrapolation},
                            {"tenor_years", tenor},
                            {"type", "Cap"},
                            {"strike", strike},
                            {"nominal", nominal}
                        },
                        json{{"npv", npv}});
                }
                // Floor
                {
                    auto floor = makeFloor(eval, cal, idx, schedStart,
                                           tenor, strike, nominal);
                    floor->setPricingEngine(engine);
                    const Real npv = floor->NPV();

                    char nm[64];
                    std::snprintf(nm, sizeof nm, "%s_%dy_floor_k%.0f_%03d",
                                  eng.tag, tenor, strike * 10000.0, caseIdx++);
                    out.addCase(nm,
                        json{
                            {"engine_tag", eng.tag},
                            {"integration_points", eng.integrationPoints},
                            {"stddevs", eng.stddevs},
                            {"extrapolate_payoff", eng.extrapolatePayoff},
                            {"flat_payoff_extrapolation", eng.flatPayoffExtrapolation},
                            {"tenor_years", tenor},
                            {"type", "Floor"},
                            {"strike", strike},
                            {"nominal", nominal}
                        },
                        json{{"npv", npv}});
                }
            }
        }
    }

    out.write();
    return 0;
}
