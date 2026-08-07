/*
 Copyright (C) 2026 Jose Moya

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

package org.jquantlib.testsuite.experimental.volatility;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.jquantlib.QL;
import org.jquantlib.experimental.volatility.Zabr;
import org.jquantlib.experimental.volatility.ZabrInterpolation;
import org.jquantlib.math.interpolations.Interpolation;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.optimization.EndCriteria;
import org.jquantlib.math.optimization.LevenbergMarquardt;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Cross-validates the {@link Zabr} interpolation factory — and the
 * {@link ZabrInterpolation} it produces — against the
 * {@code math/interpolations/zabr_interpolation} probe reference.
 * <p>
 * C++ {@code Zabr} ({@code ql/math/interpolations/zabrinterpolation.hpp:169})
 * is the ZABR member of the XABR traits-factory family. Its four siblings
 * ({@code SABR}, {@code NoArbSabr}, {@code VannaVolga}, {@code LinearFlat}) are
 * allowlisted in the coverage gate as C++-only tags; this one is ported instead
 * because JQuantLib already models the C++ {@code Interpolator} concept as a
 * real Java interface and already ports the parallel {@code Abcd} factory
 * against it.
 * <p>
 * Porting it also closed a genuine hole. The pre-existing ZABR references pin
 * {@code ZabrModel} — the closed-form smile — not {@code ZabrInterpolation},
 * whose XABR path adds the {@code ZabrSpecs} direct/inverse parameter
 * transformations and the least-squares calibration loop on top. Both are
 * covered here:
 * <ul>
 * <li>the {@code _fixed} cases pin every parameter, so no optimisation runs and
 * the smile is a deterministic function of the inputs;</li>
 * <li>the {@code _calibrated} case frees alpha, nu and rho, so the calibration
 * loop runs, and the fitted parameters are asserted alongside the smile so a
 * disagreement is attributable to the optimiser rather than the model.</li>
 * </ul>
 * <p>
 * Tolerance tiers: the {@code _fixed} cases use the tight tier (1e-12 relative,
 * 1e-14 absolute) — no iteration is involved, both sides evaluate the same
 * closed form over the same doubles. The {@code _calibrated} case uses the
 * loose tier (1e-8 relative) because its values come out of a
 * Levenberg-Marquardt solve whose stopping test is met at slightly different
 * iterates once any operation differs in the last ulp; 1e-8 is still four
 * orders tighter than the fit's own rms residual (~1e-3), so a real
 * disagreement in the calibration cannot hide under it.
 *
 * @author Jose Moya
 */
public class ZabrInterpolationFactoryTest {

    private static final String GROUP = "math/interpolations/zabr_interpolation";
    private static final double TIGHT_REL = 1.0e-12;
    private static final double TIGHT_ABS = 1.0e-14;
    private static final double LOOSE_REL = 1.0e-8;

    public ZabrInterpolationFactoryTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    private static Array toArray(final JSONArray a) {
        final double[] out = new double[a.length()];
        for (int i = 0; i < a.length(); i++) {
            out[i] = a.getDouble(i);
        }
        return new Array(out);
    }

    private static void checkCase(final String caseName, final double rel) {
        final ReferenceReader ref = ReferenceReader.load(GROUP);
        final ReferenceReader.Case c = ref.getCase(caseName);
        final JSONObject in = c.inputs();
        final JSONObject expected = (JSONObject) c.expectedRaw();

        final Zabr factory = new Zabr(
                in.getDouble("t"), in.getDouble("forward"),
                in.getDouble("alpha"), in.getDouble("beta"), in.getDouble("nu"),
                in.getDouble("rho"), in.getDouble("gamma"),
                in.getBoolean("alphaIsFixed"), in.getBoolean("betaIsFixed"),
                in.getBoolean("nuIsFixed"), in.getBoolean("rhoIsFixed"),
                in.getBoolean("gammaIsFixed"),
                false,
                new EndCriteria(60000, 100, 1e-8, 1e-8, 1e-8),
                new LevenbergMarquardt(1e-8, 1e-8, 1e-8),
                0.0020, false, 50);

        // C++ Zabr::global is a compile-time constant; assert the Java factory
        // reports the same, since bootstrap code branches on it.
        assertEquals(caseName + ": global", expected.getBoolean("global"), factory.global());

        final Interpolation interp = factory.interpolate(
                toArray(in.getJSONArray("strikes")), toArray(in.getJSONArray("vols")));
        interp.update();
        assertTrue(caseName + ": factory must produce a ZabrInterpolation",
                interp instanceof ZabrInterpolation);
        final ZabrInterpolation zabr = (ZabrInterpolation) interp;

        assertEquals(caseName + ": fitted alpha", expected.getDouble("fittedAlpha"),
                zabr.alpha(), rel * Math.abs(expected.getDouble("fittedAlpha")));
        assertEquals(caseName + ": fitted beta", expected.getDouble("fittedBeta"),
                zabr.beta(), rel * Math.abs(expected.getDouble("fittedBeta")));
        assertEquals(caseName + ": fitted nu", expected.getDouble("fittedNu"),
                zabr.nu(), rel * Math.abs(expected.getDouble("fittedNu")));
        assertEquals(caseName + ": fitted rho", expected.getDouble("fittedRho"),
                zabr.rho(), rel * Math.abs(expected.getDouble("fittedRho")));
        assertEquals(caseName + ": fitted gamma", expected.getDouble("fittedGamma"),
                zabr.gamma(), rel * Math.abs(expected.getDouble("fittedGamma")));
        assertEquals(caseName + ": rms error", expected.getDouble("rmsError"),
                zabr.rmsError(), Math.max(1e-14, rel * Math.abs(expected.getDouble("rmsError"))));

        final JSONArray rows = expected.getJSONArray("rows");
        assertTrue(caseName + ": probe produced no rows", rows.length() > 0);
        for (int r = 0; r < rows.length(); r++) {
            final JSONObject row = rows.getJSONObject(r);
            final double k = row.getDouble("k");
            final double v = row.getDouble("vol");
            assertEquals(caseName + " row " + r + " k=" + k, v, interp.op(k, true),
                    Math.max(TIGHT_ABS, rel * Math.abs(v)));
        }
    }

    /** All five parameters fixed: the smile evaluation alone, no optimisation. */
    @Test
    public void testAtmSkewAllParametersFixed() {
        QL.info("Testing the Zabr factory with all parameters fixed against C++ v1.43...");
        checkCase("atm_skew_fixed", TIGHT_REL);
    }

    /** A second fixed-parameter fixture with gamma below 1, which takes the non-closed-form branch. */
    @Test
    public void testGammaBelowOneAllParametersFixed() {
        QL.info("Testing the Zabr factory at gamma < 1 with all parameters fixed against C++ v1.43...");
        checkCase("gamma_low_fixed", TIGHT_REL);
    }

    /** alpha, nu and rho free: the XABR least-squares calibration loop. */
    @Test
    public void testAtmSkewCalibrated() {
        QL.info("Testing the Zabr factory's calibration path against C++ v1.43...");
        checkCase("atm_skew_calibrated", LOOSE_REL);
    }
}
