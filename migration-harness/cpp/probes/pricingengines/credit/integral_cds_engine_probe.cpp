// migration-harness/cpp/probes/pricingengines/credit/integral_cds_engine_probe.cpp
//
// Probe for Phase 3c L1 Track B: IntegralCdsEngine NPV / fair-spread
// fingerprint over a representative CDS lattice.
//
// Captures CDS NPV, defaultLegNPV, couponLegNPV, fairSpread, fairUpfront,
// upfrontNPV under IntegralCdsEngine for the same Buyer/Seller, running-only,
// and upfront+running CDS instances as cds_engine_probe but priced via the
// integral engine with two integration steps (1 day, 1 week).

#include <ql/version.hpp>

#include <ql/instruments/creditdefaultswap.hpp>
#include <ql/pricingengines/credit/integralcdsengine.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/settings.hpp>
#include <ql/termstructures/credit/flathazardrate.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/time/calendars/nullcalendar.hpp>
#include <ql/time/daycounters/actual360.hpp>
#include <ql/time/schedule.hpp>

#include "common.hpp"

using namespace QuantLib;
using namespace jqml_harness;

namespace {

struct IntegralCdsCase {
    std::string name;
    Protection::Side side;
    Real notional;
    Rate spread;
    int years;
    Real hazardRate;
    Real recoveryRate;
    Real flatRate;        // discount curve flat continuous rate
    bool hasUpfront;
    Real upfront;
    int stepDays;         // integration step in days (1 or 7)
};

void runCase(ReferenceWriter& out, const IntegralCdsCase& c, const Date& eval) {
    Settings::instance().evaluationDate() = eval;

    DayCounter dc = Actual360();
    Calendar cal = NullCalendar();

    Handle<YieldTermStructure> discount(ext::make_shared<FlatForward>(
        eval, c.flatRate, dc, Continuous, Annual));
    Handle<DefaultProbabilityTermStructure> probability(
        ext::make_shared<FlatHazardRate>(eval, c.hazardRate, dc));

    Date start = eval;
    Date end = eval + Period(c.years, Years);
    Schedule schedule(start, end, Period(Quarterly), cal,
                      Following, Following,
                      DateGeneration::Forward, false);

    ext::shared_ptr<CreditDefaultSwap> cds;
    if (c.hasUpfront) {
        cds = ext::make_shared<CreditDefaultSwap>(
            c.side, c.notional, c.upfront, c.spread, schedule, Following,
            dc, true, true, start, Date(),
            ext::shared_ptr<Claim>(),
            DayCounter(), true, eval);
    } else {
        cds = ext::make_shared<CreditDefaultSwap>(
            c.side, c.notional, c.spread, schedule, Following, dc,
            true, true, start);
    }
    cds->setPricingEngine(ext::make_shared<IntegralCdsEngine>(
        Period(c.stepDays, Days), probability, c.recoveryRate, discount));

    json inputs = {
        {"side", (c.side == Protection::Buyer ? "Buyer" : "Seller")},
        {"notional", c.notional},
        {"spread", c.spread},
        {"years", c.years},
        {"hazard_rate", c.hazardRate},
        {"recovery_rate", c.recoveryRate},
        {"flat_rate", c.flatRate},
        {"has_upfront", c.hasUpfront},
        {"upfront", c.hasUpfront ? c.upfront : 0.0},
        {"step_days", c.stepDays},
        {"eval_date", "2026-05-15"},
        {"calendar", "NullCalendar"},
        {"day_counter", "Actual/360"},
        {"frequency", "Quarterly"}
    };

    Real fairSpread = 0.0;
    bool fairSpreadValid = (cds->couponLegNPV() != 0.0);
    if (fairSpreadValid) {
        fairSpread = cds->fairSpread();
    }

    Real fairUpfront = 0.0;
    bool fairUpfrontValid = c.hasUpfront;
    if (fairUpfrontValid) {
        try {
            fairUpfront = cds->fairUpfront();
        } catch (...) {
            fairUpfrontValid = false;
        }
    }

    json expected = {
        {"npv", cds->NPV()},
        {"default_leg_npv", cds->defaultLegNPV()},
        {"coupon_leg_npv", cds->couponLegNPV()},
        {"upfront_npv", cds->upfrontNPV()},
        {"accrual_rebate_npv", cds->accrualRebateNPV()},
        {"fair_spread", fairSpread},
        {"fair_spread_valid", fairSpreadValid},
        {"fair_upfront", fairUpfront},
        {"fair_upfront_valid", fairUpfrontValid}
    };

    out.addCase(c.name, inputs, expected);
}

} // anonymous

int main() {
    ReferenceWriter out("pricingengines/credit/integral_cds_engine",
                        QL_VERSION, "integral_cds_engine_probe");

    const Date eval(15, May, 2026);

    // Mirror cds_engine_probe.cpp lattice but priced with IntegralCdsEngine
    // at two granularities: 1-day and 1-week steps.
    runCase(out, {
        "buyer_5y_running_only_at_par_step1d",
        Protection::Buyer, 1.0e7, 0.0150, 5,
        0.025, 0.4, 0.03,
        false, 0.0, 1
    }, eval);

    runCase(out, {
        "buyer_5y_running_only_at_par_step7d",
        Protection::Buyer, 1.0e7, 0.0150, 5,
        0.025, 0.4, 0.03,
        false, 0.0, 7
    }, eval);

    runCase(out, {
        "seller_5y_running_only_at_par_step1d",
        Protection::Seller, 1.0e7, 0.0150, 5,
        0.025, 0.4, 0.03,
        false, 0.0, 1
    }, eval);

    runCase(out, {
        "buyer_3y_running_only_offmarket_step7d",
        Protection::Buyer, 5.0e6, 0.0050, 3,
        0.020, 0.4, 0.025,
        false, 0.0, 7
    }, eval);

    runCase(out, {
        "buyer_5y_upfront_plus_running_step1d",
        Protection::Buyer, 1.0e7, 0.0100, 5,
        0.020, 0.4, 0.03,
        true, 0.025, 1
    }, eval);

    runCase(out, {
        "seller_2y_upfront_plus_running_step7d",
        Protection::Seller, 1.0e6, 0.0080, 2,
        0.015, 0.4, 0.02,
        true, 0.010, 7
    }, eval);

    runCase(out, {
        "buyer_5y_high_recovery_step1d",
        Protection::Buyer, 1.0e7, 0.0150, 5,
        0.025, 0.7, 0.03,
        false, 0.0, 1
    }, eval);

    runCase(out, {
        "buyer_5y_low_hazard_step7d",
        Protection::Buyer, 1.0e7, 0.0150, 5,
        0.0010, 0.4, 0.03,
        false, 0.0, 7
    }, eval);

    out.write();
    return 0;
}
