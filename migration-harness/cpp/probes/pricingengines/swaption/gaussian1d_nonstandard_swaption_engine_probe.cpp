// migration-harness/cpp/probes/pricingengines/swaption/gaussian1d_nonstandard_swaption_engine_probe.cpp
//
// Phase 2j.5 Track A.3 — Gaussian1dNonstandardSwaptionEngine NPV fingerprint.
// Oracle: C++ QuantLib v1.42.1.
//
// Builds a Gsr (vol-step) Gaussian1d model + Gaussian1dNonstandardSwaptionEngine
// and prices physically-settled European NonstandardSwaptions across a grid of
// (expiry, swap-tenor, swap-type, amortization) cells.
//
// Fixture: eval=2026-01-15, FlatForward 3% Continuous Actual365Fixed on TARGET,
// Euribor3M floating leg, 30/360 European fixed leg (Annual).
//
// Cells cover:
//   - 3 expiry lengths (1Y, 2Y, 5Y)
//   - 2 swap tenors (3Y, 5Y)
//   - 2 swap types (Payer, Receiver)
//   - 3 notional profiles (flat, amortizing, accreting)
//   - 2 engine variants (default 64pts/7sd, high-density 128pts/7sd)
// = 72 cases
//
// Captured output: npv = NonstandardSwaption.NPV()

#include <ql/version.hpp>

#include <ql/exercise.hpp>
#include <ql/indexes/ibor/euribor.hpp>
#include <ql/instruments/nonstandardswap.hpp>
#include <ql/instruments/nonstandardswaption.hpp>
#include <ql/models/shortrate/onefactormodels/gsr.hpp>
#include <ql/pricingengines/swaption/gaussian1dnonstandardswaptionengine.hpp>
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

// Build accreting nominals (linearly increasing).
std::vector<Real> accretingNominal(int n, Real startNom) {
    std::vector<Real> v(n);
    for (int i = 0; i < n; i++)
        v[i] = startNom * (1.0 + i * 0.05);
    return v;
}

} // namespace

int main() {
    ReferenceWriter out(
        "pricingengines/swaption/gaussian1d_nonstandard_swaption_engine",
        QL_VERSION,
        "gaussian1d_nonstandard_swaption_engine_probe");

    // --- Fixture -----------------------------------------------------------
    const Date eval(15, January, 2026);
    Settings::instance().evaluationDate() = eval;

    const DayCounter dc  = Actual365Fixed();
    const DayCounter fixedDc = Thirty360(Thirty360::European);
    const Calendar   cal = TARGET();
    const Real flatRate  = 0.03;
    const Real reversion = 0.01;
    const Real nominal   = 100.0; // base notional unit

    const Handle<YieldTermStructure> ts(
        ext::make_shared<FlatForward>(eval, flatRate, dc, Continuous, Annual));
    auto idx = ext::make_shared<Euribor3M>(ts);

    // Gsr model: same vol-step structure as the standard swaption engine probe.
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
        {"e0",  64, 7.0, true, false},  // defaults
        {"e1", 128, 7.0, true, false},  // high density
    };

    // --- Case grid ---------------------------------------------------------
    struct Cell {
        int exerciseYears;
        int swapYears;
        Swap::Type swapType;
        const char* nominalProfile; // "flat", "amort", "accret"
        Real fixedRate;
    };

    Cell cells[] = {
        // 1Y expiry, 3Y swap
        {1, 3, Swap::Payer,    "flat",   0.02},
        {1, 3, Swap::Payer,    "flat",   0.03},
        {1, 3, Swap::Payer,    "flat",   0.04},
        {1, 3, Swap::Receiver, "flat",   0.02},
        {1, 3, Swap::Receiver, "flat",   0.03},
        {1, 3, Swap::Receiver, "flat",   0.04},
        {1, 3, Swap::Payer,    "amort",  0.03},
        {1, 3, Swap::Receiver, "amort",  0.03},
        {1, 3, Swap::Payer,    "accret", 0.03},
        // 1Y expiry, 5Y swap
        {1, 5, Swap::Payer,    "flat",   0.02},
        {1, 5, Swap::Payer,    "flat",   0.03},
        {1, 5, Swap::Payer,    "flat",   0.04},
        {1, 5, Swap::Receiver, "flat",   0.02},
        {1, 5, Swap::Receiver, "flat",   0.03},
        {1, 5, Swap::Receiver, "flat",   0.04},
        {1, 5, Swap::Payer,    "amort",  0.03},
        {1, 5, Swap::Receiver, "amort",  0.03},
        {1, 5, Swap::Payer,    "accret", 0.03},
        // 2Y expiry, 3Y swap
        {2, 3, Swap::Payer,    "flat",   0.025},
        {2, 3, Swap::Payer,    "flat",   0.03},
        {2, 3, Swap::Receiver, "flat",   0.03},
        {2, 3, Swap::Receiver, "flat",   0.035},
        {2, 3, Swap::Payer,    "amort",  0.03},
        {2, 3, Swap::Receiver, "accret", 0.03},
        // 2Y expiry, 5Y swap
        {2, 5, Swap::Payer,    "flat",   0.025},
        {2, 5, Swap::Payer,    "flat",   0.03},
        {2, 5, Swap::Receiver, "flat",   0.03},
        {2, 5, Swap::Receiver, "flat",   0.04},
        {2, 5, Swap::Payer,    "amort",  0.03},
        {2, 5, Swap::Receiver, "amort",  0.03},
        {2, 5, Swap::Payer,    "accret", 0.03},
        {2, 5, Swap::Receiver, "accret", 0.03},
        // 5Y expiry, 3Y swap
        {5, 3, Swap::Payer,    "flat",   0.02},
        {5, 3, Swap::Payer,    "flat",   0.03},
        {5, 3, Swap::Receiver, "flat",   0.03},
        {5, 3, Swap::Receiver, "flat",   0.04},
        // 5Y expiry, 5Y swap
        {5, 5, Swap::Payer,    "flat",   0.02},
        {5, 5, Swap::Payer,    "flat",   0.03},
        {5, 5, Swap::Payer,    "flat",   0.04},
        {5, 5, Swap::Receiver, "flat",   0.02},
        {5, 5, Swap::Receiver, "flat",   0.03},
        {5, 5, Swap::Receiver, "flat",   0.04},
        {5, 5, Swap::Payer,    "amort",  0.03},
        {5, 5, Swap::Receiver, "amort",  0.03},
        {5, 5, Swap::Payer,    "accret", 0.03},
        {5, 5, Swap::Receiver, "accret", 0.03},
    };
    const int nCells = static_cast<int>(sizeof(cells) / sizeof(cells[0]));

    int caseIdx = 0;

    for (const auto& eng : engines) {
        auto engine = ext::make_shared<Gaussian1dNonstandardSwaptionEngine>(
            gsr,
            eng.integrationPoints,
            eng.stddevs,
            eng.extrapolatePayoff,
            eng.flatPayoffExtrapolation,
            Handle<Quote>(),
            Handle<YieldTermStructure>(),
            Gaussian1dNonstandardSwaptionEngine::None);

        for (int ci = 0; ci < nCells; ci++) {
            const Cell& cell = cells[ci];

            const Date exerciseDate = cal.advance(eval, Period(cell.exerciseYears, Years));
            auto exercise = ext::make_shared<EuropeanExercise>(exerciseDate);
            const Date startDate = cal.advance(exerciseDate, 2, Days);

            // Fixed schedule: annual periods for swapYears years
            const Date maturity = cal.advance(startDate, Period(cell.swapYears, Years));
            Schedule fixedSch(startDate, maturity, Period(1, Years), cal,
                              ModifiedFollowing, ModifiedFollowing,
                              DateGeneration::Forward, false);
            Schedule floatSch(startDate, maturity, Period(3, Months), cal,
                              ModifiedFollowing, ModifiedFollowing,
                              DateGeneration::Forward, false);

            int nFixed = static_cast<int>(fixedSch.size()) - 1;
            int nFloat = static_cast<int>(floatSch.size()) - 1;

            std::vector<Real> fixedNoms, floatNoms;
            if (std::string(cell.nominalProfile) == "flat") {
                fixedNoms = flatNominal(nFixed, nominal);
                floatNoms = flatNominal(nFloat, nominal);
            } else if (std::string(cell.nominalProfile) == "amort") {
                fixedNoms = amortizingNominal(nFixed, nominal);
                // Replicate each fixed period over 4 quarterly float periods
                for (int i = 0; i < nFixed; i++)
                    for (int q = 0; q < 4; q++)
                        floatNoms.push_back(fixedNoms[i]);
                floatNoms.resize(nFloat, floatNoms.back());
            } else { // accret
                fixedNoms = accretingNominal(nFixed, nominal);
                for (int i = 0; i < nFixed; i++)
                    for (int q = 0; q < 4; q++)
                        floatNoms.push_back(fixedNoms[i]);
                floatNoms.resize(nFloat, floatNoms.back());
            }

            std::vector<Real> fixedRates(nFixed, cell.fixedRate);
            auto swap = ext::make_shared<NonstandardSwap>(
                cell.swapType,
                fixedNoms, floatNoms,
                fixedSch, fixedRates, fixedDc,
                floatSch, idx,
                1.0, 0.0, dc, false, false);

            auto swaption = ext::make_shared<NonstandardSwaption>(swap, exercise);
            swaption->setPricingEngine(engine);
            const Real npv = swaption->NPV();

            char nm[128];
            std::snprintf(nm, sizeof nm,
                          "%s_%dyx%dy_%s_%s_k%.0f_%03d",
                          eng.tag,
                          cell.exerciseYears, cell.swapYears,
                          cell.swapType == VanillaSwap::Payer ? "p" : "r",
                          cell.nominalProfile,
                          cell.fixedRate * 10000.0,
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
                    {"nominal_profile", cell.nominalProfile},
                    {"fixed_rate", cell.fixedRate},
                    {"n_fixed", nFixed},
                    {"n_float", nFloat}
                },
                json{{"npv", npv}});
        }
    }

    out.write();
    return 0;
}
