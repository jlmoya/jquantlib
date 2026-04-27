// migration-harness/cpp/probes/pricingengines/capfloor/bacheliercapfloorengine_probe.cpp
//
// Probe for Phase 2f WI-1: BachelierCapFloorEngine NPV fingerprint with a
// constant absolute (normal) vol of 1%. Cross-validates Java's
// BachelierCapFloorEngine + BlackFormula.bachelierBlackFormula on a
// vanilla 5Y Euribor3M cap struck at 5%.

#include <ql/version.hpp>

#include <ql/cashflows/iborcoupon.hpp>
#include <ql/indexes/ibor/euribor.hpp>
#include <ql/instruments/capfloor.hpp>
#include <ql/pricingengines/capfloor/bacheliercapfloorengine.hpp>
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
    ReferenceWriter out("pricingengines/capfloor/bacheliercapfloorengine",
                        QL_VERSION, "bacheliercapfloorengine_probe");

    // --- Fixture (must match Java BachelierCapFloorEngineTest exactly) ---
    const Date eval(15, January, 2026);
    Settings::instance().evaluationDate() = eval;

    const DayCounter dc = Actual365Fixed();
    const Calendar cal = TARGET();
    const Real flatRate = 0.05;
    const Volatility normalVol = 0.01;  // 100 bp, absolute (Bachelier)
    const Rate capStrike = 0.05;
    const Real nominal = 100.0;

    const Handle<YieldTermStructure> ts(
        ext::make_shared<FlatForward>(eval, flatRate, dc, Continuous, Annual));

    const auto idx = ext::make_shared<Euribor3M>(ts);

    // Same shifted-start trick as analyticcapfloorengine_probe: avoid
    // forcing an IborIndex past-fixing lookup at eval (Java NPEs in that
    // path).
    const Date scheduleStart = eval + Period(3, Months);
    Schedule schedule(scheduleStart, eval + Period(5, Years), Period(3, Months),
                      cal, ModifiedFollowing, ModifiedFollowing,
                      DateGeneration::Forward, false);

    // Use the index's default fixingDays (2 for Euribor3M) rather than
    // forcing 0; see analyticcapfloorengine_probe.cpp for the rationale.
    Leg floatingLeg = IborLeg(schedule, idx)
        .withNotionals(std::vector<Real>(1, nominal))
        .withPaymentAdjustment(idx->businessDayConvention());

    auto cap = ext::make_shared<Cap>(floatingLeg,
                                     std::vector<Rate>(1, capStrike));

    cap->setPricingEngine(
        ext::make_shared<BachelierCapFloorEngine>(ts, normalVol, dc));

    json inputs = {
        {"eval_date", "2026-01-15"},
        {"flat_rate", flatRate},
        {"normal_vol", normalVol},
        {"cap_strike", capStrike},
        {"cap_years", 5},
        {"index_tenor_months", 3},
        {"nominal", nominal},
        {"calendar", "TARGET"},
        {"yts_day_counter", "Actual/365 Fixed"}
    };
    json expected = {
        {"bachelier_cap_npv", cap->NPV()}
    };
    out.addCase("bachelier_5y_cap_at_5pct", inputs, expected);

    out.write();
    return 0;
}
