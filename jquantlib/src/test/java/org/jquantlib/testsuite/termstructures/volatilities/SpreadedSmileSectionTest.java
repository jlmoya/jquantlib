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
 */
package org.jquantlib.testsuite.termstructures.volatilities;

import static org.junit.Assert.assertEquals;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jquantlib.QL;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.volatilities.FlatSmileSection;
import org.jquantlib.termstructures.volatilities.SpreadedSmileSection;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.junit.Test;

/**
 * Phase 5g.5b WI-3 tests for {@link SpreadedSmileSection}.
 *
 * <p>Probe-driven cross-validation against C++ v1.42.1
 * {@code migration-harness/references/termstructures/volatility/smile-section/smile_section.json}.
 *
 * <p>Tier: TIGHT (1e-12 abs) — purely additive operation; bit-exact from
 * Java side (FP identity).
 */
public class SpreadedSmileSectionTest {

    private static final String REF_GROUP =
            "termstructures/volatility/smile-section/smile_section";

    public SpreadedSmileSectionTest() {
        QL.info("::::: " + getClass().getSimpleName() + " :::::");
    }

    /**
     * Cross-validates a Spreaded(Flat 0.20, +0.025) section against C++.
     * Expected vol is 0.225 at every strike.
     */
    @Test
    public void testSpreadedFlatProbeRoundtrip() {
        final ReferenceReader ref = ReferenceReader.load(REF_GROUP);
        final Case c = ref.getCase("spreaded_flat_section");
        final JSONObject in = c.inputs();
        final JSONObject ex = (JSONObject) c.expectedRaw();

        final double tte     = in.getDouble("tte");
        final double flatVol = in.getDouble("flat_vol");
        final double spread  = in.getDouble("spread");
        final double atm     = in.getDouble("atm");

        final FlatSmileSection base = new FlatSmileSection(
                tte, flatVol, new Actual365Fixed(), atm);
        final Handle<Quote> spreadQuote =
                new Handle<Quote>(new SimpleQuote(spread));

        final SpreadedSmileSection sec = new SpreadedSmileSection(base, spreadQuote);

        final double[] queryStrikes = readDoubles(ex.getJSONArray("query_strikes"));
        final double[] expVols      = readDoubles(ex.getJSONArray("vol"));

        for (int i = 0; i < queryStrikes.length; ++i) {
            assertEquals("vol@" + queryStrikes[i],
                    expVols[i], sec.volatility(queryStrikes[i]), 1.0e-15);
        }
        assertEquals("min_strike", ex.getDouble("min_strike"), sec.minStrike(), 0.0);
        assertEquals("max_strike", ex.getDouble("max_strike"), sec.maxStrike(), 0.0);
        assertEquals("atm_level",  ex.getDouble("atm_level"),  sec.atmLevel(),  0.0);
    }

    /**
     * Quote-mutation observer test. Mutating the spread quote should
     * change subsequent vol queries.
     */
    @Test
    public void testSpreadMutationPropagates() {
        final FlatSmileSection base = new FlatSmileSection(
                1.0, 0.20, new Actual365Fixed(), 0.05);
        final SimpleQuote spreadQ = new SimpleQuote(0.025);
        final SpreadedSmileSection sec = new SpreadedSmileSection(
                base, new Handle<Quote>(spreadQ));

        assertEquals(0.225, sec.volatility(0.05), 1.0e-15);

        spreadQ.setValue(0.05);
        assertEquals(0.25, sec.volatility(0.05), 1.0e-15);

        spreadQ.setValue(-0.10);
        assertEquals(0.10, sec.volatility(0.05), 1.0e-15);
    }

    /**
     * Inspector forwarding: minStrike/maxStrike/atmLevel/dayCounter all
     * forward to the underlying section. FlatSmileSection has
     * minStrike = -DBL_MAX - shift = -DBL_MAX, maxStrike = +DBL_MAX.
     */
    @Test
    public void testInspectorForwarding() {
        final FlatSmileSection base = new FlatSmileSection(
                1.0, 0.20, new Actual365Fixed(), 0.05);
        final Handle<Quote> spreadQuote =
                new Handle<Quote>(new SimpleQuote(0.025));
        final SpreadedSmileSection sec = new SpreadedSmileSection(base, spreadQuote);

        assertEquals(base.minStrike(), sec.minStrike(), 0.0);
        assertEquals(base.maxStrike(), sec.maxStrike(), 0.0);
        assertEquals(base.atmLevel(),  sec.atmLevel(),  0.0);
        assertEquals(base.exerciseTime(), sec.exerciseTime(), 0.0);
    }

    private static double[] readDoubles(final JSONArray arr) {
        final double[] out = new double[arr.length()];
        for (int i = 0; i < arr.length(); ++i) {
            out[i] = arr.getDouble(i);
        }
        return out;
    }
}
