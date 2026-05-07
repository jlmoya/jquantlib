// migration-harness/cpp/probes/pricingengines/swaption/gaussian1d_float_float_swaption_engine_probe.cpp
//
// Phase 2j.5 Track B.3 — Gaussian1dFloatFloatSwaptionEngine NPV fingerprint.
// Oracle: C++ QuantLib v1.42.1.
//
// Builds a Gsr (vol-step) Gaussian1d model + Gaussian1dFloatFloatSwaptionEngine
// and prices physically-settled European FloatFloatSwaptions across a grid of
// (expiry, swap-tenor, swap-type, leg-shape, cap/floor) cells.
//
// Fixture: eval=2026-01-15, FlatForward 3% Continuous Actual365Fixed on TARGET,
// Euribor3M floating leg 1, Euribor6M floating leg 2 (with spread/gearing/cap/floor).
//
// Cells cover:
//   - 3 expiry lengths (1Y, 2Y, 5Y)
//   - 2 swap tenors (3Y, 5Y)
//   - 2 swap types (Payer, Receiver)
//   - cap/floor flags on leg2: none / capped / floored / capped+floored
//   - 2 engine variants (default 64pts/7sd, high-density 128pts/7sd)
//
// Captured output: npv = FloatFloatSwaption.NPV(), underlyingValue from
// additional results.

#include <ql/version.hpp>

#include <ql/exercise.hpp>
#include <ql/indexes/ibor/euribor.hpp>
#include <ql/instruments/floatfloatswap.hpp>
#include <ql/instruments/floatfloatswaption.hpp>
#include <ql/models/shortrate/onefactormodels/gsr.hpp>
#include <ql/pricingengines/swaption/gaussian1dfloatfloatswaptionengine.hpp>
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

// Build flat nominals.
std::vector<Real> flatNominal(int n, Real nom) {
    return std::vector<Real>(n, nom);
}

// Build amortizing nominals (linearly decreasing).
std::vector<Real> amortizingNominal(int n, Real startNom) {
    std::vector<Real> v(n);
    for (int i = 0; i < n; i++)
        v[i] = startNom * (1.0 - i * 0.1);
    return v;
}

} // namespace

int main() {
    ReferenceWriter out(
        "pricingengines/swaption/gaussian1d_float_float_swaption_engine",
        QL_VERSION,
        "gaussian1d_float_float_swaption_engine_probe");

    // --- Fixture -----------------------------------------------------------
    const Date eval(15, January, 2026);
    Settings::instance().evaluationDate() = eval;

    const DayCounter dc  = Actual365Fixed();
    const Calendar   cal = TARGET();
    const Real flatRate  = 0.03;
    const Real reversion = 0.01;
    const Real nominal   = 100.0;

    const Handle<YieldTermStructure> ts(
        ext::make_shared<FlatForward>(eval, flatRate, dc, Continuous, Annual));
    auto idx1 = ext::make_shared<Euribor3M>(ts);
    auto idx2 = ext::make_shared<Euribor6M>(ts);

    // Gsr model
    std::vector<Date> volStepDates;
    volStepDates.push_back(eval + Period(1, Years));
    volStepDates.push_back(eval + Period(2, Years));
    volStepDates.push_back(eval + Period(5, Years));
    std::vector<Real> vols = {0.01, 0.012, 0.014, 0.016};

    auto gsr = ext::make_shared<Gsr>(ts, volStepDates, vols, reversion);

    // --- Engine variants ---------------------------------------------------
    struct EngSpec {
        const char* tag;
        int integrationPoints;
        Real stddevs;
        bool extrapolatePayoff;
        bool flatPayoffExtrapolation;
    };
    EngSpec engines[] = {
        {"e0",  64, 7.0, true, false},
        {"e1", 128, 7.0, true, false},
    };

    // --- Case grid ---------------------------------------------------------
    struct Cell {
        int exerciseYears;
        int swapYears;
        Swap::Type swapType;
        const char* shape;     // "flat" or "amort"
        const char* capFloor;  // "none", "cap", "floor", "both"
        Real spread2;
        Real gearing2;
    };

    Cell cells[] = {
        // 1Y x 3Y
        {1, 3, Swap::Payer,    "flat",  "none",  0.005, 1.0},
        {1, 3, Swap::Payer,    "flat",  "cap",   0.005, 1.0},
        {1, 3, Swap::Payer,    "flat",  "floor", 0.005, 1.0},
        {1, 3, Swap::Payer,    "flat",  "both",  0.005, 1.0},
        {1, 3, Swap::Receiver, "flat",  "none",  0.005, 1.0},
        {1, 3, Swap::Receiver, "flat",  "cap",   0.005, 1.0},
        {1, 3, Swap::Receiver, "flat",  "floor", 0.005, 1.0},
        {1, 3, Swap::Receiver, "flat",  "both",  0.005, 1.0},
        // 1Y x 5Y
        {1, 5, Swap::Payer,    "flat",  "none",  0.0,   1.0},
        {1, 5, Swap::Payer,    "flat",  "cap",   0.0,   1.0},
        {1, 5, Swap::Receiver, "flat",  "none",  0.0,   1.0},
        {1, 5, Swap::Receiver, "flat",  "floor", 0.0,   1.0},
        {1, 5, Swap::Payer,    "amort", "none",  0.005, 1.0},
        {1, 5, Swap::Receiver, "amort", "none",  0.005, 1.0},
        {1, 5, Swap::Payer,    "amort", "both",  0.005, 1.0},
        // 2Y x 3Y
        {2, 3, Swap::Payer,    "flat",  "none",  0.0025, 1.0},
        {2, 3, Swap::Payer,    "flat",  "cap",   0.0025, 1.0},
        {2, 3, Swap::Payer,    "flat",  "floor", 0.0025, 1.0},
        {2, 3, Swap::Receiver, "flat",  "none",  0.0025, 1.0},
        {2, 3, Swap::Receiver, "flat",  "both",  0.0025, 1.0},
        {2, 3, Swap::Payer,    "amort", "none",  0.0025, 1.0},
        {2, 3, Swap::Receiver, "amort", "cap",   0.0025, 1.0},
        // 2Y x 5Y
        {2, 5, Swap::Payer,    "flat",  "none",  0.005, 0.8},
        {2, 5, Swap::Receiver, "flat",  "none",  0.005, 0.8},
        {2, 5, Swap::Payer,    "flat",  "cap",   0.005, 0.8},
        {2, 5, Swap::Receiver, "flat",  "floor", 0.005, 0.8},
        {2, 5, Swap::Payer,    "amort", "none",  0.005, 1.0},
        {2, 5, Swap::Receiver, "amort", "none",  0.005, 1.0},
        // 5Y x 3Y
        {5, 3, Swap::Payer,    "flat",  "none",  0.0,   1.0},
        {5, 3, Swap::Receiver, "flat",  "none",  0.0,   1.0},
        {5, 3, Swap::Payer,    "flat",  "cap",   0.0,   1.0},
        {5, 3, Swap::Receiver, "flat",  "floor", 0.0,   1.0},
        // 5Y x 5Y
        {5, 5, Swap::Payer,    "flat",  "none",  0.005, 1.0},
        {5, 5, Swap::Receiver, "flat",  "none",  0.005, 1.0},
        {5, 5, Swap::Payer,    "flat",  "both",  0.005, 1.0},
        {5, 5, Swap::Receiver, "flat",  "both",  0.005, 1.0},
    };
    const int nCells = static_cast<int>(sizeof(cells) / sizeof(cells[0]));

    int caseIdx = 0;

    for (const auto& eng : engines) {
        auto engine = ext::make_shared<Gaussian1dFloatFloatSwaptionEngine>(
            gsr,
            eng.integrationPoints,
            eng.stddevs,
            eng.extrapolatePayoff,
            eng.flatPayoffExtrapolation,
            Handle<Quote>(),
            Handle<YieldTermStructure>(),
            false,
            Gaussian1dFloatFloatSwaptionEngine::None);

        for (int ci = 0; ci < nCells; ci++) {
            const Cell& cell = cells[ci];

            const Date exerciseDate = cal.advance(eval, Period(cell.exerciseYears, Years));
            auto exercise = ext::make_shared<EuropeanExercise>(exerciseDate);
            const Date startDate = cal.advance(exerciseDate, 2, Days);
            const Date maturity  = cal.advance(startDate, Period(cell.swapYears, Years));

            // Schedule 1: quarterly; Schedule 2: semi-annual
            Schedule sch1(startDate, maturity, Period(3, Months), cal,
                          ModifiedFollowing, ModifiedFollowing,
                          DateGeneration::Forward, false);
            Schedule sch2(startDate, maturity, Period(6, Months), cal,
                          ModifiedFollowing, ModifiedFollowing,
                          DateGeneration::Forward, false);

            const int n1 = static_cast<int>(sch1.size()) - 1;
            const int n2 = static_cast<int>(sch2.size()) - 1;

            std::vector<Real> nom1, nom2;
            if (std::string(cell.shape) == "flat") {
                nom1 = flatNominal(n1, nominal);
                nom2 = flatNominal(n2, nominal);
            } else {
                // "amort" — amortise per fixed-period equivalent (yearly):
                // rebuild from yearly profile, replicate per quarterly/semi-annual
                std::vector<Real> baseY = amortizingNominal(cell.swapYears, nominal);
                nom1.resize(n1);
                for (int i = 0; i < n1; i++) {
                    int yr = std::min(i / 4, cell.swapYears - 1);
                    nom1[i] = baseY[yr];
                }
                nom2.resize(n2);
                for (int i = 0; i < n2; i++) {
                    int yr = std::min(i / 2, cell.swapYears - 1);
                    nom2[i] = baseY[yr];
                }
            }

            std::vector<Real> gear1(n1, 1.0);
            std::vector<Real> spr1(n1, 0.0);
            std::vector<Real> cap1(n1, Null<Real>());
            std::vector<Real> flr1(n1, Null<Real>());

            std::vector<Real> gear2(n2, cell.gearing2);
            std::vector<Real> spr2(n2, cell.spread2);
            std::vector<Real> cap2(n2, Null<Real>());
            std::vector<Real> flr2(n2, Null<Real>());

            const std::string cf(cell.capFloor);
            if (cf == "cap" || cf == "both") {
                for (int i = 0; i < n2; i++) cap2[i] = 0.04;
            }
            if (cf == "floor" || cf == "both") {
                for (int i = 0; i < n2; i++) flr2[i] = 0.02;
            }

            auto swap = ext::make_shared<FloatFloatSwap>(
                cell.swapType,
                nom1, nom2,
                sch1, idx1, dc,
                sch2, idx2, dc,
                false, false,
                gear1, spr1, cap1, flr1,
                gear2, spr2, cap2, flr2);

            auto swaption = ext::make_shared<FloatFloatSwaption>(swap, exercise);
            swaption->setPricingEngine(engine);
            const Real npv = swaption->NPV();

            // Underlying value from additionalResults
            Real underlyingValue = 0.0;
            try {
                underlyingValue = swaption->result<Real>("underlyingValue");
            } catch (...) {
                underlyingValue = 0.0;
            }

            char nm[160];
            std::snprintf(nm, sizeof nm,
                          "%s_%dyx%dy_%s_%s_%s_g%03d_s%03d_%03d",
                          eng.tag,
                          cell.exerciseYears, cell.swapYears,
                          cell.swapType == VanillaSwap::Payer ? "p" : "r",
                          cell.shape,
                          cell.capFloor,
                          static_cast<int>(cell.gearing2 * 100.0),
                          static_cast<int>(cell.spread2 * 10000.0),
                          caseIdx++);

            out.addCase(nm,
                json{
                    {"engine_tag", eng.tag},
                    {"integration_points", eng.integrationPoints},
                    {"stddevs", eng.stddevs},
                    {"extrapolate_payoff", eng.extrapolatePayoff},
                    {"flat_payoff_extrapolation", eng.flatPayoffExtrapolation},
                    {"exercise_years", cell.exerciseYears},
                    {"swap_years", cell.swapYears},
                    {"swap_type", cell.swapType == Swap::Payer ? "Payer" : "Receiver"},
                    {"shape", cell.shape},
                    {"cap_floor", cell.capFloor},
                    {"spread2", cell.spread2},
                    {"gearing2", cell.gearing2},
                    {"n1", n1},
                    {"n2", n2}
                },
                json{
                    {"npv", npv},
                    {"underlyingValue", underlyingValue}
                });
        }
    }

    out.write();
    return 0;
}
