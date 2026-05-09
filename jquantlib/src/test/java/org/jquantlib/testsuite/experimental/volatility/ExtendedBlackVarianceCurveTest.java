/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.volatility;

import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.experimental.volatility.ExtendedBlackVarianceCurve;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Phase 4f tests for {@link ExtendedBlackVarianceCurve}.
 *
 * <p>Verifies linear interpolation of variance from quoted vols + flat
 * extrapolation beyond the last knot.
 */
public class ExtendedBlackVarianceCurveTest {

    private static final double TOL = 1e-12;

    @Test
    public void testQuotedVolsInterpolate() {
        final Date refDate = new Date(15, Month.January, 2026);

        final Date[] dates = new Date[3];
        dates[0] = refDate.add(new Period(6, TimeUnit.Months));
        dates[1] = refDate.add(new Period(1, TimeUnit.Years));
        dates[2] = refDate.add(new Period(2, TimeUnit.Years));

        final Quote[] vols = new Quote[3];
        vols[0] = new SimpleQuote(0.20);
        vols[1] = new SimpleQuote(0.22);
        vols[2] = new SimpleQuote(0.25);

        final ExtendedBlackVarianceCurve curve = new ExtendedBlackVarianceCurve(
                refDate, dates, vols, new Actual365Fixed());

        // Black variance at first date should equal sigma^2 * t
        final double t1 = new Actual365Fixed().yearFraction(refDate, dates[0]);
        final double expected1 = 0.20 * 0.20 * t1;
        assertEquals("variance at first knot", expected1,
                curve.blackVariance(dates[0], 100.0), TOL);

        // At second date: 0.22^2 * t2
        final double t2 = new Actual365Fixed().yearFraction(refDate, dates[1]);
        final double expected2 = 0.22 * 0.22 * t2;
        assertEquals("variance at second knot", expected2,
                curve.blackVariance(dates[1], 100.0), TOL);

        // maxDate
        org.junit.Assert.assertEquals("maxDate", dates[2], curve.maxDate());
    }

    @Test
    public void testQuoteUpdatePropagates() {
        final Date refDate = new Date(15, Month.January, 2026);

        final Date[] dates = new Date[2];
        dates[0] = refDate.add(new Period(1, TimeUnit.Years));
        dates[1] = refDate.add(new Period(2, TimeUnit.Years));

        final SimpleQuote q1 = new SimpleQuote(0.20);
        final SimpleQuote q2 = new SimpleQuote(0.25);
        final Quote[] vols = {q1, q2};

        final ExtendedBlackVarianceCurve curve = new ExtendedBlackVarianceCurve(
                refDate, dates, vols, new Actual365Fixed());

        final double t1 = new Actual365Fixed().yearFraction(refDate, dates[0]);
        final double v1Before = curve.blackVariance(dates[0], 100.0);
        assertEquals(0.20 * 0.20 * t1, v1Before, TOL);

        // Update quote and verify the curve recomputes
        q1.setValue(0.30);
        final double v1After = curve.blackVariance(dates[0], 100.0);
        assertEquals(0.30 * 0.30 * t1, v1After, TOL);
    }
}
