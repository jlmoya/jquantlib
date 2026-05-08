// migration-harness/cpp/probes/instruments/zero_coupon_inflation_swap_probe.cpp
// Reference values for ZeroCouponInflationSwap against QuantLib v1.42.1.
// Phase 2p A.3.
//
// Builds a UKRPI ZeroInflationIndex with synthetic monthly fixings (matching
// the A.1 / A.2 fixture) plus an InterpolatedZeroInflationCurve for forecast
// fixings, and a flat-forward nominal curve for discounting. Constructs
// several ZeroCouponInflationSwap instances varying:
//   - observationLag (3M default; 2M alternate)
//   - observationInterpolation (AsIndex, Flat, Linear)
//   - swap type (Payer / Receiver)
//   - maturity (5Y, 10Y)
//
// Emits inputs and outputs:
//   - npv, fairRate
//   - legNPV[0] (fixed), legNPV[1] (inflation)
//   - legBPS[0] (fixedLegBPS — analytic from C++; legBPS[1]==0 by design)
//   - fixedLegPaymentDate_serial, inflationLegPaymentDate_serial
//   - baseDate_serial, fixingDate_serial (the cashflow's two fixing dates)

#include <cstdio>
#include <ql/version.hpp>
#include "../common.hpp"

#include <ql/cashflows/zeroinflationcashflow.hpp>
#include <ql/indexes/inflation/ukrpi.hpp>
#include <ql/instruments/zerocouponinflationswap.hpp>
#include <ql/pricingengines/swap/discountingswapengine.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/termstructures/inflation/interpolatedzeroinflationcurve.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/time/calendars/unitedkingdom.hpp>
#include <ql/time/daycounters/actualactual.hpp>

using namespace jqml_harness;
using namespace QuantLib;

namespace {

struct SwapSpec {
    const char*               name;
    Swap::Type                type;          // Payer / Receiver
    Real                      nominal;
    Date                      maturity;
    Period                    observationLag;
    CPI::InterpolationType    interpolation;
    Rate                      fixedRate;
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
    ReferenceWriter out("instruments/zero_coupon_inflation_swap",
                        QL_VERSION, "zero_coupon_inflation_swap_probe");

    // ---------- Setup ----------
    Date evalDate(13, August, 2007);
    Settings::instance().evaluationDate() = evalDate;

    Calendar calendar = UnitedKingdom();
    BusinessDayConvention bdc = ModifiedFollowing;
    DayCounter dc = ActualActual(ActualActual::ISDA);
    Frequency freq = Monthly;
    Period swapObsLag3M = Period(3, Months);

    // Forecasting zero-inflation curve (matches A.2 cashflow probe fixture).
    Date refDate = calendar.adjust(evalDate, bdc);
    std::vector<Date> nodeDates = {
        inflationPeriod(refDate - swapObsLag3M, freq).first,  // baseDate (2007-05-01)
        Date(13, August, 2008),
        Date(13, August, 2009),
        Date(13, August, 2010),
        Date(13, August, 2012),
        Date(13, August, 2017)
    };
    std::vector<Rate> nodeRates = { 0.025, 0.030, 0.032, 0.034, 0.036, 0.038 };
    auto zeroCurve = ext::make_shared<InterpolatedZeroInflationCurve<Linear>>(
        refDate, nodeDates, nodeRates, freq, dc);
    zeroCurve->enableExtrapolation();

    // UKRPI bound to the forecast curve, with synthetic fixings.
    auto ukRpi = ext::make_shared<UKRPI>(
        Handle<ZeroInflationTermStructure>(zeroCurve));
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
    Real fixVals[] = {
        189.9, 189.9, 190.5, 191.6, 192.0, 192.2, 192.2, 192.6, 193.1, 193.3, 193.6, 194.1,
        193.4, 194.2, 195.0, 196.5, 197.7, 198.5, 198.5, 199.2, 200.1, 200.4, 201.1, 202.7,
        201.6, 203.1, 204.4, 205.4, 206.2, 207.3, 206.1
    };
    for (size_t i = 0; i < sizeof(fixDates)/sizeof(Date); ++i) {
        ukRpi->addFixing(fixDates[i], fixVals[i]);
    }

    // Nominal discount curve: 5% Continuous Actual365Fixed (typical fixture).
    Handle<YieldTermStructure> nominalTS(
        ext::make_shared<FlatForward>(refDate, 0.05, dc, Continuous, Annual));
    auto engine = ext::make_shared<DiscountingSwapEngine>(nominalTS);

    // ---------- Cases ----------
    std::vector<SwapSpec> specs = {
        // Case A: 5Y Payer, 3M lag, AsIndex (NoInterpolation)
        {"A_5y_payer_AsIndex", Swap::Payer, 1.0e6,
            Date(13, August, 2012), swapObsLag3M, CPI::AsIndex, 0.020},
        // Case B: 5Y Receiver, 3M lag, AsIndex
        {"B_5y_receiver_AsIndex", Swap::Receiver, 1.0e6,
            Date(13, August, 2012), swapObsLag3M, CPI::AsIndex, 0.020},
        // Case C: 5Y Payer, 3M lag, Linear (interpolated observation)
        {"C_5y_payer_Linear", Swap::Payer, 1.0e6,
            Date(13, August, 2012), swapObsLag3M, CPI::Linear, 0.025},
        // Case D: 10Y Payer, 3M lag, AsIndex (longer maturity)
        {"D_10y_payer_AsIndex", Swap::Payer, 2.0e6,
            Date(13, August, 2017), swapObsLag3M, CPI::AsIndex, 0.030},
        // Case E: 10Y Payer, 2M lag, Flat (alt observation lag, equal to availabilityLag)
        {"E_10y_payer_2M_Flat", Swap::Payer, 5.0e5,
            Date(13, August, 2017), Period(2, Months), CPI::Flat, 0.028},
    };

    for (const auto& s : specs) {
        ZeroCouponInflationSwap zcis(s.type,
                                     s.nominal,
                                     evalDate,         // start = today
                                     s.maturity,       // pre-adjust
                                     calendar, bdc, dc,
                                     s.fixedRate,
                                     ukRpi, s.observationLag,
                                     s.interpolation);
        zcis.setPricingEngine(engine);

        json inp{
            {"type", typeName(s.type)},
            {"nominal", s.nominal},
            {"startDate_serial", evalDate.serialNumber()},
            {"maturity_serial", s.maturity.serialNumber()},
            {"observationLag_months", s.observationLag.length()},
            {"interpolation", interpName(s.interpolation)},
            {"fixedRate", s.fixedRate},
            {"calendar", "UnitedKingdom"},
            {"bdc", "ModifiedFollowing"},
            {"dayCounter", "ActualActual.ISDA"}
        };

        // Pull the inflation cashflow to expose its fixing dates.
        auto icf = ext::dynamic_pointer_cast<ZeroInflationCashFlow>(
            zcis.inflationLeg().at(0));

        json exp{
            {"npv", zcis.NPV()},
            {"fairRate", zcis.fairRate()},
            {"fixedLegNPV", zcis.fixedLegNPV()},
            {"inflationLegNPV", zcis.inflationLegNPV()},
            {"fixedLegBPS", zcis.fixedLegBPS()},
            {"fixedLegPaymentDate_serial", zcis.fixedLeg().at(0)->date().serialNumber()},
            {"inflationLegPaymentDate_serial", zcis.inflationLeg().at(0)->date().serialNumber()},
            {"baseDate_serial", icf->baseDate().serialNumber()},
            {"fixingDate_serial", icf->fixingDate().serialNumber()},
            {"startDate_actual_serial", zcis.startDate().serialNumber()},
            {"maturityDate_actual_serial", zcis.maturityDate().serialNumber()}
        };

        out.addCase(s.name, inp, exp);
    }

    out.write();
    return 0;
}
