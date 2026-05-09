/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.instruments;

import static org.junit.Assert.fail;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Phase 5f skeleton port of {@code test-suite/bermudanswaption.cpp} v1.42.1
 * (693 LOC, 6 test cases).
 *
 * <p><strong>All cases deferred to Phase 5f.5</strong> — the C++ tests
 * exercise the {@code TreeSwaptionEngine} and {@code FdHullWhiteSwaptionEngine}
 * (both already ported in Java, see
 * {@code testsuite.pricingengines.swaption.TreeSwaptionEngineTest} and
 * {@code FdHullWhiteSwaptionEngineTest}) but applied to a *Bermudan*
 * exercise, plus the OIS variant (Phase 5e dep) and G2 two-factor
 * pricing (Java {@link org.jquantlib.model.shortrate.twofactormodels.G2}
 * present but Bermudan-OIS calibration plumbing absent).
 *
 * <ul>
 *   <li>{@code testCachedValues} — Bermudan swaption tree-engine cache</li>
 *   <li>{@code testCachedG2Values} — Bermudan G2 cache</li>
 *   <li>{@code testTreeEngineTimeSnapping} — exercise-date snapping</li>
 *   <li>{@code testBermudanOISSwaptionWithHW} — OIS Bermudan w/ HW</li>
 *   <li>{@code testBermudanOISSwaptionWithG2} — OIS Bermudan w/ G2</li>
 *   <li>{@code testBermudanOISSwaptionPreservesFeatures} — feature
 *       preservation (notional schedule, day-count) in OIS Bermudan</li>
 * </ul>
 *
 * <p>Source: {@code test-suite/bermudanswaption.cpp} v1.42.1 @
 * {@code 099987f0ca}.
 */
public class BermudanSwaptionTest {

    @Ignore("Phase 5f.5 — Bermudan exercise + cached reference values")
    @Test
    public void testCachedValues() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — G2 Bermudan cached reference values")
    @Test
    public void testCachedG2Values() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — Tree engine exercise-date snapping logic")
    @Test
    public void testTreeEngineTimeSnapping() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — OIS Bermudan w/ Hull-White (Phase 5e OIS dep)")
    @Test
    public void testBermudanOISSwaptionWithHW() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — OIS Bermudan w/ G2 (Phase 5e OIS dep)")
    @Test
    public void testBermudanOISSwaptionWithG2() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — OIS Bermudan feature-preservation (Phase 5e OIS dep)")
    @Test
    public void testBermudanOISSwaptionPreservesFeatures() { fail("not implemented"); }
}
