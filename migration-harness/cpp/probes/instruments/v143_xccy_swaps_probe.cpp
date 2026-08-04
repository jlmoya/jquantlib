// migration-harness/cpp/probes/instruments/v143_xccy_swaps_probe.cpp
//
// Reference values for the constant-notional cross-currency swap family
// introduced in C++ QuantLib v1.43:
//
//   * ConstNotionalCrossCurrencySwap                  (base, explicit legs)
//   * ConstNotionalCrossCurrencyBasisSwap             (float/float)
//   * ConstNotionalCrossCurrencyFixedVsFloatingSwap   (fixed/float)
//   * DiscountingConstNotionalCrossCurrencySwapEngine
//
// Design notes
// ------------
// The upstream test-suite files pin these instruments against hard-coded
// 27-point market curves and the USDLibor / GBPLibor / SOFR / SONIA index
// definitions. Reproducing that verbatim would make the reference depend on
// index *definitions* as much as on the swap logic, so a port failing here
// would not tell us which of the two broke.
//
// Instead this probe builds every input explicitly — two discount curves and
// one projection curve from literal (date, discount-factor) tables, and
// generic IborIndex / OvernightIndex instances constructed inline. Every one
// of those is trivially reproducible in Java and Python, so a mismatch
// localises to the swap or the engine, which is the point.
//
// What is pinned, per case: NPV, and per leg legNPV / legBPS / inCcyLegNPV /
// inCcyLegBPS / npvDateDiscounts / startDiscounts / endDiscounts, plus the
// full (date, amount) listing of every leg. The cashflow listing matters:
// without it an NPV match can hide two compensating errors in leg
// construction, and notional-exchange placement in particular is easy to get
// subtly wrong.
//
// Deliberately covered edge paths, each of which the engine special-cases:
//   * spotFXSettleDate != referenceDate  -> forward-FX adjustment of the rate
//   * npvDate          != referenceDate  -> NPV rebased off the reference date
//   * an overnight-index basis leg with compoundingSpreadDaily enabled

#include <ql/version.hpp>

#include <ql/cashflows/cashflows.hpp>
#include <ql/cashflows/fixedratecoupon.hpp>
#include <ql/cashflows/iborcoupon.hpp>
#include <ql/cashflows/simplecashflow.hpp>
#include <ql/currencies/america.hpp>
#include <ql/currencies/europe.hpp>
#include <ql/indexes/iborindex.hpp>
#include <ql/instruments/constnotionalcrosscurrencybasisswap.hpp>
#include <ql/instruments/constnotionalcrosscurrencyfixedvsfloatingswap.hpp>
#include <ql/instruments/constnotionalcrosscurrencyswap.hpp>
#include <ql/pricingengines/swap/discountingconstnotionalcrosscurrencyswapengine.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/termstructures/yield/discountcurve.hpp>
#include <ql/time/calendars/target.hpp>
#include <ql/time/daycounters/actual360.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>
#include <ql/time/schedule.hpp>

#include "common.hpp"

using namespace QuantLib;
using namespace jqml_harness;

namespace {

const Date kToday(11, September, 2018);

// --- curve construction -----------------------------------------------------
//
// Small, explicit discount-factor tables. DiscountCurve interpolates the log of
// the discount factors linearly, so these are exactly reproducible anywhere.

Handle<YieldTermStructure> discountCurve(const std::vector<Real>& dfs) {
    const std::vector<Date> dates = {
        Date(11, September, 2018), Date(11, December, 2018), Date(11, March, 2019),
        Date(11, September, 2019), Date(11, September, 2020), Date(13, September, 2021),
        Date(12, September, 2022), Date(11, September, 2023), Date(11, September, 2028),
    };
    QL_REQUIRE(dates.size() == dfs.size(), "curve table size mismatch");
    return Handle<YieldTermStructure>(
        ext::make_shared<DiscountCurve>(dates, dfs, Actual365Fixed()));
}

Handle<YieldTermStructure> usdDiscount() {
    return discountCurve({1.0, 0.9941, 0.9888, 0.9757, 0.9486, 0.9228, 0.8983, 0.8747, 0.7630});
}

Handle<YieldTermStructure> eurDiscount() {
    return discountCurve({1.0, 0.9998, 0.9995, 0.9986, 0.9955, 0.9910, 0.9850, 0.9775, 0.9210});
}

Handle<YieldTermStructure> usdProjection() {
    return discountCurve({1.0, 0.9935, 0.9871, 0.9727, 0.9433, 0.9148, 0.8876, 0.8615, 0.7386});
}

Handle<YieldTermStructure> eurProjection() {
    return discountCurve({1.0, 0.9996, 0.9991, 0.9978, 0.9938, 0.9881, 0.9808, 0.9720, 0.9040});
}

// --- index construction -----------------------------------------------------
//
// Generic indexes rather than named ones (USDLibor etc.), so the reference does
// not depend on a currency-specific index definition also being ported exactly.

ext::shared_ptr<IborIndex> usdIbor3M() {
    return ext::make_shared<IborIndex>("USD-XCCY-3M", Period(3, Months), 2, USDCurrency(), TARGET(),
                                       ModifiedFollowing, false, Actual360(), usdProjection());
}

ext::shared_ptr<IborIndex> eurIbor3M() {
    return ext::make_shared<IborIndex>("EUR-XCCY-3M", Period(3, Months), 2, EURCurrency(), TARGET(),
                                       ModifiedFollowing, false, Actual360(), eurProjection());
}

ext::shared_ptr<OvernightIndex> usdOn() {
    return ext::make_shared<OvernightIndex>("USD-XCCY-ON", 0, USDCurrency(), TARGET(), Actual360(),
                                            usdProjection());
}

ext::shared_ptr<OvernightIndex> eurOn() {
    return ext::make_shared<OvernightIndex>("EUR-XCCY-ON", 0, EURCurrency(), TARGET(), Actual360(),
                                            eurProjection());
}

Schedule quarterly(const Date& start, const Date& end) {
    return Schedule(start, end, Period(3, Months), TARGET(), ModifiedFollowing, ModifiedFollowing,
                    DateGeneration::Forward, false);
}

Schedule annual(const Date& start, const Date& end) {
    return Schedule(start, end, Period(1, Years), TARGET(), ModifiedFollowing, ModifiedFollowing,
                    DateGeneration::Forward, false);
}

// --- result extraction ------------------------------------------------------

json legCashflows(const Leg& leg) {
    json flows = json::array();
    for (const auto& cf : leg) {
        json entry{
            {"dateSerial", cf->date().serialNumber()},
            {"amount", cf->amount()},
        };
        if (auto c = ext::dynamic_pointer_cast<Coupon>(cf)) {
            entry["isCoupon"] = true;
            entry["nominal"] = c->nominal();
            entry["accrualStartSerial"] = c->accrualStartDate().serialNumber();
            entry["accrualEndSerial"] = c->accrualEndDate().serialNumber();
            entry["accrualPeriod"] = c->accrualPeriod();
            entry["rate"] = c->rate();
        } else {
            entry["isCoupon"] = false;
        }
        flows.push_back(entry);
    }
    return flows;
}

// Null<Real>() must not reach the JSON as 1e308 noise — emit it as null so the
// consuming test can distinguish "engine did not provide this" from a value.
json orNull(Real v) {
    if (v == Null<Real>())
        return nullptr;
    return v;
}

json describeSwap(const ConstNotionalCrossCurrencySwap& swap, Size numLegs) {
    json legs = json::array();
    for (Size i = 0; i < numLegs; ++i) {
        legs.push_back(json{
            {"currency", swap.legCurrency(i).code()},
            {"legNPV", orNull(swap.legNPV(i))},
            {"legBPS", orNull(swap.legBPS(i))},
            {"inCcyLegNPV", orNull(swap.inCcyLegNPV(i))},
            {"inCcyLegBPS", orNull(swap.inCcyLegBPS(i))},
            {"npvDateDiscounts", orNull(swap.npvDateDiscounts(i))},
            {"startDiscounts", orNull(swap.startDiscounts(i))},
            {"endDiscounts", orNull(swap.endDiscounts(i))},
            {"cashflows", legCashflows(swap.leg(i))},
        });
    }
    return json{
        {"npv", swap.NPV()},
        {"valuationDateSerial", swap.valuationDate().serialNumber()},
        {"startDateSerial", swap.startDate().serialNumber()},
        {"maturityDateSerial", swap.maturityDate().serialNumber()},
        {"legs", legs},
    };
}

// --- swap builders ----------------------------------------------------------

// Fixed/fixed, built through the base class's explicit-leg constructor, with
// the notional exchanges attached by hand exactly as the upstream test does.
ext::shared_ptr<ConstNotionalCrossCurrencySwap> makeFixFix(Real usdNominal, Real spotFx) {
    const Calendar cal = TARGET();
    const Date start = cal.advance(kToday, Period(2, Days));
    const Date end = cal.advance(kToday, Period(5, Years));
    const Schedule sched = quarterly(start, end);
    const DayCounter dc = Actual365Fixed();

    Leg usdLeg = FixedRateLeg(sched)
                     .withNotionals(usdNominal)
                     .withCouponRates(0.0575, dc)
                     .withPaymentAdjustment(ModifiedFollowing)
                     .withPaymentCalendar(cal);
    const Date first = cal.adjust(sched.dates().front(), ModifiedFollowing);
    usdLeg.insert(usdLeg.begin(), ext::make_shared<SimpleCashFlow>(-usdNominal, first));
    usdLeg.push_back(ext::make_shared<SimpleCashFlow>(usdNominal, usdLeg.back()->date()));

    const Real eurNominal = usdNominal * spotFx;
    Leg eurLeg = FixedRateLeg(sched)
                     .withNotionals(eurNominal)
                     .withCouponRates(0.0201, dc)
                     .withPaymentAdjustment(ModifiedFollowing)
                     .withPaymentCalendar(cal);
    eurLeg.insert(eurLeg.begin(), ext::make_shared<SimpleCashFlow>(-eurNominal, first));
    eurLeg.push_back(ext::make_shared<SimpleCashFlow>(eurNominal, eurLeg.back()->date()));

    return ext::make_shared<ConstNotionalCrossCurrencySwap>(usdLeg, USDCurrency(), eurLeg,
                                                            EURCurrency());
}

ext::shared_ptr<ConstNotionalCrossCurrencyBasisSwap> makeBasis(Real usdNominal, Real spotFx,
                                                               bool overnight) {
    const Calendar cal = TARGET();
    const Date start = cal.advance(kToday, Period(2, Days));
    const Date end = cal.advance(kToday, Period(5, Years));
    const Schedule sched = quarterly(start, end);

    if (overnight) {
        return ext::make_shared<ConstNotionalCrossCurrencyBasisSwap>(
            usdNominal, USDCurrency(), sched, usdOn(), 0.0010, 1.0, usdNominal * spotFx,
            EURCurrency(), sched, eurOn(), 0.0025, 1.0,
            /*payPaymentLag*/ 2, /*recPaymentLag*/ 2,
            /*payCompoundSpread*/ true, /*payLookbackDays*/ Null<Natural>(),
            /*payObservationShift*/ false, /*payLockoutDays*/ 0, RateAveraging::Compound,
            /*recCompoundSpread*/ false, /*recLookbackDays*/ Null<Natural>(),
            /*recObservationShift*/ false, /*recLockoutDays*/ 0, RateAveraging::Compound,
            /*telescopicValueDates*/ false);
    }
    return ext::make_shared<ConstNotionalCrossCurrencyBasisSwap>(
        usdNominal, USDCurrency(), sched, usdIbor3M(), 0.0010, 1.0, usdNominal * spotFx,
        EURCurrency(), sched, eurIbor3M(), 0.0025, 1.0);
}

ext::shared_ptr<ConstNotionalCrossCurrencyFixedVsFloatingSwap> makeFixFloat(Real fixedNominal,
                                                                            Real spotFx,
                                                                            Swap::Type type) {
    const Calendar cal = TARGET();
    const Date start = cal.advance(kToday, Period(2, Days));
    const Date end = cal.advance(kToday, Period(5, Years));

    return ext::make_shared<ConstNotionalCrossCurrencyFixedVsFloatingSwap>(
        type, fixedNominal, USDCurrency(), annual(start, end), 0.0325, Actual365Fixed(),
        ModifiedFollowing, /*fixedPaymentLag*/ 0, cal, fixedNominal * spotFx, EURCurrency(),
        quarterly(start, end), eurIbor3M(), 0.0015, ModifiedFollowing, /*floatPaymentLag*/ 0, cal);
}

ext::shared_ptr<PricingEngine> makeEngine(Real spotFx, const Date& npvDate = Date(),
                                          const Date& spotFXSettleDate = Date()) {
    // spotFx is quoted as USD per EUR; the engine wants units of domestic per
    // foreign, and the domestic currency here is USD.
    const Handle<Quote> fx(ext::make_shared<SimpleQuote>(spotFx));
    return ext::make_shared<DiscountingConstNotionalCrossCurrencySwapEngine>(
        USDCurrency(), usdDiscount(), EURCurrency(), eurDiscount(), fx, ext::nullopt, Date(),
        npvDate, spotFXSettleDate);
}

} // namespace

int main() {
    Settings::instance().evaluationDate() = kToday;

    ReferenceWriter out("instruments/v143_xccy_swaps", QL_VERSION, "v143_xccy_swaps_probe");

    const Real usdNominal = 125'000'000.0;
    const Real spotFx = 1.22; // USD per EUR

    // --- base class: fixed vs fixed ---------------------------------------
    {
        auto swap = makeFixFix(usdNominal, 1.0 / spotFx);
        swap->setPricingEngine(makeEngine(spotFx));
        out.addCase("fix_fix",
                    json{{"usdNominal", usdNominal},
                         {"spotFx", spotFx},
                         {"usdRate", 0.0575},
                         {"eurRate", 0.0201}},
                    describeSwap(*swap, 2));
    }

    // Same instrument, but the FX quote settles later than the curve reference
    // date, which switches on the engine's forward-FX adjustment.
    {
        auto swap = makeFixFix(usdNominal, 1.0 / spotFx);
        const Date fxSettle(11, September, 2019);
        swap->setPricingEngine(makeEngine(spotFx, Date(), fxSettle));
        out.addCase("fix_fix_fwd_fx_settle",
                    json{{"usdNominal", usdNominal},
                         {"spotFx", spotFx},
                         {"spotFXSettleDate", "2019-09-11"}},
                    describeSwap(*swap, 2));
    }

    // Same instrument, discounted to a later NPV date.
    {
        auto swap = makeFixFix(usdNominal, 1.0 / spotFx);
        const Date npvDate(11, March, 2019);
        swap->setPricingEngine(makeEngine(spotFx, npvDate));
        out.addCase("fix_fix_forward_npv_date",
                    json{{"usdNominal", usdNominal}, {"spotFx", spotFx}, {"npvDate", "2019-03-11"}},
                    describeSwap(*swap, 2));
    }

    // --- basis swap: float vs float ---------------------------------------
    {
        auto swap = makeBasis(usdNominal, 1.0 / spotFx, /*overnight*/ false);
        swap->setPricingEngine(makeEngine(spotFx));
        json j = describeSwap(*swap, 2);
        j["fairPaySpread"] = orNull(swap->fairPaySpread());
        j["fairRecSpread"] = orNull(swap->fairRecSpread());
        out.addCase("basis_ibor",
                    json{{"usdNominal", usdNominal},
                         {"spotFx", spotFx},
                         {"paySpread", 0.0010},
                         {"recSpread", 0.0025}},
                    j);
    }

    // Overnight legs, with the pay leg compounding its spread daily.
    {
        auto swap = makeBasis(usdNominal, 1.0 / spotFx, /*overnight*/ true);
        swap->setPricingEngine(makeEngine(spotFx));
        json j = describeSwap(*swap, 2);
        j["fairPaySpread"] = orNull(swap->fairPaySpread());
        j["fairRecSpread"] = orNull(swap->fairRecSpread());
        out.addCase("basis_overnight_compound_spread",
                    json{{"usdNominal", usdNominal},
                         {"spotFx", spotFx},
                         {"paySpread", 0.0010},
                         {"recSpread", 0.0025},
                         {"payPaymentLag", 2},
                         {"recPaymentLag", 2},
                         {"payCompoundSpread", true}},
                    j);
    }

    // --- fixed vs floating -------------------------------------------------
    for (const auto& [label, type] :
         std::vector<std::pair<std::string, Swap::Type>>{{"payer", Swap::Payer},
                                                         {"receiver", Swap::Receiver}}) {
        auto swap = makeFixFloat(usdNominal, 1.0 / spotFx, type);
        swap->setPricingEngine(makeEngine(spotFx));
        json j = describeSwap(*swap, 2);
        j["fairRate"] = orNull(swap->fairRate());
        j["fairSpread"] = orNull(swap->fairSpread());
        out.addCase("fixed_vs_floating_" + label,
                    json{{"fixedNominal", usdNominal},
                         {"spotFx", spotFx},
                         {"fixedRate", 0.0325},
                         {"floatSpread", 0.0015},
                         {"type", label}},
                    j);
    }

    out.write();
    return 0;
}
