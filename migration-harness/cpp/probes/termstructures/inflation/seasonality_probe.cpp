// migration-harness/cpp/probes/termstructures/inflation/seasonality_probe.cpp
// Reference values for MultiplicativePriceSeasonality + KerkhofSeasonality
// against QuantLib v1.42.1.
// Phase 2q L1 Track C — Seasonality.
//
// Two scenario groups:
//   M_*  — MultiplicativePriceSeasonality with monthly factors. Exercise
//          seasonalityFactor (raw lookup), correctZeroRate (applied to a
//          curve), correctYoYRate, isConsistent.
//   K_*  — KerkhofSeasonality with monthly factors. Exercise the alternative
//          factor pattern + correctZeroRate.
//
// The Java test rebuilds the same setup and compares each output at TIGHT.

#include <cstdio>
#include <ql/version.hpp>
#include "../../common.hpp"

#include <ql/termstructures/inflation/seasonality.hpp>
#include <ql/termstructures/inflation/interpolatedzeroinflationcurve.hpp>
#include <ql/termstructures/inflation/interpolatedyoyinflationcurve.hpp>
#include <ql/time/calendars/unitedkingdom.hpp>
#include <ql/time/daycounters/actualactual.hpp>

using namespace jqml_harness;
using namespace QuantLib;

int main() {
    ReferenceWriter out("termstructures/inflation/seasonality",
                        QL_VERSION, "seasonality_probe");

    // ---------- Common setup ----------
    Date evalDate(13, August, 2007);
    Settings::instance().evaluationDate() = evalDate;

    Calendar calendar = UnitedKingdom();
    BusinessDayConvention bdc = ModifiedFollowing;
    DayCounter dc = ActualActual(ActualActual::ISDA);
    Frequency freq = Monthly;
    Period swapObsLag = Period(3, Months);

    // Build an InterpolatedZeroInflationCurve so we can exercise
    // correctZeroRate -> seasonality-corrected zero rate.
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
    auto curve = ext::make_shared<InterpolatedZeroInflationCurve<Linear>>(
        refDate, nodeDates, nodeRates, freq, dc);
    curve->enableExtrapolation();

    // -----------------------------------------------------------------------
    // Scenario M — MultiplicativePriceSeasonality, 12 monthly factors.
    // Stationary case: factors repeat every year. Use a slight monthly bump.
    // -----------------------------------------------------------------------
    {
        // Monthly factors (12 values, indexed Jan..Dec). These are typical
        // CPI seasonality factors — within a few percent of 1.0.
        std::vector<Rate> factors = {
            1.0030, 1.0010, 1.0050, 1.0030, 1.0050, 1.0070,
            0.9990, 0.9990, 1.0050, 1.0030, 1.0030, 0.9970
        };
        Date seasonalityBaseDate(1, January, 2007);
        auto seas = ext::make_shared<MultiplicativePriceSeasonality>(
            seasonalityBaseDate, Monthly, factors);

        json inpCommon{
            {"scenario", "M"},
            {"factorCount", (int)factors.size()},
            {"frequency", "Monthly"},
            {"seasonalityBaseDate_serial", (Integer)seasonalityBaseDate.serialNumber()}
        };

        out.addCase("M_factorCount", inpCommon, json{{"value", (int)factors.size()}});
        out.addCase("M_baseDate_serial", inpCommon,
            json{{"value", (Integer)seas->seasonalityBaseDate().serialNumber()}});

        // seasonalityFactor at each month-1 date in 2007. With seasonalityBaseDate
        // = Jan 1 2007 and monthly frequency, seasonalityFactor at Jan 1 2007 == factors[0].
        Date probeDates[] = {
            Date(1, January,  2007),
            Date(1, February, 2007),
            Date(1, March,    2007),
            Date(1, April,    2007),
            Date(1, May,      2007),
            Date(1, June,     2007),
            Date(1, July,     2007),
            Date(1, August,   2007),
            Date(1, September,2007),
            Date(1, October,  2007),
            Date(1, November, 2007),
            Date(1, December, 2007),
            // Wrap to 2008 — factors repeat
            Date(1, January,  2008),
            Date(1, June,     2008)
        };
        for (size_t i = 0; i < sizeof(probeDates)/sizeof(Date); ++i) {
            char nm[64];
            std::snprintf(nm, sizeof(nm), "M_seasonalityFactor_%zu", i);
            json k{{"date_serial", (Integer)probeDates[i].serialNumber()}};
            out.addCase(nm, k, json{{"value", seas->seasonalityFactor(probeDates[i])}});
        }

        // Apply seasonality to the curve and exercise correctZeroRate via
        // curve->zeroRate (which the C++ ZeroInflationTermStructure invokes
        // when hasSeasonality()). Compare adjusted vs unadjusted at a grid.
        auto curveSeas = ext::make_shared<InterpolatedZeroInflationCurve<Linear>>(
            refDate, nodeDates, nodeRates, freq, dc);
        curveSeas->enableExtrapolation();
        curveSeas->setSeasonality(seas);

        Date rateProbe[] = {
            Date(1, August,  2007), // close to baseDate
            Date(1, December,2007),
            Date(1, June,    2008),
            Date(1, June,    2009),
            Date(1, December,2010),
            Date(1, June,    2012),
            Date(1, June,    2014)
        };
        for (size_t i = 0; i < sizeof(rateProbe)/sizeof(Date); ++i) {
            char nm[64];
            std::snprintf(nm, sizeof(nm), "M_correctZeroRate_grid_%zu", i);
            json k{{"date_serial", (Integer)rateProbe[i].serialNumber()}};
            json v{
                {"unadjusted", curve->zeroRate(rateProbe[i])},
                {"adjusted", curveSeas->zeroRate(rateProbe[i])}
            };
            out.addCase(nm, k, v);
        }

        // Direct correctYoYRate calls (probe the YoY branch independently).
        Rate yoyInputs[] = { 0.020, 0.025, 0.030, 0.035 };
        Date yoyDates[] = {
            Date(1, March,    2008),
            Date(1, July,     2008),
            Date(1, November, 2008),
            Date(1, June,     2009)
        };
        for (size_t i = 0; i < sizeof(yoyInputs)/sizeof(Rate); ++i) {
            char nm[64];
            std::snprintf(nm, sizeof(nm), "M_correctYoYRate_%zu", i);
            json k{{"date_serial", (Integer)yoyDates[i].serialNumber()},
                   {"input_rate", yoyInputs[i]}};
            out.addCase(nm, k,
                json{{"value", seas->correctYoYRate(yoyDates[i], yoyInputs[i], *curve)}});
        }

        // Phase 2q D.2: exercise InterpolatedYoYInflationCurve::yoyRate with
        // seasonality installed. Mirrors the M_correctZeroRate_grid_* cases
        // but on the YoY curve. With *stationary* monthly factors (12 values
        // repeating annually), YoY seasonality correction factor(d) /
        // factor(d-1Y) ≈ 1 by construction (same calendar month → identical
        // factor). The unadjusted/adjusted columns therefore match within
        // floating-point roundoff but the test still verifies that the wiring
        // is in place and that the path doesn't throw. Non-stationary
        // multi-year factors are forbidden by C++'s isConsistent check
        // (factor(curveBaseDate + nYears) must equal factor(curveBaseDate)).
        std::vector<Date> yoyNodeDates = {
            Date(1, May, 2007),
            Date(13, August, 2008),
            Date(13, August, 2009),
            Date(13, August, 2010),
            Date(13, August, 2012),
            Date(13, August, 2017)
        };
        std::vector<Rate> yoyNodeRates = { 0.025, 0.027, 0.029, 0.031, 0.034, 0.036 };
        auto yoyCurve = ext::make_shared<InterpolatedYoYInflationCurve<Linear>>(
            refDate, yoyNodeDates, yoyNodeRates, freq, dc);
        yoyCurve->enableExtrapolation();

        auto yoyCurveSeas = ext::make_shared<InterpolatedYoYInflationCurve<Linear>>(
            refDate, yoyNodeDates, yoyNodeRates, freq, dc);
        yoyCurveSeas->enableExtrapolation();
        yoyCurveSeas->setSeasonality(seas);

        Date yoyRateProbe[] = {
            Date(1, August,  2007),
            Date(1, December,2007),
            Date(1, June,    2008),
            Date(1, June,    2009),
            Date(1, December,2010),
            Date(1, June,    2012)
        };
        for (size_t i = 0; i < sizeof(yoyRateProbe)/sizeof(Date); ++i) {
            char nm[64];
            std::snprintf(nm, sizeof(nm), "M_correctYoYRate_curve_%zu", i);
            json k{{"date_serial", (Integer)yoyRateProbe[i].serialNumber()}};
            json v{
                {"unadjusted", yoyCurve->yoyRate(yoyRateProbe[i])},
                {"adjusted", yoyCurveSeas->yoyRate(yoyRateProbe[i])}
            };
            out.addCase(nm, k, v);
        }

        // isConsistent — we have 12 factors with Monthly frequency: should pass
        out.addCase("M_isConsistent", inpCommon,
                    json{{"value", seas->isConsistent(*curve)}});
    }

    // -----------------------------------------------------------------------
    // Scenario K — KerkhofSeasonality. Same 12 monthly factors, monthly freq.
    // Kerkhof uses a different cumulative-product formulation.
    // -----------------------------------------------------------------------
    {
        std::vector<Rate> factors = {
            1.0030, 1.0010, 1.0050, 1.0030, 1.0050, 1.0070,
            0.9990, 0.9990, 1.0050, 1.0030, 1.0030, 0.9970
        };
        Date seasonalityBaseDate(1, January, 2007);
        auto seas = ext::make_shared<KerkhofSeasonality>(
            seasonalityBaseDate, factors);

        json inpCommon{
            {"scenario", "K"},
            {"factorCount", (int)factors.size()},
            {"seasonalityBaseDate_serial", (Integer)seasonalityBaseDate.serialNumber()}
        };

        // seasonalityFactor at each month in 2007; Kerkhof's factor at
        // Jan==1.0 (no months traversed), then cumulative.
        Date probeDates[] = {
            Date(15, January,  2007),
            Date(15, February, 2007),
            Date(15, March,    2007),
            Date(15, April,    2007),
            Date(15, May,      2007),
            Date(15, June,     2007),
            Date(15, July,     2007),
            Date(15, August,   2007),
            Date(15, September,2007),
            Date(15, October,  2007),
            Date(15, November, 2007),
            Date(15, December, 2007)
        };
        for (size_t i = 0; i < sizeof(probeDates)/sizeof(Date); ++i) {
            char nm[64];
            std::snprintf(nm, sizeof(nm), "K_seasonalityFactor_%zu", i);
            json k{{"date_serial", (Integer)probeDates[i].serialNumber()}};
            out.addCase(nm, k, json{{"value", seas->seasonalityFactor(probeDates[i])}});
        }

        // correctZeroRate via curve overlay
        auto curveSeasK = ext::make_shared<InterpolatedZeroInflationCurve<Linear>>(
            refDate, nodeDates, nodeRates, freq, dc);
        curveSeasK->enableExtrapolation();
        curveSeasK->setSeasonality(seas);

        Date rateProbe[] = {
            Date(1, August,  2007),
            Date(1, December,2007),
            Date(1, June,    2008),
            Date(1, June,    2009),
            Date(1, December,2010)
        };
        for (size_t i = 0; i < sizeof(rateProbe)/sizeof(Date); ++i) {
            char nm[64];
            std::snprintf(nm, sizeof(nm), "K_correctZeroRate_grid_%zu", i);
            json k{{"date_serial", (Integer)rateProbe[i].serialNumber()}};
            json v{
                {"unadjusted", curve->zeroRate(rateProbe[i])},
                {"adjusted", curveSeasK->zeroRate(rateProbe[i])}
            };
            out.addCase(nm, k, v);
        }
    }

    out.write();
    return 0;
}
