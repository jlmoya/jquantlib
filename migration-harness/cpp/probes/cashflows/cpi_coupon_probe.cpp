// migration-harness/cpp/probes/cashflows/cpi_coupon_probe.cpp
// Reference values for CPICoupon + CPICashFlow against QuantLib v1.42.1.
// Phase 2q L1 Track C — CPI cashflow family.
//
// Builds a UKRPI ZeroInflationIndex with synthetic monthly fixings and an
// InterpolatedZeroInflationCurve to forecast future fixings, then constructs:
//   - several CPICoupon instances (varying observation interpolation, baseCPI
//     vs baseDate ctor, past-only vs future endDate)
//   - several CPICashFlow instances (similar variants but the standalone
//     IndexedCashFlow form)
//
// For coupons, also exercises CPICouponPricer.swapletRate / accruedRate to
// drive the InflationCoupon::performCalculations -> rate() pipeline.
//
// The Java test rebuilds the same index + curve, instantiates the same
// objects, and compares each scalar at TIGHT.

#include <cstdio>
#include <ql/version.hpp>
#include "../common.hpp"

#include <ql/cashflows/cpicoupon.hpp>
#include <ql/cashflows/cpicouponpricer.hpp>
#include <ql/indexes/inflation/ukrpi.hpp>
#include <ql/termstructures/inflation/interpolatedzeroinflationcurve.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/time/calendars/unitedkingdom.hpp>
#include <ql/time/daycounters/actualactual.hpp>

using namespace jqml_harness;
using namespace QuantLib;

namespace {

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
    ReferenceWriter out("cashflows/cpi_coupon",
                        QL_VERSION, "cpi_coupon_probe");

    // ---------- Common setup ----------
    Date evalDate(13, August, 2007);
    Settings::instance().evaluationDate() = evalDate;

    Calendar calendar = UnitedKingdom();
    BusinessDayConvention bdc = ModifiedFollowing;
    DayCounter dc = ActualActual(ActualActual::ISDA);
    Frequency freq = Monthly;
    Period swapObsLag = Period(3, Months);

    // Build a forecasting zero-inflation curve.
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

    // Nominal flat-forward yield TS for pricer discount.
    Handle<YieldTermStructure> nominalTs(
        ext::make_shared<FlatForward>(refDate, 0.05, dc));

    auto pricer = ext::make_shared<CPICouponPricer>(nominalTs);

    // ---------------------------------------------------------------
    // Section A: CPICoupon — three constructor variants. The pricer is set
    // for each so swapletRate (rate()) is computed via accruedRate.
    // ---------------------------------------------------------------
    {
        // Past coupon: start 13-Aug-2005, end 13-Aug-2006, observationLag=3M,
        // payment 15-Aug-2006. Both legs deterministic from fixings.
        struct Spec {
            const char* name;
            Real baseCPI;    // either a value or Null<Real>() (use baseDate)
            Date baseDate;   // either a value or Date()
            Real notional;
            Date startDate;
            Date endDate;
            Date paymentDate;
            CPI::InterpolationType obsInterp;
            Real fixedRate;
        };

        // baseDate-only ctor uses 13-May-2005 (~3M before startDate); the
        // pricer will then call laggedFixing(baseDate + obsLag, ...).
        std::vector<Spec> specs = {
            // A1: past period, AsIndex, baseCPI given.
            {"A1_past_AsIndex_baseCPI",
                194.1,            // baseCPI = December 2005 RPI value-ish
                Date(),
                1000000.0,
                Date(13, August, 2005), Date(13, August, 2006),
                Date(15, August, 2006),
                CPI::AsIndex,
                1.0},
            // A2: past period, Flat, baseCPI given (same numeric as AsIndex
            // for a monthly index but exercises Flat branch).
            {"A2_past_Flat_baseCPI",
                194.1,
                Date(),
                1000000.0,
                Date(13, August, 2005), Date(13, August, 2006),
                Date(15, August, 2006),
                CPI::Flat,
                1.0},
            // A3: past period, Linear (between consecutive fixings).
            {"A3_past_Linear_baseCPI",
                194.1,
                Date(),
                1000000.0,
                Date(13, August, 2005), Date(13, August, 2006),
                Date(15, August, 2006),
                CPI::Linear,
                1.0},
            // A4: future end, AsIndex (forecast curve).
            {"A4_future_AsIndex_baseCPI",
                194.1,
                Date(),
                1000000.0,
                Date(13, August, 2005), Date(13, August, 2010),
                Date(15, August, 2010),
                CPI::AsIndex,
                1.0},
            // A5: baseDate-only constructor (baseCPI = Null<Real>());
            // pricer derives I0 from cpiIndex laggedFixing.
            {"A5_past_AsIndex_baseDate",
                Null<Real>(),
                Date(13, May, 2005),
                1000000.0,
                Date(13, August, 2005), Date(13, August, 2006),
                Date(15, August, 2006),
                CPI::AsIndex,
                1.0},
            // A6: future end + non-trivial fixedRate (gearing).
            {"A6_future_Linear_gearing",
                194.1,
                Date(),
                500000.0,
                Date(13, August, 2005), Date(13, August, 2009),
                Date(15, August, 2009),
                CPI::Linear,
                0.5},
        };

        for (const auto& s : specs) {
            ext::shared_ptr<CPICoupon> coupon;
            if (s.baseCPI != Null<Real>() && s.baseDate == Date()) {
                coupon = ext::make_shared<CPICoupon>(
                    s.baseCPI, s.paymentDate, s.notional,
                    s.startDate, s.endDate,
                    ukRpi, swapObsLag, s.obsInterp, dc, s.fixedRate);
            } else if (s.baseCPI == Null<Real>() && s.baseDate != Date()) {
                coupon = ext::make_shared<CPICoupon>(
                    s.baseDate, s.paymentDate, s.notional,
                    s.startDate, s.endDate,
                    ukRpi, swapObsLag, s.obsInterp, dc, s.fixedRate);
            } else {
                coupon = ext::make_shared<CPICoupon>(
                    s.baseCPI, s.baseDate, s.paymentDate, s.notional,
                    s.startDate, s.endDate,
                    ukRpi, swapObsLag, s.obsInterp, dc, s.fixedRate);
            }
            coupon->setPricer(pricer);

            json inp{
                {"baseCPI", s.baseCPI == Null<Real>() ? "null" : std::to_string(s.baseCPI)},
                {"baseDate_serial", s.baseDate == Date() ? -1 : (Integer)s.baseDate.serialNumber()},
                {"notional", s.notional},
                {"startDate_serial", (Integer)s.startDate.serialNumber()},
                {"endDate_serial", (Integer)s.endDate.serialNumber()},
                {"paymentDate_serial", (Integer)s.paymentDate.serialNumber()},
                {"observationLag_months", swapObsLag.length()},
                {"observationInterpolation", interpName(s.obsInterp)},
                {"fixedRate", s.fixedRate}
            };

            json exp{
                {"date_serial", (Integer)coupon->date().serialNumber()},
                {"indexFixing", coupon->indexFixing()},
                {"indexRatio_at_endDate", coupon->indexRatio(coupon->accrualEndDate())},
                {"rate", coupon->rate()},
                {"amount", coupon->amount()},
                {"adjustedIndexGrowth", coupon->adjustedIndexGrowth()}
            };

            out.addCase(s.name, inp, exp);
        }
    }

    // ---------------------------------------------------------------
    // Section B: CPICashFlow — standalone (IndexedCashFlow-style)
    // ---------------------------------------------------------------
    {
        struct Spec {
            const char* name;
            Real notional;
            Date baseDate;
            Real baseFixing;            // either explicit or Null<Real>()
            Date observationDate;
            Date paymentDate;
            CPI::InterpolationType interp;
            bool growthOnly;
        };

        std::vector<Spec> specs = {
            // B1: explicit baseFixing, AsIndex, growthOnly=false (bond-style)
            {"B1_pastObs_AsIndex_baseFixing_grow",
                1000000.0,
                Date(1, May, 2007),  194.1,
                Date(13, August, 2007),
                Date(15, August, 2007),
                CPI::AsIndex, false},
            // B2: explicit baseFixing, AsIndex, growthOnly=true (swap-style)
            {"B2_pastObs_AsIndex_baseFixing_swap",
                1000000.0,
                Date(1, May, 2007),  194.1,
                Date(13, August, 2007),
                Date(15, August, 2007),
                CPI::AsIndex, true},
            // B3: future observation -> forecast path
            {"B3_futureObs_AsIndex_baseFixing_grow",
                1000000.0,
                Date(1, May, 2007),  194.1,
                Date(13, August, 2010),
                Date(15, August, 2010),
                CPI::AsIndex, false},
            // B4: future observation, Linear interpolation
            {"B4_futureObs_Linear_baseFixing_grow",
                1000000.0,
                Date(1, May, 2007),  194.1,
                Date(13, August, 2010),
                Date(15, August, 2010),
                CPI::Linear, false},
            // B5: baseDate-only (no baseFixing), pricer/cf computes I0
            {"B5_pastObs_AsIndex_baseDate",
                500000.0,
                Date(1, May, 2007),  Null<Real>(),
                Date(13, August, 2007),
                Date(15, August, 2007),
                CPI::AsIndex, true},
        };

        for (const auto& s : specs) {
            CPICashFlow cf(s.notional, ukRpi, s.baseDate, s.baseFixing,
                           s.observationDate, swapObsLag, s.interp,
                           s.paymentDate, s.growthOnly);

            json inp{
                {"notional", s.notional},
                {"baseDate_serial", (Integer)s.baseDate.serialNumber()},
                {"baseFixing", s.baseFixing == Null<Real>() ? "null" : std::to_string(s.baseFixing)},
                {"observationDate_serial", (Integer)s.observationDate.serialNumber()},
                {"paymentDate_serial", (Integer)s.paymentDate.serialNumber()},
                {"observationLag_months", swapObsLag.length()},
                {"interpolation", interpName(s.interp)},
                {"growthOnly", s.growthOnly}
            };

            json exp{
                {"date_serial", (Integer)cf.date().serialNumber()},
                {"baseFixing", cf.baseFixing()},
                {"indexFixing", cf.indexFixing()},
                {"amount", cf.amount()}
            };

            out.addCase(s.name, inp, exp);
        }
    }

    out.write();
    return 0;
}
