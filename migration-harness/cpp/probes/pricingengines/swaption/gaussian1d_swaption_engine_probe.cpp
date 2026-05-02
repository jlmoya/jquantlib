// migration-harness/cpp/probes/pricingengines/swaption/gaussian1d_swaption_engine_probe.cpp
//
// Phase 2j WI-2.1 — Gaussian1dSwaptionEngine NPV fingerprint.
// Oracle: C++ QuantLib v1.42.1.
//
// Builds a Gsr (vol-step) Gaussian1d model + Gaussian1dSwaptionEngine and
// prices physically-settled European swaptions across (expiry, tenor, strike,
// type, integrationPoints, stddevs, extrapolatePayoff/flatExtrapolation) cells.
// All cases share the same fixture: eval=2026-01-15, FlatForward 3% Continuous
// Actual365Fixed on TARGET, Euribor3M floating leg, 30/360 European fixed
// leg (Annual). The same yield curve serves as both model TS and (implicit)
// discount curve (the engine accepts an empty discountCurve_ and falls back
// to the model TS — verified in the cpp).
//
// Captured outputs:
//   - npv : Swaption.NPV() under Gaussian1dSwaptionEngine

#include <ql/version.hpp>

#include <ql/exercise.hpp>
#include <ql/indexes/ibor/euribor.hpp>
#include <ql/instruments/swaption.hpp>
#include <ql/instruments/vanillaswap.hpp>
#include <ql/models/shortrate/onefactormodels/gsr.hpp>
#include <ql/pricingengines/swaption/gaussian1dswaptionengine.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/settings.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/time/calendars/target.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>
#include <ql/time/daycounters/thirty360.hpp>
#include <ql/time/schedule.hpp>

#include "common.hpp"

#include <cstdio>
#include <vector>

using namespace QuantLib;
using namespace jqml_harness;

namespace {

ext::shared_ptr<Swaption> makeEuropeanSwaption(
    const Date& eval,
    const Calendar& cal,
    const ext::shared_ptr<IborIndex>& idx,
    const DayCounter& fixedDc,
    const DayCounter& floatDc,
    int exerciseYears,
    int swapYears,
    Real strike,
    VanillaSwap::Type type,
    Real nominal) {

    const Date exerciseDate = cal.advance(eval, Period(exerciseYears, Years));
    auto exercise = ext::make_shared<EuropeanExercise>(exerciseDate);
    const Date startDate = cal.advance(exerciseDate, 2, Days);
    const Date maturity = cal.advance(startDate, Period(swapYears, Years));

    Schedule fixedSchedule(startDate, maturity, Period(1, Years), cal,
                           ModifiedFollowing, ModifiedFollowing,
                           DateGeneration::Forward, false);
    Schedule floatSchedule(startDate, maturity, Period(3, Months), cal,
                           ModifiedFollowing, ModifiedFollowing,
                           DateGeneration::Forward, false);

    auto swap = ext::make_shared<VanillaSwap>(
        type, nominal, fixedSchedule, strike, fixedDc,
        floatSchedule, idx, 0.0, floatDc);
    return ext::make_shared<Swaption>(swap, exercise);
}

} // namespace

int main() {
    ReferenceWriter out("pricingengines/swaption/gaussian1d_swaption_engine",
                        QL_VERSION, "gaussian1d_swaption_engine_probe");

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

    const DayCounter fixedDc = Thirty360(Thirty360::European);

    // Vol-step dates: 1Y, 2Y, 5Y after eval — gives 4 vol levels.
    std::vector<Date> volStepDates;
    volStepDates.push_back(eval + Period(1, Years));
    volStepDates.push_back(eval + Period(2, Years));
    volStepDates.push_back(eval + Period(5, Years));
    std::vector<Real> vols;
    vols.push_back(0.01);
    vols.push_back(0.012);
    vols.push_back(0.014);
    vols.push_back(0.016);

    auto gsr = ext::make_shared<Gsr>(ts, volStepDates, vols, reversion);

    // --- Engine variants ---------------------------------------------------
    // Each engine variant differs by integrationPoints / stddevs / extrapolate.
    struct Engine {
        const char* tag;
        int integrationPoints;
        Real stddevs;
        bool extrapolatePayoff;
        bool flatPayoffExtrapolation;
    };
    Engine engines[] = {
        // baseline (defaults from C++ ctor: 64 pts, 7.0 sd, true, false)
        {"e0",  64, 7.0, true,  false},
        // higher density
        {"e1", 128, 7.0, true,  false},
        // narrower std-dev cap, no extrapolation
        {"e2",  64, 5.0, false, false},
        // flat extrapolation variant
        {"e3",  64, 7.0, true,  true},
    };

    // --- Swaption cells ----------------------------------------------------
    // 4 engines × 3 expiries × 2 tenors × 3 strikes × 2 types = 144 cases.
    // Trimmed grid keeps probe runtime modest while spanning ITM/ATM/OTM and
    // payer/receiver across short / mid / long expiries.
    int caseIdx = 0;
    int exerciseYears[] = {1, 3, 5};
    int swapYears[]     = {2, 5};
    // strikes are fixed shifts around 3% (flat curve so par ~3%); the goal is
    // ITM / ATM / OTM coverage rather than calibration.
    Real strikes[] = {0.02, 0.03, 0.04};
    VanillaSwap::Type types[] = {VanillaSwap::Payer, VanillaSwap::Receiver};

    for (const auto& eng : engines) {
        for (int ey : exerciseYears) {
            for (int sy : swapYears) {
                for (Real strike : strikes) {
                    for (VanillaSwap::Type type : types) {
                        auto swaption = makeEuropeanSwaption(
                            eval, cal, idx, fixedDc, dc, ey, sy, strike, type, nominal);
                        swaption->setPricingEngine(
                            ext::make_shared<Gaussian1dSwaptionEngine>(
                                gsr, eng.integrationPoints, eng.stddevs,
                                eng.extrapolatePayoff,
                                eng.flatPayoffExtrapolation,
                                Handle<YieldTermStructure>()));
                        const Real npv = swaption->NPV();

                        char nm[64];
                        std::snprintf(nm, sizeof nm, "%s_%dyx%dy_k%.0f_%s_%03d",
                                      eng.tag, ey, sy, strike * 10000.0,
                                      type == VanillaSwap::Payer ? "p" : "r",
                                      caseIdx++);
                        out.addCase(nm,
                            json{
                                {"engine_tag", eng.tag},
                                {"integration_points", eng.integrationPoints},
                                {"stddevs", eng.stddevs},
                                {"extrapolate_payoff", eng.extrapolatePayoff},
                                {"flat_payoff_extrapolation", eng.flatPayoffExtrapolation},
                                {"exercise_years", ey},
                                {"swap_years", sy},
                                {"strike", strike},
                                {"type", type == VanillaSwap::Payer ? "Payer" : "Receiver"},
                                {"nominal", nominal}
                            },
                            json{{"npv", npv}});
                    }
                }
            }
        }
    }

    out.write();
    return 0;
}
