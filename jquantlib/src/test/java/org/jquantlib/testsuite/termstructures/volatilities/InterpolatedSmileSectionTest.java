/*
 Copyright (C) 2026 JQuantLib migration contributors.

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

/*
 Copyright (C) 2025 Paolo D'Elia

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/
package org.jquantlib.testsuite.termstructures.volatilities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jquantlib.QL;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.math.interpolations.factories.Cubic;
import org.jquantlib.math.interpolations.factories.Linear;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.volatilities.InterpolatedSmileSection;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 test-suite/interpolatedsmilesection.cpp
 * (Phase 5g.5b WI-2). All 5 C++ cases ported faithfully, plus 2
 * probe-driven Cubic-interpolator cases cross-validated against
 * {@code migration-harness/references/termstructures/volatility/smile-section/smile_section.json}.
 *
 * <p>Tier: TIGHT (1e-12) for analytic queries; node-coincident queries
 * are bit-exact.
 */
public class InterpolatedSmileSectionTest {

    private static final String REF_GROUP =
            "termstructures/volatility/smile-section/smile_section";

    public InterpolatedSmileSectionTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    /**
     * Faithful port of C++ {@code testInterpolationAndVariance} (lines 43-73).
     * Uses Linear interpolator across 3 strikes, queries at midpoint 95
     * (between 90 and 100), checks vol and variance.
     */
    @Test
    public void testInterpolationAndVariance() {
        QL.info("Testing basic behavior of linearly interpolated smile section...");
        final double expiry = 0.25;
        final double sqrtT  = Math.sqrt(expiry);
        final double[] strikes = {90.0, 100.0, 110.0};
        final double[] stdDevs = {0.20 * sqrtT, 0.15 * sqrtT, 0.18 * sqrtT};
        final double atmLevel  = 95.0;

        final InterpolatedSmileSection section = new InterpolatedSmileSection(
                expiry, strikes, stdDevs, atmLevel,
                new Linear(), new Actual365Fixed(),
                VolatilityType.ShiftedLognormal, 0.0, false);

        final double strike = 95.0;
        final double v90  = stdDevs[0] / sqrtT;
        final double v100 = stdDevs[1] / sqrtT;
        final double expectedVol = linearInterp(strike, 90.0, v90, 100.0, v100);

        assertEquals(expectedVol, section.volatility(strike), 1.0e-12);
        assertEquals(expectedVol * expectedVol * expiry,
                section.variance(strike), 1.0e-12);
    }

    /**
     * Faithful port of C++ {@code testExtrapolationWhenAllowed}
     * (lines 75-107). flatStrikeExtrapolation=false, queries below min
     * strike (80) and above max strike (120).
     */
    @Test
    public void testExtrapolationWhenAllowed() {
        QL.info("Testing extrapolation behavior of linearly interpolated smile section...");
        final double expiry = 0.25;
        final double sqrtT  = Math.sqrt(expiry);
        final double[] strikes = {90.0, 100.0, 110.0};
        final double[] stdDevs = {0.20 * sqrtT, 0.15 * sqrtT, 0.18 * sqrtT};
        final double atmLevel  = 95.0;

        final InterpolatedSmileSection section = new InterpolatedSmileSection(
                expiry, strikes, stdDevs, atmLevel,
                new Linear(), new Actual365Fixed(),
                VolatilityType.ShiftedLognormal, 0.0, false);

        final double v90  = stdDevs[0] / sqrtT;
        final double v100 = stdDevs[1] / sqrtT;
        final double v110 = stdDevs[2] / sqrtT;

        final double expectedLow  = linearInterp(80.0, 90.0, v90, 100.0, v100);
        final double expectedHigh = linearInterp(120.0, 110.0, v110, 100.0, v100);

        assertEquals(expectedLow,  section.volatility(80.0),  1.0e-12);
        assertEquals(expectedHigh, section.volatility(120.0), 1.0e-12);
    }

    /**
     * Faithful port of C++ {@code testHandlesUpdatePropagates}
     * (lines 109-147). Constructs with explicit handles and verifies
     * mutating a quote propagates.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testHandlesUpdatePropagates() {
        QL.info("Testing that interpolated smile section observes its quotes...");
        final double expiry = 0.25;
        final double sqrtT  = Math.sqrt(expiry);
        final double[] strikes = {80.0, 90.0, 100.0};

        final SimpleQuote q0 = new SimpleQuote(0.20 * sqrtT);
        final SimpleQuote q1 = new SimpleQuote(0.15 * sqrtT);
        final SimpleQuote q2 = new SimpleQuote(0.18 * sqrtT);
        final Handle<Quote>[] handles = (Handle<Quote>[]) new Handle[] {
                new Handle<Quote>(q0),
                new Handle<Quote>(q1),
                new Handle<Quote>(q2)};
        final SimpleQuote atm = new SimpleQuote(95.0);
        final Handle<Quote> atmHandle = new Handle<Quote>(atm);

        final InterpolatedSmileSection section = new InterpolatedSmileSection(
                expiry, strikes, handles, atmHandle,
                new Linear(), new Actual365Fixed(),
                VolatilityType.ShiftedLognormal, 0.0, false);

        final double v90Before  = q1.value() / sqrtT;
        final double v100Before = q2.value() / sqrtT;
        final double expectedBefore =
                linearInterp(95.0, 90.0, v90Before, 100.0, v100Before);
        assertEquals(expectedBefore, section.volatility(95.0), 1.0e-12);

        // Mutate q1
        q1.setValue(0.20 * sqrtT);
        final double v90After = q1.value() / sqrtT;
        final double expectedAfter =
                linearInterp(95.0, 90.0, v90After, 100.0, v100Before);
        assertEquals(expectedAfter, section.volatility(95.0), 1.0e-12);
    }

    /**
     * Faithful port of C++ {@code testFlatStrikeExtrapolation}
     * (lines 149-190). Sets flatStrikeExtrapolation=true and verifies
     * out-of-bounds queries clamp to the boundary vol.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testFlatStrikeExtrapolation() {
        QL.info("Testing flat strike extrapolation in interpolated smile section...");
        final double expiry = 0.25;
        final double sqrtT  = Math.sqrt(expiry);
        final double[] strikes = {90.0, 100.0, 110.0};

        final SimpleQuote q0 = new SimpleQuote(0.20 * sqrtT);
        final SimpleQuote q1 = new SimpleQuote(0.15 * sqrtT);
        final SimpleQuote q2 = new SimpleQuote(0.18 * sqrtT);
        final Handle<Quote>[] handles = (Handle<Quote>[]) new Handle[] {
                new Handle<Quote>(q0),
                new Handle<Quote>(q1),
                new Handle<Quote>(q2)};
        final Handle<Quote> atmHandle = new Handle<Quote>(new SimpleQuote(95.0));

        final InterpolatedSmileSection section = new InterpolatedSmileSection(
                expiry, strikes, handles, atmHandle,
                new Linear(), new Actual365Fixed(),
                VolatilityType.ShiftedLognormal, 0.0, true);

        final double v90  = q0.value() / sqrtT;
        final double v110 = q2.value() / sqrtT;

        assertEquals(v90,  section.volatility(85.0),  1.0e-12);
        assertEquals(v110, section.volatility(120.0), 1.0e-12);

        q0.setValue(0.21 * sqrtT);
        final double v90After = q0.value() / sqrtT;
        assertEquals(v90After, section.volatility(85.0), 1.0e-12);
    }

    /**
     * Faithful port of C++ {@code testErrorThrowingWhenNonSortedStrikes}
     * (lines 192-211).
     */
    @Test
    public void testErrorThrowingWhenNonSortedStrikes() {
        QL.info("Testing that creation of interpolated smile section with non-sorted strikes throws...");
        final double expiry = 0.25;
        final double sqrtT  = Math.sqrt(expiry);
        final double[] strikes = {90.0, 110.0, 100.0};
        final double[] stdDevs = {0.20 * sqrtT, 0.15 * sqrtT, 0.18 * sqrtT};
        final double atmLevel  = 95.0;

        try {
            new InterpolatedSmileSection(
                    expiry, strikes, stdDevs, atmLevel,
                    new Linear(), new Actual365Fixed(),
                    VolatilityType.ShiftedLognormal, 0.0, false);
            fail("Constructor with non-sorted strikes should throw");
        } catch (final RuntimeException e) {
            // expected — message should mention "sorted in ascending order"
        }
    }

    // =====================================================================
    // Probe-driven cross-validation: Cubic interpolator
    // =====================================================================

    /**
     * Phase 5g.5b WI-2 probe-driven test for Cubic interpolator path.
     * Cross-validates against C++ v1.42.1 reference values.
     */
    @Test
    public void testInterpolatedCubicGrid() {
        final ReferenceReader ref = ReferenceReader.load(REF_GROUP);
        final Case c = ref.getCase("interpolated_cubic_5y_grid");
        final JSONObject in = c.inputs();
        final JSONObject ex = (JSONObject) c.expectedRaw();

        final double tte = in.getDouble("tte");
        final double atm = in.getDouble("atm_level");
        final double[] strikes = readDoubles(in.getJSONArray("strikes"));
        final double[] vols    = readDoubles(in.getJSONArray("vols"));
        final double[] stdDevs = new double[vols.length];
        for (int i = 0; i < vols.length; ++i) {
            stdDevs[i] = vols[i] * Math.sqrt(tte);
        }

        final InterpolatedSmileSection sec = new InterpolatedSmileSection(
                tte, strikes, stdDevs, atm,
                new Cubic(), new Actual365Fixed(),
                VolatilityType.ShiftedLognormal, 0.0, false);

        final double[] queryStrikes = readDoubles(ex.getJSONArray("query_strikes"));
        final double[] expVols      = readDoubles(ex.getJSONArray("vol"));
        final double[] expVar       = readDoubles(ex.getJSONArray("variance"));

        for (int i = 0; i < queryStrikes.length; ++i) {
            assertEquals("vol@" + queryStrikes[i],
                    expVols[i], sec.volatility(queryStrikes[i]), 1.0e-12);
            assertEquals("variance@" + queryStrikes[i],
                    expVar[i], sec.variance(queryStrikes[i]), 1.0e-14);
        }
        assertEquals("min_strike", ex.getDouble("min_strike"), sec.minStrike(), 0.0);
        assertEquals("max_strike", ex.getDouble("max_strike"), sec.maxStrike(), 0.0);
        assertEquals("atm_query",  ex.getDouble("atm_query"),  sec.atmLevel(),  0.0);
    }

    /**
     * Phase 5g.5b WI-2 probe-driven test for Cubic interpolator with
     * flatStrikeExtrapolation=true. Cross-validates against C++ refs.
     */
    @Test
    public void testInterpolatedCubicFlatExtrapolation() {
        final ReferenceReader ref = ReferenceReader.load(REF_GROUP);
        final Case c = ref.getCase("interpolated_cubic_flat_extrap");
        final JSONObject in = c.inputs();
        final JSONObject ex = (JSONObject) c.expectedRaw();

        final double tte = in.getDouble("tte");
        final double atm = in.getDouble("atm_level");
        final double[] strikes = readDoubles(in.getJSONArray("strikes"));
        final double[] vols    = readDoubles(in.getJSONArray("vols"));
        final double[] stdDevs = new double[vols.length];
        for (int i = 0; i < vols.length; ++i) {
            stdDevs[i] = vols[i] * Math.sqrt(tte);
        }

        final InterpolatedSmileSection sec = new InterpolatedSmileSection(
                tte, strikes, stdDevs, atm,
                new Cubic(), new Actual365Fixed(),
                VolatilityType.ShiftedLognormal, 0.0, true);

        final double[] queryStrikes = readDoubles(ex.getJSONArray("query_strikes"));
        final double[] expVols      = readDoubles(ex.getJSONArray("vol"));

        for (int i = 0; i < queryStrikes.length; ++i) {
            assertEquals("vol@" + queryStrikes[i],
                    expVols[i], sec.volatility(queryStrikes[i]), 1.0e-12);
        }
    }

    // ---- helpers ----------------------------------------------------------

    /** Mirrors C++ test-suite linearInterp helper. */
    private static double linearInterp(final double x,
                                       final double x0, final double y0,
                                       final double x1, final double y1) {
        return y0 + (y1 - y0) * (x - x0) / (x1 - x0);
    }

    private static double[] readDoubles(final JSONArray arr) {
        final double[] out = new double[arr.length()];
        for (int i = 0; i < arr.length(); ++i) {
            out[i] = arr.getDouble(i);
        }
        return out;
    }
}
