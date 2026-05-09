/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
*/
package org.jquantlib.testsuite.termstructures.volatilities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.jquantlib.QL;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.termstructures.volatilities.Sabr;
import org.jquantlib.termstructures.volatilities.SabrSmileSection;
import org.jquantlib.termstructures.volatilities.SmileSection;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.junit.Test;

/**
 * Phase 4f.5 — SabrSmileSection unit tests.
 *
 * <p>Cross-validates the Java port against C++ QuantLib v1.42.1
 * {@code SabrSmileSection}. The cross-validation strategy is to compare
 * volatility/variance values from the smile section against direct calls to
 * {@link Sabr#unsafeShiftedSabrVolatility}, since both use the identical
 * Hagan-Kumar-Lesniewski-Woodward formula (verified by SabrTest TIGHT
 * regression values).
 */
public class SabrSmileSectionTest {

    public SabrSmileSectionTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    private static final double TIGHT = 1.0e-12;

    @Test
    public void testTimeBasedConstructorAccessors() {
        final double T = 1.0;
        final double forward = 0.05;
        final double[] params = {0.10, 0.5, 0.40, -0.20};
        final SabrSmileSection sec = new SabrSmileSection(T, forward, params);

        assertEquals(0.10, sec.alpha(), 0.0);
        assertEquals(0.50, sec.beta(),  0.0);
        assertEquals(0.40, sec.nu(),    0.0);
        assertEquals(-0.20, sec.rho(),  0.0);
        assertEquals(forward, sec.atmLevel(), 0.0);
        assertEquals(0.0, sec.minStrike(), 0.0);  // shift = 0 → -shift = 0
        assertEquals(Double.MAX_VALUE, sec.maxStrike(), 0.0);
        assertEquals(T, sec.exerciseTime(), 0.0);
    }

    @Test
    public void testVolatilityMatchesDirectSabrFormula() {
        final double T = 2.0;
        final double forward = 0.04;
        final double[] params = {0.15, 0.6, 0.30, -0.30};
        final SabrSmileSection sec = new SabrSmileSection(T, forward, params);
        final Sabr sabr = new Sabr();

        for (final double strike : new double[]{0.02, 0.03, 0.04, 0.05, 0.06}) {
            final double secVol = sec.volatility(strike);
            final double directVol = sabr.unsafeShiftedSabrVolatility(strike, forward, T,
                    params[0], params[1], params[2], params[3], 0.0,
                    VolatilityType.ShiftedLognormal);
            assertEquals("strike=" + strike, directVol, secVol, TIGHT);

            final double secVar = sec.variance(strike);
            assertEquals("strike=" + strike, directVol * directVol * T, secVar, TIGHT);
        }
    }

    @Test
    public void testShiftedSabrSmileSection() {
        final double T = 1.0;
        final double forward = 0.02;
        final double shift = 0.02;
        final double[] params = {0.20, 0.5, 0.30, -0.10};
        final SabrSmileSection sec = new SabrSmileSection(
                T, forward, params, shift, VolatilityType.ShiftedLognormal);

        // minStrike should be -shift = -0.02
        assertEquals(-0.02, sec.minStrike(), 0.0);

        final Sabr sabr = new Sabr();

        // At strike = -0.01 (above -shift, so valid)
        final double strike = -0.01;
        final double secVol = sec.volatility(strike);
        final double directVol = sabr.unsafeShiftedSabrVolatility(strike, forward, T,
                params[0], params[1], params[2], params[3], shift,
                VolatilityType.ShiftedLognormal);
        assertEquals(directVol, secVol, TIGHT);
        assertTrue("vol > 0", secVol > 0);
    }

    @Test
    public void testStrikeClampingAtBarrier() {
        // Mirror C++: strike = max(0.00001 - shift, strike).
        // For shift=0, this clamps strikes ≤ 0 to 0.00001.
        final double T = 1.0;
        final double forward = 0.05;
        final double[] params = {0.10, 0.5, 0.30, -0.10};
        final SabrSmileSection sec = new SabrSmileSection(T, forward, params);
        final Sabr sabr = new Sabr();

        // Strike below 0.00001 should be clamped to 0.00001
        final double secVolAtZero = sec.volatility(-0.001);
        final double directVolClamped = sabr.unsafeShiftedSabrVolatility(0.00001,
                forward, T, params[0], params[1], params[2], params[3], 0.0,
                VolatilityType.ShiftedLognormal);
        assertEquals(directVolClamped, secVolAtZero, TIGHT);
    }

    @Test
    public void testDateBasedConstructor() {
        final Date refDate = new Date(15, Month.January, 2026);
        final Date exDate  = refDate.add(365);  // ~ T = 1 year (365/365)
        final double forward = 0.05;
        final double[] params = {0.10, 0.5, 0.40, -0.20};
        final SabrSmileSection sec = new SabrSmileSection(
                exDate, forward, params, refDate, new Actual365Fixed(),
                0.0, VolatilityType.ShiftedLognormal);

        assertEquals(forward, sec.atmLevel(), 0.0);
        assertEquals(1.0, sec.exerciseTime(), 1.0e-12);
        assertTrue("vol > 0 at ATM", sec.volatility(forward) > 0);
    }

    @Test(expected = Exception.class)
    public void testRejectsInvalidShift() {
        final double[] params = {0.10, 0.5, 0.40, -0.20};
        // forward + shift = 0 → must throw
        new SabrSmileSection(1.0, 0.0, params, 0.0, VolatilityType.ShiftedLognormal);
    }

    @Test(expected = Exception.class)
    public void testRejectsInvalidParamLength() {
        new SabrSmileSection(1.0, 0.05, new double[]{0.10, 0.5, 0.40});
    }

    @Test
    public void testIsSmileSectionInstance() {
        final SabrSmileSection sec = new SabrSmileSection(1.0, 0.05,
                new double[]{0.10, 0.5, 0.40, -0.20});
        assertTrue("SabrSmileSection is a SmileSection", sec instanceof SmileSection);
    }
}
