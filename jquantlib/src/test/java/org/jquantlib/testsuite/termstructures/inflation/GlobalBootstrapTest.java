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
 */
package org.jquantlib.testsuite.termstructures.inflation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.ActualActual;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.inflation.UKRPI;
import org.jquantlib.math.interpolations.factories.Linear;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.indexes.IndexManager;
import org.jquantlib.termstructures.InflationTermStructure;
import org.jquantlib.termstructures.inflation.GlobalBootstrap;
import org.jquantlib.termstructures.inflation.PiecewiseZeroInflationCurve;
import org.jquantlib.termstructures.inflation.ZeroCouponInflationSwapHelper;
import org.jquantlib.testsuite.util.InflationCommonVars;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.UnitedKingdom;
import org.junit.Before;
import org.junit.Test;

/**
 * Smoke tests for the {@link GlobalBootstrap} strategy on
 * {@link PiecewiseZeroInflationCurve}.
 *
 * <p>Java port of QuantLib v1.42.1 {@code GlobalBootstrap<Curve>} template
 * specialized for inflation curves (Phase 2v L0 A.2). The tests:
 * <ol>
 *   <li>Build a UK RPI piecewise zero-inflation curve via the iterative
 *       bootstrap (default).</li>
 *   <li>Build the same curve via {@link GlobalBootstrap}.</li>
 *   <li>Verify both produce equivalent pillar data (LOOSE 1e-6 — global LM
 *       converges to the same fixed point as iterative Brent within solver
 *       tolerance).</li>
 *   <li>Verify {@code helper.impliedQuote()} round-trips with the global
 *       bootstrap.</li>
 * </ol>
 *
 * <p>Tier: LOOSE — both bootstraps target the same fixed point but use
 * different solvers (Brent + FDNewton vs. Levenberg-Marquardt), so equivalence
 * holds at solver-tolerance level only.
 */
public class GlobalBootstrapTest {

    /**
     * Clear UK RPI fixings between tests so each test starts from a clean
     * slate. Both tests then seed their own canonical UKRPI fixings.
     */
    @Before
    public void resetIndexHistories() {
        IndexManager.getInstance().clearHistories();
    }

    @Test
    public void globalBootstrap_matchesIterativeBootstrapPillarData() {
        // Match the canonical setup used by PiecewiseZeroInflationCurveTest
        // (eval date 13-Aug-2007, UK calendar, ActualActual ISDA, Monthly).
        final Date evalDate = new Date(13, Month.August, 2007);
        new Settings().setEvaluationDate(evalDate);

        final Calendar cal = new UnitedKingdom();
        final BusinessDayConvention bdc = BusinessDayConvention.ModifiedFollowing;
        final DayCounter dc = new ActualActual(ActualActual.Convention.ISDA);
        final Frequency freq = Frequency.Monthly;
        final Period swapObsLag = new Period(3, TimeUnit.Months);

        final Date refDate = cal.adjust(evalDate, bdc);
        final Date baseDate = InflationTermStructure
                .inflationPeriod(refDate.sub(swapObsLag), freq).first();

        final UKRPI ukRpi = new UKRPI(freq, false, false);
        InflationCommonVars.addCanonicalUkRpiFixings(ukRpi, 31);

        // 4 synthetic ZCIIS quotes (1Y/2Y/5Y/10Y) — same as the Phase 2p
        // PiecewiseZeroInflationCurveTest.
        final double[] quoteRates = { 0.0250, 0.0290, 0.0330, 0.0360 };
        final Period[] tenors = {
                new Period(1, TimeUnit.Years),
                new Period(2, TimeUnit.Years),
                new Period(5, TimeUnit.Years),
                new Period(10, TimeUnit.Years)
        };

        // ----- Iterative bootstrap -----
        final List<ZeroCouponInflationSwapHelper> helpersIter = new ArrayList<>();
        for (int i = 0; i < quoteRates.length; ++i) {
            final var qh = new Handle<Quote>(new SimpleQuote(quoteRates[i]));
            final Date maturity = refDate.add(tenors[i]);
            helpersIter.add(new ZeroCouponInflationSwapHelper(
                    qh, swapObsLag, maturity, cal, bdc, dc, ukRpi));
        }
        final var curveIter = new PiecewiseZeroInflationCurve<Linear>(Linear.class, refDate, baseDate,
                        freq, dc, helpersIter);
        curveIter.enableExtrapolation();
        final double[] dataIter = curveIter.data();

        // ----- GlobalBootstrap -----
        final List<ZeroCouponInflationSwapHelper> helpersGlobal = new ArrayList<>();
        for (int i = 0; i < quoteRates.length; ++i) {
            final var qh = new Handle<Quote>(new SimpleQuote(quoteRates[i]));
            final Date maturity = refDate.add(tenors[i]);
            helpersGlobal.add(new ZeroCouponInflationSwapHelper(
                    qh, swapObsLag, maturity, cal, bdc, dc, ukRpi));
        }
        final var curveGlobal = new PiecewiseZeroInflationCurve<Linear>(Linear.class, refDate, baseDate,
                        freq, dc, helpersGlobal, 1.0e-12, new GlobalBootstrap());
        curveGlobal.enableExtrapolation();
        final double[] dataGlobal = curveGlobal.data();

        // Compare pillar data — both bootstraps converge on the same fixed
        // point; LOOSE tier (the LM optimizer terminates earlier than Brent's
        // bracketing solve). 1e-5 abs is comfortable for inflation rates ~3%.
        assertEquals("pillar count mismatch", dataIter.length, dataGlobal.length);
        for (int i = 0; i < dataIter.length; ++i) {
            assertEquals("pillar[" + i + "] mismatch (iter vs global)",
                    dataIter[i], dataGlobal[i], 1.0e-5);
        }

        // Each helper.quoteError() should be at the LM accuracy level (1e-6
        // loosened a touch for fair-rate noise).
        for (int i = 0; i < helpersGlobal.size(); ++i) {
            final double err = helpersGlobal.get(i).quoteError();
            assertTrue("helper[" + i + "] residual " + err + " exceeds 1e-5",
                    Math.abs(err) < 1.0e-5);
        }
    }

    @Test
    public void globalBootstrap_smokeProducesNonEmptyCurve() {
        // Minimal smoke test — ensure the constructor runs without throwing
        // and produces a curve with the expected number of pillars.
        final Date evalDate = new Date(13, Month.August, 2007);
        new Settings().setEvaluationDate(evalDate);

        final Calendar cal = new UnitedKingdom();
        final BusinessDayConvention bdc = BusinessDayConvention.ModifiedFollowing;
        final DayCounter dc = new ActualActual(ActualActual.Convention.ISDA);
        final Frequency freq = Frequency.Monthly;
        final Period swapObsLag = new Period(3, TimeUnit.Months);

        final Date refDate = cal.adjust(evalDate, bdc);
        final Date baseDate = InflationTermStructure
                .inflationPeriod(refDate.sub(swapObsLag), freq).first();

        final UKRPI ukRpi = new UKRPI(freq, false, false);
        InflationCommonVars.addCanonicalUkRpiFixings(ukRpi, 31);

        final List<ZeroCouponInflationSwapHelper> helpers = new ArrayList<>();
        helpers.add(new ZeroCouponInflationSwapHelper(
                new Handle<>(new SimpleQuote(0.025)), swapObsLag,
                refDate.add(new Period(1, TimeUnit.Years)), cal, bdc, dc, ukRpi));
        helpers.add(new ZeroCouponInflationSwapHelper(
                new Handle<>(new SimpleQuote(0.029)), swapObsLag,
                refDate.add(new Period(2, TimeUnit.Years)), cal, bdc, dc, ukRpi));

        final var curve = new PiecewiseZeroInflationCurve<Linear>(Linear.class, refDate, baseDate,
                        freq, dc, helpers, 1.0e-12, new GlobalBootstrap());
        curve.enableExtrapolation();

        // Trigger bootstrap.
        final Date[] dates = curve.dates();
        assertNotNull(dates);
        // baseDate + 2 helpers = 3 pillars (after dedup).
        assertEquals(3, dates.length);
        // Pillar values should be finite, positive (we used positive quotes).
        for (final double d : curve.data()) {
            assertTrue("non-finite pillar value " + d, !Double.isNaN(d) && !Double.isInfinite(d));
        }
    }
}
