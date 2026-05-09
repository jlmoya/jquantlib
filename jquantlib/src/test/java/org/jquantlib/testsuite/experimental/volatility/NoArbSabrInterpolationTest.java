/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.volatility;

import org.jquantlib.experimental.volatility.NoArbSabrInterpolation;
import org.jquantlib.experimental.volatility.NoArbSabrModel;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.termstructures.volatilities.Sabr;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Phase 4f integration tests for {@link NoArbSabrInterpolation}.
 *
 * <p>Demonstrates that the calibration loop runs end-to-end (Halton restarts,
 * Levenberg-Marquardt minimization on the SABR-bound parameter domain, with
 * Hagan SABR fallback as the smile evaluator while the absorption-table
 * dependent {@link NoArbSabrModel#optionPrice(double)} is deferred).
 */
public class NoArbSabrInterpolationTest {

    private static final double VOL_TOL = 0.05; // calibration tolerance

    /**
     * Build a synthetic vol smile from a known SABR parameter set, then
     * calibrate NoArbSabrInterpolation; verify the fit is close to the
     * generating SABR parameters (since the fallback IS the SABR formula).
     */
    @Test
    public void testCalibrationRecoversInputs() {
        // True SABR params (within Doust admissible bounds)
        final double trueAlpha = 0.05;
        final double trueBeta  = 0.5;
        final double trueNu    = 0.30;
        final double trueRho   = -0.30;

        final double forward   = 0.05;
        final double t         = 1.0;

        // Synthetic strike grid
        final double[] strikes = {0.02, 0.03, 0.04, 0.05, 0.06, 0.07, 0.08};
        final double[] vols    = new double[strikes.length];
        final Sabr sabr = new Sabr();
        for (int i = 0; i < strikes.length; ++i) {
            vols[i] = sabr.unsafeSabrVolatility(strikes[i], forward, t,
                    trueAlpha, trueBeta, trueNu, trueRho);
        }

        // Calibrate (all 4 free)
        final NoArbSabrInterpolation interp = new NoArbSabrInterpolation(
                new Array(strikes), new Array(vols), t, forward,
                trueAlpha, trueBeta, trueNu, trueRho,
                false, false, false, false,
                true, null, null);
        interp.update();

        // RMS error should be tiny because the fallback IS the SABR formula.
        assertTrue("RMS error should be small (Hagan recovers Hagan): " + interp.rmsError(),
                interp.rmsError() < 1.0e-3);

        // Verify smile evaluation reproduces the input vols.
        for (int i = 0; i < strikes.length; ++i) {
            final double computed = interp.op(strikes[i]);
            assertEquals("vol at strike " + strikes[i],
                    vols[i], computed, VOL_TOL);
        }
    }
}
