// migration-harness/cpp/probes/pricingengines/capfloor/analyticcapfloorengine_probe.cpp
//
// Probe for Phase 2f WI-1: AnalyticCapFloorEngine NPV fingerprint with
// HullWhite model (a TermStructureConsistentModel implementing AffineModel,
// so the engine takes its referenceDate/dayCounter from the model's curve).
//
// Captures Cap.NPV() under AnalyticCapFloorEngine(hw, ts) for a vanilla
// 5Y Euribor3M cap struck at 5%.

#include <ql/version.hpp>

#include <ql/cashflows/iborcoupon.hpp>
#include <ql/indexes/ibor/euribor.hpp>
#include <ql/instruments/capfloor.hpp>
#include <ql/models/shortrate/onefactormodels/hullwhite.hpp>
#include <ql/pricingengines/capfloor/analyticcapfloorengine.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/settings.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/time/calendars/target.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>
#include <ql/time/schedule.hpp>

#include "common.hpp"

#include <vector>

using namespace QuantLib;
using namespace jqml_harness;

int main() {
    ReferenceWriter out("pricingengines/capfloor/analyticcapfloorengine",
                        QL_VERSION, "analyticcapfloorengine_probe");

    // --- Fixture (must match Java AnalyticCapFloorEngineTest exactly) ---
    const Date eval(15, January, 2026);
    Settings::instance().evaluationDate() = eval;

    const DayCounter dc = Actual365Fixed();
    const Calendar cal = TARGET();
    const Real flatRate = 0.05;
    const Real hwA = 0.1;
    const Real hwSigma = 0.01;
    const Rate capStrike = 0.05;
    const Real nominal = 100.0;

    const Handle<YieldTermStructure> ts(
        ext::make_shared<FlatForward>(eval, flatRate, dc, Continuous, Annual));

    const auto idx = ext::make_shared<Euribor3M>(ts);

    // Start the schedule one period after eval so the first fixing date is
    // strictly in the future. This avoids relying on an
    // IborIndex past-fixing lookup (Java's IborIndex.fixing path NPEs on
    // missing-but-required-on-eval fixings, vs C++'s TimeSeries returning
    // Null<Real> and falling through to the forecast); keeps the fixture
    // identical between Java and C++ otherwise.
    const Date scheduleStart = eval + Period(3, Months);
    Schedule schedule(scheduleStart, eval + Period(5, Years), Period(3, Months),
                      cal, ModifiedFollowing, ModifiedFollowing,
                      DateGeneration::Forward, false);

    Leg floatingLeg = IborLeg(schedule, idx)
        .withNotionals(std::vector<Real>(1, nominal))
        .withPaymentAdjustment(idx->businessDayConvention())
        .withFixingDays(0);

    auto cap = ext::make_shared<Cap>(floatingLeg,
                                     std::vector<Rate>(1, capStrike));

    auto hw = ext::make_shared<HullWhite>(ts, hwA, hwSigma);
    cap->setPricingEngine(
        ext::make_shared<AnalyticCapFloorEngine>(hw, ts));

    json inputs = {
        {"eval_date", "2026-01-15"},
        {"flat_rate", flatRate},
        {"hw_a", hwA},
        {"hw_sigma", hwSigma},
        {"cap_strike", capStrike},
        {"cap_years", 5},
        {"index_tenor_months", 3},
        {"nominal", nominal},
        {"calendar", "TARGET"},
        {"yts_day_counter", "Actual/365 Fixed"}
    };
    json expected = {
        {"analytic_cap_npv", cap->NPV()}
    };
    out.addCase("hw_5y_cap_at_5pct", inputs, expected);

    out.write();
    return 0;
}
