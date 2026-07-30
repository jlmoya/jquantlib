// migration-harness/cpp/probes/pricingengines/inflation/inflation_cap_floor_engines_probe.cpp
// Reference values for InflationCapFloorEngines (Black, Unit-Displaced Black,
// Bachelier) against QuantLib v1.42.1.
// Phase 2r Track C C.2.
//
// Each engine prices a YoY inflation cap or floor using a constant-vol
// surface (ConstantYoYOptionletVolatility). Both Java tests and C++ probes
// use a constant 20% vol surface so the comparison is apples-to-apples.

#include <cstdio>
#include <ql/version.hpp>
#include "../../common.hpp"

#include <ql/cashflows/cashflows.hpp>
#include <ql/cashflows/inflationcouponpricer.hpp>
#include <ql/cashflows/yoyinflationcoupon.hpp>
#include <ql/currencies/europe.hpp>
#include <ql/instruments/inflationcapfloor.hpp>
#include <ql/pricingengines/inflation/inflationcapfloorengines.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/termstructures/inflation/interpolatedyoyinflationcurve.hpp>
#include <ql/termstructures/volatility/inflation/yoyinflationoptionletvolatilitystructure.hpp>
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

} // namespace

int main() {
    ReferenceWriter out("pricingengines/inflation/inflation_cap_floor_engines",
                        QL_VERSION, "inflation_cap_floor_engines_probe");

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

    QL_DEPRECATED_DISABLE_WARNING
    auto yyIndex = ext::make_shared<YoYInflationIndex>(
        "YY_RPI", UKRegion(), false, Monthly,
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

    // Nominal discount curve
    Handle<YieldTermStructure> nominalTS(
        ext::make_shared<FlatForward>(refDate, 0.05, dc, Continuous, Annual));

    // Constant vol surface: 20%
    Real volValue = 0.20;
    Handle<YoYOptionletVolatilitySurface> volTS(
        ext::make_shared<ConstantYoYOptionletVolatility>(
            volValue,           // vol value
            0,                  // settlementDays
            calendar, bdc, dc,
            observationLag,
            freq,
            /*indexIsInterpolated*/ false));

    // Build a 5Y YoY leg
    Date startDate = evalDate;
    Date endDate = Date(13, August, 2012);
    Schedule schedule = MakeSchedule()
        .from(startDate).to(endDate)
        .withTenor(1*Years)
        .withConvention(Unadjusted)
        .withCalendar(calendar)
        .forwards();

    auto buildLeg = [&]() -> Leg {
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
        setCouponPricer(leg, ext::make_shared<YoYInflationCouponPricer>());
        return leg;
    };

    struct Spec {
        const char* name;
        YoYInflationCapFloor::Type type;
        std::vector<Rate> capRates;
        std::vector<Rate> floorRates;
    };
    std::vector<Spec> specs = {
        {"cap_atm",  YoYInflationCapFloor::Cap,   {0.025}, {}},
        {"cap_otm",  YoYInflationCapFloor::Cap,   {0.040}, {}},
        {"floor_atm",YoYInflationCapFloor::Floor, {},      {0.025}},
        {"floor_otm",YoYInflationCapFloor::Floor, {},      {0.010}},
    };

    for (const auto& s : specs) {
        Leg leg = buildLeg();
        YoYInflationCapFloor inst(s.type, leg, s.capRates, s.floorRates);

        // Three engines, each priced separately
        auto blackEngine = ext::make_shared<YoYInflationBlackCapFloorEngine>(
            yyIndex, volTS, nominalTS);
        auto udbEngine = ext::make_shared<YoYInflationUnitDisplacedBlackCapFloorEngine>(
            yyIndex, volTS, nominalTS);
        auto bachEngine = ext::make_shared<YoYInflationBachelierCapFloorEngine>(
            yyIndex, volTS, nominalTS);

        inst.setPricingEngine(blackEngine);
        Real blackNPV = inst.NPV();

        inst.setPricingEngine(udbEngine);
        Real udbNPV = inst.NPV();

        inst.setPricingEngine(bachEngine);
        Real bachNPV = inst.NPV();

        json inp{
            {"type", capFloorTypeName(s.type)},
            {"strikeCap", s.capRates.empty() ? 0.0 : s.capRates[0]},
            {"strikeFloor", s.floorRates.empty() ? 0.0 : s.floorRates[0]},
            {"vol", volValue},
            {"nominal", 1.0e6}
        };
        json exp{
            {"npv_black", blackNPV},
            {"npv_unitDisplacedBlack", udbNPV},
            {"npv_bachelier", bachNPV}
        };
        out.addCase(s.name, inp, exp);
    }

    out.write();
    return 0;
}
