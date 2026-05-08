// migration-harness/cpp/probes/instruments/inflation_cap_floor_probe.cpp
// Reference values for InflationCapFloor + CPICapFloor + CPISwap structural
// metadata against QuantLib v1.42.1.
// Phase 2r Track C C.1.
//
// Focus on structural / non-vol dependent metadata:
//   - InflationCapFloor: type, capRates, floorRates, startDate, maturityDate,
//     atmRate (when discount curve provided), per-coupon arguments
//     (startDates, fixingDates, payDates, accrualTimes, gearings, spreads,
//     nominals).
//   - CPICapFloor: type, fixingDate, payDate, observationLag, strike, baseCPI.
//   - CPISwap: type, nominal, fairRate, fairSpread, legNPV(0/1), legBPS(0/1),
//     coupon counts, payment dates.
//
// Vol-driven NPV is covered by inflation_cap_floor_engines_probe.cpp +
// yoy_optionlet_pricer_probe.cpp.

#include <cstdio>
#include <ql/version.hpp>
#include "../common.hpp"

#include <ql/cashflows/cashflows.hpp>
#include <ql/cashflows/inflationcouponpricer.hpp>
#include <ql/cashflows/yoyinflationcoupon.hpp>
#include <ql/currencies/europe.hpp>
#include <ql/indexes/ibor/euribor.hpp>
#include <ql/instruments/inflationcapfloor.hpp>
#include <ql/instruments/cpicapfloor.hpp>
#include <ql/instruments/cpiswap.hpp>
#include <ql/pricingengines/swap/discountingswapengine.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/termstructures/inflation/interpolatedyoyinflationcurve.hpp>
#include <ql/termstructures/inflation/interpolatedzeroinflationcurve.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/time/calendars/unitedkingdom.hpp>
#include <ql/time/daycounters/actualactual.hpp>
#include <ql/time/schedule.hpp>

using namespace jqml_harness;
using namespace QuantLib;

namespace {

const char* capFloorTypeName(YoYInflationCapFloor::Type t) {
    switch (t) {
        case YoYInflationCapFloor::Cap:    return "Cap";
        case YoYInflationCapFloor::Floor:  return "Floor";
        case YoYInflationCapFloor::Collar: return "Collar";
        default: return "?";
    }
}

const char* optionTypeName(Option::Type t) {
    switch (t) {
        case Option::Call: return "Call";
        case Option::Put:  return "Put";
        default: return "?";
    }
}

} // namespace

int main() {
    ReferenceWriter out("instruments/inflation_cap_floor",
                        QL_VERSION, "inflation_cap_floor_probe");

    Date evalDate(13, August, 2007);
    Settings::instance().evaluationDate() = evalDate;

    Calendar calendar = UnitedKingdom();
    BusinessDayConvention bdc = ModifiedFollowing;
    DayCounter dc = ActualActual(ActualActual::ISDA);
    Frequency freq = Monthly;
    Period observationLag = Period(3, Months);
    Date refDate = calendar.adjust(evalDate, bdc);

    // YoY curve
    std::vector<Date> nodeDates = {
        inflationPeriod(refDate - observationLag, freq).first,
        Date(13, August, 2008),
        Date(13, August, 2009),
        Date(13, August, 2010),
        Date(13, August, 2012),
        Date(13, August, 2017)
    };
    std::vector<Rate> nodeRates = {0.025, 0.027, 0.029, 0.031, 0.034, 0.036};
    auto yoyCurve = ext::make_shared<InterpolatedYoYInflationCurve<Linear>>(
        refDate, nodeDates, nodeRates, freq, dc);
    yoyCurve->enableExtrapolation();

    // YoY index
    QL_DEPRECATED_DISABLE_WARNING
    auto yyIndex = ext::make_shared<YoYInflationIndex>(
        "YY_RPI", UKRegion(), false, false, Monthly,
        Period(2, Months), GBPCurrency(),
        Handle<YoYInflationTermStructure>(yoyCurve));
    QL_DEPRECATED_ENABLE_WARNING

    // Seed historic fixings
    Date fixDates[] = {
        Date(1, January,   2005), Date(1, February,  2005), Date(1, March,    2005),
        Date(1, April,     2005), Date(1, May,       2005), Date(1, June,     2005),
        Date(1, July,      2005), Date(1, August,    2005), Date(1, September,2005),
        Date(1, October,   2005), Date(1, November,  2005), Date(1, December, 2005),
        Date(1, January,   2006), Date(1, February,  2006), Date(1, March,    2006),
        Date(1, April,     2006), Date(1, May,       2006), Date(1, June,     2006),
        Date(1, July,      2006), Date(1, August,    2006), Date(1, September,2006),
        Date(1, October,   2006), Date(1, November,  2006), Date(1, December, 2006),
        Date(1, January,   2007), Date(1, February,  2007), Date(1, March,    2007),
        Date(1, April,     2007), Date(1, May,       2007), Date(1, June,     2007),
        Date(1, July,      2007),
    };
    for (size_t i = 0; i < sizeof(fixDates)/sizeof(Date); ++i) {
        yyIndex->addFixing(fixDates[i], 0.025);
    }

    // Nominal discount curve: 5% Continuous Actual365Fixed
    Handle<YieldTermStructure> nominalTS(
        ext::make_shared<FlatForward>(refDate, 0.05, dc, Continuous, Annual));

    // ===== InflationCapFloor (YoY) =====
    {
        Date startDate = evalDate;
        Date endDate = Date(13, August, 2012); // 5Y
        Schedule schedule = MakeSchedule()
            .from(startDate).to(endDate)
            .withTenor(1*Years)
            .withConvention(Unadjusted)
            .withCalendar(calendar)
            .forwards();

        // Build YoY leg via inline construction (simulates yoyInflationLeg)
        Leg leg;
        for (Size i = 0; i < schedule.size()-1; ++i) {
            Date start = schedule.date(i);
            Date end   = schedule.date(i+1);
            Date paymentDate = calendar.adjust(end, bdc);
            auto coupon = ext::make_shared<YoYInflationCoupon>(
                paymentDate, /*nominal*/1.0e6,
                start, end,
                /*fixingDays*/0,
                yyIndex, observationLag, CPI::AsIndex,
                dc, /*gearing*/1.0, /*spread*/0.0,
                start, end);
            leg.push_back(coupon);
        }
        // Set pricer for swaplet rate path
        setCouponPricer(leg, ext::make_shared<YoYInflationCouponPricer>());

        // Cap with strike 0.03
        std::vector<Rate> capStrikes = {0.03};
        YoYInflationCapFloor cap(YoYInflationCapFloor::Cap, leg, capStrikes);

        // Floor with strike 0.02
        std::vector<Rate> floorStrikes = {0.02};
        YoYInflationCapFloor floor(YoYInflationCapFloor::Floor, leg, floorStrikes);

        // Collar [0.02, 0.03]
        std::vector<Rate> capRates_c = {0.03};
        std::vector<Rate> floorRates_c = {0.02};
        YoYInflationCapFloor collar(YoYInflationCapFloor::Collar, leg,
                                    capRates_c, floorRates_c);

        for (auto* cf : {&cap, &floor, &collar}) {
            std::string nm = std::string("inflcf_") + capFloorTypeName(cf->type());

            // payDates
            json payDates = json::array();
            json startDates = json::array();
            json fixingDates = json::array();
            json accrualTimes = json::array();
            for (const auto& cfx : cf->yoyLeg()) {
                auto cpn = ext::dynamic_pointer_cast<YoYInflationCoupon>(cfx);
                payDates.push_back(cfx->date().serialNumber());
                startDates.push_back(cpn->accrualStartDate().serialNumber());
                fixingDates.push_back(cpn->fixingDate().serialNumber());
                accrualTimes.push_back(cpn->accrualPeriod());
            }

            json inp{
                {"type", capFloorTypeName(cf->type())},
                {"strike", 0.03},
                {"nominal", 1.0e6}
            };
            // atmRate requires a per-coupon BPS computation that goes through
            // the YoYInflationCouponPricer; in the simple swap case we exclude
            // it from the probe (covered by YearOnYearInflationSwap probe).
            json exp{
                {"startDate_serial", cf->startDate().serialNumber()},
                {"maturityDate_serial", cf->maturityDate().serialNumber()},
                {"numCoupons", static_cast<int>(cf->yoyLeg().size())},
                {"payDates", payDates},
                {"startDates", startDates},
                {"fixingDates", fixingDates},
                {"accrualTimes", accrualTimes}
            };
            // Cap rates (only present in Cap & Collar)
            if (!cf->capRates().empty()) {
                json capRatesArr = json::array();
                for (Rate r : cf->capRates()) capRatesArr.push_back(r);
                exp["capRates"] = capRatesArr;
            }
            if (!cf->floorRates().empty()) {
                json floorRatesArr = json::array();
                for (Rate r : cf->floorRates()) floorRatesArr.push_back(r);
                exp["floorRates"] = floorRatesArr;
            }
            out.addCase(nm, inp, exp);
        }
    }

    // ===== CPICapFloor =====
    {
        // Build a ZeroInflationIndex (v1.42.1 ctor, no interpolated flag)
        auto zeroIndex = ext::make_shared<ZeroInflationIndex>(
            "Z_RPI", UKRegion(), false, Monthly,
            Period(2, Months), GBPCurrency());

        // Seed enough historic fixings
        Date zfixDates[] = {
            Date(1, January,   2005), Date(1, February,  2005), Date(1, March,    2005),
            Date(1, April,     2005), Date(1, May,       2005), Date(1, June,     2005),
            Date(1, July,      2005), Date(1, August,    2005), Date(1, September,2005),
            Date(1, October,   2005), Date(1, November,  2005), Date(1, December, 2005),
            Date(1, January,   2006), Date(1, February,  2006), Date(1, March,    2006),
            Date(1, April,     2006), Date(1, May,       2006), Date(1, June,     2006),
            Date(1, July,      2006), Date(1, August,    2006), Date(1, September,2006),
            Date(1, October,   2006), Date(1, November,  2006), Date(1, December, 2006),
            Date(1, January,   2007), Date(1, February,  2007), Date(1, March,    2007),
            Date(1, April,     2007), Date(1, May,       2007), Date(1, June,     2007),
            Date(1, July,      2007),
        };
        Real cpi = 100.0;
        for (size_t i = 0; i < sizeof(zfixDates)/sizeof(Date); ++i) {
            zeroIndex->addFixing(zfixDates[i], cpi);
            cpi *= 1.002;
        }

        // CPI Cap (Call)
        Date startDate = evalDate;
        Date maturity = Date(13, August, 2012);
        Real baseCPI = 100.0;
        Rate strike = 0.03;
        CPICapFloor cap(Option::Call, 1.0e6, startDate, baseCPI, maturity,
                        calendar, bdc, calendar, bdc, strike,
                        zeroIndex, observationLag, CPI::AsIndex);

        json capInp{
            {"type", "Cap"},
            {"nominal", 1.0e6},
            {"strike", strike},
            {"baseCPI", baseCPI},
            {"observationLag_months", observationLag.length()},
            {"observationInterpolation", "AsIndex"}
        };
        json capExp{
            {"option_type", optionTypeName(cap.type())},
            {"strike", cap.strike()},
            {"nominal", cap.nominal()},
            {"fixingDate_serial", cap.fixingDate().serialNumber()},
            {"payDate_serial", cap.payDate().serialNumber()},
            {"observationLag_months", cap.observationLag().length()}
        };
        out.addCase("cpicf_cap_AsIndex", capInp, capExp);

        // CPI Floor (Put)
        CPICapFloor floor(Option::Put, 1.0e6, startDate, baseCPI, maturity,
                          calendar, bdc, calendar, bdc, 0.01,
                          zeroIndex, observationLag, CPI::AsIndex);
        json fInp{
            {"type", "Floor"},
            {"nominal", 1.0e6},
            {"strike", 0.01},
            {"baseCPI", baseCPI},
            {"observationLag_months", observationLag.length()},
            {"observationInterpolation", "AsIndex"}
        };
        json fExp{
            {"option_type", optionTypeName(floor.type())},
            {"strike", floor.strike()},
            {"nominal", floor.nominal()},
            {"fixingDate_serial", floor.fixingDate().serialNumber()},
            {"payDate_serial", floor.payDate().serialNumber()},
            {"observationLag_months", floor.observationLag().length()}
        };
        out.addCase("cpicf_floor_AsIndex", fInp, fExp);

        // ===== CPISwap =====
        // Start a few months out to avoid needing past Euribor fixings
        // (the first floating fixing is settlement-2 days for the first
        // accrual period).
        Date cpiStart = Date(15, December, 2007);
        Date cpiEnd = Date(15, December, 2012);
        Schedule fixedSchedule = MakeSchedule()
            .from(cpiStart).to(cpiEnd)
            .withTenor(1*Years)
            .withConvention(Unadjusted)
            .withCalendar(calendar)
            .forwards();
        Schedule floatSchedule = MakeSchedule()
            .from(cpiStart).to(cpiEnd)
            .withTenor(6*Months)
            .withConvention(Unadjusted)
            .withCalendar(calendar)
            .forwards();
        // Use a Euribor 6M as the floating index (link to nominalTS for forecasting)
        auto floatIndex = ext::make_shared<Euribor6M>(nominalTS);

        Real fixedRate = 0.025;
        Real cpiBase = 100.0;
        Real spread = 0.0;
        Natural fixingDays = 2;

        // Link the zeroIndex to a zero inflation curve so CPI ratios can be
        // forecast for future fixings (required by CPICouponPricer's
        // accruedRate path called via CashFlows::npv -> Coupon::amount).
        std::vector<Date> zNodeDates = {
            inflationPeriod(refDate - observationLag, freq).first,
            Date(13, August, 2008),
            Date(13, August, 2009),
            Date(13, August, 2010),
            Date(13, August, 2012),
            Date(13, August, 2017)
        };
        // Synthetic CPI levels from baseCPI=100.
        std::vector<Rate> zNodeFixings = {100.0, 102.0, 104.0, 106.0, 110.0, 120.0};
        auto zeroCurve = ext::make_shared<InterpolatedZeroInflationCurve<Linear>>(
            refDate, zNodeDates, zNodeFixings, freq, dc);
        zeroCurve->enableExtrapolation();
        // Use a different name to avoid the global IndexManager keeping
        // the previous zeroIndex's fixings registered (which would conflict
        // with the new addFixing seed values).
        zeroIndex = ext::make_shared<ZeroInflationIndex>(
            "Z_RPI2", UKRegion(), false, Monthly,
            Period(2, Months), GBPCurrency(),
            Handle<ZeroInflationTermStructure>(zeroCurve));
        Real cpi2 = 100.0;
        for (size_t i = 0; i < sizeof(zfixDates)/sizeof(Date); ++i) {
            zeroIndex->addFixing(zfixDates[i], cpi2);
            cpi2 *= 1.002;
        }

        CPISwap swap(Swap::Payer, 1.0e6, false,
                     spread, dc, floatSchedule, bdc, fixingDays, floatIndex,
                     fixedRate, cpiBase, dc, fixedSchedule, bdc, observationLag,
                     zeroIndex, CPI::AsIndex);
        auto engine = ext::make_shared<DiscountingSwapEngine>(nominalTS);
        swap.setPricingEngine(engine);

        json sInp{
            {"type", "Payer"},
            {"nominal", 1.0e6},
            {"baseCPI", cpiBase},
            {"fixedRate", fixedRate},
            {"spread", spread},
            {"observationLag_months", observationLag.length()}
        };
        json sExp{
            {"npv", swap.NPV()},
            {"fairRate", swap.fairRate()},
            {"fairSpread", swap.fairSpread()},
            {"fixedLegNPV", swap.fixedLegNPV()},
            {"floatLegNPV", swap.floatLegNPV()},
            {"fixedLegBPS", swap.legBPS(0)},
            {"floatLegBPS", swap.legBPS(1)},
            {"numFixedFlows", static_cast<int>(swap.cpiLeg().size())},
            {"numFloatFlows", static_cast<int>(swap.floatLeg().size())}
        };
        out.addCase("cpiswap_5y_payer_AsIndex", sInp, sExp);
    }

    out.write();
    return 0;
}
