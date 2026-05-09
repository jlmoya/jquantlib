// migration-harness/cpp/probes/cashflows/multiple_resets_coupon_probe.cpp
// Reference values for MultipleResetsCoupon + Compounding/Averaging pricers
// against QuantLib v1.42.1.
//
// Phase 5d.5-MR.
//
// Builds a 6-month USDLibor index with a flat 3% forward curve and a six
// monthly reset schedule (so resetsPerCoupon=3 yields two 3-month coupons
// of 6 weekly fixings each, etc.).  We use a minimal scenario:
//
//   - Eval date 2026-04-01
//   - Schedule: monthly resets from 2026-04-01 to 2026-10-01 (6 sub-periods).
//   - Single coupon spanning the whole period via MultipleResetsCoupon
//     directly, fixingDays=2, no rate spread.
//   - Compounding swapletRate (= compounded forward = ((1+f*dt)^N - 1) / T)
//   - Averaging swapletRate (= simple average of forwards weighted by dt)
//
// Then exercises a 2-coupon MultipleResetsLeg with resetsPerCoupon=3 and
// reads coupon[0].rate() under both averaging methods.
//
// The Java test rebuilds the same setup and compares each scalar at TIGHT.

#include <cstdio>
#include <ql/version.hpp>
#include "../common.hpp"

#include <ql/cashflows/multipleresetscoupon.hpp>
#include <ql/cashflows/rateaveraging.hpp>
#include <ql/indexes/ibor/usdlibor.hpp>
#include <ql/instruments/multipleresetsswap.hpp>
#include <ql/pricingengines/swap/discountingswapengine.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/time/calendars/unitedstates.hpp>
#include <ql/time/daycounters/actual360.hpp>

using namespace jqml_harness;
using namespace QuantLib;

int main() {
    ReferenceWriter out("cashflows/multiple_resets_coupon",
                        QL_VERSION, "multiple_resets_coupon_probe");

    // ---------- Common setup ----------
    Date evalDate(1, April, 2026);
    Settings::instance().evaluationDate() = evalDate;

    Calendar calendar = UnitedStates(UnitedStates::GovernmentBond);
    DayCounter dc = Actual360();

    // Flat 3% forward curve.
    Handle<YieldTermStructure> ytsHandle(
        ext::make_shared<FlatForward>(evalDate, 0.03, dc, Compounded, Annual));

    // 1-month USDLibor (matches our monthly reset schedule).
    auto idx = ext::make_shared<USDLibor>(Period(1, Months), ytsHandle);
    // The first sub-period's fixing date is 2 business days before April 1 2026,
    // i.e. March 30, 2026 — which is in the past relative to evalDate. Supply
    // a historical fixing so the pricer doesn't need to forecast.
    idx->addFixing(Date(30, March, 2026), 0.025);

    // 6-period reset schedule (monthly), first date == coupon start.
    Schedule resetSchedule(Date(1, April, 2026), Date(1, October, 2026),
                           Period(1, Months), calendar, ModifiedFollowing,
                           ModifiedFollowing,
                           DateGeneration::Forward, false);

    // ---------- Case 1: single MultipleResetsCoupon, Compound ----------
    Real nominal = 1.0e6;
    Date paymentDate = calendar.adjust(resetSchedule.dates().back(),
                                       ModifiedFollowing);

    MultipleResetsCoupon coupon(paymentDate, nominal, resetSchedule,
                                /* fixingDays */ 2, idx,
                                /* gearing  */ 1.0,
                                /* couponSpread */ 0.0,
                                /* rateSpread   */ 0.0);

    auto compoundPricer = ext::make_shared<CompoundingMultipleResetsPricer>();
    coupon.setPricer(compoundPricer);
    Real compoundRate   = coupon.rate();
    Real compoundAmount = coupon.amount();

    auto averagePricer = ext::make_shared<AveragingMultipleResetsPricer>();
    coupon.setPricer(averagePricer);
    Real averageRate   = coupon.rate();
    Real averageAmount = coupon.amount();

    // Capture sub-period accruals (dt) and value dates for the Java test
    // to verify schedule-derived structure.
    auto valueDates = coupon.valueDates();
    auto fixingDates = coupon.fixingDates();
    auto dts = coupon.dt();

    json valueDatesArr = json::array();
    for (const auto& d : valueDates) valueDatesArr.push_back(d.serialNumber());
    json fixingDatesArr = json::array();
    for (const auto& d : fixingDates) fixingDatesArr.push_back(d.serialNumber());
    json dtArr = json::array();
    for (const auto& dt : dts) dtArr.push_back(dt);

    out.addCase("single_coupon_compound_vs_average",
        {
            {"evalDate", evalDate.serialNumber()},
            {"nominal", nominal},
            {"fixingDays", 2},
            {"resetSchedule_size", resetSchedule.size()},
            {"forwardRate", 0.03}
        },
        {
            {"compoundRate", compoundRate},
            {"compoundAmount", compoundAmount},
            {"averageRate", averageRate},
            {"averageAmount", averageAmount},
            {"accrualPeriod", coupon.accrualPeriod()},
            {"valueDates", valueDatesArr},
            {"fixingDates", fixingDatesArr},
            {"dt", dtArr},
            {"paymentDate", coupon.date().serialNumber()},
            {"fixingDate", coupon.fixingDate().serialNumber()}
        });

    // ---------- Case 2: 2-coupon MultipleResetsLeg with resetsPerCoupon=3 ----------
    // Build a fresh full reset schedule (same shape as above) and split.
    Leg legCompound = MultipleResetsLeg(resetSchedule, idx, /* resetsPerCoupon */ 3)
        .withNotionals(nominal)
        .withAveragingMethod(RateAveraging::Compound);
    Leg legAverage = MultipleResetsLeg(resetSchedule, idx, /* resetsPerCoupon */ 3)
        .withNotionals(nominal)
        .withAveragingMethod(RateAveraging::Simple);

    auto cpn0Compound = ext::dynamic_pointer_cast<MultipleResetsCoupon>(legCompound[0]);
    auto cpn1Compound = ext::dynamic_pointer_cast<MultipleResetsCoupon>(legCompound[1]);
    auto cpn0Average  = ext::dynamic_pointer_cast<MultipleResetsCoupon>(legAverage[0]);
    auto cpn1Average  = ext::dynamic_pointer_cast<MultipleResetsCoupon>(legAverage[1]);

    out.addCase("leg_two_coupons_resetsPerCoupon3",
        {
            {"resetsPerCoupon", 3},
            {"nominal", nominal}
        },
        {
            {"legSize", static_cast<int>(legCompound.size())},
            {"compound_cpn0_rate", cpn0Compound->rate()},
            {"compound_cpn0_amount", cpn0Compound->amount()},
            {"compound_cpn1_rate", cpn1Compound->rate()},
            {"compound_cpn1_amount", cpn1Compound->amount()},
            {"average_cpn0_rate", cpn0Average->rate()},
            {"average_cpn0_amount", cpn0Average->amount()},
            {"average_cpn1_rate", cpn1Average->rate()},
            {"average_cpn1_amount", cpn1Average->amount()},
            {"compound_cpn0_fixings_count",
             static_cast<int>(cpn0Compound->fixingDates().size())}
        });

    // ---------- Case 3: MultipleResetsSwap NPV ----------
    Schedule fixedSchedule(Date(1, April, 2026), Date(1, October, 2026),
                           Period(6, Months), calendar, ModifiedFollowing,
                           ModifiedFollowing, DateGeneration::Forward, false);
    Real fixedRate = 0.025;
    MultipleResetsSwap swap(
        Swap::Payer, nominal,
        fixedSchedule, fixedRate, dc,
        resetSchedule, idx, /* resetsPerCoupon */ 3,
        /* spread */ 0.0,
        RateAveraging::Compound);

    auto engine = ext::make_shared<DiscountingSwapEngine>(ytsHandle);
    swap.setPricingEngine(engine);

    out.addCase("swap_npv_compound",
        {
            {"fixedRate", fixedRate},
            {"resetsPerCoupon", 3},
            {"nominal", nominal},
            {"averaging", "Compound"}
        },
        {
            {"NPV", swap.NPV()},
            {"fixedLegNPV", swap.fixedLegNPV()},
            {"floatingLegNPV", swap.floatingLegNPV()},
            {"fairRate", swap.fairRate()}
        });

    out.write();
    std::printf("multiple_resets_coupon_probe: 3 cases written to references/cashflows/multiple_resets_coupon.json\n");
    return 0;
}
