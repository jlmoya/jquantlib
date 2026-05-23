/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License. See LICENSE.TXT in the
 project root for licence terms.
*/

package org.jquantlib.testsuite.termstructures.volatilities;

import org.jquantlib.QL;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.experimental.volatility.ZabrInterpolation;
import org.jquantlib.experimental.volatility.ZabrSmileSection;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.termstructures.volatilities.SmileSection;
import org.jquantlib.termstructures.volatilities.ZabrEvaluationTag;
import org.jquantlib.termstructures.volatilities.ZabrInterpolatedSmileSection;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link ZabrInterpolatedSmileSection} and
 * {@link ZabrEvaluationTag}.
 *
 * <p>The smile section is a thin wrapper around {@link ZabrInterpolation}
 * that runs calibration on first access and forwards
 * {@link SmileSection#volatilityImpl(double) volatilityImpl} /
 * {@link SmileSection#varianceImpl(double) varianceImpl} to the calibrated
 * interpolator. Because the underlying interpolator is already cross-validated
 * against C++ probes, this test relies on <b>algebraic equivalence</b>:
 * the wrapper must produce exactly the same vol values as a directly-built
 * {@link ZabrInterpolation} fed with the same inputs.
 *
 * <p>The ZabrEvaluationTag tests verify the 1:1 mapping between the
 * sealed tag hierarchy and {@link ZabrSmileSection.Evaluation}.
 *
 * <p>L2-C Phase 2 forward closure.
 */
public class ZabrInterpolatedSmileSectionTest {

    /** Tight tier — wrapper is exact algebra over delegated calibrator. */
    private static final double TOL_EXACT = 0.0;

    public ZabrInterpolatedSmileSectionTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    // ------------------------------------------------------------------
    // ZabrInterpolatedSmileSection
    // ------------------------------------------------------------------

    /**
     * Synthetic market: forward 0.05; five absolute strikes 0.03..0.07
     * with a hand-crafted vol curve. Calibrate a free ZABR and verify
     * the smile section reproduces the calibrated vols exactly.
     */
    @Test
    public void testCalibrationDelegationExactEquivalence() {
        final Date expiry = new Date(15, Month.January, 2030);
        final double forward = 0.05;
        final double atmVol = 0.20;
        final double[] strikes = {0.030, 0.040, 0.050, 0.060, 0.070};
        // Slightly U-shaped smile around ATM.
        final double[] vols = {0.245, 0.215, 0.200, 0.215, 0.245};

        final ZabrInterpolatedSmileSection sec = new ZabrInterpolatedSmileSection(expiry, forward, strikes, atmVol,
                vols);

        // Trigger calibration and read calibrated params.
        final double a = sec.alpha();
        final double b = sec.beta();
        final double n = sec.nu();
        final double r = sec.rho();
        final double g = sec.gamma();

        // Build a parallel ZabrInterpolation directly with the same inputs.
        final Array vx = new Array(strikes);
        final Array vy = new Array(vols);
        final ZabrInterpolation ref = new ZabrInterpolation(vx, vy, sec.exerciseTime(), forward,
                org.jquantlib.math.Constants.NULL_REAL, org.jquantlib.math.Constants.NULL_REAL,
                org.jquantlib.math.Constants.NULL_REAL, org.jquantlib.math.Constants.NULL_REAL,
                org.jquantlib.math.Constants.NULL_REAL,
                false, false, false, false, false, true, null, null);
        ref.update();

        // After calibration with identical seeds the two should produce
        // identical parameters and identical vol values.
        assertEquals(a, ref.alpha(), TOL_EXACT);
        assertEquals(b, ref.beta(), TOL_EXACT);
        assertEquals(n, ref.nu(), TOL_EXACT);
        assertEquals(r, ref.rho(), TOL_EXACT);
        assertEquals(g, ref.gamma(), TOL_EXACT);

        // Vol agreement on a fresh strike grid.
        for (final double k : new double[] {0.025, 0.035, 0.045, 0.055, 0.065, 0.080}) {
            assertEquals("vol@" + k, ref.op(k, true), sec.volatility(k), TOL_EXACT);
        }
    }

    @Test
    public void testVarianceImplMatchesVolSquaredTimesT() {
        final Date expiry = new Date(15, Month.January, 2030);
        final double[] strikes = {0.030, 0.040, 0.050, 0.060, 0.070};
        final double[] vols = {0.245, 0.215, 0.200, 0.215, 0.245};

        final ZabrInterpolatedSmileSection sec = new ZabrInterpolatedSmileSection(expiry, 0.05, strikes, 0.20, vols);
        final double T = sec.exerciseTime();
        for (final double k : new double[] {0.035, 0.050, 0.065}) {
            final double v = sec.volatility(k);
            assertEquals("var@" + k, v * v * T, sec.variance(k), 1e-15);
        }
    }

    @Test
    public void testInspectors() {
        final Date expiry = new Date(15, Month.January, 2030);
        final double[] strikes = {0.030, 0.040, 0.050, 0.060, 0.070};
        final double[] vols = {0.245, 0.215, 0.200, 0.215, 0.245};

        final ZabrInterpolatedSmileSection sec = new ZabrInterpolatedSmileSection(expiry, 0.05, strikes, 0.20, vols);
        assertEquals(0.030, sec.minStrike(), TOL_EXACT);
        assertEquals(0.070, sec.maxStrike(), TOL_EXACT);
        assertEquals(0.05, sec.atmLevel(), TOL_EXACT);
        assertEquals(ZabrSmileSection.Evaluation.ShortMaturityLognormal, sec.evaluation());
        assertNotNull(sec.endCriteria());
        assertTrue("rmsError ≥ 0", sec.rmsError() >= 0.0);
        assertTrue("maxError ≥ 0", sec.maxError() >= 0.0);
    }

    @Test
    public void testFloatingStrikesShiftsByForward() {
        final Date expiry = new Date(15, Month.January, 2030);
        final double forward = 0.05;
        final double atmVol = 0.20;
        // Offsets relative to forward.
        final double[] strikeOffsets = {-0.020, -0.010, 0.0, 0.010, 0.020};
        final double[] volSpreads = {0.045, 0.015, 0.0, 0.015, 0.045};

        final ZabrInterpolatedSmileSection sec = new ZabrInterpolatedSmileSection(expiry, forward, strikeOffsets, true,
                atmVol, volSpreads, org.jquantlib.math.Constants.NULL_REAL, org.jquantlib.math.Constants.NULL_REAL,
                org.jquantlib.math.Constants.NULL_REAL, org.jquantlib.math.Constants.NULL_REAL,
                org.jquantlib.math.Constants.NULL_REAL, false, false, false, false, false, true, null, null,
                new Actual365Fixed());

        // After construction, min/max strikes are forward + offsets.
        assertEquals(0.030, sec.minStrike(), 1e-15);
        assertEquals(0.070, sec.maxStrike(), 1e-15);
    }

    @Test
    public void testUpdateMarksDirty() {
        final Date expiry = new Date(15, Month.January, 2030);
        final double[] strikes = {0.030, 0.040, 0.050, 0.060, 0.070};
        final double[] vols = {0.245, 0.215, 0.200, 0.215, 0.245};

        final ZabrInterpolatedSmileSection sec = new ZabrInterpolatedSmileSection(expiry, 0.05, strikes, 0.20, vols);
        final double v0 = sec.volatility(0.050);
        sec.update(); // mark dirty
        final double v1 = sec.volatility(0.050); // recalc → same result
        assertEquals(v0, v1, 1e-12);
    }

    // ------------------------------------------------------------------
    // ZabrEvaluationTag — sealed marker hierarchy
    // ------------------------------------------------------------------

    @Test
    public void testTagSingletonsAndEnumMapping() {
        assertSame(ZabrSmileSection.Evaluation.ShortMaturityLognormal,
                ZabrEvaluationTag.SHORT_MATURITY_LOGNORMAL.evaluation());
        assertSame(ZabrSmileSection.Evaluation.ShortMaturityNormal,
                ZabrEvaluationTag.SHORT_MATURITY_NORMAL.evaluation());
        assertSame(ZabrSmileSection.Evaluation.LocalVolatility,
                ZabrEvaluationTag.LOCAL_VOLATILITY.evaluation());
        assertSame(ZabrSmileSection.Evaluation.FullFd,
                ZabrEvaluationTag.FULL_FD.evaluation());
    }

    @Test
    public void testTagPatternSwitch() {
        // JDK 25 sealed-type pattern matching: every tag covered exhaustively.
        for (final ZabrEvaluationTag tag : new ZabrEvaluationTag[] {
                ZabrEvaluationTag.SHORT_MATURITY_LOGNORMAL,
                ZabrEvaluationTag.SHORT_MATURITY_NORMAL,
                ZabrEvaluationTag.LOCAL_VOLATILITY,
                ZabrEvaluationTag.FULL_FD,
        }) {
            final String name = switch (tag) {
                case ZabrEvaluationTag.ZabrShortMaturityLognormal t -> "shortLog";
                case ZabrEvaluationTag.ZabrShortMaturityNormal    t -> "shortNorm";
                case ZabrEvaluationTag.ZabrLocalVolatility        t -> "locVol";
                case ZabrEvaluationTag.ZabrFullFd                 t -> "fullFd";
            };
            assertNotNull("dispatched name for " + tag, name);
            assertTrue("non-empty for " + tag, name.length() > 0);
        }
    }
}
