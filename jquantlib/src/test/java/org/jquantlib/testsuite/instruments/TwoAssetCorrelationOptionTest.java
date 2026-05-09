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
 * Phase 5k skeleton port of {@code test-suite/twoassetcorrelationoption.cpp}
 * v1.42.1 (91 LOC, 1 case).
 *
 * <p>Exercises the two-asset correlation option (Zhang 1995 closed form;
 * payoff is the in-the-money intrinsic of asset 2 conditional on asset 1
 * being in the money). Cross-validated against the Haug 2007 reference
 * table.
 *
 * <p><strong>The 1 case deferred to Phase 5k.5</strong> — Java has no
 * two-asset correlation subsystem:
 * <ul>
 *   <li>No {@code TwoAssetCorrelationOption} instrument class;
 *   <li>No {@code AnalyticTwoAssetCorrelationEngine} (Zhang 1995 closed
 *       form; depends on bivariate-normal CDF);
 *   <li>The bivariate normal CDF used by the formula is present in
 *       {@code org.jquantlib.math.distributions} but is not wired
 *       through a two-asset correlation engine.
 * </ul>
 *
 * <p>Production-code carry-forward to a future multi-asset correlation
 * extensions phase; Phase 5k.5 is the test-only carry-forward tag.
 *
 * <p>Source: {@code test-suite/twoassetcorrelationoption.cpp} v1.42.1
 * @ {@code 099987f0ca}.
 */
public class TwoAssetCorrelationOptionTest {

    private static final String REASON_ANALYTIC =
            "Phase 5k.5 — requires TwoAssetCorrelationOption instrument + "
          + "AnalyticTwoAssetCorrelationEngine (Zhang 1995) for the "
          + "Haug 2007 reference table";

    @Ignore(REASON_ANALYTIC) @Test public void testAnalyticEngine() { fail("not implemented"); }
}
