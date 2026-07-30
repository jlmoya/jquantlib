// migration-harness/cpp/probes/instruments/year_on_year_inflation_swap_probe.cpp
// Reference values for YearOnYearInflationSwap against QuantLib v1.42.1.
// Phase 2q B.
//
// Sister to zero_coupon_inflation_swap_probe.cpp. Constructs a YYIIS bound
// to a synthetic interpolated YoY inflation curve, with a flat-forward
// nominal discount curve. Several swap cases vary maturity, type, lag, and
// observation interpolation.
//
// Emits inputs and outputs:
//   - npv, fairRate, fairSpread
//   - legNPV[0] (fixed), legNPV[1] (yoy)
//   - legBPS[0] (fixed), legBPS[1] (yoy)
//   - fixedLegPaymentDate_serial[i], yoyLegPaymentDate_serial[i] (per-coupon)
//   - startDate_actual_serial, maturityDate_actual_serial

#include <cstdio>
#include <ql/version.hpp>
#include "../common.hpp"

#include <ql/cashflows/cashflows.hpp>
#include <ql/cashflows/yoyinflationcoupon.hpp>
#include <ql/currencies/europe.hpp>
#include <ql/instruments/yearonyearinflationswap.hpp>
#include <ql/pricingengines/swap/discountingswapengine.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/termstructures/inflation/interpolatedyoyinflationcurve.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/time/calendars/unitedkingdom.hpp>
#include <ql/time/daycounters/actualactual.hpp>
#include <ql/time/schedule.hpp>

using namespace jqml_harness;
using namespace QuantLib;

namespace {

struct SwapSpec {
    const char*               name;
    Swap::Type                type;
    Real                      nominal;
    Date                      maturity;
    Period                    observationLag;
    CPI::InterpolationType    interpolation;
    Rate                      fixedRate;
    Spread                    spread;
};

const char* typeName(Swap::Type t) {
    switch (t) {
        case Swap::Payer:    return "Payer";
        case Swap::Receiver: return "Receiver";
        default:             return "?";
    }
}

const char* interpName(CPI::InterpolationType t) {
    switch (t) {
        case CPI::AsIndex: return "AsIndex";
        case CPI::Flat:    return "Flat";
        case CPI::Linear:  return "Linear";
        default:           return "?";
    }
}

} // namespace

int main() {
    ReferenceWriter out("instruments/year_on_year_inflation_swap",
                        QL_VERSION, "year_on_year_inflation_swap_probe");

    Date evalDate(13, August, 2007);
    Settings::instance().evaluationDate() = evalDate;

    Calendar calendar = UnitedKingdom();
    BusinessDayConvention bdc = ModifiedFollowing;
    DayCounter dc = ActualActual(ActualActual::ISDA);
    Frequency freq = Monthly;
    Period swapObsLag3M = Period(3, Months);

    // Forecast YoY curve (matches yoy_inflation_curve_probe scenario I).
    Date refDate = calendar.adjust(evalDate, bdc);
    std::vector<Date> nodeDates = {
        inflationPeriod(refDate - swapObsLag3M, freq).first,
        Date(13, August, 2008),
        Date(13, August, 2009),
        Date(13, August, 2010),
        Date(13, August, 2012),
        Date(13, August, 2017)
    };
    std::vector<Rate> nodeRates = { 0.025, 0.027, 0.029, 0.031, 0.034, 0.036 };
    auto yoyCurve = ext::make_shared<InterpolatedYoYInflationCurve<Linear>>(
        refDate, nodeDates, nodeRates, freq, dc);
    yoyCurve->enableExtrapolation();

    // YoY index (genuine, non-ratio) bound to forecast curve.
    QL_DEPRECATED_DISABLE_WARNING
    auto yyIndex = ext::make_shared<YoYInflationIndex>(
        "YY_RPI",
        UKRegion(),
        false,                      // revised
        Monthly,
        Period(2, Months),
        GBPCurrency(),
        Handle<YoYInflationTermStructure>(yoyCurve));
    QL_DEPRECATED_ENABLE_WARNING

    // Seed historical YoY fixings (constant 2.5% — sufficient for the
    // C++ pricer's swapletRate to produce a deterministic result on
    // already-elapsed accrual periods).
    Date fixDates[] = {
        Date(1, January,  2005), Date(1, February, 2005), Date(1, March,    2005),
        Date(1, April,    2005), Date(1, May,      2005), Date(1, June,     2005),
        Date(1, July,     2005), Date(1, August,   2005), Date(1, September,2005),
        Date(1, October,  2005), Date(1, November, 2005), Date(1, December, 2005),
        Date(1, January,  2006), Date(1, February, 2006), Date(1, March,    2006),
        Date(1, April,    2006), Date(1, May,      2006), Date(1, June,     2006),
        Date(1, July,     2006), Date(1, August,   2006), Date(1, September,2006),
        Date(1, October,  2006), Date(1, November, 2006), Date(1, December, 2006),
        Date(1, January,  2007), Date(1, February, 2007), Date(1, March,    2007),
        Date(1, April,    2007), Date(1, May,      2007), Date(1, June,     2007),
        Date(1, July,     2007),
    };
    for (size_t i = 0; i < sizeof(fixDates)/sizeof(Date); ++i) {
        yyIndex->addFixing(fixDates[i], 0.025);
    }

    // Nominal discount curve: 5% Continuous Actual365Fixed.
    Handle<YieldTermStructure> nominalTS(
        ext::make_shared<FlatForward>(refDate, 0.05, dc, Continuous, Annual));
    auto engine = ext::make_shared<DiscountingSwapEngine>(nominalTS);

    std::vector<SwapSpec> specs = {
        // Case A: 5Y Payer, 3M lag, AsIndex
        {"A_5y_payer_AsIndex", Swap::Payer, 1.0e6,
            Date(13, August, 2012), swapObsLag3M, CPI::AsIndex, 0.025, 0.0},
        // Case B: 5Y Receiver, 3M lag, AsIndex
        {"B_5y_receiver_AsIndex", Swap::Receiver, 1.0e6,
            Date(13, August, 2012), swapObsLag3M, CPI::AsIndex, 0.025, 0.0},
        // Case C: 10Y Payer, 3M lag, AsIndex
        {"C_10y_payer_AsIndex", Swap::Payer, 2.0e6,
            Date(13, August, 2017), swapObsLag3M, CPI::AsIndex, 0.030, 0.0},
        // Case D: 5Y Payer, 2M lag, AsIndex (alt obs lag = availability lag)
        {"D_5y_payer_2M_AsIndex", Swap::Payer, 5.0e5,
            Date(13, August, 2012), Period(2, Months), CPI::AsIndex, 0.025, 0.0},
    };

    for (const auto& s : specs) {
        // Build annual fixed/yoy schedules — both share the backwards schedule.
        Schedule fixedSchedule = MakeSchedule()
            .from(evalDate)
            .to(s.maturity)
            .withTenor(1 * Years)
            .withConvention(Unadjusted)
            .withCalendar(calendar)
            .backwards();
        Schedule yoySchedule = fixedSchedule;

        YearOnYearInflationSwap yyiis(s.type,
                                      s.nominal,
                                      fixedSchedule,
                                      s.fixedRate,
                                      dc,
                                      yoySchedule,
                                      yyIndex,
                                      s.observationLag,
                                      s.interpolation,
                                      s.spread,
                                      dc,
                                      calendar,
                                      bdc);
        yyiis.setPricingEngine(engine);

        json inp{
            {"type", typeName(s.type)},
            {"nominal", s.nominal},
            {"startDate_serial", evalDate.serialNumber()},
            {"maturity_serial", s.maturity.serialNumber()},
            {"observationLag_months", s.observationLag.length()},
            {"interpolation", interpName(s.interpolation)},
            {"fixedRate", s.fixedRate},
            {"spread", s.spread},
            {"calendar", "UnitedKingdom"},
            {"bdc", "ModifiedFollowing"},
            {"dayCounter", "ActualActual.ISDA"}
        };

        json fixedPay = json::array();
        for (const auto& cf : yyiis.fixedLeg()) {
            fixedPay.push_back(cf->date().serialNumber());
        }
        json yoyPay = json::array();
        for (const auto& cf : yyiis.yoyLeg()) {
            yoyPay.push_back(cf->date().serialNumber());
        }

        json exp{
            {"npv", yyiis.NPV()},
            {"fairRate", yyiis.fairRate()},
            {"fairSpread", yyiis.fairSpread()},
            {"fixedLegNPV", yyiis.fixedLegNPV()},
            {"yoyLegNPV", yyiis.yoyLegNPV()},
            {"fixedLegBPS", yyiis.legBPS(0)},
            {"yoyLegBPS", yyiis.legBPS(1)},
            {"fixedLegPaymentDates", fixedPay},
            {"yoyLegPaymentDates", yoyPay},
            {"startDate_actual_serial", yyiis.startDate().serialNumber()},
            {"maturityDate_actual_serial", yyiis.maturityDate().serialNumber()},
            {"numFixedCoupons", static_cast<int>(yyiis.fixedLeg().size())},
            {"numYoyCoupons", static_cast<int>(yyiis.yoyLeg().size())}
        };

        out.addCase(s.name, inp, exp);
    }

    out.write();
    return 0;
}
