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

import org.jquantlib.math.interpolations.SABRInterpolation;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.optimization.EndCriteria;
import org.jquantlib.math.optimization.LevenbergMarquardt;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.jquantlib.testsuite.util.Tolerance;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Cross-validates {@code XABRInterpolationImpl}'s Halton multi-restart
 * loop against C++ v1.42.1 {@code xabrinterpolation.hpp} via the
 * {@code xabr_restart_loop_probe} reference. Two cases:
 *
 * <ol>
 *   <li>{@code single_iter_deterministic} — maxGuesses=1, no Halton:
 *       fingerprints the first-iteration path that runs the optimizer
 *       once from {@code XABRCoeffHolder::defaultValues}.</li>
 *   <li>{@code multi_iter_convergence} — the canonical 31-strike SABR
 *       smile with errorAccept=1e-10, maxGuesses=50: validates that
 *       the Halton restart loop recovers the true (alpha, beta, nu, rho).</li>
 * </ol>
 *
 * <p>SABRInterpolation is used as the entry point because it's the only
 * concrete XABR consumer we have wired up (the C++ side has Sabr only at
 * v1.42.1; ZABR/SVI/etc. are post-1.42 additions). The test exercises the
 * generic XABR Halton loop end-to-end via the SABRSpecs concrete model.
 */
public class XABRInterpolationImplTest {

    @Test
    public void singleIterDeterministic_matchesCpp() {
        final ReferenceReader reader =
                ReferenceReader.load("math/interpolations/xabr_restart_loop");
        final Case c = reader.getCase("single_iter_deterministic");
        final JSONObject in = c.inputs();
        final JSONObject exp = (JSONObject) c.expectedRaw();

        final SABRInterpolation sabr = build(in);
        sabr.update();

        // Loose tier on params and error — even the single-iteration path
        // runs LevenbergMarquardt to convergence, and the Java port's LM
        // accumulates floating-point error slightly differently than C++
        // Boost's MINPACK-derived implementation. Observed delta ~2e-11
        // (relative) in alpha; loose tier is 1e-8 abs+rel. The structural
        // assertion is that Java reaches the SAME local minimum as C++
        // (same end criteria, same error magnitude, same param topology),
        // not that the bit pattern is identical.
        assertLoose("alpha", sabr.alpha(), exp.getDouble("alpha"));
        assertLoose("beta",  sabr.beta(),  exp.getDouble("beta"));
        assertLoose("nu",    sabr.nu(),    exp.getDouble("nu"));
        assertLoose("rho",   sabr.rho(),   exp.getDouble("rho"));
        assertLoose("error", sabr.rmsError(), exp.getDouble("error"));
        assertLoose("maxError", sabr.maxError(), exp.getDouble("maxError"));
        assertEquals("endCriteria",
                exp.getString("endCriteria"), sabr.endCriteria().name());
    }

    @Test
    public void multiIterConvergence_recoversTrueParams() {
        final ReferenceReader reader =
                ReferenceReader.load("math/interpolations/xabr_restart_loop");
        final Case c = reader.getCase("multi_iter_convergence");
        final JSONObject in = c.inputs();
        final JSONObject exp = (JSONObject) c.expectedRaw();

        final SABRInterpolation sabr = build(in);
        sabr.update();

        // Loose tier on params — same LM-port-vs-C++-Boost noise floor as
        // the single-iter case. Both runs converge to the SAME minimum
        // (true alpha=0.3 etc., agreement to ~3e-11 absolute, well inside
        // loose tier's 1e-8). The interesting structural fact is that the
        // Halton restart loop runs long enough to BREAK OUT of the local
        // minimum near alpha~0.299 the first iteration finds — error drops
        // from ~5.8e-7 (single-iter) to ~1e-13 (after restarts).
        assertLoose("alpha", sabr.alpha(), exp.getDouble("alpha"));
        assertLoose("beta",  sabr.beta(),  exp.getDouble("beta"));
        assertLoose("nu",    sabr.nu(),    exp.getDouble("nu"));
        assertLoose("rho",   sabr.rho(),   exp.getDouble("rho"));
        assertLoose("error", sabr.rmsError(), exp.getDouble("error"));
        assertLoose("maxError", sabr.maxError(), exp.getDouble("maxError"));
        assertEquals("endCriteria",
                exp.getString("endCriteria"), sabr.endCriteria().name());
    }

    private static SABRInterpolation build(final JSONObject in) {
        final JSONArray strikesJson = in.getJSONArray("strikes");
        final JSONArray volsJson = in.getJSONArray("volatilities");
        final double[] strikes = new double[strikesJson.length()];
        final double[] vols = new double[volsJson.length()];
        for (int i = 0; i < strikes.length; i++) {
            strikes[i] = strikesJson.getDouble(i);
            vols[i] = volsJson.getDouble(i);
        }
        // Match the C++ probe: EndCriteria(60000, 100, 1e-8, 1e-8, 1e-8) is
        // the XABR default; LM(1e-8, 1e-8, 1e-8) is the C++ probe's choice.
        final EndCriteria ec = new EndCriteria(60000, 100, 1e-8, 1e-8, 1e-8);
        final LevenbergMarquardt lm = new LevenbergMarquardt(1e-8, 1e-8, 1e-8);
        return new SABRInterpolation(
                new Array(strikes), new Array(vols),
                in.getDouble("expiry"), in.getDouble("forward"),
                in.getDouble("alpha"), in.getDouble("beta"),
                in.getDouble("nu"),    in.getDouble("rho"),
                in.getBoolean("alphaIsFixed"), in.getBoolean("betaIsFixed"),
                in.getBoolean("nuIsFixed"),    in.getBoolean("rhoIsFixed"),
                /*vegaWeighted*/ false,
                ec, lm,
                in.getDouble("errorAccept"),
                in.getBoolean("useMaxError"),
                in.getInt("maxGuesses"),
                /*shift*/ 0.0);
    }

    private static void assertLoose(final String label,
                                    final double got, final double expected) {
        if (!Tolerance.loose(got, expected)) {
            fail(label + ": expected=" + expected + " got=" + got
                    + " (loose tier: rel=1e-8 abs=1e-8)");
        }
    }
}
