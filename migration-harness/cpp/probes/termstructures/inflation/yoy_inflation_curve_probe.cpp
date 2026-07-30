// migration-harness/cpp/probes/termstructures/inflation/yoy_inflation_curve_probe.cpp
// Reference values for InterpolatedYoYInflationCurve + PiecewiseYoYInflationCurve.
// Phase 2q B — YoY-inflation termstructures family (QuantLib v1.42.1)
//
// Two scenario groups, sister to the zero-inflation probe:
//   I_*: InterpolatedYoYInflationCurve constructed directly from (dates,rates).
//        Validates yoyRate(date)/yoyRate(time) reproduces input rates at pillars
//        and gives expected linear-interpolation values between them.
//   P_*: PiecewiseYoYInflationCurve bootstrapped from synthetic
//        YearOnYearInflationSwapHelper instances. Validates bootstrap result.
//
// baseDate snapped to (refDate - 3M) inflation-period start, freq Monthly.
// YYUKRPI-style: 2-month availability lag, NoInterpolation observation.

#include <cstdio>
#include <ql/version.hpp>
#include "../../common.hpp"

#include <ql/cashflows/yoyinflationcoupon.hpp>
#include <ql/indexes/inflation/ukrpi.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/termstructures/inflation/inflationhelpers.hpp>
#include <ql/termstructures/inflation/interpolatedyoyinflationcurve.hpp>
#include <ql/termstructures/inflation/piecewiseyoyinflationcurve.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/time/calendars/unitedkingdom.hpp>
#include <ql/time/daycounters/actualactual.hpp>

using namespace jqml_harness;
using namespace QuantLib;

int main() {
    ReferenceWriter out("termstructures/inflation/yoy_inflation_curve",
                        QL_VERSION, "yoy_inflation_curve_probe");

    // ---------- Common setup ----------
    Date evalDate(13, August, 2007);
    Settings::instance().evaluationDate() = evalDate;

    Calendar calendar = UnitedKingdom();
    BusinessDayConvention bdc = ModifiedFollowing;
    DayCounter dc = ActualActual(ActualActual::ISDA);

    // YYUKRPI is constructed via the underlying UKRPI ratio path in C++,
    // but a "genuine" (non-ratio) YoY index is what the Java test will use.
    // Use a shared_ptr<YoYInflationIndex> directly so we exercise the
    // genuine-yoy path (ratio_=false) — same as what Java's YYUKRPI does.
    // Modern (post-1.38) constructor: family-name path → ratio=false (genuine YoY).
    // The deprecated overload with `interpolated` flag is what older Java
    // mirrors; we use the modern overload here because (a) v1.42.1 prefers
    // it and (b) it produces the same ratio_=false index. The Java test
    // instantiates YoYInflationIndex directly with interpolated=false,
    // ratio=false; the underlying behavior matches the modern C++.
    QL_DEPRECATED_DISABLE_WARNING
    auto yyIndex = ext::make_shared<YoYInflationIndex>(
        "YY_RPI",
        UKRegion(),
        false,        // revised
        Monthly,
        Period(2, Months),
        GBPCurrency());
    QL_DEPRECATED_ENABLE_WARNING

    // Seed monthly YoY fixings 2005-01..2007-07 so that YearOnYearInflation-
    // SwapHelper's internal YYIIS can compute baseFixings on the YoY leg
    // for short-dated nodes.
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
    // YoY rate values (0.025 = 2.5%) — gentle annual inflation around 2.5%.
    Real fixVals[] = {
        0.025, 0.025, 0.025, 0.025, 0.025, 0.025, 0.025, 0.025,
        0.025, 0.025, 0.025, 0.025, 0.025, 0.025, 0.025, 0.025,
        0.025, 0.025, 0.025, 0.025, 0.025, 0.025, 0.025, 0.025,
        0.025, 0.025, 0.025, 0.025, 0.025, 0.025, 0.025
    };
    for (size_t i = 0; i < sizeof(fixDates)/sizeof(Date); ++i) {
        yyIndex->addFixing(fixDates[i], fixVals[i]);
    }

    Period swapObsLag = Period(3, Months);
    Frequency freq = Monthly;

    // -----------------------------------------------------------------------
    // Scenario I — InterpolatedYoYInflationCurve constructed from
    // (dates, rates). Tests interpolation behavior at pillars + interior.
    // -----------------------------------------------------------------------
    {
        Date refDate = calendar.adjust(evalDate, bdc);

        std::vector<Date> dates = {
            inflationPeriod(refDate - swapObsLag, freq).first,
            Date(13, August, 2008),
            Date(13, August, 2009),
            Date(13, August, 2010),
            Date(13, August, 2012),
            Date(13, August, 2017)
        };
        std::vector<Rate> rates = { 0.025, 0.027, 0.029, 0.031, 0.034, 0.036 };

        auto curve = ext::make_shared<InterpolatedYoYInflationCurve<Linear>>(
            refDate, dates, rates, freq, dc);
        curve->enableExtrapolation();

        json inp{{"scenario","I"},{"frequency","Monthly"},
                 {"dayCounter","ActualActual.ISDA"}};

        out.addCase("I_baseDate_serial",      inp, json{{"value", curve->baseDate().serialNumber()}});
        out.addCase("I_referenceDate_serial", inp, json{{"value", curve->referenceDate().serialNumber()}});
        out.addCase("I_maxDate_serial",       inp, json{{"value", curve->maxDate().serialNumber()}});
        out.addCase("I_frequency",            inp, json{{"value", static_cast<int>(curve->frequency())}});

        // YoY rate at pillar dates
        for (size_t i = 0; i < dates.size(); ++i) {
            char nm[32]; std::snprintf(nm, sizeof(nm), "I_yoyRate_pillar_%zu", i);
            json ki{{"date_serial", dates[i].serialNumber()}, {"index", static_cast<int>(i)}};
            out.addCase(nm, ki, json{{"value", curve->yoyRate(dates[i])}});
        }

        // YoY rate at inter-pillar dates
        Date interDates[] = {
            Date(13, February, 2008),
            Date(13, February, 2009),
            Date(13, February, 2010),
            Date(13, August,   2011),
            Date(13, August,   2014),
            Date(13, August,   2016)
        };
        for (size_t i = 0; i < sizeof(interDates)/sizeof(Date); ++i) {
            char nm[32]; std::snprintf(nm, sizeof(nm), "I_yoyRate_inter_%zu", i);
            json ki{{"date_serial", interDates[i].serialNumber()}};
            out.addCase(nm, ki, json{{"value", curve->yoyRate(interDates[i])}});
        }

        // yoyRate(time) variants
        Time tProbe[] = { 0.0, 0.5, 1.0, 1.5, 3.0, 7.5, 9.5 };
        for (size_t i = 0; i < sizeof(tProbe)/sizeof(Time); ++i) {
            char nm[32]; std::snprintf(nm, sizeof(nm), "I_yoyRate_time_%zu", i);
            json ki{{"time", tProbe[i]}};
            out.addCase(nm, ki,
                        json{{"value", curve->yoyRate(tProbe[i])}});
        }

        // Inspectors
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
    // Scenario P — PiecewiseYoYInflationCurve bootstrapped via
    // YearOnYearInflationSwapHelper.
    // -----------------------------------------------------------------------
    {
        Date refDate = calendar.adjust(evalDate, bdc);
        Date baseDate = inflationPeriod(refDate - swapObsLag, freq).first;
        Real baseYoYRate = 0.025;

        // Synthetic YYIIS quotes at 1Y/2Y/5Y/10Y maturities.
        struct Q { Period tenor; Rate rate; };
        std::vector<Q> qs = {
            {Period(1, Years),  0.0250},
            {Period(2, Years),  0.0270},
            {Period(5, Years),  0.0310},
            {Period(10, Years), 0.0340}
        };

        // Helpers need a nominal TS for the YYIIS engine. Use flat zero so
        // the discount factors cancel between fixed and YoY legs (matches
        // C++ helper convention — fair rate is engine-curve-independent).
        Handle<YieldTermStructure> nominalTS(
            ext::make_shared<FlatForward>(refDate, 0.0, dc, Continuous, Annual));

        std::vector<ext::shared_ptr<BootstrapHelper<YoYInflationTermStructure>>> helpers;
        for (const auto& q : qs) {
            Date maturity = refDate + q.tenor;
            auto quoteHandle = Handle<Quote>(ext::make_shared<SimpleQuote>(q.rate));
            auto h = ext::make_shared<YearOnYearInflationSwapHelper>(
                quoteHandle,
                swapObsLag,
                maturity,
                calendar,
                bdc,
                dc,
                yyIndex,
                CPI::AsIndex,
                nominalTS);
            helpers.push_back(h);
        }

        auto curve = ext::make_shared<PiecewiseYoYInflationCurve<Linear>>(
            refDate, baseDate, baseYoYRate, freq, dc, helpers);
        curve->enableExtrapolation();

        // Trigger bootstrap.
        Date d = curve->dates().back();
        (void)d;

        json inp{{"scenario","P"},{"frequency","Monthly"},
                 {"dayCounter","ActualActual.ISDA"},
                 {"swapObsLag_months", 3}};

        out.addCase("P_baseDate_serial",      inp, json{{"value", curve->baseDate().serialNumber()}});
        out.addCase("P_referenceDate_serial", inp, json{{"value", curve->referenceDate().serialNumber()}});

        for (size_t i = 0; i < qs.size(); ++i) {
            char nm[32]; std::snprintf(nm, sizeof(nm), "P_helperQuote_%zu", i);
            json ki{{"index", static_cast<int>(i)},
                    {"tenor_years", qs[i].tenor.length()},
                    {"input_rate", qs[i].rate}};
            out.addCase(nm, ki, json{{"value", helpers[i]->impliedQuote()}});
        }

        json ps = json::array();
        for (const Date& dd : curve->dates()) ps.push_back(dd.serialNumber());
        out.addCase("P_pillarDates_serials", inp, json{{"values", ps}});

        json pdata = json::array();
        for (Real r : curve->data()) pdata.push_back(r);
        out.addCase("P_pillarData", inp, json{{"values", pdata}});

        // yoyRate values at a date grid.
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
            char nm[32]; std::snprintf(nm, sizeof(nm), "P_yoyRate_grid_%zu", i);
            json ki{{"date_serial", probeDates[i].serialNumber()}};
            out.addCase(nm, ki,
                        json{{"value", curve->yoyRate(probeDates[i])}});
        }
    }

    out.write();
    return 0;
}
