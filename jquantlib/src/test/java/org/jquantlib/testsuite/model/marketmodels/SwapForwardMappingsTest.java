/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.model.marketmodels;

import static org.junit.Assert.fail;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Phase 5f skeleton port of {@code test-suite/swapforwardmappings.cpp}
 * v1.42.1 (445 LOC, 2 test cases).
 *
 * <p>Java has {@link
 * org.jquantlib.model.marketmodels.SwapForwardMappings} but the
 * test-suite cases also pull in {@code MarketModel}, {@code CurveState},
 * {@code SwaptionPseudoDerivative}, and the LIBOR-rate-pseudo-root
 * Jacobian-of-the-mapping logic — all of which sit deeper in the
 * marketmodels machinery (Phase 3h–3k carry-forwards).
 *
 * <p><strong>Both cases deferred to Phase 5f.5</strong>:
 * <ul>
 *   <li>{@code testForwardSwapJacobians} — Jacobian of swap rates
 *       wrt forward rates matches numerical finite-difference</li>
 *   <li>{@code testSwaptionImpliedVolatility} — swaption implied vol
 *       inferred from forward-rate covariance matrix matches direct
 *       Black inversion</li>
 * </ul>
 *
 * <p>Source: {@code test-suite/swapforwardmappings.cpp} v1.42.1 @
 * {@code 099987f0ca}.
 */
public class SwapForwardMappingsTest {

    @Ignore("Phase 5f.5 — swap-vs-forward Jacobian harness (marketmodels dep)")
    @Test
    public void testForwardSwapJacobians() { fail("not implemented"); }

    @Ignore("Phase 5f.5 — swaption-implied-vol-from-FRA-cov harness")
    @Test
    public void testSwaptionImpliedVolatility() { fail("not implemented"); }
}
