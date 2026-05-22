/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Cross-validated tests for InterpolatingCPICapFloorEngine against
 QuantLib v1.42.1 via
 migration-harness/references/experimental/inflation/cpi_cap_floor_engines.json
 (Phase 2s C.2).
*/
package org.jquantlib.testsuite.experimental.inflation;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.ActualActual;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.experimental.inflation.CPICapFloorTermPriceSurface;
import org.jquantlib.experimental.inflation.InterpolatedCPICapFloorTermPriceSurface;
import org.jquantlib.indexes.CPI;
import org.jquantlib.indexes.inflation.UKRPI;
import org.jquantlib.instruments.CPICapFloor;
import org.jquantlib.instruments.Option;
import org.jquantlib.math.interpolations.factories.Bilinear;
import org.jquantlib.math.interpolations.factories.Linear;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.pricingengines.inflation.InterpolatingCPICapFloorEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.ZeroInflationTermStructure;
import org.jquantlib.termstructures.inflation.PiecewiseZeroInflationCurve;
import org.jquantlib.termstructures.inflation.ZeroCouponInflationSwapHelper;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.jquantlib.testsuite.util.Tolerance;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.MakeSchedule;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.UnitedKingdom;
import org.junit.Test;

import static org.junit.Assert.fail;

/**
 * Probe-driven tests for {@link InterpolatingCPICapFloorEngine}.
 *
 * <p>Replicates the UK RPI fixture from C++ test-suite/inflationcpicapfloor.cpp
 * (cpicapfloorpricer test). Builds a CPI cap/floor term-price surface, then
 * prices CPICapFloor instruments via the engine.
 *
 * <p>Tier rationale: TIGHT for at-grid maturities (engine just looks up the
 * surface price). For interior queries, the engine output equals the surface
 * interpolation — also TIGHT modulo Bilinear arithmetic.
 */
public class CPICapFloorEnginesTest {

    private static final String REF_GROUP =
            "experimental/inflation/cpi_cap_floor_engines";

    @Test
    public void cpiCapFloorEngines_matchesCpp() {
        final Calendar cal = new UnitedKingdom();
        final BusinessDayConvention bdc = BusinessDayConvention.ModifiedFollowing;
        final Date today = new Date(1, Month.June, 2010);
        final Date evaluationDate = cal.adjust(today);
        new Settings().setEvaluationDate(evaluationDate);
        final DayCounter dcZCIIS = new ActualActual(ActualActual.Convention.ISDA);
        final DayCounter dcNominal = new ActualActual(ActualActual.Convention.ISDA);

        // UKRPI fixings
        final Schedule rpiSchedule = new MakeSchedule(
                new Date(1, Month.July, 2007),
                new Date(1, Month.April, 2010),
                new Period(1, TimeUnit.Months), cal, bdc).schedule();
        final double[] fixData = {
                206.1, 207.3, 208.0, 208.9, 209.7, 210.9,
                209.8, 211.4, 212.1, 214.0, 215.1, 216.8,
                216.5, 217.2, 218.4, 217.7, 216.0, 212.9,
                210.1, 211.4, 211.3, 211.5, 212.8, 213.4,
                213.4, 214.4, 215.3, 216.0, 216.6, 218.0,
                217.9, 219.2, 220.7, 222.8
        };
        final UKRPI ii = new UKRPI(Frequency.Monthly, false, false);
        for (int i = 0; i < rpiSchedule.size(); i++) {
            ii.addFixing(rpiSchedule.date(i), fixData[i], true);
        }

        // Nominal yield curve — FlatForward 5%
        final FlatForward nominalTSimpl = new FlatForward(evaluationDate, 0.05,
                dcNominal, Compounding.Continuous, Frequency.Annual);
        final var nominalUK = new Handle<YieldTermStructure>(nominalTSimpl);

        // ZCIIS data
        final Period observationLag = new Period(2, TimeUnit.Months);
        final Date[] zciisDates = {
                new Date(1, Month.June, 2011), new Date(1, Month.June, 2012),
                new Date(1, Month.June, 2013), new Date(1, Month.June, 2014),
                new Date(1, Month.June, 2015), new Date(1, Month.June, 2016),
                new Date(1, Month.June, 2017), new Date(1, Month.June, 2018),
                new Date(1, Month.June, 2019), new Date(1, Month.June, 2020),
                new Date(1, Month.June, 2022), new Date(1, Month.June, 2025),
                new Date(1, Month.June, 2030), new Date(1, Month.June, 2035),
                new Date(1, Month.June, 2040), new Date(1, Month.June, 2050),
                new Date(1, Month.June, 2060)
        };
        final double[] zciisRates = {
                3.087, 3.12, 3.059, 3.11, 3.15, 3.207, 3.253, 3.288, 3.314,
                3.401, 3.458, 3.52, 3.655, 3.668, 3.695, 3.634, 3.629
        };

        final List<ZeroCouponInflationSwapHelper> helpers = new ArrayList<>();
        for (int i = 0; i < zciisRates.length; i++) {
            final Quote q = new SimpleQuote(zciisRates[i] / 100.0);
            final var qh = new Handle<Quote>(q);
            helpers.add(new ZeroCouponInflationSwapHelper(qh, observationLag,
                    zciisDates[i], cal, bdc, dcZCIIS, ii));
        }

        final double baseZeroRate = zciisRates[0] / 100.0;
        final Date lastFixDate = ii.timeSeries().lastKey();
        final Date baseDate = org.jquantlib.termstructures.InflationTermStructure
                .inflationPeriod(lastFixDate, ii.frequency()).first();
        final var pCPIts = new PiecewiseZeroInflationCurve<Linear>(Linear.class, evaluationDate,
                        baseDate, ii.frequency(), dcZCIIS, helpers);
        pCPIts.dates();   // trigger bootstrap

        // Fresh ii2 with bootstrapped curve attached (avoids observer cycle)
        final UKRPI ii2 = new UKRPI(Frequency.Monthly, false, false,
                new Handle<>(pCPIts));

        // Cap/floor surface data
        final Period[] cfMaturities = {
                new Period(3, TimeUnit.Years),  new Period(5, TimeUnit.Years),
                new Period(7, TimeUnit.Years),  new Period(10, TimeUnit.Years),
                new Period(15, TimeUnit.Years), new Period(20, TimeUnit.Years),
                new Period(30, TimeUnit.Years)
        };
        final double[] cStrikes = {0.03, 0.04, 0.05, 0.06};
        final double[] fStrikes = {-0.01, 0.0, 0.01, 0.02};
        final double[][] cPriceData = {
                {227.6, 100.27, 38.8, 14.94},
                {345.32, 127.9, 40.59, 14.11},
                {477.95, 170.19, 50.62, 16.88},
                {757.81, 303.95, 107.62, 43.61},
                {1140.73, 481.89, 168.4, 63.65},
                {1537.6, 607.72, 172.27, 54.87},
                {2211.67, 839.24, 184.75, 45.03}
        };
        final double[][] fPriceData = {
                {15.62, 28.38, 53.61, 104.6},
                {21.45, 36.73, 66.66, 129.6},
                {24.45, 42.08, 77.04, 152.24},
                {39.25, 63.52, 109.2, 203.44},
                {36.82, 63.62, 116.97, 232.73},
                {39.7, 67.47, 121.79, 238.56},
                {41.48, 73.9, 139.75, 286.75}
        };
        final Matrix cPrice = new Matrix(cStrikes.length, cfMaturities.length);
        final Matrix fPrice = new Matrix(fStrikes.length, cfMaturities.length);
        for (int i = 0; i < cStrikes.length; i++)
            for (int j = 0; j < cfMaturities.length; j++)
                cPrice.set(i, j, cPriceData[j][i] / 10000.0);
        for (int i = 0; i < fStrikes.length; i++)
            for (int j = 0; j < cfMaturities.length; j++)
                fPrice.set(i, j, fPriceData[j][i] / 10000.0);

        final var surf = new InterpolatedCPICapFloorTermPriceSurface<Bilinear>(Bilinear.class,
                        1.0, baseZeroRate, observationLag, cal, bdc, dcZCIIS,
                        ii2, CPI.InterpolationType.Flat, nominalUK,
                        cStrikes, fStrikes, cfMaturities, cPrice, fPrice);
        final var surfH = new Handle<CPICapFloorTermPriceSurface>(surf);
        final InterpolatingCPICapFloorEngine engine =
                new InterpolatingCPICapFloorEngine(surfH);

        // Engine inputs
        final Date startDate = new Settings().evaluationDate();
        final Calendar fixCalendar = new UnitedKingdom();
        final Calendar payCalendar = new UnitedKingdom();
        final BusinessDayConvention fixConvention = BusinessDayConvention.Unadjusted;
        final BusinessDayConvention payConvention = BusinessDayConvention.ModifiedFollowing;
        final CPI.InterpolationType observationInterpolation = CPI.InterpolationType.AsIndex;
        final double baseCPI = CPI.laggedFixing(ii2, startDate,
                observationLag, observationInterpolation);

        final ReferenceReader ref = ReferenceReader.load(REF_GROUP);
        final List<String> mismatches = new ArrayList<>();

        for (final String name : ref.caseNames()) {
            final Case c = ref.getCase(name);
            try {
                final String typeStr = c.inputs().getString("type");
                final double strike = c.inputs().getDouble("strike");
                final int matYears = c.inputs().getInt("maturity_years");
                final Option.Type type = "Call".equals(typeStr)
                        ? Option.Type.Call : Option.Type.Put;
                final Date maturity = startDate.add(new Period(matYears, TimeUnit.Years));
                final CPICapFloor cap = new CPICapFloor(type,
                        1.0, startDate, baseCPI, maturity,
                        fixCalendar, fixConvention,
                        payCalendar, payConvention,
                        strike, ii2, observationLag, observationInterpolation);
                cap.setPricingEngine(engine);
                final double cpp = c.expectedDouble();
                final double java = cap.NPV();
                if (!Tolerance.tight(java, cpp)) {
                    mismatches.add(fmt(name, cpp, java, "TIGHT"));
                }
            } catch (final Exception e) {
                mismatches.add(name + ": EXCEPTION " + e.getClass().getSimpleName()
                        + " " + e.getMessage());
            }
        }
        if (!mismatches.isEmpty()) {
            fail(mismatches.size() + " mismatch(es):\n" + String.join("\n", mismatches));
        }
    }

    private static String fmt(final String name, final double expected,
            final double actual, final String tier) {
        return String.format("%s: expected=%.17e actual=%.17e diff=%.3e (%s)",
                name, expected, actual, Math.abs(actual - expected), tier);
    }
}
