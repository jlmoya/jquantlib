// migration-harness/cpp/probes/instruments/nonstandard_swaption_probe.cpp
//
// Phase 2j.5 Track A.2 — NonstandardSwaption structural fingerprint.
// Oracle: C++ QuantLib v1.42.1.
//
// Tests the structural (non-pricing) aspects of NonstandardSwaption:
//   - settlementType()       : Settlement::Type enum value (0=Physical, 1=Cash)
//   - settlementMethod()     : Settlement::Method enum value
//   - type()                 : Swap::Type of the underlying (0=Receiver, -1=Payer)
//   - underlyingSwap()       : accessor returns non-null
//   - exerciseDate           : exercise.dates().back() (last exercise date)
//   - isExpired              : false when eval < exercise date
//   - nFixedCoupons          : underlying fixedLeg().size()
//   - nFloatCoupons          : underlying floatingLeg().size()
//   - fromSwaptionConstruct  : construction from Swaption copies all fields
//
// Fixture: eval=2026-01-15, TARGET calendar, Euribor3M, FlatForward 4%.

#include <ql/version.hpp>

#include <ql/exercise.hpp>
#include <ql/indexes/ibor/euribor.hpp>
#include <ql/instruments/nonstandardswap.hpp>
#include <ql/instruments/nonstandardswaption.hpp>
#include <ql/instruments/swaption.hpp>
#include <ql/instruments/vanillaswap.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/settings.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/time/calendars/target.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>
#include <ql/time/daycounters/thirty360.hpp>
#include <ql/time/schedule.hpp>

#include "common.hpp"

#include <vector>
#include <string>

using namespace QuantLib;
using namespace jqml_harness;

namespace {

const Date EVAL(15, January, 2026);
const DayCounter DC   = Actual365Fixed();
const DayCounter FIXED_DC = Thirty360(Thirty360::European);
const Calendar   CAL  = TARGET();
const Real FLAT_RATE  = 0.04;

Handle<YieldTermStructure> makeTS() {
    return Handle<YieldTermStructure>(
        ext::make_shared<FlatForward>(EVAL, FLAT_RATE, DC, Continuous, Annual));
}

ext::shared_ptr<IborIndex> makeIdx(const Handle<YieldTermStructure>& ts) {
    return ext::make_shared<Euribor3M>(ts);
}

Schedule makeSchedule(const Date& start, int years, const Period& tenor) {
    Date end = CAL.advance(start, Period(years, Years));
    return Schedule(start, end, tenor, CAL,
                    ModifiedFollowing, ModifiedFollowing,
                    DateGeneration::Forward, false);
}

// Build a NonstandardSwaption directly from a NonstandardSwap.
// Returns fingerprints as a JSON object.
json swaptionFingerprint(
    const ext::shared_ptr<NonstandardSwap>& swap,
    const ext::shared_ptr<Exercise>& exercise,
    Settlement::Type settlType,
    Settlement::Method settlMethod)
{
    NonstandardSwaption nsw(swap, exercise, settlType, settlMethod);

    // Exercise date: last date in exercise schedule
    Date exDate = nsw.exercise()->dates().back();

    return {
        {"settlementType",   (int)nsw.settlementType()},
        {"settlementMethod", (int)nsw.settlementMethod()},
        {"swapType",         (int)nsw.type()},
        {"hasUnderlying",    (nsw.underlyingSwap() != nullptr)},
        {"exerciseYear",     exDate.year()},
        {"exerciseMonth",    (int)exDate.month()},
        {"exerciseDay",      exDate.dayOfMonth()},
        {"isExpired",        nsw.isExpired()},
        {"nFixedCoupons",    (int)nsw.underlyingSwap()->fixedLeg().size()},
        {"nFloatCoupons",    (int)nsw.underlyingSwap()->floatingLeg().size()}
    };
}

} // namespace

int main() {
    ReferenceWriter out("instruments/nonstandard_swaption",
                        QL_VERSION, "nonstandard_swaption_probe");

    Settings::instance().evaluationDate() = EVAL;
    auto ts  = makeTS();
    auto idx = makeIdx(ts);

    // ── Case 1: 1y European exercise, 5y Receiver, Physical/PhysicalOTC ──────
    {
        Date start = CAL.advance(EVAL, Period(1, Years));
        Date swapStart = CAL.advance(start, 2, Days);
        Schedule fixedSch = makeSchedule(swapStart, 5, Period(1, Years));
        Schedule floatSch = makeSchedule(swapStart, 5, Period(3, Months));
        int nFixed = fixedSch.size() - 1;
        int nFloat = floatSch.size() - 1;
        std::vector<Real> fixedNom(nFixed, 1e6);
        std::vector<Real> floatNom(nFloat, 1e6);
        std::vector<Real> fixedRates(nFixed, 0.035);

        auto swap = ext::make_shared<NonstandardSwap>(
            Swap::Receiver, fixedNom, floatNom, fixedSch, fixedRates,
            FIXED_DC, floatSch, idx, 1.0, 0.0, DC);
        auto exercise = ext::make_shared<EuropeanExercise>(start);

        out.addCase("receiver_physical_otc_1y5y",
            {{"swapType", "Receiver"}, {"exerciseYears", 1}, {"swapYears", 5},
             {"settlementType", "Physical"}, {"settlementMethod", "PhysicalOTC"}},
            swaptionFingerprint(swap, exercise,
                Settlement::Physical, Settlement::PhysicalOTC));
    }

    // ── Case 2: 2y European exercise, 3y Payer, Physical/PhysicalOTC ─────────
    {
        Date start = CAL.advance(EVAL, Period(2, Years));
        Date swapStart = CAL.advance(start, 2, Days);
        Schedule fixedSch = makeSchedule(swapStart, 3, Period(1, Years));
        Schedule floatSch = makeSchedule(swapStart, 3, Period(3, Months));
        int nFixed = fixedSch.size() - 1;
        int nFloat = floatSch.size() - 1;
        std::vector<Real> fixedNom(nFixed, 5e5);
        std::vector<Real> floatNom(nFloat, 5e5);
        std::vector<Real> fixedRates(nFixed, 0.04);

        auto swap = ext::make_shared<NonstandardSwap>(
            Swap::Payer, fixedNom, floatNom, fixedSch, fixedRates,
            FIXED_DC, floatSch, idx, 1.0, 0.0, DC);
        auto exercise = ext::make_shared<EuropeanExercise>(start);

        out.addCase("payer_physical_otc_2y3y",
            {{"swapType", "Payer"}, {"exerciseYears", 2}, {"swapYears", 3},
             {"settlementType", "Physical"}, {"settlementMethod", "PhysicalOTC"}},
            swaptionFingerprint(swap, exercise,
                Settlement::Physical, Settlement::PhysicalOTC));
    }

    // ── Case 3: Cash / CollateralizedCashPrice ────────────────────────────────
    {
        Date start = CAL.advance(EVAL, Period(1, Years));
        Date swapStart = CAL.advance(start, 2, Days);
        Schedule fixedSch = makeSchedule(swapStart, 10, Period(1, Years));
        Schedule floatSch = makeSchedule(swapStart, 10, Period(3, Months));
        int nFixed = fixedSch.size() - 1;
        int nFloat = floatSch.size() - 1;
        std::vector<Real> fixedNom(nFixed, 1e6);
        std::vector<Real> floatNom(nFloat, 1e6);
        std::vector<Real> fixedRates(nFixed, 0.03);

        auto swap = ext::make_shared<NonstandardSwap>(
            Swap::Receiver, fixedNom, floatNom, fixedSch, fixedRates,
            FIXED_DC, floatSch, idx, 1.0, 0.0, DC);
        auto exercise = ext::make_shared<EuropeanExercise>(start);

        out.addCase("receiver_cash_ccp_1y10y",
            {{"swapType", "Receiver"}, {"exerciseYears", 1}, {"swapYears", 10},
             {"settlementType", "Cash"}, {"settlementMethod", "CollateralizedCashPrice"}},
            swaptionFingerprint(swap, exercise,
                Settlement::Cash, Settlement::CollateralizedCashPrice));
    }

    // ── Case 4: Cash / ParYieldCurve ─────────────────────────────────────────
    {
        Date start = CAL.advance(EVAL, Period(3, Years));
        Date swapStart = CAL.advance(start, 2, Days);
        Schedule fixedSch = makeSchedule(swapStart, 7, Period(1, Years));
        Schedule floatSch = makeSchedule(swapStart, 7, Period(3, Months));
        int nFixed = fixedSch.size() - 1;
        int nFloat = floatSch.size() - 1;
        std::vector<Real> fixedNom(nFixed, 2e6);
        std::vector<Real> floatNom(nFloat, 2e6);
        std::vector<Real> fixedRates(nFixed, 0.045);

        auto swap = ext::make_shared<NonstandardSwap>(
            Swap::Payer, fixedNom, floatNom, fixedSch, fixedRates,
            FIXED_DC, floatSch, idx, 1.0, 0.001, DC);
        auto exercise = ext::make_shared<EuropeanExercise>(start);

        out.addCase("payer_cash_paryieldcurve_3y7y",
            {{"swapType", "Payer"}, {"exerciseYears", 3}, {"swapYears", 7},
             {"settlementType", "Cash"}, {"settlementMethod", "ParYieldCurve"}},
            swaptionFingerprint(swap, exercise,
                Settlement::Cash, Settlement::ParYieldCurve));
    }

    // ── Case 5: Physical / PhysicalCleared ───────────────────────────────────
    {
        Date start = CAL.advance(EVAL, Period(5, Years));
        Date swapStart = CAL.advance(start, 2, Days);
        Schedule fixedSch = makeSchedule(swapStart, 5, Period(1, Years));
        Schedule floatSch = makeSchedule(swapStart, 5, Period(3, Months));
        int nFixed = fixedSch.size() - 1;
        int nFloat = floatSch.size() - 1;
        std::vector<Real> fixedNom(nFixed, 1e6);
        std::vector<Real> floatNom(nFloat, 1e6);
        std::vector<Real> fixedRates(nFixed, 0.05);

        auto swap = ext::make_shared<NonstandardSwap>(
            Swap::Payer, fixedNom, floatNom, fixedSch, fixedRates,
            FIXED_DC, floatSch, idx, 1.0, 0.0, DC);
        auto exercise = ext::make_shared<EuropeanExercise>(start);

        out.addCase("payer_physical_cleared_5y5y",
            {{"swapType", "Payer"}, {"exerciseYears", 5}, {"swapYears", 5},
             {"settlementType", "Physical"}, {"settlementMethod", "PhysicalCleared"}},
            swaptionFingerprint(swap, exercise,
                Settlement::Physical, Settlement::PhysicalCleared));
    }

    // ── Case 6: per-period notionals (amortizing), 2y/3y Receiver ────────────
    {
        Date start = CAL.advance(EVAL, Period(2, Years));
        Date swapStart = CAL.advance(start, 2, Days);
        Schedule fixedSch = makeSchedule(swapStart, 3, Period(1, Years));
        Schedule floatSch = makeSchedule(swapStart, 3, Period(3, Months));

        std::vector<Real> fixedNom  = {1e6, 7e5, 4e5};
        std::vector<Real> floatNom  = {1e6, 1e6, 1e6, 7e5, 7e5, 7e5, 4e5, 4e5, 4e5, 3e5, 3e5, 3e5};
        std::vector<Real> fixedRates = {0.03, 0.035, 0.04};

        auto swap = ext::make_shared<NonstandardSwap>(
            Swap::Receiver, fixedNom, floatNom, fixedSch, fixedRates,
            FIXED_DC, floatSch, idx, 1.0, 0.0, DC);
        auto exercise = ext::make_shared<EuropeanExercise>(start);

        out.addCase("receiver_amortizing_2y3y",
            {{"swapType", "Receiver"}, {"exerciseYears", 2}, {"swapYears", 3},
             {"settlementType", "Physical"}, {"amortizing", true}},
            swaptionFingerprint(swap, exercise,
                Settlement::Physical, Settlement::PhysicalOTC));
    }

    // ── Case 7: fromSwaption constructor ─────────────────────────────────────
    // Build a plain Swaption from a VanillaSwap and wrap it.
    {
        Date exDate = CAL.advance(EVAL, Period(1, Years));
        auto exercise = ext::make_shared<EuropeanExercise>(exDate);
        Date start = CAL.advance(exDate, 2, Days);
        Schedule fixedSch = makeSchedule(start, 5, Period(1, Years));
        Schedule floatSch = makeSchedule(start, 5, Period(3, Months));
        auto vanilla = ext::make_shared<VanillaSwap>(
            Swap::Payer, 1e6, fixedSch, 0.04, FIXED_DC, floatSch, idx, 0.0, DC);
        Swaption swaption(vanilla, exercise,
                          Settlement::Physical, Settlement::PhysicalOTC);

        NonstandardSwaption nsw(swaption);

        Date exDate2 = nsw.exercise()->dates().back();
        out.addCase("from_swaption_payer_1y5y",
            {{"constructedFrom", "Swaption"}, {"swapType", "Payer"},
             {"exerciseYears", 1}, {"swapYears", 5}},
            {{"settlementType",   (int)nsw.settlementType()},
             {"settlementMethod", (int)nsw.settlementMethod()},
             {"swapType",         (int)nsw.type()},
             {"hasUnderlying",    (nsw.underlyingSwap() != nullptr)},
             {"exerciseYear",     exDate2.year()},
             {"exerciseMonth",    (int)exDate2.month()},
             {"exerciseDay",      exDate2.dayOfMonth()},
             {"isExpired",        nsw.isExpired()},
             {"nFixedCoupons",    (int)nsw.underlyingSwap()->fixedLeg().size()},
             {"nFloatCoupons",    (int)nsw.underlyingSwap()->floatingLeg().size()}});
    }

    // ── Case 8: isExpired when exercise date is in the past ──────────────────
    {
        // Use a past exercise date relative to EVAL=2026-01-15
        Date pastDate(14, January, 2025);  // clearly past
        auto exercise = ext::make_shared<EuropeanExercise>(pastDate);
        Date swapStart = CAL.advance(pastDate, 2, Days);
        Schedule fixedSch = makeSchedule(swapStart, 5, Period(1, Years));
        Schedule floatSch = makeSchedule(swapStart, 5, Period(3, Months));
        int nFixed = fixedSch.size() - 1;
        int nFloat = floatSch.size() - 1;
        std::vector<Real> fixedNom(nFixed, 1e6);
        std::vector<Real> floatNom(nFloat, 1e6);
        std::vector<Real> fixedRates(nFixed, 0.04);

        auto swap = ext::make_shared<NonstandardSwap>(
            Swap::Payer, fixedNom, floatNom, fixedSch, fixedRates,
            FIXED_DC, floatSch, idx, 1.0, 0.0, DC);
        NonstandardSwaption nsw(swap, exercise,
                                Settlement::Physical, Settlement::PhysicalOTC);

        out.addCase("is_expired_past_exercise",
            {{"evalDate", "2026-01-15"}, {"exerciseDate", "2025-01-14"},
             {"expectExpired", true}},
            {{"isExpired", nsw.isExpired()}});
    }

    // ── Cases 9-16: grid of exercise/swap tenors ──────────────────────────────
    struct GridCase { int exerciseYears; int swapYears; Settlement::Type st; Settlement::Method sm; };
    std::vector<GridCase> grid = {
        {1, 2,  Settlement::Physical, Settlement::PhysicalOTC},
        {1, 5,  Settlement::Cash,     Settlement::ParYieldCurve},
        {2, 5,  Settlement::Physical, Settlement::PhysicalOTC},
        {2, 10, Settlement::Cash,     Settlement::CollateralizedCashPrice},
        {3, 5,  Settlement::Physical, Settlement::PhysicalCleared},
        {5, 5,  Settlement::Cash,     Settlement::ParYieldCurve},
        {5, 10, Settlement::Physical, Settlement::PhysicalOTC},
        {10, 5, Settlement::Cash,     Settlement::CollateralizedCashPrice},
    };
    for (std::size_t i = 0; i < grid.size(); ++i) {
        const auto& gc = grid[i];
        Date start = CAL.advance(EVAL, Period(gc.exerciseYears, Years));
        Date swapStart = CAL.advance(start, 2, Days);
        Schedule fixedSch = makeSchedule(swapStart, gc.swapYears, Period(1, Years));
        Schedule floatSch = makeSchedule(swapStart, gc.swapYears, Period(3, Months));
        int nFixed = fixedSch.size() - 1;
        int nFloat = floatSch.size() - 1;
        std::vector<Real> fixedNom(nFixed, 1e6);
        std::vector<Real> floatNom(nFloat, 1e6);
        std::vector<Real> fixedRates(nFixed, 0.035);

        auto swap = ext::make_shared<NonstandardSwap>(
            Swap::Receiver, fixedNom, floatNom, fixedSch, fixedRates,
            FIXED_DC, floatSch, idx, 1.0, 0.0, DC);
        auto exercise = ext::make_shared<EuropeanExercise>(start);

        std::string name = "grid_" + std::to_string(i)
                         + "_" + std::to_string(gc.exerciseYears)
                         + "y" + std::to_string(gc.swapYears) + "y";

        Date exDate2 = exercise->dates().back();
        NonstandardSwaption nsw(swap, exercise, gc.st, gc.sm);

        out.addCase(name,
            {{"exerciseYears", gc.exerciseYears}, {"swapYears", gc.swapYears},
             {"settlementType", (int)gc.st}, {"settlementMethod", (int)gc.sm}},
            {{"settlementType",   (int)nsw.settlementType()},
             {"settlementMethod", (int)nsw.settlementMethod()},
             {"swapType",         (int)nsw.type()},
             {"hasUnderlying",    (nsw.underlyingSwap() != nullptr)},
             {"exerciseYear",     exDate2.year()},
             {"exerciseMonth",    (int)exDate2.month()},
             {"isExpired",        nsw.isExpired()},
             {"nFixedCoupons",    (int)nsw.underlyingSwap()->fixedLeg().size()},
             {"nFloatCoupons",    (int)nsw.underlyingSwap()->floatingLeg().size()}});
    }

    out.write();
    return 0;
}
