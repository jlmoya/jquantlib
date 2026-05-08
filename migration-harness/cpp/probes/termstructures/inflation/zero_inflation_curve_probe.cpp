// migration-harness/cpp/probes/termstructures/inflation/zero_inflation_curve_probe.cpp
// Reference values for InterpolatedZeroInflationCurve + PiecewiseZeroInflationCurve.
// Phase 2p A.1 — zero-inflation termstructures family (QuantLib v1.42.1)
//
// Two scenario groups:
//   I_*: InterpolatedZeroInflationCurve constructed directly from (dates,rates).
//        Validates that zeroRate(date)/zeroRate(time) reproduces the input rates
//        at pillar dates and gives the expected linear-interpolation values
//        between them.
//   P_*: PiecewiseZeroInflationCurve bootstrapped from synthetic
//        ZeroCouponInflationSwapHelper instances. Validates bootstrap result.
//
// The curve baseDate is set to (today - obsLag) inflationPeriod start, with
// frequency = Monthly. UKRPI-style: 2-month availability lag, NoInterpolation
// observation. Index fixings are seeded so the helper can compute baseFixing.

#include <cstdio>
#include <ql/version.hpp>
#include "../../common.hpp"

#include <ql/cashflows/cpicoupon.hpp>
#include <ql/indexes/inflation/ukrpi.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/termstructures/inflation/inflationhelpers.hpp>
#include <ql/termstructures/inflation/interpolatedzeroinflationcurve.hpp>
#include <ql/termstructures/inflation/piecewisezeroinflationcurve.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/time/calendars/unitedkingdom.hpp>
#include <ql/time/daycounters/actualactual.hpp>

using namespace jqml_harness;
using namespace QuantLib;

int main() {
    ReferenceWriter out("termstructures/inflation/zero_inflation_curve",
                        QL_VERSION, "zero_inflation_curve_probe");

    // ---------- Common setup ----------
    Date evalDate(13, August, 2007);
    Settings::instance().evaluationDate() = evalDate;

    Calendar calendar = UnitedKingdom();
    BusinessDayConvention bdc = ModifiedFollowing;
    DayCounter dc = ActualActual(ActualActual::ISDA);

    // UKRPI fixings (rough, real-world-ish synthetic values around 200)
    // Cover 2005-01 through 2007-08.
    auto ukRpiNoInterp = ext::make_shared<UKRPI>();
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
        ukRpiNoInterp->addFixing(fixDates[i], fixVals[i]);
    }

    Period swapObsLag = Period(3, Months);
    Frequency freq = Monthly;

    // -----------------------------------------------------------------------
    // Scenario I — InterpolatedZeroInflationCurve constructed from
    // (dates, rates). Tests interpolation behavior + boundary access.
    // -----------------------------------------------------------------------
    {
        Date refDate = calendar.adjust(evalDate, bdc);

        // 5 pillar nodes at synthetic 1Y, 2Y, 3Y, 5Y, 10Y maturities
        std::vector<Date> dates = {
            inflationPeriod(refDate - swapObsLag, freq).first, // baseDate
            Date(13, August, 2008),
            Date(13, August, 2009),
            Date(13, August, 2010),
            Date(13, August, 2012),
            Date(13, August, 2017)
        };
        std::vector<Rate> rates = { 0.025, 0.030, 0.032, 0.034, 0.036, 0.038 };

        auto curve = ext::make_shared<InterpolatedZeroInflationCurve<Linear>>(
            refDate, dates, rates, freq, dc);
        curve->enableExtrapolation();

        json inp{{"scenario","I"},{"frequency","Monthly"},
                 {"dayCounter","ActualActual.ISDA"}};

        out.addCase("I_baseDate_serial",      inp, json{{"value", curve->baseDate().serialNumber()}});
        out.addCase("I_referenceDate_serial", inp, json{{"value", curve->referenceDate().serialNumber()}});
        out.addCase("I_maxDate_serial",       inp, json{{"value", curve->maxDate().serialNumber()}});
        out.addCase("I_frequency",            inp, json{{"value", static_cast<int>(curve->frequency())}});

        // Reproduce input rates at pillar dates (bit-exact expected for first 5)
        for (size_t i = 0; i < dates.size(); ++i) {
            char nm[32]; std::snprintf(nm, sizeof(nm), "I_zeroRate_pillar_%zu", i);
            json ki{{"date_serial", dates[i].serialNumber()}, {"index", static_cast<int>(i)}};
            out.addCase(nm, ki, json{{"value", curve->zeroRate(dates[i])}});
        }

        // Probe inter-pillar dates (interpolated values)
        Date interDates[] = {
            Date(13, February, 2008),
            Date(13, February, 2009),
            Date(13, February, 2010),
            Date(13, August,   2011),
            Date(13, August,   2014),
            Date(13, August,   2016)
        };
        for (size_t i = 0; i < sizeof(interDates)/sizeof(Date); ++i) {
            char nm[32]; std::snprintf(nm, sizeof(nm), "I_zeroRate_inter_%zu", i);
            json ki{{"date_serial", interDates[i].serialNumber()}};
            out.addCase(nm, ki, json{{"value", curve->zeroRate(interDates[i])}});
        }

        // zeroRate(time) variants
        Time tProbe[] = { 0.0, 0.5, 1.0, 1.5, 3.0, 7.5, 9.5 };
        for (size_t i = 0; i < sizeof(tProbe)/sizeof(Time); ++i) {
            char nm[32]; std::snprintf(nm, sizeof(nm), "I_zeroRate_time_%zu", i);
            json ki{{"time", tProbe[i]}};
            out.addCase(nm, ki,
                        json{{"value", curve->zeroRate(tProbe[i])}});
        }

        // Inspectors: dates() / times() / rates()
        json ds = json::array();
        for (const Date& d : curve->dates()) ds.push_back(d.serialNumber());
        out.addCase("I_dates_serials", inp, json{{"values", ds}});

        json ts = json::array();
        for (Time t : curve->times()) ts.push_back(t);
        out.addCase("I_times", inp, json{{"values", ts}});

        json rs = json::array();
        for (Real r : curve->rates()) rs.push_back(r);
        out.addCase("I_rates", inp, json{{"values", rs}});
    }

    // -----------------------------------------------------------------------
    // Scenario P — PiecewiseZeroInflationCurve bootstrapped via
    // ZeroCouponInflationSwapHelper.
    // -----------------------------------------------------------------------
    {
        Date refDate = calendar.adjust(evalDate, bdc);
        // baseDate = startDate - swapObsLag, snapped to inflation-period start.
        Date baseDate = inflationPeriod(refDate - swapObsLag, freq).first;

        // Synthetic ZCIIS quotes with maturities at 1Y, 2Y, 5Y, 10Y.
        struct Q { Period tenor; Rate rate; };
        std::vector<Q> qs = {
            {Period(1, Years),  0.0250},
            {Period(2, Years),  0.0290},
            {Period(5, Years),  0.0330},
            {Period(10, Years), 0.0360}
        };

        std::vector<ext::shared_ptr<BootstrapHelper<ZeroInflationTermStructure>>> helpers;
        for (const auto& q : qs) {
            Date maturity = refDate + q.tenor;
            auto quoteHandle = Handle<Quote>(ext::make_shared<SimpleQuote>(q.rate));
            auto h = ext::make_shared<ZeroCouponInflationSwapHelper>(
                quoteHandle,
                swapObsLag,
                maturity,
                calendar,
                bdc,
                dc,
                ukRpiNoInterp,
                CPI::AsIndex);
            helpers.push_back(h);
        }

        auto curve = ext::make_shared<PiecewiseZeroInflationCurve<Linear>>(
            refDate, baseDate, freq, dc, helpers);
        curve->enableExtrapolation();

        // Ensure bootstrap runs
        Date d = curve->dates().back();
        (void)d;

        json inp{{"scenario","P"},{"frequency","Monthly"},
                 {"dayCounter","ActualActual.ISDA"},
                 {"swapObsLag_months", 3}};

        out.addCase("P_baseDate_serial",      inp, json{{"value", curve->baseDate().serialNumber()}});
        out.addCase("P_referenceDate_serial", inp, json{{"value", curve->referenceDate().serialNumber()}});

        // Bootstrap result: zeroRate at each helper's pillarDate should match the input quote.
        for (size_t i = 0; i < qs.size(); ++i) {
            char nm[32]; std::snprintf(nm, sizeof(nm), "P_helperQuote_%zu", i);
            json ki{{"index", static_cast<int>(i)},
                    {"tenor_years", qs[i].tenor.length()},
                    {"input_rate", qs[i].rate}};
            // The implied quote should equal the input quote after bootstrap.
            out.addCase(nm, ki, json{{"value", helpers[i]->impliedQuote()}});
        }

        // Pillar dates and bootstrapped data values
        json ps = json::array();
        for (const Date& dd : curve->dates()) ps.push_back(dd.serialNumber());
        out.addCase("P_pillarDates_serials", inp, json{{"values", ps}});

        json pdata = json::array();
        for (Real r : curve->data()) pdata.push_back(r);
        out.addCase("P_pillarData", inp, json{{"values", pdata}});

        // zeroRate values at a date grid. Use dates between baseDate and 10Y.
        Date probeDates[] = {
            refDate + Period(6, Months),
            refDate + Period(1, Years),
            refDate + Period(18, Months),
            refDate + Period(2, Years),
            refDate + Period(3, Years),
            refDate + Period(4, Years),
            refDate + Period(5, Years),
            refDate + Period(7, Years),
            refDate + Period(10, Years)
        };
        for (size_t i = 0; i < sizeof(probeDates)/sizeof(Date); ++i) {
            char nm[32]; std::snprintf(nm, sizeof(nm), "P_zeroRate_grid_%zu", i);
            json ki{{"date_serial", probeDates[i].serialNumber()}};
            out.addCase(nm, ki,
                        json{{"value", curve->zeroRate(probeDates[i])}});
        }
    }

    out.write();
    return 0;
}
