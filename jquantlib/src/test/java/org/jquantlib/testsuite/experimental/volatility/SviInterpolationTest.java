/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.volatility;

import org.jquantlib.experimental.volatility.SviInterpolation;
import org.jquantlib.math.matrixutilities.Array;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Phase 4f integration test for {@link SviInterpolation}.
 *
 * <p>Demonstrates that the SVI calibration loop (Halton restarts +
 * Levenberg-Marquardt + 5-parameter SVI direct/inverse transformations)
 * runs end-to-end and recovers the generating SVI parameters.
 */
public class SviInterpolationTest {

    @Test
    public void testCalibrationRecoversInputs() {
        // True SVI parameters (Gatheral 2004)
        final double trueA = 0.04, trueB = 0.4, trueSigma = 0.1, trueRho = -0.4, trueM = 0.0;

        final double forward = 100.0;
        final double t = 1.0;

        // Synthetic strike grid
        final double[] strikes = {60.0, 80.0, 90.0, 100.0, 110.0, 120.0, 140.0};
        final double[] vols    = new double[strikes.length];
        for (int i = 0; i < strikes.length; ++i) {
            final double k = Math.log(strikes[i] / forward);
            final double w = SviInterpolation.sviTotalVariance(trueA, trueB, trueSigma, trueRho, trueM, k);
            vols[i] = Math.sqrt(Math.max(0.0, w / t));
        }

        // Calibrate (all 5 free)
        final SviInterpolation interp = new SviInterpolation(
                new Array(strikes), new Array(vols), t, forward,
                trueA, trueB, trueSigma, trueRho, trueM,
                false, false, false, false, false,
                true, null, null);
        interp.update();

        // RMS error should be near zero (SVI recovers SVI)
        assertTrue("RMS error should be tiny: " + interp.rmsError(),
                interp.rmsError() < 1.0e-2);

        // Verify smile evaluation reproduces the input vols within tolerance
        for (int i = 0; i < strikes.length; ++i) {
            final double computed = interp.op(strikes[i]);
            assertEquals("vol at strike " + strikes[i],
                    vols[i], computed, 1.0e-2);
        }
    }
}
