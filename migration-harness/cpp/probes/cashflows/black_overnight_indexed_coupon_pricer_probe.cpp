// migration-harness/cpp/probes/cashflows/black_overnight_indexed_coupon_pricer_probe.cpp
//
// Diagnostic probe for the BlackCompoundingOvernightIndexedCouponPricer drift
// surfaced by Java tests testBlackOvernightIndexedCouponPricerCapletFloorlet
// and testBlackAverageONIndexedCouponPricerCapletFloorlet (Phase 5e.5b-CFC-c
// salvage). Java's cap rate diverges from C++ v1.42.1 by ~6.7e-7 on both
// tests' Capped branch.
//
// Replicates the exact test config:
//   today        = 1-Jul-2025
//   forecast     = flat 4% Actual/360
//   vol          = ConstantOptionletVolatility(today, TARGET, Following, 0.10, Act360)
//   coupon       = OvernightIndexedCoupon(start=1-Jul-2035, end=1-Oct-2035,
//                                          SOFR, gearing=1, spread=0,
//                                          Compound averaging)
//   cap          = 0.045
//
// Dumps the intermediate values that feed optionletRateGlobal so we can
// diff line-by-line against Java instrumentation:
//   * effectiveIndexFixing (= vanilla rate)
//   * fixingDates.size, .front, .back
//   * vol.referenceDate, referenceDate+1
//   * sigmaDate, sigma
//   * fixingStartTime, fixingEndTime
//   * T (before and after Lyashenko-Mercurio correction)
//   * stdDev
//   * blackFormula call premium
//   * capletRate (= gearing * blackPremium)
//   * final cappedCoupon.rate() (matches the test assertion)

#include <cstdio>
#include <ql/version.hpp>
#include "../common.hpp"

#include <ql/cashflows/overnightindexedcoupon.hpp>
#include <ql/cashflows/blackovernightindexedcouponpricer.hpp>
#include <ql/cashflows/couponpricer.hpp>
#include <ql/indexes/ibor/sofr.hpp>
#include <ql/instruments/payoffs.hpp>
#include <ql/pricingengines/blackformula.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/termstructures/volatility/optionlet/constantoptionletvol.hpp>
#include <ql/time/calendars/target.hpp>
#include <ql/time/calendars/unitedstates.hpp>
#include <ql/time/daycounters/actual360.hpp>
#include <ql/utilities/null.hpp>

using namespace jqml_harness;
using namespace QuantLib;

namespace {

// Tiny helper to JSON-serialize a Date as "YYYY-MM-DD" plus the serialNumber
// (since Java may use the serial form for arithmetic).
json dateToJson(const Date& d) {
    char buf[16];
    std::snprintf(buf, sizeof(buf), "%04d-%02d-%02d",
                  static_cast<int>(d.year()),
                  static_cast<int>(d.month()),
                  static_cast<int>(d.dayOfMonth()));
    return json{
        {"date",   buf},
        {"serial", static_cast<long>(d.serialNumber())}
    };
}

} // namespace

int main() {
    ReferenceWriter out("cashflows/black_overnight_indexed_coupon_pricer",
                        QL_VERSION, "black_overnight_indexed_coupon_pricer_probe");

    // -- Mirror BlackONPricerVars from C++ test fixture --
    Date today = Date(1, July, 2025);
    Settings::instance().evaluationDate() = today;
    DayCounter dc = Actual360();
    Real notional = 1000000.0;

    Handle<Quote> volQuote(ext::make_shared<SimpleQuote>(0.10));
    RelinkableHandle<YieldTermStructure> forecastCurve;
    RelinkableHandle<OptionletVolatilityStructure> vol;
    forecastCurve.linkTo(ext::make_shared<FlatForward>(today, 0.04, dc));
    vol.linkTo(ext::make_shared<ConstantOptionletVolatility>(
        today, TARGET(), Following, volQuote, dc));
    auto sofr = ext::make_shared<Sofr>(forecastCurve);

    Date start = Date(1, July, 2035);
    Date end   = Date(1, October, 2035);
    Rate cap   = 0.045;

    // -- Build the vanilla coupon (mirrors makeBaseCoupon, Compound) --
    auto vanillaCoupon = ext::make_shared<OvernightIndexedCoupon>(
        end, notional, start, end, sofr,
        1.0, 0.0, Date(), Date(), dc,
        false, RateAveraging::Compound, Null<Natural>(), 0, false, false);
    vanillaCoupon->setPricer(
        ext::make_shared<CompoundingOvernightIndexedCouponPricer>());
    Rate vanillaRate = vanillaCoupon->rate();

    // -- Mirror optionletRateGlobal step-by-step (Compound case) --
    const std::vector<Date>& fixingDates = vanillaCoupon->fixingDates();
    const Date refDate    = vol->referenceDate();
    const Date refDateP1  = refDate + 1;
    const Date sigmaDate  = std::max(fixingDates.front(), refDateP1);
    const Real sigma      = vol->volatility(sigmaDate, cap);
    const Time fixingStartTime = vol->timeFromReference(fixingDates.front());
    const Time fixingEndTime   = vol->timeFromReference(fixingDates.back());
    const Time effectiveTime   = fixingEndTime;
    Real T_initial = std::max(fixingStartTime, 0.0);
    Real T_final   = T_initial;
    if (!close_enough(fixingEndTime, T_final)) {
        T_final += std::pow(fixingEndTime - T_final, 3.0)
                 / std::pow(fixingEndTime - fixingStartTime, 2.0)
                 / 3.0;
    }
    const Real stdDev = sigma * std::sqrt(T_final);
    const Real blackPremium = blackFormula(
        Option::Call, /*K=*/cap, /*F=*/vanillaRate, stdDev, /*discount=*/1.0,
        /*displacement=*/0.0);
    const Real capletRate_manual = 1.0 /*gearing*/ * blackPremium;

    // -- Now actually run the test path: cappedCoupon.rate() --
    auto cappedCoupon = ext::make_shared<CappedFlooredOvernightIndexedCoupon>(
        ext::make_shared<OvernightIndexedCoupon>(
            end, notional, start, end, sofr,
            1.0, 0.0, Date(), Date(), dc, false,
            RateAveraging::Compound, Null<Natural>(), 0, false, false),
        cap, Null<Rate>());
    auto pricer = ext::make_shared<BlackCompoundingOvernightIndexedCouponPricer>(vol);
    cappedCoupon->setPricer(pricer);
    Rate cappedRate = cappedCoupon->rate();

    // -- Dump for the Compound test --
    json inpCompound{
        {"today",         dateToJson(today)},
        {"start",         dateToJson(start)},
        {"end",           dateToJson(end)},
        {"cap",           cap},
        {"flatRate",      0.04},
        {"vol",           0.10},
        {"averaging",     "Compound"}
    };
    json expCompound{
        {"vanillaRate",            vanillaRate},
        {"fixingDates_size",       static_cast<int>(fixingDates.size())},
        {"fixingDates_front",      dateToJson(fixingDates.front())},
        {"fixingDates_back",       dateToJson(fixingDates.back())},
        {"vol_referenceDate",      dateToJson(refDate)},
        {"vol_referenceDate_p1",   dateToJson(refDateP1)},
        {"sigmaDate",              dateToJson(sigmaDate)},
        {"sigma",                  sigma},
        {"fixingStartTime",        fixingStartTime},
        {"fixingEndTime",          fixingEndTime},
        {"effectiveTime",          effectiveTime},
        {"T_initial",              T_initial},
        {"T_final",                T_final},
        {"stdDev",                 stdDev},
        {"blackPremium",           blackPremium},
        {"capletRate_manual",      capletRate_manual},
        {"cappedCoupon_rate",      cappedRate},
        {"expected_capped_rate",   0.036604717},
        {"diff_vs_test_expected",  cappedRate - 0.036604717}
    };
    out.addCase("compound_caplet_diagnostic", inpCompound, expCompound);

    // -- Now the Simple averaging case (mirror makeBaseCoupon Simple) --
    auto vanillaCouponS = ext::make_shared<OvernightIndexedCoupon>(
        end, notional, start, end, sofr,
        1.0, 0.0, Date(), Date(), dc,
        false, RateAveraging::Simple, Null<Natural>(), 0, false, false);
    vanillaCouponS->setPricer(
        ext::make_shared<ArithmeticAveragedOvernightIndexedCouponPricer>());
    Rate vanillaRateS = vanillaCouponS->rate();

    const std::vector<Date>& fixingDatesS = vanillaCouponS->fixingDates();
    const Date refDateS   = vol->referenceDate();
    const Date refDateP1S = refDateS + 1;
    const Date sigmaDateS = std::max(fixingDatesS.front(), refDateP1S);
    const Real sigmaS     = vol->volatility(sigmaDateS, cap);
    const Time fixingStartTimeS = vol->timeFromReference(fixingDatesS.front());
    const Time fixingEndTimeS   = vol->timeFromReference(fixingDatesS.back());
    Real T_initialS = std::max(fixingStartTimeS, 0.0);
    Real T_finalS   = T_initialS;
    if (!close_enough(fixingEndTimeS, T_finalS)) {
        T_finalS += std::pow(fixingEndTimeS - T_finalS, 3.0)
                  / std::pow(fixingEndTimeS - fixingStartTimeS, 2.0)
                  / 3.0;
    }
    const Real stdDevS = sigmaS * std::sqrt(T_finalS);
    const Real blackPremiumS = blackFormula(
        Option::Call, cap, vanillaRateS, stdDevS, 1.0, 0.0);

    auto cappedCouponS = ext::make_shared<CappedFlooredOvernightIndexedCoupon>(
        ext::make_shared<OvernightIndexedCoupon>(
            end, notional, start, end, sofr,
            1.0, 0.0, Date(), Date(), dc, false,
            RateAveraging::Simple, Null<Natural>(), 0, false, false),
        cap, Null<Rate>());
    auto pricerS = ext::make_shared<BlackAveragingOvernightIndexedCouponPricer>(vol);
    cappedCouponS->setPricer(pricerS);
    Rate cappedRateS = cappedCouponS->rate();

    json inpSimple{
        {"today",         dateToJson(today)},
        {"start",         dateToJson(start)},
        {"end",           dateToJson(end)},
        {"cap",           cap},
        {"flatRate",      0.04},
        {"vol",           0.10},
        {"averaging",     "Simple"}
    };
    json expSimple{
        {"vanillaRate",            vanillaRateS},
        {"fixingDates_size",       static_cast<int>(fixingDatesS.size())},
        {"fixingDates_front",      dateToJson(fixingDatesS.front())},
        {"fixingDates_back",       dateToJson(fixingDatesS.back())},
        {"sigma",                  sigmaS},
        {"fixingStartTime",        fixingStartTimeS},
        {"fixingEndTime",          fixingEndTimeS},
        {"T_initial",              T_initialS},
        {"T_final",                T_finalS},
        {"stdDev",                 stdDevS},
        {"blackPremium",           blackPremiumS},
        {"cappedCoupon_rate",      cappedRateS},
        {"expected_capped_rate",   0.036488300},
        {"diff_vs_test_expected",  cappedRateS - 0.036488300}
    };
    out.addCase("simple_caplet_diagnostic", inpSimple, expSimple);

    out.write();
    std::printf("BlackON probe: compound capped=%.15f (expected 0.036604717, diff=%.3e)\n",
                cappedRate, cappedRate - 0.036604717);
    std::printf("BlackON probe: simple   capped=%.15f (expected 0.036488300, diff=%.3e)\n",
                cappedRateS, cappedRateS - 0.036488300);
    return 0;
}
