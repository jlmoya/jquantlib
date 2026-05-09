/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.volatility;

import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.experimental.volatility.SABRVolTermStructure;
import org.jquantlib.termstructures.volatilities.Sabr;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Phase 4f smoke tests for {@link SABRVolTermStructure}.
 *
 * <p>Cross-checks the analytic SABR closed form (Hagan) wrapped by the
 * term-structure shell.
 */
public class SABRVolTermStructureTest {

    private static final double TOL = 1e-12;

    /** SABRVolTermStructure should reproduce the standalone {@link Sabr#sabrVolatility} value. */
    @Test
    public void testReproducesSabrFormula() {
        final double alpha = 0.10, beta = 0.5, gamma = 0.40, rho = -0.20;
        final double s0 = 100.0, r = 0.02;
        final Date refDate = new Date(15, Month.January, 2026);

        final SABRVolTermStructure ts = new SABRVolTermStructure(
                alpha, beta, gamma, rho, s0, r, refDate, new Actual365Fixed());

        // ATM vol at t = 0.5y, strike = ATM forward
        final double t = 0.5;
        final double strike = s0 * Math.exp(r * t);
        // SABRVolTermStructure routes to BlackVolatilityTermStructure.blackVol(time, strike, extrapolate),
        // which calls blackVolImpl. Use the public accessor.
        final double vol = ts.blackVol(t, strike, true);

        // Reference: direct Sabr formula using the same parameters.
        final double expected = new Sabr().sabrVolatility(strike, strike, t, alpha, beta, gamma, rho);

        assertEquals("SABRVolTermStructure should match analytic SABR formula",
                expected, vol, TOL);
    }

    /** Range bounds: minStrike == 0, maxStrike == Double.MAX_VALUE. */
    @Test
    public void testStrikeBounds() {
        final Date refDate = new Date(15, Month.January, 2026);
        final SABRVolTermStructure ts = new SABRVolTermStructure(
                0.10, 0.5, 0.40, -0.20, 100.0, 0.02, refDate, new Actual365Fixed());
        assertEquals("minStrike", 0.0, ts.minStrike(), 0.0);
        assertEquals("maxStrike", Double.MAX_VALUE, ts.maxStrike(), 0.0);
    }
}
