/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.volatility;

import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.experimental.volatility.DumasParametricVolSurface;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Phase Production-Audit tests for {@link DumasParametricVolSurface}.
 *
 * <p>Cross-validated against the v1.42.1 test-suite reference helper
 * (riskneutraldensitycalculator.cpp lines 213-249) at
 * {@code migration-harness/references/experimental/volatility/dumas_parametric_vol_surface.json}.
 *
 * @author Phase Production-Audit
 */
public class DumasParametricVolSurfaceTest {

    private static final double TOL = 1.0e-12;

    @Test
    public void testFormulaAgainstCppReference() {
        final ReferenceReader ref = ReferenceReader.load(
                "experimental/volatility/dumas_parametric_vol_surface");

        // Match the probe setup exactly: spot=100, r=1.5%, q=2.5%,
        // dc=Actual365Fixed, b1..b5 = (0.25, 0.03, 0.005, -0.02, -0.005).
        // For flat surface alternative: b1=0.30, others=0.
        final DayCounter dc = new Actual365Fixed();
        final Date today = new Date(15, Month.January, 2026);

        final var spot = new Handle<Quote>(new SimpleQuote(100.0));
        final var rTS = new Handle<YieldTermStructure>(
                new FlatForward(today, new Handle<Quote>(new SimpleQuote(0.015)), dc));
        final var qTS = new Handle<YieldTermStructure>(
                new FlatForward(today, new Handle<Quote>(new SimpleQuote(0.025)), dc));

        final DumasParametricVolSurface skewed =
                new DumasParametricVolSurface(0.25, 0.03, 0.005, -0.02, -0.005,
                        spot, rTS, qTS);
        final DumasParametricVolSurface flat =
                new DumasParametricVolSurface(0.30, 0.0, 0.0, 0.0, 0.0,
                        spot, rTS, qTS);

        for (final String name : ref.caseNames()) {
            final ReferenceReader.Case c = ref.getCase(name);
            final JSONObject in = c.inputs();
            final double t = in.getDouble("t");
            final double k = in.getDouble("K");
            final boolean isFlat = in.getBoolean("flat");

            final double exp = ((JSONObject) c.expectedRaw()).getDouble("blackVol");
            final DumasParametricVolSurface s = isFlat ? flat : skewed;
            final double act = s.blackVol(t, k, true);

            assertEquals(name + " blackVol",
                    exp, act,
                    Math.max(TOL, Math.abs(exp) * TOL));
        }
    }

    /** Sanity: t=0 short-circuit returns b1 regardless of strike. */
    @Test
    public void testZeroTimeReturnsB1() {
        final DayCounter dc = new Actual365Fixed();
        final Date today = new Date(15, Month.January, 2026);
        final var spot = new Handle<Quote>(new SimpleQuote(100.0));
        final var rTS = new Handle<YieldTermStructure>(
                new FlatForward(today, new Handle<Quote>(new SimpleQuote(0.015)), dc));
        final var qTS = new Handle<YieldTermStructure>(
                new FlatForward(today, new Handle<Quote>(new SimpleQuote(0.025)), dc));

        final DumasParametricVolSurface s =
                new DumasParametricVolSurface(0.25, 0.03, 0.005, -0.02, -0.005,
                        spot, rTS, qTS);
        for (double k : new double[]{50, 100, 150, 500}) {
            assertEquals("t=0 returns b1 at K=" + k,
                    0.25, s.blackVol(0.0, k, true), TOL);
        }
    }

    /** Sanity: minStrike()=0, maxStrike()=Double.MAX_VALUE. */
    @Test
    public void testStrikeRange() {
        final DayCounter dc = new Actual365Fixed();
        final Date today = new Date(15, Month.January, 2026);
        final var spot = new Handle<Quote>(new SimpleQuote(100.0));
        final var rTS = new Handle<YieldTermStructure>(
                new FlatForward(today, new Handle<Quote>(new SimpleQuote(0.015)), dc));
        final var qTS = new Handle<YieldTermStructure>(
                new FlatForward(today, new Handle<Quote>(new SimpleQuote(0.025)), dc));
        final DumasParametricVolSurface s =
                new DumasParametricVolSurface(0.25, 0.03, 0.005, -0.02, -0.005,
                        spot, rTS, qTS);
        assertEquals(0.0, s.minStrike(), 0.0);
        assertEquals(Double.MAX_VALUE, s.maxStrike(), 0.0);
    }
}
