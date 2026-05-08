// migration-harness/cpp/probes/cashflows/zero_inflation_cashflow_probe.cpp
// Reference values for ZeroInflationCashFlow (and its IndexedCashFlow base)
// against QuantLib v1.42.1.  Phase 2p A.2.
//
// Builds a UKRPI ZeroInflationIndex with synthetic monthly fixings and an
// InterpolatedZeroInflationCurve to forecast future fixings, then constructs
// several ZeroInflationCashFlow instances varying:
//   - observation interpolation (NoInterpolation = AsIndex / Flat / Linear)
//   - growthOnly flag (false = bond style, true = swap style)
//   - whether endDate is in the past (deterministic from fixings) or future
//     (forecast from curve)
//
// Emits inputs (notional, observationLag months, startDate, endDate,
// paymentDate, growthOnly, interpolation type) and outputs:
//   - amount() (the cashflow's pay value)
//   - date() serial (the payment date)
//   - baseFixing() and indexFixing() — the two-leg fixings used to compute
//     the ratio
//
// The Java test will rebuild the same index + curve, instantiate the same
// cashflow, and compare each scalar at TIGHT tolerance.

#include <cstdio>
#include <ql/version.hpp>
#include "../common.hpp"

#include <ql/cashflows/zeroinflationcashflow.hpp>
#include <ql/indexes/inflation/ukrpi.hpp>
#include <ql/termstructures/inflation/interpolatedzeroinflationcurve.hpp>
#include <ql/time/calendars/unitedkingdom.hpp>
#include <ql/time/daycounters/actualactual.hpp>

using namespace jqml_harness;
using namespace QuantLib;

namespace {

struct CashflowSpec {
    const char*               name;
    Real                      notional;
    Date                      startDate;
    Date                      endDate;
    Period                    observationLag;
    Date                      paymentDate;
    CPI::InterpolationType    interpolation;
    bool                      growthOnly;
};

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
    ReferenceWriter out("cashflows/zero_inflation_cashflow",
                        QL_VERSION, "zero_inflation_cashflow_probe");

    // ---------- Setup ----------
    Date evalDate(13, August, 2007);
    Settings::instance().evaluationDate() = evalDate;

    Calendar calendar = UnitedKingdom();
    BusinessDayConvention bdc = ModifiedFollowing;
    DayCounter dc = ActualActual(ActualActual::ISDA);
    Frequency freq = Monthly;
    Period swapObsLag = Period(3, Months);

    // Build a forecasting zero-inflation curve (constructed independently of
    // any index instance — the index registers with this handle below).
    Date refDate = calendar.adjust(evalDate, bdc);
    std::vector<Date> nodeDates = {
        inflationPeriod(refDate - swapObsLag, freq).first,  // baseDate (2007-05-01)
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

    // ---------- Cases ----------
    // Mix of past-only (deterministic), past-vs-future (mixed), and
    // future-vs-future (forecast both legs) configurations.
    std::vector<CashflowSpec> specs = {
        // Case A: both legs in the past, AsIndex (NoInterpolation)
        {"A_past_AsIndex_grow",
            1000000.0,
            Date(13, August, 2005), Date(13, August, 2006), Period(3, Months),
            Date(15, August, 2006), CPI::AsIndex, false},
        // Case B: both legs in the past, Flat — same numerically as AsIndex for
        // monthly index but exercises the Flat branch of the switch.
        {"B_past_Flat_grow",
            1000000.0,
            Date(13, August, 2005), Date(13, August, 2006), Period(3, Months),
            Date(15, August, 2006), CPI::Flat, false},
        // Case C: both legs in the past, Linear — exercises linear interpolation
        // between consecutive fixings.
        {"C_past_Linear_grow",
            1000000.0,
            Date(13, August, 2005), Date(13, August, 2006), Period(3, Months),
            Date(15, August, 2006), CPI::Linear, false},
        // Case D: same as A but growthOnly=true (swap style)
        {"D_past_AsIndex_swap",
            1000000.0,
            Date(13, August, 2005), Date(13, August, 2006), Period(3, Months),
            Date(15, August, 2006), CPI::AsIndex, true},
        // Case E: end leg in the future, AsIndex — forecast path
        {"E_future_AsIndex_grow",
            1000000.0,
            Date(13, August, 2005), Date(13, August, 2010), Period(3, Months),
            Date(15, August, 2010), CPI::AsIndex, false},
        // Case F: end leg in the future, Linear — exercise Linear forecast
        {"F_future_Linear_grow",
            1000000.0,
            Date(13, August, 2005), Date(13, August, 2010), Period(3, Months),
            Date(15, August, 2010), CPI::Linear, false},
        // Case G: end leg in the future, Linear, growthOnly=true
        {"G_future_Linear_swap",
            5000000.0,
            Date(13, August, 2005), Date(13, August, 2009), Period(3, Months),
            Date(15, August, 2009), CPI::Linear, true},
        // Case H: longer-dated future end + AsIndex
        {"H_long_future_AsIndex_swap",
            2000000.0,
            Date(13, August, 2005), Date(13, August, 2012), Period(3, Months),
            Date(15, August, 2012), CPI::AsIndex, true},
    };

    for (const auto& s : specs) {
        ZeroInflationCashFlow cf(s.notional, ukRpi, s.interpolation,
                                 s.startDate, s.endDate, s.observationLag,
                                 s.paymentDate, s.growthOnly);

        json inp{
            {"notional", s.notional},
            {"startDate_serial", s.startDate.serialNumber()},
            {"endDate_serial", s.endDate.serialNumber()},
            {"paymentDate_serial", s.paymentDate.serialNumber()},
            {"observationLag_months", s.observationLag.length()},
            {"interpolation", interpName(s.interpolation)},
            {"growthOnly", s.growthOnly}
        };

        json exp{
            {"amount", cf.amount()},
            {"date_serial", cf.date().serialNumber()},
            {"baseFixing", cf.baseFixing()},
            {"indexFixing", cf.indexFixing()}
        };

        out.addCase(s.name, inp, exp);
    }

    out.write();
    return 0;
}
