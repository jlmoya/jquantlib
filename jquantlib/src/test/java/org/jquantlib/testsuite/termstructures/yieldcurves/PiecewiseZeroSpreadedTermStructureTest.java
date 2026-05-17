/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

package org.jquantlib.testsuite.termstructures.yieldcurves;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.math.interpolations.CubicInterpolation;
import org.jquantlib.math.interpolations.Interpolation.Interpolator;
import org.jquantlib.math.interpolations.factories.BackwardFlat;
import org.jquantlib.math.interpolations.factories.Cubic;
import org.jquantlib.math.interpolations.factories.ForwardFlat;
import org.jquantlib.math.interpolations.factories.Linear;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.InterpolatedPiecewiseZeroSpreadedTermStructure;
import org.jquantlib.termstructures.yieldcurves.InterpolatedZeroCurve;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Target;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 test-suite/piecewisezerospreadedtermstructure.cpp
 * (Phase 5e.5b-CFC-d-55).
 *
 * <p>10 BOOST_AUTO_TEST_CASE methods exercising
 * {@link InterpolatedPiecewiseZeroSpreadedTermStructure} (a yield curve
 * built from a base curve plus a vector of spread quotes interpolated
 * across pillar dates).
 *
 * <p>Source: {@code test-suite/piecewisezerospreadedtermstructure.cpp}
 * v1.42.1 @ {@code 099987f0ca}.
 */
public class PiecewiseZeroSpreadedTermStructureTest {

    public PiecewiseZeroSpreadedTermStructureTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }


    //
    // ---- test-fixture (mirrors C++ struct CommonVars) ----
    //

    private static final class CommonVars {
        final Calendar calendar;
        final int settlementDays;
        final DayCounter dayCount;
        final Compounding compounding;
        final YieldTermStructure termStructure;
        final Date today;
        final Date settlementDate;

        CommonVars() {
            calendar = new Target();
            settlementDays = 2;
            today = new Date(9, Month.June, 2009);
            compounding = Compounding.Continuous;
            dayCount = new Actual360();
            settlementDate = calendar.advance(today, settlementDays, TimeUnit.Days);

            new Settings().setEvaluationDate(today);

            final int[] ts = { 13, 41, 75, 165, 256, 345, 524, 703 };
            final double[] r = {
                0.035, 0.033, 0.034, 0.034, 0.036, 0.037, 0.039, 0.040
            };
            final Date[] dates = new Date[ts.length + 1];
            final double[] rates = new double[ts.length + 1];
            dates[0] = settlementDate;
            rates[0] = 0.035;
            for (int i = 0; i < ts.length; ++i) {
                dates[i + 1] = calendar.advance(today, ts[i], TimeUnit.Days);
                rates[i + 1] = r[i];
            }
            termStructure = new InterpolatedZeroCurve<Linear>(
                    Linear.class, dates, rates, dayCount);
        }
    }


    //
    // ---- helpers ----
    //

    /** Default tolerance, exactly matching the C++ test-suite (1e-9). */
    private static final double TOL = 1.0e-9;

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static Handle<Quote>[] handles(final SimpleQuote... quotes) {
        final Handle[] arr = new Handle[quotes.length];
        for (int i = 0; i < quotes.length; ++i) {
            arr[i] = new Handle<Quote>(quotes[i]);
        }
        return (Handle<Quote>[]) arr;
    }


    //
    // ---- tests ----
    //

    @Test
    public void testFlatInterpolationLeft() {
        QL.info("Testing flat interpolation before the first spreaded date...");

        final CommonVars vars = new CommonVars();

        final SimpleQuote spread1 = new SimpleQuote(0.02);
        final SimpleQuote spread2 = new SimpleQuote(0.03);
        final Handle<Quote>[] spreads = handles(spread1, spread2);

        final Date[] spreadDates = new Date[] {
            vars.calendar.advance(vars.today, 8, TimeUnit.Months),
            vars.calendar.advance(vars.today, 15, TimeUnit.Months),
        };

        final Date interpolationDate = vars.calendar.advance(vars.today, 6, TimeUnit.Months);

        final InterpolatedPiecewiseZeroSpreadedTermStructure spreadedTermStructure =
                new InterpolatedPiecewiseZeroSpreadedTermStructure(
                        new Handle<YieldTermStructure>(vars.termStructure),
                        spreads, spreadDates, new Linear());

        final double t = vars.dayCount.yearFraction(vars.today, interpolationDate);
        final double interpolatedZeroRate = spreadedTermStructure
                .zeroRate(t, vars.compounding, Frequency.Annual, false).rate();

        final double expectedRate = vars.termStructure
                .zeroRate(t, vars.compounding, Frequency.Annual, false).rate()
                + spread1.value();

        assertEquals("flat-left interpolated zero rate",
                expectedRate, interpolatedZeroRate, TOL);
    }

    @Test
    public void testFlatInterpolationRight() {
        QL.info("Testing flat interpolation after the last spreaded date...");

        final CommonVars vars = new CommonVars();

        final SimpleQuote spread1 = new SimpleQuote(0.02);
        final SimpleQuote spread2 = new SimpleQuote(0.03);
        final Handle<Quote>[] spreads = handles(spread1, spread2);

        final Date[] spreadDates = new Date[] {
            vars.calendar.advance(vars.today, 8, TimeUnit.Months),
            vars.calendar.advance(vars.today, 15, TimeUnit.Months),
        };

        final Date interpolationDate = vars.calendar.advance(vars.today, 20, TimeUnit.Months);

        final InterpolatedPiecewiseZeroSpreadedTermStructure spreadedTermStructure =
                new InterpolatedPiecewiseZeroSpreadedTermStructure(
                        new Handle<YieldTermStructure>(vars.termStructure),
                        spreads, spreadDates, new Linear());
        spreadedTermStructure.enableExtrapolation();

        final double t = vars.dayCount.yearFraction(vars.today, interpolationDate);
        final double interpolatedZeroRate = spreadedTermStructure
                .zeroRate(t, vars.compounding, Frequency.Annual, true).rate();

        final double expectedRate = vars.termStructure
                .zeroRate(t, vars.compounding, Frequency.Annual, true).rate()
                + spread2.value();

        assertEquals("flat-right interpolated zero rate",
                expectedRate, interpolatedZeroRate, TOL);
    }

    @Test
    public void testLinearInterpolationMultipleSpreads() {
        QL.info("Testing linear interpolation with more than two spreaded dates...");

        final CommonVars vars = new CommonVars();

        final SimpleQuote spread1 = new SimpleQuote(0.02);
        final SimpleQuote spread2 = new SimpleQuote(0.02);
        final SimpleQuote spread3 = new SimpleQuote(0.035);
        final SimpleQuote spread4 = new SimpleQuote(0.04);
        final Handle<Quote>[] spreads = handles(spread1, spread2, spread3, spread4);

        final Date[] spreadDates = new Date[] {
            vars.calendar.advance(vars.today, 90, TimeUnit.Days),
            vars.calendar.advance(vars.today, 150, TimeUnit.Days),
            vars.calendar.advance(vars.today, 30, TimeUnit.Months),
            vars.calendar.advance(vars.today, 40, TimeUnit.Months),
        };

        final Date interpolationDate = vars.calendar.advance(vars.today, 120, TimeUnit.Days);

        final InterpolatedPiecewiseZeroSpreadedTermStructure spreadedTermStructure =
                new InterpolatedPiecewiseZeroSpreadedTermStructure(
                        new Handle<YieldTermStructure>(vars.termStructure),
                        spreads, spreadDates, new Linear());

        final double t = vars.dayCount.yearFraction(vars.today, interpolationDate);
        final double interpolatedZeroRate = spreadedTermStructure
                .zeroRate(t, vars.compounding, Frequency.Annual, false).rate();

        final double expectedRate = vars.termStructure
                .zeroRate(t, vars.compounding, Frequency.Annual, false).rate()
                + spread1.value();

        assertEquals("multi-spread linear interpolated zero rate",
                expectedRate, interpolatedZeroRate, TOL);
    }

    @Test
    public void testLinearInterpolation() {
        QL.info("Testing linear interpolation between two dates...");

        final CommonVars vars = new CommonVars();

        final SimpleQuote spread1 = new SimpleQuote(0.02);
        final SimpleQuote spread2 = new SimpleQuote(0.03);
        final Handle<Quote>[] spreads = handles(spread1, spread2);

        final Date[] spreadDates = new Date[] {
            vars.calendar.advance(vars.today, 100, TimeUnit.Days),
            vars.calendar.advance(vars.today, 150, TimeUnit.Days),
        };

        final Date interpolationDate = vars.calendar.advance(vars.today, 120, TimeUnit.Days);

        final InterpolatedPiecewiseZeroSpreadedTermStructure spreadedTermStructure =
                new InterpolatedPiecewiseZeroSpreadedTermStructure(
                        new Handle<YieldTermStructure>(vars.termStructure),
                        spreads, spreadDates, new Linear());

        final Date d0 = vars.calendar.advance(vars.today, 100, TimeUnit.Days);
        final Date d1 = vars.calendar.advance(vars.today, 150, TimeUnit.Days);
        final Date d2 = vars.calendar.advance(vars.today, 120, TimeUnit.Days);

        final double m = (0.03 - 0.02) / vars.dayCount.yearFraction(d0, d1);
        final double expectedRate = m * vars.dayCount.yearFraction(d0, d2) + 0.054;

        final double t = vars.dayCount.yearFraction(vars.settlementDate, interpolationDate);
        final double interpolatedZeroRate = spreadedTermStructure
                .zeroRate(t, vars.compounding, Frequency.Annual, false).rate();

        assertEquals("linear-interpolated zero rate (analytic slope check)",
                expectedRate, interpolatedZeroRate, TOL);
    }

    @Test
    public void testForwardFlatInterpolation() {
        QL.info("Testing forward flat interpolation between two dates...");

        final CommonVars vars = new CommonVars();

        final SimpleQuote spread1 = new SimpleQuote(0.02);
        final SimpleQuote spread2 = new SimpleQuote(0.03);
        final Handle<Quote>[] spreads = handles(spread1, spread2);

        final Date[] spreadDates = new Date[] {
            vars.calendar.advance(vars.today, 75, TimeUnit.Days),
            vars.calendar.advance(vars.today, 260, TimeUnit.Days),
        };

        final Date interpolationDate = vars.calendar.advance(vars.today, 100, TimeUnit.Days);

        final InterpolatedPiecewiseZeroSpreadedTermStructure spreadedTermStructure =
                new InterpolatedPiecewiseZeroSpreadedTermStructure(
                        new Handle<YieldTermStructure>(vars.termStructure),
                        spreads, spreadDates, new ForwardFlat());

        final double t = vars.dayCount.yearFraction(vars.today, interpolationDate);
        final double interpolatedZeroRate = spreadedTermStructure
                .zeroRate(t, vars.compounding, Frequency.Annual, false).rate();

        final double expectedRate = vars.termStructure
                .zeroRate(t, vars.compounding, Frequency.Annual, false).rate()
                + spread1.value();

        assertEquals("ForwardFlat-interpolated zero rate",
                expectedRate, interpolatedZeroRate, TOL);
    }

    @Test
    public void testBackwardFlatInterpolation() {
        QL.info("Testing backward flat interpolation between two dates...");

        final CommonVars vars = new CommonVars();

        final SimpleQuote spread1 = new SimpleQuote(0.02);
        final SimpleQuote spread2 = new SimpleQuote(0.03);
        final SimpleQuote spread3 = new SimpleQuote(0.04);
        final Handle<Quote>[] spreads = handles(spread1, spread2, spread3);

        final Date[] spreadDates = new Date[] {
            vars.calendar.advance(vars.today, 100, TimeUnit.Days),
            vars.calendar.advance(vars.today, 200, TimeUnit.Days),
            vars.calendar.advance(vars.today, 300, TimeUnit.Days),
        };

        final Date interpolationDate = vars.calendar.advance(vars.today, 110, TimeUnit.Days);

        final InterpolatedPiecewiseZeroSpreadedTermStructure spreadedTermStructure =
                new InterpolatedPiecewiseZeroSpreadedTermStructure(
                        new Handle<YieldTermStructure>(vars.termStructure),
                        spreads, spreadDates, new BackwardFlat());

        final double t = vars.dayCount.yearFraction(vars.today, interpolationDate);
        final double interpolatedZeroRate = spreadedTermStructure
                .zeroRate(t, vars.compounding, Frequency.Annual, false).rate();

        final double expectedRate = vars.termStructure
                .zeroRate(t, vars.compounding, Frequency.Annual, false).rate()
                + spread2.value();

        assertEquals("BackwardFlat-interpolated zero rate",
                expectedRate, interpolatedZeroRate, TOL);
    }

    @Test
    public void testDefaultInterpolation() {
        QL.info("Testing default interpolation between two dates...");

        final CommonVars vars = new CommonVars();

        final SimpleQuote spread1 = new SimpleQuote(0.02);
        final SimpleQuote spread2 = new SimpleQuote(0.02);
        final Handle<Quote>[] spreads = handles(spread1, spread2);

        final Date[] spreadDates = new Date[] {
            vars.calendar.advance(vars.today, 75, TimeUnit.Days),
            vars.calendar.advance(vars.today, 160, TimeUnit.Days),
        };

        final Date interpolationDate = vars.calendar.advance(vars.today, 100, TimeUnit.Days);

        // C++: PiecewiseZeroSpreadedTermStructure = ...<Linear> typedef.
        final InterpolatedPiecewiseZeroSpreadedTermStructure spreadedTermStructure =
                new InterpolatedPiecewiseZeroSpreadedTermStructure(
                        new Handle<YieldTermStructure>(vars.termStructure),
                        spreads, spreadDates, new Linear());

        final double t = vars.dayCount.yearFraction(vars.today, interpolationDate);
        final double interpolatedZeroRate = spreadedTermStructure
                .zeroRate(t, vars.compounding, Frequency.Annual, false).rate();

        final double expectedRate = vars.termStructure
                .zeroRate(t, vars.compounding, Frequency.Annual, false).rate()
                + spread1.value();

        assertEquals("default (Linear) interpolated zero rate",
                expectedRate, interpolatedZeroRate, TOL);
    }

    @Test
    public void testSetInterpolationFactory() {
        QL.info("Testing factory constructor with additional parameters...");

        final CommonVars vars = new CommonVars();

        final SimpleQuote spread1 = new SimpleQuote(0.02);
        final SimpleQuote spread2 = new SimpleQuote(0.03);
        final SimpleQuote spread3 = new SimpleQuote(0.01);
        final Handle<Quote>[] spreads = handles(spread1, spread2, spread3);

        final Date[] spreadDates = new Date[] {
            vars.calendar.advance(vars.today, 8, TimeUnit.Months),
            vars.calendar.advance(vars.today, 15, TimeUnit.Months),
            vars.calendar.advance(vars.today, 25, TimeUnit.Months),
        };

        final Date interpolationDate = vars.calendar.advance(vars.today, 11, TimeUnit.Months);

        final Frequency freq = Frequency.NoFrequency;

        // C++: Cubic(CubicInterpolation::Spline, false)
        // Defaults for the rest:
        //   leftCondition  = SecondDerivative, leftValue  = 0.0,
        //   rightCondition = SecondDerivative, rightValue = 0.0.
        final Interpolator factory = new Cubic(
                CubicInterpolation.DerivativeApprox.Spline,
                false,
                CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0,
                CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0);

        final InterpolatedPiecewiseZeroSpreadedTermStructure spreadedTermStructure =
                new InterpolatedPiecewiseZeroSpreadedTermStructure(
                        new Handle<YieldTermStructure>(vars.termStructure),
                        spreads, spreadDates, vars.compounding, freq, factory);

        final double t = vars.dayCount.yearFraction(vars.today, interpolationDate);
        final double interpolatedZeroRate = spreadedTermStructure
                .zeroRate(t, vars.compounding, Frequency.Annual, false).rate();

        final double expectedRate = vars.termStructure
                .zeroRate(t, vars.compounding, Frequency.Annual, false).rate()
                + 0.026065770863;

        assertEquals("Cubic-factory interpolated zero rate",
                expectedRate, interpolatedZeroRate, TOL);
    }

    @Test
    public void testMaxDate() {
        QL.info("Testing term structure max date...");

        final CommonVars vars = new CommonVars();

        final SimpleQuote spread1 = new SimpleQuote(0.02);
        final SimpleQuote spread2 = new SimpleQuote(0.03);
        final Handle<Quote>[] spreads = handles(spread1, spread2);

        final Date[] spreadDates = new Date[] {
            vars.calendar.advance(vars.today, 8, TimeUnit.Months),
            vars.calendar.advance(vars.today, 15, TimeUnit.Months),
        };

        final InterpolatedPiecewiseZeroSpreadedTermStructure spreadedTermStructure =
                new InterpolatedPiecewiseZeroSpreadedTermStructure(
                        new Handle<YieldTermStructure>(vars.termStructure),
                        spreads, spreadDates, new Linear());

        final Date maxDate = spreadedTermStructure.maxDate();

        final Date originalMax = vars.termStructure.maxDate();
        final Date lastSpread = spreadDates[spreadDates.length - 1];
        final Date expectedDate = originalMax.le(lastSpread) ? originalMax : lastSpread;

        if (!maxDate.equals(expectedDate)) {
            fail("unable to reproduce max date\n"
                    + "    calculated: " + maxDate + "\n"
                    + "    expected:   " + expectedDate);
        }
    }

    @Test
    public void testQuoteChanging() {
        QL.info("Testing quote update...");

        final CommonVars vars = new CommonVars();

        final SimpleQuote spread1 = new SimpleQuote(0.02);
        final SimpleQuote spread2 = new SimpleQuote(0.03);
        final Handle<Quote>[] spreads = handles(spread1, spread2);

        final Date[] spreadDates = new Date[] {
            vars.calendar.advance(vars.today, 100, TimeUnit.Days),
            vars.calendar.advance(vars.today, 150, TimeUnit.Days),
        };

        final Date interpolationDate = vars.calendar.advance(vars.today, 120, TimeUnit.Days);

        final InterpolatedPiecewiseZeroSpreadedTermStructure spreadedTermStructure =
                new InterpolatedPiecewiseZeroSpreadedTermStructure(
                        new Handle<YieldTermStructure>(vars.termStructure),
                        spreads, spreadDates, new BackwardFlat());

        final double t = vars.dayCount.yearFraction(vars.settlementDate, interpolationDate);
        double interpolatedZeroRate = spreadedTermStructure
                .zeroRate(t, vars.compounding, Frequency.Annual, false).rate();
        double expectedRate = vars.termStructure
                .zeroRate(t, vars.compounding, Frequency.Annual, false).rate()
                + 0.03;

        assertEquals("pre-change BackwardFlat interpolated zero rate",
                expectedRate, interpolatedZeroRate, TOL);

        spread2.setValue(0.025);

        interpolatedZeroRate = spreadedTermStructure
                .zeroRate(t, vars.compounding, Frequency.Annual, false).rate();
        expectedRate = vars.termStructure
                .zeroRate(t, vars.compounding, Frequency.Annual, false).rate()
                + 0.025;

        assertEquals("post-change BackwardFlat interpolated zero rate",
                expectedRate, interpolatedZeroRate, TOL);
    }
}
