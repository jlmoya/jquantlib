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

/*
 Copyright (C) 2019 Quaternion Risk Management Ltd
 Copyright (C) 2020 Skandinaviska Enskilda Banken AB (publ)
 Copyright (C) 2025 Paolo D'Elia

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/
package org.jquantlib.testsuite.termstructures.volatilities.equityfx;

import static org.junit.Assert.assertEquals;

import org.jquantlib.QL;
import org.jquantlib.instruments.Option;
import org.jquantlib.pricingengines.BlackFormula;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.ActualActual;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.experimental.fx.DeltaVolQuote;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.SmileSection;
import org.jquantlib.termstructures.volatilities.equityfx.BlackVolTimeExtrapolation;
import org.jquantlib.termstructures.volatilities.equityfx.BlackVolatilitySurfaceDelta;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Target;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 test-suite/blackvolsurfacedelta.cpp.
 *
 * <p>Body-filled in Phase 5e.5b-CFC-d-68 after porting
 * {@link BlackVolatilitySurfaceDelta} and
 * {@link BlackVolTimeExtrapolation}. Tolerances match the C++ source:
 * {@code 1e-12} for the constant-vol test and {@code 1e-8} for the
 * non-constant, time-extrapolation and smile-interpolation tests.
 *
 * <p>Boost's {@code BOOST_CHECK_CLOSE(a,b,p)} compares
 * {@code |a-b|/max(|a|,|b|) <= p/100}; we therefore compare with relative
 * tolerances {@code 1e-14} (for the {@code 1e-12} percent cases) and
 * {@code 1e-10} (for the {@code 1e-8} percent cases) — modulo loose-tier
 * rules in CLAUDE.md these are within the project's TIGHT band, the same
 * tolerances the C++ test enforces.
 */
public class BlackVolSurfaceDeltaTest {

    /** Relative tolerance equivalent to {@code BOOST_CHECK_CLOSE(...,1e-12)} (percent). */
    private static final double REL_TOL_TIGHT = 1.0e-14;
    /** Relative tolerance equivalent to {@code BOOST_CHECK_CLOSE(...,1e-8)} (percent). */
    private static final double REL_TOL_LOOSE = 1.0e-10;

    public BlackVolSurfaceDeltaTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    private static void checkClose(final double expected, final double actual, final double relTol) {
        final double denom = Math.max(Math.abs(expected), Math.abs(actual));
        final double diff = Math.abs(expected - actual);
        if (denom == 0.0) {
            assertEquals(expected, actual, 0.0);
            return;
        }
        if (diff / denom > relTol) {
            assertEquals("expected " + expected + " actual " + actual + " rel diff " + (diff / denom),
                    expected, actual, relTol * denom);
        }
    }

    @Test
    public void testBlackVolSurfaceDeltaConstantVol() {

        QL.info("Testing BlackVolatilitySurfaceDelta...");

        final Date refDate = new Date(1, Month.January, 2010);
        new Settings().setEvaluationDate(refDate);

        final double constVol = 0.10;

        final Date[] dates = { new Date(1, Month.January, 2011), new Date(1, Month.January, 2012) };
        final double[] putDeltas = { -0.25 };
        final double[] callDeltas = { 0.25 };
        final boolean hasAtm = false;
        final Matrix blackVolMatrix = new Matrix(2, 2);
        for (int r = 0; r < 2; ++r) {
            for (int c = 0; c < 2; ++c) {
                blackVolMatrix.set(r, c, constVol);
            }
        }

        final Calendar cal = new Target();
        final DayCounter dc = new ActualActual(ActualActual.Convention.ISDA);
        final Handle<Quote> spot = new Handle<Quote>(new SimpleQuote(1.0));
        final Handle<YieldTermStructure> dts = new Handle<YieldTermStructure>(
                new FlatForward(0, cal, 0.011, dc));
        final Handle<YieldTermStructure> fts = new Handle<YieldTermStructure>(
                new FlatForward(0, cal, 0.012, dc));

        final BlackVolatilitySurfaceDelta surface = new BlackVolatilitySurfaceDelta(
                refDate, dates, putDeltas, callDeltas, hasAtm, blackVolMatrix,
                dc, cal, spot, dts, fts);

        final double[] times = { 0.25, 0.5, 1.0, 1.5, 2.0, 2.5, 10.0 };
        for (final double t : times) {
            for (double k = 0.5; k < 2.0; k += 0.05) {
                final double vol = surface.blackVol(t, k);
                checkClose(constVol, vol, REL_TOL_TIGHT);
            }
        }
    }

    @Test
    public void testBlackVolSurfaceDeltaNonConstantVol() {

        QL.info("Testing BlackVolatilitySurfaceDelta with non constant vol surface...");

        final Date refDate = new Date(1, Month.January, 2010);
        new Settings().setEvaluationDate(refDate);

        final double[][] volData = {
                {0.15, 0.13, 0.135},
                {0.14, 0.11, 0.125},
                {0.13, 0.10, 0.12},
                {0.125, 0.095, 0.115},
        };
        final Matrix vols = new Matrix(volData);

        final Date[] dates = {
                refDate.add(new Period(1, TimeUnit.Months)),
                refDate.add(new Period(6, TimeUnit.Months)),
                refDate.add(new Period(1, TimeUnit.Years)),
                refDate.add(new Period(2, TimeUnit.Years)),
        };
        final double[] putDeltas = { -0.25 };
        final double[] callDeltas = { 0.25 };
        final boolean hasAtm = true;

        final Calendar cal = new Target();
        final DayCounter dc = new ActualActual(ActualActual.Convention.ISDA);
        final Handle<Quote> spot = new Handle<Quote>(new SimpleQuote(1.18));
        final Handle<YieldTermStructure> dts = new Handle<YieldTermStructure>(
                new FlatForward(0, cal, 0.02, dc));
        final Handle<YieldTermStructure> fts = new Handle<YieldTermStructure>(
                new FlatForward(0, cal, 0.035, dc));

        final BlackVolatilitySurfaceDelta surface = new BlackVolatilitySurfaceDelta(
                refDate, dates, putDeltas, callDeltas, hasAtm, vols,
                dc, cal, spot, dts, fts);

        final double atmStrike = 1.18;

        final SmileSection smile1M = surface.blackVolSmile(refDate.add(new Period(1, TimeUnit.Months)));
        checkClose(0.13010360399, smile1M.volatility(atmStrike), REL_TOL_LOOSE);

        final SmileSection smile15D = surface.blackVolSmile(refDate.add(new Period(15, TimeUnit.Days)));
        checkClose(0.13007226607, smile15D.volatility(atmStrike), REL_TOL_LOOSE);

        final SmileSection smile3M = surface.blackVolSmile(refDate.add(new Period(3, TimeUnit.Months)));
        checkClose(0.115077252583, smile3M.volatility(atmStrike), REL_TOL_LOOSE);

        final double lowStrike = 1.10;
        final double highStrike = 1.30;

        final SmileSection smile6M = surface.blackVolSmile(refDate.add(new Period(6, TimeUnit.Months)));
        checkClose(0.1411379628132, smile6M.volatility(lowStrike), REL_TOL_LOOSE);
        checkClose(0.136291154962, smile6M.volatility(highStrike), REL_TOL_LOOSE);
    }

    @Test
    public void testTimeExtrapolation() {

        QL.info("Testing time extrapolation of BlackVolatilitySurfaceDelta...");

        final Date refDate = new Date(1, Month.January, 2010);
        new Settings().setEvaluationDate(refDate);

        final double[][] volData = {
                {0.15, 0.13, 0.135},
                {0.14, 0.11, 0.125},
                {0.13, 0.10, 0.12},
                {0.125, 0.095, 0.115},
        };
        final Matrix vols = new Matrix(volData);

        final Date[] dates = {
                refDate.add(new Period(1, TimeUnit.Months)),
                refDate.add(new Period(6, TimeUnit.Months)),
                refDate.add(new Period(1, TimeUnit.Years)),
                refDate.add(new Period(2, TimeUnit.Years)),
        };
        final double[] putDeltas = { -0.25 };
        final double[] callDeltas = { 0.25 };
        final boolean hasAtm = true;
        final double atmStrike = 1.18;

        final Calendar cal = new Target();
        final DayCounter dc = new ActualActual(ActualActual.Convention.ISDA);
        final Handle<Quote> spot = new Handle<Quote>(new SimpleQuote(1.18));
        final Handle<YieldTermStructure> dts = new Handle<YieldTermStructure>(
                new FlatForward(0, cal, 0.02, dc));
        final Handle<YieldTermStructure> fts = new Handle<YieldTermStructure>(
                new FlatForward(0, cal, 0.035, dc));

        final BlackVolatilitySurfaceDelta surface1 = new BlackVolatilitySurfaceDelta(
                refDate, dates, putDeltas, callDeltas, hasAtm, vols,
                dc, cal, spot, dts, fts,
                DeltaVolQuote.DeltaType.Spot, DeltaVolQuote.AtmType.AtmSpot, null,
                BlackVolatilitySurfaceDelta.SmileInterpolationMethod.Linear, false,
                BlackVolTimeExtrapolation.Type.FlatVolatility,
                new Period(0, TimeUnit.Days), DeltaVolQuote.DeltaType.Fwd,
                DeltaVolQuote.AtmType.AtmDeltaNeutral, null);

        checkClose(0.095, surface1.blackVol(refDate.add(new Period(2, TimeUnit.Years)), atmStrike), REL_TOL_LOOSE);
        checkClose(0.11684859871, surface1.blackVol(refDate.add(new Period(2, TimeUnit.Years)), atmStrike - 0.1), REL_TOL_LOOSE);
        checkClose(0.11438709864, surface1.blackVol(refDate.add(new Period(2, TimeUnit.Years)), atmStrike + 0.1), REL_TOL_LOOSE);
        checkClose(0.095, surface1.blackVol(refDate.add(new Period(3, TimeUnit.Years)), atmStrike), REL_TOL_LOOSE);
        checkClose(0.11684859871, surface1.blackVol(refDate.add(new Period(3, TimeUnit.Years)), atmStrike - 0.1), REL_TOL_LOOSE);
        checkClose(0.11438709864, surface1.blackVol(refDate.add(new Period(3, TimeUnit.Years)), atmStrike + 0.1), REL_TOL_LOOSE);

        final BlackVolatilitySurfaceDelta surface2 = new BlackVolatilitySurfaceDelta(
                refDate, dates, putDeltas, callDeltas, hasAtm, vols,
                dc, cal, spot, dts, fts,
                DeltaVolQuote.DeltaType.Spot, DeltaVolQuote.AtmType.AtmSpot, null,
                BlackVolatilitySurfaceDelta.SmileInterpolationMethod.Linear, false,
                BlackVolTimeExtrapolation.Type.LinearVariance,
                new Period(0, TimeUnit.Days), DeltaVolQuote.DeltaType.Fwd,
                DeltaVolQuote.AtmType.AtmDeltaNeutral, null);

        checkClose(0.095, surface2.blackVol(refDate.add(new Period(2, TimeUnit.Years)), atmStrike), REL_TOL_LOOSE);
        checkClose(0.11684859871, surface2.blackVol(refDate.add(new Period(2, TimeUnit.Years)), atmStrike - 0.1), REL_TOL_LOOSE);
        checkClose(0.11438709864, surface2.blackVol(refDate.add(new Period(2, TimeUnit.Years)), atmStrike + 0.1), REL_TOL_LOOSE);
        checkClose(0.09327379053, surface2.blackVol(refDate.add(new Period(3, TimeUnit.Years)), atmStrike), REL_TOL_LOOSE);
        checkClose(0.11174756764, surface2.blackVol(refDate.add(new Period(3, TimeUnit.Years)), atmStrike - 0.1), REL_TOL_LOOSE);
        checkClose(0.11128755593, surface2.blackVol(refDate.add(new Period(3, TimeUnit.Years)), atmStrike + 0.1), REL_TOL_LOOSE);

        final BlackVolatilitySurfaceDelta surface3 = new BlackVolatilitySurfaceDelta(
                refDate, dates, putDeltas, callDeltas, hasAtm, vols,
                dc, cal, spot, dts, fts,
                DeltaVolQuote.DeltaType.Spot, DeltaVolQuote.AtmType.AtmSpot, null,
                BlackVolatilitySurfaceDelta.SmileInterpolationMethod.Linear, false,
                BlackVolTimeExtrapolation.Type.UseInterpolator,
                new Period(0, TimeUnit.Days), DeltaVolQuote.DeltaType.Fwd,
                DeltaVolQuote.AtmType.AtmDeltaNeutral, null);
        surface3.enableExtrapolation();

        checkClose(0.095, surface3.blackVol(refDate.add(new Period(2, TimeUnit.Years)), atmStrike), REL_TOL_LOOSE);
        checkClose(0.11684859871, surface3.blackVol(refDate.add(new Period(2, TimeUnit.Years)), atmStrike - 0.1), REL_TOL_LOOSE);
        checkClose(0.11438709864, surface3.blackVol(refDate.add(new Period(2, TimeUnit.Years)), atmStrike + 0.1), REL_TOL_LOOSE);
        checkClose(0.09327379053, surface3.blackVol(refDate.add(new Period(3, TimeUnit.Years)), atmStrike), REL_TOL_LOOSE);
        checkClose(0.11174756764, surface3.blackVol(refDate.add(new Period(3, TimeUnit.Years)), atmStrike - 0.1), REL_TOL_LOOSE);
        checkClose(0.11128755593, surface3.blackVol(refDate.add(new Period(3, TimeUnit.Years)), atmStrike + 0.1), REL_TOL_LOOSE);
    }

    @Test
    public void testSmileInterpolation() {

        QL.info("Testing smile interpolation of BlackVolatilitySurfaceDelta...");

        final Date refDate = new Date(1, Month.January, 2010);
        new Settings().setEvaluationDate(refDate);

        final double[][] volData = {
                {0.15, 0.13, 0.135},
                {0.14, 0.11, 0.125},
                {0.13, 0.10, 0.12},
                {0.125, 0.095, 0.115},
        };
        final Matrix vols = new Matrix(volData);

        final Date[] dates = {
                refDate.add(new Period(1, TimeUnit.Months)),
                refDate.add(new Period(6, TimeUnit.Months)),
                refDate.add(new Period(1, TimeUnit.Years)),
                refDate.add(new Period(2, TimeUnit.Years)),
        };
        final double[] putDeltas = { -0.25 };
        final double[] callDeltas = { 0.25 };
        final boolean hasAtm = true;
        final double atmStrike = 1.18;

        final Calendar cal = new Target();
        final DayCounter dc = new ActualActual(ActualActual.Convention.ISDA);
        final Handle<Quote> spot = new Handle<Quote>(new SimpleQuote(1.18));
        final Handle<YieldTermStructure> dts = new Handle<YieldTermStructure>(
                new FlatForward(0, cal, 0.02, dc));
        final Handle<YieldTermStructure> fts = new Handle<YieldTermStructure>(
                new FlatForward(0, cal, 0.035, dc));

        final BlackVolatilitySurfaceDelta surface1 = new BlackVolatilitySurfaceDelta(
                refDate, dates, putDeltas, callDeltas, hasAtm, vols,
                dc, cal, spot, dts, fts,
                DeltaVolQuote.DeltaType.Spot, DeltaVolQuote.AtmType.AtmSpot, null,
                BlackVolatilitySurfaceDelta.SmileInterpolationMethod.Linear, false,
                BlackVolTimeExtrapolation.Type.FlatVolatility,
                new Period(0, TimeUnit.Days), DeltaVolQuote.DeltaType.Fwd,
                DeltaVolQuote.AtmType.AtmDeltaNeutral, null);

        SmileSection smile = surface1.blackVolSmile(refDate.add(new Period(6, TimeUnit.Months)));
        checkClose(0.11, smile.volatility(atmStrike), REL_TOL_LOOSE);
        checkClose(0.14882625471, smile.volatility(atmStrike - 0.1), REL_TOL_LOOSE);
        checkClose(0.13265179475, smile.volatility(atmStrike + 0.1), REL_TOL_LOOSE);
        checkClose(0.17882625471, smile.volatility(smile.minStrike() - 0.1), REL_TOL_LOOSE);
        checkClose(0.14765179475, smile.volatility(smile.maxStrike() + 0.1), REL_TOL_LOOSE);
        checkClose(0.33413127354, smile.volatility(smile.minStrike() - 0.5), REL_TOL_LOOSE);
        checkClose(0.23825897375, smile.volatility(smile.maxStrike() + 0.5), REL_TOL_LOOSE);

        final BlackVolatilitySurfaceDelta surface2 = new BlackVolatilitySurfaceDelta(
                refDate, dates, putDeltas, callDeltas, hasAtm, vols,
                dc, cal, spot, dts, fts,
                DeltaVolQuote.DeltaType.Spot, DeltaVolQuote.AtmType.AtmSpot, null,
                BlackVolatilitySurfaceDelta.SmileInterpolationMethod.NaturalCubic, false,
                BlackVolTimeExtrapolation.Type.FlatVolatility,
                new Period(0, TimeUnit.Days), DeltaVolQuote.DeltaType.Fwd,
                DeltaVolQuote.AtmType.AtmDeltaNeutral, null);

        smile = surface2.blackVolSmile(refDate.add(new Period(6, TimeUnit.Months)));
        checkClose(0.11, smile.volatility(atmStrike), REL_TOL_LOOSE);
        checkClose(0.15285738778, smile.volatility(atmStrike - 0.1), REL_TOL_LOOSE);
        checkClose(0.13548210924, smile.volatility(atmStrike + 0.1), REL_TOL_LOOSE);
        checkClose(0.16572286711, smile.volatility(smile.minStrike() - 0.1), REL_TOL_LOOSE);
        checkClose(0.13314942082, smile.volatility(smile.maxStrike() + 0.1), REL_TOL_LOOSE);
        checkClose(0.0, smile.volatility(smile.minStrike() - 0.5), REL_TOL_LOOSE);
        checkClose(0.0, smile.volatility(smile.maxStrike() + 0.5), REL_TOL_LOOSE);

        final BlackVolatilitySurfaceDelta surface3 = new BlackVolatilitySurfaceDelta(
                refDate, dates, putDeltas, callDeltas, hasAtm, vols,
                dc, cal, spot, dts, fts,
                DeltaVolQuote.DeltaType.Spot, DeltaVolQuote.AtmType.AtmSpot, null,
                BlackVolatilitySurfaceDelta.SmileInterpolationMethod.FinancialCubic, false,
                BlackVolTimeExtrapolation.Type.FlatVolatility,
                new Period(0, TimeUnit.Days), DeltaVolQuote.DeltaType.Fwd,
                DeltaVolQuote.AtmType.AtmDeltaNeutral, null);

        smile = surface3.blackVolSmile(refDate.add(new Period(6, TimeUnit.Months)));
        checkClose(0.11, smile.volatility(atmStrike), REL_TOL_LOOSE);
        checkClose(0.15285738778, smile.volatility(atmStrike - 0.1), REL_TOL_LOOSE);
        checkClose(0.13548210924, smile.volatility(atmStrike + 0.1), REL_TOL_LOOSE);
        checkClose(0.16572286711, smile.volatility(smile.minStrike() - 0.1), REL_TOL_LOOSE);
        checkClose(0.13314942082, smile.volatility(smile.maxStrike() + 0.1), REL_TOL_LOOSE);
        checkClose(0.0, smile.volatility(smile.minStrike() - 0.5), REL_TOL_LOOSE);
        checkClose(0.0, smile.volatility(smile.maxStrike() + 0.5), REL_TOL_LOOSE);

        final BlackVolatilitySurfaceDelta surface4 = new BlackVolatilitySurfaceDelta(
                refDate, dates, putDeltas, callDeltas, hasAtm, vols,
                dc, cal, spot, dts, fts,
                DeltaVolQuote.DeltaType.Spot, DeltaVolQuote.AtmType.AtmSpot, null,
                BlackVolatilitySurfaceDelta.SmileInterpolationMethod.CubicSpline, false,
                BlackVolTimeExtrapolation.Type.FlatVolatility,
                new Period(0, TimeUnit.Days), DeltaVolQuote.DeltaType.Fwd,
                DeltaVolQuote.AtmType.AtmDeltaNeutral, null);

        smile = surface4.blackVolSmile(refDate.add(new Period(6, TimeUnit.Months)));
        checkClose(0.11, smile.volatility(atmStrike), REL_TOL_LOOSE);
        checkClose(0.15226345029, smile.volatility(atmStrike - 0.1), REL_TOL_LOOSE);
        checkClose(0.13619688725, smile.volatility(atmStrike + 0.1), REL_TOL_LOOSE);
        checkClose(0.16765348886, smile.volatility(smile.minStrike() - 0.1), REL_TOL_LOOSE);
        checkClose(0.12948693808, smile.volatility(smile.maxStrike() + 0.1), REL_TOL_LOOSE);
        checkClose(0.0, smile.volatility(smile.minStrike() - 0.5), REL_TOL_LOOSE);
        checkClose(0.0, smile.volatility(smile.maxStrike() + 0.5), REL_TOL_LOOSE);
    }

    /**
     * Faithful port of C++ v1.43 {@code testSmileSectionWithAtmLevel}
     * ({@code test-suite/blackvolsurfacedelta.cpp}).
     *
     * <p>{@link BlackVolatilitySurfaceDelta} carries spot plus both yield curves, so v1.43 promoted its
     * private {@code forward(Time)} helper to a public {@code atmLevel(Time)} override. That is what gives
     * the {@link SmileSection} returned by {@code smileSection()} a usable {@code atmLevel()} — and hence a
     * working {@code optionPrice()}, which without the override fails the base class's requirement that an
     * ATM level be known.
     *
     * <p>Both checks use the C++ tolerance of {@code 1e-12} verbatim. Neither is a numerical-accuracy check:
     * the first compares against the same {@code spot * df_f / df_d} product the surface computes, and the
     * second against {@code blackFormula} evaluated at exactly the vol the smile reports — so the only room
     * for disagreement is the order of a handful of floating-point operations.
     */
    @Test
    public void testSmileSectionWithAtmLevel() {

        QL.info("Testing SmileSection from a vol surface that overrides atmLevel(Time)...");

        final Date refDate = new Date(1, Month.January, 2010);
        new Settings().setEvaluationDate(refDate);
        final DayCounter dc = new ActualActual(ActualActual.Convention.ISDA);

        final Date[] dates = {
                new Date(1, Month.January, 2011),
                new Date(1, Month.January, 2012),
        };
        final double[] putDeltas = { -0.25 };
        final double[] callDeltas = { 0.25 };
        final Matrix volMatrix = new Matrix(2, 2);
        volMatrix.fill(0.10);

        final Handle<Quote> spot = new Handle<Quote>(new SimpleQuote(1.0));
        final Handle<YieldTermStructure> dts = new Handle<YieldTermStructure>(
                new FlatForward(refDate, 0.011, dc));
        final Handle<YieldTermStructure> fts = new Handle<YieldTermStructure>(
                new FlatForward(refDate, 0.012, dc));

        final BlackVolatilitySurfaceDelta surface = new BlackVolatilitySurfaceDelta(
                refDate, dates, putDeltas, callDeltas, false, volMatrix,
                dc, new Target(), spot, dts, fts);

        final Date maturity = new Date(1, Month.July, 2011);
        final SmileSection smile = surface.smileSection(maturity);

        // atmLevel matches spot * df_q / df_r (FX-style forward)
        final double expectedFwd = spot.currentLink().value()
                * fts.currentLink().discount(maturity) / dts.currentLink().discount(maturity);
        final double tolerance = 1.0e-12;
        assertEquals("smile atmLevel mismatch", expectedFwd, smile.atmLevel(), tolerance);

        // optionPrice() works (without the atmLevel(Time) override it would throw) and matches the
        // Black formula at the forward.
        final double df = dts.currentLink().discount(maturity);
        final double callPrice = smile.optionPrice(expectedFwd, Option.Type.Call, df);
        final double t = surface.timeFromReference(maturity);
        final double vol = smile.volatility(expectedFwd);
        final double expectedCall = BlackFormula.blackFormula(Option.Type.Call, expectedFwd, expectedFwd,
                vol * Math.sqrt(t), df);
        assertEquals("smile optionPrice mismatch", expectedCall, callPrice, tolerance);
    }
}
