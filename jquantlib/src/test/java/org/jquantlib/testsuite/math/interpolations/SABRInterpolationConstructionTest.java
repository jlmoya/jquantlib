/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is released under the BSD License.

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

package org.jquantlib.testsuite.math.interpolations;

import org.jquantlib.math.Constants;
import org.jquantlib.math.interpolations.SABRInterpolation;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.jquantlib.testsuite.util.Tolerance;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.fail;

/**
 * Cross-validated construction-time test for {@code SABRCoeffHolder}'s
 * post-default parameter state, against C++ v1.42.1
 * {@code sabrinterpolation.hpp::SABRSpecs::defaultValues}.
 *
 * <p>Consumes the orphan {@code sabr_interpolation_probe} reference
 * generated during Phase 2b L4 (alpha/beta/nu/rho post-default values).
 * Lit up after Phase 2c WI-2 aligned the alpha-default formula. See
 * {@code docs/migration/phase2c-design.md} §3.3 WI-3 for context.
 */
public class SABRInterpolationConstructionTest {

    @Test
    public void allFourParams_postConstruction_matchCppDefaults() {
        final ReferenceReader reader =
                ReferenceReader.load("math/interpolations/sabr_interpolation");
        final Case c = reader.getCase("nullguess_defaults");
        final JSONObject in = c.inputs();
        final JSONObject exp = (JSONObject) c.expectedRaw();

        final JSONArray strikesJson = in.getJSONArray("strikes");
        final JSONArray volsJson = in.getJSONArray("volatilities");
        final double[] strikes = new double[strikesJson.length()];
        final double[] volatilities = new double[volsJson.length()];
        for (int i = 0; i < strikes.length; i++) {
            strikes[i] = strikesJson.getDouble(i);
            volatilities[i] = volsJson.getDouble(i);
        }
        final double expiry = in.getDouble("expiry");
        final double forward = in.getDouble("forward");

        // The four guesses are stored as the string "Null<Real>" in the
        // reference file, mirroring how the C++ probe records the sentinel.
        // Java's equivalent is Constants.NULL_REAL (= Double.MAX_VALUE).
        final SABRInterpolation sabr = new SABRInterpolation(
                new Array(strikes), new Array(volatilities),
                expiry, forward,
                Constants.NULL_REAL, Constants.NULL_REAL,
                Constants.NULL_REAL, Constants.NULL_REAL,
                false, false, false, false,
                false,
                null, null);
        // Don't call update() — we're asserting the post-construction state
        // produced by SABRCoeffHolder's defaultValues, not a fitted result.

        assertParamMatches(exp, "alpha_post_default", sabr.alpha());
        assertParamMatches(exp, "beta_post_default",  sabr.beta());
        assertParamMatches(exp, "nu_post_default",    sabr.nu());
        assertParamMatches(exp, "rho_post_default",   sabr.rho());
    }

    /**
     * High-beta arm of the alpha-default formula. C++ v1.42.1
     * sabrinterpolation.hpp:71-76 has the {@code 0.2} factor OUTSIDE
     * the ternary, so the {@code beta >= 0.9999} branch yields
     * {@code 0.2 * 1.0 = 0.2} — NOT {@code sqrt(0.2) ~= 0.4472}.
     * This case pins {@code beta=1.0} with {@code betaIsFixed=true} so
     * beta stays at 1.0 through construction and the high-beta arm is
     * exercised.
     */
    @Test
    public void highBeta_alphaDefault_returnsZeroPointTwo() {
        final ReferenceReader reader =
                ReferenceReader.load("math/interpolations/sabr_interpolation");
        final Case c = reader.getCase("highbeta_defaults");
        final JSONObject in = c.inputs();
        final JSONObject exp = (JSONObject) c.expectedRaw();

        final JSONArray strikesJson = in.getJSONArray("strikes");
        final JSONArray volsJson = in.getJSONArray("volatilities");
        final double[] strikes = new double[strikesJson.length()];
        final double[] volatilities = new double[volsJson.length()];
        for (int i = 0; i < strikes.length; i++) {
            strikes[i] = strikesJson.getDouble(i);
            volatilities[i] = volsJson.getDouble(i);
        }
        final double expiry = in.getDouble("expiry");
        final double forward = in.getDouble("forward");

        final SABRInterpolation sabr = new SABRInterpolation(
                new Array(strikes), new Array(volatilities),
                expiry, forward,
                Constants.NULL_REAL, 1.0,
                Constants.NULL_REAL, Constants.NULL_REAL,
                false, true, false, false,    // betaIsFixed=true so beta stays at 1.0
                false,
                null, null);

        assertParamMatches(exp, "alpha_post_default", sabr.alpha());
        assertParamMatches(exp, "beta_post_default",  sabr.beta());
        assertParamMatches(exp, "nu_post_default",    sabr.nu());
        assertParamMatches(exp, "rho_post_default",   sabr.rho());
    }

    private static void assertParamMatches(
            final JSONObject exp, final String key, final double got) {
        final double expected = exp.getDouble(key);
        if (!Tolerance.tight(got, expected)) {
            fail(key + ": expected=" + expected + " got=" + got
                    + " (tight tier: rel=1e-12 abs=1e-14)");
        }
    }
}
