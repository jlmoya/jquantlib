// migration-harness/cpp/probes/instruments/floatfloat_swaption_probe.cpp
//
// Phase 2j.5 Track B.2 — FloatFloatSwaption structural fingerprint.
// Oracle: C++ QuantLib v1.42.1.
//
// Tests the structural (non-pricing) aspects of FloatFloatSwaption:
//   - settlementType()    : Settlement::Type enum value (0=Physical, 1=Cash)
//   - settlementMethod()  : Settlement::Method enum value
//   - type()              : FloatFloatSwap::type() (Receiver=0, Payer=1)
//   - underlyingSwap()    : accessor returns non-null
//   - exerciseDate        : exercise.dates().back() (last exercise date)
//   - isExpired           : false when eval < exercise date
//   - nLeg1Coupons        : underlying leg1().size()
//   - nLeg2Coupons        : underlying leg2().size()
//
// Fixture: eval=2026-01-15, TARGET calendar, Euribor3M + Euribor6M,
//          FlatForward 4% Continuous Actual365Fixed.

#include <ql/version.hpp>

#include <ql/exercise.hpp>
#include <ql/indexes/ibor/euribor.hpp>
#include <ql/instruments/floatfloatswap.hpp>
#include <ql/instruments/floatfloatswaption.hpp>
#include <ql/settings.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/time/calendars/target.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>
#include <ql/time/schedule.hpp>

#include "common.hpp"

#include <vector>
#include <string>

using namespace QuantLib;
using namespace jqml_harness;

namespace {

const Date EVAL(15, January, 2026);
const DayCounter DC  = Actual365Fixed();
const Calendar   CAL = TARGET();
const Real FLAT_RATE = 0.04;

Handle<YieldTermStructure> makeTS() {
    return Handle<YieldTermStructure>(
        ext::make_shared<FlatForward>(EVAL, FLAT_RATE, DC, Continuous, Annual));
}

ext::shared_ptr<IborIndex> makeIdx3M(const Handle<YieldTermStructure>& ts) {
    return ext::make_shared<Euribor3M>(ts);
}

ext::shared_ptr<IborIndex> makeIdx6M(const Handle<YieldTermStructure>& ts) {
    return ext::make_shared<Euribor6M>(ts);
}

Schedule makeSchedule(const Date& start, int years, const Period& tenor) {
    Date end = CAL.advance(start, Period(years, Years));
    return Schedule(start, end, tenor, CAL,
                    ModifiedFollowing, ModifiedFollowing,
                    DateGeneration::Forward, false);
}

// Build a simple FloatFloatSwap (Ibor3M vs Ibor6M) with uniform nominals.
ext::shared_ptr<FloatFloatSwap> makeSwap(
    Swap::Type type,
    const Date& swapStart, int swapYears,
    const ext::shared_ptr<IborIndex>& idx1,
    const ext::shared_ptr<IborIndex>& idx2,
    double nominal,
    double spread1 = 0.0,
    double spread2 = 0.0)
{
    Schedule sch1 = makeSchedule(swapStart, swapYears, Period(3, Months));
    Schedule sch2 = makeSchedule(swapStart, swapYears, Period(6, Months));
    int n1 = (int)sch1.size() - 1;
    int n2 = (int)sch2.size() - 1;
    std::vector<Real> nom1(n1, nominal);
    std::vector<Real> nom2(n2, nominal);
    std::vector<Real> sp1(n1, spread1);
    std::vector<Real> sp2(n2, spread2);
    std::vector<Real> gear1(n1, 1.0);
    std::vector<Real> gear2(n2, 1.0);
    std::vector<Real> cappedRate1(n1, Null<Real>());
    std::vector<Real> flooredRate1(n1, Null<Real>());
    std::vector<Real> cappedRate2(n2, Null<Real>());
    std::vector<Real> flooredRate2(n2, Null<Real>());

    return ext::make_shared<FloatFloatSwap>(
        type, nom1, nom2, sch1, idx1, DC, sch2, idx2, DC,
        false, false,
        gear1, sp1, cappedRate1, flooredRate1,
        gear2, sp2, cappedRate2, flooredRate2);
}

json swaptionFingerprint(
    const ext::shared_ptr<FloatFloatSwap>& swap,
    const ext::shared_ptr<Exercise>& exercise,
    Settlement::Type settlType,
    Settlement::Method settlMethod)
{
    FloatFloatSwaption ffs(swap, exercise, settlType, settlMethod);

    Date exDate = ffs.exercise()->dates().back();

    // type(): Receiver=0, Payer=1 (Swap::Type enum)
    int swapTypeInt;
    if (ffs.type() == Swap::Receiver) swapTypeInt = 0;
    else swapTypeInt = 1;

    return {
        {"settlementType",   (int)ffs.settlementType()},
        {"settlementMethod", (int)ffs.settlementMethod()},
        {"swapType",         swapTypeInt},
        {"hasUnderlying",    (ffs.underlyingSwap().get() != nullptr)},
        {"exerciseYear",     exDate.year()},
        {"exerciseMonth",    (int)exDate.month()},
        {"exerciseDay",      exDate.dayOfMonth()},
        {"isExpired",        ffs.isExpired()},
        {"nLeg1Coupons",     (int)ffs.underlyingSwap()->leg1().size()},
        {"nLeg2Coupons",     (int)ffs.underlyingSwap()->leg2().size()}
    };
}

} // namespace

int main() {
    ReferenceWriter out("instruments/floatfloat_swaption",
                        QL_VERSION, "floatfloat_swaption_probe");

    Settings::instance().evaluationDate() = EVAL;
    auto ts   = makeTS();
    auto idx3 = makeIdx3M(ts);
    auto idx6 = makeIdx6M(ts);

    // ── Case 1: 1y European, 5y Receiver, Physical/PhysicalOTC ──────────────
    {
        Date start    = CAL.advance(EVAL, Period(1, Years));
        Date swapStart = CAL.advance(start, 2, Days);
        auto swap = makeSwap(Swap::Receiver, swapStart, 5, idx3, idx6, 1e6);
        auto ex   = ext::make_shared<EuropeanExercise>(start);
        out.addCase("receiver_physical_otc_1y5y",
            {{"swapType","Receiver"},{"exerciseYears",1},{"swapYears",5},
             {"settlementType","Physical"},{"settlementMethod","PhysicalOTC"}},
            swaptionFingerprint(swap, ex, Settlement::Physical, Settlement::PhysicalOTC));
    }

    // ── Case 2: 2y European, 3y Payer, Physical/PhysicalOTC ─────────────────
    {
        Date start     = CAL.advance(EVAL, Period(2, Years));
        Date swapStart = CAL.advance(start, 2, Days);
        auto swap = makeSwap(Swap::Payer, swapStart, 3, idx3, idx6, 5e5);
        auto ex   = ext::make_shared<EuropeanExercise>(start);
        out.addCase("payer_physical_otc_2y3y",
            {{"swapType","Payer"},{"exerciseYears",2},{"swapYears",3},
             {"settlementType","Physical"},{"settlementMethod","PhysicalOTC"}},
            swaptionFingerprint(swap, ex, Settlement::Physical, Settlement::PhysicalOTC));
    }

    // ── Case 3: 1y European, 10y Receiver, Cash/CollateralizedCashPrice ──────
    {
        Date start     = CAL.advance(EVAL, Period(1, Years));
        Date swapStart = CAL.advance(start, 2, Days);
        auto swap = makeSwap(Swap::Receiver, swapStart, 10, idx3, idx6, 1e6);
        auto ex   = ext::make_shared<EuropeanExercise>(start);
        out.addCase("receiver_cash_ccp_1y10y",
            {{"swapType","Receiver"},{"exerciseYears",1},{"swapYears",10},
             {"settlementType","Cash"},{"settlementMethod","CollateralizedCashPrice"}},
            swaptionFingerprint(swap, ex, Settlement::Cash, Settlement::CollateralizedCashPrice));
    }

    // ── Case 4: 3y European, 7y Payer, Cash/ParYieldCurve ───────────────────
    {
        Date start     = CAL.advance(EVAL, Period(3, Years));
        Date swapStart = CAL.advance(start, 2, Days);
        auto swap = makeSwap(Swap::Payer, swapStart, 7, idx3, idx6, 2e6, 0.0005, 0.001);
        auto ex   = ext::make_shared<EuropeanExercise>(start);
        out.addCase("payer_cash_paryieldcurve_3y7y",
            {{"swapType","Payer"},{"exerciseYears",3},{"swapYears",7},
             {"settlementType","Cash"},{"settlementMethod","ParYieldCurve"}},
            swaptionFingerprint(swap, ex, Settlement::Cash, Settlement::ParYieldCurve));
    }

    // ── Case 5: 5y European, 5y Payer, Physical/PhysicalCleared ─────────────
    {
        Date start     = CAL.advance(EVAL, Period(5, Years));
        Date swapStart = CAL.advance(start, 2, Days);
        auto swap = makeSwap(Swap::Payer, swapStart, 5, idx3, idx6, 1e6);
        auto ex   = ext::make_shared<EuropeanExercise>(start);
        out.addCase("payer_physical_cleared_5y5y",
            {{"swapType","Payer"},{"exerciseYears",5},{"swapYears",5},
             {"settlementType","Physical"},{"settlementMethod","PhysicalCleared"}},
            swaptionFingerprint(swap, ex, Settlement::Physical, Settlement::PhysicalCleared));
    }

    // ── Case 6: isExpired when exercise date is in the past ─────────────────
    {
        Date pastDate(14, January, 2025);
        auto ex        = ext::make_shared<EuropeanExercise>(pastDate);
        Date swapStart = CAL.advance(pastDate, 2, Days);
        auto swap = makeSwap(Swap::Payer, swapStart, 5, idx3, idx6, 1e6);
        FloatFloatSwaption ffs(swap, ex,
                               Settlement::Physical, Settlement::PhysicalOTC);
        out.addCase("is_expired_past_exercise",
            {{"evalDate","2026-01-15"},{"exerciseDate","2025-01-14"},
             {"expectExpired",true}},
            {{"isExpired", ffs.isExpired()}});
    }

    // ── Cases 7-14: grid of exercise/swap tenors ─────────────────────────────
    struct GridCase { int exerciseYears; int swapYears; Settlement::Type st; Settlement::Method sm; };
    std::vector<GridCase> grid = {
        {1,  2, Settlement::Physical, Settlement::PhysicalOTC},
        {1,  5, Settlement::Cash,     Settlement::ParYieldCurve},
        {2,  5, Settlement::Physical, Settlement::PhysicalOTC},
        {2, 10, Settlement::Cash,     Settlement::CollateralizedCashPrice},
        {3,  5, Settlement::Physical, Settlement::PhysicalCleared},
        {5,  5, Settlement::Cash,     Settlement::ParYieldCurve},
        {5, 10, Settlement::Physical, Settlement::PhysicalOTC},
        {10, 5, Settlement::Cash,     Settlement::CollateralizedCashPrice},
    };
    for (std::size_t i = 0; i < grid.size(); ++i) {
        const auto& gc = grid[i];
        Date start     = CAL.advance(EVAL, Period(gc.exerciseYears, Years));
        Date swapStart = CAL.advance(start, 2, Days);
        auto swap = makeSwap(Swap::Receiver, swapStart, gc.swapYears, idx3, idx6, 1e6);
        auto ex   = ext::make_shared<EuropeanExercise>(start);

        std::string name = "grid_" + std::to_string(i)
                         + "_" + std::to_string(gc.exerciseYears)
                         + "y" + std::to_string(gc.swapYears) + "y";

        Date exDate = ex->dates().back();
        FloatFloatSwaption ffs(swap, ex, gc.st, gc.sm);

        int swapTypeInt = (ffs.type() == Swap::Receiver) ? 0 : 1;

        out.addCase(name,
            {{"exerciseYears", gc.exerciseYears}, {"swapYears", gc.swapYears},
             {"settlementType", (int)gc.st}, {"settlementMethod", (int)gc.sm}},
            {{"settlementType",   (int)ffs.settlementType()},
             {"settlementMethod", (int)ffs.settlementMethod()},
             {"swapType",         swapTypeInt},
             {"hasUnderlying",    (ffs.underlyingSwap().get() != nullptr)},
             {"exerciseYear",     exDate.year()},
             {"exerciseMonth",    (int)exDate.month()},
             {"isExpired",        ffs.isExpired()},
             {"nLeg1Coupons",     (int)ffs.underlyingSwap()->leg1().size()},
             {"nLeg2Coupons",     (int)ffs.underlyingSwap()->leg2().size()}});
    }

    out.write();
    return 0;
}
